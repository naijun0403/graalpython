/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.partial;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_PARTIAL_STATE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETSTATE__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectReprAsJavaStringNode;
import com.oracle.graal.python.lib.PyObjectStrAsJavaStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PPartial)
public class PartialBuiltins extends PythonBuiltins {
    public static Object[] getNewPartialArgs(PPartial partial, Object[] args, ConditionProfile hasArgsProfile) {
        return getNewPartialArgs(partial, args, hasArgsProfile, 0);
    }

    public static Object[] getNewPartialArgs(PPartial partial, Object[] args, ConditionProfile hasArgsProfile, int offset) {
        Object[] newArgs;
        Object[] pArgs = partial.getArgs();
        if (hasArgsProfile.profile(args.length > offset)) {
            newArgs = new Object[pArgs.length + args.length - offset];
            PythonUtils.arraycopy(pArgs, 0, newArgs, 0, pArgs.length);
            PythonUtils.arraycopy(args, offset, newArgs, pArgs.length, args.length - offset);
        } else {
            newArgs = pArgs;
        }
        return newArgs;
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PartialBuiltinsFactory.getFactories();
    }

    @Builtin(name = "func", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, doc = "function object to use in future partial calls")
    @GenerateNodeFactory
    public abstract static class PartialFuncNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGet(PPartial self) {
            return self.getFn();
        }
    }

    @Builtin(name = "args", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, doc = "tuple of arguments to future partial calls")
    @GenerateNodeFactory
    public abstract static class PartialArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doGet(PPartial self) {
            return self.getArgsTuple(factory());
        }
    }

    @Builtin(name = __DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class PartialDictNode extends PythonBinaryBuiltinNode {
        @Specialization
        protected Object getDict(PPartial self, @SuppressWarnings("unused") PNone mapping,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(self);
        }

        @Specialization
        protected Object setDict(PPartial self, PDict mapping,
                        @Cached SetDictNode setDict) {
            setDict.execute(self, mapping);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(mapping)", "!isDict(mapping)"})
        protected Object setDict(@SuppressWarnings("unused") PPartial self, Object mapping) {
            throw raise(TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, mapping);
        }
    }

    @Builtin(name = "keywords", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, doc = "dictionary of keyword arguments to future partial calls")
    @GenerateNodeFactory
    public abstract static class PartialKeywordsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doGet(PPartial self) {
            return self.getOrCreateKw(factory());
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PartialReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reduce(PPartial self,
                        @Cached GetClassNode getClassNode,
                        @Cached GetDictIfExistsNode getDictIfExistsNode,
                        @Cached GetOrCreateDictNode getOrCreateDictNode) {
            final PDict dict;
            if (self.getShape().getPropertyCount() > 0) {
                dict = getOrCreateDictNode.execute(self);
            } else {
                dict = getDictIfExistsNode.execute(self);
            }
            return factory().createTuple(new Object[]{
                            getClassNode.execute(self),
                            factory().createTuple(new Object[]{self.getFn()}),
                            factory().createTuple(new Object[]{self.getFn(), self.getArgsTuple(factory()), self.getKw(), (dict != null) ? dict : PNone.NONE})});
        }
    }

    @Builtin(name = __SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PartialSetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object setState(VirtualFrame frame, PPartial self, PTuple state,
                        @Cached SetDictNode setDictNode,
                        @Cached SequenceNodes.GetSequenceStorageNode storageNode,
                        @Cached SequenceStorageNodes.GetInternalObjectArrayNode arrayNode,
                        @Cached PyCallableCheckNode callableCheckNode,
                        @Cached TupleBuiltins.GetItemNode getItemNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            if (lenNode.execute(state.getSequenceStorage()) != 4) {
                throw raise(PythonBuiltinClassType.TypeError, INVALID_PARTIAL_STATE);
            }

            final Object function = getItemNode.execute(frame, state, 0);
            final Object fnArgs = getItemNode.execute(frame, state, 1);
            final Object fnKwargs = getItemNode.execute(frame, state, 2);
            final Object dict = getItemNode.execute(frame, state, 3);

            if (!callableCheckNode.execute(function) ||
                            !PGuards.isPTuple(fnArgs) ||
                            (fnKwargs != PNone.NONE && !PGuards.isDict(fnKwargs))) {
                throw raise(PythonBuiltinClassType.TypeError, INVALID_PARTIAL_STATE);
            }

            self.setFn(function);

            assert fnArgs instanceof PTuple;
            self.setArgs((PTuple) fnArgs, storageNode, arrayNode);

            assert fnKwargs instanceof PDict;
            self.setKwCopy((PDict) fnKwargs, factory(), lib);

            if (dict != PNone.NONE) {
                assert dict instanceof PDict;
                setDictNode.execute(self, (PDict) dict);
            }

            return PNone.NONE;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object fallback(Object self, Object state) {
            throw raise(PythonBuiltinClassType.TypeError, INVALID_PARTIAL_STATE);
        }
    }

    @Builtin(name = __CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class PartialCallNode extends PythonVarargsBuiltinNode {
        private int indexOf(PKeyword[] keywords, PKeyword kw) {
            for (int i = 0; i < keywords.length; i++) {
                if (keywords[i].getName().equals(kw.getName())) {
                    return i;
                }
            }
            return -1;
        }

        protected boolean withKeywords(PKeyword[] keywords) {
            return keywords.length > 0;
        }

        @Specialization(guards = "!self.hasKw(lib)")
        Object callWoDict(VirtualFrame frame, PPartial self, Object[] args, PKeyword[] keywords,
                        @Cached ConditionProfile hasArgsProfile,
                        @Cached CallVarargsMethodNode callNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object[] callArgs = getNewPartialArgs(self, args, hasArgsProfile);
            return callNode.execute(frame, self.getFn(), callArgs, keywords);
        }

        @Specialization(guards = {"self.hasKw(lib)", "!withKeywords(keywords)"})
        Object callWDictWoKw(VirtualFrame frame, PPartial self, Object[] args, PKeyword[] keywords,
                        @Cached ExpandKeywordStarargsNode starargsNode,
                        @Cached ConditionProfile hasArgsProfile,
                        @Cached CallVarargsMethodNode callNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object[] callArgs = getNewPartialArgs(self, args, hasArgsProfile);
            return callNode.execute(frame, self.getFn(), callArgs, starargsNode.execute(self.getKw()));
        }

        @Specialization(guards = {"self.hasKw(lib)", "withKeywords(keywords)"})
        Object callWDictWKw(VirtualFrame frame, PPartial self, Object[] args, PKeyword[] keywords,
                        @Cached ExpandKeywordStarargsNode starargsNode,
                        @Cached ConditionProfile hasArgsProfile,
                        @Cached CallVarargsMethodNode callNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            Object[] callArgs = getNewPartialArgs(self, args, hasArgsProfile);

            final PKeyword[] pKeywords = starargsNode.execute(self.getKw());
            PKeyword[] callKeywords = new PKeyword[pKeywords.length + keywords.length];
            PythonUtils.arraycopy(pKeywords, 0, callKeywords, 0, pKeywords.length);
            int kwIndex = pKeywords.length;
            // check for overriding keywords and store the new ones
            for (PKeyword kw : keywords) {
                int idx = indexOf(pKeywords, kw);
                if (idx == -1) {
                    callKeywords[kwIndex] = kw;
                    kwIndex += 1;
                } else {
                    callKeywords[idx] = kw;
                }
            }
            callKeywords = PythonUtils.arrayCopyOfRange(callKeywords, 0, kwIndex);

            return callNode.execute(frame, self.getFn(), callArgs, callKeywords);
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PartialReprNode extends PythonUnaryBuiltinNode {
        private static void reprArgs(VirtualFrame frame, PPartial partial, StringBuilder sb, PyObjectReprAsJavaStringNode reprNode) {
            for (Object arg : partial.getArgs()) {
                PythonUtils.append(sb, ", ");
                PythonUtils.append(sb, reprNode.execute(frame, arg));
            }
        }

        private static void reprKwArgs(VirtualFrame frame, PPartial partial, StringBuilder sb, PyObjectReprAsJavaStringNode reprNode, PyObjectStrAsJavaStringNode strNode, HashingStorageLibrary lib) {
            final PDict kwDict = partial.getKw();
            if (kwDict != null) {
                final HashingStorage storage = kwDict.getDictStorage();
                for (Object key : lib.keys(storage)) {
                    final Object value = lib.getItem(storage, key);
                    PythonUtils.append(sb, ", ");
                    PythonUtils.append(sb, strNode.execute(frame, key));
                    PythonUtils.append(sb, "=");
                    PythonUtils.append(sb, reprNode.execute(frame, value));
                }
            }
        }

        @Specialization
        public static Object repr(VirtualFrame frame, PPartial partial,
                        @Cached PyObjectStrAsJavaStringNode strNode,
                        @Cached PyObjectReprAsJavaStringNode reprNode,
                        @Cached GetClassNode classNode,
                        @Cached TypeNodes.GetNameNode nameNode,
                        @Cached ObjectNodes.GetFullyQualifiedClassNameNode classNameNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            final Object cls = classNode.execute(partial);
            final String name = (cls == PythonBuiltinClassType.PPartial) ? classNameNode.execute(frame, partial) : nameNode.execute(cls);
            StringBuilder sb = PythonUtils.newStringBuilder(name);
            PythonUtils.append(sb, "(");
            PythonContext ctxt = PythonContext.get(classNameNode);
            if (!ctxt.reprEnter(partial)) {
                return "...";
            }
            try {
                PythonUtils.append(sb, reprNode.execute(frame, partial.getFn()));
                reprArgs(frame, partial, sb, reprNode);
                reprKwArgs(frame, partial, sb, reprNode, strNode, lib);
                PythonUtils.append(sb, ")");
                return PythonUtils.sbToString(sb);
            } finally {
                ctxt.reprLeave(partial);
            }
        }
    }
}
