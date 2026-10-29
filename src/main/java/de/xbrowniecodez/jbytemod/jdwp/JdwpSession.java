package de.xbrowniecodez.jbytemod.jdwp;

import de.xbrowniecodez.jbytemod.jdwp.protocol.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;

public class JdwpSession implements JdwpConnection.EventListener {

    private final JdwpConnection conn;
    private final List<SessionListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public JdwpSession(JdwpConnection conn) {
        this.conn = conn;
        conn.addEventListener(this);
    }

    public JdwpModel.VmInfo getVmInfo() throws IOException, InterruptedException {
        JdwpPacket reply = conn.send(JdwpCommands.CS_VIRTUAL_MACHINE, JdwpCommands.VM_VERSION);
        checkError(reply);
        JdwpPacket.DataReader r = reply.reader();
        String desc = r.readString();
        int major = r.readInt();
        int minor = r.readInt();
        String ver = r.readString();
        String name = r.readString();
        return new JdwpModel.VmInfo(desc, major, minor, ver, name);
    }

    public List<JdwpModel.RemoteClass> getAllClasses() throws IOException, InterruptedException {
        JdwpPacket reply = conn.send(JdwpCommands.CS_VIRTUAL_MACHINE, JdwpCommands.VM_ALL_CLASSES);
        checkError(reply);
        JdwpPacket.DataReader r = reply.reader();
        int count = r.readInt();
        List<JdwpModel.RemoteClass> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int tag = r.readByte();
            long typeId = r.readObjectId(conn.getReferenceTypeIdSize());
            String sig = r.readString();
            int status = r.readInt();
            list.add(new JdwpModel.RemoteClass(typeId, tag, sig, status));
        }
        return list;
    }

    public List<JdwpModel.RemoteClass> getClassesBySignature(String signature) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeString(signature);
        JdwpPacket reply = conn.send(JdwpCommands.CS_VIRTUAL_MACHINE, JdwpCommands.VM_CLASSES_BY_SIGNATURE, w.toBytes());
        checkError(reply);
        JdwpPacket.DataReader r = reply.reader();
        int count = r.readInt();
        List<JdwpModel.RemoteClass> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int tag = r.readByte();
            long typeId = r.readObjectId(conn.getReferenceTypeIdSize());
            int status = r.readInt();
            list.add(new JdwpModel.RemoteClass(typeId, tag, signature, status));
        }
        return list;
    }

    public List<JdwpModel.RemoteMethod> getMethods(long classId) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeObjectId(classId, conn.getReferenceTypeIdSize());
        JdwpPacket reply = conn.send(JdwpCommands.CS_REFERENCE_TYPE, JdwpCommands.RT_METHODS, w.toBytes());
        checkError(reply);
        JdwpPacket.DataReader r = reply.reader();
        int count = r.readInt();
        List<JdwpModel.RemoteMethod> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long methodId = r.readObjectId(conn.getMethodIdSize());
            String name = r.readString();
            String sig = r.readString();
            int mod = r.readInt();
            list.add(new JdwpModel.RemoteMethod(methodId, classId, name, sig, mod));
        }
        return list;
    }

    public List<JdwpModel.LineEntry> getLineTable(long classId, long methodId) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeObjectId(classId, conn.getReferenceTypeIdSize());
        w.writeObjectId(methodId, conn.getMethodIdSize());
        JdwpPacket reply = conn.send(JdwpCommands.CS_METHOD, JdwpCommands.M_LINE_TABLE, w.toBytes());
        if (reply.isError()) return Collections.emptyList();
        JdwpPacket.DataReader r = reply.reader();
        r.readLong();
        r.readLong();
        int count = r.readInt();
        List<JdwpModel.LineEntry> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long idx = r.readLong();
            int line = r.readInt();
            lines.add(new JdwpModel.LineEntry(idx, line));
        }
        return lines;
    }

    public List<JdwpModel.RemoteThread> getAllThreads() throws IOException, InterruptedException {
        JdwpPacket reply = conn.send(JdwpCommands.CS_VIRTUAL_MACHINE, JdwpCommands.VM_ALL_THREADS);
        checkError(reply);
        JdwpPacket.DataReader r = reply.reader();
        int count = r.readInt();
        List<JdwpModel.RemoteThread> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long tid = r.readObjectId(conn.getObjectIdSize());
            String name = getThreadName(tid);
            list.add(new JdwpModel.RemoteThread(tid, name));
        }
        return list;
    }

    private String getThreadName(long threadId) {
        try {
            JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
            w.writeObjectId(threadId, conn.getObjectIdSize());
            JdwpPacket reply = conn.send(JdwpCommands.CS_THREAD_REFERENCE, JdwpCommands.TR_NAME, w.toBytes());
            if (!reply.isError()) 
                return reply.reader().readString();
        } catch (Exception ignored) {}
        return "thread-" + threadId;
    }

    public void refreshThreadStatus(JdwpModel.RemoteThread thread) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeObjectId(thread.threadId, conn.getObjectIdSize());
        JdwpPacket reply = conn.send(JdwpCommands.CS_THREAD_REFERENCE, JdwpCommands.TR_STATUS, w.toBytes());
        if (!reply.isError()) {
            JdwpPacket.DataReader r = reply.reader();
            thread.status = r.readInt();
            thread.suspendStatus = r.readInt();
        }
    }

    public List<JdwpModel.StackFrame> getFrames(long threadId) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeObjectId(threadId, conn.getObjectIdSize());
        w.writeInt(0);
        w.writeInt(-1);
        JdwpPacket reply = conn.send(JdwpCommands.CS_THREAD_REFERENCE, JdwpCommands.TR_FRAMES, w.toBytes());
        checkError(reply);
        JdwpPacket.DataReader r = reply.reader();
        int count = r.readInt();
        List<JdwpModel.StackFrame> frames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long frameId = r.readObjectId(conn.getFrameIdSize());
            int typeTag = r.readByte();
            long classId = r.readObjectId(conn.getReferenceTypeIdSize());
            long methodId = r.readObjectId(conn.getMethodIdSize());
            long codeIndex = r.readLong();
            frames.add(new JdwpModel.StackFrame(frameId, threadId, i, classId, methodId, codeIndex));
        }
        return frames;
    }

    public List<JdwpModel.LocalVariable> getFrameLocals(JdwpModel.StackFrame frame) throws IOException, InterruptedException {
        JdwpPacket.DataWriter slotQuery = new JdwpPacket.DataWriter();
        slotQuery.writeObjectId(frame.classId, conn.getReferenceTypeIdSize());
        slotQuery.writeObjectId(frame.methodId, conn.getMethodIdSize());
        JdwpPacket vtReply = conn.send(JdwpCommands.CS_METHOD, JdwpCommands.M_VARIABLE_TABLE, slotQuery.toBytes());
        if (vtReply.isError()) return Collections.emptyList();
        JdwpPacket.DataReader vr = vtReply.reader();
        vr.readInt();
        int slotCount = vr.readInt();
        List<int[]> slotInfo = new ArrayList<>();
        List<String[]> slotMeta = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            long codeIndex = vr.readLong();
            String name = vr.readString();
            String sig = vr.readString();
            int length = vr.readInt();
            int slot = vr.readInt();
            if (frame.codeIndex >= codeIndex && frame.codeIndex < codeIndex + length) {
                slotInfo.add(new int[]{slot});
                slotMeta.add(new String[]{name, sig});
            }
        }
        if (slotInfo.isEmpty()) 
            return Collections.emptyList();
        JdwpPacket.DataWriter valQuery = new JdwpPacket.DataWriter();
        valQuery.writeObjectId(frame.threadId, conn.getObjectIdSize());
        valQuery.writeObjectId(frame.frameId, conn.getFrameIdSize());
        valQuery.writeInt(slotInfo.size());
        for (int i = 0; i < slotInfo.size(); i++) {
            valQuery.writeInt(slotInfo.get(i)[0]);
            String sig = slotMeta.get(i)[1];
            valQuery.writeByte(sig.isEmpty() ? JdwpTag.OBJECT : (byte) sig.charAt(0));
        }
        JdwpPacket valReply = conn.send(JdwpCommands.CS_STACK_FRAME, JdwpCommands.SF_GET_VALUES, valQuery.toBytes());
        if (valReply.isError()) 
             return Collections.emptyList();
        JdwpPacket.DataReader vvr = valReply.reader();
        int numValues = vvr.readInt();
        List<JdwpModel.LocalVariable> locals = new ArrayList<>(numValues);
        for (int i = 0; i < numValues; i++) {
            byte tag = (byte) vvr.readByte();
            Object value = readTaggedValue(vvr, tag);
            String[] meta = slotMeta.get(i);
            locals.add(new JdwpModel.LocalVariable(slotInfo.get(i)[0], meta[0], meta[1], value));
        }
        return locals;
    }

    private Object readTaggedValue(JdwpPacket.DataReader r, byte tag) throws IOException {
        switch (tag) {
            case JdwpTag.BYTE: 
                return (byte) r.readByte();
            case JdwpTag.BOOLEAN: 
                return r.readBoolean();
            case JdwpTag.CHAR: 
                return (char) r.readShort();
            case JdwpTag.SHORT: 
                return r.readShort();
            case JdwpTag.INT: 
                return r.readInt();
            case JdwpTag.FLOAT: 
                return Float.intBitsToFloat(r.readInt());
            case JdwpTag.LONG: 
                return r.readLong();
            case JdwpTag.DOUBLE: 
                return Double.longBitsToDouble(r.readLong());
            case JdwpTag.VOID: 
                return "<void>";
            case JdwpTag.OBJECT:
            case JdwpTag.STRING:
            case JdwpTag.THREAD:
            case JdwpTag.CLASS_OBJECT:
            case JdwpTag.ARRAY: {
                long id = r.readObjectId(conn.getObjectIdSize());
                if (tag == JdwpTag.STRING) 
                return resolveString(id);
                return JdwpTag.tagName(tag) + "@" + id;
            }
            default:
                return "<tag=0x" + Integer.toHexString(tag & 0xFF) + ">";
        }
    }

    private String resolveString(long stringId) {
        try {
            JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
            w.writeObjectId(stringId, conn.getObjectIdSize());
            JdwpPacket reply = conn.send(9, 10, w.toBytes());
            if (!reply.isError()) return "\"" + reply.reader().readString() + "\"";
        } catch (Exception ignored) {}
        return "String@" + stringId;
    }

    public void suspendVm() throws IOException, InterruptedException {
        checkError(conn.send(JdwpCommands.CS_VIRTUAL_MACHINE, JdwpCommands.VM_SUSPEND));
    }

    public void resumeVm() throws IOException, InterruptedException {
        checkError(conn.send(JdwpCommands.CS_VIRTUAL_MACHINE, JdwpCommands.VM_RESUME));
    }

    public void suspendThread(long threadId) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeObjectId(threadId, conn.getObjectIdSize());
        checkError(conn.send(JdwpCommands.CS_THREAD_REFERENCE, JdwpCommands.TR_SUSPEND, w.toBytes()));
    }

    public void resumeThread(long threadId) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeObjectId(threadId, conn.getObjectIdSize());
        checkError(conn.send(JdwpCommands.CS_THREAD_REFERENCE, JdwpCommands.TR_RESUME, w.toBytes()));
    }

    public JdwpModel.BreakpointRequest setBreakpoint(long classId, long methodId, long codeIndex,
                                                      String className, String methodName) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeByte(JdwpCommands.EK_BREAKPOINT);
        w.writeByte(JdwpCommands.SP_EVENT_THREAD);
        w.writeInt(1);
        w.writeByte(JdwpCommands.MOD_LOCATION_ONLY);
        w.writeByte(1);
        w.writeObjectId(classId, conn.getReferenceTypeIdSize());
        w.writeObjectId(methodId, conn.getMethodIdSize());
        w.writeLong(codeIndex);
        JdwpPacket reply = conn.send(JdwpCommands.CS_EVENT_REQUEST, JdwpCommands.ER_SET, w.toBytes());
        checkError(reply);
        int reqId = reply.reader().readInt();
        JdwpModel.Location loc = new JdwpModel.Location(1, classId, methodId, codeIndex);
        return new JdwpModel.BreakpointRequest(reqId, loc, className, methodName, codeIndex);
    }

    public void clearBreakpoint(int requestId) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeByte(JdwpCommands.EK_BREAKPOINT);
        w.writeInt(requestId);
        checkError(conn.send(JdwpCommands.CS_EVENT_REQUEST, JdwpCommands.ER_CLEAR, w.toBytes()));
    }

    public void clearAllBreakpoints() throws IOException, InterruptedException {
        checkError(conn.send(JdwpCommands.CS_EVENT_REQUEST, JdwpCommands.ER_CLEAR_ALL_BREAKPOINTS));
    }

    public int requestMethodEntry(long classId) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeByte(JdwpCommands.EK_METHOD_ENTRY);
        w.writeByte(JdwpCommands.SP_EVENT_THREAD);
        w.writeInt(1);
        w.writeByte(JdwpCommands.MOD_CLASS_ONLY);
        w.writeObjectId(classId, conn.getReferenceTypeIdSize());
        JdwpPacket reply = conn.send(JdwpCommands.CS_EVENT_REQUEST, JdwpCommands.ER_SET, w.toBytes());
        checkError(reply);
        return reply.reader().readInt();
    }

    public int requestSingleStep(long threadId, int size, int depth) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeByte(JdwpCommands.EK_SINGLE_STEP);
        w.writeByte(JdwpCommands.SP_EVENT_THREAD);
        w.writeInt(1);
        w.writeByte(JdwpCommands.MOD_STEP);
        w.writeObjectId(threadId, conn.getObjectIdSize());
        w.writeInt(size);
        w.writeInt(depth);
        JdwpPacket reply = conn.send(JdwpCommands.CS_EVENT_REQUEST, JdwpCommands.ER_SET, w.toBytes());
        checkError(reply);
        return reply.reader().readInt();
    }

    public void clearEventRequest(int eventKind, int requestId) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeByte(eventKind);
        w.writeInt(requestId);
        conn.send(JdwpCommands.CS_EVENT_REQUEST, JdwpCommands.ER_CLEAR, w.toBytes());
    }

    public boolean[] getCapabilities() throws IOException, InterruptedException {
        JdwpPacket reply = conn.send(JdwpCommands.CS_VIRTUAL_MACHINE, JdwpCommands.VM_CAPABILITIES_NEW);
        if (reply.isError()) 
            return new boolean[0];
        JdwpPacket.DataReader r = reply.reader();
        List<Boolean> flags = new ArrayList<>();
        while (r.available() > 0) {
            flags.add(r.readBoolean());
        }
        boolean[] result = new boolean[flags.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = flags.get(i);
        }
        return result;
    }

    public Set<String> getFieldKeys(long classId) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeObjectId(classId, conn.getReferenceTypeIdSize());
        JdwpPacket reply = conn.send(JdwpCommands.CS_REFERENCE_TYPE, JdwpCommands.RT_FIELDS, w.toBytes());
        checkError(reply);
        JdwpPacket.DataReader r = reply.reader();
        int count = r.readInt();
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            r.readObjectId(conn.getFieldIdSize());
            String name = r.readString();
            String sig = r.readString();
            r.readInt();
            keys.add(name + ":" + sig);
        }
        return keys;
    }

    public void redefineClasses(Map<Long, byte[]> classes) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeInt(classes.size());
        for (Map.Entry<Long, byte[]> entry : classes.entrySet()) {
            w.writeObjectId(entry.getKey(), conn.getReferenceTypeIdSize());
            w.writeInt(entry.getValue().length);
            w.writeBytes(entry.getValue());
        }
        checkError(conn.send(JdwpCommands.CS_VIRTUAL_MACHINE, JdwpCommands.VM_REDEFINE_CLASSES, w.toBytes()));
    }

    public String getClassSignature(long classId) throws IOException, InterruptedException {
        JdwpPacket.DataWriter w = new JdwpPacket.DataWriter();
        w.writeObjectId(classId, conn.getReferenceTypeIdSize());
        JdwpPacket reply = conn.send(JdwpCommands.CS_REFERENCE_TYPE, JdwpCommands.RT_SIGNATURE, w.toBytes());
        if (reply.isError()) 
        return "<unknown>";
        return reply.reader().readString();
    }

    @Override
    public void onEvent(JdwpPacket eventPacket) {
        try {
            JdwpPacket.DataReader r = eventPacket.reader();
            int suspendPolicy = r.readByte();
            int eventCount = r.readInt();
            for (int i = 0; i < eventCount; i++) {
                int eventKind = r.readByte();
                int requestId = r.readInt();
                if (eventKind == JdwpCommands.EK_BREAKPOINT || eventKind == JdwpCommands.EK_SINGLE_STEP) {
                    long threadId = r.readObjectId(conn.getObjectIdSize());
                    int typeTag = r.readByte();
                    long classId = r.readObjectId(conn.getReferenceTypeIdSize());
                    long methodId = r.readObjectId(conn.getMethodIdSize());
                    long index = r.readLong();
                    JdwpModel.Location loc = new JdwpModel.Location(typeTag, classId, methodId, index);
                    JdwpModel.BreakpointHit hit = new JdwpModel.BreakpointHit(requestId, threadId, loc);
                    for (SessionListener l : listeners) {
                        try { l.onBreakpointHit(hit); } catch (Exception ignored) {}
                    }
                } else if (eventKind == JdwpCommands.EK_THREAD_START) {
                    long threadId = r.readObjectId(conn.getObjectIdSize());
                    for (SessionListener l : listeners) {
                        try { l.onThreadEvent(threadId, true); } catch (Exception ignored) {}
                    }
                } else if (eventKind == JdwpCommands.EK_THREAD_DEATH) {
                    long threadId = r.readObjectId(conn.getObjectIdSize());
                    for (SessionListener l : listeners) {
                        try { l.onThreadEvent(threadId, false); } catch (Exception ignored) {}
                    }
                } else if (eventKind == JdwpCommands.EK_VM_DEATH) {
                    for (SessionListener l : listeners) {
                        try { l.onVmDeath(); } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onDisconnected(Exception cause) {
        for (SessionListener l : listeners) {
            try { l.onDisconnected(cause); } catch (Exception ignored) {}
        }
    }

    public void addListener(SessionListener l) { 
        listeners.add(l); 
    }
    
    public void removeListener(SessionListener l) { 
        listeners.remove(l); 
    }

    public JdwpConnection getConnection() { 
        return conn; 
    }

    public void close() {
        try { 
            conn.close(); 
        } catch (Exception ignored) {}
    }

    private void checkError(JdwpPacket reply) throws IOException {
        if (reply.isError()) {
            throw new JdwpException(reply.errorCode);
        }
    }

    public interface SessionListener {
        void onBreakpointHit(JdwpModel.BreakpointHit hit);
        void onThreadEvent(long threadId, boolean started);
        void onVmDeath();
        void onDisconnected(Exception cause);
    }
}
