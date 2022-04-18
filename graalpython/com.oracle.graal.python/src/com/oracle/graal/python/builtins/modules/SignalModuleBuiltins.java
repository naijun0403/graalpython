/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.HiddenKey;

import com.oracle.graal.python.util.Signal;
import com.oracle.graal.python.util.SignalHandler;

@CoreFunctions(defineModule = "_signal")
public class SignalModuleBuiltins extends PythonBuiltins {
    private static final ConcurrentHashMap<Integer, Object> signalHandlers = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, SignalHandler> defaultSignalHandlers = new ConcurrentHashMap<>();

    private static final HiddenKey signalQueueKey = new HiddenKey("signalQueue");
    private final ConcurrentLinkedDeque<SignalTriggerAction> signalQueue = new ConcurrentLinkedDeque<>();
    private static final HiddenKey signalSemaKey = new HiddenKey("signalQueue");
    private final Semaphore signalSema = new Semaphore(0);

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SignalModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        builtinConstants.put("SIG_DFL", Signals.SIG_DFL);
        builtinConstants.put("SIG_IGN", Signals.SIG_IGN);
        for (int i = 0; i < Signals.signalNames.length; i++) {
            String name = Signals.signalNames[i];
            if (name != null) {
                builtinConstants.put("SIG" + name, i);
            }
        }
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);

        PythonModule signalModule = core.lookupBuiltinModule("_signal");
        signalModule.setAttribute(signalQueueKey, signalQueue);
        signalModule.setAttribute(signalSemaKey, signalSema);

        core.getContext().registerAsyncAction(() -> {
            SignalTriggerAction poll = signalQueue.poll();
            try {
                while (poll == null) {
                    signalSema.acquire();
                    poll = signalQueue.poll();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return poll;
        });
    }

    private static class SignalTriggerAction extends AsyncHandler.AsyncPythonAction {
        private final Object callableObject;
        private final int signum;

        SignalTriggerAction(Object callable, int signum) {
            this.callableObject = callable;
            this.signum = signum;
        }

        @Override
        public Object callable() {
            return callableObject;
        }

        @Override
        public Object[] arguments() {
            return new Object[]{signum, null};
        }

        @Override
        public int frameIndex() {
            return 1;
        }
    }

    @Builtin(name = "alarm", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class AlarmNode extends PythonUnaryBuiltinNode {
        @Specialization
        int alarm(long seconds) {
            Signals.scheduleAlarm(seconds);
            return 0;
        }

        @Specialization(rewriteOn = OverflowException.class)
        int alarm(PInt seconds) throws OverflowException {
            Signals.scheduleAlarm(seconds.longValueExact());
            return 0;
        }

        @Specialization
        int alarmOvf(PInt seconds) {
            try {
                Signals.scheduleAlarm(seconds.longValueExact());
                return 0;
            } catch (OverflowException e) {
                throw raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C long");
            }
        }
    }

    @TruffleBoundary
    private static Object handlerToPython(SignalHandler handler, int signum) {
        if (handler == SignalHandler.SIG_DFL) {
            return Signals.SIG_DFL;
        } else if (handler == SignalHandler.SIG_IGN) {
            return Signals.SIG_IGN;
        } else if (handler instanceof Signals.PythonSignalHandler) {
            return signalHandlers.getOrDefault(signum, PNone.NONE);
        } else {
            // Save default JVM handlers to be restored later
            defaultSignalHandlers.put(signum, handler);
            return Signals.SIG_DFL;
        }
    }

    @Builtin(name = "getsignal", minNumOfPositionalArgs = 1, parameterNames = {"signalnum"})
    @ArgumentClinic(name = "signalnum", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class GetSignalNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object getsignal(int signum) {
            return handlerToPython(Signals.getCurrentSignalHandler(signum), signum);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SignalModuleBuiltinsClinicProviders.GetSignalNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "default_int_handler", minNumOfPositionalArgs = 0, takesVarArgs = true, takesVarKeywordArgs = false)
    @GenerateNodeFactory
    abstract static class DefaultIntHandlerNode extends PythonBuiltinNode {
        @Specialization
        Object defaultIntHandler(@SuppressWarnings("unused") Object[] args) {
            // TODO should be implemented properly.
            throw raise(PythonErrorType.KeyboardInterrupt);
        }
    }

    @Builtin(name = "signal", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class SignalNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "!callableCheck.execute(idNum)", limit = "1")
        Object signalId(VirtualFrame frame, @SuppressWarnings("unused") PythonModule self, Object signal, Object idNum,
                        @SuppressWarnings("unused") @Shared("callableCheck") @Cached PyCallableCheckNode callableCheck,
                        @Shared("asSize") @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached CastToJavaIntExactNode cast) {
            // Note: CPython checks if id is the same reference as SIG_IGN/SIG_DFL constants, which
            // are instances of Handlers enum
            // The -1 fallback will be correctly reported as an error later on
            int id;
            try {
                id = cast.execute(idNum);
            } catch (CannotCastException | PException e) {
                id = -1;
            }
            return signal(asSizeNode.executeExact(frame, signal), id);
        }

        @TruffleBoundary
        private Object signal(int signum, int id) {
            SignalHandler oldHandler;
            try {
                if (id == Signals.SIG_DFL && defaultSignalHandlers.containsKey(signum)) {
                    oldHandler = Signals.setSignalHandler(signum, defaultSignalHandlers.get(signum));
                } else {
                    oldHandler = Signals.setSignalHandler(signum, id);
                }
            } catch (IllegalArgumentException e) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.SIGNAL_MUST_BE_SIGIGN_SIGDFL_OR_CALLABLE_OBJ);
            }
            Object result = handlerToPython(oldHandler, signum);
            signalHandlers.remove(signum);
            return result;
        }

        @Specialization(guards = "callableCheck.execute(handler)", limit = "1")
        Object signalHandler(VirtualFrame frame, PythonModule self, Object signal, Object handler,
                        @SuppressWarnings("unused") @Shared("callableCheck") @Cached PyCallableCheckNode callableCheck,
                        @Shared("asSize") @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached ReadAttributeFromObjectNode readQueueNode,
                        @Cached ReadAttributeFromObjectNode readSemaNode) {
            return signal(self, asSizeNode.executeExact(frame, signal), handler, readQueueNode, readSemaNode);
        }

        @TruffleBoundary
        private Object signal(PythonModule self, int signum, Object handler, ReadAttributeFromObjectNode readQueueNode, ReadAttributeFromObjectNode readSemaNode) {
            ConcurrentLinkedDeque<SignalTriggerAction> queue = getQueue(self, readQueueNode);
            Semaphore semaphore = getSemaphore(self, readSemaNode);
            SignalHandler oldHandler;
            SignalTriggerAction signalTrigger = new SignalTriggerAction(handler, signum);
            try {
                oldHandler = Signals.setSignalHandler(signum, () -> {
                    queue.add(signalTrigger);
                    semaphore.release();
                });
            } catch (IllegalArgumentException e) {
                throw raise(PythonErrorType.ValueError, e);
            }
            Object result = handlerToPython(oldHandler, signum);
            signalHandlers.put(signum, handler);
            return result;
        }

        @SuppressWarnings("unchecked")
        private static ConcurrentLinkedDeque<SignalTriggerAction> getQueue(PythonModule self, ReadAttributeFromObjectNode readNode) {
            Object queueObject = readNode.execute(self, signalQueueKey);
            if (queueObject instanceof ConcurrentLinkedDeque) {
                ConcurrentLinkedDeque<SignalTriggerAction> queue = (ConcurrentLinkedDeque<SignalTriggerAction>) queueObject;
                return queue;
            } else {
                throw new IllegalStateException("the signal trigger queue was modified!");
            }
        }

        private static Semaphore getSemaphore(PythonModule self, ReadAttributeFromObjectNode readNode) {
            Object semaphore = readNode.execute(self, signalSemaKey);
            if (semaphore instanceof Semaphore) {
                return (Semaphore) semaphore;
            } else {
                throw new IllegalStateException("the signal trigger semaphore was modified!");
            }
        }
    }

    @Builtin(name = "set_wakeup_fd", minNumOfPositionalArgs = 1, parameterNames = {"", "warn_on_full_buffer"})
    @GenerateNodeFactory
    abstract static class SetWakeupFdNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static int doGeneric(Object fd, Object warnOnFullBuffer) {
            // TODO: implement
            return -1;
        }
    }

    @Builtin(name = "raise_signal", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"signalnum"})
    @ArgumentClinic(name = "signalnum", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class RaiseSignalNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PNone doInt(int signum) {
            Signal.raise(new Signal(Signals.signalNumberToName(signum)));
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SignalModuleBuiltinsClinicProviders.RaiseSignalNodeClinicProviderGen.INSTANCE;
        }
    }
}

