package de.xbrowniecodez.jbytemod.jdwp.protocol;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class JdwpPacket {

    public static final int HEADER_SIZE = 11;
    public static final int FLAG_REPLY = 0x80;

    public final int id;
    public final int flags;
    public final int commandSet;
    public final int command;
    public final short errorCode;
    public final byte[] data;

    private JdwpPacket(int id, int flags, int commandSet, int command, short errorCode, byte[] data) {
        this.id = id;
        this.flags = flags;
        this.commandSet = commandSet;
        this.command = command;
        this.errorCode = errorCode;
        this.data = data;
    }

    public static JdwpPacket command(int id, int commandSet, int command, byte[] data) {
        return new JdwpPacket(id, 0, commandSet, command, (short) 0, data != null ? data : new byte[0]);
    }

    public static JdwpPacket readFrom(InputStream in) throws IOException {
        byte[] header = readExact(in, HEADER_SIZE);
        ByteBuffer hb = ByteBuffer.wrap(header);
        int length = hb.getInt();
        int id = hb.getInt();
        int flags = hb.get() & 0xFF;
        int dataLen = length - HEADER_SIZE;
        boolean reply = (flags & FLAG_REPLY) != 0;
        int commandSet, command;
        short errorCode;
        if (reply) {
            errorCode = hb.getShort();
            commandSet = 0;
            command = 0;
        } else {
            commandSet = hb.get() & 0xFF;
            command = hb.get() & 0xFF;
            errorCode = 0;
        }
        byte[] data = dataLen > 0 ? readExact(in, dataLen) : new byte[0];
        return new JdwpPacket(id, flags, commandSet, command, errorCode, data);
    }

    public byte[] toBytes() {
        int total = HEADER_SIZE + data.length;
        ByteBuffer buf = ByteBuffer.allocate(total);
        buf.putInt(total);
        buf.putInt(id);
        buf.put((byte) flags);
        if (isReply()) {
            buf.putShort(errorCode);
        } else {
            buf.put((byte) commandSet);
            buf.put((byte) command);
        }
        buf.put(data);
        return buf.array();
    }

    public boolean isReply() {
        return (flags & FLAG_REPLY) != 0;
    }

    public boolean isError() {
        return isReply() && errorCode != 0;
    }

    public DataReader reader() {
        return new DataReader(data);
    }

    private static byte[] readExact(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int read = 0;
        while (read < n) {
            int r = in.read(buf, read, n - read);
            if (r < 0) throw new EOFException("JDWP stream ended unexpectedly");
            read += r;
        }
        return buf;
    }

    public static class DataWriter {
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        private final DataOutputStream dos = new DataOutputStream(bos);

        public DataWriter writeByte(int v) throws IOException { 
            dos.writeByte(v); 
            return this; 
        }
        
        public DataWriter writeBoolean(boolean v) throws IOException { 
            dos.writeByte(v ? 1 : 0); 
            return this; 
        }
        
        public DataWriter writeInt(int v) throws IOException { 
            dos.writeInt(v); 
            return this; 
        }
        
        public DataWriter writeLong(long v) throws IOException { 
            dos.writeLong(v); 
            return this; 
        }
        
        public DataWriter writeBytes(byte[] v) throws IOException { 
            dos.write(v); 
            return this; 
        }
        
        public DataWriter writeString(String s) throws IOException {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(bytes.length);
            dos.write(bytes);
            return this;
        }
        public DataWriter writeObjectId(long id, int size) throws IOException {
            writeIdSized(id, size);
            return this;
        }

        private void writeIdSized(long id, int size) throws IOException {
            switch (size) {
                case 4: dos.writeInt((int) id); 
                break;
                case 8: dos.writeLong(id); 
                break;
                default: throw new IOException("Unsupported id size: " + size);
            }
        }

        public byte[] toBytes() { 
            return bos.toByteArray(); 
        }
    }

    public static class DataReader {
        private final DataInputStream dis;

        public DataReader(byte[] data) {
            this.dis = new DataInputStream(new ByteArrayInputStream(data));
        }

        public int readByte() throws IOException { 
            return dis.readUnsignedByte(); 
        }
        
        public boolean readBoolean() throws IOException { 
            return dis.readByte() != 0; 
        }
        
        public int readInt() throws IOException { 
            return dis.readInt(); 
        }
        
        public long readLong() throws IOException { 
            return dis.readLong(); 
        }
        
        public short readShort() throws IOException { 
            return dis.readShort(); 
        }

        public String readString() throws IOException {
            int len = dis.readInt();
            byte[] b = new byte[len];
            dis.readFully(b);
            return new String(b, StandardCharsets.UTF_8);
        }

        public long readObjectId(int size) throws IOException {
            switch (size) {
                case 4: 
                    return dis.readInt() & 0xFFFFFFFFL;
                case 8: 
                    return dis.readLong();
                default: throw new IOException("Unsupported id size: " + size);
            }
        }

        public int available() throws IOException { 
            return dis.available(); 
        }
    }
}
