/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.exception;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Serves both as a throwable carrier of the python exception object and as a represenation of the
 * exception state at a single point in the program. An important invariant is that it must never be
 * rethrown after the contained exception object has been exposed to the program, instead, a new
 * object must be created for each throw.
 */
@ExportLibrary(value = InteropLibrary.class, delegateTo = "pythonException")
public final class PException extends AbstractTruffleException {
    private static final long serialVersionUID = -6437116280384996361L;

    /** A marker object indicating that there is for sure no exception. */
    public static final PException NO_EXCEPTION = new PException(null, null);

    private String message = null;
    protected final PBaseException pythonException;
    private boolean hideLocation = false;
    private CallTarget tracebackCutoffTarget;
    private PFrame.Reference frameInfo;
    private Node catchLocation;
    private int catchBci;
    private LazyTraceback traceback;
    private boolean reified = false;

    private PException(PBaseException actual, Node node) {
        super(node);
        this.pythonException = actual;
    }

    private PException(PBaseException actual, Node node, Throwable wrapped) {
        super(null, wrapped, UNLIMITED_STACK_TRACE, node);
        this.pythonException = actual;
    }

    private PException(PBaseException actual, LazyTraceback traceback, Throwable wrapped) {
        super(null, wrapped, UNLIMITED_STACK_TRACE, null);
        this.pythonException = actual;
        this.traceback = traceback;
        reified = true;
    }

    public static PException fromObject(PBaseException actual, Node node, boolean withJavaStacktrace) {
        Throwable wrapped = null;
        if (withJavaStacktrace) {
            // Create a carrier for the java stacktrace as PException cannot have one
            wrapped = createStacktraceCarrier();
        }
        return fromObject(actual, node, wrapped);
    }

    @TruffleBoundary
    private static RuntimeException createStacktraceCarrier() {
        return new RuntimeException();
    }

    public static PException fromObject(PBaseException actual, Node node, Throwable wrapped) {
        PException pException = new PException(actual, node, wrapped);
        actual.setException(pException);
        return pException;
    }

    public static PException fromExceptionInfo(PBaseException pythonException, PTraceback traceback, boolean withJavaStacktrace) {
        LazyTraceback lazyTraceback = null;
        if (traceback != null) {
            lazyTraceback = new LazyTraceback(traceback);
        }
        return fromExceptionInfo(pythonException, lazyTraceback, withJavaStacktrace);
    }

    public static PException fromExceptionInfo(PBaseException pythonException, LazyTraceback traceback, boolean withJavaStacktrace) {
        pythonException.ensureReified();
        Throwable wrapped = null;
        if (withJavaStacktrace) {
            // Create a carrier for the java stacktrace as PException cannot have one
            wrapped = createStacktraceCarrier();
        }
        PException pException = new PException(pythonException, traceback, wrapped);
        pythonException.setException(pException);
        return pException;
    }

    @Override
    public String getMessage() {
        if (message == null) {
            message = pythonException.toString();
        }
        return message;
    }

    public void setMessage(Object object) {
        message = object.toString();
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (this == PException.NO_EXCEPTION) {
            return "NO_EXCEPTION";
        }
        return getMessage();
    }

    public boolean shouldHideLocation() {
        return hideLocation;
    }

    public void setHideLocation(boolean hideLocation) {
        this.hideLocation = hideLocation;
    }

    public Node getCatchLocation() {
        return catchLocation;
    }

    public int getCatchBci() {
        return catchBci;
    }

    /**
     * Return the associated {@link PBaseException}. This method doesn't ensure traceback
     * consistency and should be avoided unless you can guarantee that the exception will not escape
     * to the program. Use
     * {@link PException#setCatchingFrameAndGetEscapedException(VirtualFrame, Node)
     * reifyAndGetPythonException}.
     */
    public PBaseException getUnreifiedException() {
        return pythonException;
    }

    public void expectIndexError(IsBuiltinClassProfile profile) {
        if (!profile.profileException(this, PythonBuiltinClassType.IndexError)) {
            throw this;
        }
    }

    public void expectStopIteration(IsBuiltinClassProfile profile) {
        if (!profile.profileException(this, PythonBuiltinClassType.StopIteration)) {
            throw this;
        }
    }

    public void expectAttributeError(IsBuiltinClassProfile profile) {
        if (!profile.profileException(this, PythonBuiltinClassType.AttributeError)) {
            throw this;
        }
    }

    public boolean expectTypeOrOverflowError(IsBuiltinClassProfile profile) {
        boolean ofError = !profile.profileException(this, PythonBuiltinClassType.TypeError);
        if (ofError && !profile.profileException(this, PythonBuiltinClassType.OverflowError)) {
            throw this;
        }
        return ofError;
    }

    public void expectOverflowError(IsBuiltinClassProfile profile) {
        if (!profile.profileException(this, PythonBuiltinClassType.OverflowError)) {
            throw this;
        }
    }

    public void expectTypeError(IsBuiltinClassProfile profile) {
        if (!profile.profileException(this, PythonBuiltinClassType.TypeError)) {
            throw this;
        }
    }

    public void expect(PythonBuiltinClassType error, IsBuiltinClassProfile profile) {
        if (!profile.profileException(this, error)) {
            throw this;
        }
    }

