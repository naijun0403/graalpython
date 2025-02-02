/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.truffle;

import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeCast;
import com.oracle.truffle.api.dsl.TypeCheck;
import com.oracle.truffle.api.dsl.TypeSystem;

/**
 * This type system is supposed to be used in builtin nodes to reduce the number of specializations
 * due to type combinations. A node that needs to handle all combinations of Python {@code int} and
 * {@code float} types can just use the most general primitive type, i.e., in case of Python type
 * {@code int} use Java {@code long} and in case of Python type {@code float} use Java
 * {@code double}. {@code PInt} needs to be treated separately because of its arbitrary precision.
 *
 * Only use in nodes where it is known that {@code PInt} and {@code PFloat} objects are not
 * subclassed!
 */
@TypeSystem
public abstract class PythonArithmeticTypes {

    @ImplicitCast
    public static double PFloatToDouble(PFloat value) {
        // NOTE: That's correct because we just use it in arithmetic operations where CPython also
        // access the value ('f_val') directly. So, even if the object is subclassed, it is ignored.
        return value.getValue();
    }

    @ImplicitCast
    public static int booleanToInt(boolean value) {
        return value ? 1 : 0;
    }

    @ImplicitCast
    public static long booleanToLong(boolean value) {
        return value ? 1 : 0;
    }

    @ImplicitCast
    public static long intToLong(int value) {
        return value;
    }

    @TypeCheck(PythonNativeObject.class)
    public static boolean isNativeObject(Object object) {
        return PythonNativeObject.isInstance(object);
    }

    @TypeCast(PythonNativeObject.class)
    public static PythonNativeObject asNativeObject(Object object) {
        return PythonNativeObject.cast(object);
    }

    @TypeCheck(PythonNativeClass.class)
    public static boolean isNativeClass(Object object) {
        return PGuards.isNativeClass(object);
    }

    @TypeCast(PythonNativeClass.class)
    public static PythonNativeClass asNativeClass(Object object) {
        return PythonNativeClass.cast(object);
    }
}
