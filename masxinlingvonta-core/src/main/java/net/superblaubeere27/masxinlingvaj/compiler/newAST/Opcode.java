package net.superblaubeere27.masxinlingvaj.compiler.newAST;

import java.util.concurrent.atomic.AtomicInteger;

public interface Opcode {
    String[] OPNAMES = {
            "PHI"
    };
    AtomicInteger opcodeCounter = new AtomicInteger();
    int PHI_STORE = 0;
    int LOCAL_LOAD = 1;
    int PHI = 2;
    int GET_PARAM = 3;
    int LOCAL_STORE = 4;
    int UNCONDITIONAL_BRANCH = 5;
    int CONDITIONAL_BRANCH = 6;
    int CONST_NULL = 7;
    int CONST_INT = 8;
    int CONST_LONG = 9;
    int CONST_FLOAT = 10;
    int CONST_DOUBLE = 11;
    int CONST_STRING = 12;
    int CONST_TYPE = 13;
    int RET_VOID = 14;
    int RET = 15;
    int BINOP = 16;
    int CATCH = 17;
    int ARRAY_STORE = 18;
    int ARRAY_LOAD = 19;
    int NEGATION = 20;
    int PRIMITIVE_CAST = 21;
    int INTEGER_COMPARE = 22;
    int FLOAT_COMPARE = 23;
    int OBJECT_COMPARE = 24;
    int SWITCH = 25;
    int GET_STATIC_FIELD = 26;
    int GET_FIELD = 27;
    int PUT_STATIC_FIELD = 28;
    int PUT_FIELD = 29;
    int ALLOC_OBJECT = 30;
    int ALLOC_ARRAY = 31;
    int CHECKCAST = 32;
    int INSTANCEOF = 33;
    int INVOKE_STATIC = 34;
    int INVOKE_INSTANCE = 35;
    int ARRAY_LENGTH = 36;
    int MONITOR = 37;
    int EXPR = 38;
    int EXCEPTION_CHECK = 39;
    int CREATE_REF = 40;
    int DELETE_REF = 41;
    int CLEAR_EXCEPTION = 42;

    static String opname(int op) {
        return OPNAMES[op];
    }
}
