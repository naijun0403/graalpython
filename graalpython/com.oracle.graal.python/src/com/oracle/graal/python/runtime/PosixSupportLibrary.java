/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime;

import java.nio.ByteBuffer;

import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;

/**
 * Internal abstraction layer for POSIX functionality. Instance of the implementation is stored in
 * the context. Use {@link PythonContext#getPosixSupport()} to access it.
 */
@GenerateLibrary(receiverType = PosixSupport.class)
public abstract class PosixSupportLibrary extends Library {

    public static final int DEFAULT_DIR_FD = -100;  // TODO C code assumes that this constant is
                                                    // equal to AT_FDCWD

    public static final int O_CLOEXEC = 524288;

    public static final char POSIX_FILENAME_SEPARATOR = '/';

    public static final int S_IFMT = 0170000;
    public static final int S_IFDIR = 0040000;
    public static final int S_IFREG = 0100000;
    public static final int S_IFLNK = 0120000;

    public static final int DT_UNKNOWN = 0;
    public static final int DT_DIR = 4;
    public static final int DT_REG = 8;
    public static final int DT_LNK = 10;

    public abstract String getBackend(Object recevier);

    public abstract String strerror(Object receiver, int errorCode);

    public abstract long getpid(Object receiver);

    public abstract int umask(Object receiver, int mask) throws PosixException;

    public abstract int openAt(Object receiver, int dirFd, PosixPath pathname, int flags, int mode) throws PosixException;

    public abstract void close(Object receiver, int fd) throws PosixException;

    public abstract Buffer read(Object receiver, int fd, long length) throws PosixException;

    public abstract long write(Object receiver, int fd, Buffer data) throws PosixException;

    public abstract int dup(Object receiver, int fd) throws PosixException;

    public abstract int dup2(Object receiver, int fd, int fd2, boolean inheritable) throws PosixException;

    public abstract boolean getInheritable(Object receiver, int fd) throws PosixException;

    public abstract void setInheritable(Object receiver, int fd, boolean inheritable) throws PosixException;

    public abstract int[] pipe(Object receiver) throws PosixException;

    public abstract long lseek(Object receiver, int fd, long offset, int how) throws PosixException;

    public abstract void ftruncate(Object receiver, int fd, long length) throws PosixException;

    public abstract void fsync(Object receiver, int fd) throws PosixException;

    public abstract boolean getBlocking(Object receiver, int fd) throws PosixException;

    public abstract void setBlocking(Object receiver, int fd, boolean blocking) throws PosixException;

    public abstract int[] getTerminalSize(Object receiver, int fd) throws PosixException;

    // see stat_struct_to_longs in posix.c for the layout of the array
    public abstract long[] fstatAt(Object receiver, int dirFd, PosixPath pathname, boolean followSymlinks) throws PosixException;

    /**
     * Performs operation of fstat(fd).
     *
     * @param receiver the receiver of the message
     * @param fd the file descriptor
     * @param filename filename for the error message, can be null
     * @param handleEintr if {@code true}, EINTR causes the call to be repeated
     * @return see {@code stat_struct_to_longs} in posix.c for the layout of the array
     * @throws PosixException if an error occurs (can be EINTR if {@code handleEintr} is
     *             {@code false}
     */
    public abstract long[] fstat(Object receiver, int fd, Object filename, boolean handleEintr) throws PosixException;

    public abstract Object[] uname(Object receiver) throws PosixException;

    public abstract void unlinkAt(Object receiver, int dirFd, PosixPath pathname, boolean rmdir) throws PosixException;

    public abstract void symlinkAt(Object receiver, PosixPath target, int linkpathDirFd, PosixPath linkpath) throws PosixException;

    public abstract void mkdirAt(Object receiver, int dirFd, PosixPath pathname, int mode) throws PosixException;

    public abstract Object getcwd(Object receiver) throws PosixException;

    public abstract void chdir(Object receiver, PosixPath path) throws PosixException;

