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
package com.oracle.graal.python.builtins.objects.iterator;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.nodes.BuiltinNames.ITER;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LENGTH_HINT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETSTATE__;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.ArrayNodes;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.MapNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.LenOfRangeNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PArrayIterator,
                PythonBuiltinClassType.PDictItemIterator, PythonBuiltinClassType.PDictReverseItemIterator,
                PythonBuiltinClassType.PDictKeyIterator, PythonBuiltinClassType.PDictReverseKeyIterator,
                PythonBuiltinClassType.PDictValueIterator, PythonBuiltinClassType.PDictReverseValueIterator})
public class IteratorBuiltins extends PythonBuiltins {

    /*
     * "extendClasses" only needs one of the set of Java classes that are mapped to the Python
     * class.
     */

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IteratorBuiltinsFactory.getFactories();
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

        public static final Object STOP_MARKER = new Object();
        private final boolean throwStopIteration;
        private final ConditionProfile profile = ConditionProfile.createCountingProfile();

        NextNode() {
            this.throwStopIteration = true;
        }

        NextNode(boolean throwStopIteration) {
            this.throwStopIteration = throwStopIteration;
        }

        public abstract Object execute(VirtualFrame frame, PBuiltinIterator iterator);

        private Object stopIteration(PBuiltinIterator self) {
            self.setExhausted();
            if (throwStopIteration) {
                throw raiseStopIteration();
            } else {
                return STOP_MARKER;
            }
        }

