/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.annotations.ClinicConverterFactory.ArgumentIndex;
import com.oracle.graal.python.annotations.ClinicConverterFactory.ArgumentName;
import com.oracle.graal.python.annotations.ClinicConverterFactory.BuiltinName;
import com.oracle.graal.python.annotations.ClinicConverterFactory.DefaultValue;
import com.oracle.graal.python.annotations.ClinicConverterFactory.UseDefaultForNone;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.ClinicTestsClinicProviders.MyBuiltinWithCustomConvertorClinicProviderGen;
import com.oracle.graal.python.builtins.modules.ClinicTestsClinicProviders.MyBuiltinWithDefaultValuesClinicProviderGen;
import com.oracle.graal.python.builtins.modules.ClinicTestsFactory.MyBuiltinWithCustomConvertorNodeGen;
import com.oracle.graal.python.builtins.modules.ClinicTestsFactory.MyBuiltinWithDefaultValuesNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class ClinicTests {
    public static final boolean EXPECT_TYPE_ERROR = true;
    public static final int DEFAULT_VALUE_42 = 42;

    @Before
    public void setUp() {
        PythonTests.enterContext();
    }

    @After
    public void tearDown() {
        PythonTests.closeContext();
    }

    @Builtin(name = "mybuiltin", parameterNames = {"a", "b"})
    @ArgumentClinic(name = "a", conversion = ClinicConversion.Int, useDefaultForNone = true, defaultValue = "DEFAULT_VALUE_42")
    @ArgumentClinic(name = "b", conversion = ClinicConversion.Int, defaultValue = "7")
    public abstract static class MyBuiltinWithDefaultValues extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MyBuiltinWithDefaultValuesClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doDefaults(int a, int b) {
            assertEquals(42, a);
            assertEquals(7, b);
            return "doDefaults";
        }
    }

    @Test
    public void testDefaultValues() {
        CallTarget callTarget = createCallTarget(MyBuiltinWithDefaultValuesNodeGen.create());
        assertEquals("doDefaults", callTarget.call(PNone.NO_VALUE, PNone.NO_VALUE));
        assertEquals("doDefaults", callTarget.call(PNone.NONE, PNone.NO_VALUE));
        assertEquals("typeError", callTarget.call(PNone.NONE, PNone.NONE, EXPECT_TYPE_ERROR));
    }

    public static final class MyCustomConvertor extends ArgumentCastNode {
        private final String expectedArgValue;

        public MyCustomConvertor(String expectedArgValue) {
            this.expectedArgValue = expectedArgValue;
        }

        @Override
        public Object execute(VirtualFrame frame, Object value) {
            assertEquals(expectedArgValue, value);
            return 42;
        }

        @ClinicConverterFactory
        public static MyCustomConvertor createWithExpected(@ArgumentName String name, @ArgumentIndex int index,
                        @BuiltinName String builtinName, @DefaultValue Object defaultValue, @UseDefaultForNone boolean useDefaultForNone,
                        String extraArgument, int extraIntArg) {
            assertEquals("mybuiltin2", builtinName);
            assertEquals(42, extraIntArg);
            switch (name) {
                case "a":
                    assertEquals(0, index);
                    assertEquals(42, defaultValue);
                    assertTrue(useDefaultForNone);
                    assertEquals("a_extra", extraArgument);
                    return new MyCustomConvertor("a_input");
                case "b":
                    assertEquals(1, index);
                    assertEquals(7, defaultValue);
                    assertFalse(useDefaultForNone);
                    assertEquals("b_extra", extraArgument);
                    return new MyCustomConvertor("b_input");
                default:
                    throw new AssertionError(name);
            }
        }
    }

    @Builtin(name = "mybuiltin2", parameterNames = {"a", "b"})
    @ArgumentClinic(name = "a", conversionClass = MyCustomConvertor.class, useDefaultForNone = true, defaultValue = "42", args = {"A_ARG", "42"})
    @ArgumentClinic(name = "b", conversionClass = MyCustomConvertor.class, useDefaultForNone = false, defaultValue = "7", args = {"\"b_extra\"", "B_INT_ARG"})
    public abstract static class MyBuiltinWithCustomConvertor extends PythonBinaryClinicBuiltinNode {
        public static final String A_ARG = "a_extra";
        public static final int B_INT_ARG = 42;

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MyBuiltinWithCustomConvertorClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doDefaults(int a, int b) {
            assertEquals(42, a);
            assertEquals(42, b);
            return "done";
        }
    }

    @Test
    public void testCustomConvertor() {
        CallTarget callTarget = createCallTarget(MyBuiltinWithCustomConvertorNodeGen.create());
        assertEquals("done", callTarget.call("a_input", "b_input"));
    }

    private static CallTarget createCallTarget(PythonBinaryClinicBuiltinNode node) {
        return new BinaryBuiltinRoot(node).getCallTarget();
    }

    private static final class BinaryBuiltinRoot extends RootNode {
        @Child PythonBinaryClinicBuiltinNode node;

        protected BinaryBuiltinRoot(PythonBinaryClinicBuiltinNode node) {
            super(null);
            this.node = node;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return node.execute(frame, frame.getArguments()[0], frame.getArguments()[1]);
            } catch (PException ex) {
                boolean expectTypeError = frame.getArguments().length >= 3 && (boolean) frame.getArguments()[2];
                if (expectTypeError) {
                    ex.expect(PythonBuiltinClassType.TypeError, IsBuiltinClassProfile.getUncached());
                    return "typeError";
                } else {
                    throw ex;
                }
            }
        }
    }
}
