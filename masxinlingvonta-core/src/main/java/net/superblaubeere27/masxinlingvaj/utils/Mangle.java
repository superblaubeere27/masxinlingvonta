/*
 * Copyright (c) 2002, 2006, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


package net.superblaubeere27.masxinlingvaj.utils;

import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;

/**
 * JDK 8's Mangle method from javah
 */
public class Mangle {

    public static String mangle(String name, int mtype) {
        StringBuffer result = new StringBuffer(100);
        int length = name.length();

        for (int i = 0; i < length; i++) {
            char ch = name.charAt(i);
            if (isalnum(ch)) {
                result.append(ch);
            } else if ((ch == '/') &&
                    mtype == Mangle.Type.CLASS) {
                result.append('_');
            } else if ((ch == '$') &&
                    mtype == Mangle.Type.CLASS) {
                result.append('_');
                result.append('_');
            } else if (ch == '_' && mtype == Mangle.Type.FIELDSTUB) {
                result.append('_');
            } else if (ch == '_' && mtype == Mangle.Type.CLASS) {
                result.append('_');
            } else if (mtype == Mangle.Type.JNI) {
                String esc = null;
                if (ch == '_')
                    esc = "_1";
                else if (ch == '/')
                    esc = "_";
                else if (ch == ';')
                    esc = "_2";
                else if (ch == '[')
                    esc = "_3";
                if (esc != null) {
                    result.append(esc);
                } else {
                    result.append(mangleChar(ch));
                }
            } else if (mtype == Mangle.Type.SIGNATURE) {
                if (isprint(ch)) {
                    result.append(ch);
                } else {
                    result.append(mangleChar(ch));
                }
            } else {
                result.append(mangleChar(ch));
            }
        }

        return result.toString();
    }

    public static String mangleMethod(MethodOrFieldIdentifier identifier) {
        return mangleMethod(identifier.getOwner(), identifier.getName(), identifier.getDesc(), Type.METHOD_JNI_LONG);
    }

    public static String mangleMethod(String className, String methodName, String methodDesc, int mtype) {
        StringBuffer result = new StringBuffer(100);
        result.append("Java_");

        if (mtype == Mangle.Type.METHOD_JDK_1) {
            result.append(mangle(className, Mangle.Type.CLASS));
            result.append('_');
            result.append(mangle(methodName,
                    Mangle.Type.FIELD));
            result.append("_stub");
            return result.toString();
        }

        /* JNI */
        result.append(mangle(className, Mangle.Type.JNI));
        result.append('_');
        result.append(mangle(methodName,
                Mangle.Type.JNI));

        if (mtype == Mangle.Type.METHOD_JNI_LONG) {
            result.append("__");
            String sig = methodDesc;
            sig = sig.substring(1);
            sig = sig.substring(0, sig.lastIndexOf(')'));
            result.append(mangle(sig, Mangle.Type.JNI));
        }

        return result.toString();
    }

    public static String mangleChar(char ch) {
        String s = Integer.toHexString(ch);
        int nzeros = 5 - s.length();
        char[] result = new char[6];
        result[0] = '_';
        for (int i = 1; i <= nzeros; i++)
            result[i] = '0';
        for (int i = nzeros + 1, j = 0; i < 6; i++, j++)
            result[i] = s.charAt(j);
        return new String(result);
    }

    /* Warning: Intentional ASCII operation. */
    private static boolean isalnum(char ch) {
        /* quick test */
        return ((ch >= 'A' && ch <= 'Z') ||
                (ch >= 'a' && ch <= 'z') ||
                (ch >= '0' && ch <= '9'));
    }

    /* Warning: Intentional ASCII operation. */
    private static boolean isprint(char ch) {
        return ch >= 32 && ch <= 126;
    }

    public static class Type {

        public static final int CLASS = 1;
        public static final int FIELDSTUB = 2;
        public static final int FIELD = 3;
        public static final int JNI = 4;
        public static final int SIGNATURE = 5;
        public static final int METHOD_JDK_1 = 6;
        public static final int METHOD_JNI_SHORT = 7;
        public static final int METHOD_JNI_LONG = 8;
    }
}
