/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_BYTE_ARRAY_REALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_DOUBLE_ARRAY_REALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_DOUBLE_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_INT_ARRAY_REALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_INT_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_LONG_ARRAY_REALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_LONG_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_OBJECT_ARRAY_REALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_OBJECT_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.iterator.IteratorBuiltins.NextNode.STOP_MARKER;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Boolean;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Byte;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Double;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Empty;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Int;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Long;
import static com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType.Uninitialized;

import java.lang.reflect.Array;
import java.util.Arrays;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexCustomMessageNode;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetSequenceStorageNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.AppendNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CmpNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ConcatBaseNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ConcatNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CopyItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CopyNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CreateEmptyNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CreateStorageFromIteratorNodeFactory.CreateStorageFromIteratorNodeCachedNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.DeleteItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.DeleteNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.DeleteSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.DoGeneralizationNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ExtendNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetElementTypeNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemDynamicNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemScalarNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.InsertItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.IsAssignCompatibleNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.IsDataTypeCompatibleNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ItemIndexNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ListGeneralizationNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.NoGeneralizationCustomMessageNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.NoGeneralizationNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.RepeatNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemDynamicNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemScalarNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetLenNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.StorageToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.VerifyNativeItemNodeGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.IteratorBuiltins.NextNode;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes.BuiltinIteratorLengthHint;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes.GetInternalIteratorSequenceStorage;
import com.oracle.graal.python.builtins.objects.iterator.PBuiltinIterator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.LenOfRangeNode;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode.CoerceToIntSlice;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode.ComputeIndices;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaByteNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStoreException;
import com.oracle.graal.python.runtime.sequence.storage.TypedSequenceStorage;
import com.oracle.graal.python.util.BiFunction;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class SequenceStorageNodes {

    public interface GenNodeSupplier {
        GeneralizationNode create();

        GeneralizationNode getUncached();
    }

    @GenerateUncached
    public abstract static class IsAssignCompatibleNode extends Node {

        protected abstract boolean execute(SequenceStorage lhs, SequenceStorage rhs);

        /**
         * Tests if each element of {@code rhs} can be assign to {@code lhs} without casting.
         */
        @Specialization
        static boolean compatibleAssign(SequenceStorage lhs, SequenceStorage rhs,
                        @Cached GetElementType getElementTypeNode) {
            ListStorageType rhsType = getElementTypeNode.execute(rhs);
            switch (getElementTypeNode.execute(lhs)) {
                case Boolean:
                    return rhsType == Boolean || rhsType == Uninitialized || rhsType == Empty;
                case Byte:
                    return rhsType == Boolean || rhsType == Byte || rhsType == Uninitialized || rhsType == Empty;
                case Int:
                    return rhsType == Boolean || rhsType == ListStorageType.Byte || rhsType == ListStorageType.Int || rhsType == Uninitialized || rhsType == Empty;
                case Long:
                    return rhsType == Boolean || rhsType == Byte || rhsType == Int || rhsType == Long || rhsType == Uninitialized || rhsType == Empty;
                case Double:
                    return rhsType == Double || rhsType == Uninitialized || rhsType == Empty;
                case Generic:
                    return true;
                case Empty:
                case Uninitialized:
                    return false;
            }
            assert false : "should not reach";
            return false;
        }

        public static IsAssignCompatibleNode create() {
            return IsAssignCompatibleNodeGen.create();
        }

        public static IsAssignCompatibleNode getUncached() {
            return IsAssignCompatibleNodeGen.getUncached();
        }
    }

    @GenerateUncached
    abstract static class IsDataTypeCompatibleNode extends Node {

        protected abstract boolean execute(SequenceStorage lhs, SequenceStorage rhs);

        /**
         * Tests if each element of {@code rhs} can be assign to {@code lhs} without casting.
         */
        @Specialization
        static boolean compatibleAssign(SequenceStorage lhs, SequenceStorage rhs,
                        @Cached GetElementType getElementTypeNode) {
            ListStorageType rhsType = getElementTypeNode.execute(rhs);
            switch (getElementTypeNode.execute(lhs)) {
                case Boolean:
                case Byte:
                case Int:
                case Long:
                    return rhsType == Boolean || rhsType == Byte || rhsType == Int || rhsType == Long || rhsType == Uninitialized || rhsType == Empty;
                case Double:
                    return rhsType == Double || rhsType == Uninitialized || rhsType == Empty;
                case Generic:
                    return true;
                case Empty:
                case Uninitialized:
                    return false;
            }
            assert false : "should not reach";
            return false;
        }

        public static IsDataTypeCompatibleNode create() {
            return IsDataTypeCompatibleNodeGen.create();
        }

        public static IsDataTypeCompatibleNode getUncached() {
            return IsDataTypeCompatibleNodeGen.getUncached();
        }
    }

    @ImportStatic(PythonOptions.class)
    abstract static class SequenceStorageBaseNode extends PNodeWithContext {

        protected static final int DEFAULT_CAPACITY = 8;

        protected static final int MAX_SEQUENCE_STORAGES = 9;
        protected static final int MAX_ARRAY_STORAGES = 7;

        protected static boolean isByteStorage(NativeSequenceStorage store) {
            return store.getElementType() == ListStorageType.Byte;
        }

        /**
         * Tests if {@code left} has the same element type as {@code right}.
         */
        protected static boolean compatible(SequenceStorage left, NativeSequenceStorage right) {
            switch (right.getElementType()) {
                case Boolean:
                    return left instanceof BoolSequenceStorage;
                case Byte:
                    return left instanceof ByteSequenceStorage;
                case Int:
                    return left instanceof IntSequenceStorage;
                case Long:
                    return left instanceof LongSequenceStorage;
                case Double:
                    return left instanceof DoubleSequenceStorage;
                case Generic:
                    return left instanceof ObjectSequenceStorage;
            }
            assert false : "should not reach";
            return false;
        }

        protected static boolean isNative(SequenceStorage store) {
            return store instanceof NativeSequenceStorage;
        }

        protected static boolean isEmpty(LenNode lenNode, SequenceStorage left) {
            return lenNode.execute(left) == 0;
        }

        protected static boolean isBoolean(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.Boolean;
        }

        protected static boolean isByte(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.Byte;
        }

        protected static boolean isByteLike(GetElementType getElementTypeNode, SequenceStorage s) {
            return isByte(getElementTypeNode, s) || isInt(getElementTypeNode, s) || isLong(getElementTypeNode, s);
        }

        protected static boolean isInt(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.Int;
        }

        protected static boolean isLong(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.Long;
        }

        protected static boolean isDouble(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.Double;
        }

        protected static boolean isObject(GetElementType getElementTypeNode, SequenceStorage s) {
            return getElementTypeNode.execute(s) == ListStorageType.Generic;
        }

        protected static boolean isBoolean(ListStorageType et) {
            return et == ListStorageType.Boolean;
        }

        protected static boolean isByte(ListStorageType et) {
            return et == ListStorageType.Byte;
        }

        protected static boolean isByteLike(ListStorageType et) {
            return isByte(et) || isInt(et) || isLong(et);
        }

        protected static boolean isInt(ListStorageType et) {
            return et == ListStorageType.Int;
        }

        protected static boolean isLong(ListStorageType et) {
            return et == ListStorageType.Long;
        }

        protected static boolean isDouble(ListStorageType et) {
            return et == ListStorageType.Double;
        }

        protected static boolean isObject(ListStorageType et) {
            return et == ListStorageType.Generic;
        }

        protected static boolean hasStorage(Object source) {
            return source instanceof PSequence && !(source instanceof PString);
        }
    }

    abstract static class NormalizingNode extends PNodeWithContext {

        @Child private NormalizeIndexNode normalizeIndexNode;
        @Child private PyNumberAsSizeNode asSizeNode;
        @Child private LenNode lenNode;

        protected NormalizingNode(NormalizeIndexNode normalizeIndexNode) {
            this.normalizeIndexNode = normalizeIndexNode;
        }

        protected final int normalizeIndex(VirtualFrame frame, Object idx, SequenceStorage store) {
            int intIdx = getAsSizeNode().executeExact(frame, idx, IndexError);
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.execute(intIdx, getStoreLength(store));
            }
            return intIdx;
        }

        protected final int normalizeIndex(@SuppressWarnings("unused") VirtualFrame frame, int idx, SequenceStorage store) {
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.execute(idx, getStoreLength(store));
            }
            return idx;
        }

        protected final int normalizeIndex(VirtualFrame frame, long idx, SequenceStorage store) {
            int intIdx = getAsSizeNode().executeExact(frame, idx, IndexError);
            if (normalizeIndexNode != null) {
                return normalizeIndexNode.execute(intIdx, getStoreLength(store));
            }
            return intIdx;
        }

        private int getStoreLength(SequenceStorage store) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(LenNode.create());
            }
            return lenNode.execute(store);
        }

        private PyNumberAsSizeNode getAsSizeNode() {
            if (asSizeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asSizeNode = insert(PyNumberAsSizeNode.create());
            }
            return asSizeNode;
        }

        protected static boolean isPSlice(Object obj) {
            return obj instanceof PSlice;
        }

    }

    public abstract static class GetItemNode extends NormalizingNode {

        @Child private GetItemScalarNode getItemScalarNode;
        @Child private GetItemSliceNode getItemSliceNode;
        @Child private PRaiseNode raiseNode;
        private final String keyTypeErrorMessage;
        private final BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod;

        public GetItemNode(NormalizeIndexNode normalizeIndexNode, String keyTypeErrorMessage, BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod) {
            super(normalizeIndexNode);
            this.keyTypeErrorMessage = keyTypeErrorMessage;
            this.factoryMethod = factoryMethod;
        }

        public abstract Object execute(VirtualFrame frame, SequenceStorage s, Object key);

        public abstract Object execute(VirtualFrame frame, SequenceStorage s, int key);

        public abstract Object execute(VirtualFrame frame, SequenceStorage s, long key);

        public abstract int executeInt(VirtualFrame frame, SequenceStorage s, int key);

        public abstract long executeLong(VirtualFrame frame, SequenceStorage s, long key);

        @Specialization
        protected Object doScalarInt(VirtualFrame frame, SequenceStorage storage, int idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization
        protected Object doScalarLong(VirtualFrame frame, SequenceStorage storage, long idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization
        protected Object doScalarPInt(VirtualFrame frame, SequenceStorage storage, PInt idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected Object doScalarGeneric(VirtualFrame frame, SequenceStorage storage, Object idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization
        protected Object doSlice(VirtualFrame frame, SequenceStorage storage, PSlice slice,
                        @Cached LenNode lenNode,
                        @Cached PythonObjectFactory factory,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached ComputeIndices compute,
                        @Cached LenOfRangeNode sliceLen) {
            SliceInfo info = compute.execute(frame, sliceCast.execute(slice), lenNode.execute(storage));
            if (factoryMethod != null) {
                return factoryMethod.apply(getGetItemSliceNode().execute(storage, info.start, info.stop, info.step, sliceLen.len(info)), factory);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException();
        }

        @Fallback
        protected Object doInvalidKey(@SuppressWarnings("unused") SequenceStorage storage, Object key) {
            throw ensureRaiseNode().raise(TypeError, keyTypeErrorMessage, key);
        }

        private GetItemScalarNode getGetItemScalarNode() {
            if (getItemScalarNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemScalarNode = insert(GetItemScalarNode.create());
            }
            return getItemScalarNode;
        }

        private GetItemSliceNode getGetItemSliceNode() {
            if (getItemSliceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemSliceNode = insert(GetItemSliceNode.create());
            }
            return getItemSliceNode;
        }

        private PRaiseNode ensureRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
        }

        public static GetItemNode createNotNormalized() {
            return GetItemNodeGen.create(null, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, null);
        }

        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode) {
            return GetItemNodeGen.create(normalizeIndexNode, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, null);
        }

        public static GetItemNode create() {
            return GetItemNodeGen.create(NormalizeIndexNode.create(), ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, null);
        }

        public static GetItemNode createNotNormalized(String keyTypeErrorMessage) {
            return GetItemNodeGen.create(null, keyTypeErrorMessage, null);
        }

        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode, String keyTypeErrorMessage) {
            return GetItemNodeGen.create(normalizeIndexNode, keyTypeErrorMessage, null);
        }

        public static GetItemNode create(String keyTypeErrorMessage) {
            return GetItemNodeGen.create(NormalizeIndexNode.create(), keyTypeErrorMessage, null);
        }

        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode, String keyTypeErrorMessage, BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod) {
            return GetItemNodeGen.create(normalizeIndexNode, keyTypeErrorMessage, factoryMethod);
        }

        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode, BiFunction<SequenceStorage, PythonObjectFactory, Object> factoryMethod) {
            return GetItemNodeGen.create(normalizeIndexNode, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, factoryMethod);
        }

    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class GetItemDynamicNode extends Node {

        public abstract Object executeObject(SequenceStorage s, Object key);

        public final Object execute(SequenceStorage s, int key) {
            return executeObject(s, key);
        }

        public final Object execute(SequenceStorage s, long key) {
            return executeObject(s, key);
        }

        public final Object execute(SequenceStorage s, PInt key) {
            return executeObject(s, key);
        }

        @Specialization
        protected static Object doScalarInt(SequenceStorage storage, int idx,
                        @Shared("getItemScalarNode") @Cached GetItemScalarNode getItemScalarNode,
                        @Shared("normalizeIndexNode") @Cached NormalizeIndexCustomMessageNode normalizeIndexNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            return getItemScalarNode.execute(storage, normalizeIndexNode.execute(idx, lenNode.execute(storage), ErrorMessages.INDEX_OUT_OF_RANGE));
        }

        @Specialization
        protected static Object doScalarLong(SequenceStorage storage, long idx,
                        @Shared("getItemScalarNode") @Cached GetItemScalarNode getItemScalarNode,
                        @Shared("normalizeIndexNode") @Cached NormalizeIndexCustomMessageNode normalizeIndexNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            return getItemScalarNode.execute(storage, normalizeIndexNode.execute(idx, lenNode.execute(storage), ErrorMessages.INDEX_OUT_OF_RANGE));
        }

        @Specialization
        protected static Object doScalarPInt(SequenceStorage storage, PInt idx,
                        @Shared("getItemScalarNode") @Cached GetItemScalarNode getItemScalarNode,
                        @Shared("normalizeIndexNode") @Cached NormalizeIndexCustomMessageNode normalizeIndexNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            return getItemScalarNode.execute(storage, normalizeIndexNode.execute(idx, lenNode.execute(storage), ErrorMessages.INDEX_OUT_OF_RANGE));
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected static Object doScalarGeneric(SequenceStorage storage, Object idx,
                        @Shared("getItemScalarNode") @Cached GetItemScalarNode getItemScalarNode,
                        @Shared("normalizeIndexNode") @Cached NormalizeIndexCustomMessageNode normalizeIndexNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            return getItemScalarNode.execute(storage, normalizeIndexNode.execute(idx, lenNode.execute(storage), ErrorMessages.INDEX_OUT_OF_RANGE));
        }

        @Specialization
        @SuppressWarnings("unused")
        protected static Object doSlice(SequenceStorage storage, PSlice slice,
                        @Cached GetItemSliceNode getItemSliceNode,
                        @Cached PythonObjectFactory factory,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached ComputeIndices compute,
                        @Cached LenOfRangeNode sliceLen) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException();
        }

        public static GetItemDynamicNode create() {
            return GetItemDynamicNodeGen.create();
        }

        public static GetItemDynamicNode getUncached() {
            return GetItemDynamicNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class GetItemScalarNode extends Node {

        public abstract Object execute(SequenceStorage s, int idx);

        public static GetItemScalarNode create() {
            return GetItemScalarNodeGen.create();
        }

        public static GetItemScalarNode getUncached() {
            return GetItemScalarNodeGen.getUncached();
        }

        public abstract boolean executeBoolean(SequenceStorage s, int idx);

        public abstract byte executeByte(SequenceStorage s, int idx);

        public abstract char executeChar(SequenceStorage s, int idx);

        public abstract int executeInt(SequenceStorage s, int idx);

        public abstract long executeLong(SequenceStorage s, int idx);

        public abstract double executeDouble(SequenceStorage s, int idx);

        @Specialization
        protected static boolean doBoolean(BoolSequenceStorage storage, int idx) {
            return storage.getBoolItemNormalized(idx);
        }

        @Specialization
        protected static int doByte(ByteSequenceStorage storage, int idx) {
            return storage.getIntItemNormalized(idx);
        }

        @Specialization
        protected static int doInt(IntSequenceStorage storage, int idx) {
            return storage.getIntItemNormalized(idx);
        }

        @Specialization
        protected static long doLong(LongSequenceStorage storage, int idx) {
            return storage.getLongItemNormalized(idx);
        }

        @Specialization
        protected static double doDouble(DoubleSequenceStorage storage, int idx) {
            return storage.getDoubleItemNormalized(idx);
        }

        @Specialization
        protected static Object doObject(ObjectSequenceStorage storage, int idx) {
            return storage.getItemNormalized(idx);
        }

        @Specialization
        protected static Object doMro(MroSequenceStorage storage, int idx) {
            return storage.getItemNormalized(idx);
        }

        @Specialization(guards = "isObject(getElementType, storage)", limit = "1")
        protected static Object doNativeObject(NativeSequenceStorage storage, int idx,
                        @CachedLibrary("storage.getPtr()") InteropLibrary lib,
                        @Shared("verifyNativeItemNode") @Cached VerifyNativeItemNode verifyNativeItemNode,
                        @Shared("getElementType") @Cached @SuppressWarnings("unused") GetElementType getElementType,
                        @Cached CExtNodes.ToJavaNode toJavaNode,
                        @Cached BranchProfile errorProfile,
                        @Cached PRaiseNode raiseNode) {
            try {
                return verifyResult(verifyNativeItemNode, raiseNode, storage, toJavaNode.execute(lib.readArrayElement(storage.getPtr(), idx)));
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                // The 'InvalidArrayIndexException' should really not happen since we did a bounds
                // check before.
                errorProfile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, e);
            }
        }

        @Specialization(guards = "isByteStorage(storage)", limit = "1")
        protected static int doNativeByte(NativeSequenceStorage storage, int idx,
                        @CachedLibrary("storage.getPtr()") InteropLibrary lib,
                        @Shared("verifyNativeItemNode") @Cached VerifyNativeItemNode verifyNativeItemNode,
                        @Shared("getElementType") @Cached GetElementType getElementType,
                        @Cached BranchProfile errorProfile,
                        @Cached PRaiseNode raiseNode) {
            Object result = doNative(storage, idx, lib, verifyNativeItemNode, getElementType, errorProfile, raiseNode);
            return (byte) result & 0xFF;
        }

        @Specialization(guards = {"!isByteStorage(storage)", "!isObject(getElementType, storage)"}, limit = "1")
        protected static Object doNative(NativeSequenceStorage storage, int idx,
                        @CachedLibrary("storage.getPtr()") InteropLibrary lib,
                        @Shared("verifyNativeItemNode") @Cached VerifyNativeItemNode verifyNativeItemNode,
                        @Shared("getElementType") @Cached @SuppressWarnings("unused") GetElementType getElementType,
                        @Cached BranchProfile errorProfile,
                        @Cached PRaiseNode raiseNode) {
            try {
                return verifyResult(verifyNativeItemNode, raiseNode, storage, lib.readArrayElement(storage.getPtr(), idx));
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                // The 'InvalidArrayIndexException' should really not happen since we did a bounds
                // check before.
                errorProfile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, e);
            }
        }

        private static Object verifyResult(VerifyNativeItemNode verifyNativeItemNode, PRaiseNode raiseNode, NativeSequenceStorage storage, Object item) {
            if (verifyNativeItemNode.execute(storage.getElementType(), item)) {
                return item;
            }
            throw raiseNode.raise(SystemError, ErrorMessages.INVALID_ITEM_RETURNED_FROM_NATIVE_SEQ, item, storage.getElementType());
        }
    }

    @GenerateUncached
    @ImportStatic({ListStorageType.class, SequenceStorageBaseNode.class})
    public abstract static class GetItemSliceNode extends Node {

        public abstract SequenceStorage execute(SequenceStorage s, int start, int stop, int step, int length);

        @Specialization
        @SuppressWarnings("unused")
        protected static EmptySequenceStorage doEmpty(EmptySequenceStorage storage, int start, int stop, int step, int length) {
            return EmptySequenceStorage.INSTANCE;
        }

        @Specialization(limit = "MAX_ARRAY_STORAGES", guards = {"storage.getClass() == cachedClass"})
        protected static SequenceStorage doManagedStorage(BasicSequenceStorage storage, int start, int stop, int step, int length,
                        @Cached("storage.getClass()") Class<? extends BasicSequenceStorage> cachedClass) {
            return cachedClass.cast(storage).getSliceInBound(start, stop, step, length);
        }

        @Specialization(guards = "isByte(storage.getElementType())")
        protected static NativeSequenceStorage doNativeByte(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached PRaiseNode raise,
                        @Cached StorageToNativeNode storageToNativeNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            byte[] newArray = new byte[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (byte) readNativeElement(lib, storage.getPtr(), i, raise);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "isInt(storage.getElementType())")
        protected static NativeSequenceStorage doNativeInt(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached PRaiseNode raise,
                        @Cached StorageToNativeNode storageToNativeNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            int[] newArray = new int[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (int) readNativeElement(lib, storage.getPtr(), i, raise);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "isLong(storage.getElementType())")
        protected static NativeSequenceStorage doNativeLong(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached PRaiseNode raise,
                        @Cached StorageToNativeNode storageToNativeNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            long[] newArray = new long[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (long) readNativeElement(lib, storage.getPtr(), i, raise);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "isDouble(storage.getElementType())")
        protected static NativeSequenceStorage doNativeDouble(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached PRaiseNode raise,
                        @Cached StorageToNativeNode storageToNativeNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            double[] newArray = new double[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (double) readNativeElement(lib, storage.getPtr(), i, raise);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "isObject(storage.getElementType())")
        protected static NativeSequenceStorage doNativeObject(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached PRaiseNode raise,
                        @Cached StorageToNativeNode storageToNativeNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            Object[] newArray = new Object[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = readNativeElement(lib, storage.getPtr(), i, raise);
            }
            return storageToNativeNode.execute(newArray);
        }

        private static Object readNativeElement(InteropLibrary lib, Object ptr, int idx, PRaiseNode raise) {
            try {
                return lib.readArrayElement(ptr, idx);
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw raise.raise(PythonBuiltinClassType.SystemError, e);
            }
        }

        public static GetItemSliceNode create() {
            return GetItemSliceNodeGen.create();
        }

        public static GetItemSliceNode getUncached() {
            return GetItemSliceNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class SetItemDynamicNode extends Node {

        public abstract SequenceStorage execute(Frame frame, GenNodeSupplier generalizationNodeProvider, SequenceStorage s, Object key, Object value);

        @Specialization
        protected static SequenceStorage doScalarInt(GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, int idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode,
                        @Shared("doGenNode") @Cached DoGeneralizationNode doGenNode,
                        @Shared("normalizeNode") @Cached NormalizeIndexCustomMessageNode normalizeNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            int normalized = normalizeNode.execute(idx, lenNode.execute(storage), ErrorMessages.INDEX_OUT_OF_RANGE);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = doGenNode.execute(generalizationNodeProvider, storage, e.getIndicationValue());
                try {
                    setItemScalarNode.execute(generalized, normalized, value);
                } catch (SequenceStoreException e1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
                return generalized;
            }
        }

        @Specialization
        protected static SequenceStorage doScalarLong(GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, long idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode,
                        @Shared("doGenNode") @Cached DoGeneralizationNode doGenNode,
                        @Shared("normalizeNode") @Cached NormalizeIndexCustomMessageNode normalizeNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            int normalized = normalizeNode.execute(idx, lenNode.execute(storage), ErrorMessages.INDEX_OUT_OF_RANGE);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = doGenNode.execute(generalizationNodeProvider, storage, e.getIndicationValue());
                setItemScalarNode.execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization
        protected static SequenceStorage doScalarPInt(GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, PInt idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode,
                        @Shared("doGenNode") @Cached DoGeneralizationNode doGenNode,
                        @Shared("normalizeNode") @Cached NormalizeIndexCustomMessageNode normalizeNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            int normalized = normalizeNode.execute(idx, lenNode.execute(storage), ErrorMessages.INDEX_OUT_OF_RANGE);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = doGenNode.execute(generalizationNodeProvider, storage, e.getIndicationValue());
                setItemScalarNode.execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected static SequenceStorage doScalarGeneric(GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, Object idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode,
                        @Shared("doGenNode") @Cached DoGeneralizationNode doGenNode,
                        @Shared("normalizeNode") @Cached NormalizeIndexCustomMessageNode normalizeNode,
                        @Shared("lenNode") @Cached LenNode lenNode) {
            int normalized = normalizeNode.execute(idx, lenNode.execute(storage), ErrorMessages.INDEX_OUT_OF_RANGE);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = doGenNode.execute(generalizationNodeProvider, storage, e.getIndicationValue());
                setItemScalarNode.execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization
        protected static SequenceStorage doSlice(VirtualFrame frame, GenNodeSupplier generalizationNodeProvider, SequenceStorage storage, PSlice slice, Object iterable,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Cached SetItemSliceNode setItemSliceNode,
                        @Shared("doGenNode") @Cached DoGeneralizationNode doGenNode,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached ComputeIndices compute) {
            SliceInfo info = compute.execute(frame, sliceCast.execute(slice), storage.length());
            // We need to construct the list eagerly because if a SequenceStoreException occurs, we
            // must not use iterable again. It could have side-effects.
            PList values = constructListNode.execute(frame, iterable);
            try {
                setItemSliceNode.execute(frame, storage, info, values);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = doGenNode.execute(generalizationNodeProvider, storage, e.getIndicationValue());
                setItemSliceNode.execute(frame, generalized, info, values);
                return generalized;
            }
        }

        public static SetItemDynamicNode create() {
            return SetItemDynamicNodeGen.create();
        }

        public static SetItemDynamicNode getUncached() {
            return SetItemDynamicNodeGen.getUncached();
        }
    }

    @GenerateUncached
    abstract static class DoGeneralizationNode extends Node {

        public abstract SequenceStorage execute(GenNodeSupplier supplier, SequenceStorage storage, Object value);

        @Specialization(guards = "supplier == cachedSupplier")
        static SequenceStorage doCached(@SuppressWarnings("unused") GenNodeSupplier supplier, SequenceStorage storage, Object value,
                        @Cached("supplier") @SuppressWarnings("unused") GenNodeSupplier cachedSupplier,
                        @Cached(value = "supplier.create()", uncached = "supplier.getUncached()") GeneralizationNode genNode) {

            return genNode.execute(storage, value);
        }

        @Specialization(replaces = "doCached")
        static SequenceStorage doUncached(GenNodeSupplier supplier, SequenceStorage storage, Object value) {
            return supplier.getUncached().execute(storage, value);
        }

        public static DoGeneralizationNode create() {
            return DoGeneralizationNodeGen.create();
        }

        public static DoGeneralizationNode getUncached() {
            return DoGeneralizationNodeGen.getUncached();
        }
    }

    public abstract static class SetItemNode extends NormalizingNode {
        @Child private GeneralizationNode generalizationNode;

        private final Supplier<GeneralizationNode> generalizationNodeProvider;

        public SetItemNode(NormalizeIndexNode normalizeIndexNode, Supplier<GeneralizationNode> generalizationNodeProvider) {
            super(normalizeIndexNode);
            this.generalizationNodeProvider = generalizationNodeProvider;
        }

        public abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage s, Object key, Object value);

        public abstract SequenceStorage executeInt(VirtualFrame frame, SequenceStorage s, int key, Object value);

        public abstract SequenceStorage executeLong(VirtualFrame frame, SequenceStorage s, long key, Object value);

        @Specialization
        protected SequenceStorage doScalarInt(VirtualFrame frame, SequenceStorage storage, int idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode) {
            int normalized = normalizeIndex(frame, idx, storage);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                try {
                    setItemScalarNode.execute(generalized, normalized, value);
                } catch (SequenceStoreException e1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
                return generalized;
            }
        }

        @Specialization
        protected SequenceStorage doScalarLong(VirtualFrame frame, SequenceStorage storage, long idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode) {
            int normalized = normalizeIndex(frame, idx, storage);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                setItemScalarNode.execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization
        protected SequenceStorage doScalarPInt(VirtualFrame frame, SequenceStorage storage, PInt idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode) {
            int normalized = normalizeIndex(frame, idx, storage);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                setItemScalarNode.execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected SequenceStorage doScalarGeneric(VirtualFrame frame, SequenceStorage storage, Object idx, Object value,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Shared("setItemScalarNode") @Cached SetItemScalarNode setItemScalarNode) {
            int normalized = normalizeIndex(frame, idx, storage);
            try {
                setItemScalarNode.execute(storage, normalized, value);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                setItemScalarNode.execute(generalized, normalized, value);
                return generalized;
            }
        }

        @Specialization
        protected SequenceStorage doSliceSequence(VirtualFrame frame, SequenceStorage storage, PSlice slice, PSequence sequence,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Cached SetItemSliceNode setItemSliceNode,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached LenNode lenNode,
                        @Cached SliceLiteralNode.SliceUnpack unpack,
                        @Cached SliceLiteralNode.AdjustIndices adjustIndices) {
            SliceInfo unadjusted = unpack.execute(sliceCast.execute(slice));
            int len = lenNode.execute(storage);
            SliceInfo info = adjustIndices.execute(len, unadjusted);
            try {
                setItemSliceNode.execute(frame, storage, info, sequence, true);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                setItemSliceNode.execute(frame, generalized, info, sequence, false);
                return generalized;
            }
        }

        @Specialization(replaces = "doSliceSequence")
        protected SequenceStorage doSliceGeneric(VirtualFrame frame, SequenceStorage storage, PSlice slice, Object iterable,
                        @Shared("generalizeProfile") @Cached BranchProfile generalizeProfile,
                        @Cached SetItemSliceNode setItemSliceNode,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached LenNode lenNode,
                        @Cached SliceLiteralNode.SliceUnpack unpack,
                        @Cached SliceLiteralNode.AdjustIndices adjustIndices) {
            SliceInfo unadjusted = unpack.execute(sliceCast.execute(slice));
            int len = lenNode.execute(storage);
            SliceInfo info = adjustIndices.execute(len, unadjusted);

            // We need to construct the list eagerly because if a SequenceStoreException occurs, we
            // must not use iterable again. It could have sice-effects.
            PList values = constructListNode.execute(frame, iterable);
            try {
                setItemSliceNode.execute(frame, storage, info, values, true);
                return storage;
            } catch (SequenceStoreException e) {
                generalizeProfile.enter();
                SequenceStorage generalized = generalizeStore(storage, e.getIndicationValue());
                setItemSliceNode.execute(frame, generalized, info, values, false);
                return generalized;
            }
        }

        private SequenceStorage generalizeStore(SequenceStorage storage, Object value) {
            if (generalizationNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                generalizationNode = insert(generalizationNodeProvider.get());
            }
            return generalizationNode.execute(storage, value);
        }

        public static SetItemNode create(NormalizeIndexNode normalizeIndexNode, Supplier<GeneralizationNode> generalizationNodeProvider) {
            return SetItemNodeGen.create(normalizeIndexNode, generalizationNodeProvider);
        }

        public static SetItemNode create(NormalizeIndexNode normalizeIndexNode, String invalidItemErrorMessage) {
            return SetItemNodeGen.create(normalizeIndexNode, () -> NoGeneralizationCustomMessageNode.create(invalidItemErrorMessage));
        }

        public static SetItemNode create(String invalidItemErrorMessage) {
            return SetItemNodeGen.create(NormalizeIndexNode.create(), () -> NoGeneralizationCustomMessageNode.create(invalidItemErrorMessage));
        }

    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class SetItemScalarNode extends Node {

        public abstract void execute(SequenceStorage s, int idx, Object value);

        public abstract void execute(SequenceStorage s, int idx, byte value);

        @Specialization
        protected static void doBoolean(BoolSequenceStorage storage, int idx, boolean value) {
            storage.setBoolItemNormalized(idx, value);
        }

        @Specialization
        protected static void doByteSimple(ByteSequenceStorage storage, int idx, byte value) {
            storage.setByteItemNormalized(idx, value);
        }

        @Specialization(replaces = "doByteSimple")
        protected static void doByte(ByteSequenceStorage storage, int idx, Object value,
                        @Shared("castToByteNode") @Cached CastToByteNode castToByteNode) {
            // TODO: clean this up, we really might need a frame
            storage.setByteItemNormalized(idx, castToByteNode.execute(null, value));
        }

        @Specialization
        protected static void doInt(IntSequenceStorage storage, int idx, int value) {
            storage.setIntItemNormalized(idx, value);
        }

        @Specialization(rewriteOn = OverflowException.class)
        protected static void doIntL(IntSequenceStorage storage, int idx, long value) throws OverflowException {
            storage.setIntItemNormalized(idx, PInt.intValueExact(value));
        }

        @Specialization(replaces = "doIntL")
        protected static void doIntLOvf(IntSequenceStorage storage, int idx, long value) {
            try {
                storage.setIntItemNormalized(idx, PInt.intValueExact(value));
            } catch (OverflowException e) {
                throw new SequenceStoreException(value);
            }
        }

        @Specialization(guards = "!value.isNative()")
        protected static void doInt(IntSequenceStorage storage, int idx, PInt value) {
            try {
                storage.setIntItemNormalized(idx, value.intValueExact());
            } catch (OverflowException e) {
                throw new SequenceStoreException(value);
            }
        }

        @Specialization
        protected static void doLong(LongSequenceStorage storage, int idx, long value) {
            storage.setLongItemNormalized(idx, value);
        }

        @Specialization
        protected static void doLong(LongSequenceStorage storage, int idx, int value) {
            storage.setLongItemNormalized(idx, value);
        }

        @Specialization(guards = "!value.isNative()")
        protected static void doLong(LongSequenceStorage storage, int idx, PInt value) {
            try {
                storage.setLongItemNormalized(idx, value.longValueExact());
            } catch (OverflowException e) {
                throw new SequenceStoreException(value);
            }
        }

        @Specialization
        protected static void doDouble(DoubleSequenceStorage storage, int idx, double value) {
            storage.setDoubleItemNormalized(idx, value);
        }

        @Specialization
        protected static void doObject(ObjectSequenceStorage storage, int idx, Object value) {
            storage.setItemNormalized(idx, value);
        }

        @Specialization(guards = "isByteStorage(storage)")
        protected static void doNativeByte(NativeSequenceStorage storage, int idx, Object value,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Shared("castToByteNode") @Cached CastToByteNode castToByteNode) {
            try {
                lib.writeArrayElement(storage.getPtr(), idx, castToByteNode.execute(null, value));
            } catch (UnsupportedMessageException | UnsupportedTypeException | InvalidArrayIndexException e) {
                throw raiseNode.raise(SystemError, e);
            }
        }

        @Specialization
        protected static void doNative(NativeSequenceStorage storage, int idx, Object value,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached VerifyNativeItemNode verifyNativeItemNode) {
            try {
                lib.writeArrayElement(storage.getPtr(), idx, verifyValue(storage, value, verifyNativeItemNode));
            } catch (UnsupportedMessageException | UnsupportedTypeException | InvalidArrayIndexException e) {
                throw raiseNode.raise(SystemError, e);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static void doError(SequenceStorage s, int idx, Object item) {
            throw new SequenceStoreException(item);
        }

        private static Object verifyValue(NativeSequenceStorage storage, Object item, VerifyNativeItemNode verifyNativeItemNode) {
            if (verifyNativeItemNode.execute(storage.getElementType(), item)) {
                return item;
            }
            throw new SequenceStoreException(item);
        }

        public static SetItemScalarNode create() {
            return SetItemScalarNodeGen.create();
        }
    }

    @GenerateUncached
    @ImportStatic({ListStorageType.class, SequenceStorageBaseNode.class})
    public abstract static class SetItemSliceNode extends Node {

        public abstract void execute(Frame frame, SequenceStorage s, SliceInfo info, Object iterable, boolean canGeneralize);

        public final void execute(Frame frame, SequenceStorage s, SliceInfo info, Object iterable) {
            execute(frame, s, info, iterable, true);
        }

        @Specialization(guards = "hasStorage(seq)")
        static void doStorage(SequenceStorage s, SliceInfo info, PSequence seq, boolean canGeneralize,
                        @Shared("setStorageSliceNode") @Cached SetStorageSliceNode setStorageSliceNode,
                        @Cached GetSequenceStorageNode getSequenceStorageNode) {
            setStorageSliceNode.execute(s, info, getSequenceStorageNode.execute(seq), canGeneralize);
        }

        @Specialization
        static void doGeneric(VirtualFrame frame, SequenceStorage s, SliceInfo info, Object iterable, boolean canGeneralize,
                        @Shared("setStorageSliceNode") @Cached SetStorageSliceNode setStorageSliceNode,
                        @Cached ListNodes.ConstructListNode constructListNode) {
            PList list = constructListNode.execute(frame, iterable);
            setStorageSliceNode.execute(s, info, list.getSequenceStorage(), canGeneralize);
        }

        public static SetItemSliceNode create() {
            return SetItemSliceNodeGen.create();
        }

        public static SetItemSliceNode getUncached() {
            return SetItemSliceNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class MemMoveNode extends Node {

        public abstract void execute(SequenceStorage s, int distPos, int srcPos, int length);

        @SuppressWarnings("unused")
        @Specialization(guards = "length <= 0")
        protected static void nothing(SequenceStorage storage, int distPos, int srcPos, int length) {
        }

        @Specialization(limit = "MAX_ARRAY_STORAGES", guards = {"length > 0", "storage.getClass() == cachedClass"})
        protected static void doMove(BasicSequenceStorage storage, int distPos, int srcPos, int length,
                        @Cached("storage.getClass()") Class<? extends BasicSequenceStorage> cachedClass) {
            Object array = cachedClass.cast(storage).getInternalArrayObject();
            PythonUtils.arraycopy(array, srcPos, array, distPos, length);
        }

        @Specialization(guards = {"length > 0", "!isBasicSequenceStorage(storage)"})
        protected static void doOther(SequenceStorage storage, int distPos, int srcPos, int length,
                        @Cached SetItemScalarNode setLeftItemNode,
                        @Cached GetItemScalarNode getRightItemNode) {
            for (int cur = distPos, j = srcPos, i = 0; i < length; cur += 1, j++, i++) {
                setLeftItemNode.execute(storage, cur, getRightItemNode.execute(storage, j));
            }
        }

        protected static boolean isBasicSequenceStorage(Object o) {
            return o instanceof BasicSequenceStorage;
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class MemCopyNode extends Node {

        public abstract void execute(SequenceStorage dist, int distPos, SequenceStorage src, int srcPos, int length);

        @SuppressWarnings("unused")
        @Specialization(guards = "length <= 0")
        protected static void nothing(SequenceStorage dist, int distPos, SequenceStorage src, int srcPos, int length) {
        }

        @Specialization(limit = "MAX_ARRAY_STORAGES", guards = {"length > 0", "dist.getClass() == cachedClass", "src.getClass() == dist.getClass()"})
        protected static void doCopy(BasicSequenceStorage dist, int distPos, BasicSequenceStorage src, int srcPos, int length,
                        @Cached("dist.getClass()") Class<? extends BasicSequenceStorage> cachedClass) {
            Object distArray = cachedClass.cast(dist).getInternalArrayObject();
            Object srcArray = cachedClass.cast(src).getInternalArrayObject();
            PythonUtils.arraycopy(srcArray, srcPos, distArray, distPos, length);
        }

        @Specialization(guards = {"length > 0", "!isBasicSequenceStorage(dist) || dist.getClass() != src.getClass()"})
        protected static void doOther(SequenceStorage dist, int distPos, SequenceStorage src, int srcPos, int length,
                        @Cached SetItemScalarNode setLeftItemNode,
                        @Cached GetItemScalarNode getRightItemNode) {
            for (int cur = distPos, j = srcPos, i = 0; i < length; cur += 1, j++, i++) {
                setLeftItemNode.execute(dist, cur, getRightItemNode.execute(src, j));
            }
        }

        protected static boolean isBasicSequenceStorage(Object o) {
            return o instanceof BasicSequenceStorage;
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    abstract static class SetStorageSliceNode extends Node {

        public abstract void execute(SequenceStorage s, SliceInfo info, SequenceStorage iterable, boolean canGeneralize);

        @Specialization(limit = "MAX_ARRAY_STORAGES", guards = {"self.getClass() == cachedClass", "self.getClass() == sequence.getClass()", "replacesWholeSequence(cachedClass, self, info)"})
        static void doWholeSequence(BasicSequenceStorage self, @SuppressWarnings("unused") SliceInfo info, BasicSequenceStorage sequence, @SuppressWarnings("unused") boolean canGeneralize,
                        @Cached("self.getClass()") Class<? extends BasicSequenceStorage> cachedClass) {
            BasicSequenceStorage selfProfiled = cachedClass.cast(self);
            BasicSequenceStorage otherProfiled = cachedClass.cast(sequence);
            selfProfiled.setInternalArrayObject(otherProfiled.getCopyOfInternalArrayObject());
            selfProfiled.setNewLength(otherProfiled.length());
            selfProfiled.minimizeCapacity();
        }

        @Specialization(guards = {"!canGeneralize || isDataTypeCompatibleNode.execute(self, values)", "sinfo.step == 1"})
        static void singleStep(SequenceStorage self, SliceInfo sinfo, SequenceStorage values, @SuppressWarnings("unused") boolean canGeneralize,
                        @Cached @SuppressWarnings("unused") IsDataTypeCompatibleNode isDataTypeCompatibleNode,
                        @Cached LenNode lenNode,
                        @Cached SetLenNode setLenNode,
                        @Cached EnsureCapacityNode ensureCapacityNode,
                        @Cached MemMoveNode memove,
                        @Cached MemCopyNode memcpy,
                        @Cached CopyNode copyNode,
                        @Cached ConditionProfile memoryError,
                        @Cached ConditionProfile negGrowth,
                        @Cached ConditionProfile posGrowth,
                        @Cached PRaiseNode raiseNode) {
            int start = sinfo.start;
            int stop = sinfo.stop;
            int step = sinfo.step;

            SequenceStorage data = (values == self) ? copyNode.execute(values) : values;
            int needed = lenNode.execute(data);
            /*- Make sure b[5:2] = ... inserts before 5, not before 2. */
            if ((step < 0 && start < stop) || (step > 0 && start > stop)) {
                stop = start;
            }
            singleStep(self, start, stop, data, needed,
                            lenNode, setLenNode, ensureCapacityNode, memove, memcpy,
                            memoryError, negGrowth, posGrowth, raiseNode);
        }

        @Specialization(guards = {"!canGeneralize || isDataTypeCompatibleNode.execute(self, values)", "sinfo.step != 1"})
        static void multiStep(SequenceStorage self, SliceInfo sinfo, SequenceStorage values, @SuppressWarnings("unused") boolean canGeneralize,
                        @Cached @SuppressWarnings("unused") IsDataTypeCompatibleNode isDataTypeCompatibleNode,
                        @Cached ConditionProfile wrongLength,
                        @Cached ConditionProfile deleteSlice,
                        @Cached LenNode lenNode,
                        @Cached SetLenNode setLenNode,
                        @Cached EnsureCapacityNode ensureCapacityNode,
                        @Cached MemMoveNode memove,
                        @Cached SetItemScalarNode setLeftItemNode,
                        @Cached GetItemScalarNode getRightItemNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached CopyNode copyNode) {
            int start = sinfo.start;
            int step = sinfo.step;
            int slicelen = sinfo.sliceLength;
            assert slicelen != -1 : "slice info has not been adjusted";

            SequenceStorage data = (values == self) ? copyNode.execute(values) : values;
            int needed = lenNode.execute(data);
            if (deleteSlice.profile(needed == 0)) {
                DeleteSliceNode.multipleSteps(self, sinfo,
                                lenNode, setLenNode, ensureCapacityNode, memove);
            } else {
                /*- Assign slice */
                if (wrongLength.profile(needed != slicelen)) {
                    raiseNode.raise(ValueError, ErrorMessages.ATTEMPT_TO_ASSIGN_SEQ_OF_SIZE_TO_SLICE_OF_SIZE, needed, slicelen);
                }
                for (int cur = start, i = 0; i < slicelen; cur += step, i++) {
                    setLeftItemNode.execute(self, cur, getRightItemNode.execute(data, i));
                }
            }
        }

        @Specialization(guards = {"canGeneralize", "!isAssignCompatibleNode.execute(self, sequence)"})
        static void doError(@SuppressWarnings("unused") SequenceStorage self, @SuppressWarnings("unused") SliceInfo info, SequenceStorage sequence, @SuppressWarnings("unused") boolean canGeneralize,
                        @Cached @SuppressWarnings("unused") IsAssignCompatibleNode isAssignCompatibleNode) {
            throw new SequenceStoreException(sequence.getIndicativeValue());
        }

        /**
         * based on CPython/Objects/listobject.c#list_ass_subscript and
         * CPython/Objects/bytearrayobject.c#bytearray_ass_subscript
         */
        static void singleStep(SequenceStorage self, int lo, int hi, SequenceStorage data, int needed,
                        LenNode selfLenNode,
                        SetLenNode setLenNode,
                        EnsureCapacityNode ensureCapacityNode,
                        MemMoveNode memove,
                        MemCopyNode memcpy,
                        ConditionProfile memoryError,
                        ConditionProfile negGrowth,
                        ConditionProfile posGrowth,
                        PRaiseNode raiseNode) {
            int avail = hi - lo;
            int growth = needed - avail;
            assert avail >= 0 : "sliceInfo.start and sliceInfo.stop have not been adjusted.";
            int len = selfLenNode.execute(self);

            if (negGrowth.profile(growth < 0)) {
                // ensure capacity will check if the storage can be resized.
                ensureCapacityNode.execute(self, len + growth);

                // We are shrinking the list here

                /*-
                TODO: it might be a good idea to implement this logic
                if (lo == 0)
                Shrink the buffer by advancing its logical start
                  0   lo               hi             old_size
                  |   |<----avail----->|<-----tail------>|
                  |      |<-bytes_len->|<-----tail------>|
                  0    new_lo         new_hi          new_size
                */

                // For now we are doing the generic approach
                /*-
                  0   lo               hi               old_size
                  |   |<----avail----->|<-----tomove------>|
                  |   |<-bytes_len->|<-----tomove------>|
                  0   lo         new_hi              new_size
                */
                memove.execute(self, lo + needed, hi, len - hi);
                setLenNode.execute(self, len + growth); // growth is negative (Shrinking)
            } else if (posGrowth.profile(growth > 0)) {
                // ensure capacity will check if the storage can be resized.
                ensureCapacityNode.execute(self, len + growth);

                if (memoryError.profile(len > Integer.MAX_VALUE - growth)) {
                    throw raiseNode.raise(MemoryError);
                }

                len += growth;
                setLenNode.execute(self, len);

                /*- Make the place for the additional bytes */
                /*-
                  0   lo        hi               old_size
                  |   |<-avail->|<-----tomove------>|
                  |   |<---bytes_len-->|<-----tomove------>|
                  0   lo            new_hi              new_size
                 */
                memove.execute(self, lo + needed, hi, len - lo - needed);
            }

            if (needed > 0) {
                memcpy.execute(self, lo, data, 0, needed);
            }
        }

        protected static boolean replacesWholeSequence(Class<? extends BasicSequenceStorage> cachedClass, BasicSequenceStorage s, SliceInfo info) {
            return info.start == 0 && info.step == 1 && info.stop == cachedClass.cast(s).length();
        }
    }

    @GenerateUncached
    abstract static class VerifyNativeItemNode extends Node {

        public abstract boolean execute(ListStorageType expectedType, Object item);

        @Specialization(guards = "elementType == cachedElementType", limit = "1")
        static boolean doCached(@SuppressWarnings("unused") ListStorageType elementType, Object item,
                        @Cached("elementType") ListStorageType cachedElementType,
                        @Shared("profile") @Cached ConditionProfile profile) {
            return doGeneric(cachedElementType, item, profile);
        }

        @Specialization(replaces = "doCached")
        static boolean doGeneric(ListStorageType expectedType, Object item,
                        @Shared("profile") @Cached ConditionProfile profile) {
            boolean res = false;
            switch (expectedType) {
                case Byte:
                    res = item instanceof Byte;
                    break;
                case Int:
                    res = item instanceof Integer;
                    break;
                case Long:
                    res = item instanceof Long;
                    break;
                case Double:
                    res = item instanceof Double;
                    break;
                case Generic:
                    res = !(item instanceof Byte || item instanceof Integer || item instanceof Long || item instanceof Double);
                    break;
            }
            return profile.profile(res);
        }

        public static VerifyNativeItemNode create() {
            return VerifyNativeItemNodeGen.create();
        }

        public static VerifyNativeItemNode getUncached() {
            return VerifyNativeItemNodeGen.getUncached();
        }
    }

    @ImportStatic(NativeCAPISymbol.class)
    @GenerateUncached
    public abstract static class StorageToNativeNode extends Node {

        public abstract NativeSequenceStorage execute(Object obj);

        @Specialization
        static NativeSequenceStorage doByte(byte[] arr,
                        @Exclusive @Cached PCallCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.call(FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE, wrap(PythonContext.get(callNode), arr), arr.length), arr.length, arr.length, ListStorageType.Byte);
        }

        @Specialization
        static NativeSequenceStorage doInt(int[] arr,
                        @Exclusive @Cached PCallCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.call(FUN_PY_TRUFFLE_INT_ARRAY_TO_NATIVE, wrap(PythonContext.get(callNode), arr), arr.length), arr.length, arr.length, ListStorageType.Int);
        }

        @Specialization
        static NativeSequenceStorage doLong(long[] arr,
                        @Exclusive @Cached PCallCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.call(FUN_PY_TRUFFLE_LONG_ARRAY_TO_NATIVE, wrap(PythonContext.get(callNode), arr), arr.length), arr.length, arr.length, ListStorageType.Long);
        }

        @Specialization
        static NativeSequenceStorage doDouble(double[] arr,
                        @Exclusive @Cached PCallCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.call(FUN_PY_TRUFFLE_DOUBLE_ARRAY_TO_NATIVE, wrap(PythonContext.get(callNode), arr), arr.length), arr.length, arr.length, ListStorageType.Double);
        }

        @Specialization
        static NativeSequenceStorage doObject(Object[] arr,
                        @Exclusive @Cached PCallCapiFunction callNode,
                        @Exclusive @Cached ToSulongNode toSulongNode) {
            Object[] wrappedValues = new Object[arr.length];
            for (int i = 0; i < wrappedValues.length; i++) {
                wrappedValues[i] = toSulongNode.execute(arr[i]);
            }
            return new NativeSequenceStorage(callNode.call(FUN_PY_TRUFFLE_OBJECT_ARRAY_TO_NATIVE, wrap(PythonContext.get(callNode), wrappedValues), wrappedValues.length), wrappedValues.length,
                            wrappedValues.length,
                            ListStorageType.Generic);
        }

        private static Object wrap(PythonContext context, Object arr) {
            return context.getEnv().asGuestValue(arr);
        }

        public static StorageToNativeNode create() {
            return StorageToNativeNodeGen.create();
        }

        public static StorageToNativeNode getUncached() {
            return StorageToNativeNodeGen.getUncached();
        }
    }

    public abstract static class CmpNode extends SequenceStorageBaseNode {
        @Child private GetItemScalarNode getItemNode;
        @Child private GetItemScalarNode getRightItemNode;
        @Child private BinaryComparisonNode cmpOp;
        @Child private CoerceToBooleanNode castToBooleanNode;

        @Child private LenNode lenNode;

        protected CmpNode(BinaryComparisonNode cmpOp) {
            this.cmpOp = cmpOp;
        }

        public abstract boolean execute(VirtualFrame frame, SequenceStorage left, SequenceStorage right);

        protected boolean isEmpty(SequenceStorage left) {
            return getLenNode().execute(left) == 0;
        }

        private LenNode getLenNode() {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(LenNode.create());
            }
            return lenNode;
        }

        private boolean testingEqualsWithDifferingLengths(int llen, int rlen) {
            // shortcut: if the lengths differ, the lists differ.
            CompilerAsserts.compilationConstant(cmpOp.getClass());
            if (cmpOp instanceof BinaryComparisonNode.EqNode) {
                if (llen != rlen) {
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isEmpty(left)", "isEmpty(right)"})
        boolean doEmpty(SequenceStorage left, SequenceStorage right) {
            return cmpOp.cmp(0, 0);
        }

        @Specialization
        boolean doBoolStorage(BoolSequenceStorage left, BoolSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            if (testingEqualsWithDifferingLengths(llen, rlen)) {
                return false;
            }
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                int litem = PInt.intValue(left.getBoolItemNormalized(i));
                int ritem = PInt.intValue(right.getBoolItemNormalized(i));
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doByteStorage(ByteSequenceStorage left, ByteSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            if (testingEqualsWithDifferingLengths(llen, rlen)) {
                return false;
            }
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                byte litem = left.getByteItemNormalized(i);
                byte ritem = right.getByteItemNormalized(i);
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doIntStorage(IntSequenceStorage left, IntSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            if (testingEqualsWithDifferingLengths(llen, rlen)) {
                return false;
            }
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                int litem = left.getIntItemNormalized(i);
                int ritem = right.getIntItemNormalized(i);
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doLongStorage(LongSequenceStorage left, LongSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            if (testingEqualsWithDifferingLengths(llen, rlen)) {
                return false;
            }
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                long litem = left.getLongItemNormalized(i);
                long ritem = right.getLongItemNormalized(i);
                if (litem != ritem) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doDoubleStorage(DoubleSequenceStorage left, DoubleSequenceStorage right) {
            int llen = left.length();
            int rlen = right.length();
            if (testingEqualsWithDifferingLengths(llen, rlen)) {
                return false;
            }
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                double litem = left.getDoubleItemNormalized(i);
                double ritem = right.getDoubleItemNormalized(i);
                if (java.lang.Double.compare(litem, ritem) != 0) {
                    return cmpOp.cmp(litem, ritem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        @Specialization
        boolean doGeneric(VirtualFrame frame, SequenceStorage left, SequenceStorage right,
                        @Cached PyObjectRichCompareBool.EqNode eqNode) {
            int llen = getLenNode().execute(left);
            int rlen = getLenNode().execute(right);
            if (testingEqualsWithDifferingLengths(llen, rlen)) {
                return false;
            }
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                Object leftItem = getGetItemNode().execute(left, i);
                Object rightItem = getGetRightItemNode().execute(right, i);
                if (!eqNode.execute(frame, leftItem, rightItem)) {
                    return cmpGeneric(frame, leftItem, rightItem);
                }
            }
            return cmpOp.cmp(llen, rlen);
        }

        private GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        private GetItemScalarNode getGetRightItemNode() {
            if (getRightItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getRightItemNode = insert(GetItemScalarNode.create());
            }
            return getRightItemNode;
        }

        private boolean cmpGeneric(VirtualFrame frame, Object left, Object right) {
            return castToBoolean(frame, cmpOp.executeObject(frame, left, right));
        }

        private boolean castToBoolean(VirtualFrame frame, Object value) {
            if (castToBooleanNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToBooleanNode = insert(CoerceToBooleanNode.createIfTrueNode());
            }
            return castToBooleanNode.executeBoolean(frame, value);
        }

        public static CmpNode createLe() {
            return CmpNodeGen.create(BinaryComparisonNode.LeNode.create());
        }

        public static CmpNode createLt() {
            return CmpNodeGen.create(BinaryComparisonNode.LtNode.create());
        }

        public static CmpNode createGe() {
            return CmpNodeGen.create(BinaryComparisonNode.GeNode.create());
        }

        public static CmpNode createGt() {
            return CmpNodeGen.create(BinaryComparisonNode.GtNode.create());
        }

        public static CmpNode createEq() {
            return CmpNodeGen.create(BinaryComparisonNode.EqNode.create());
        }

    }

    /**
     * Will try to get the internal byte[]. Otherwise, it will get a copy. Please note that the
     * actual length of the storage and the internal storage might differ.
     */
    public abstract static class GetInternalBytesNode extends PNodeWithContext {

        public final byte[] execute(PBytesLike bytes) {
            return execute(null, bytes);
        }

        public abstract byte[] execute(VirtualFrame frame, Object bytes);

        protected static boolean isByteSequenceStorage(PBytesLike bytes) {
            return bytes.getSequenceStorage() instanceof ByteSequenceStorage;
        }

        protected static boolean isSimple(Object bytes) {
            return bytes instanceof PBytesLike && isByteSequenceStorage((PBytesLike) bytes);
        }

        @Specialization(guards = "isByteSequenceStorage(bytes)")
        static byte[] doBytes(PBytesLike bytes,
                        @Cached SequenceStorageNodes.GetInternalArrayNode internalArray) {
            return (byte[]) internalArray.execute(bytes.getSequenceStorage());
        }

        @Specialization(guards = "!isSimple(bytes)")
        static byte[] doGeneric(VirtualFrame frame, Object bytes,
                        @Cached BytesNodes.ToBytesNode toBytesNode) {
            return toBytesNode.execute(frame, bytes);
        }
    }

    /**
     * Use this node to get the internal byte array of the storage (if possible) to avoid copying.
     * Otherwise, it will create a copy with the exact size of the stored data.
     */
    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class GetInternalByteArrayNode extends PNodeWithContext {

        public abstract byte[] execute(SequenceStorage s);

        @Specialization
        static byte[] doByteSequenceStorage(ByteSequenceStorage s) {
            return s.getInternalByteArray();
        }

        @Specialization(guards = "isByteStorage(s)")
        static byte[] doNativeByte(NativeSequenceStorage s,
                        @Shared("getItemNode") @Cached GetItemScalarNode getItemNode) {
            byte[] barr = new byte[s.length()];
            for (int i = 0; i < barr.length; i++) {
                int elem = getItemNode.executeInt(s, i);
                assert elem >= 0 && elem < 256;
                barr[i] = (byte) elem;
            }
            return barr;
        }

        @Specialization(guards = {"len(lenNode, s) == cachedLen", "cachedLen <= 32"}, limit = "1")
        @ExplodeLoop
        static byte[] doGenericLenCached(SequenceStorage s,
                        @Shared("getItemNode") @Cached GetItemScalarNode getItemNode,
                        @Cached CastToJavaByteNode castToByteNode,
                        @Cached @SuppressWarnings("unused") LenNode lenNode,
                        @Cached("len(lenNode, s)") int cachedLen) {
            byte[] barr = new byte[cachedLen];
            for (int i = 0; i < cachedLen; i++) {
                barr[i] = castToByteNode.execute(getItemNode.execute(s, i));
            }
            return barr;
        }

        @Specialization(replaces = "doGenericLenCached")
        static byte[] doGeneric(SequenceStorage s,
                        @Shared("getItemNode") @Cached GetItemScalarNode getItemNode,
                        @Cached CastToJavaByteNode castToByteNode,
                        @Cached LenNode lenNode) {
            byte[] barr = new byte[lenNode.execute(s)];
            for (int i = 0; i < barr.length; i++) {
                barr[i] = castToByteNode.execute(getItemNode.execute(s, i));
            }
            return barr;
        }

        protected static int len(LenNode lenNode, SequenceStorage s) {
            return lenNode.execute(s);
        }

        public static GetInternalByteArrayNode getUncached() {
            return SequenceStorageNodesFactory.GetInternalByteArrayNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class ToByteArrayNode extends Node {

        public abstract byte[] execute(SequenceStorage s);

        @Specialization
        static byte[] doByteSequenceStorage(ByteSequenceStorage s,
                        @Cached ConditionProfile profile) {
            byte[] bytes = GetInternalByteArrayNode.doByteSequenceStorage(s);
            int storageLength = s.length();
            if (profile.profile(storageLength != bytes.length)) {
                return exactCopy(bytes, storageLength);
            }
            return bytes;
        }

        @Specialization(guards = "!isByteSequenceStorage(s)")
        static byte[] doOther(SequenceStorage s,
                        @Cached GetInternalByteArrayNode getInternalByteArrayNode) {
            return getInternalByteArrayNode.execute(s);
        }

        private static byte[] exactCopy(byte[] barr, int len) {
            return PythonUtils.arrayCopyOf(barr, len);
        }

        static boolean isByteSequenceStorage(SequenceStorage s) {
            return s instanceof ByteSequenceStorage;
        }

    }

    abstract static class ConcatBaseNode extends SequenceStorageBaseNode {

        @Child private SetItemScalarNode setItemNode;
        @Child private GetItemScalarNode getItemNode;
        @Child private GetItemScalarNode getRightItemNode;
        @Child private SetLenNode setLenNode;

        public abstract SequenceStorage execute(SequenceStorage dest, SequenceStorage left, SequenceStorage right);

        @Specialization(guards = "!isNative(right)")
        static SequenceStorage doLeftEmpty(@SuppressWarnings("unused") EmptySequenceStorage dest, @SuppressWarnings("unused") EmptySequenceStorage left, SequenceStorage right,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("outOfMemProfile") @Cached BranchProfile outOfMemProfile,
                        @Shared("copyNode") @Cached CopyNode copyNode) {
            try {
                return copyNode.execute(right);
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            }
        }

        @Specialization(guards = "!isNative(left)")
        static SequenceStorage doRightEmpty(@SuppressWarnings("unused") EmptySequenceStorage dest, SequenceStorage left, @SuppressWarnings("unused") EmptySequenceStorage right,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("outOfMemProfile") @Cached BranchProfile outOfMemProfile,
                        @Shared("copyNode") @Cached CopyNode copyNode) {
            try {
                return copyNode.execute(left);
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            }
        }

        @Specialization(guards = {"dest.getClass() == left.getClass()", "left.getClass() == right.getClass()", "!isNative(dest)", "cachedClass == dest.getClass()"})
        SequenceStorage doManagedManagedSameType(SequenceStorage dest, SequenceStorage left, SequenceStorage right,
                        @Cached("left.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage destProfiled = cachedClass.cast(dest);
            SequenceStorage leftProfiled = cachedClass.cast(left);
            SequenceStorage rightProfiled = cachedClass.cast(right);
            Object arr1 = leftProfiled.getInternalArrayObject();
            int len1 = leftProfiled.length();
            Object arr2 = rightProfiled.getInternalArrayObject();
            int len2 = rightProfiled.length();
            concat(destProfiled.getInternalArrayObject(), arr1, len1, arr2, len2);
            getSetLenNode().execute(destProfiled, len1 + len2);
            return destProfiled;
        }

        @Specialization(guards = {"dest.getClass() == right.getClass()", "!isNative(dest)", "cachedClass == dest.getClass()"})
        SequenceStorage doEmptyManagedSameType(SequenceStorage dest, @SuppressWarnings("unused") EmptySequenceStorage left, SequenceStorage right,
                        @Cached("left.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage destProfiled = cachedClass.cast(dest);
            SequenceStorage rightProfiled = cachedClass.cast(right);
            Object arr2 = rightProfiled.getInternalArrayObject();
            int len2 = rightProfiled.length();
            PythonUtils.arraycopy(arr2, 0, destProfiled.getInternalArrayObject(), 0, len2);
            getSetLenNode().execute(destProfiled, len2);
            return destProfiled;
        }

        @Specialization(guards = {"dest.getClass() == left.getClass()", "!isNative(dest)", "cachedClass == dest.getClass()"})
        SequenceStorage doManagedEmptySameType(SequenceStorage dest, SequenceStorage left, @SuppressWarnings("unused") EmptySequenceStorage right,
                        @Cached("left.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage destProfiled = cachedClass.cast(dest);
            SequenceStorage leftProfiled = cachedClass.cast(left);
            Object arr1 = leftProfiled.getInternalArrayObject();
            int len1 = leftProfiled.length();
            PythonUtils.arraycopy(arr1, 0, destProfiled.getInternalArrayObject(), 0, len1);
            getSetLenNode().execute(destProfiled, len1);
            return destProfiled;
        }

        @Specialization
        SequenceStorage doGeneric(SequenceStorage dest, SequenceStorage left, SequenceStorage right,
                        @Cached LenNode lenNode) {
            int len1 = lenNode.execute(left);
            int len2 = lenNode.execute(right);
            for (int i = 0; i < len1; i++) {
                getSetItemNode().execute(dest, i, getGetItemNode().execute(left, i));
            }
            for (int i = 0; i < len2; i++) {
                getSetItemNode().execute(dest, i + len1, getGetRightItemNode().execute(right, i));
            }
            getSetLenNode().execute(dest, len1 + len2);
            return dest;
        }

        private SetItemScalarNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(SetItemScalarNode.create());
            }
            return setItemNode;
        }

        private GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        private GetItemScalarNode getGetRightItemNode() {
            if (getRightItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getRightItemNode = insert(GetItemScalarNode.create());
            }
            return getRightItemNode;
        }

        private SetLenNode getSetLenNode() {
            if (setLenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setLenNode = insert(SetLenNode.create());
            }
            return setLenNode;
        }

        private static void concat(Object dest, Object arr1, int len1, Object arr2, int len2) {
            PythonUtils.arraycopy(arr1, 0, dest, 0, len1);
            PythonUtils.arraycopy(arr2, 0, dest, len1, len2);
        }

        public static ConcatBaseNode create() {
            return ConcatBaseNodeGen.create();
        }
    }

    /**
     * Concatenates two sequence storages; creates a storage of a suitable type and writes the
     * result to the new storage.
     */
    public abstract static class ConcatNode extends SequenceStorageBaseNode {
        private static final String DEFAULT_ERROR_MSG = ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP;

        @Child private ConcatBaseNode concatBaseNode = ConcatBaseNodeGen.create();
        @Child private CreateEmptyNode createEmptyNode = CreateEmptyNode.create();
        @Child private GeneralizationNode genNode;

        private final Supplier<GeneralizationNode> genNodeProvider;

        /*
         * CPython is inconsistent when too repeats are done. Most types raise MemoryError, but e.g.
         * bytes raises OverflowError when the memory might be available but the size overflows
         * sys.maxint
         */
        private final PythonBuiltinClassType errorForOverflow;

        ConcatNode(Supplier<GeneralizationNode> genNodeProvider, PythonBuiltinClassType errorForOverflow) {
            this.genNodeProvider = genNodeProvider;
            this.errorForOverflow = errorForOverflow;
        }

        public abstract SequenceStorage execute(SequenceStorage left, SequenceStorage right);

        @Specialization
        SequenceStorage doRight(SequenceStorage left, SequenceStorage right,
                        @Cached ConditionProfile shouldOverflow,
                        @Cached PRaiseNode raiseNode,
                        @Cached LenNode lenNode,
                        @Cached BranchProfile outOfMemProfile) {
            int destlen = 0;
            try {
                int len1 = lenNode.execute(left);
                int len2 = lenNode.execute(right);
                // we eagerly generalize the store to avoid possible cascading generalizations
                destlen = PythonUtils.addExact(len1, len2);
                if (errorForOverflow == OverflowError && shouldOverflow.profile(destlen >= SysModuleBuiltins.MAXSIZE)) {
                    // cpython raises an overflow error when this happens
                    throw raiseNode.raise(OverflowError);
                }
                SequenceStorage generalized = generalizeStore(createEmpty(left, right, destlen), right);
                return doConcat(generalized, left, right);
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(errorForOverflow);
            }
        }

        private SequenceStorage createEmpty(SequenceStorage l, SequenceStorage r, int len) {
            if (l instanceof EmptySequenceStorage) {
                return createEmptyNode.execute(r, len, -1);
            }
            return createEmptyNode.execute(l, len, len);
        }

        private SequenceStorage doConcat(SequenceStorage dest, SequenceStorage leftProfiled, SequenceStorage rightProfiled) {
            try {
                return concatBaseNode.execute(dest, leftProfiled, rightProfiled);
            } catch (SequenceStoreException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("generalized sequence storage cannot take value: " + e.getIndicationValue());
            }
        }

        private SequenceStorage generalizeStore(SequenceStorage storage, Object value) {
            if (genNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                genNode = insert(genNodeProvider.get());
            }
            return genNode.execute(storage, value);
        }

        public static ConcatNode create() {
            return create(() -> NoGeneralizationCustomMessageNode.create(DEFAULT_ERROR_MSG), MemoryError);
        }

        public static ConcatNode createWithOverflowError() {
            return create(() -> NoGeneralizationCustomMessageNode.create(DEFAULT_ERROR_MSG), OverflowError);
        }

        public static ConcatNode create(String msg) {
            return create(() -> NoGeneralizationCustomMessageNode.create(msg), MemoryError);
        }

        public static ConcatNode create(Supplier<GeneralizationNode> genNodeProvider) {
            return create(genNodeProvider, MemoryError);
        }

        private static ConcatNode create(Supplier<GeneralizationNode> genNodeProvider, PythonBuiltinClassType errorForOverflow) {
            return ConcatNodeGen.create(genNodeProvider, errorForOverflow);
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class ExtendNode extends SequenceStorageBaseNode {
        @Child private GeneralizationNode genNode;

        private final GenNodeSupplier genNodeProvider;

        public ExtendNode(GenNodeSupplier genNodeProvider) {
            this.genNodeProvider = genNodeProvider;
        }

        public abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage s, Object iterable, int len);

        private static int lengthResult(int current, int ext) {
            try {
                return PythonUtils.addExact(current, ext);
            } catch (OverflowException e) {
                // (mq) There is no need to ensure capacity as we either
                // run out of memory or dealing with a fake length.
                return current;
            }
        }

        @Specialization(guards = {"hasStorage(seq)", "cannotBeOverridden(seq, getClassNode)"}, limit = "1")
        SequenceStorage doWithStorage(SequenceStorage left, PSequence seq, int len,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached GetSequenceStorageNode getStorageNode,
                        @Cached LenNode lenNode,
                        @Cached EnsureCapacityNode ensureCapacityNode,
                        @Cached ConcatBaseNode concatStoragesNode) {
            SequenceStorage right = getStorageNode.execute(seq);
            int lenLeft = lenNode.execute(left);
            int lenResult;
            if (len > 0) {
                lenResult = lengthResult(lenLeft, len);
            } else {
                lenResult = lengthResult(lenLeft, lenNode.execute(right));
            }
            SequenceStorage dest = null;
            try {
                // EnsureCapacityNode handles the overflow and raises an error
                dest = ensureCapacityNode.execute(left, lenResult);
                return concatStoragesNode.execute(dest, left, right);
            } catch (SequenceStoreException e) {
                dest = generalizeStore(dest, e.getIndicationValue());
                dest = ensureCapacityNode.execute(dest, lenResult);
                return concatStoragesNode.execute(dest, left, right);
            }
        }

        @Specialization(guards = "!hasStorage(iterable) || !cannotBeOverridden(iterable, getClassNode)", limit = "1")
        SequenceStorage doWithoutStorage(VirtualFrame frame, SequenceStorage left, Object iterable, int len,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached LenNode lenNode,
                        @Cached EnsureCapacityNode ensureCapacityNode,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached AppendNode appendNode) {
            SequenceStorage currentStore = left;
            int lenLeft = lenNode.execute(currentStore);
            Object it = getIter.execute(frame, iterable);
            if (len > 0) {
                ensureCapacityNode.execute(left, lengthResult(lenLeft, len));
            }
            while (true) {
                Object value;
                try {
                    value = getNextNode.execute(frame, it);
                    currentStore = appendNode.execute(currentStore, value, genNodeProvider);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return currentStore;
                }
            }
        }

        private SequenceStorage generalizeStore(SequenceStorage storage, Object value) {
            if (genNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                genNode = insert(genNodeProvider.create());
            }
            return genNode.execute(storage, value);
        }

        protected ExtendNode createRecursive() {
            return ExtendNodeGen.create(genNodeProvider);
        }

        public static ExtendNode create(GenNodeSupplier genNodeProvider) {
            return ExtendNodeGen.create(genNodeProvider);
        }
    }

    public abstract static class RepeatNode extends SequenceStorageBaseNode {
        private static final String ERROR_MSG = "can't multiply sequence by non-int of type '%p'";

        @Child private GetItemScalarNode getItemNode;
        @Child private RepeatNode recursive;

        /*
         * CPython is inconsistent when too repeats are done. Most types raise MemoryError, but e.g.
         * bytes raises OverflowError when the memory might be available but the size overflows
         * sys.maxint
         */
        private final PythonBuiltinClassType errorForOverflow;

        protected RepeatNode(PythonBuiltinClassType errorForOverflow) {
            this.errorForOverflow = errorForOverflow;
        }

        public abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage left, Object times);

        public abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage left, int times);

        @Specialization
        static SequenceStorage doEmpty(EmptySequenceStorage s, @SuppressWarnings("unused") int times) {
            return s;
        }

        @Specialization(guards = "times <= 0")
        static SequenceStorage doZeroRepeat(SequenceStorage s, @SuppressWarnings("unused") int times,
                        @Cached CreateEmptyNode createEmptyNode) {
            return createEmptyNode.execute(s, 0, -1);
        }

        /* special but common case: something like '[False] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        BoolSequenceStorage doBoolSingleElement(BoolSequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile outOfMemProfile) {
            try {
                boolean[] repeated = new boolean[PythonUtils.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getBoolItemNormalized(0));
                return new BoolSequenceStorage(repeated);
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(errorForOverflow);
            }
        }

        /* special but common case: something like '["\x00"] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        ByteSequenceStorage doByteSingleElement(ByteSequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile outOfMemProfile) {
            try {
                byte[] repeated = new byte[PythonUtils.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getByteItemNormalized(0));
                return new ByteSequenceStorage(repeated);
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(errorForOverflow);
            }
        }

        /* special but common case: something like '[0] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        IntSequenceStorage doIntSingleElement(IntSequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile outOfMemProfile) {
            try {
                int[] repeated = new int[PythonUtils.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getIntItemNormalized(0));
                return new IntSequenceStorage(repeated);
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(errorForOverflow);
            }
        }

        /* special but common case: something like '[0L] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        LongSequenceStorage doLongSingleElement(LongSequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile outOfMemProfile) {
            try {
                long[] repeated = new long[PythonUtils.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getLongItemNormalized(0));
                return new LongSequenceStorage(repeated);
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(errorForOverflow);
            }
        }

        /* special but common case: something like '[0.0] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        DoubleSequenceStorage doDoubleSingleElement(DoubleSequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile outOfMemProfile) {
            try {
                double[] repeated = new double[PythonUtils.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getDoubleItemNormalized(0));
                return new DoubleSequenceStorage(repeated);
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(errorForOverflow);
            }
        }

        /* special but common case: something like '[None] * n' */
        @Specialization(guards = {"s.length() == 1", "times > 0"})
        ObjectSequenceStorage doObjectSingleElement(ObjectSequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile outOfMemProfile) {
            try {
                Object[] repeated = new Object[PythonUtils.multiplyExact(s.length(), times)];
                Arrays.fill(repeated, s.getItemNormalized(0));
                return new ObjectSequenceStorage(repeated);
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(errorForOverflow);
            }
        }

        @Specialization(limit = "MAX_ARRAY_STORAGES", guards = {"times > 0", "!isNative(s)", "s.getClass() == cachedClass"})
        SequenceStorage doManaged(BasicSequenceStorage s, int times,
                        @Exclusive @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile outOfMemProfile,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            try {
                SequenceStorage profiled = cachedClass.cast(s);
                Object arr1 = profiled.getInternalArrayObject();
                int len = profiled.length();
                int newLength = PythonUtils.multiplyExact(len, times);
                SequenceStorage repeated = profiled.createEmpty(newLength);
                Object destArr = repeated.getInternalArrayObject();
                repeat(destArr, arr1, len, times);
                repeated.setNewLength(newLength);
                return repeated;
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(errorForOverflow);
            }
        }

        @Specialization(replaces = "doManaged", guards = "times > 0")
        SequenceStorage doGeneric(SequenceStorage s, int times,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Cached CreateEmptyNode createEmptyNode,
                        @Cached BranchProfile outOfMemProfile,
                        @Cached SetItemScalarNode setItemNode,
                        @Cached GetItemScalarNode getDestItemNode,
                        @Cached LenNode lenNode) {
            try {
                int len = lenNode.execute(s);
                int newLen = PythonUtils.multiplyExact(len, times);
                SequenceStorage repeated = createEmptyNode.execute(s, newLen, -1);

                for (int i = 0; i < len; i++) {
                    setItemNode.execute(repeated, i, getGetItemNode().execute(s, i));
                }

                // read from destination since that is potentially faster
                for (int j = 1; j < times; j++) {
                    for (int i = 0; i < len; i++) {
                        setItemNode.execute(repeated, j * len + i, getDestItemNode.execute(repeated, i));
                    }
                }

                repeated.setNewLength(newLen);
                return repeated;
            } catch (OutOfMemoryError e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(MemoryError);
            } catch (OverflowException e) {
                outOfMemProfile.enter();
                throw raiseNode.raise(errorForOverflow);
            }
        }

        @Specialization(guards = "!isInt(times)")
        SequenceStorage doNonInt(VirtualFrame frame, SequenceStorage s, Object times,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (!indexCheckNode.execute(times)) {
                throw raiseNode.raise(TypeError, ERROR_MSG, times);
            }
            int i = asSizeNode.executeExact(frame, times);
            if (recursive == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursive = insert(RepeatNodeGen.create(errorForOverflow));
            }
            return recursive.execute(frame, s, i);
        }

        private GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        private static void repeat(Object dest, Object src, int len, int times) {
            for (int i = 0; i < times; i++) {
                PythonUtils.arraycopy(src, 0, dest, i * len, len);
            }
        }

        protected static boolean isInt(Object times) {
            return times instanceof Integer;
        }

        public static RepeatNode create() {
            return RepeatNodeGen.create(MemoryError);
        }

        public static RepeatNode createWithOverflowError() {
            return RepeatNodeGen.create(OverflowError);
        }
    }

    public abstract static class ContainsNode extends SequenceStorageBaseNode {
        public abstract boolean execute(VirtualFrame frame, SequenceStorage left, Object item);

        @Specialization
        public static boolean doIndexOf(VirtualFrame frame, SequenceStorage left, Object item,
                        @Cached IndexOfNode indexOfNode) {
            return indexOfNode.execute(frame, left, item) != -1;
        }

    }

    public abstract static class IndexOfNode extends SequenceStorageBaseNode {
        public abstract int execute(VirtualFrame frame, SequenceStorage left, Object item);

        @Specialization(guards = "lenNode.execute(left) == 0")
        @SuppressWarnings("unused")
        static int doEmpty(SequenceStorage left, Object item,
                        @Cached LenNode lenNode) {
            return -1;
        }

        @Specialization
        public static int doByteStorage(ByteSequenceStorage s, int item) {
            return s.indexOfInt(item);
        }

        @Specialization
        public static int doByteStorage(ByteSequenceStorage s, byte item) {
            return s.indexOfByte(item);
        }

        @Specialization
        public static int doIntStorage(IntSequenceStorage s, int item) {
            return s.indexOfInt(item);
        }

        @Specialization
        public static int doLongStorage(LongSequenceStorage s, long item) {
            return s.indexOfLong(item);
        }

        @Specialization
        public static int doDoubleStorage(DoubleSequenceStorage s, double item) {
            return s.indexOfDouble(item);
        }

        @Specialization
        static int doGeneric(VirtualFrame frame, SequenceStorage left, Object item,
                        @Cached LenNode lenNode,
                        @Cached GetItemScalarNode getItemNode,
                        @Cached PyObjectRichCompareBool.EqNode eqNode) {
            for (int i = 0; i < lenNode.execute(left); i++) {
                Object leftItem = getItemNode.execute(left, i);
                if (eqNode.execute(frame, item, leftItem)) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * Generalization node must convert given storage to a storage that is able to be written any
     * number of any valid elements. I.e., there must be a specialization handling that storage type
     * in {@link SetItemScalarNode}. Note: it is possible that the RHS of the write may be invalid
     * element, e.g., large integer when the storage is bytes array storage, but in such case the
     * {@link SetItemScalarNode} will correctly raise Python level {@code ValueError}.
     */
    public abstract static class GeneralizationNode extends Node {
        public abstract SequenceStorage execute(SequenceStorage toGeneralize, Object indicationValue);

    }

    /**
     * Does not allow any generalization but compatible types.
     */
    @GenerateUncached
    public abstract static class NoGeneralizationNode extends GeneralizationNode {

        public static final GenNodeSupplier DEFAULT = new GenNodeSupplier() {

            @Override
            public GeneralizationNode getUncached() {
                return NoGeneralizationNodeGen.getUncached();
            }

            @Override
            public GeneralizationNode create() {
                return NoGeneralizationNodeGen.create();
            }
        };

        @Specialization
        protected SequenceStorage doGeneric(SequenceStorage s, Object indicationVal,
                        @Cached IsAssignCompatibleNode isAssignCompatibleNode,
                        @Cached GetElementType getElementType,
                        @Cached("createClassProfile()") ValueProfile valTypeProfile,
                        @Cached PRaiseNode raiseNode) {

            Object val = valTypeProfile.profile(indicationVal);
            if (val instanceof SequenceStorage && isAssignCompatibleNode.execute(s, (SequenceStorage) val)) {
                return s;
            }

            ListStorageType et = getElementType.execute(s);
            if (val instanceof Byte && SequenceStorageBaseNode.isByteLike(et) ||
                            val instanceof Integer && (SequenceStorageBaseNode.isInt(et) || SequenceStorageBaseNode.isLong(et)) ||
                            val instanceof Long && SequenceStorageBaseNode.isLong(et) || SequenceStorageBaseNode.isObject(et)) {
                return s;
            }

            throw raiseNode.raise(TypeError, getErrorMessage());
        }

        protected String getErrorMessage() {
            return "";
        }
    }

    public abstract static class NoGeneralizationCustomMessageNode extends NoGeneralizationNode {

        private final String errorMessage;

        public NoGeneralizationCustomMessageNode(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Override
        protected final String getErrorMessage() {
            return errorMessage;
        }

        public static NoGeneralizationCustomMessageNode create(String msg) {
            return NoGeneralizationCustomMessageNodeGen.create(msg);
        }
    }

    /**
     * Byte array is specific that it supports being written using slice from other iterables as
     * long as the individual elements written can be converted to bytes. Arrays from the array
     * module do not support this, they can be written only individual elements or slice from other
     * array of the same type.
     *
     * This node works with the assumption that all storages of byte arrays support writing of any
     * number of bytes. There is no actual generalization of the storage, but instead we tell the
     * caller that it should try again with the same storage (in the second try, it should try to
     * write whatever is in RHS no matter of the type of the storage of RHS).
     *
     * This is only limitation of this node. Shall we ever want to support byte arrays that can be
     * backed by different types of storage, we'd only need to change this node to accommodate for
     * that and return the most generic storage of those.
     */
    public static class ByteArrayGeneralizationNode extends GeneralizationNode {
        public static ByteArrayGeneralizationNode UNCACHED = new ByteArrayGeneralizationNode();

        public static final GenNodeSupplier SUPPLIER = new GenNodeSupplier() {
            @Override
            public GeneralizationNode getUncached() {
                return UNCACHED;
            }

            @Override
            public GeneralizationNode create() {
                return new ByteArrayGeneralizationNode();
            }
        };

        public static final Supplier<GeneralizationNode> CACHED_SUPPLIER = () -> new ByteArrayGeneralizationNode();

        @Override
        public SequenceStorage execute(SequenceStorage toGeneralize, @SuppressWarnings("unused") Object indicationValue) {
            return toGeneralize;
        }
    }

    /**
     * Implements list generalization rules; previously in 'SequenceStroage.generalizeFor'.
     */
    @GenerateUncached
    public abstract static class ListGeneralizationNode extends GeneralizationNode {

        public static final GenNodeSupplier SUPPLIER = new GenNodeSupplier() {

            @Override
            public GeneralizationNode getUncached() {
                return ListGeneralizationNodeGen.getUncached();
            }

            @Override
            public GeneralizationNode create() {
                return ListGeneralizationNodeGen.create();
            }
        };

        private static final int DEFAULT_CAPACITY = 8;

        @Specialization
        static ObjectSequenceStorage doObject(@SuppressWarnings("unused") ObjectSequenceStorage s, @SuppressWarnings("unused") Object indicationValue) {
            return s;
        }

        @Specialization
        static SequenceStorage doEmptyStorage(@SuppressWarnings("unused") EmptySequenceStorage s, SequenceStorage other,
                        @Cached("createClassProfile()") ValueProfile otherProfile) {
            return otherProfile.profile(other).createEmpty(DEFAULT_CAPACITY);
        }

        @Specialization
        static ByteSequenceStorage doEmptyByte(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") byte val) {
            return new ByteSequenceStorage(DEFAULT_CAPACITY);
        }

        @Specialization
        static IntSequenceStorage doEmptyInteger(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") int val) {
            return new IntSequenceStorage();
        }

        @Specialization
        static LongSequenceStorage doEmptyLong(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") long val) {
            return new LongSequenceStorage();
        }

        @Specialization
        static DoubleSequenceStorage doEmptyDouble(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") double val) {
            return new DoubleSequenceStorage();
        }

        protected static boolean isKnownType(Object val) {
            return val instanceof Byte || val instanceof Integer || val instanceof Long || val instanceof Double;
        }

        @Specialization(guards = "!isKnownType(val)")
        static ObjectSequenceStorage doEmptyObject(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") Object val) {
            return new ObjectSequenceStorage(DEFAULT_CAPACITY);
        }

        @Specialization
        static ByteSequenceStorage doByteByte(ByteSequenceStorage s, @SuppressWarnings("unused") byte val) {
            return s;
        }

        @Specialization
        static IntSequenceStorage doByteInteger(@SuppressWarnings("unused") ByteSequenceStorage s, @SuppressWarnings("unused") int val) {
            int[] copied = new int[s.length()];
            for (int i = 0; i < copied.length; i++) {
                copied[i] = s.getIntItemNormalized(i);
            }
            return new IntSequenceStorage(copied);
        }

        @Specialization
        static LongSequenceStorage doByteLong(@SuppressWarnings("unused") ByteSequenceStorage s, @SuppressWarnings("unused") long val) {
            long[] copied = new long[s.length()];
            for (int i = 0; i < copied.length; i++) {
                copied[i] = s.getIntItemNormalized(i);
            }
            return new LongSequenceStorage(copied);
        }

        @Specialization
        static SequenceStorage doIntegerInteger(IntSequenceStorage s, @SuppressWarnings("unused") int val) {
            return s;
        }

        @Specialization
        static SequenceStorage doIntegerLong(@SuppressWarnings("unused") IntSequenceStorage s, @SuppressWarnings("unused") long val) {
            long[] copied = new long[s.length()];
            for (int i = 0; i < copied.length; i++) {
                copied[i] = s.getIntItemNormalized(i);
            }
            return new LongSequenceStorage(copied);
        }

        @Specialization
        static LongSequenceStorage doLongByte(LongSequenceStorage s, @SuppressWarnings("unused") byte val) {
            return s;
        }

        @Specialization
        static LongSequenceStorage doLongInteger(LongSequenceStorage s, @SuppressWarnings("unused") int val) {
            return s;
        }

        @Specialization
        static LongSequenceStorage doLongLong(LongSequenceStorage s, @SuppressWarnings("unused") long val) {
            return s;
        }

        // TODO native sequence storage

        @Specialization(guards = "isAssignCompatibleNode.execute(s, indicationStorage)", limit = "1")
        static TypedSequenceStorage doTyped(TypedSequenceStorage s, @SuppressWarnings("unused") SequenceStorage indicationStorage,
                        @Shared("isAssignCompatibleNode") @Cached @SuppressWarnings("unused") IsAssignCompatibleNode isAssignCompatibleNode) {
            return s;
        }

        @Specialization(guards = "isFallbackCase(s, value, isAssignCompatibleNode)", limit = "1")
        static ObjectSequenceStorage doTyped(SequenceStorage s, @SuppressWarnings("unused") Object value,
                        @Cached("createClassProfile()") ValueProfile selfProfile,
                        @Shared("isAssignCompatibleNode") @Cached @SuppressWarnings("unused") IsAssignCompatibleNode isAssignCompatibleNode) {
            SequenceStorage profiled = selfProfile.profile(s);
            if (profiled instanceof BasicSequenceStorage) {
                return new ObjectSequenceStorage(profiled.getInternalArray());
            }
            // TODO copy all values
            return new ObjectSequenceStorage(DEFAULT_CAPACITY);
        }

        protected static boolean isFallbackCase(SequenceStorage s, Object value, IsAssignCompatibleNode isAssignCompatibleNode) {
            // there are explicit specializations for all cases with EmptySequenceStorage
            if (s instanceof EmptySequenceStorage || s instanceof ObjectSequenceStorage) {
                return false;
            }
            if ((s instanceof ByteSequenceStorage || s instanceof IntSequenceStorage || s instanceof LongSequenceStorage) &&
                            (value instanceof Byte || value instanceof Integer || value instanceof Long)) {
                return false;
            }
            return !(value instanceof SequenceStorage) || !isAssignCompatibleNode.execute(s, (SequenceStorage) value);
        }

        public static ListGeneralizationNode create() {
            return ListGeneralizationNodeGen.create();
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class AppendNode extends Node {

        public abstract SequenceStorage execute(SequenceStorage s, Object val, GenNodeSupplier genNodeSupplier);

        @Specialization
        static SequenceStorage doEmpty(EmptySequenceStorage s, Object val, GenNodeSupplier genNodeSupplier,
                        @Cached AppendNode recursive,
                        @Shared("genNode") @Cached DoGeneralizationNode doGenNode) {
            SequenceStorage newStorage = doGenNode.execute(genNodeSupplier, s, val);
            return recursive.execute(newStorage, val, genNodeSupplier);
        }

        @Specialization
        static SequenceStorage doManaged(BasicSequenceStorage s, Object val, GenNodeSupplier genNodeSupplier,
                        @Cached BranchProfile increaseCapacity,
                        @Cached EnsureCapacityNode ensureCapacity,
                        @Cached SetLenNode setLenNode,
                        @Cached LenNode lenNode,
                        @Cached SetItemScalarNode setItemNode,
                        @Shared("genNode") @Cached DoGeneralizationNode doGenNode) {
            int len = lenNode.execute(s);
            int newLen = len + 1;
            int capacity = s.getCapacity();
            if (newLen > capacity) {
                increaseCapacity.enter();
                ensureCapacity.execute(s, len + 1);
            }
            try {
                setItemNode.execute(s, len, val);
                setLenNode.execute(s, len + 1);
                return s;
            } catch (SequenceStoreException e) {
                SequenceStorage generalized = doGenNode.execute(genNodeSupplier, s, e.getIndicationValue());
                ensureCapacity.execute(generalized, len + 1);
                try {
                    setItemNode.execute(generalized, len, val);
                    setLenNode.execute(generalized, len + 1);
                    return generalized;
                } catch (SequenceStoreException e1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
            }
        }

        // TODO native sequence storage

        public static AppendNode create() {
            return AppendNodeGen.create();
        }

        public static AppendNode getUncached() {
            return AppendNodeGen.getUncached();
        }
    }

    public abstract static class CreateEmptyNode extends SequenceStorageBaseNode {

        @Child private GetElementType getElementType;

        public abstract SequenceStorage execute(SequenceStorage s, int cap, int len);

        private ListStorageType getElementType(SequenceStorage s) {
            if (getElementType == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getElementType = insert(GetElementType.create());
            }
            return getElementType.execute(s);
        }

        protected boolean isBoolean(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Boolean;
        }

        protected boolean isInt(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Int;
        }

        protected boolean isLong(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Long;
        }

        protected boolean isByte(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Byte;
        }

        protected boolean isByteLike(SequenceStorage s) {
            return isByte(s) || isInt(s) || isLong(s);
        }

        protected boolean isDouble(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Double;
        }

        protected boolean isObject(SequenceStorage s) {
            return getElementType(s) == ListStorageType.Generic;
        }

        @Specialization(guards = "isBoolean(s)")
        static BoolSequenceStorage doBoolean(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            BoolSequenceStorage ss = new BoolSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        @Specialization(guards = "isByte(s)")
        static ByteSequenceStorage doByte(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            ByteSequenceStorage ss = new ByteSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        @Specialization(guards = "isInt(s)")
        static IntSequenceStorage doInt(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            IntSequenceStorage ss = new IntSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        @Specialization(guards = "isLong(s)")
        static LongSequenceStorage doLong(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            LongSequenceStorage ss = new LongSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        @Specialization(guards = "isDouble(s)")
        static DoubleSequenceStorage doDouble(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            DoubleSequenceStorage ss = new DoubleSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        @Fallback
        static ObjectSequenceStorage doObject(@SuppressWarnings("unused") SequenceStorage s, int cap, int len) {
            ObjectSequenceStorage ss = new ObjectSequenceStorage(cap);
            if (len != -1) {
                ss.ensureCapacity(len);
                ss.setNewLength(len);
            }
            return ss;
        }

        public static CreateEmptyNode create() {
            return CreateEmptyNodeGen.create();
        }
    }

    @GenerateUncached
    @ImportStatic(ListStorageType.class)
    public abstract static class EnsureCapacityNode extends SequenceStorageBaseNode {

        public abstract SequenceStorage execute(SequenceStorage s, int cap);

        @Specialization
        static EmptySequenceStorage doEmpty(EmptySequenceStorage s, @SuppressWarnings("unused") int cap) {
            return s;
        }

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        static BasicSequenceStorage doManaged(BasicSequenceStorage s, int cap,
                        @Cached PRaiseNode raiseNode,
                        @Cached BranchProfile overflowErrorProfile,
                        @Cached("s.getClass()") Class<? extends BasicSequenceStorage> cachedClass) {
            try {
                BasicSequenceStorage profiled = cachedClass.cast(s);
                profiled.ensureCapacity(cap);
                return profiled;
            } catch (OutOfMemoryError | ArithmeticException e) {
                overflowErrorProfile.enter();
                throw raiseNode.raise(MemoryError);
            }
        }

        @Specialization(guards = "s.getElementType() == Byte")
        static NativeSequenceStorage doNativeByte(NativeSequenceStorage s, @SuppressWarnings("unused") int cap,
                        @Shared("i") @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Exclusive @Cached PCallCapiFunction callCapiFunction,
                        @Shared("r") @Cached PRaiseNode raiseNode) {
            return reallocNativeSequenceStorage(s, cap, lib, callCapiFunction, raiseNode, FUN_PY_TRUFFLE_BYTE_ARRAY_REALLOC);
        }

        @Specialization(guards = "s.getElementType() == Int")
        static NativeSequenceStorage doNativeInt(NativeSequenceStorage s, @SuppressWarnings("unused") int cap,
                        @Shared("i") @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Exclusive @Cached PCallCapiFunction callCapiFunction,
                        @Shared("r") @Cached PRaiseNode raiseNode) {
            return reallocNativeSequenceStorage(s, cap, lib, callCapiFunction, raiseNode, FUN_PY_TRUFFLE_INT_ARRAY_REALLOC);
        }

        @Specialization(guards = "s.getElementType() == Long")
        static NativeSequenceStorage doNativeLong(NativeSequenceStorage s, @SuppressWarnings("unused") int cap,
                        @Shared("i") @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Exclusive @Cached PCallCapiFunction callCapiFunction,
                        @Shared("r") @Cached PRaiseNode raiseNode) {
            return reallocNativeSequenceStorage(s, cap, lib, callCapiFunction, raiseNode, FUN_PY_TRUFFLE_LONG_ARRAY_REALLOC);
        }

        @Specialization(guards = "s.getElementType() == Double")
        static NativeSequenceStorage doNativeDouble(NativeSequenceStorage s, @SuppressWarnings("unused") int cap,
                        @Shared("i") @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Exclusive @Cached PCallCapiFunction callCapiFunction,
                        @Shared("r") @Cached PRaiseNode raiseNode) {
            return reallocNativeSequenceStorage(s, cap, lib, callCapiFunction, raiseNode, FUN_PY_TRUFFLE_DOUBLE_ARRAY_REALLOC);
        }

        @Specialization(guards = "s.getElementType() == Generic")
        static NativeSequenceStorage doNativeObject(NativeSequenceStorage s, @SuppressWarnings("unused") int cap,
                        @Shared("i") @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Exclusive @Cached PCallCapiFunction callCapiFunction,
                        @Shared("r") @Cached PRaiseNode raiseNode) {
            return reallocNativeSequenceStorage(s, cap, lib, callCapiFunction, raiseNode, FUN_PY_TRUFFLE_OBJECT_ARRAY_REALLOC);
        }

        private static NativeSequenceStorage reallocNativeSequenceStorage(NativeSequenceStorage s, int requestedCapacity, InteropLibrary lib, PCallCapiFunction callCapiFunction, PRaiseNode raiseNode,
                        NativeCAPISymbol function) {
            if (requestedCapacity > s.getCapacity()) {
                int newCapacity;
                try {
                    newCapacity = Math.max(16, PythonUtils.multiplyExact(requestedCapacity, 2));
                } catch (OverflowException e) {
                    newCapacity = requestedCapacity;
                }
                Object ptr = callCapiFunction.call(function, s.getPtr(), newCapacity);
                if (lib.isNull(ptr)) {
                    throw raiseNode.raise(MemoryError);
                }
                s.setPtr(ptr);
                s.setCapacity(newCapacity);
            }
            return s;
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class GetInternalArrayNode extends Node {

        public abstract Object execute(SequenceStorage s);

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        static Object doSpecial(SequenceStorage s,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            return cachedClass.cast(s).getInternalArrayObject();
        }

        @Specialization(replaces = "doSpecial")
        @TruffleBoundary
        static Object doGeneric(SequenceStorage s) {
            return s.getInternalArrayObject();
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class CopyNode extends Node {

        public abstract SequenceStorage execute(SequenceStorage s);

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        static SequenceStorage doSpecial(SequenceStorage s,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            return CompilerDirectives.castExact(CompilerDirectives.castExact(s, cachedClass).copy(), cachedClass);
        }

        @Specialization(replaces = "doSpecial")
        @TruffleBoundary
        static SequenceStorage doGeneric(SequenceStorage s) {
            return s.copy();
        }

        public static CopyNode create() {
            return CopyNodeGen.create();
        }

        public static CopyNode getUncached() {
            return CopyNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class CopyInternalArrayNode extends Node {

        public abstract Object[] execute(SequenceStorage s);

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        static Object[] doTyped(TypedSequenceStorage s,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            return cachedClass.cast(s).getInternalArray();
        }

        @Specialization(replaces = "doTyped")
        @TruffleBoundary
        static Object[] doTypedUncached(TypedSequenceStorage s) {
            return s.getInternalArray();
        }

        @Specialization
        static Object[] doEmpty(EmptySequenceStorage s) {
            return s.getCopyOfInternalArray();
        }

        @Specialization
        static Object[] doNative(NativeSequenceStorage s) {
            return s.getCopyOfInternalArray();
        }

        @Specialization
        static Object[] doGeneric(ObjectSequenceStorage s) {
            return s.getCopyOfInternalArray();
        }

        public static CopyInternalArrayNode getUncached() {
            return SequenceStorageNodesFactory.CopyInternalArrayNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class CopyItemNode extends Node {

        public abstract void execute(SequenceStorage s, int to, int from);

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        static void doSpecial(SequenceStorage s, int to, int from,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            cachedClass.cast(s).copyItem(to, from);
        }

        @Specialization(replaces = "doSpecial")
        @TruffleBoundary
        static void doGeneric(SequenceStorage s, int to, int from) {
            s.copyItem(to, from);
        }

        public static CopyItemNode create() {
            return CopyItemNodeGen.create();
        }

        public static CopyItemNode getUncached() {
            return CopyItemNodeGen.getUncached();
        }
    }

    @ImportStatic(SequenceStorageBaseNode.class)
    public static final class LenNode extends Node {
        private static final LenNode UNCACHED = new LenNode();

        public int execute(SequenceStorage s) {
            return s.length();
        }

        public static LenNode create() {
            return new LenNode();
        }

        public static LenNode getUncached() {
            return UNCACHED;
        }
    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class SetLenNode extends Node {

        public abstract void execute(SequenceStorage s, int len);

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        static void doSpecial(SequenceStorage s, int len,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            cachedClass.cast(s).setNewLength(len);
        }

        @Specialization(replaces = "doSpecial")
        static void doGeneric(SequenceStorage s, int len) {
            s.setNewLength(len);
        }

        public static SetLenNode create() {
            return SetLenNodeGen.create();
        }

        public static SetLenNode getUncached() {
            return SetLenNodeGen.getUncached();
        }
    }

    public abstract static class DeleteNode extends NormalizingNode {
        @Child private DeleteItemNode deleteItemNode;
        @Child private DeleteSliceNode deleteSliceNode;

        public DeleteNode(NormalizeIndexNode normalizeIndexNode) {
            super(normalizeIndexNode);
        }

        public abstract void execute(VirtualFrame frame, SequenceStorage s, Object indexOrSlice);

        public abstract void execute(VirtualFrame frame, SequenceStorage s, int index);

        public abstract void execute(VirtualFrame frame, SequenceStorage s, long index);

        @Specialization
        protected void doScalarInt(VirtualFrame frame, SequenceStorage storage, int idx) {
            getDeleteItemNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization
        protected void doScalarLong(VirtualFrame frame, SequenceStorage storage, long idx) {
            getDeleteItemNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization
        protected void doScalarPInt(VirtualFrame frame, SequenceStorage storage, PInt idx) {
            getDeleteItemNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization(guards = "!isPSlice(idx)")
        protected void doScalarGeneric(VirtualFrame frame, SequenceStorage storage, Object idx) {
            getDeleteItemNode().execute(storage, normalizeIndex(frame, idx, storage));
        }

        @Specialization
        protected void doSlice(SequenceStorage storage, PSlice slice,
                        @Cached LenNode lenNode,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached SliceLiteralNode.SliceUnpack unpack,
                        @Cached SliceLiteralNode.AdjustIndices adjustIndices) {
            int len = lenNode.execute(storage);
            SliceInfo unadjusted = unpack.execute(sliceCast.execute(slice));
            SliceInfo info = adjustIndices.execute(len, unadjusted);
            try {
                getDeleteSliceNode().execute(storage, info);
            } catch (SequenceStoreException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        private DeleteItemNode getDeleteItemNode() {
            if (deleteItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                deleteItemNode = insert(DeleteItemNode.create());
            }
            return deleteItemNode;
        }

        private DeleteSliceNode getDeleteSliceNode() {
            if (deleteSliceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                deleteSliceNode = insert(DeleteSliceNode.create());
            }
            return deleteSliceNode;
        }

        public static DeleteNode create(NormalizeIndexNode normalizeIndexNode) {
            return DeleteNodeGen.create(normalizeIndexNode);
        }

        public static DeleteNode create() {
            return DeleteNodeGen.create(NormalizeIndexNode.create());
        }
    }

    @GenerateUncached
    public abstract static class DeleteItemNode extends SequenceStorageBaseNode {

        public abstract void execute(SequenceStorage s, int idx);

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = {"s.getClass() == cachedClass", "isLastItem(s, cachedClass, idx)"})
        static void doLastItem(SequenceStorage s, @SuppressWarnings("unused") int idx,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage profiled = cachedClass.cast(s);
            profiled.setNewLength(profiled.length() - 1);
        }

        @Specialization(limit = "MAX_SEQUENCE_STORAGES", guards = "s.getClass() == cachedClass")
        static void doGeneric(SequenceStorage s, @SuppressWarnings("unused") int idx,
                        @Cached GetItemScalarNode getItemNode,
                        @Cached SetItemScalarNode setItemNode,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage profiled = cachedClass.cast(s);
            int len = profiled.length();

            for (int i = idx; i < len - 1; i++) {
                setItemNode.execute(profiled, i, getItemNode.execute(profiled, i + 1));
            }
            profiled.setNewLength(len - 1);
        }

        protected static boolean isLastItem(SequenceStorage s, Class<? extends SequenceStorage> cachedClass, int idx) {
            return idx == cachedClass.cast(s).length() - 1;
        }

        protected static DeleteItemNode create() {
            return DeleteItemNodeGen.create();
        }
    }

    abstract static class DeleteSliceNode extends SequenceStorageBaseNode {

        public abstract void execute(SequenceStorage s, SliceInfo info);

        @Specialization(guards = "sinfo.step == 1")
        static void singleStep(SequenceStorage store, SliceInfo sinfo,
                        @Cached ConditionProfile shortCircuitProfile,
                        @Cached LenNode selfLenNode,
                        @Cached SetLenNode setLenNode,
                        @Cached MemMoveNode memove) {
            int length = selfLenNode.execute(store);
            int sliceLength = sinfo.sliceLength;

            if (shortCircuitProfile.profile(sliceLength == 0)) {
                return;
            }
            int ilow = sinfo.start;
            int ihigh = sinfo.stop;
            int n = 0; /* # of elements in replacement list */

            ilow = (ilow < 0) ? 0 : (ilow > length) ? length : ilow;
            ihigh = (ihigh < ilow) ? ilow : (ihigh > length) ? length : ihigh;

            int norig = ihigh - ilow; /* # of elements in list getting replaced */
            assert norig >= 0 : "Something wrong with slice info";
            int d = n - norig; /* Change in size */
            if (length + d == 0) {
                setLenNode.execute(store, 0);
                return;
            }

            if (d == 0) {
                return;
            }

            int tail = length - ihigh;
            memove.execute(store, ihigh + d, ihigh, tail);

            // change the result length
            // TODO reallocate array if the change is big?
            // Then unnecessary big array is kept in the memory.
            setLenNode.execute(store, length + d);
        }

        @Specialization(guards = "sinfo.step != 1")
        static void multipleSteps(SequenceStorage store, SliceInfo sinfo,
                        @Cached EnsureCapacityNode ensureCapacityNode,
                        @Cached LenNode selfLenNode,
                        @Cached SetLenNode setLenNode,
                        @Cached MemMoveNode memove) {
            multipleSteps(store, sinfo, selfLenNode, setLenNode, ensureCapacityNode, memove);
        }

        static void multipleSteps(SequenceStorage self, PSlice.SliceInfo sinfo,
                        LenNode selfLenNode,
                        SetLenNode setLenNode,
                        EnsureCapacityNode ensureCapacityNode,
                        MemMoveNode memove) {
            int start, stop, step, slicelen;
            start = sinfo.start;
            step = sinfo.step;
            slicelen = sinfo.sliceLength;
            int len = selfLenNode.execute(self);
            step = step > (len + 1) ? len : step;
            /*- Delete slice */
            int cur;
            int i;

            // ensure capacity will check if the storage can be resized.
            ensureCapacityNode.execute(self, len - slicelen);

            if (slicelen == 0) {
                /*- Nothing to do here. */
                return;
            }

            if (step < 0) {
                stop = start + 1;
                start = stop + step * (slicelen - 1) - 1;
                step = -step;
            }
            for (cur = start, i = 0; i < slicelen; cur += step, i++) {
                int lim = step - 1;

                if (cur + step >= len) {
                    lim = len - cur - 1;
                }

                memove.execute(self, cur - i, cur + 1, lim);
            }
            /*- Move the tail of the bytes, in one chunk */
            cur = start + slicelen * step;
            if (cur < len) {
                memove.execute(self, cur - slicelen, cur, len - cur);
            }

            // change the result length
            // TODO reallocate array if the change is big?
            // Then unnecessary big array is kept in the memory.
            setLenNode.execute(self, len - slicelen);
        }

        protected static DeleteSliceNode create() {
            return DeleteSliceNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class GetElementType extends Node {

        public abstract ListStorageType execute(SequenceStorage s);

        @Specialization(limit = "cacheLimit()", guards = {"s.getClass() == cachedClass"})
        static ListStorageType doCached(SequenceStorage s,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            return cachedClass.cast(s).getElementType();
        }

        @Specialization(replaces = "doCached")
        static ListStorageType doUncached(SequenceStorage s) {
            return s.getElementType();
        }

        protected static int cacheLimit() {
            return SequenceStorageBaseNode.MAX_SEQUENCE_STORAGES;
        }

        public static GetElementType create() {
            return GetElementTypeNodeGen.create();
        }

        public static GetElementType getUncached() {
            return GetElementTypeNodeGen.getUncached();
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    public abstract static class ItemIndexNode extends SequenceStorageBaseNode {

        @Child private GetItemScalarNode getItemNode;
        @Child private LenNode lenNode;

        public abstract int execute(VirtualFrame frame, SequenceStorage s, Object item, int start, int end);

        public abstract int execute(VirtualFrame frame, SequenceStorage s, boolean item, int start, int end);

        public abstract int execute(VirtualFrame frame, SequenceStorage s, char item, int start, int end);

        public abstract int execute(VirtualFrame frame, SequenceStorage s, int item, int start, int end);

        public abstract int execute(VirtualFrame frame, SequenceStorage s, long item, int start, int end);

        public abstract int execute(VirtualFrame frame, SequenceStorage s, double item, int start, int end);

        @Specialization(guards = "isBoolean(getElementType, s)")
        int doBoolean(SequenceStorage s, boolean item, int start, int end,
                        @Cached @SuppressWarnings("unused") GetElementType getElementType) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemScalarNode().executeBoolean(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization(guards = "isByte(getElementType, s)")
        int doByte(SequenceStorage s, byte item, int start, int end,
                        @Cached @SuppressWarnings("unused") GetElementType getElementType) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemScalarNode().executeByte(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization(guards = "isInt(getElementType, s)")
        int doInt(SequenceStorage s, int item, int start, int end,
                        @Cached @SuppressWarnings("unused") GetElementType getElementType) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemScalarNode().executeInt(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization(guards = "isLong(getElementType, s)")
        int doLong(SequenceStorage s, long item, int start, int end,
                        @Cached @SuppressWarnings("unused") GetElementType getElementType) {
            for (int i = start; i < getLength(s, end); i++) {
                if (getItemScalarNode().executeLong(s, i) == item) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization(guards = "isDouble(getElementType, s)")
        int doDouble(SequenceStorage s, double item, int start, int end,
                        @Cached @SuppressWarnings("unused") GetElementType getElementType) {
            for (int i = start; i < getLength(s, end); i++) {
                if (java.lang.Double.compare(getItemScalarNode().executeDouble(s, i), item) == 0) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization
        int doGeneric(VirtualFrame frame, SequenceStorage s, Object item, int start, int end,
                        @Cached PyObjectRichCompareBool.EqNode eqNode) {
            for (int i = start; i < getLength(s, end); i++) {
                Object object = getItemScalarNode().execute(s, i);
                if (eqNode.execute(frame, object, item)) {
                    return i;
                }
            }
            return -1;
        }

        private GetItemScalarNode getItemScalarNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        private int getLength(SequenceStorage s, int end) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(LenNode.create());
            }
            return Math.min(lenNode.execute(s), end);
        }

        public static ItemIndexNode create() {
            return ItemIndexNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class GetInternalObjectArrayNode extends Node {

        public abstract Object[] execute(SequenceStorage s);

        @Specialization
        static Object[] doObjectSequenceStorage(ObjectSequenceStorage s) {
            return s.getInternalArray();
        }

        @Specialization
        static Object[] doTypedSequenceStorage(TypedSequenceStorage s,
                        @Cached CopyInternalArrayNode copy) {
            Object[] internalArray = copy.execute(s);
            assert internalArray.length == s.length();
            return internalArray;
        }

        @Specialization
        static Object[] doNativeObject(NativeSequenceStorage s,
                        @Exclusive @Cached GetItemScalarNode getItemNode) {
            return materializeGeneric(s, s.length(), getItemNode);
        }

        @Specialization
        static Object[] doEmptySequenceStorage(EmptySequenceStorage s) {
            return s.getInternalArray();
        }

        @Specialization(replaces = {"doObjectSequenceStorage", "doTypedSequenceStorage", "doNativeObject", "doEmptySequenceStorage"})
        static Object[] doGeneric(SequenceStorage s,
                        @Cached LenNode lenNode,
                        @Exclusive @Cached GetItemScalarNode getItemNode) {
            return materializeGeneric(s, lenNode.execute(s), getItemNode);
        }

        private static Object[] materializeGeneric(SequenceStorage s, int len, GetItemScalarNode getItemNode) {
            Object[] barr = new Object[len];
            for (int i = 0; i < barr.length; i++) {
                barr[i] = getItemNode.execute(s, i);
            }
            return barr;
        }

        public static GetInternalObjectArrayNode getUncached() {
            return SequenceStorageNodesFactory.GetInternalObjectArrayNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class ToArrayNode extends Node {
        public abstract Object[] execute(SequenceStorage s);

        @Specialization
        static Object[] doObjectSequenceStorage(ObjectSequenceStorage s,
                        @Cached ConditionProfile profile) {
            Object[] objects = GetInternalObjectArrayNode.doObjectSequenceStorage(s);
            int storageLength = s.length();
            if (profile.profile(storageLength != objects.length)) {
                return exactCopy(objects, storageLength);
            }
            return objects;
        }

        @Specialization(guards = "!isObjectSequenceStorage(s)")
        static Object[] doOther(SequenceStorage s,
                        @Cached GetInternalObjectArrayNode getInternalObjectArrayNode) {
            return getInternalObjectArrayNode.execute(s);
        }

        private static Object[] exactCopy(Object[] barr, int len) {
            return PythonUtils.arrayCopyOf(barr, len);
        }

        static boolean isObjectSequenceStorage(SequenceStorage s) {
            return s instanceof ObjectSequenceStorage;
        }

    }

    @GenerateUncached
    @ImportStatic(SequenceStorageBaseNode.class)
    public abstract static class InsertItemNode extends Node {
        public final SequenceStorage execute(SequenceStorage storage, int index, Object value) {
            return execute(storage, index, value, true);
        }

        protected abstract SequenceStorage execute(SequenceStorage storage, int index, Object value, boolean recursive);

        @Specialization
        protected static SequenceStorage doStorage(EmptySequenceStorage storage, int index, Object value, boolean recursive,
                        @Cached InsertItemNode recursiveNode) {
            if (!recursive) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            SequenceStorage newStorage = storage.generalizeFor(value, null);
            return recursiveNode.execute(newStorage, index, value, false);
        }

        @Specialization(limit = "MAX_ARRAY_STORAGES", guards = {"storage.getClass() == cachedClass"})
        protected static SequenceStorage doStorage(BasicSequenceStorage storage, int index, Object value, boolean recursive,
                        @Cached InsertItemNode recursiveNode,
                        @Cached("storage.getClass()") Class<? extends SequenceStorage> cachedClass) {
            try {
                cachedClass.cast(storage).insertItem(index, value);
                return storage;
            } catch (SequenceStoreException e) {
                if (!recursive) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
                SequenceStorage newStorage = cachedClass.cast(storage).generalizeFor(value, null);
                return recursiveNode.execute(newStorage, index, value, false);
            }
        }

        @Specialization
        protected static SequenceStorage doStorage(NativeSequenceStorage storage, int index, Object value, @SuppressWarnings("unused") boolean recursive,
                        @Cached EnsureCapacityNode ensureCapacityNode,
                        @Cached GetItemScalarNode getItem,
                        @Cached SetItemScalarNode setItem) {
            int newLength = storage.length() + 1;
            ensureCapacityNode.execute(storage, newLength);
            for (int i = storage.length(); i > index; i--) {
                setItem.execute(storage, i, getItem.execute(storage, i - 1));
            }
            setItem.execute(storage, index, value);
            storage.setNewLength(newLength);
            return storage;
        }

        public static InsertItemNode create() {
            return InsertItemNodeGen.create();
        }

        public static InsertItemNode getUncached() {
            return InsertItemNodeGen.getUncached();
        }
    }

    public abstract static class CreateStorageFromIteratorNode extends Node {
        public abstract SequenceStorage execute(VirtualFrame frame, Object iterator, int len);

        public final SequenceStorage execute(VirtualFrame frame, Object iterator) {
            return execute(frame, iterator, -1);
        }

        private static final int START_SIZE = 4;

        protected SequenceStorage createStorage(VirtualFrame frame, Object iterator, int len, ListStorageType type, GetNextNode nextNode, IsBuiltinClassProfile errorProfile,
                        ConditionProfile growArrayProfile) {
            final int size = len > 0 ? len : START_SIZE;
            if (type == Uninitialized || type == Empty) {
                return createStorageUninitialized(frame, iterator, nextNode, errorProfile, size);
            } else {
                int i = 0;
                Object array = null;
                try {
                    switch (type) {
                        case Boolean: {
                            boolean[] elements = new boolean[size];
                            array = elements;
                            try {
                                while (true) {
                                    boolean value = nextNode.executeBoolean(frame, iterator);
                                    if (growArrayProfile.profile(i >= elements.length)) {
                                        array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i++] = value;
                                }
                            } catch (PException e) {
                                LoopNode.reportLoopCount(this, i);
                                e.expectStopIteration(errorProfile);
                            }
                            return new BoolSequenceStorage(elements, i);
                        }
                        case Byte: {
                            byte[] elements = new byte[size];
                            array = elements;
                            try {
                                while (true) {
                                    int value = nextNode.executeInt(frame, iterator);
                                    byte bvalue;
                                    try {
                                        bvalue = PInt.byteValueExact(value);
                                        if (growArrayProfile.profile(i >= elements.length)) {
                                            array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                        }
                                        elements[i++] = bvalue;
                                    } catch (OverflowException e) {
                                        throw new UnexpectedResultException(value);
                                    }
                                }
                            } catch (PException e) {
                                LoopNode.reportLoopCount(this, i);
                                e.expectStopIteration(errorProfile);
                            }
                            return new ByteSequenceStorage(elements, i);
                        }
                        case Int: {
                            int[] elements = new int[size];
                            array = elements;
                            try {
                                while (true) {
                                    int value = nextNode.executeInt(frame, iterator);
                                    if (growArrayProfile.profile(i >= elements.length)) {
                                        array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i++] = value;
                                }
                            } catch (PException e) {
                                LoopNode.reportLoopCount(this, i);
                                e.expectStopIteration(errorProfile);
                            }
                            return new IntSequenceStorage(elements, i);
                        }
                        case Long: {
                            long[] elements = new long[size];
                            array = elements;
                            try {
                                while (true) {
                                    long value = nextNode.executeLong(frame, iterator);
                                    if (growArrayProfile.profile(i >= elements.length)) {
                                        array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i++] = value;
                                }
                            } catch (PException e) {
                                LoopNode.reportLoopCount(this, i);
                                e.expectStopIteration(errorProfile);
                            }
                            return new LongSequenceStorage(elements, i);
                        }
                        case Double: {
                            double[] elements = new double[size];
                            array = elements;
                            try {
                                while (true) {
                                    double value = nextNode.executeDouble(frame, iterator);
                                    if (growArrayProfile.profile(i >= elements.length)) {
                                        array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i++] = value;
                                }
                            } catch (PException e) {
                                LoopNode.reportLoopCount(this, i);
                                e.expectStopIteration(errorProfile);
                            }
                            return new DoubleSequenceStorage(elements, i);
                        }
                        case Generic: {
                            Object[] elements = new Object[size];
                            try {
                                while (true) {
                                    Object value = nextNode.execute(frame, iterator);
                                    if (growArrayProfile.profile(i >= elements.length)) {
                                        elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i++] = value;
                                }
                            } catch (PException e) {
                                LoopNode.reportLoopCount(this, i);
                                e.expectStopIteration(errorProfile);
                            }
                            return new ObjectSequenceStorage(elements, i);
                        }
                        default:
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            throw new RuntimeException("unexpected state");
                    }
                } catch (UnexpectedResultException e) {
                    return genericFallback(frame, iterator, array, i, e.getResult(), nextNode, errorProfile, growArrayProfile);
                }
            }
        }

        private SequenceStorage createStorageUninitialized(VirtualFrame frame, Object iterator, GetNextNode nextNode, IsBuiltinClassProfile errorProfile, int size) {
            Object[] elements = new Object[size];
            int i = 0;
            while (true) {
                try {
                    Object value = nextNode.execute(frame, iterator);
                    if (i >= elements.length) {
                        // Intentionally not profiled, because "size" can be reprofiled after this
                        // first initialization run
                        elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                    }
                    elements[i++] = value;
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    LoopNode.reportLoopCount(this, i);
                    break;
                }
            }
            return SequenceStorageFactory.createStorage(PythonUtils.arrayCopyOf(elements, i));
        }

        private SequenceStorage genericFallback(VirtualFrame frame, Object iterator, Object array, int count, Object result, GetNextNode nextNode, IsBuiltinClassProfile errorProfile,
                        ConditionProfile growArrayProfile) {
            Object[] elements = new Object[Array.getLength(array) * 2];
            int i = 0;
            for (; i < count; i++) {
                elements[i] = Array.get(array, i);
            }
            elements[i++] = result;
            while (true) {
                try {
                    Object value = nextNode.execute(frame, iterator);
                    if (growArrayProfile.profile(i >= elements.length)) {
                        elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                    }
                    elements[i++] = value;
                } catch (PException e) {
                    LoopNode.reportLoopCount(this, i);
                    e.expectStopIteration(errorProfile);
                    break;
                }
            }
            return new ObjectSequenceStorage(elements, i);
        }

        /**
         * This version is specific to builtin iterators and looks for STOP_MARKER instead of
         * StopIteration.
         */
        protected static SequenceStorage createStorageFromBuiltin(VirtualFrame frame, PBuiltinIterator iterator, int len, ListStorageType type, NextNode nextNode, IsBuiltinClassProfile errorProfile,
                        ConditionProfile growArrayProfile, LoopConditionProfile loopProfile) {
            final int size = len > 0 ? len : START_SIZE;
            if (type == Uninitialized || type == Empty) {
                Object[] elements = new Object[size];
                int i = 0;
                try {
                    Object value;
                    for (; loopProfile.profile((value = nextNode.execute(frame, iterator)) != STOP_MARKER); i++) {
                        if (growArrayProfile.profile(i >= elements.length)) {
                            elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                        }
                        elements[i] = value;
                    }
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                }
                return SequenceStorageFactory.createStorage(PythonUtils.arrayCopyOf(elements, i));
            } else {
                int i = 0;
                Object array = null;
                try {
                    Object value;
                    switch (type) {
                        case Boolean: {
                            boolean[] elements = new boolean[size];
                            array = elements;
                            try {
                                for (; loopProfile.profile((value = nextNode.execute(frame, iterator)) != STOP_MARKER); i++) {
                                    if (growArrayProfile.profile(i >= elements.length)) {
                                        elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                        array = elements;
                                    }
                                    elements[i] = PGuards.expectBoolean(value);
                                }
                            } catch (PException e) {
                                e.expectStopIteration(errorProfile);
                            }
                            return new BoolSequenceStorage(elements, i);
                        }
                        case Byte: {
                            byte[] elements = new byte[size];
                            array = elements;
                            try {
                                for (; loopProfile.profile((value = nextNode.execute(frame, iterator)) != STOP_MARKER); i++) {
                                    byte bvalue;
                                    try {
                                        bvalue = PInt.byteValueExact(PGuards.expectInteger(value));
                                        if (growArrayProfile.profile(i >= elements.length)) {
                                            array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                        }
                                        elements[i] = bvalue;
                                    } catch (OverflowException e) {
                                        throw new UnexpectedResultException(value);
                                    }
                                }
                            } catch (PException e) {
                                e.expectStopIteration(errorProfile);
                            }
                            return new ByteSequenceStorage(elements, i);
                        }
                        case Int: {
                            int[] elements = new int[size];
                            array = elements;
                            try {
                                for (; loopProfile.profile((value = nextNode.execute(frame, iterator)) != STOP_MARKER); i++) {
                                    if (growArrayProfile.profile(i >= elements.length)) {
                                        array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i] = PGuards.expectInteger(value);
                                }
                            } catch (PException e) {
                                e.expectStopIteration(errorProfile);
                            }
                            return new IntSequenceStorage(elements, i);
                        }
                        case Long: {
                            long[] elements = new long[size];
                            array = elements;
                            try {
                                for (; loopProfile.profile((value = nextNode.execute(frame, iterator)) != STOP_MARKER); i++) {
                                    if (growArrayProfile.profile(i >= elements.length)) {
                                        array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i] = PGuards.expectLong(value);
                                }
                            } catch (PException e) {
                                e.expectStopIteration(errorProfile);
                            }
                            return new LongSequenceStorage(elements, i);
                        }
                        case Double: {
                            double[] elements = new double[size];
                            array = elements;
                            try {
                                for (; loopProfile.profile((value = nextNode.execute(frame, iterator)) != STOP_MARKER); i++) {
                                    if (growArrayProfile.profile(i >= elements.length)) {
                                        array = elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i] = PGuards.expectDouble(value);
                                }
                            } catch (PException e) {
                                e.expectStopIteration(errorProfile);
                            }
                            return new DoubleSequenceStorage(elements, i);
                        }
                        case Generic: {
                            Object[] elements = new Object[size];
                            try {
                                for (; loopProfile.profile((value = nextNode.execute(frame, iterator)) != STOP_MARKER); i++) {
                                    if (growArrayProfile.profile(i >= elements.length)) {
                                        elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                                    }
                                    elements[i] = value;
                                }
                            } catch (PException e) {
                                e.expectStopIteration(errorProfile);
                            }
                            return new ObjectSequenceStorage(elements, i);
                        }
                        default:
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            throw new RuntimeException("unexpected state");
                    }
                } catch (UnexpectedResultException e) {
                    return genericFallback(frame, iterator, array, i, e.getResult(), nextNode, errorProfile);
                }
            }
        }

        private static SequenceStorage genericFallback(VirtualFrame frame, PBuiltinIterator iterator, Object array, int count, Object result, NextNode nextNode, IsBuiltinClassProfile errorProfile) {
            Object[] elements = new Object[Array.getLength(array) * 2];
            int i = 0;
            for (; i < count; i++) {
                elements[i] = Array.get(array, i);
            }
            elements[i++] = result;
            Object value;
            try {
                while ((value = nextNode.execute(frame, iterator)) != STOP_MARKER) {
                    if (i >= elements.length) {
                        elements = PythonUtils.arrayCopyOf(elements, elements.length * 2);
                    }
                    elements[i++] = value;
                }
            } catch (PException e) {
                e.expectStopIteration(errorProfile);
            }
            return new ObjectSequenceStorage(elements, i);
        }

        public abstract static class CreateStorageFromIteratorNodeCached extends CreateStorageFromIteratorNode {

            @Child private GetClassNode getClass = GetClassNode.create();
            @Child private GetElementType getElementType;

            @CompilationFinal private ListStorageType expectedElementType = Uninitialized;

            private static final int MAX_PREALLOCATE_SIZE = 32;
            @CompilationFinal int startSizeProfiled = START_SIZE;

            public boolean isBuiltinIterator(Object iterator) {
                return iterator instanceof PBuiltinIterator && getClass.execute((PBuiltinIterator) iterator) == PythonBuiltinClassType.PIterator;
            }

            public static SequenceStorage getSequenceStorage(GetInternalIteratorSequenceStorage node, PBuiltinIterator iterator) {
                return iterator.index != 0 || iterator.isExhausted() ? null : node.execute(iterator);
            }

            @Specialization(guards = {"isBuiltinIterator(it)", "storage != null"})
            public SequenceStorage createBuiltinFastPath(PBuiltinIterator it, int len,
                            @Cached GetInternalIteratorSequenceStorage getIterSeqStorageNode,
                            @Bind("getSequenceStorage(getIterSeqStorageNode, it)") SequenceStorage storage,
                            @Cached CopyNode copyNode) {
                it.setExhausted();
                return copyNode.execute(storage);
            }

            @Specialization(replaces = "createBuiltinFastPath", guards = {"isBuiltinIterator(iterator)", "len < 0"})
            public SequenceStorage createBuiltinUnknownLen(VirtualFrame frame, PBuiltinIterator iterator, int len,
                            @Cached BuiltinIteratorLengthHint lengthHint,
                            @Shared("loopProfile") @Cached LoopConditionProfile loopProfile,
                            @Shared("errProfile") @Cached IsBuiltinClassProfile errorProfile,
                            @Shared("arrayGrowProfile") @Cached("createCountingProfile()") ConditionProfile arrayGrowProfile,
                            @Cached NextNode nextNode) {
                int expectedLen = lengthHint.execute(iterator);
                if (expectedLen < 0) {
                    expectedLen = startSizeProfiled;
                }
                SequenceStorage s = createStorageFromBuiltin(frame, iterator, expectedLen, expectedElementType, nextNode, errorProfile, arrayGrowProfile, loopProfile);
                return profileResult(s, true);
            }

            @Specialization(replaces = "createBuiltinFastPath", guards = {"isBuiltinIterator(iterator)", "len >= 0"})
            public SequenceStorage createBuiltinKnownLen(VirtualFrame frame, PBuiltinIterator iterator, int len,
                            @Shared("loopProfile") @Cached LoopConditionProfile loopProfile,
                            @Shared("errProfile") @Cached IsBuiltinClassProfile errorProfile,
                            @Shared("arrayGrowProfile") @Cached("createCountingProfile()") ConditionProfile arrayGrowProfile,
                            @Cached NextNode nextNode) {
                SequenceStorage s = createStorageFromBuiltin(frame, iterator, len, expectedElementType, nextNode, errorProfile, arrayGrowProfile, loopProfile);
                return profileResult(s, false);
            }

            @Specialization(guards = {"!isBuiltinIterator(iterator)", "len < 0"})
            public SequenceStorage createGenericUnknownLen(VirtualFrame frame, Object iterator, int len,
                            @Shared("errProfile") @Cached IsBuiltinClassProfile errorProfile,
                            @Shared("arrayGrowProfile") @Cached("createCountingProfile()") ConditionProfile arrayGrowProfile,
                            @Cached GetNextNode getNextNode) {
                SequenceStorage s = createStorage(frame, iterator, startSizeProfiled, expectedElementType, getNextNode, errorProfile, arrayGrowProfile);
                return profileResult(s, true);
            }

            @Specialization(guards = {"!isBuiltinIterator(iterator)", "len >= 0"})
            public SequenceStorage createGenericKnownLen(VirtualFrame frame, Object iterator, int len,
                            @Shared("errProfile") @Cached IsBuiltinClassProfile errorProfile,
                            @Shared("arrayGrowProfile") @Cached("createCountingProfile()") ConditionProfile arrayGrowProfile,
                            @Cached GetNextNode getNextNode) {
                SequenceStorage s = createStorage(frame, iterator, len, expectedElementType, getNextNode, errorProfile, arrayGrowProfile);
                return profileResult(s, false);
            }

            private SequenceStorage profileResult(SequenceStorage storage, boolean profileLength) {
                if (CompilerDirectives.inInterpreter() && profileLength) {
                    int actualLen = storage.length();
                    if (startSizeProfiled < actualLen && actualLen <= MAX_PREALLOCATE_SIZE) {
                        startSizeProfiled = actualLen;
                    }
                }
                if (getElementType == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getElementType = insert(GetElementType.create());
                }
                ListStorageType actualElementType = getElementType.execute(storage);
                if (expectedElementType != actualElementType) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    expectedElementType = actualElementType;
                }
                return storage;
            }
        }

        private static final class CreateStorageFromIteratorUncachedNode extends CreateStorageFromIteratorNode {
            public static final CreateStorageFromIteratorUncachedNode INSTANCE = new CreateStorageFromIteratorUncachedNode();

            @Override
            public SequenceStorage execute(VirtualFrame frame, Object iterator, int len) {
                return executeImpl(iterator, len);
            }

            @TruffleBoundary
            private SequenceStorage executeImpl(Object iterator, int len) {
                if (iterator instanceof PBuiltinIterator) {
                    PBuiltinIterator pbi = (PBuiltinIterator) iterator;
                    if (GetClassNode.getUncached().execute(pbi) == PythonBuiltinClassType.PIterator && pbi.index == 0 && !pbi.isExhausted()) {
                        SequenceStorage s = GetInternalIteratorSequenceStorage.getUncached().execute(pbi);
                        if (s != null) {
                            return s.copy();
                        }
                    }
                }
                return create().createStorageUninitialized(null, iterator, GetNextNode.getUncached(), IsBuiltinClassProfile.getUncached(), len >= 0 ? len : START_SIZE);
            }
        }

        public static CreateStorageFromIteratorNode create() {
            return CreateStorageFromIteratorNodeCachedNodeGen.create();
        }

        public static CreateStorageFromIteratorNode getUncached() {
            return CreateStorageFromIteratorUncachedNode.INSTANCE;
        }
    }
}
