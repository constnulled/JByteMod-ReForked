package de.xbrowniecodez.jbytemod.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

public class CustomClassReader extends ClassReader {

    private static final int VERSION_OFFSET = 6;

    public CustomClassReader(byte[] classFile) {
        super(classFile);
    }

    @Override
    @SuppressWarnings("deprecation")
    public short readShort(int offset) {
        byte[] data = this.b;
        if (data == null || offset < 0 || offset + 1 >= data.length) return Opcodes.V1_7;
        short value = (short) ((data[offset] & 255) << 8 | data[offset + 1] & 255);
        if (offset == VERSION_OFFSET && value > Opcodes.V1_8) return Opcodes.V1_8;
        return value;
    }

    @Override
    public String readUTF8(int offset, char[] charBuffer) {
        try {
            return super.readUTF8(offset, charBuffer);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String readModule(int offset, char[] charBuffer) {
        try {
            return super.readModule(offset, charBuffer);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public int readUnsignedShort(int offset) {
        try {
            return super.readUnsignedShort(offset);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public String readClass(int offset, char[] charBuffer) {
        try {
            return super.readClass(offset, charBuffer);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public Object readConst(int constantPoolEntryIndex, char[] charBuffer) {
        try {
            return super.readConst(constantPoolEntryIndex, charBuffer);
        } catch (Exception e) {
            return null;
        }
    }
}