    @TruffleBoundary
    public Iterable<TruffleStackTraceElement> getTruffleStackTrace() {
        if (tracebackCutoffTarget == null) {
            tracebackCutoffTarget = Truffle.getRuntime().iterateFrames(FrameInstance::getCallTarget, 0);
        }
        // Cause may contain wrapped Java exception
        if (getCause() != null) {
            return TruffleStackTrace.getStackTrace(getCause());
        } else {
            return TruffleStackTrace.getStackTrace(this);
        }
    }

    public boolean shouldCutOffTraceback(TruffleStackTraceElement element) {
        return tracebackCutoffTarget != null && element.getTarget() == tracebackCutoffTarget;
    }

    public void setCatchingFrameReference(PFrame.Reference frameInfo, Node catchLocation) {
        this.frameInfo = frameInfo;
        this.catchLocation = catchLocation;
    }

    /**
     * Save the exception handler's frame for the traceback. Should be called by all
     * exception-handling structures that need their current frame to be visible in the traceback,
     * i.e except, finally and __exit__. The frame is not yet marked as escaped.
     *
     * @param frame The current frame of the exception handler.
     */
    public void setCatchingFrameReference(Frame frame, Node catchLocation) {
        setCatchingFrameReference(PArguments.getCurrentFrameInfo(frame), catchLocation);
    }

    public void setCatchingFrameReference(Frame frame, PBytecodeRootNode catchLocation, int catchBci) {
        setCatchingFrameReference(PArguments.getCurrentFrameInfo(frame), catchLocation);
        this.catchBci = catchBci;
    }

    /**
     * Shortcut for {@link #setCatchingFrameReference(PFrame.Reference, Node)} and @{link
     * {@link #getEscapedException()}}
     */
    public PBaseException setCatchingFrameAndGetEscapedException(Frame frame, Node catchLocation) {
        setCatchingFrameReference(frame, catchLocation);
        return this.getEscapedException();
    }

    public void markFrameEscaped() {
        if (this.frameInfo != null) {
            this.frameInfo.markAsEscaped();
        }
    }

    /**
     * Get the python exception while ensuring that the traceback frame is marked as escaped
     */
    public PBaseException getEscapedException() {
        markFrameEscaped();
        return pythonException;
    }

    /**
     * Get traceback from the time the exception was caught (reified). The contained python object's
     * traceback may not be the same as it is mutable and thus may change after being caught.
     */
    public LazyTraceback getTraceback() {
        ensureReified();
        return traceback;
    }

    /**
     * Set traceback for the exception state. This has no effect on the contained python exception
     * object at this point but it may be synced to the object at a later point if the exception
     * state gets reraised (for example with `raise` without arguments as opposed to the exception
     * object itself being explicitly reraised with `raise e`).
     */
    public void setTraceback(LazyTraceback traceback) {
        ensureReified();
        this.traceback = traceback;
    }

    /**
     * If not done already, create the traceback for this exception state using the frame previously
     * provided to {@link #setCatchingFrameReference(PFrame.Reference, Node)} and sync it to the
     * attached python exception
     */
    public void ensureReified() {
        if (!reified) {
            // Frame may be null when the catch handler is the C boundary, which is internal and
            // shouldn't leak to the traceback
            if (frameInfo != null) {
                assert frameInfo != PFrame.Reference.EMPTY;
                frameInfo.markAsEscaped();
            }
            // Make a snapshot of the traceback at the point of the exception handler. This may be
            // called later than in the exception handler, but only in cases when the exception
            // hasn't escaped to the prgram and thus couldn't have changed in the meantime
            traceback = pythonException.internalReifyException(frameInfo);
            reified = true;
        }
    }

    /**
     * Prepare a new exception to be thrown to provide the semantics of a reraise. The difference
     * between this method and creating a new exception using
     * {@link #fromObject(PBaseException, Node, boolean) fromObject} is that this method makes the
     * traceback look like the last catch didn't happen, which is desired in `raise` without
     * arguments, at the end of `finally`, `__exit__`...
     */
    public PException getExceptionForReraise() {
        return pythonException.getExceptionForReraise(getTraceback());
    }

    @TruffleBoundary
    public void printStack() {
        // a convenience methods for debugging
        ExceptionUtils.printPythonLikeStackTrace(this);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isException() {
        return true;
    }

    @ExportMessage
    ExceptionType getExceptionType(
                    @CachedLibrary(limit = "1") InteropLibrary lib) throws UnsupportedMessageException {
        return lib.getExceptionType(pythonException);
    }

    @ExportMessage
    RuntimeException throwException(@Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            throw getExceptionForReraise();
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasSourceLocation() {
        return getLocation() != null && getLocation().getEncapsulatingSourceSection() != null;
    }

    @ExportMessage(name = "getSourceLocation")
    SourceSection getExceptionSourceLocation(
                    @Cached BranchProfile unsupportedProfile) throws UnsupportedMessageException {
        if (hasSourceLocation()) {
            return getLocation().getEncapsulatingSourceSection();
        }
        unsupportedProfile.enter();
        throw UnsupportedMessageException.create();
    }

    // Note: remaining interop messages are forwarded to the contained PBaseException
}
