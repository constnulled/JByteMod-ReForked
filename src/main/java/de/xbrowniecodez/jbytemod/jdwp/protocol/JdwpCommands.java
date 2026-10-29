package de.xbrowniecodez.jbytemod.jdwp.protocol;

public final class JdwpCommands {

    private JdwpCommands() {}

    public static final int CS_VIRTUAL_MACHINE = 1;
    public static final int VM_VERSION = 1;
    public static final int VM_CLASSES_BY_SIGNATURE = 2;
    public static final int VM_ALL_CLASSES = 3;
    public static final int VM_ALL_THREADS = 4;
    public static final int VM_SUSPEND = 8;
    public static final int VM_RESUME = 9;
    public static final int VM_EXIT = 10;
    public static final int VM_CAPABILITIES = 12;
    public static final int VM_ID_SIZES = 7;
    public static final int VM_CAPABILITIES_NEW = 17;
    public static final int VM_REDEFINE_CLASSES = 18;

    public static final int CS_REFERENCE_TYPE = 2;
    public static final int RT_SIGNATURE = 1;
    public static final int RT_CLASS_LOADER = 2;
    public static final int RT_MODIFIERS = 3;
    public static final int RT_FIELDS = 4;
    public static final int RT_METHODS = 5;
    public static final int RT_SOURCE_FILE = 7;
    public static final int RT_INTERFACES = 10;
    public static final int RT_CLASS_OBJECT = 11;

    public static final int CS_CLASS_TYPE = 3;
    public static final int CT_SUPERCLASS = 1;

    public static final int CS_METHOD = 6;
    public static final int M_LINE_TABLE = 1;
    public static final int M_VARIABLE_TABLE = 2;
    public static final int M_BYTECODES = 3;

    public static final int CS_OBJECT_REFERENCE = 9;
    public static final int OR_REFERENCE_TYPE = 1;
    public static final int OR_GET_VALUES = 2;

    public static final int CS_THREAD_REFERENCE = 11;
    public static final int TR_NAME = 1;
    public static final int TR_SUSPEND = 2;
    public static final int TR_RESUME = 3;
    public static final int TR_STATUS = 4;
    public static final int TR_THREAD_GROUP = 5;
    public static final int TR_FRAMES = 6;
    public static final int TR_FRAME_COUNT = 7;

    public static final int CS_STACK_FRAME = 16;
    public static final int SF_GET_VALUES = 1;

    public static final int CS_EVENT_REQUEST = 15;
    public static final int ER_SET = 1;
    public static final int ER_CLEAR = 2;
    public static final int ER_CLEAR_ALL_BREAKPOINTS = 3;

    public static final int CS_EVENT = 64;
    public static final int EV_COMPOSITE = 100;

    public static final int EK_SINGLE_STEP = 1;
    public static final int EK_BREAKPOINT = 2;
    public static final int EK_FRAME_POP = 3;
    public static final int EK_EXCEPTION = 4;
    public static final int EK_USER_DEFINED = 5;
    public static final int EK_THREAD_START = 6;
    public static final int EK_THREAD_DEATH = 7;
    public static final int EK_CLASS_PREPARE = 8;
    public static final int EK_CLASS_UNLOAD = 9;
    public static final int EK_CLASS_LOAD = 10;
    public static final int EK_FIELD_ACCESS = 20;
    public static final int EK_FIELD_MODIFICATION = 21;
    public static final int EK_EXCEPTION_CATCH = 30;
    public static final int EK_METHOD_ENTRY = 40;
    public static final int EK_METHOD_EXIT = 41;
    public static final int EK_VM_START = 90;
    public static final int EK_VM_DEATH = 99;

    public static final int SP_NONE = 0;
    public static final int SP_EVENT_THREAD = 1;
    public static final int SP_ALL = 2;

    public static final int MOD_COUNT = 1;
    public static final int MOD_CONDITIONAL = 2;
    public static final int MOD_THREAD_ONLY = 3;
    public static final int MOD_CLASS_ONLY = 4;
    public static final int MOD_CLASS_MATCH = 5;
    public static final int MOD_CLASS_EXCLUDE = 6;
    public static final int MOD_LOCATION_ONLY = 7;
    public static final int MOD_EXCEPTION_ONLY = 8;
    public static final int MOD_FIELD_ONLY = 9;
    public static final int MOD_STEP = 10;
    public static final int MOD_INSTANCE_ONLY = 11;

    public static final int STEP_SIZE_MIN = 0;
    public static final int STEP_SIZE_LINE = 1;
    public static final int STEP_DEPTH_INTO = 0;
    public static final int STEP_DEPTH_OVER = 1;
    public static final int STEP_DEPTH_OUT = 2;
}
