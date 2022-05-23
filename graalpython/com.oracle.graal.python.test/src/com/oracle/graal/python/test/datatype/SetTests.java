/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.test.datatype;

import static com.oracle.graal.python.test.PythonTests.assertLastLineError;
import static com.oracle.graal.python.test.PythonTests.assertPrints;

import org.junit.Test;

public class SetTests {

    @Test
    public void setNone() {
        String source = "set(None)";
        assertLastLineError("TypeError: 'NoneType' object is not iterable\n", source);
    }

    @Test
    public void setClear() {
        String source = "s = set([1,2,3])\n" + //
                        "print(s)\n" + //
                        "s.clear()\n" + //
                        "print(s)";
        assertPrints("{1, 2, 3}\nset()\n", source);
    }

    @Test
    public void setAdd() {
        String source = "s = set([1,2,3])\n" + //
                        "print(s)\n" + //
                        "s.add(4)\n" + //
                        "print(s)";
        assertPrints("{1, 2, 3}\n{1, 2, 3, 4}\n", source);
    }

    @Test
    public void setLiteral1() {
        String source = "print({1, 2, 3})\n";
        assertPrints("{1, 2, 3}\n", source);
    }

    @Test
    public void setLiteral2() {
        String source = "s1 = {1, 2, 3}\n" +
                        "s2 = {*s1, 4}\n" +
                        "print(s2)\n";
        assertPrints("{1, 2, 3, 4}\n", source);
    }

    @Test
    public void setLiteral3() {
        String source = "s1 = {1, 2, 3}\n" +
                        "s2 = {0, *s1, 4}\n" +
                        "print(s2)\n";
        assertPrints("{0, 1, 2, 3, 4}\n", source);
    }
}
