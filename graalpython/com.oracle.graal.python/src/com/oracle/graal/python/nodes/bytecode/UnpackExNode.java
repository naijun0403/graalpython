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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;

@GenerateUncached
public abstract class UnpackExNode extends PNodeWithContext {
    public abstract int execute(Frame virtualFrame, int stackTop, Frame localFrame, Object collection, int countBefore, int countAfter);

    @Specialization(guards = {"cannotBeOverridden(sequence, getClassNode)", "!isPString(sequence)"}, limit = "1")
    static int doUnpackSequence(int initialStackTop, Frame localFrame, PSequence sequence, int countBefore, int countAfter,
                    @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                    @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Cached SequenceStorageNodes.LenNode lenNode,
                    @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                    @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                    @Cached BranchProfile errorProfile,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("raise") @Cached PRaiseNode raiseNode) {
        CompilerAsserts.partialEvaluationConstant(countBefore);
        CompilerAsserts.partialEvaluationConstant(countAfter);
        int resultStackTop = initialStackTop + countBefore + 1 + countAfter;
        int stackTop = resultStackTop;
        SequenceStorage storage = getSequenceStorageNode.execute(sequence);
        int len = lenNode.execute(storage);
        int starLen = len - countBefore - countAfter;
        if (starLen < 0) {
            errorProfile.enter();
            throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, len);
        }
        stackTop = moveItemsToStack(storage, localFrame, stackTop, 0, countBefore, getItemNode);
        PList starList = factory.createList(getItemSliceNode.execute(storage, countBefore, countBefore + starLen, 1, starLen));
        localFrame.setObject(stackTop--, starList);
        moveItemsToStack(storage, localFrame, stackTop, len - countAfter, countAfter, getItemNode);
        return resultStackTop;
    }

    @Fallback
    static int doUnpackIterable(VirtualFrame virtualFrame, int initialStackTop, Frame localFrame, Object collection, int countBefore, int countAfter,
                    @Cached PyObjectGetIter getIter,
                    @Cached GetNextNode getNextNode,
                    @Cached IsBuiltinClassProfile notIterableProfile,
                    @Cached IsBuiltinClassProfile stopIterationProfile,
                    @Cached ListNodes.ConstructListNode constructListNode,
                    @Cached SequenceStorageNodes.LenNode lenNode,
                    @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                    @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("raise") @Cached PRaiseNode raiseNode) {
        CompilerAsserts.partialEvaluationConstant(countBefore);
        CompilerAsserts.partialEvaluationConstant(countAfter);
        int resultStackTop = initialStackTop + countBefore + 1 + countAfter;
        int stackTop = resultStackTop;
        Object iterator;
        try {
            iterator = getIter.execute(virtualFrame, collection);
        } catch (PException e) {
            e.expectTypeError(notIterableProfile);
            throw raiseNode.raise(TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
        }
        stackTop = moveItemsToStack(virtualFrame, iterator, localFrame, stackTop, 0, countBefore, countBefore + countAfter, getNextNode, stopIterationProfile, raiseNode);
        PList starAndAfter = constructListNode.execute(virtualFrame, iterator);
        SequenceStorage storage = starAndAfter.getSequenceStorage();
        int lenAfter = lenNode.execute(storage);
        if (lenAfter < countAfter) {
            throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, countBefore + lenAfter);
        }
        if (countAfter == 0) {
            localFrame.setObject(stackTop, starAndAfter);
        } else {
            int starLen = lenAfter - countAfter;
            PList starList = factory.createList(getItemSliceNode.execute(storage, 0, starLen, 1, starLen));
            localFrame.setObject(stackTop--, starList);
            moveItemsToStack(storage, localFrame, stackTop, starLen, countAfter, getItemNode);
        }
        return resultStackTop;
    }

    @ExplodeLoop
    private static int moveItemsToStack(VirtualFrame virtualFrame, Object iterator, Frame localFrame, int initialStackTop, int offset, int length, int totalLength, GetNextNode getNextNode,
                    IsBuiltinClassProfile stopIterationProfile, PRaiseNode raiseNode) {
        CompilerAsserts.partialEvaluationConstant(length);
        int stackTop = initialStackTop;
        for (int i = 0; i < length; i++) {
            try {
                Object item = getNextNode.execute(virtualFrame, iterator);
                localFrame.setObject(stackTop--, item);
            } catch (PException e) {
                e.expectStopIteration(stopIterationProfile);
                throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, totalLength, offset + i);
            }
        }
        return stackTop;
    }

    @ExplodeLoop
    private static int moveItemsToStack(SequenceStorage storage, Frame localFrame, int initialStackTop, int offset, int length, SequenceStorageNodes.GetItemScalarNode getItemNode) {
        CompilerAsserts.partialEvaluationConstant(length);
        int stackTop = initialStackTop;
        for (int i = 0; i < length; i++) {
            localFrame.setObject(stackTop--, getItemNode.execute(storage, offset + i));
        }
        return stackTop;
    }

    public static UnpackExNode create() {
        return UnpackExNodeGen.create();
    }

    public static UnpackExNode getUncached() {
        return UnpackExNodeGen.getUncached();
    }
}
