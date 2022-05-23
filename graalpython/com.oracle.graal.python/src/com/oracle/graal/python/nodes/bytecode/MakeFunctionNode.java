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
package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.source.Source;

public abstract class MakeFunctionNode extends PNodeWithContext {
    private final RootCallTarget callTarget;
    private final CodeUnit code;
    private final Signature signature;
    private final PCode cachedCode;
    private final String doc;

    private final Assumption sharedCodeStableAssumption = Truffle.getRuntime().createAssumption("shared code stable assumption");
    private final Assumption sharedDefaultsStableAssumption = Truffle.getRuntime().createAssumption("shared defaults stable assumption");

    public abstract int execute(Object globals, int initialStackTop, Frame localFrame, int flags);

    public MakeFunctionNode(RootCallTarget callTarget, CodeUnit code, Signature signature, PCode cachedCode, String doc) {
        this.callTarget = callTarget;
        this.code = code;
        this.signature = signature;
        this.cachedCode = cachedCode;
        this.doc = doc;
    }

    @Specialization
    int makeFunction(Object globals, int initialStackTop, Frame localFrame, int flags,
                    @Cached PythonObjectFactory factory,
                    @CachedLibrary(limit = "1") DynamicObjectLibrary dylib) {
        int stackTop = initialStackTop;

        PCode codeObj = cachedCode;
        if (codeObj == null) {
            // Multi-context mode
            codeObj = createCode(factory, code, callTarget, signature);
        }

        PCell[] closure = null;
        Object annotations = null;
        PKeyword[] kwdefaults = null;
        Object[] defaults = null;

        if ((flags & CodeUnit.HAS_CLOSURE) != 0) {
            closure = (PCell[]) localFrame.getObject(stackTop);
            localFrame.setObject(stackTop--, null);
        }
        if ((flags & CodeUnit.HAS_ANNOTATIONS) != 0) {
            annotations = localFrame.getObject(stackTop);
            localFrame.setObject(stackTop--, null);
        }
        if ((flags & CodeUnit.HAS_KWONLY_DEFAULTS) != 0) {
            kwdefaults = (PKeyword[]) localFrame.getObject(stackTop);
            localFrame.setObject(stackTop--, null);
        }
        if ((flags & CodeUnit.HAS_DEFAULTS) != 0) {
            defaults = (Object[]) localFrame.getObject(stackTop);
            localFrame.setObject(stackTop--, null);
        }

        Assumption codeStableAssumption;
        Assumption defaultsStableAssumption;
        if (CompilerDirectives.inCompiledCode()) {
            codeStableAssumption = sharedCodeStableAssumption;
            defaultsStableAssumption = sharedDefaultsStableAssumption;
        } else {
            codeStableAssumption = Truffle.getRuntime().createAssumption();
            defaultsStableAssumption = Truffle.getRuntime().createAssumption();
        }
        PFunction function = factory.createFunction(code.name, code.qualname, codeObj, (PythonObject) globals, defaults, kwdefaults, closure, codeStableAssumption, defaultsStableAssumption);

        if (annotations != null) {
            dylib.put(function, __ANNOTATIONS__, annotations);
        }
        if (doc != null) {
            dylib.put(function, __DOC__, doc);
        }

        localFrame.setObject(++stackTop, function);
        return stackTop;
    }

    private static PCode createCode(PythonObjectFactory factory, CodeUnit code, RootCallTarget callTarget, Signature signature) {
        return factory.createCode(callTarget, signature, code.varnames.length, code.stacksize, code.flags, code.constants, code.names,
                        code.varnames, code.freevars, code.cellvars, null, code.name, code.startOffset, code.srcOffsetTable);
    }

    public static MakeFunctionNode create(PythonLanguage language, CodeUnit code, Source source) {
        RootCallTarget callTarget;
        PBytecodeRootNode bytecodeRootNode = new PBytecodeRootNode(language, code, source);
        if (code.isGeneratorOrCoroutine()) {
            // TODO what should the frameDescriptor be? does it matter?
            callTarget = new PBytecodeGeneratorFunctionRootNode(language, bytecodeRootNode.getFrameDescriptor(), bytecodeRootNode, code.name).getCallTarget();
        } else {
            callTarget = bytecodeRootNode.getCallTarget();
        }
        PCode cachedCode = null;
        if (language.isSingleContext()) {
            cachedCode = createCode(PythonObjectFactory.getUncached(), code, callTarget, bytecodeRootNode.getSignature());
        }
        String doc = null;
        if (code.constants.length > 0 && code.constants[0] instanceof String) {
            doc = (String) code.constants[0];
        }
        return MakeFunctionNodeGen.create(callTarget, code, bytecodeRootNode.getSignature(), cachedCode, doc);
    }
}
