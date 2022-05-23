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
package com.oracle.graal.python.builtins.objects.generator;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.GeneratorExit;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.traceback.GetTracebackNode;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.CallTargetInvokeNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallVarargsNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.generator.AbstractYieldNode;
import com.oracle.graal.python.nodes.generator.YieldFromNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PGenerator)
public class GeneratorBuiltins extends PythonBuiltins {

    /**
     * Creates a fresh copy of the generator arguments to be used for the next invocation of the
     * generator. This is necessary to avoid persisting caller state. For example: If the generator
     * is invoked using {@code next(g)} outside of any {@code except} handler but the generator
     * requests the exception state, then the exception state will be written into the arguments. If
     * we now use the same arguments array every time, the next invocation would think that there is
     * not excepion but in fact, the a subsequent call ot {@code next} may have a different
     * exception state.
     *
     * <pre>
     *     g = my_generator()
     *
     *     # invoke without any exception context
     *     next(g)
     *
     *     try:
     *         raise ValueError
     *     except ValueError:
     *         # invoke with exception context
     *         next(g)
     * </pre>
     *
     * This is necessary for correct chaining of exceptions.
     */
    private static Object[] prepareArguments(PGenerator self) {
        Object[] generatorArguments = self.getArguments();
        Object[] arguments = new Object[generatorArguments.length];
        PythonUtils.arraycopy(generatorArguments, 0, arguments, 0, arguments.length);
        return arguments;
    }

    private static void checkResumable(PythonBuiltinBaseNode node, PGenerator self) {
        if (self.isFinished()) {
            throw node.raiseStopIteration();
        }
        if (self.isRunning()) {
            throw node.raise(ValueError, ErrorMessages.GENERATOR_ALREADY_EXECUTING);
        }
    }

    @ImportStatic({PGuards.class, PythonOptions.class})
    abstract static class ResumeGeneratorNode extends Node {
        public abstract Object execute(VirtualFrame frame, PGenerator self, Object sendValue);

        @Specialization(guards = "sameCallTarget(self.getCurrentCallTarget(), call.getCallTarget())", limit = "getCallSiteInlineCacheMaxDepth()")
        static Object cached(VirtualFrame frame, PGenerator self, Object sendValue,
                        @Cached("createDirectCall(self.getCurrentCallTarget())") CallTargetInvokeNode call,
                        @Cached PRaiseNode raiseNode) {
            self.setRunning(true);
            Object[] arguments = prepareArguments(self);
            if (sendValue != null) {
                PArguments.setSpecialArgument(arguments, sendValue);
            }
            Object result;
            try {
                result = call.execute(frame, null, null, null, arguments);
            } catch (PException e) {
                self.markAsFinished();
                throw e;
            } finally {
                self.setRunning(false);
                self.setNextCallTarget();
            }
            if (result == null) {
                throw raiseNode.raise(StopIteration, self.getReturnValue());
            }
            return result;
        }

        @Specialization(replaces = "cached")
        @Megamorphic
        static Object generic(VirtualFrame frame, PGenerator self, Object sendValue,
                        @Cached ConditionProfile hasFrameProfile,
                        @Cached GenericInvokeNode call,
                        @Cached PRaiseNode raiseNode) {
            self.setRunning(true);
            Object[] arguments = prepareArguments(self);
            if (sendValue != null) {
                PArguments.setSpecialArgument(arguments, sendValue);
            }
            Object result;
            try {
                if (hasFrameProfile.profile(frame != null)) {
                    result = call.execute(frame, self.getCurrentCallTarget(), arguments);
                } else {
                    result = call.execute(self.getCurrentCallTarget(), arguments);
                }
            } catch (PException e) {
                self.markAsFinished();
                throw e;
            } finally {
                self.setRunning(false);
                self.setNextCallTarget();
            }
            if (result == null) {
                throw raiseNode.raise(StopIteration, self.getReturnValue());
            }
            return result;
        }

        protected static CallTargetInvokeNode createDirectCall(CallTarget target) {
            return CallTargetInvokeNode.create(target, false, true);
        }

        protected static boolean sameCallTarget(RootCallTarget target1, CallTarget target2) {
            return target1 == target2;
        }
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GeneratorBuiltinsFactory.getFactories();
    }

