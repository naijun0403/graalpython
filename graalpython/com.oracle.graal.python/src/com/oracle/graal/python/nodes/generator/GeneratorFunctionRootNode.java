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
package com.oracle.graal.python.nodes.generator;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.PClosureFunctionRootNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.parser.GeneratorInfo;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

public class GeneratorFunctionRootNode extends PClosureFunctionRootNode {
    private final RootCallTarget callTarget;
    @CompilationFinal(dimensions = 1) private RootCallTarget[] callTargets;
    private final FrameDescriptor frameDescriptor;
    private final GeneratorInfo generatorInfo;
    private final ExecutionCellSlots cellSlots;
    private final String originalName;

    @Child private PythonObjectFactory factory = PythonObjectFactory.create();

    public GeneratorFunctionRootNode(PythonLanguage language, RootCallTarget callTarget, String originalName, FrameDescriptor frameDescriptor, ExecutionCellSlots executionCellSlots,
                    Signature signature, GeneratorInfo generatorInfo) {
        super(language, frameDescriptor, executionCellSlots, signature);
        this.callTarget = callTarget;
        this.originalName = originalName;
        this.frameDescriptor = frameDescriptor;
        this.cellSlots = executionCellSlots;
        this.generatorInfo = generatorInfo;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        // TODO 'materialize' generator frame and create locals dict eagerly
        if (callTargets == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callTargets = createYieldTargets(callTarget);
        }
        CompilerAsserts.partialEvaluationConstant(cellSlots);

        Object[] arguments = frame.getArguments();

        // This is passed from CallDispatch node
        PFunction generatorFunction = PArguments.getGeneratorFunction(arguments);

        return factory.createGenerator(generatorFunction.getName(), generatorFunction.getQualname(), callTargets, frameDescriptor, arguments, PArguments.getClosure(frame), cellSlots,
                        generatorInfo, null);
    }

    public static RootCallTarget[] createYieldTargets(RootCallTarget callTarget) {
        CompilerAsserts.neverPartOfCompilation();
        int numYields = NodeUtil.countNodes(callTarget.getRootNode(), (node) -> node instanceof AbstractYieldNode);
        RootCallTarget[] callTargets = new RootCallTarget[numYields + 1];
        callTargets[0] = callTarget;
        for (int i = 1; i < callTargets.length; i++) {
            callTargets[i] = PythonUtils.getOrCreateCallTarget(NodeUtil.cloneNode(callTarget.getRootNode()));
        }
        return callTargets;
    }

    public RootNode getFunctionRootNode() {
        return callTarget.getRootNode();
    }

    @Override
    public String getName() {
        return originalName;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "<generator function root" + originalName + ">";
    }

    @Override
    public void initializeFrame(VirtualFrame frame) {
        // nothing to do
    }

    @Override
    public boolean isPythonInternal() {
        RootNode rootNode = callTarget.getRootNode();
        return rootNode instanceof PRootNode && ((PRootNode) rootNode).isPythonInternal();
    }

    @Override
    public SourceSection getSourceSection() {
        return getFunctionRootNode().getSourceSection();
    }

}
