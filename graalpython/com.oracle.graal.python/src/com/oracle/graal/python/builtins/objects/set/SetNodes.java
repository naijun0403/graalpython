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
package com.oracle.graal.python.builtins.objects.set;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class SetNodes {

    @GenerateUncached
    public abstract static class ConstructSetNode extends PNodeWithContext {
        public abstract PSet execute(Frame frame, Object cls, Object value);

        public final PSet executeWith(Frame frame, Object value) {
            return this.execute(frame, PythonBuiltinClassType.PSet, value);
        }

        @Specialization
        static PSet setString(VirtualFrame frame, Object cls, String arg,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("setItem") @Cached HashingCollectionNodes.SetItemNode setItemNode) {
            PSet set = factory.createSet(cls);
            for (int i = 0; i < PString.length(arg); i++) {
                setItemNode.execute(frame, set, PString.valueOf(PString.charAt(arg, i)), PNone.NONE);
            }
            return set;
        }

        @Specialization(guards = "emptyArguments(none)")
        static PSet set(Object cls, @SuppressWarnings("unused") PNone none,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createSet(cls);
        }

        @Specialization(guards = "!isNoValue(iterable)")
        static PSet setIterable(VirtualFrame frame, Object cls, Object iterable,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("setItem") @Cached HashingCollectionNodes.SetItemNode setItemNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            PSet set = factory.createSet(cls);
            Object iterator = getIter.execute(frame, iterable);
            while (true) {
                try {
                    setItemNode.execute(frame, set, nextNode.execute(frame, iterator), PNone.NONE);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return set;
                }
            }
        }

        @Fallback
        static PSet setObject(@SuppressWarnings("unused") Object cls, Object value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_NOT_ITERABLE, value);
        }

        public static ConstructSetNode create() {
            return SetNodesFactory.ConstructSetNodeGen.create();
        }

        public static ConstructSetNode getUncached() {
            return SetNodesFactory.ConstructSetNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class AddNode extends PNodeWithContext {
        public abstract void execute(Frame frame, PSet self, Object o);

        @Specialization
        public static void add(VirtualFrame frame, PSet self, Object o,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode) {
            setItemNode.execute(frame, self, o, PNone.NONE);
        }

        public static AddNode create() {
            return SetNodesFactory.AddNodeGen.create();
        }

        public static AddNode getUncached() {
            return SetNodesFactory.AddNodeGen.getUncached();
        }
    }

    public abstract static class DiscardNode extends PythonBinaryBuiltinNode {

        public abstract boolean execute(VirtualFrame frame, PSet self, Object key);

        @Specialization(limit = "3")
        boolean discard(VirtualFrame frame, PSet self, Object key,
                        @Cached BranchProfile updatedStorage,
                        @Cached BaseSetBuiltins.ConvertKeyNode conv,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage storage = self.getDictStorage();
            HashingStorage newStore = null;
            // TODO: FIXME: this might call __hash__ twice
            Object checkedKey = conv.execute(key);
            boolean hasKey = lib.hasKeyWithFrame(storage, checkedKey, hasFrame, frame);
            if (hasKey) {
                newStore = lib.delItemWithFrame(storage, checkedKey, hasFrame, frame);
            }

            if (hasKey) {
                if (newStore != storage) {
                    updatedStorage.enter();
                    self.setDictStorage(newStore);
                }
            }
            return hasKey;
        }
    }
}
