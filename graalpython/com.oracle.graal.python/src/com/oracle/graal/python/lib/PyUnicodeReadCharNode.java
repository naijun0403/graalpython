/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

/**
 * Equivalent of CPython's {@code PyLong_AsDouble}. Converts an object into a Java double. Raises
 * {@code OverflowError} on overflow.
 */
@GenerateUncached
public abstract class PyUnicodeReadCharNode extends PNodeWithContext {
    public abstract int execute(Object object, long lindex);

    @Specialization
    int doGeneric(Object type, long lindex,
                    @Cached CastToJavaStringNode castToJavaStringNode,
                    @Cached PRaiseNode raiseNode) {
        try {
            String s = castToJavaStringNode.execute(type);
            int index = PInt.intValueExact(lindex);
            // avoid StringIndexOutOfBoundsException
            if (index < 0 || index >= PString.length(s)) {
                throw raiseNode.raise(IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
            }
            return PString.charAt(s, index);
        } catch (CannotCastException e) {
            throw raiseNode.raise(TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
        } catch (OverflowException e) {
            throw raiseNode.raise(IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
        }
    }

    public static PyUnicodeReadCharNode create() {
        return PyUnicodeReadCharNodeGen.create();
    }
}
