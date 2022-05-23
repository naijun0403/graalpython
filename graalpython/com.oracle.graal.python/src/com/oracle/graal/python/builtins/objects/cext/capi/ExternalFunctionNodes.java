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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.ErrorMessages.RETURNED_NULL_WO_SETTING_ERROR;
import static com.oracle.graal.python.nodes.ErrorMessages.RETURNED_RESULT_WITH_ERROR_SET;

import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AllToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.BinaryFirstToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ConvertArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FastCallArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FastCallWithKeywordsArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ReleaseNativeWrapperNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.SSizeArgProcToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.SSizeObjArgProcToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TernaryFirstSecondToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TernaryFirstThirdToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToBorrowedRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToJavaStealingNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ReleaseNativeWrapperNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ToBorrowedRefNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ToJavaStealingNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.CheckInquiryResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.CheckIterNextResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.CheckPrimitiveFunctionResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.CreateArgsTupleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.DefaultCheckFunctionResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.InitCheckFunctionResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.MaterializePrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetIndexNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.ConvertPIntToPrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonContextFactory.GetThreadStateNodeGen;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class ExternalFunctionNodes {

    static final String KW_CALLABLE = "$callable";
    static final String KW_CLOSURE = "$closure";
    static final String[] KEYWORDS_HIDDEN_CALLABLE = new String[]{KW_CALLABLE};
    static final String[] KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE = new String[]{KW_CALLABLE, KW_CLOSURE};

    public static PKeyword[] createKwDefaults(Object callable) {
        return new PKeyword[]{new PKeyword(ExternalFunctionNodes.KW_CALLABLE, callable)};
    }

    public static PKeyword[] createKwDefaults(Object callable, Object closure) {
        return new PKeyword[]{new PKeyword(ExternalFunctionNodes.KW_CALLABLE, callable), new PKeyword(ExternalFunctionNodes.KW_CLOSURE, closure)};
    }

    public static Object getHiddenCallable(PKeyword[] kwDefaults) {
        if (kwDefaults.length >= KEYWORDS_HIDDEN_CALLABLE.length) {
            PKeyword kwDefault = kwDefaults[0];
            assert KW_CALLABLE.equals(kwDefault.getName()) : "invalid keyword defaults";
            return kwDefault.getValue();
        }
        throw CompilerDirectives.shouldNotReachHere();
    }

    /**
     * Enum of well-known function and slot signatures. The integer values must stay in sync with
     * the definition in {code capi.h}.
     */
    public enum PExternalFunctionWrapper {
        DIRECT(1),
        FASTCALL(2, FastCallArgsToSulongNode::create),
        FASTCALL_WITH_KEYWORDS(3, FastCallWithKeywordsArgsToSulongNode::create),
        KEYWORDS(4),               // METH_VARARGS | METH_KEYWORDS
        VARARGS(5),                // METH_VARARGS
        NOARGS(6),                 // METH_NOARGS
        O(7),                      // METH_O
        ALLOC(9, BinaryFirstToSulongNode::create),
        GETATTR(10, BinaryFirstToSulongNode::create),
        SETATTR(11, TernaryFirstThirdToSulongNode::create),
        RICHCMP(12, TernaryFirstSecondToSulongNode::create),
        SETITEM(13, SSizeObjArgProcToSulongNode::create),
        UNARYFUNC(14),
        BINARYFUNC(15),
        BINARYFUNC_L(16),
        BINARYFUNC_R(17),
        TERNARYFUNC(18),
        TERNARYFUNC_R(19),
        LT(20, TernaryFirstSecondToSulongNode::create),
        LE(21, TernaryFirstSecondToSulongNode::create),
        EQ(22, TernaryFirstSecondToSulongNode::create),
        NE(23, TernaryFirstSecondToSulongNode::create),
        GT(24, TernaryFirstSecondToSulongNode::create),
        GE(25, TernaryFirstSecondToSulongNode::create),
        ITERNEXT(26, 0, AllToSulongNode::create, CheckIterNextResultNodeGen::create),
        INQUIRY(27, 0, AllToSulongNode::create, CheckInquiryResultNodeGen::create),
        DELITEM(28, 1, SSizeObjArgProcToSulongNode::create),
        GETITEM(29, 0, SSizeArgProcToSulongNode::create),
        GETTER(30, BinaryFirstToSulongNode::create),
        SETTER(31, TernaryFirstSecondToSulongNode::create),
        INITPROC(32, 0, AllToSulongNode::create, InitCheckFunctionResultNodeGen::create),
        HASHFUNC(33, 0, AllToSulongNode::create, CheckPrimitiveFunctionResultNodeGen::create),
        CALL(34),
        SETATTRO(35, 0, AllToSulongNode::create, InitCheckFunctionResultNodeGen::create),
        DESCR_GET(36),
        DESCR_SET(37, 0, AllToSulongNode::create, InitCheckFunctionResultNodeGen::create),
        LENFUNC(38, 0, AllToSulongNode::create, CheckPrimitiveFunctionResultNodeGen::create),
        OBJOBJPROC(39, 0, AllToSulongNode::create, CheckInquiryResultNodeGen::create),
        OBJOBJARGPROC(40, 0, AllToSulongNode::create, CheckPrimitiveFunctionResultNodeGen::create),
        NEW(41),
        MP_DELITEM(42, 0, AllToSulongNode::create, CheckPrimitiveFunctionResultNodeGen::create);

        @CompilationFinal(dimensions = 1) private static final PExternalFunctionWrapper[] VALUES = Arrays.copyOf(values(), values().length);

        PExternalFunctionWrapper(int value, int numDefaults, Supplier<ConvertArgsToSulongNode> convertArgsNodeSupplier, Supplier<CheckFunctionResultNode> checkFunctionResultNodeSupplier) {
            this.value = value;
            this.numDefaults = numDefaults;
            this.convertArgsNodeSupplier = convertArgsNodeSupplier;
            this.checkFunctionResultNodeSupplier = checkFunctionResultNodeSupplier;
        }

        PExternalFunctionWrapper(int value, Supplier<ConvertArgsToSulongNode> convertArgsNodeSupplier) {
            this(value, 0, convertArgsNodeSupplier, DefaultCheckFunctionResultNodeGen::create);
        }

        PExternalFunctionWrapper(int value, int numDefaults, Supplier<ConvertArgsToSulongNode> convertArgsNodeSupplier) {
            this(value, numDefaults, convertArgsNodeSupplier, DefaultCheckFunctionResultNodeGen::create);
        }

        PExternalFunctionWrapper(int value) {
            this(value, 0, AllToSulongNode::create, DefaultCheckFunctionResultNodeGen::create);
        }

        @ExplodeLoop
        static PExternalFunctionWrapper fromValue(int value) {
            for (int i = 0; i < VALUES.length; i++) {
                if (VALUES[i].value == value) {
                    return VALUES[i];
                }
            }
            return null;
        }

        private final int value;
        private final int numDefaults;
        private final Supplier<ConvertArgsToSulongNode> convertArgsNodeSupplier;
        private final Supplier<CheckFunctionResultNode> checkFunctionResultNodeSupplier;

        @TruffleBoundary
        static RootCallTarget getOrCreateCallTarget(PExternalFunctionWrapper sig, PythonLanguage language, String name, boolean doArgAndResultConversion, boolean isStatic) {
            Class<?> nodeKlass;
            Function<PythonLanguage, RootNode> rootNodeFunction;
            switch (sig) {
                case ALLOC:
                    nodeKlass = AllocFuncRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new AllocFuncRootNode(l, name, sig) : l -> new AllocFuncRootNode(l, name);
                    break;
                case DIRECT:
                case DESCR_SET:
                case LENFUNC:
                case HASHFUNC:
                case SETATTRO:
                case OBJOBJPROC:
                case OBJOBJARGPROC:
                case UNARYFUNC:
                case BINARYFUNC:
                case BINARYFUNC_L:
                    /*
                     * If no conversion is requested, this means we directly call a managed function
                     * (without argument conversion). Null indicates this
                     */
                    if (!doArgAndResultConversion) {
                        return null;
                    }
                    nodeKlass = MethDirectRoot.class;
                    rootNodeFunction = l -> MethDirectRoot.create(language, name);
                    break;
                case CALL:
                case INITPROC:
                case KEYWORDS:
                case NEW:
                    nodeKlass = MethKeywordsRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethKeywordsRoot(l, name, isStatic, sig) : l -> new MethKeywordsRoot(l, name, isStatic);
                    break;
                case VARARGS:
                    nodeKlass = MethVarargsRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethVarargsRoot(l, name, isStatic, sig) : l -> new MethVarargsRoot(l, name, isStatic);
                    break;
                case NOARGS:
                case INQUIRY:
                    nodeKlass = MethNoargsRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethNoargsRoot(l, name, isStatic, sig) : l -> new MethNoargsRoot(l, name, isStatic);
                    break;
                case O:
                    nodeKlass = MethORoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethORoot(l, name, isStatic, sig) : l -> new MethORoot(l, name, isStatic);
                    break;
                case FASTCALL:
                    nodeKlass = MethFastcallRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethFastcallRoot(l, name, isStatic, sig) : l -> new MethFastcallRoot(l, name, isStatic);
                    break;
                case FASTCALL_WITH_KEYWORDS:
                    nodeKlass = MethFastcallWithKeywordsRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethFastcallWithKeywordsRoot(l, name, isStatic, sig) : l -> new MethFastcallWithKeywordsRoot(l, name, isStatic);
                    break;
                case GETATTR:
                    nodeKlass = GetAttrFuncRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new GetAttrFuncRootNode(l, name, sig) : l -> new GetAttrFuncRootNode(l, name);
                    break;
                case SETATTR:
                    nodeKlass = SetAttrFuncRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new SetAttrFuncRootNode(l, name, sig) : l -> new SetAttrFuncRootNode(l, name);
                    break;
                case DESCR_GET:
                    nodeKlass = DescrGetRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new DescrGetRootNode(l, name, sig) : l -> new DescrGetRootNode(l, name);
                    break;
                case RICHCMP:
                    nodeKlass = RichCmpFuncRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new RichCmpFuncRootNode(l, name, sig) : l -> new RichCmpFuncRootNode(l, name);
                    break;
                case SETITEM:
                case DELITEM:
                    nodeKlass = SetItemRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new SetItemRootNode(l, name, sig) : l -> new SetItemRootNode(l, name);
                    break;
                case GETITEM:
                    nodeKlass = GetItemRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new GetItemRootNode(l, name, sig) : l -> new GetItemRootNode(l, name);
                    break;
                case BINARYFUNC_R:
                    nodeKlass = MethReverseRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethReverseRootNode(l, name, sig) : l -> new MethReverseRootNode(l, name);
                    break;
                case TERNARYFUNC:
                    nodeKlass = MethPowRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethPowRootNode(l, name, sig) : l -> new MethPowRootNode(l, name);
                    break;
                case TERNARYFUNC_R:
                    nodeKlass = MethRPowRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethRPowRootNode(l, name, sig) : l -> new MethRPowRootNode(l, name);
                    break;
                case GT:
                case GE:
                case LE:
                case LT:
                case EQ:
                case NE:
                    nodeKlass = MethRichcmpOpRootNode.class;
                    int op = getCompareOpCode(sig);
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethRichcmpOpRootNode(l, name, sig, op) : l -> new MethRichcmpOpRootNode(l, name, op);
                    break;
                case ITERNEXT:
                    nodeKlass = IterNextFuncRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new IterNextFuncRootNode(l, name, sig) : l -> new IterNextFuncRootNode(l, name);
                    break;
                case GETTER:
                    /*
                     * If no conversion is requested, this means we directly call a managed function
                     * (without argument conversion). Null indicates this
                     */
                    if (!doArgAndResultConversion) {
                        return null;
                    }
                    nodeKlass = GetterRoot.class;
                    rootNodeFunction = l -> new GetterRoot(l, name, sig);
                    break;
                case SETTER:
                    /*
                     * If no conversion is requested, this means we directly call a managed function
                     * (without argument conversion). Null indicates this
                     */
                    if (!doArgAndResultConversion) {
                        return null;
                    }
                    nodeKlass = SetterRoot.class;
                    rootNodeFunction = l -> new SetterRoot(l, name, sig);
                    break;
                case MP_DELITEM:
                    nodeKlass = MpDelItemRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MpDelItemRootNode(l, name, sig) : l -> new MpDelItemRootNode(l, name);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            return language.createCachedCallTarget(rootNodeFunction, nodeKlass, sig, name, doArgAndResultConversion);
        }

        public static PBuiltinFunction createWrapperFunction(String name, Object callable, Object enclosingType, int flags, int sig,
                        PythonLanguage language,
                        PythonObjectFactory factory,
                        boolean doArgAndResultConversion) {
            return createWrapperFunction(name, callable, enclosingType, flags, PExternalFunctionWrapper.fromValue(sig),
                            language, factory, doArgAndResultConversion);
        }

        /**
         * Creates a built-in function for a specific signature. This built-in function also does
         * appropriate argument and result conversion and calls the provided callable.
         *
         * @param language The Python language object.
         * @param sig The wrapper/signature ID as defined in {@link PExternalFunctionWrapper}.
         * @param name The name of the method.
         * @param callable The native function pointer.
         * @param enclosingType The type the function belongs to (needed for checking of
         *            {@code self}).
         * @param factory Just an instance of {@link PythonObjectFactory} to create the function
         *            object.
         * @return A {@link PBuiltinFunction} implementing the semantics of the specified slot
         *         wrapper.
         */
        @TruffleBoundary
        public static PBuiltinFunction createWrapperFunction(String name, Object callable, Object enclosingType, int flags,
                        PExternalFunctionWrapper sig,
                        PythonLanguage language,
                        PythonObjectFactory factory,
                        boolean doArgAndResultConversion) {
            RootCallTarget callTarget = getOrCreateCallTarget(sig, language, name, doArgAndResultConversion, flags > 0 && CExtContext.isMethStatic(flags));
            if (callTarget == null) {
                return null;
            }
            Object[] defaults;
            int numDefaults = sig.numDefaults;
            if (numDefaults > 0) {
                defaults = new Object[numDefaults];
                Arrays.fill(defaults, PNone.NO_VALUE);
            } else {
                defaults = PythonUtils.EMPTY_OBJECT_ARRAY;
            }
            Object type = SpecialMethodNames.__NEW__.equals(name) ? null : enclosingType;
            // TODO(fa): this should eventually go away
            switch (sig) {
                case NOARGS:
                case O:
                case VARARGS:
                case KEYWORDS:
                case FASTCALL:
                case FASTCALL_WITH_KEYWORDS:
                    return factory.createBuiltinFunction(name, type, defaults, ExternalFunctionNodes.createKwDefaults(callable), flags, callTarget);
            }
            return factory.createWrapperDescriptor(name, type, defaults, ExternalFunctionNodes.createKwDefaults(callable), flags, callTarget);
        }

        private static int getCompareOpCode(PExternalFunctionWrapper sig) {
            // op codes for binary comparisons (defined in 'object.h')
            switch (sig) {
                case LT:
                    return 0;
                case LE:
                    return 1;
                case EQ:
                    return 2;
                case NE:
                    return 3;
                case GT:
                    return 4;
                case GE:
                    return 5;
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        @TruffleBoundary
        ConvertArgsToSulongNode createConvertArgsToSulongNode() {
            return convertArgsNodeSupplier.get();
        }

        @TruffleBoundary
        CheckFunctionResultNode getCheckFunctionResultNode() {
            return checkFunctionResultNodeSupplier.get();
        }
    }

    static final class MethDirectRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, true, 0, false, null, KEYWORDS_HIDDEN_CALLABLE);

        private MethDirectRoot(PythonLanguage lang, String name) {
            super(lang, name, true, PExternalFunctionWrapper.DIRECT);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            // return a copy of the args array since it will be modified
            Object[] varargs = PArguments.getVariableArguments(frame);
            return PythonUtils.arrayCopyOf(varargs, varargs.length);
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            for (int i = 0; i < cArguments.length; i++) {
                ensureReleaseNativeWrapperNode().execute(cArguments[i]);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @TruffleBoundary
        public static MethDirectRoot create(PythonLanguage lang, String name) {
            return new MethDirectRoot(lang, name);
        }
    }

    /**
     * Like {@link com.oracle.graal.python.nodes.call.FunctionInvokeNode} but invokes a C function.
     */
    static final class ExternalFunctionInvokeNode extends PNodeWithContext implements IndirectCallNode {
        @Child private CheckFunctionResultNode checkResultNode;
        @Child private PForeignToPTypeNode fromForeign = PForeignToPTypeNode.create();
        @Child private ToJavaStealingNode asPythonObjectNode = ToJavaStealingNodeGen.create();
        @Child private InteropLibrary lib;
        @Child private PRaiseNode raiseNode;
        @Child private GetThreadStateNode getThreadStateNode = GetThreadStateNodeGen.create();
        @Child private GilNode gilNode = GilNode.create();

        @CompilationFinal private Assumption nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
        @CompilationFinal private Assumption nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();

        @Override
        public Assumption needNotPassFrameAssumption() {
            return nativeCodeDoesntNeedMyFrame;
        }

        @Override
        public Assumption needNotPassExceptionAssumption() {
            return nativeCodeDoesntNeedExceptionState;
        }

        @Override
        public Node copy() {
            ExternalFunctionInvokeNode node = (ExternalFunctionInvokeNode) super.copy();
            node.nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();
            node.nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
            return node;
        }

        @TruffleBoundary
        ExternalFunctionInvokeNode() {
            this.checkResultNode = DefaultCheckFunctionResultNodeGen.create();
        }

        @TruffleBoundary
        ExternalFunctionInvokeNode(CheckFunctionResultNode checkFunctionResultNode) {
            this.checkResultNode = checkFunctionResultNode != null ? checkFunctionResultNode : DefaultCheckFunctionResultNodeGen.create();
        }

        public Object execute(VirtualFrame frame, String name, Object callable, Object[] cArguments) {
            if (lib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                /*
                 * We must use a dispatched library because we cannot be sure that we always see the
                 * same type of callable. For example, in multi-context mode you could see an LLVM
                 * native pointer and an LLVM managed pointer.
                 */
                lib = insert(InteropLibrary.getFactory().createDispatched(2));
            }

            PythonContext ctx = PythonContext.get(this);
            PythonThreadState threadState = getThreadStateNode.execute(ctx);

            // If any code requested the caught exception (i.e. used 'sys.exc_info()'), we store
            // it to the context since we cannot propagate it through the native frames.
            Object state = IndirectCallContext.enter(frame, threadState, this);

            try {
                return fromNative(asPythonObjectNode.execute(checkResultNode.execute(ctx, name, lib.execute(callable, cArguments))));
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ensureRaiseNode().raise(PythonBuiltinClassType.TypeError, ErrorMessages.CALLING_NATIVE_FUNC_FAILED, name, e);
            } catch (ArityException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ensureRaiseNode().raise(PythonBuiltinClassType.TypeError, ErrorMessages.CALLING_NATIVE_FUNC_EXPECTED_ARGS, name, e.getExpectedMinArity(), e.getActualArity());
            } finally {
                /*
                 * Always re-acquire the GIL here. This is necessary because it could happen that C
                 * extensions are releasing the GIL and if then an LLVM exception occurs, C code
                 * wouldn't re-acquire it (unexpectedly).
                 */
                gilNode.acquire();

                /*
                 * Special case after calling a C function: transfer caught exception back to frame
                 * to simulate the global state semantics.
                 */
                PArguments.setException(frame, threadState.getCaughtException());
                IndirectCallContext.exit(frame, threadState, state);
            }
        }

        private Object fromNative(Object result) {
            return fromForeign.executeConvert(result);
        }

        private PRaiseNode ensureRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
        }

        public static ExternalFunctionInvokeNode create() {
            return new ExternalFunctionInvokeNode();
        }

        public static ExternalFunctionInvokeNode create(CheckFunctionResultNode checkFunctionResultNode) {
            return new ExternalFunctionInvokeNode(checkFunctionResultNode);
        }
    }

    abstract static class MethodDescriptorRoot extends PRootNode {
        @Child private CalleeContext calleeContext = CalleeContext.create();
        @Child private CallVarargsMethodNode invokeNode;
        @Child private ExternalFunctionInvokeNode externalInvokeNode;
        @Child private ReadIndexedArgumentNode readSelfNode;
        @Child private ReadIndexedArgumentNode readCallableNode;
        @Child private ReleaseNativeWrapperNode releaseNativeWrapperNode;
        @Child private PRaiseNode raiseNode;
        @Child private ConvertArgsToSulongNode toSulongNode;

        private final String name;

        MethodDescriptorRoot(PythonLanguage language, String name, boolean isStatic) {
            this(language, name, isStatic, null);
        }

        MethodDescriptorRoot(PythonLanguage language, String name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language);
            CompilerAsserts.neverPartOfCompilation();
            this.name = name;
            if (provider != null) {
                this.externalInvokeNode = ExternalFunctionInvokeNode.create(provider.getCheckFunctionResultNode());
                ConvertArgsToSulongNode convertArgsNode = provider.createConvertArgsToSulongNode();
                this.toSulongNode = convertArgsNode != null ? convertArgsNode : CExtNodes.AllToSulongNode.create();
            } else {
                this.invokeNode = CallVarargsMethodNode.create();
            }
            if (!isStatic) {
                readSelfNode = ReadIndexedArgumentNode.create(0);
            }
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            calleeContext.enter(frame);
            try {
                Object callable = ensureReadCallableNode().execute(frame);
                if (externalInvokeNode != null) {
                    Object[] cArguments = prepareCArguments(frame);
                    toSulongNode.executeInto(cArguments, 0, cArguments, 0);
                    try {
                        return externalInvokeNode.execute(frame, name, callable, cArguments);
                    } finally {
                        postprocessCArguments(frame, cArguments);
                    }
                } else {
                    assert externalInvokeNode == null;
                    return invokeNode.execute(frame, callable, preparePArguments(frame), PArguments.getKeywordArguments(frame));
                }
            } finally {
                calleeContext.exit(frame, this);
            }
        }

        /**
         * Prepare the arguments for calling the C function. The arguments will then be converted to
         * LLVM arguments using the {@link #toSulongNode}. This will modify the returned array.
         */
        protected abstract Object[] prepareCArguments(VirtualFrame frame);

        @SuppressWarnings("unused")
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            // default: do nothing
        }

        protected Object[] preparePArguments(VirtualFrame frame) {
            Object[] variableArguments = PArguments.getVariableArguments(frame);

            int variableArgumentsLength = variableArguments != null ? variableArguments.length : 0;
            // we need to subtract 1 due to the hidden default param that carries the callable
            int userArgumentLength = PArguments.getUserArgumentLength(frame) - 1;
            int argumentsLength = userArgumentLength + variableArgumentsLength;
            Object[] arguments = new Object[argumentsLength];

            // first, copy positional arguments
            PythonUtils.arraycopy(frame.getArguments(), PArguments.USER_ARGUMENTS_OFFSET, arguments, 0, userArgumentLength);

            // now, copy variable arguments
            if (variableArguments != null) {
                PythonUtils.arraycopy(variableArguments, 0, arguments, userArgumentLength, variableArgumentsLength);
            }
            return arguments;
        }

        private ReadIndexedArgumentNode ensureReadCallableNode() {
            if (readCallableNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // we insert a hidden argument at the end of the positional arguments
                int hiddenArg = getSignature().getParameterIds().length;
                readCallableNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
            }
            return readCallableNode;
        }

        protected final ReleaseNativeWrapperNode ensureReleaseNativeWrapperNode() {
            if (releaseNativeWrapperNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                releaseNativeWrapperNode = insert(ReleaseNativeWrapperNodeGen.create());
            }
            return releaseNativeWrapperNode;
        }

        protected final PRaiseNode getRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public NodeCost getCost() {
            // this is just a thin argument shuffling wrapper
            return NodeCost.NONE;
        }

        @Override
        public String toString() {
            return "<METH root " + name + ">";
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }

        protected final Object readSelf(VirtualFrame frame) {
            if (readSelfNode != null) {
                return readSelfNode.execute(frame);
            }
            return PNone.NO_VALUE;
        }
    }

    public static final class MethKeywordsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private PythonObjectFactory factory;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;
        @Child private CreateArgsTupleNode createArgsTupleNode;

        public MethKeywordsRoot(PythonLanguage language, String name, boolean isStatic) {
            super(language, name, isStatic);
        }

        public MethKeywordsRoot(PythonLanguage language, String name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
            this.factory = PythonObjectFactory.create();
            this.readVarargsNode = ReadVarArgsNode.create(true);
            this.readKwargsNode = ReadVarKeywordsNode.create(PythonUtils.EMPTY_STRING_ARRAY);
            this.createArgsTupleNode = CreateArgsTupleNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            PKeyword[] kwargs = readKwargsNode.executePKeyword(frame);
            return new Object[]{self, createArgsTupleNode.execute(factory, args), factory.createDict(kwargs)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
            releaseNativeWrapperNode.execute(cArguments[2]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethVarargsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private PythonObjectFactory factory;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private CreateArgsTupleNode createArgsTupleNode;

        public MethVarargsRoot(PythonLanguage language, String name, boolean isStatic) {
            super(language, name, isStatic);
        }

        public MethVarargsRoot(PythonLanguage language, String name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
            this.factory = PythonObjectFactory.create();
            this.readVarargsNode = ReadVarArgsNode.create(true);
            this.createArgsTupleNode = CreateArgsTupleNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            return new Object[]{self, createArgsTupleNode.execute(factory, args)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethNoargsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);

        public MethNoargsRoot(PythonLanguage language, String name, boolean isStatic) {
            super(language, name, isStatic);
        }

        public MethNoargsRoot(PythonLanguage language, String name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            return new Object[]{self, PNone.NONE};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ensureReleaseNativeWrapperNode().execute(cArguments[0]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethORoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "arg"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArgNode;

        public MethORoot(PythonLanguage language, String name, boolean isStatic) {
            super(language, name, isStatic);
        }

        public MethORoot(PythonLanguage language, String name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg = readArgNode.execute(frame);
            return new Object[]{self, arg};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethFastcallWithKeywordsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private PythonObjectFactory factory;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;
        @Child private PythonNativeWrapperLibrary wrapperLib;

        public MethFastcallWithKeywordsRoot(PythonLanguage language, String name, boolean isStatic) {
            super(language, name, isStatic);
        }

        public MethFastcallWithKeywordsRoot(PythonLanguage language, String name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
            this.factory = PythonObjectFactory.create();
            this.readVarargsNode = ReadVarArgsNode.create(true);
            this.readKwargsNode = ReadVarKeywordsNode.create(PythonUtils.EMPTY_STRING_ARRAY);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            PKeyword[] kwargs = readKwargsNode.executePKeyword(frame);
            Object[] fastcallArgs = new Object[args.length + kwargs.length];
            Object[] fastcallKwnames = new Object[kwargs.length];
            PythonUtils.arraycopy(args, 0, fastcallArgs, 0, args.length);
            for (int i = 0; i < kwargs.length; i++) {
                fastcallKwnames[i] = kwargs[i].getName();
                fastcallArgs[args.length + i] = kwargs[i].getValue();
            }
            return new Object[]{self, new CPyObjectArrayWrapper(fastcallArgs), args.length, factory.createTuple(fastcallKwnames)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            CPyObjectArrayWrapper wrapper = (CPyObjectArrayWrapper) cArguments[1];
            wrapper.free(ensureWrapperLib(wrapper), ensureReleaseNativeWrapperNode());
            releaseNativeWrapperNode.execute(cArguments[3]);
        }

        private PythonNativeWrapperLibrary ensureWrapperLib(CPyObjectArrayWrapper wrapper) {
            if (wrapperLib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                wrapperLib = insert(PythonNativeWrapperLibrary.getFactory().create(wrapper));
            }
            return wrapperLib;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethFastcallRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private PythonNativeWrapperLibrary wrapperLib;

        public MethFastcallRoot(PythonLanguage language, String name, boolean isStatic) {
            super(language, name, isStatic);
        }

        public MethFastcallRoot(PythonLanguage language, String name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
            this.readVarargsNode = ReadVarArgsNode.create(true);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            return new Object[]{self, new CPyObjectArrayWrapper(args), args.length};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            CPyObjectArrayWrapper wrapper = (CPyObjectArrayWrapper) cArguments[1];
            wrapper.free(ensureWrapperLib(wrapper), ensureReleaseNativeWrapperNode());
        }

        private PythonNativeWrapperLibrary ensureWrapperLib(CPyObjectArrayWrapper wrapper) {
            if (wrapperLib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                wrapperLib = insert(PythonNativeWrapperLibrary.getFactory().create(wrapper));
            }
            return wrapperLib;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for C function type {@code allocfunc} and {@code ssizeargfunc}.
     */
    static class AllocFuncRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "nitems"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArgNode;
        @Child private ConvertPIntToPrimitiveNode asSsizeTNode;

        AllocFuncRootNode(PythonLanguage language, String name) {
            super(language, name, false);
        }

        AllocFuncRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.asSsizeTNode = ConvertPIntToPrimitiveNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg = readArgNode.execute(frame);
            try {
                return new Object[]{self, asSsizeTNode.executeLong(arg, 1, Long.BYTES)};
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ensureReleaseNativeWrapperNode().execute(cArguments[0]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for a get attribute function (C type {@code getattrfunc}).
     */
    static final class GetAttrFuncRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "key"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArgNode;
        @Child private CExtNodes.AsCharPointerNode asCharPointerNode;
        @Child private PCallCapiFunction callFreeNode;

        GetAttrFuncRootNode(PythonLanguage language, String name) {
            super(language, name, false);
        }

        GetAttrFuncRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.asCharPointerNode = CExtNodes.AsCharPointerNode.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg = readArgNode.execute(frame);
            // TODO we should use 'CStringWrapper' for 'arg' but it does currently not support
            // PString
            return new Object[]{self, asCharPointerNode.execute(arg)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ensureReleaseNativeWrapperNode().execute(cArguments[0]);
            ensureCallFreeNode().call(NativeCAPISymbol.FUN_PY_TRUFFLE_FREE, cArguments[1]);
        }

        private PCallCapiFunction ensureCallFreeNode() {
            if (callFreeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callFreeNode = insert(PCallCapiFunction.create());
            }
            return callFreeNode;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for a set attribute function (C type {@code setattrfunc}).
     */
    static final class SetAttrFuncRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "key", "value"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private CExtNodes.AsCharPointerNode asCharPointerNode;
        @Child private PCallCapiFunction callFreeNode;

        SetAttrFuncRootNode(PythonLanguage language, String name) {
            super(language, name, false);
        }

        SetAttrFuncRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.asCharPointerNode = CExtNodes.AsCharPointerNode.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg1 = readArg1Node.execute(frame);
            Object arg2 = readArg2Node.execute(frame);
            // TODO we should use 'CStringWrapper' for 'arg1' but it does currently not support
            // PString
            return new Object[]{self, asCharPointerNode.execute(arg1), arg2};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            ensureCallFreeNode().call(NativeCAPISymbol.FUN_PY_TRUFFLE_FREE, cArguments[1]);
            releaseNativeWrapperNode.execute(cArguments[2]);
        }

        private PCallCapiFunction ensureCallFreeNode() {
            if (callFreeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callFreeNode = insert(PCallCapiFunction.create());
            }
            return callFreeNode;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for a rich compare function (C type {@code richcmpfunc}).
     */
    static final class RichCmpFuncRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "other", "op"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private ConvertPIntToPrimitiveNode asSsizeTNode;

        RichCmpFuncRootNode(PythonLanguage language, String name) {
            super(language, name, false);
        }

        RichCmpFuncRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.asSsizeTNode = ConvertPIntToPrimitiveNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            try {
                Object self = readSelf(frame);
                Object arg1 = readArg1Node.execute(frame);
                Object arg2 = readArg2Node.execute(frame);
                return new Object[]{self, arg1, asSsizeTNode.executeInt(arg2, 1, Integer.BYTES)};
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c: wrap_sq_item}.
     */
    static final class GetItemRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "i"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private GetIndexNode getIndexNode;

        GetItemRootNode(PythonLanguage language, String name) {
            super(language, name, false);
        }

        GetItemRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.getIndexNode = GetIndexNode.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg1 = readArg1Node.execute(frame);
            return new Object[]{self, getIndexNode.execute(self, arg1)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ensureReleaseNativeWrapperNode().execute(cArguments[0]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c: wrap_sq_setitem}.
     */
    static final class SetItemRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "i", "value"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private GetIndexNode getIndexNode;

        SetItemRootNode(PythonLanguage language, String name) {
            super(language, name, false);
        }

        SetItemRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.getIndexNode = GetIndexNode.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg1 = readArg1Node.execute(frame);
            Object arg2 = readArg2Node.execute(frame);
            return new Object[]{self, getIndexNode.execute(self, arg1), arg2};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[2]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c:wrap_descr_get}
     */
    public static final class DescrGetRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "obj", "type"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readObj;
        @Child private ReadIndexedArgumentNode readType;

        public DescrGetRootNode(PythonLanguage language, String name) {
            super(language, name, false);
        }

        public DescrGetRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readObj = ReadIndexedArgumentNode.create(1);
            this.readType = ReadIndexedArgumentNode.create(2);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object obj = readObj.execute(frame);
            Object type = readType.execute(frame);
            return new Object[]{self, obj == PNone.NONE ? PNone.NO_VALUE : obj, type == PNone.NONE ? PNone.NO_VALUE : type};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
            releaseNativeWrapperNode.execute(cArguments[2]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implement mapping of {@code __delitem__} to {@code mp_ass_subscript}. It handles adding the
     * NULL 3rd argument.
     */
    static final class MpDelItemRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "i"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg1Node;

        MpDelItemRootNode(PythonLanguage language, String name) {
            super(language, name, false);
        }

        MpDelItemRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg1 = readArg1Node.execute(frame);
            return new Object[]{self, arg1, PNone.NO_VALUE};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
            releaseNativeWrapperNode.execute(cArguments[2]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for reverse binary operations.
     */
    static final class MethReverseRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "obj"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg0Node;
        @Child private ReadIndexedArgumentNode readArg1Node;

        MethReverseRootNode(PythonLanguage language, String name) {
            super(language, name, false);
            this.readArg0Node = ReadIndexedArgumentNode.create(0);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
        }

        MethReverseRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg0Node = ReadIndexedArgumentNode.create(0);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object arg0 = readArg0Node.execute(frame);
            Object arg1 = readArg1Node.execute(frame);
            return new Object[]{arg1, arg0};
        }

        @Override
        protected Object[] preparePArguments(VirtualFrame frame) {
            Object arg0 = readArg0Node.execute(frame);
            Object arg1 = readArg1Node.execute(frame);
            return new Object[]{arg1, arg0};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for native power function (with an optional third argument).
     */
    static class MethPowRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(false, 0, false, new String[]{"args"}, KEYWORDS_HIDDEN_CALLABLE);

        @Child private ReadVarArgsNode readVarargsNode;

        private final ConditionProfile profile;

        MethPowRootNode(PythonLanguage language, String name) {
            super(language, name, false);
            this.profile = null;
        }

        MethPowRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readVarargsNode = ReadVarArgsNode.create(true);
            this.profile = ConditionProfile.createBinaryProfile();
        }

        @Override
        protected final Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object[] varargs = readVarargsNode.executeObjectArray(frame);
            Object arg0 = varargs[0];
            Object arg1 = profile.profile(varargs.length > 1) ? varargs[1] : PNone.NONE;
            return getArguments(self, arg0, arg1);
        }

        Object[] getArguments(Object arg0, Object arg1, Object arg2) {
            return new Object[]{arg0, arg1, arg2};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
            releaseNativeWrapperNode.execute(cArguments[2]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for native reverse power function (with an optional third argument).
     */
    static final class MethRPowRootNode extends MethPowRootNode {

        MethRPowRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        MethRPowRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @Override
        Object[] getArguments(Object arg0, Object arg1, Object arg2) {
            return new Object[]{arg1, arg0, arg2};
        }
    }

    /**
     * Wrapper root node for native power function (with an optional third argument).
     */
    static final class MethRichcmpOpRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "other"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArgNode;

        private final int op;

        MethRichcmpOpRootNode(PythonLanguage language, String name, int op) {
            super(language, name, false);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.op = op;
        }

        MethRichcmpOpRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider, int op) {
            super(language, name, false, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.op = op;
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg = readArgNode.execute(frame);
            return new Object[]{self, arg, op};
        }

        @Override
        protected Object[] preparePArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg = readArgNode.execute(frame);
            return new Object[]{self, arg, SpecialMethodNames.getCompareOpString(op)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for C function type {@code iternextfunc}.
     */
    static class IterNextFuncRootNode extends MethodDescriptorRoot {

        IterNextFuncRootNode(PythonLanguage language, String name) {
            super(language, name, false);
        }

        IterNextFuncRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{readSelf(frame)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ensureReleaseNativeWrapperNode().execute(cArguments[0]);
        }

        @Override
        public Signature getSignature() {
            // same signature as a method without arguments (just the self)
            return MethNoargsRoot.SIGNATURE;
        }
    }

    abstract static class GetSetRootNode extends MethodDescriptorRoot {

        @Child private ReadIndexedArgumentNode readClosureNode;

        GetSetRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
        }

        protected final Object readClosure(VirtualFrame frame) {
            if (readClosureNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // we insert a hidden argument after the hidden callable arg
                int hiddenArg = getSignature().getParameterIds().length + 1;
                readClosureNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
            }
            return readClosureNode.execute(frame);
        }

    }

    /**
     * Wrapper root node for C function type {@code getter}.
     */
    public static class GetterRoot extends GetSetRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE, true);

        public GetterRoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            return new Object[]{self, readClosure(frame)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ensureReleaseNativeWrapperNode().execute(cArguments[0]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for C function type {@code setter}.
     */
    public static class SetterRoot extends GetSetRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "value"}, KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE, true);

        @Child private ReadIndexedArgumentNode readArgNode;

        public SetterRoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg = ensureReadArgNode().execute(frame);
            return new Object[]{self, arg, readClosure(frame)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        private ReadIndexedArgumentNode ensureReadArgNode() {
            if (readArgNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArgNode = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArgNode;
        }

        @TruffleBoundary
        public static PBuiltinFunction createFunction(PythonLanguage lang, Object owner, String propertyName, Object target, Object closure) {
            RootCallTarget rootCallTarget = PExternalFunctionWrapper.getOrCreateCallTarget(PExternalFunctionWrapper.SETTER, lang, propertyName, true, false);
            if (rootCallTarget == null) {
                throw CompilerDirectives.shouldNotReachHere("Calling non-native get descriptor functions is not support");
            }
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            return factory.createGetSetBuiltinFunction(propertyName, owner, PythonUtils.EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(target, closure), rootCallTarget);
        }
    }

    /**
     * We need to inflate all primitives in order to avoid memory leaks. Explanation: Primitives
     * would currently be wrapped into a PrimitiveNativeWrapper. If any of those will receive a
     * toNative message, the managed code will be the only owner of those wrappers. But we will
     * never be able to reach the wrapper from the arguments if they are just primitive. So, we
     * inflate the primitives and we can then traverse the tuple and reach the wrappers of its
     * arguments after the call returned.
     */
    abstract static class CreateArgsTupleNode extends Node {
        public abstract PTuple execute(PythonObjectFactory factory, Object[] args);

        @Specialization(guards = {"args.length == cachedLen", "cachedLen <= 16"})
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
        static PTuple doCachedLen(PythonObjectFactory factory, Object[] args,
                        @Cached("args.length") int cachedLen,
                        @Cached("createToBorrowedRefNodes(args.length)") ToBorrowedRefNode[] toBorrowedRefNodes,
                        @Cached("createMaterializeNodes(args.length)") MaterializePrimitiveNode[] materializePrimitiveNodes) {

            for (int i = 0; i < cachedLen; i++) {
                args[i] = prepareReference(args[i], factory, materializePrimitiveNodes[i], toBorrowedRefNodes[i]);
            }
            return factory.createTuple(args);
        }

        @Specialization(replaces = "doCachedLen")
        static PTuple doGeneric(PythonObjectFactory factory, Object[] args,
                        @Cached ToBorrowedRefNode toNewRefNode,
                        @Cached MaterializePrimitiveNode materializePrimitiveNode) {

            for (int i = 0; i < args.length; i++) {
                args[i] = prepareReference(args[i], factory, materializePrimitiveNode, toNewRefNode);
            }
            return factory.createTuple(args);
        }

        private static Object prepareReference(Object arg, PythonObjectFactory factory, MaterializePrimitiveNode materializePrimitiveNode, ToBorrowedRefNode toNewRefNode) {
            Object result = materializePrimitiveNode.execute(factory, arg);

            // Tuples are actually stealing the reference of their items. That's why we need to
            // increase the reference count by 1 at this point. However, it could be that the
            // object does not have a native wrapper yet. We use ToNewRefNode to ensure that the
            // object has a native wrapper or to increase the reference count by 1 if a native
            // wrapper already exists.
            toNewRefNode.execute(result);
            return result;
        }

        static ToBorrowedRefNode[] createToBorrowedRefNodes(int length) {
            ToBorrowedRefNode[] newRefNodes = new ToBorrowedRefNode[length];
            for (int i = 0; i < length; i++) {
                newRefNodes[i] = ToBorrowedRefNodeGen.create();
            }
            return newRefNodes;
        }

        static MaterializePrimitiveNode[] createMaterializeNodes(int length) {
            MaterializePrimitiveNode[] materializePrimitiveNodes = new MaterializePrimitiveNode[length];
            for (int i = 0; i < length; i++) {
                materializePrimitiveNodes[i] = MaterializePrimitiveNodeGen.create();
            }
            return materializePrimitiveNodes;
        }
    }

    /**
     * Special helper nodes that materializes any primitive that would leak the wrapper if the
     * reference is owned by managed code only.
     */
    @TypeSystemReference(PythonTypes.class)
    abstract static class MaterializePrimitiveNode extends Node {

        public abstract Object execute(PythonObjectFactory factory, Object object);

        // NOTE: Booleans don't need to be materialized because they are singletons.

        @Specialization
        static PInt doInteger(PythonObjectFactory factory, int i) {
            return factory.createInt(i);
        }

        @Specialization(replaces = "doInteger")
        static PInt doLong(PythonObjectFactory factory, long l) {
            return factory.createInt(l);
        }

        @Specialization
        static PFloat doDouble(PythonObjectFactory factory, double d) {
            return factory.createFloat(d);
        }

        @Specialization
        static PString doString(PythonObjectFactory factory, String s) {
            return factory.createString(s);
        }

        @Specialization(guards = "!needsMaterialization(object)")
        static Object doObject(@SuppressWarnings("unused") PythonObjectFactory factory, Object object) {
            return object;
        }

        static boolean needsMaterialization(Object object) {
            return object instanceof Integer || object instanceof Long || PGuards.isDouble(object) || object instanceof String;
        }
    }

    // roughly equivalent to _Py_CheckFunctionResult in Objects/call.c
    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class DefaultCheckFunctionResultNode extends CheckFunctionResultNode {

        @Specialization(limit = "1")
        static Object doNativeWrapper(PythonContext context, String name, DynamicObjectNativeWrapper.PythonObjectNativeWrapper result,
                        @CachedLibrary(value = "result") PythonNativeWrapperLibrary lib,
                        @Cached DefaultCheckFunctionResultNode recursive) {
            return recursive.execute(context, name, lib.getDelegate(result));
        }

        @Specialization(guards = "!isPythonObjectNativeWrapper(result)")
        Object doPrimitiveWrapper(PythonContext context, String name, @SuppressWarnings("unused") PythonNativeWrapper result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile) {
            checkFunctionResult(this, name, false, true, context, errOccurredProfile);
            return result;
        }

        @Specialization(guards = "isNoValue(result)")
        Object doNoValue(PythonContext context, String name, @SuppressWarnings("unused") PNone result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile) {
            checkFunctionResult(this, name, true, true, context, errOccurredProfile);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "!isNoValue(result)")
        Object doPythonObject(PythonContext context, String name, @SuppressWarnings("unused") PythonAbstractObject result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile) {
            checkFunctionResult(this, name, false, true, context, errOccurredProfile);
            return result;
        }

        @Specialization
        Object doPythonNativeNull(PythonContext context, String name, @SuppressWarnings("unused") PythonNativeNull result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile) {
            checkFunctionResult(this, name, true, true, context, errOccurredProfile);
            return result;
        }

        @Specialization
        int doInteger(PythonContext context, String name, int result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile) {
            // If the native functions returns a primitive int, only a value '-1' indicates an
            // error.
            checkFunctionResult(this, name, result == -1, false, context, errOccurredProfile);
            return result;
        }

        @Specialization
        long doLong(PythonContext context, String name, long result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile) {
            // If the native functions returns a primitive int, only a value '-1' indicates an
            // error.
            checkFunctionResult(this, name, result == -1, false, context, errOccurredProfile);
            return result;
        }

        /*
         * Our fallback case, but with some cached params. PythonObjectNativeWrapper results should
         * be unwrapped and recursively delegated (see #doNativeWrapper) and PNone is treated
         * specially, because we consider it as null in #doNoValue and as not null in
         * #doPythonObject
         */
        @Specialization(guards = {"!isPythonObjectNativeWrapper(result)", "!isPNone(result)"})
        Object doForeign(PythonContext context, String name, Object result,
                        @Exclusive @Cached ConditionProfile isNullProfile,
                        @Exclusive @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile) {
            checkFunctionResult(this, name, isNullProfile.profile(lib.isNull(result)), true, context, errOccurredProfile);
            return result;
        }

        private static void checkFunctionResult(Node node, String name, boolean indicatesError, boolean strict, PythonContext context, ConditionProfile errOccurredProfile) {
            PythonLanguage language = PythonLanguage.get(node);
            checkFunctionResult(node, name, indicatesError, strict, language, context, errOccurredProfile, RETURNED_NULL_WO_SETTING_ERROR, RETURNED_RESULT_WITH_ERROR_SET);
        }

        /**
         * Check the result of a C extension function.
         *
         * @param node The processing node (needed for the source location if a {@code SystemError}
         *            is raised).
         * @param name The name of the function (used for the error message).
         * @param indicatesError {@code true} if the function results indicates an error (e.g.
         *            {@code NULL} if the return type is a pointer or {@code -1} if the return type
         *            is an int).
         * @param strict If {@code true}, a {@code SystemError} will be raised if the result value
         *            indicates an error but no exception was set. Setting this to {@code false}
         *            mostly makes sense for primitive return values with semantics
         *            {@code if (res != -1 && PyErr_Occurred()}.
         * @param language The Python language.
         * @param context The Python context.
         * @param errOccurredProfile Profiles if a Python exception occurred and is set in the
         *            context.
         * @param nullButNoErrorMessage Error message used if the value indicates an error and is
         *            not primitive but no error was set.
         * @param resultWithErrorMessage Error message used if an error was set but the value does
         *            not indicate and error.
         */
        static void checkFunctionResult(Node node, String name, boolean indicatesError, boolean strict, PythonLanguage language, PythonContext context, ConditionProfile errOccurredProfile,
                        String nullButNoErrorMessage, String resultWithErrorMessage) {
            PythonThreadState threadState = context.getThreadState(language);
            PException currentException = threadState.getCurrentException();
            boolean errOccurred = errOccurredProfile.profile(currentException != null);
            if (indicatesError) {
                // consume exception
                threadState.setCurrentException(null);
                if (errOccurred) {
                    throw currentException.getExceptionForReraise();
                } else if (strict) {
                    throw raiseNullButNoError(node, name, nullButNoErrorMessage);
                }
            } else if (errOccurred) {
                // consume exception
                threadState.setCurrentException(null);
                throw raiseResultWithError(language, node, name, currentException, resultWithErrorMessage);
            }
        }

        @TruffleBoundary
        static PException raiseNullButNoError(Node node, String name, String nullButNoErrorMessage) {
            throw PRaiseNode.raiseUncached(node, PythonErrorType.SystemError, nullButNoErrorMessage, name);
        }

        @TruffleBoundary
        static PException raiseResultWithError(PythonLanguage language, Node node, String name, PException currentException, String resultWithErrorMessage) {
            PBaseException sysExc = PythonObjectFactory.getUncached().createBaseException(PythonErrorType.SystemError, resultWithErrorMessage, new Object[]{name});
            sysExc.setCause(currentException.getEscapedException());
            throw PRaiseNode.raise(node, sysExc, PythonOptions.isPExceptionWithJavaStacktrace(language));
        }

        protected static boolean isNativeNull(Object object) {
            return object instanceof PythonNativeNull;
        }

        protected static boolean isPythonObjectNativeWrapper(Object object) {
            return object instanceof DynamicObjectNativeWrapper.PythonObjectNativeWrapper;
        }
    }

    /**
     * Equivalent of the result processing part in {@code Objects/typeobject.c: wrap_next}.
     */
    abstract static class CheckIterNextResultNode extends CheckFunctionResultNode {

        @Specialization(limit = "3")
        static Object doGeneric(PythonContext context, @SuppressWarnings("unused") String name, Object result,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @CachedLibrary("result") InteropLibrary lib,
                        @Cached PRaiseNode raiseNode) {
            if (lib.isNull(result)) {
                PException currentException = getThreadStateNode.getCurrentException(context);
                // if no exception occurred, the iterator is exhausted -> raise StopIteration
                if (currentException == null) {
                    throw raiseNode.raiseStopIteration();
                } else {
                    // consume exception
                    getThreadStateNode.setCurrentException(context, null);
                    // re-raise exception
                    throw currentException.getExceptionForReraise();
                }
            }
            return result;
        }
    }

    /**
     * Processes the function result with CPython semantics:
     *
     * <pre>
     *     if (func(self, args, kwds) < 0)
     *         return NULL;
     *     Py_RETURN_NONE;
     * </pre>
     *
     * This is the case for {@code wrap_init}, {@code wrap_descr_delete}, {@code wrap_descr_set},
     * {@code wrap_delattr}, {@code wrap_setattr}.
     */
    @ImportStatic(PGuards.class)
    public abstract static class InitCheckFunctionResultNode extends CheckFunctionResultNode {

        @Specialization(guards = "result >= 0")
        Object doNoError(PythonContext context, String name, @SuppressWarnings("unused") int result,
                        @Shared("p") @Cached ConditionProfile errOccurredProfile) {
            // This is the most likely case
            DefaultCheckFunctionResultNode.checkFunctionResult(this, name, false, true, context, errOccurredProfile);
            return PNone.NONE;
        }

        @Specialization(guards = "result < 0")
        @SuppressWarnings("unused")
        Object doError(PythonContext context, String name, int result,
                        @Shared("p") @Cached ConditionProfile errOccurredProfile) {
            DefaultCheckFunctionResultNode.checkFunctionResult(this, name, true, true, context, errOccurredProfile);
            throw CompilerDirectives.shouldNotReachHere();
        }

        // Slow path
        @Specialization(replaces = {"doNoError", "doError"})
        Object notNumber(PythonContext context, @SuppressWarnings("unused") String name, Object result,
                        @Shared("p") @Cached ConditionProfile errOccurredProfile,
                        @CachedLibrary(limit = "2") InteropLibrary lib) {
            int ret = 0;
            if (lib.isNumber(result)) {
                try {
                    ret = lib.asInt(result);
                    if (ret >= 0) {
                        return PNone.NONE;
                    }
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            DefaultCheckFunctionResultNode.checkFunctionResult(this, name, ret < 0, true, context, errOccurredProfile);
            return result;
        }
    }

    /**
     * Processes the function result with CPython semantics:
     *
     * <pre>
     *     Py_ssize_t res = func(...);
     *     if (res == -1 && PyErr_Occurred())
     *         return NULL;
     * </pre>
     *
     * This is the case for {@code wrap_delitem}, {@code wrap_objobjargproc},
     * {@code wrap_sq_delitem}, {@code wrap_sq_setitem}, {@code asdf}.
     */
    @ImportStatic(PGuards.class)
    public abstract static class CheckPrimitiveFunctionResultNode extends CheckFunctionResultNode {

        @Specialization(guards = "!isMinusOne(result)")
        long doLongNoError(PythonContext context, String name, long result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile) {
            DefaultCheckFunctionResultNode.checkFunctionResult(this, name, false, false, context, errOccurredProfile);
            return result;
        }

        @Specialization(guards = "isMinusOne(result)")
        long doLongIndicatesError(PythonContext context, String name, long result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile) {
            DefaultCheckFunctionResultNode.checkFunctionResult(this, name, true, false, context, errOccurredProfile);
            return result;
        }

        @Specialization(replaces = {"doLongNoError", "doLongIndicatesError"})
        long doLong(PythonContext context, String name, long result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile) {
            DefaultCheckFunctionResultNode.checkFunctionResult(this, name, result == -1, false, context, errOccurredProfile);
            return result;
        }

        @Specialization(replaces = {"doLongNoError", "doLongIndicatesError", "doLong"})
        long doGeneric(PythonContext context, String name, Object result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile,
                        @CachedLibrary(limit = "2") InteropLibrary lib) {
            if (lib.fitsInLong(result)) {
                try {
                    long ret = lib.asLong(result);
                    DefaultCheckFunctionResultNode.checkFunctionResult(this, name, ret == -1, false, context, errOccurredProfile);
                    return ret;
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            throw CompilerDirectives.shouldNotReachHere("expected primitive function result but does not fit into Java long");
        }
    }

    /**
     * Tests if the primitive result of the called function is {@code -1} and if an error occurred.
     * In this case, the error is re-raised. Otherwise, it converts the result to a Boolean. This is
     * equivalent to the result processing part in {@code Object/typeobject.c: wrap_inquirypred} and
     * {@code Object/typeobject.c: wrap_objobjproc}.
     */
    abstract static class CheckInquiryResultNode extends CheckFunctionResultNode {

        @Specialization(guards = "result > 0")
        boolean doLongTrue(PythonContext context, String name, @SuppressWarnings("unused") long result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile) {
            // the guard implies: result != -1
            DefaultCheckFunctionResultNode.checkFunctionResult(this, name, false, false, context, errOccurredProfile);
            return true;
        }

        @Specialization(guards = "result == 0")
        boolean doLongFalse(PythonContext context, String name, @SuppressWarnings("unused") long result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile) {
            // the guard implies: result != -1
            DefaultCheckFunctionResultNode.checkFunctionResult(this, name, false, false, context, errOccurredProfile);
            return false;
        }

        @Specialization(guards = "!isMinusOne(result)", replaces = {"doLongTrue", "doLongFalse"})
        boolean doLongNoError(PythonContext context, String name, long result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile) {
            DefaultCheckFunctionResultNode.checkFunctionResult(this, name, false, false, context, errOccurredProfile);
            return result != 0;
        }

        @Specialization(replaces = {"doLongTrue", "doLongFalse", "doLongNoError"})
        boolean doLong(PythonContext context, String name, long result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile) {
            DefaultCheckFunctionResultNode.checkFunctionResult(this, name, result == -1, false, context, errOccurredProfile);
            return result != 0;
        }

        @Specialization(replaces = {"doLongTrue", "doLongFalse", "doLongNoError", "doLong"})
        boolean doGeneric(PythonContext context, String name, Object result,
                        @Shared("errOccurredProfile") @Cached ConditionProfile errOccurredProfile,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            if (lib.fitsInLong(result)) {
                try {
                    return doLong(context, name, lib.asLong(result), errOccurredProfile);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseUncached(this, SystemError, "function '%s' did not return an integer", name);
        }
    }
}
