package com.lunarrpc.discord;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Low-level IPC client that communicates with the Discord desktop client
 * through the local named pipe / Unix domain socket.
 *
 * Protocol: each frame is [int32 opcode][int32 length][payload bytes]
 *
 * Windows: named pipe  \\.\pipe\discord-ipc-0  through  discord-ipc-9
 * Unix:    abstract Unix domain socket  /discord-ipc-0  through  /discord-ipc-9
 */
public class DiscordIPC {

    private Object pipe; // RandomAccessFile on Windows, Socket on Unix
    private InputStream input;
    private OutputStream output;
    private final boolean isWindows;

    static class IPCMessage {
        final int opcode;
        final String payload;
        IPCMessage(int opcode, String payload) {
            this.opcode = opcode;
            this.payload = payload;
        }
    }

    public DiscordIPC() {
        this.isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Tries to connect to the first available Discord IPC pipe (0-9).
     *
     * @return true if connection succeeded
     */
    public boolean connect() {
        for (int i = 0; i < 10; i++) {
            try {
                if (isWindows) {
                    return connectWindows(i);
                } else {
                    if (connectUnix(i)) return true;
                }
            } catch (Exception e) {
                // Try next pipe index
            }
        }
        return false;
    }

    // ========================================================================
    // Windows: Named Pipe via RandomAccessFile
    // ========================================================================

    private boolean connectWindows(int index) throws Exception {
        String pipePath = "\\\\.\\pipe\\discord-ipc-" + index;
        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(pipePath, "rw");
        this.pipe = raf;
        this.input = new WindowsPipeInputStream(raf);
        this.output = new WindowsPipeOutputStream(raf);
        return true;
    }

    // ========================================================================
    // Unix: Unix Domain Socket via JNA or Process fallback
    // ========================================================================

    private boolean connectUnix(int index) throws Exception {
        // Try to find the Discord IPC socket in common locations
        String xdgRuntimeDir = System.getenv("XDG_RUNTIME_DIR");
        String tmpDir = System.getenv("TMPDIR");
        if (tmpDir == null) tmpDir = "/tmp";

        String[] searchDirs;
        if (xdgRuntimeDir != null) {
            searchDirs = new String[]{xdgRuntimeDir, tmpDir};
        } else {
            searchDirs = new String[]{tmpDir};
        }

        // Try abstract socket first (most Linux setups)
        for (String dir : searchDirs) {
            String socketPath = dir + "/discord-ipc-" + index;
            try {
                java.net.SocketAddress address = new java.net.UnixDomainSocketAddress(socketPath);

                // Try using Java 16+ Unix domain socket API
                Class<?> socketClass = Class.forName("java.net.UnixDomainSocket");
                // This won't work directly since we can't cast, let's try alternative approach

                // Fallback: use ProcessBuilder with socat or nc
                // Actually, let's try the standard Java 16+ approach
                var channel = java.nio.channels.SocketChannel.open(java.net.UnixDomainSocketAddress.of(socketPath));
                this.pipe = channel;
                this.input = new UnixChannelInputStream(channel);
                this.output = new UnixChannelOutputStream(channel);
                return true;
            } catch (Exception e) {
                // Try next directory
            }
        }

        // Try snap directory
        String snapDir = System.getProperty("user.home") + "/snap/discord/common";
        try {
            String socketPath = snapDir + "/discord-ipc-" + index;
            var channel = java.nio.channels.SocketChannel.open(java.net.UnixDomainSocketAddress.of(socketPath));
            this.pipe = channel;
            this.input = new UnixChannelInputStream(channel);
            this.output = new UnixChannelOutputStream(channel);
            return true;
        } catch (Exception e) {
            // Not found
        }

        return false;
    }

    // ========================================================================
    // IPC frame read / write
    // ========================================================================

    /**
     * Sends a frame: [opcode:int32][length:int32][payload:bytes]
     */
    public void send(int opcode, String payload) throws IOException {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer header = ByteBuffer.allocate(8);
        header.order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(opcode);
        header.putInt(payloadBytes.length);
        output.write(header.array());
        output.write(payloadBytes);
        output.flush();
    }

    /**
     * Reads one frame from the IPC pipe.
     *
     * @return the decoded message, or null on EOF / error
     */
    public IPCMessage read() {
        try {
            byte[] headerBuf = new byte[8];
            int totalRead = 0;
            while (totalRead < 8) {
                int n = input.read(headerBuf, totalRead, 8 - totalRead);
                if (n <= 0) return null;
                totalRead += n;
            }

            ByteBuffer header = ByteBuffer.wrap(headerBuf);
            header.order(ByteOrder.LITTLE_ENDIAN);
            int opcode = header.getInt();
            int length = header.getInt();

            if (length <= 0 || length > 1024 * 1024) {
                // Sanity check — skip unreasonably large frames
                return new IPCMessage(opcode, "");
            }

            byte[] payloadBuf = new byte[length];
            totalRead = 0;
            while (totalRead < length) {
                int n = input.read(payloadBuf, totalRead, length - totalRead);
                if (n <= 0) return null;
                totalRead += n;
            }

            String payload = new String(payloadBuf, StandardCharsets.UTF_8);
            return new IPCMessage(opcode, payload);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Closes the IPC connection.
     */
    public void close() {
        try {
            if (pipe instanceof java.io.RandomAccessFile raf) {
                raf.close();
            } else if (pipe instanceof java.nio.channels.SocketChannel ch) {
                ch.close();
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    // ========================================================================
    // Windows named pipe I/O wrappers
    // ========================================================================

    private static class WindowsPipeInputStream extends InputStream {
        private final java.io.RandomAccessFile raf;
        WindowsPipeInputStream(java.io.RandomAccessFile raf) { this.raf = raf; }
        @Override public int read() throws IOException { return raf.read(); }
        @Override public int read(byte[] b, int off, int len) throws IOException { return raf.read(b, off, len); }
    }

    private static class WindowsPipeOutputStream extends OutputStream {
        private final java.io.RandomAccessFile raf;
        WindowsPipeOutputStream(java.io.RandomAccessFile raf) { this.raf = raf; }
        @Override public void write(int b) throws IOException { raf.write(b); }
        @Override public void write(byte[] b, int off, int len) throws IOException { raf.write(b, off, len); }
        @Override public void flush() throws IOException { /* named pipes flush automatically on Windows */ }
    }

    // ========================================================================
    // Unix domain socket I/O wrappers (Java 16+ SocketChannel)
    // ========================================================================

    private static class UnixChannelInputStream extends InputStream {
        private final java.nio.channels.SocketChannel channel;
        UnixChannelInputStream(java.nio.channels.SocketChannel ch) { this.channel = ch; }
        @Override
        public int read() throws IOException {
            ByteBuffer buf = ByteBuffer.allocate(1);
            int n = channel.read(buf);
            if (n <= 0) return -1;
            return buf.get(0) & 0xFF;
        }
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            ByteBuffer buf = ByteBuffer.wrap(b, off, len);
            int n = channel.read(buf);
            return n <= 0 ? -1 : n;
        }
    }

    private static class UnixChannelOutputStream extends OutputStream {
        private final java.nio.channels.SocketChannel channel;
        UnixChannelOutputStream(java.nio.channels.SocketChannel ch) { this.channel = ch; }
        @Override
        public void write(int b) throws IOException {
            ByteBuffer buf = ByteBuffer.allocate(1);
            buf.put((byte) b);
            buf.flip();
            channel.write(buf);
        }
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            ByteBuffer buf = ByteBuffer.wrap(b, off, len);
            while (buf.hasRemaining()) {
                channel.write(buf);
            }
        }
        @Override
        public void flush() throws IOException { /* Unix sockets flush automatically */ }
    }
}
