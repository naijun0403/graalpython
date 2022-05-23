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
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.LenNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class HashingCollectionNodes {

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class LenNode extends PNodeWithContext {
        public abstract int execute(PHashingCollection c);

        @Specialization(limit = "4")
        static int getLen(PHashingCollection c,
                        @CachedLibrary("c.getDictStorage()") HashingStorageLibrary lib) {
            return lib.length(c.getDictStorage());
        }

        public static LenNode create() {
            return LenNodeGen.create();
        }
    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class SetItemNode extends PNodeWithContext {
        public abstract void execute(Frame frame, PHashingCollection c, Object key, Object value);

        @Specialization(limit = "4")
        static void doSetItem(Frame frame, PHashingCollection c, Object key, Object value,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary("c.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage storage = c.getDictStorage();
            storage = lib.setItemWithFrame(storage, key, value, hasFrame, (VirtualFrame) frame);
            c.setDictStorage(storage);
        }

        public static SetItemNode create() {
            return SetItemNodeGen.create();
        }

        public static SetItemNode getUncached() {
            return SetItemNodeGen.getUncached();
        }
    }

    @ImportStatic({PGuards.class})
    abstract static class SetValueHashingStorageNode extends PNodeWithContext {
        abstract HashingStorage execute(VirtualFrame frame, HashingStorage iterator, Object value);

        @Specialization
        static HashingStorage doEconomicStorage(VirtualFrame frame, EconomicMapStorage map, Object value,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached.Exclusive @Cached ConditionProfile findProfile) {
            // We want to avoid calling __hash__() during map.put
            HashingStorageLibrary.HashingStorageIterable<EconomicMapStorage.DictKey> iter = map.dictKeys();
            for (EconomicMapStorage.DictKey key : iter) {
                map.setValue(frame, key, value, findProfile, eqNode);
            }
            return map;
        }

        @Specialization(guards = "!isEconomicMapStorage(map)")
        static HashingStorage doGeneric(VirtualFrame frame, HashingStorage map, Object value,
                        @Cached ConditionProfile hasFrame,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorageLibrary.HashingStorageIterable<Object> iter = lib.keys(map);
            HashingStorage storage = map;
            for (Object key : iter) {
                storage = lib.setItemWithFrame(storage, key, value, hasFrame, frame);
            }
            return storage;
        }

        protected static boolean isEconomicMapStorage(Object o) {
            return o instanceof EconomicMapStorage;
        }
    }

    /**
     * Gets clone of the keys of the storage with all values either set to given value or with no
     * guarantees about the values if {@link PNone#NO_VALUE} is passed as {@code value}.
     */
    @ImportStatic({PGuards.class, PythonOptions.class})
    public abstract static class GetClonedHashingStorageNode extends PNodeWithContext {
        @Child private PRaiseNode raise;

        public abstract HashingStorage execute(VirtualFrame frame, Object iterator, Object value);

        public final HashingStorage doNoValue(VirtualFrame frame, Object iterator) {
            return execute(frame, iterator, PNone.NO_VALUE);
        }

        @Specialization(guards = "isNoValue(value)", limit = "1")
        static HashingStorage doHashingCollectionNoValue(PHashingCollection other, @SuppressWarnings("unused") Object value,
                        @CachedLibrary("other.getDictStorage()") HashingStorageLibrary lib) {
            return lib.copy(other.getDictStorage());
        }

        @Specialization(guards = "isNoValue(value)", limit = "1")
        static HashingStorage doPDictKeyViewNoValue(PDictView.PDictKeysView other, Object value,
                        @CachedLibrary("other.getWrappedDict().getDictStorage()") HashingStorageLibrary lib) {
            return doHashingCollectionNoValue(other.getWrappedDict(), value, lib);
        }

        @Specialization(guards = "!isNoValue(value)", limit = "1")
        static HashingStorage doHashingCollection(VirtualFrame frame, PHashingCollection other, Object value,
                        @Cached SetValueHashingStorageNode setValue,
                        @CachedLibrary("other.getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage storage = lib.copy(other.getDictStorage());
            storage = setValue.execute(frame, storage, value);
            return storage;
        }

        @Specialization(guards = "!isNoValue(value)", limit = "1")
        static HashingStorage doPDictView(VirtualFrame frame, PDictView.PDictKeysView other, Object value,
                        @Cached SetValueHashingStorageNode setValue,
                        @CachedLibrary("other.getWrappedDict().getDictStorage()") HashingStorageLibrary lib) {
            return doHashingCollection(frame, other.getWrappedDict(), value, setValue, lib);
        }

        @Specialization
        static HashingStorage doString(VirtualFrame frame, String str, Object value,
                        @Shared("hasFrame") @Cached ConditionProfile hasFrame,
                        @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage storage = PDict.createNewStorage(true, PString.length(str));
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            for (int i = 0; i < PString.length(str); i++) {
                String key = PString.valueOf(PString.charAt(str, i));
                storage = lib.setItemWithFrame(storage, key, val, hasFrame, frame);
            }
            return storage;
        }

        @Specialization
        static HashingStorage doString(VirtualFrame frame, PString pstr, Object value,
                        @Shared("hasFrame") @Cached ConditionProfile hasFrame,
                        @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            return doString(frame, pstr.getValue(), value, hasFrame, lib);
        }

        @Specialization(guards = {"!isPHashingCollection(other)", "!isDictKeysView(other)", "!isString(other)"})
        static HashingStorage doIterable(VirtualFrame frame, Object other, Object value,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Shared("hasFrame") @Cached ConditionProfile hasFrame,
                        @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage curStorage = EmptyStorage.INSTANCE;
            Object iterator = getIter.execute(frame, other);
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            while (true) {
                Object key;
                try {
                    key = nextNode.execute(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return curStorage;
                }
                curStorage = lib.setItemWithFrame(curStorage, key, val, hasFrame, frame);
            }
        }

        @Fallback
        HashingStorage fail(Object other, @SuppressWarnings("unused") Object value) {
            if (raise == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raise = insert(PRaiseNode.create());
            }
            throw raise.raise(TypeError, ErrorMessages.OBJ_NOT_ITERABLE, other);
        }
    }

    /**
     * Returns {@link HashingStorage} with the same keys as the given iterator. There is no
     * guarantee about the values!
     */
    @ImportStatic({SpecialMethodNames.class, PGuards.class})
    public abstract static class GetHashingStorageNode extends PNodeWithContext {

        public abstract HashingStorage execute(VirtualFrame frame, Object iterator);

        @Specialization
        static HashingStorage doHashingCollection(PHashingCollection other) {
            return other.getDictStorage();
        }

        @Specialization
        static HashingStorage doPDictView(PDictView.PDictKeysView other) {
            return other.getWrappedDict().getDictStorage();
        }

        @Specialization(guards = {"!isPHashingCollection(other)", "!isDictKeysView(other)"})
        static HashingStorage doGeneric(VirtualFrame frame, Object other,
                        @Cached GetClonedHashingStorageNode getHashingStorageNode) {
            return getHashingStorageNode.doNoValue(frame, other);
        }
    }
}
