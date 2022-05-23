/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import java.util.ArrayList;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.parser.PythonParserImpl;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class PRootNode extends RootNode {
    private final ConditionProfile frameEscapedWithoutAllocation = ConditionProfile.createBinaryProfile();
    private final ConditionProfile frameEscaped = ConditionProfile.createBinaryProfile();

    @CompilationFinal private Assumption dontNeedCallerFrame = createCallerFrameAssumption();

    /**
     * Flag indicating if some child node of this root node (or a callee) eventually needs the
     * exception state. Hence, the caller of this root node should provide the exception state in
     * the arguments.
     */
    @CompilationFinal private Assumption dontNeedExceptionState = createExceptionStateAssumption();

    private int nodeCount = -1;

    /**
     * This contains all the deprecation warnings that were issued while parsing the contents of
     * this root node. They cannot be raised/printed immediately because the parse result might be
     * cached, in which case the parser will not trigger them directly. At the place where the code
     * would "logically" be parsed, the warnings can be raised via
     * {@link #triggerDeprecationWarnings()}.
     */
    @CompilationFinal(dimensions = 1) private String[] deprecationWarnings;

    // contains the code of this root node in marshaled/serialized form
    private byte[] code;

    protected PRootNode(TruffleLanguage<?> language) {
        super(language);
    }

    protected PRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    public final int getNodeCount() {
        CompilerAsserts.neverPartOfCompilation();
        int n = nodeCount;
        if (n != -1) {
            return n;
        }
        return nodeCount = NodeUtil.countNodes(this);
    }

    public ConditionProfile getFrameEscapedProfile() {
        return frameEscaped;
    }

    public ConditionProfile getFrameEscapedWithoutAllocationProfile() {
        return frameEscapedWithoutAllocation;
    }

    public boolean needsCallerFrame() {
        return !dontNeedCallerFrame.isValid();
    }

    public void setNeedsCallerFrame() {
        CompilerAsserts.neverPartOfCompilation("this is usually called from behind a TruffleBoundary");
        dontNeedCallerFrame.invalidate();
    }

    public boolean needsExceptionState() {
        return !dontNeedExceptionState.isValid();
    }

    public void setNeedsExceptionState() {
        CompilerAsserts.neverPartOfCompilation("this is usually called from behind a TruffleBoundary");
        dontNeedExceptionState.invalidate();
    }

    public final void triggerDeprecationWarnings() {
        if (deprecationWarnings != null) {
            triggerDeprecationWarningsBoundary();
        }
    }

    @TruffleBoundary
    private void triggerDeprecationWarningsBoundary() {
        Python3Core errors = PythonContext.get(this);
        try {
            for (String warning : deprecationWarnings) {
                errors.warn(PythonBuiltinClassType.DeprecationWarning, "%s", warning);
            }
        } catch (Exception e) {
            throw PythonParserImpl.handleParserError(errors, getSourceSection().getSource(), e);
        }
    }

    public final void setDeprecationWarnings(ArrayList<String> deprecationWarnings) {
        this.deprecationWarnings = deprecationWarnings == null || deprecationWarnings.isEmpty() ? null : deprecationWarnings.toArray(new String[0]);
    }

    @Override
    public boolean isCaptureFramesForTrace() {
        return true;
    }

    @Override
    public boolean isCloningAllowed() {
        return true;
    }

    @Override
    public Node copy() {
        PRootNode pRootNode = (PRootNode) super.copy();
        // create new assumptions such that splits do not share them
        pRootNode.dontNeedCallerFrame = createCallerFrameAssumption();
        pRootNode.dontNeedExceptionState = createExceptionStateAssumption();
        return pRootNode;
    }

    public abstract Signature getSignature();

    public abstract boolean isPythonInternal();

    @CompilerDirectives.TruffleBoundary
    private static boolean isPythonInternal(PRootNode rootNode) {
        return rootNode.isPythonInternal();
    }

    public static boolean isPythonInternal(RootNode rootNode) {
        return rootNode instanceof PRootNode && isPythonInternal((PRootNode) rootNode);
    }

    public static boolean isPythonBuiltin(RootNode rootNode) {
        return rootNode instanceof BuiltinFunctionRootNode;
    }

    private static Assumption createCallerFrameAssumption() {
        return Truffle.getRuntime().createAssumption("does not need caller frame");
    }

    private static Assumption createExceptionStateAssumption() {
        return Truffle.getRuntime().createAssumption("does not need exception state");
    }

    public final void setCode(byte[] data) {
        CompilerAsserts.neverPartOfCompilation();
        assert this.code == null;
        this.code = data;
    }

    @TruffleBoundary
    public final byte[] getCode() {
        if (code != null) {
            return code;
        }
        return code = extractCode();
    }

    protected byte[] extractCode() {
        // no code for non-user functions
        return PythonUtils.EMPTY_BYTE_ARRAY;
    }
}