    @Builtin(name = __NAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(noValue)")
        static Object getName(PGenerator self, @SuppressWarnings("unused") PNone noValue) {
            return self.getName();
        }

        @Specialization
        static Object setName(PGenerator self, String value) {
            self.setName(value);
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setName(PGenerator self, Object value,
                        @Cached StringNodes.CastToJavaStringCheckedNode cast) {
            return setName(self, cast.cast(value, ErrorMessages.MUST_BE_SET_TO_S_OBJ, __NAME__, "string"));
        }
    }

    @Builtin(name = __QUALNAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class QualnameNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(noValue)")
        static Object getQualname(PGenerator self, @SuppressWarnings("unused") PNone noValue) {
            return self.getQualname();
        }

        @Specialization
        static Object setQualname(PGenerator self, String value) {
            self.setQualname(value);
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setQualname(PGenerator self, Object value,
                        @Cached StringNodes.CastToJavaStringCheckedNode cast) {
            return setQualname(self, cast.cast(value, ErrorMessages.MUST_BE_SET_TO_S_OBJ, __QUALNAME__, "string"));
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object iter(PGenerator self) {
            return self;
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1, doc = "Implement next(self).")
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object next(VirtualFrame frame, PGenerator self,
                        @Cached ResumeGeneratorNode resumeGeneratorNode) {
            checkResumable(this, self);
            return resumeGeneratorNode.execute(frame, self, null);
        }
    }

    @Builtin(name = "send", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SendNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object send(VirtualFrame frame, PGenerator self, Object value,
                        @Cached ResumeGeneratorNode resumeGeneratorNode) {
            checkResumable(this, self);
            if (!self.isStarted() && value != PNone.NONE) {
                throw raise(TypeError, ErrorMessages.SEND_NON_NONE_TO_UNSTARTED_GENERATOR);
            }
            return resumeGeneratorNode.execute(frame, self, value);
        }
    }

    // throw(typ[,val[,tb]])
    @Builtin(name = "throw", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class ThrowNode extends PythonBuiltinNode {

        @Child private MaterializeFrameNode materializeFrameNode;
        @Child private GetTracebackNode getTracebackNode;

        @ImportStatic({PGuards.class, SpecialMethodNames.class})
        abstract static class PrepareExceptionNode extends Node {
            public abstract PBaseException execute(VirtualFrame frame, Object type, Object value);

            private PRaiseNode raiseNode;
            private IsSubtypeNode isSubtypeNode;

            @Specialization
            static PBaseException doException(PBaseException exc, @SuppressWarnings("unused") PNone value) {
                return exc;
            }

            @Specialization(guards = "!isPNone(value)")
            PBaseException doException(@SuppressWarnings("unused") PBaseException exc, @SuppressWarnings("unused") Object value) {
                throw raise().raise(PythonBuiltinClassType.TypeError, ErrorMessages.INSTANCE_EX_MAY_NOT_HAVE_SEP_VALUE);
            }

            @Specialization(guards = "isTypeNode.execute(type)", limit = "1")
            PBaseException doException(VirtualFrame frame, Object type, PBaseException value,
                            @SuppressWarnings("unused") @Shared("isType") @Cached TypeNodes.IsTypeNode isTypeNode,
                            @Cached BuiltinFunctions.IsInstanceNode isInstanceNode,
                            @Cached BranchProfile isNotInstanceProfile,
                            @Cached("create(__CALL__)") LookupAndCallVarargsNode callConstructor) {
                if (isInstanceNode.executeWith(frame, value, type)) {
                    checkExceptionClass(type);
                    return value;
                } else {
                    isNotInstanceProfile.enter();
                    return doCreateObject(frame, type, value, isTypeNode, callConstructor);
                }
            }

            @Specialization(guards = "isTypeNode.execute(type)", limit = "1")
            PBaseException doCreate(VirtualFrame frame, Object type, @SuppressWarnings("unused") PNone value,
                            @SuppressWarnings("unused") @Shared("isType") @Cached TypeNodes.IsTypeNode isTypeNode,
                            @Cached("create(__CALL__)") LookupAndCallVarargsNode callConstructor) {
                checkExceptionClass(type);
                Object instance = callConstructor.execute(frame, type, new Object[]{type});
                if (instance instanceof PBaseException) {
                    return (PBaseException) instance;
                } else {
                    throw raise().raise(TypeError, ErrorMessages.EXCEPTIONS_MUST_DERIVE_FROM_BASE_EX);
                }
            }

            @Specialization(guards = "isTypeNode.execute(type)", limit = "1")
            PBaseException doCreateTuple(VirtualFrame frame, Object type, PTuple value,
                            @SuppressWarnings("unused") @Shared("isType") @Cached TypeNodes.IsTypeNode isTypeNode,
                            @Cached GetObjectArrayNode getObjectArrayNode,
                            @Cached("create(__CALL__)") LookupAndCallVarargsNode callConstructor) {
                checkExceptionClass(type);
                Object[] array = getObjectArrayNode.execute(value);
                Object[] args = new Object[array.length + 1];
                args[0] = type;
                PythonUtils.arraycopy(array, 0, args, 1, array.length);
                Object instance = callConstructor.execute(frame, type, args);
                if (instance instanceof PBaseException) {
                    return (PBaseException) instance;
                } else {
                    throw raise().raise(TypeError, ErrorMessages.EXCEPTIONS_MUST_DERIVE_FROM_BASE_EX);
                }
            }

            @Specialization(guards = {"isTypeNode.execute(type)", "!isPNone(value)", "!isPTuple(value)", "!isPBaseException(value)"}, limit = "1")
            PBaseException doCreateObject(VirtualFrame frame, Object type, Object value,
                            @SuppressWarnings("unused") @Shared("isType") @Cached TypeNodes.IsTypeNode isTypeNode,
                            @Cached("create(__CALL__)") LookupAndCallVarargsNode callConstructor) {
                checkExceptionClass(type);
                Object instance = callConstructor.execute(frame, type, new Object[]{type, value});
                if (instance instanceof PBaseException) {
                    return (PBaseException) instance;
                } else {
                    throw raise().raise(TypeError, ErrorMessages.EXCEPTIONS_MUST_DERIVE_FROM_BASE_EX);
                }
            }

            @Fallback
            PBaseException doError(Object type, @SuppressWarnings("unused") Object value) {
                throw raise().raise(TypeError, ErrorMessages.EXCEPTIONS_MUST_BE_CLASSES_OR_INSTANCES_DERIVING_FROM_BASE_EX, type);
            }

            private PRaiseNode raise() {
                if (raiseNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    raiseNode = insert(PRaiseNode.create());
                }
                return raiseNode;
            }

            private void checkExceptionClass(Object type) {
                if (isSubtypeNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    isSubtypeNode = insert(IsSubtypeNode.create());
                }
                if (!isSubtypeNode.execute(type, PythonBuiltinClassType.PBaseException)) {
                    throw raise().raise(TypeError, ErrorMessages.EXCEPTIONS_MUST_BE_CLASSES_OR_INSTANCES_DERIVING_FROM_BASE_EX, type);
                }
            }
        }

        @Specialization
        Object sendThrow(VirtualFrame frame, PGenerator self, Object typ, Object val, @SuppressWarnings("unused") PNone tb,
                        @Cached PrepareExceptionNode prepareExceptionNode,
                        @Cached ResumeGeneratorNode resumeGeneratorNode) {
            if (self.isRunning()) {
                throw raise(ValueError, ErrorMessages.GENERATOR_ALREADY_EXECUTING);
            }
            PBaseException instance = prepareExceptionNode.execute(frame, typ, val);
            return doThrow(frame, resumeGeneratorNode, self, instance, getLanguage());
        }

        @Specialization
        Object sendThrow(VirtualFrame frame, PGenerator self, Object typ, Object val, PTraceback tb,
                        @Cached PrepareExceptionNode prepareExceptionNode,
                        @Cached ResumeGeneratorNode resumeGeneratorNode) {
            if (self.isRunning()) {
                throw raise(ValueError, ErrorMessages.GENERATOR_ALREADY_EXECUTING);
            }
            PBaseException instance = prepareExceptionNode.execute(frame, typ, val);
            instance.setTraceback(tb);
            return doThrow(frame, resumeGeneratorNode, self, instance, getLanguage());
        }

        private Object doThrow(VirtualFrame frame, ResumeGeneratorNode resumeGeneratorNode, PGenerator self, PBaseException instance, PythonLanguage language) {
            instance.setContext(null); // Will be filled when caught
            if (self.isStarted() && !self.isFinished()) {
                instance.ensureReified();
                // Pass it to the generator where it will be thrown by the last yield, the location
                // will be filled there
                return resumeGeneratorNode.execute(frame, self, new ThrowData(instance, PythonOptions.isPExceptionWithJavaStacktrace(language)));
            } else {
                // Unstarted generator, we cannot pass the exception into the generator as there is
                // nothing that would handle it.
                // Instead, we throw the exception here and fake entering the generator by adding
                // its frame to the traceback manually.
                Node location = self.getCurrentCallTarget().getRootNode();
                MaterializedFrame generatorFrame = PArguments.getGeneratorFrame(self.getArguments());
                PFrame pFrame = ensureMaterializeFrameNode().execute(null, location, false, false, generatorFrame);
                PTraceback existingTraceback = null;
                if (instance.getTraceback() != null) {
                    existingTraceback = ensureGetTracebackNode().execute(instance.getTraceback());
                }
                PTraceback newTraceback = factory().createTraceback(pFrame, pFrame.getLine(), existingTraceback);
                instance.setTraceback(newTraceback);
                throw PException.fromObject(instance, location, PythonOptions.isPExceptionWithJavaStacktrace(language));
            }
        }

        @Specialization(guards = {"!isPNone(tb)", "!isPTraceback(tb)"})
        @SuppressWarnings("unused")
        Object doError(VirtualFrame frame, PGenerator self, Object typ, Object val, Object tb) {
            throw raise(TypeError, ErrorMessages.THROW_THIRD_ARG_MUST_BE_TRACEBACK);
        }

        private MaterializeFrameNode ensureMaterializeFrameNode() {
            if (materializeFrameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                materializeFrameNode = insert(MaterializeFrameNode.create());
            }
            return materializeFrameNode;
        }

        private GetTracebackNode ensureGetTracebackNode() {
            if (getTracebackNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getTracebackNode = insert(GetTracebackNode.create());
            }
            return getTracebackNode;
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object close(VirtualFrame frame, PGenerator self,
                        @Cached IsBuiltinClassProfile isGeneratorExit,
                        @Cached IsBuiltinClassProfile isStopIteration,
                        @Cached ResumeGeneratorNode resumeGeneratorNode,
                        @Cached ConditionProfile isStartedPorfile) {
            if (self.isRunning()) {
                throw raise(ValueError, ErrorMessages.GENERATOR_ALREADY_EXECUTING);
            }
            if (isStartedPorfile.profile(self.isStarted() && !self.isFinished())) {
                PBaseException pythonException = factory().createBaseException(GeneratorExit);
                // Pass it to the generator where it will be thrown by the last yield, the location
                // will be filled there
                boolean withJavaStacktrace = PythonOptions.isPExceptionWithJavaStacktrace(getLanguage());
                try {
                    resumeGeneratorNode.execute(frame, self, new ThrowData(pythonException, withJavaStacktrace));
                } catch (PException pe) {
                    if (isGeneratorExit.profileException(pe, GeneratorExit) || isStopIteration.profileException(pe, StopIteration)) {
                        // This is the "success" path
                        return PNone.NONE;
                    }
                    throw pe;
                } finally {
                    self.markAsFinished();
                }
                throw raise(RuntimeError, ErrorMessages.GENERATOR_IGNORED_EXIT);
            } else {
                self.markAsFinished();
                return PNone.NONE;
            }
        }
    }

    @Builtin(name = "gi_code", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getCode(PGenerator self,
                        @Cached ConditionProfile hasCodeProfile) {
            PCode code = self.getCode();
            if (hasCodeProfile.profile(code == null)) {
                code = factory().createCode(self.getCurrentCallTarget());
                self.setCode(code);
            }
            return code;
        }
    }

    @Builtin(name = "gi_running", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class GetRunningNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object getRunning(PGenerator self, @SuppressWarnings("unused") PNone none) {
            return self.isRunning();
        }

        @Specialization(guards = "!isNoValue(obj)")
        static Object setRunning(@SuppressWarnings("unused") PGenerator self, @SuppressWarnings("unused") Object obj,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(AttributeError, ErrorMessages.READONLY_ATTRIBUTE);
        }
    }

    @Builtin(name = "gi_frame", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFrameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getFrame(PGenerator self,
                        @Cached PythonObjectFactory factory) {
            if (self.isFinished()) {
                return PNone.NONE;
            } else {
                MaterializedFrame generatorFrame = PArguments.getGeneratorFrame(self.getArguments());
                PDict locals = PArguments.getGeneratorFrameLocals(generatorFrame);
                Object[] arguments = PArguments.create();
                Node location = self.getCurrentYieldNode();
                if (location == null) {
                    location = self.getCurrentCallTarget().getRootNode();
                }
                PFrame frame = factory.createPFrame(PFrame.Reference.EMPTY, location, locals);
                PArguments.setGlobals(arguments, PArguments.getGlobals(self.getArguments()));
                PArguments.setClosure(arguments, PArguments.getClosure(self.getArguments()));
                PArguments.setGeneratorFrame(arguments, generatorFrame);
                frame.setArguments(arguments);
                if (self.isStarted()) {
                    // Hack: Fake bytecode to make inspect.getgeneratorstate distinguish suspended
                    // and unstarted generators
                    frame.setLasti(10000);
                }
                return frame;
            }
        }
    }

    @Builtin(name = "gi_yieldfrom", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetYieldFromNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getYieldFrom(PGenerator self) {
            AbstractYieldNode currentYield = self.getCurrentYieldNode();
            if (currentYield instanceof YieldFromNode) {
                int iteratorSlot = ((YieldFromNode) currentYield).getIteratorSlot();
                return PArguments.getControlDataFromGeneratorArguments(self.getArguments()).getIteratorAt(iteratorSlot);
            } else {
                return PNone.NONE;
            }
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        String repr(PGenerator self) {
            return self.toString();
        }
    }
}