// Checkstyle: stop
/*
 * (tfel): This class is supposed to go away to be replaced with a yet to be designed Truffle API
 * for signals
 */
final class Signals {
    static final int SIG_DFL = 0;
    static final int SIG_IGN = 1;
    private static final int SIGMAX = 31;
    static final String[] signalNames = new String[SIGMAX + 1];

    static {
        signalNames[22] = "ABRT";
        signalNames[8] = "FPE";
        signalNames[4] = "ILL";
        signalNames[2] = "INT";
        signalNames[11] = "SEGV";
        signalNames[15] = "TERM";
    }

    private static class Alarm implements Runnable {
        private final long seconds;

        Alarm(long seconds) {
            this.seconds = seconds;
        }

        @Override
        public void run() {
            long t0 = System.currentTimeMillis();
            while ((System.currentTimeMillis() - t0) < seconds * 1000) {
                try {
                    Thread.sleep(seconds * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @TruffleBoundary
    synchronized static void scheduleAlarm(long seconds) {
        new Thread(new Alarm(seconds)).start();
    }

    static class PythonSignalHandler implements SignalHandler {
        private final Runnable handler;

        public PythonSignalHandler(Runnable handler) {
            this.handler = handler;
        }

        @Override
        public void handle(Signal arg0) {
            handler.run();
        }
    }

    static String signalNumberToName(int signum) {
        return signum > SIGMAX ? "INVALID SIGNAL" : signalNames[signum];
    }

    @TruffleBoundary
    synchronized static SignalHandler setSignalHandler(int signum, Runnable handler) throws IllegalArgumentException {
        return setSignalHandler(signum, new PythonSignalHandler(handler));
    }

    @TruffleBoundary
    synchronized static SignalHandler setSignalHandler(int signum, SignalHandler handler) throws IllegalArgumentException {
        return SignalHandler.SIG_DFL;
    }

    @TruffleBoundary
    synchronized static SignalHandler setSignalHandler(int signum, int handler) throws IllegalArgumentException {
        return SignalHandler.SIG_DFL;
    }

    @TruffleBoundary
    synchronized static SignalHandler getCurrentSignalHandler(int signum) {
        return SignalHandler.SIG_DFL;
    }
}