    /**
     * Performs operation of fchdir(fd).
     *
     * @param receiver the receiver of the message
     * @param fd the file descriptor
     * @param pathname pathname for the error message, can be null
     * @param handleEintr if {@code true}, EINTR causes the call to be repeated
     * @throws PosixException if an error occurs (can be EINTR if {@code handleEintr} is
     *             {@code false}
     */
    public abstract void fchdir(Object receiver, int fd, Object pathname, boolean handleEintr) throws PosixException;

    public abstract boolean isatty(Object receiver, int fd);

    /**
     * @return an opaque directory stream object to be used in calls to {@code readdir} and
     *         {@code closedir}
     */
    public abstract Object opendir(Object receiver, PosixPath path) throws PosixException;

    public abstract Object fdopendir(Object receiver, PosixFd fd) throws PosixException;

    public abstract void closedir(Object receiver, Object dirStream);

    /**
     * @return an opaque dir entry object to be used in calls to {@code dirEntry*()} methods
     */
    public abstract Object readdir(Object receiver, Object dirStream) throws PosixException;

    /**
     * @return an opaque object representing the dir entry name
     * @see #getPathAsBytes(Object, Object, PythonObjectFactory)
     * @see #getPathAsString(Object, Object)
     */
    public abstract Object dirEntryGetName(Object receiver, Object dirEntry) throws PosixException;

    /**
     * Returns the dir entry path, which is the name of the dir entry joined with the path passed to
     * {@link #opendir(Object, PosixPath)}. If {@link #fdopendir(Object, PosixFd)} was used, dir
     * entry path is the same as its name.
     * 
     * @return an opaque object representing the dir entry path
     * @see #getPathAsBytes(Object, Object, PythonObjectFactory)
     * @see #getPathAsString(Object, Object)
     */
    public abstract Object dirEntryGetPath(Object receiver, Object dirEntry) throws PosixException;

    public abstract long dirEntryGetInode(Object receiver, Object dirEntry) throws PosixException;

    /**
     * The result should be cached in the {@code dirEntry} object (separate cache for each value of
     * {@code followSymlinks}).
     * 
     * @return stat of the entry in the same format as
     *         {@link #fstatAt(Object, int, PosixPath, boolean)}
     */
    public abstract long[] dirEntryStat(Object receiver, Object dirEntry, boolean followSymlinks) throws PosixException, PosixExceptionWithOpaquePath;

    /**
     * @return one of the {@code DT_xxx} constants
     */
    public abstract int dirEntryGetType(Object receiver, Object dirEntry);

    /**
     * Converts a {@code String} into the internal representation of paths used by the library
     * implementation. The implementation should return {@code null} if the path after any necessary
     * conversion contains embedded null characters.
     *
     * @param receiver the receiver of the message
     * @param path the path as a {@code String}
     * @return an opaque object representing the path or {@code null} if the path contains null
     *         characters
     */
    public abstract Object createPathFromString(Object receiver, String path);

    /**
     * Converts a {@code byte} array into the internal representation of paths used by the library
     * implementation. The implementation should return {@code null} if the path after any necessary
     * conversion contains embedded null characters.
     *
     * @param receiver the receiver of the message
     * @param path the path as a a {@code byte[]} array
     * @return an opaque object representing the path or {@code null} if the path contains null
     *         characters
     */
    public abstract Object createPathFromBytes(Object receiver, byte[] path);

    public abstract String getPathAsString(Object receiver, Object path);

    public abstract PBytes getPathAsBytes(Object receiver, Object path, PythonObjectFactory factory);

    public static abstract class PosixExceptionBase extends Exception {

        private static final long serialVersionUID = -115762483478883093L;

        private final int errorCode;
        private final Object filename1;
        private final Object filename2;

        public PosixExceptionBase(int errorCode, String message, Object filename1, Object filename2) {
            super(message);
            this.errorCode = errorCode;
            this.filename1 = filename1;
            this.filename2 = filename2;
        }