        @Specialization(guards = "self.isExhausted()")
        Object exhausted(@SuppressWarnings("unused") PBuiltinIterator self) {
            if (throwStopIteration) {
                throw raiseStopIteration();
            } else {
                return STOP_MARKER;
            }
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PArrayIterator self,
                        @Cached("createClassProfile()") ValueProfile itemTypeProfile,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            PArray array = self.array;
            if (self.getIndex() < array.getLength()) {
                // TODO avoid boxing by getting the array's typecode and using primitive return
                // types
                return itemTypeProfile.profile(getValueNode.execute(array, self.index++));
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PIntegerSequenceIterator self) {
            if (self.getIndex() < self.sequence.length()) {
                return self.sequence.getIntItemNormalized(self.index++);
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PObjectSequenceIterator self) {
            if (self.getIndex() < self.sequence.length()) {
                return self.sequence.getItemNormalized(self.index++);
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PIntRangeIterator self) {
            if (profile.profile(self.hasNextInt())) {
                return self.nextInt();
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PBigRangeIterator self) {
            if (self.hasNextBigInt()) {
                return factory().createInt(self.nextBigInt());
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PDoubleSequenceIterator self) {
            if (self.getIndex() < self.sequence.length()) {
                return self.sequence.getDoubleItemNormalized(self.index++);
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PLongSequenceIterator self) {
            if (self.getIndex() < self.sequence.length()) {
                return self.sequence.getLongItemNormalized(self.index++);
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PBaseSetIterator self,
                        @Cached ConditionProfile sizeChanged,
                        @CachedLibrary(limit = "1") HashingStorageLibrary storageLibrary) {
            if (self.hasNext()) {
                if (sizeChanged.profile(self.checkSizeChanged(storageLibrary))) {
                    throw raise(RuntimeError, ErrorMessages.CHANGED_SIZE_DURING_ITERATION, "Set");
                }
                return self.next();
            }
            return stopIteration(self);
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PStringIterator self) {
            if (self.getIndex() < self.value.length()) {
                return Character.toString(self.value.charAt(self.index++));
            }
            return stopIteration(self);
        }

        @CompilerDirectives.TruffleBoundary
        private Object nextDictValue(PDictView.PBaseDictIterator<?> self) {
            return self.next(factory());
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PDictView.PBaseDictIterator<?> self,
                        @Cached ConditionProfile sizeChanged,
                        @CachedLibrary(limit = "2") HashingStorageLibrary storageLibrary,
                        @Cached ConditionProfile profile) {
            if (profile.profile(self.hasNext())) {
                if (sizeChanged.profile(self.checkSizeChanged(storageLibrary))) {
                    throw raise(RuntimeError, ErrorMessages.CHANGED_SIZE_DURING_ITERATION, "dictionary");
                }
                return nextDictValue(self);
            }
            return stopIteration(self);
        }

        @Specialization(guards = {"!self.isExhausted()", "self.isPSequence()"})
        Object next(VirtualFrame frame, PSequenceIterator self,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage s = getStorage.execute(self.getPSequence());
            if (self.getIndex() < lenNode.execute(s)) {
                return getItemNode.execute(frame, s, self.index++);
            }
            return stopIteration(self);
        }

        @Specialization(guards = {"!self.isExhausted()", "!self.isPSequence()"})
        Object next(VirtualFrame frame, PSequenceIterator self,
                        @Cached("create(GetItem)") LookupAndCallBinaryNode callGetItem,
                        @Cached IsBuiltinClassProfile profile) {
            try {
                return callGetItem.executeObject(frame, self.getObject(), self.index++);
            } catch (PException e) {
                e.expectIndexError(profile);
                return stopIteration(self);
            }
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object self) {
            return self;
        }
    }

    @Builtin(name = __LENGTH_HINT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LengthHintNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.isExhausted()")
        public static int exhausted(@SuppressWarnings("unused") PBuiltinIterator self) {
            return 0;
        }

        @Specialization
        public static int lengthHint(PArrayIterator self) {
            return self.array.getLength() - self.getIndex();
        }

        @Specialization(guards = "!self.isExhausted()", limit = "2")
        public static int lengthHint(@SuppressWarnings({"unused"}) VirtualFrame frame, PDictView.PBaseDictIterator<?> self,
                        @CachedLibrary("self.getHashingStorage()") HashingStorageLibrary hlib,
                        @Cached ConditionProfile profile) {
            if (profile.profile(self.checkSizeChanged(hlib))) {
                return 0;
            }
            return self.getSize() - self.getIndex();
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(PIntegerSequenceIterator self) {
            int len = self.sequence.length() - self.getIndex();
            return len < 0 ? 0 : len;
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(PObjectSequenceIterator self) {
            int len = self.sequence.length() - self.getIndex();
            return len < 0 ? 0 : len;
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(PIntRangeIterator self) {
            return self.getLength();
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(VirtualFrame frame, PBigRangeIterator self,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            return asSizeNode.executeExact(frame, self.getLen());
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(PDoubleSequenceIterator self) {
            int len = self.sequence.length() - self.getIndex();
            return len < 0 ? 0 : len;
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(PLongSequenceIterator self) {
            int len = self.sequence.length() - self.getIndex();
            return len < 0 ? 0 : len;
        }

        @Specialization(guards = "!self.isExhausted()", limit = "1")
        public static int lengthHint(PBaseSetIterator self,
                        @CachedLibrary("self.getSet().getDictStorage()") HashingStorageLibrary hlib,
                        @Cached ConditionProfile profile) {
            int size = self.getSize();
            final int lenSet = hlib.length(self.getSet().getDictStorage());
            if (profile.profile(lenSet != size)) {
                return 0;
            }
            int len = size - self.getIndex();
            return len < 0 ? 0 : len;
        }

        @Specialization(guards = "!self.isExhausted()")
        public static int lengthHint(PStringIterator self) {
            int len = self.value.length() - self.getIndex();
            return len < 0 ? 0 : len;
        }

        @Specialization(guards = {"!self.isExhausted()", "self.isPSequence()"})
        public static int lengthHint(PSequenceIterator self,
                        @Cached SequenceNodes.LenNode lenNode) {
            int len = lenNode.execute(self.getPSequence()) - self.getIndex();
            return len < 0 ? 0 : len;
        }

        @Specialization(guards = {"!self.isExhausted()", "!self.isPSequence()"})
        public static int lengthHint(VirtualFrame frame, PSequenceIterator self,
                        @Cached PyObjectSizeNode sizeNode) {
            int len = sizeNode.execute(frame, self.getObject()) - self.getIndex();
            return len < 0 ? 0 : len;
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Child PyObjectGetAttr getAttrNode;

        @Specialization
        public Object reduce(VirtualFrame frame, PArrayIterator self,
                        @Cached ConditionProfile exhaustedProfile) {
            PythonContext context = PythonContext.get(this);
            if (!exhaustedProfile.profile(self.isExhausted())) {
                return reduceInternal(frame, self.array, self.getIndex(), context);
            } else {
                return reduceInternal(frame, factory().createEmptyTuple(), context);
            }
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PBaseSetIterator self,
                        @Cached SequenceStorageNodes.CreateStorageFromIteratorNode storageNode) {
            int index = self.index;
            boolean isExhausted = self.isExhausted();
            PList list = factory().createList(storageNode.execute(frame, self));
            self.setExhausted(isExhausted);
            self.index = index;
            return reduceInternal(frame, list, self.getIndex(), PythonContext.get(this));
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PDictView.PBaseDictIterator<?> self,
                        @Cached SequenceStorageNodes.CreateStorageFromIteratorNode storageNode,
                        @Cached MapNodes.GetIteratorState getState,
                        @Cached MapNodes.SetIteratorState setState) {
            int index = self.index;
            boolean isExhausted = self.isExhausted();
            int state = getState.execute(self.getIterator());
            PList list = factory().createList(storageNode.execute(frame, self));
            setState.execute(self.getIterator(), state);
            self.setExhausted(isExhausted);
            self.index = index;
            return reduceInternal(frame, list, PythonContext.get(this));
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PIntegerSequenceIterator self) {
            PythonContext context = PythonContext.get(this);
            if (self.isExhausted()) {
                return reduceInternal(frame, factory().createList(), null, context);
            }
            return reduceInternal(frame, self.getObject(), self.getIndex(), context);
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PPrimitiveIterator self) {
            PythonContext context = PythonContext.get(this);
            if (self.isExhausted()) {
                return reduceInternal(frame, factory().createList(), null, context);
            }
            return reduceInternal(frame, self.getObject(), self.getIndex(), context);
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PStringIterator self) {
            PythonContext context = PythonContext.get(this);
            if (self.isExhausted()) {
                return reduceInternal(frame, "", null, context);
            }
            return reduceInternal(frame, self.value, self.getIndex(), context);
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PIntRangeIterator self,
                        @Cached LenOfRangeNode length) {
            int start = self.getReduceStart();
            int stop = self.getReduceStop();
            int step = self.getReduceStep();
            int len = length.executeInt(start, stop, step);
            return reduceInternal(frame, factory().createIntRange(start, stop, step, len), self.getIndex(), PythonContext.get(this));
        }

        @Specialization
        public Object reduce(VirtualFrame frame, PBigRangeIterator self,
                        @Cached LenOfRangeNode length) {
            PInt start = self.getReduceStart();
            PInt stop = self.getReduceStop(factory());
            PInt step = self.getReduceStep();
            PInt len = factory().createInt(length.execute(start.getValue(), stop.getValue(), step.getValue()));
            return reduceInternal(frame, factory().createBigRange(start, stop, step, len), self.getLongIndex(factory()), PythonContext.get(this));
        }

        @Specialization(guards = "self.isPSequence()")
        public Object reduce(VirtualFrame frame, PSequenceIterator self) {
            PythonContext context = PythonContext.get(this);
            if (self.isExhausted()) {
                return reduceInternal(frame, factory().createTuple(new Object[0]), null, context);
            }
            return reduceInternal(frame, self.getPSequence(), self.getIndex(), context);
        }

        @Specialization(guards = "!self.isPSequence()")
        public Object reduceNonSeq(@SuppressWarnings({"unused"}) VirtualFrame frame, PSequenceIterator self) {
            PythonContext context = PythonContext.get(this);
            if (!self.isExhausted()) {
                return reduceInternal(frame, self.getObject(), self.getIndex(), context);
            } else {
                return reduceInternal(frame, factory().createTuple(new Object[0]), null, context);
            }
        }

        private PTuple reduceInternal(VirtualFrame frame, Object arg, PythonContext context) {
            return reduceInternal(frame, arg, null, context);
        }

        private PTuple reduceInternal(VirtualFrame frame, Object arg, Object state, PythonContext context) {
            PythonModule builtins = context.getBuiltins();
            Object iter = getGetAttrNode().execute(frame, builtins, ITER);
            PTuple args = factory().createTuple(new Object[]{arg});
            // callable, args, state (optional)
            if (state != null) {
                return factory().createTuple(new Object[]{iter, args, state});
            } else {
                return factory().createTuple(new Object[]{iter, args});
            }
        }

        private PyObjectGetAttr getGetAttrNode() {
            if (getAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getAttrNode = insert(PyObjectGetAttr.create());
            }
            return getAttrNode;
        }
    }

    @Builtin(name = __SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        @CompilerDirectives.TruffleBoundary
        public static Object reduce(PBigRangeIterator self, Object index,
                        @Cached CastToJavaBigIntegerNode castToJavaBigIntegerNode) {
            BigInteger idx = castToJavaBigIntegerNode.execute(index);
            if (idx.compareTo(BigInteger.ZERO) < 0) {
                idx = BigInteger.ZERO;
            }
            self.setLongIndex(idx);
            return PNone.NONE;
        }

        @Specialization
        public static Object reduce(VirtualFrame frame, PBuiltinIterator self, Object index,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            int idx = asSizeNode.executeExact(frame, index);
            if (idx < 0) {
                idx = 0;
            }
            self.index = idx;
            return PNone.NONE;
        }
    }
}
