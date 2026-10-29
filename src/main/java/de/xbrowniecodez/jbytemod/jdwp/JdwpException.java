package de.xbrowniecodez.jbytemod.jdwp;

import java.io.IOException;

public class JdwpException extends IOException {

    private final int code;

    public JdwpException(int code) {
        super("JDWP error " + code + ": " + describe(code));
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static String describe(int code) {
        switch (code) {
            case 10: 
               return "invalid thread";
            case 13: 
               return "thread not suspended";
            case 20: 
               return "invalid object";
            case 21: 
               return "invalid class";
            case 22: 
               return "class not prepared";
            case 23: 
               return "invalid method id";
            case 24: 
               return "invalid location";
            case 30: 
               return "invalid frame id";
            case 35: 
               return "invalid slot";
            case 41: 
               return "not found";
            case 60: 
               return "invalid class format";
            case 61: 
               return "circular class definition";
            case 62: 
               return "new class fails bytecode verification";
            case 63: 
               return "the VM cannot add methods";
            case 64: 
               return "the VM cannot change fields (schema change)";
            case 65: 
               return "invalid type state";
            case 66: 
               return "the VM cannot change the class hierarchy";
            case 67: 
               return "the VM cannot delete methods";
            case 68: 
               return "class file version is not supported by the target VM";
            case 69: 
               return "class name does not match the loaded class";
            case 70: 
               return "the VM cannot change class modifiers";
            case 71: 
               return "the VM cannot change method modifiers";
            case 72: 
               return "the VM cannot change class attributes";
            case 99: 
               return "not implemented";
            case 100: 
               return "null pointer";
            case 101: 
               return "absent information";
            case 103: 
               return "illegal argument";
            case 110: 
               return "out of memory";
            case 111: 
               return "access denied";
            case 112: 
               return "VM is dead";
            case 113: 
               return "internal error";
            default: 
            return "unknown error";
        }
    }
}