        public int getErrorCode() {
            return errorCode;
        }

        public Object getFilename1() {
            return filename1;
        }

        public Object getFilename2() {
            return filename2;
        }

        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return this;
        }
    }

    /**
     * {@code PosixException} contains an error code and message with up to two (optional) filenames
     * or paths. These are Python objects typically passed in the corresponding call to a posix
     * builtin.
     *
     * @see PosixExceptionWithOpaquePath for an alternative handling of filenames
     */
    public static class PosixException extends PosixExceptionBase {

        private static final long serialVersionUID = -3518878356923547758L;

        public PosixException(int errorCode, String message) {
            this(errorCode, message, null, null);
        }

        public PosixException(int errorCode, String message, Object filename) {
            this(errorCode, message, filename, null);
        }

        public PosixException(int errorCode, String message, Object filename1, Object filename2) {
            super(errorCode, message, filename1, filename2);
        }
    }

    /**
     * Unlike {@code PosixException}, the optional filenames/paths in this exception are opaque
     * objects generated by the posix support layer and thus need to be converted using
     * {@code #getPathAsBytes} or {@code #getPathAsString} by the caller.
     */
    public static class PosixExceptionWithOpaquePath extends PosixExceptionBase {

        private static final long serialVersionUID = 9166632909457821627L;

        public PosixExceptionWithOpaquePath(int errorCode, String message, Object filename1, Object filename2) {
            super(errorCode, message, filename1, filename2);
        }
    }

    @ValueType
    public static class Buffer {
        public final byte[] data;
        public long length;

        public Buffer(byte[] data, long length) {
            assert data != null && length >= 0 && length <= data.length;
            this.data = data;
            this.length = length;
        }

        public static Buffer allocate(long capacity) {
            if (capacity > Integer.MAX_VALUE) {
                throw CompilerDirectives.shouldNotReachHere("Long arrays are not supported yet");
            }
            return new Buffer(new byte[(int) capacity], 0);
        }

        public static Buffer wrap(byte[] data) {
            return new Buffer(data, data.length);
        }

        public Buffer withLength(long newLength) {
            if (newLength > data.length) {
                throw CompilerDirectives.shouldNotReachHere("Actual length cannot be greater than capacity");
            }
            length = newLength;
            return this;
        }

        @TruffleBoundary
        public ByteBuffer getByteBuffer() {
            return ByteBuffer.wrap(data);
        }
    }

    /**
     * Represents the result of {@code path_t} conversion. Similar to CPython's {@code path_t}
     * structure, but only contains the results of the conversion, not the conversion parameters.
     */
    public abstract static class PosixFileHandle {

        public static final PosixFileHandle DEFAULT = new PosixFileHandle() {
        };

        /**
         * Contains the original object (or the object returned by {@code __fspath__}) for auditing
         * purposes. This field is {code null} iff the path parameter was optional and the caller
         * did not provide it.
         */
        public final Object originalObject;

        private PosixFileHandle() {
            originalObject = null;
        }

        protected PosixFileHandle(Object originalObject) {
            this.originalObject = originalObject;
        }
    }

    /**
     * Contains the path converted to the representation used by the {@code PosixSupportLibrary}
     * implementation
     *
     * @see PosixSupportLibrary#createPathFromString(Object, String)
     * @see PosixSupportLibrary#createPathFromBytes(Object, byte[])
     */
    public static class PosixPath extends PosixFileHandle {
        public final Object value;
        public final boolean wasBufferLike;

        public PosixPath(Object originalObject, Object value, boolean wasBufferLike) {
            super(originalObject);
            this.value = value;
            this.wasBufferLike = wasBufferLike;
        }
    }

    /**
     * Contains the file descriptor if it was allowed in the argument conversion node and the caller
     * provided an integer instead of a path.
     */
    public static class PosixFd extends PosixFileHandle {
        public final int fd;

        public PosixFd(Object originalObject, int fd) {
            super(originalObject);
            this.fd = fd;
        }
    }
}
