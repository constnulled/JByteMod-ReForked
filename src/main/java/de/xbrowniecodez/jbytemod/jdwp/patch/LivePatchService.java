package de.xbrowniecodez.jbytemod.jdwp.patch;

import de.xbrowniecodez.jbytemod.asm.CustomClassWriter;
import de.xbrowniecodez.jbytemod.jdwp.JdwpException;
import de.xbrowniecodez.jbytemod.jdwp.JdwpModel;
import de.xbrowniecodez.jbytemod.jdwp.JdwpSession;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class LivePatchService {

    private static final int CAP_CAN_REDEFINE = 7;
    private static final int CAP_CAN_ADD_METHOD = 8;
    private static final int CAP_CAN_UNRESTRICTED = 9;

    public enum State { 
       PENDING, 
       PATCHED, 
       SKIPPED, 
       FAILED
    }

    public static final class Result {
        public final String className;
        public final State state;
        public final String message;

        public Result(String className, State state, String message) {
            this.className = className;
            this.state = state;
            this.message = message;
        }
    }

    private static final class Prepared {
        final String name;
        final List<Long> typeIds;
        final byte[] bytes;
        final long fingerprint;

        Prepared(String name, List<Long> typeIds, byte[] bytes, long fingerprint) {
            this.name = name;
            this.typeIds = typeIds;
            this.bytes = bytes;
            this.fingerprint = fingerprint;
        }
    }

    private final Map<String, Long> baseline = new ConcurrentHashMap<>();
    private final Map<String, Long> applied = new ConcurrentHashMap<>();

    public int captureBaseline(Map<String, ClassNode> classes) {
        baseline.clear();
        applied.clear();
        if (classes == null) return 0;
        for (Map.Entry<String, ClassNode> entry : new ArrayList<>(classes.entrySet())) {
            try {
                baseline.put(entry.getKey(), fingerprint(entry.getValue()));
            } catch (RuntimeException ignored) {
            }
        }
        return baseline.size();
    }

    public boolean hasBaseline() {
        return !baseline.isEmpty();
    }

    public List<String> findPending(Map<String, ClassNode> classes) {
        List<String> pending = new ArrayList<>();
        if (classes == null) return pending;
        for (Map.Entry<String, ClassNode> entry : new ArrayList<>(classes.entrySet())) {
            String name = entry.getKey();
            Long base = baseline.get(name);
            if (base == null) continue;
            long current;
            try {
                current = fingerprint(entry.getValue());
            } catch (RuntimeException e) {
                continue;
            }
            Long last = applied.get(name);
            if (last == null) last = base;
            if (current != last) pending.add(name);
        }
        Collections.sort(pending);
        return pending;
    }

    public List<Result> patch(JdwpSession session, Map<String, ClassNode> classes, Collection<String> names, int writerFlags)
            throws IOException, InterruptedException {
        boolean[] caps = session.getCapabilities();
        if (!capability(caps, CAP_CAN_REDEFINE)) {
            throw new IOException("The target VM does not support class redefinition");
        }
        boolean canAddMethod = capability(caps, CAP_CAN_ADD_METHOD);
        boolean canUnrestricted = capability(caps, CAP_CAN_UNRESTRICTED);

        List<Result> results = new ArrayList<>();
        Map<String, Prepared> ready = new LinkedHashMap<>();

        for (String name : names) {
            ClassNode node = classes == null ? null : classes.get(name);
            if (node == null) {
                results.add(new Result(name, State.FAILED, "Class is not in the loaded archive"));
                continue;
            }
            List<JdwpModel.RemoteClass> remote = session.getClassesBySignature("L" + name + ";");
            if (remote.isEmpty()) {
                results.add(new Result(name, State.SKIPPED, "Not loaded in the target VM"));
                continue;
            }
            String problem = checkSchema(session, remote.get(0).typeId, node, canAddMethod, canUnrestricted);
            if (problem != null) {
                results.add(new Result(name, State.FAILED, problem));
                continue;
            }
            byte[] bytes;
            long fingerprint;
            try {
                bytes = serialize(node, writerFlags);
                fingerprint = fingerprint(node);
            } catch (Exception | StackOverflowError e) {
                results.add(new Result(name, State.FAILED, "Could not write class: " + e));
                continue;
            }
            List<Long> ids = new ArrayList<>();
            for (JdwpModel.RemoteClass rc : remote) ids.add(rc.typeId);
            ready.put(name, new Prepared(name, ids, bytes, fingerprint));
        }

        if (ready.isEmpty()) return results;

        try {
            session.redefineClasses(toRequest(ready.values()));
            for (Prepared p : ready.values()) {
                applied.put(p.name, p.fingerprint);
                results.add(new Result(p.name, State.PATCHED, "Redefined in the target VM"));
            }
        } catch (IOException batchError) {
            if (ready.size() == 1) {
                Prepared p = ready.values().iterator().next();
                results.add(new Result(p.name, State.FAILED, describe(batchError)));
            } else {
                for (Prepared p : ready.values()) {
                    try {
                        session.redefineClasses(toRequest(Collections.singletonList(p)));
                        applied.put(p.name, p.fingerprint);
                        results.add(new Result(p.name, State.PATCHED, "Redefined in the target VM"));
                    } catch (IOException single) {
                        results.add(new Result(p.name, State.FAILED, describe(single)));
                    }
                }
            }
        }
        return results;
    }

    private static Map<Long, byte[]> toRequest(Collection<Prepared> prepared) {
        Map<Long, byte[]> request = new LinkedHashMap<>();
        for (Prepared p : prepared) {
            for (Long id : p.typeIds) request.put(id, p.bytes);
        }
        return request;
    }

    private static String describe(IOException e) {
        if (e instanceof JdwpException) return e.getMessage();
        return String.valueOf(e.getMessage());
    }

    private static boolean capability(boolean[] caps, int index) {
        return caps.length <= index || caps[index];
    }

    private static String checkSchema(JdwpSession session, long typeId, ClassNode node,
                                      boolean canAddMethod, boolean canUnrestricted)
            throws IOException, InterruptedException {
        Set<String> remoteMethods = new HashSet<>();
        for (JdwpModel.RemoteMethod m : session.getMethods(typeId)) {
            remoteMethods.add(m.name + m.signature);
        }
        Set<String> localMethods = new HashSet<>();
        for (MethodNode m : node.methods) {
            localMethods.add(m.name + m.desc);
        }
        Set<String> remoteFields = session.getFieldKeys(typeId);
        Set<String> localFields = new HashSet<>();
        for (FieldNode f : node.fields) {
            localFields.add(f.name + ":" + f.desc);
        }

        boolean addedMethods = !remoteMethods.containsAll(localMethods);
        boolean removedMethods = !localMethods.containsAll(remoteMethods);
        boolean fieldsChanged = !remoteFields.equals(localFields);

        if (addedMethods && !canAddMethod) {
            return "Adds methods, which the target VM cannot do. Only method bodies can be patched";
        }
        if (removedMethods && !canUnrestricted) {
            return "Removes or renames methods, which the target VM cannot do. Only method bodies can be patched";
        }
        if (fieldsChanged && !canUnrestricted) {
            return "Changes fields, which the target VM cannot do. Only method bodies can be patched";
        }
        return null;
    }

    public static byte[] serialize(ClassNode node, int flags) {
        CustomClassWriter writer = new CustomClassWriter(flags);
        node.accept(writer);
        return writer.toByteArray();
    }

    public static long fingerprint(ClassNode cn) {
        long h = 1125899906842597L;
        h = mix(h, cn.version);
        h = mix(h, cn.access);
        h = mix(h, cn.name);
        h = mix(h, cn.superName);
        h = mix(h, cn.interfaces == null ? 0 : cn.interfaces.hashCode());
        if (cn.fields != null) {
            for (FieldNode f : cn.fields) {
                h = mix(h, f.name);
                h = mix(h, f.desc);
                h = mix(h, f.access);
                h = mix(h, f.value == null ? 0 : f.value.hashCode());
            }
        }
        if (cn.methods != null) {
            for (MethodNode m : cn.methods) {
                h = mix(h, m.name);
                h = mix(h, m.desc);
                h = mix(h, m.access);
                h = mix(h, m.maxStack);
                h = mix(h, m.maxLocals);
                InsnList insns = m.instructions;
                if (insns != null) {
                    for (AbstractInsnNode insn = insns.getFirst(); insn != null; insn = insn.getNext()) {
                        h = mixInsn(h, insns, insn);
                    }
                }
                if (m.tryCatchBlocks != null) {
                    for (TryCatchBlockNode t : m.tryCatchBlocks) {
                        h = mix(h, t.type);
                        h = mix(h, insns.indexOf(t.start));
                        h = mix(h, insns.indexOf(t.end));
                        h = mix(h, insns.indexOf(t.handler));
                    }
                }
            }
        }
        return h;
    }

    private static long mixInsn(long h, InsnList insns, AbstractInsnNode insn) {
        h = mix(h, insn.getOpcode());
        h = mix(h, insn.getType());
        if (insn instanceof VarInsnNode) {
            h = mix(h, ((VarInsnNode) insn).var);
        } else if (insn instanceof IntInsnNode) {
            h = mix(h, ((IntInsnNode) insn).operand);
        } else if (insn instanceof JumpInsnNode) {
            h = mix(h, insns.indexOf(((JumpInsnNode) insn).label));
        } else if (insn instanceof LdcInsnNode) {
            Object cst = ((LdcInsnNode) insn).cst;
            h = mix(h, cst == null ? 0 : cst.hashCode());
        } else if (insn instanceof MethodInsnNode) {
            MethodInsnNode mi = (MethodInsnNode) insn;
            h = mix(h, mi.owner);
            h = mix(h, mi.name);
            h = mix(h, mi.desc);
            h = mix(h, mi.itf ? 1 : 0);
        } else if (insn instanceof FieldInsnNode) {
            FieldInsnNode fi = (FieldInsnNode) insn;
            h = mix(h, fi.owner);
            h = mix(h, fi.name);
            h = mix(h, fi.desc);
        } else if (insn instanceof TypeInsnNode) {
            h = mix(h, ((TypeInsnNode) insn).desc);
        } else if (insn instanceof IincInsnNode) {
            h = mix(h, ((IincInsnNode) insn).var);
            h = mix(h, ((IincInsnNode) insn).incr);
        } else if (insn instanceof MultiANewArrayInsnNode) {
            h = mix(h, ((MultiANewArrayInsnNode) insn).desc);
            h = mix(h, ((MultiANewArrayInsnNode) insn).dims);
        } else if (insn instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode di = (InvokeDynamicInsnNode) insn;
            h = mix(h, di.name);
            h = mix(h, di.desc);
            h = mix(h, di.bsm == null ? 0 : di.bsm.hashCode());
            h = mix(h, Arrays.hashCode(di.bsmArgs));
        } else if (insn instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode ts = (TableSwitchInsnNode) insn;
            h = mix(h, ts.min);
            h = mix(h, ts.max);
            h = mix(h, insns.indexOf(ts.dflt));
            for (LabelNode l : ts.labels) h = mix(h, insns.indexOf(l));
        } else if (insn instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode ls = (LookupSwitchInsnNode) insn;
            h = mix(h, insns.indexOf(ls.dflt));
            h = mix(h, ls.keys.hashCode());
            for (LabelNode l : ls.labels) h = mix(h, insns.indexOf(l));
        }
        return h;
    }

    private static long mix(long h, int v) {
        return (h ^ v) * 1099511628211L;
    }

    private static long mix(long h, String s) {
        return mix(h, s == null ? 0 : s.hashCode());
    }
}
