/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltinsClinicProviders.DumpNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltinsClinicProviders.DumpsNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.code.CodeNodes.CreateCodeNode;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.GetObjectArrayNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetInternalObjectArrayNodeGen;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.str.StringNodesFactory.IsInternedStringNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.lib.PyComplexCheckExactNodeGen;
import com.oracle.graal.python.lib.PyDictCheckExactNodeGen;
import com.oracle.graal.python.lib.PyFloatCheckExactNodeGen;
import com.oracle.graal.python.lib.PyFrozenSetCheckExactNodeGen;
import com.oracle.graal.python.lib.PyListCheckExactNodeGen;
import com.oracle.graal.python.lib.PyLongCheckExactNodeGen;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PySetCheckExactNodeGen;
import com.oracle.graal.python.lib.PyTupleCheckExactNodeGen;
import com.oracle.graal.python.lib.PyUnicodeCheckExactNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.memory.ByteArraySupport;

@CoreFunctions(defineModule = "marshal")
public final class MarshalModuleBuiltins extends PythonBuiltins {
    static final int CURRENT_VERSION = 4;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MarshalModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        builtinConstants.put("version", CURRENT_VERSION);
    }

    @Builtin(name = "dump", minNumOfPositionalArgs = 2, parameterNames = {"value", "file", "version"})
    @ArgumentClinic(name = "version", defaultValue = "CURRENT_VERSION", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class DumpNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DumpNodeClinicProviderGen.INSTANCE;
        }

        protected static LookupAndCallBinaryNode createCallWriteNode() {
            return LookupAndCallBinaryNode.create("write");
        }

        @Specialization
        Object doit(VirtualFrame frame, Object value, Object file, int version,
                        @Cached("createCallWriteNode()") LookupAndCallBinaryNode callNode) {
            Object savedState = IndirectCallContext.enter(frame, this);
            try {
                return callNode.executeObject(frame, file, factory().createBytes(Marshal.dump(value, version, getCore())));
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } catch (Marshal.MarshalError me) {
                throw raise(me.type, me.message, me.arguments);
            } finally {
                IndirectCallContext.exit(frame, this, savedState);
            }
        }
    }

    @Builtin(name = "dumps", minNumOfPositionalArgs = 1, parameterNames = {"value", "version"})
    @ArgumentClinic(name = "version", defaultValue = "CURRENT_VERSION", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class DumpsNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DumpsNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doit(VirtualFrame frame, Object value, int version) {
            Object savedState = IndirectCallContext.enter(frame, this);
            try {
                return factory().createBytes(Marshal.dump(value, version, getCore()));
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } catch (Marshal.MarshalError me) {
                throw raise(me.type, me.message, me.arguments);
            } finally {
                IndirectCallContext.exit(frame, this, savedState);
            }
        }
    }

    @Builtin(name = "load", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LoadNode extends PythonBuiltinNode {
        protected static LookupAndCallBinaryNode createCallReadNode() {
            return LookupAndCallBinaryNode.create("read");
        }

        @Specialization
        Object doit(VirtualFrame frame, Object file,
                        @Cached("createCallReadNode()") LookupAndCallBinaryNode callNode,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferLib) {
            Object buffer = callNode.executeObject(frame, file, 0);
            if (!bufferLib.hasBuffer(buffer)) {
                throw raise(PythonBuiltinClassType.TypeError, "file.read() returned not bytes but %p", buffer);
            }
            try {
                return Marshal.loadFile(file);
            } catch (NumberFormatException e) {
                throw raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA_S, e.getMessage());
            } catch (Marshal.MarshalError me) {
                throw raise(me.type, me.message, me.arguments);
            }
        }
    }

    @Builtin(name = "loads", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"bytes"})
    @ArgumentClinic(name = "bytes", conversion = ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class LoadsNode extends PythonUnaryClinicBuiltinNode {

        @Specialization
        Object doit(VirtualFrame frame, Object buffer,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            try {
                return Marshal.load(bufferLib.getInternalOrCopiedByteArray(buffer), bufferLib.getBufferLength(buffer));
            } catch (NumberFormatException e) {
                throw raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA_S, e.getMessage());
            } catch (Marshal.MarshalError me) {
                throw raise(me.type, me.message, me.arguments);
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MarshalModuleBuiltinsClinicProviders.LoadsNodeClinicProviderGen.INSTANCE;
        }

    }

    static final class Marshal {
        private static final char TYPE_NULL = '0';
        private static final char TYPE_NONE = 'N';
        private static final char TYPE_NOVALUE = 'n';
        private static final char TYPE_FALSE = 'F';
        private static final char TYPE_TRUE = 'T';
        private static final char TYPE_STOPITER = 'S';
        private static final char TYPE_ELLIPSIS = '.';
        private static final char TYPE_INT = 'i';
        private static final char TYPE_INT64 = 'I'; // just for backward compatibility with CPython
        private static final char TYPE_FLOAT = 'f';
        private static final char TYPE_BINARY_FLOAT = 'g';
        private static final char TYPE_COMPLEX = 'x';
        private static final char TYPE_BINARY_COMPLEX = 'y';
        private static final char TYPE_LONG = 'l';
        private static final char TYPE_STRING = 's';
        private static final char TYPE_INTERNED = 't';
        private static final char TYPE_REF = 'r';
        private static final char TYPE_TUPLE = '(';
        private static final char TYPE_LIST = '[';
        private static final char TYPE_DICT = '{';
        private static final char TYPE_UNICODE = 'u';
        private static final char TYPE_UNKNOWN = '?';
        private static final char TYPE_SET = '<';
        private static final char TYPE_FROZENSET = '>';
        private static final char FLAG_REF = 0x80;
        private static final char TYPE_ASCII = 'a';
        private static final char TYPE_ASCII_INTERNED = 'A';
        private static final char TYPE_SMALL_TUPLE = ')';
        private static final char TYPE_SHORT_ASCII = 'z';
        private static final char TYPE_SHORT_ASCII_INTERNED = 'Z';

        // Following types are GraalPython-specific for serializing our code objects that may use
        // plain Java objects
        private static final char TYPE_GRAALPYTHON_CODE = 'C';
        private static final char TYPE_GRAALPYTHON_CODE_UNIT = 'U';
        private static final char TYPE_BIG_INTEGER = 'B';
        private static final char TYPE_ARRAY = ']';

        private static final char ARRAY_TYPE_OBJECT = 'o';
        private static final char ARRAY_TYPE_INT = 'i';
        private static final char ARRAY_TYPE_LONG = 'l';
        private static final char ARRAY_TYPE_DOUBLE = 'd';
        private static final char ARRAY_TYPE_BYTE = 'b';
        private static final char ARRAY_TYPE_SHORT = 's';
        private static final char ARRAY_TYPE_STRING = 'S';
        private static final int MAX_MARSHAL_STACK_DEPTH = 201;

        // CPython enforces 15bits per digit when reading/writing large integers for portability
        private static final int MARSHAL_SHIFT = 15;
        private static final BigInteger MARSHAL_BASE = BigInteger.valueOf(1 << MARSHAL_SHIFT);

        private static final int BYTES_PER_LONG = Long.SIZE / Byte.SIZE;
        private static final int BYTES_PER_INT = Integer.SIZE / Byte.SIZE;
        private static final int BYTES_PER_SHORT = Short.SIZE / Byte.SIZE;

        /**
         * This class exists to throw errors out of the (un)marshalling code, without having to
         * construct Python exceptions (yet). Since the (un)marshalling code does not have nodes or
         * frames ready, callers are responsible for catching the MarshalError and translating it
         * into a PException so that the python level exception has the correct context and
         * traceback.
         */
        static final class MarshalError extends RuntimeException {
            static final long serialVersionUID = 5323687983726237118L;

            final PythonBuiltinClassType type;
            final String message;
            final Object[] arguments;

            MarshalError(PythonBuiltinClassType type, String message, Object... arguments) {
                super(null, null);
                this.type = type;
                this.message = message;
                this.arguments = arguments;
            }

            @SuppressWarnings("sync-override")
            @Override
            public final Throwable fillInStackTrace() {
                return this;
            }
        }

        @TruffleBoundary
        static byte[] dump(Object value, int version, Python3Core core) throws IOException, MarshalError {
            Marshal outMarshal = new Marshal(version, core.getTrue(), core.getFalse());
            outMarshal.writeObject(value);
            return outMarshal.out.toByteArray();
        }

        @TruffleBoundary
        static Object load(byte[] ary, int length) throws NumberFormatException, MarshalError {
            Marshal inMarshal = new Marshal(ary, length);
            Object result = inMarshal.readObject();
            if (result == null) {
                throw new MarshalError(PythonBuiltinClassType.TypeError, ErrorMessages.BAD_MARSHAL_DATA_NULL);
            }
            return result;
        }

        @TruffleBoundary
        static Object loadFile(Object file) throws NumberFormatException, MarshalError {
            Marshal inMarshal = new Marshal(file);
            Object result = inMarshal.readObject();
            if (result == null) {
                throw new MarshalError(PythonBuiltinClassType.TypeError, ErrorMessages.BAD_MARSHAL_DATA_NULL);
            }
            return result;
        }

        /**
         * This is for making the Marshal object simpler. This stream implements the logic of
         * Python's r_string function in marshal.c when p->readable is set, i.e., it uses readinto
         * to read enough bytes into a buffer.
         */
        static final class FileLikeInputStream extends InputStream {
            private static final String METHOD = "readinto";
            private final Object fileLike;
            private final PyObjectCallMethodObjArgs callReadIntoNode;
            private final PyNumberAsSizeNode asSize;
            private final PByteArray buffer;
            private final ByteSequenceStorage singleByteStore;

            FileLikeInputStream(Object fileLike) {
                this.fileLike = fileLike;
                this.callReadIntoNode = PyObjectCallMethodObjArgs.getUncached();
                this.asSize = PyNumberAsSizeNode.getUncached();
                this.singleByteStore = new ByteSequenceStorage(new byte[1]);
                this.buffer = PythonObjectFactory.getUncached().createByteArray(singleByteStore);
            }

            @Override
            public int read() {
                Object readIntoResult = callReadIntoNode.execute(null, fileLike, METHOD, buffer);
                int numRead = asSize.executeExact(null, readIntoResult, ValueError);
                if (numRead > 1) {
                    throw new MarshalError(ValueError, ErrorMessages.S_RETURNED_TOO_MUCH_DATA, "read()", 1, numRead);
                }
                return singleByteStore.getIntItemNormalized(0);
            }

            @Override
            public int read(byte[] b, int off, int len) {
                assert off == 0;
                ByteSequenceStorage tempStore = new ByteSequenceStorage(b, len);
                buffer.setSequenceStorage(tempStore);
                try {
                    Object readIntoResult = callReadIntoNode.execute(null, fileLike, METHOD, buffer);
                    int numRead = asSize.executeExact(null, readIntoResult, ValueError);
                    if (numRead > len) {
                        throw new MarshalError(ValueError, ErrorMessages.S_RETURNED_TOO_MUCH_DATA, "read()", 1, numRead);
                    }
                    return numRead;
                } finally {
                    buffer.setSequenceStorage(singleByteStore);
                }
            }
        }

        private static final PythonObjectFactory factory = PythonObjectFactory.getUncached();
        final HashMap<Object, Integer> refMap;
        final ArrayList<Object> refList;
        final ByteArrayOutputStream out;
        final InputStream in;
        final int version;
        final PInt pyTrue;
        final PInt pyFalse;
        // CPython's marshal code is little endian
        final ByteArraySupport baSupport = ByteArraySupport.littleEndian();
        byte[] buffer = new byte[Long.BYTES];
        int depth = 0;

        Marshal(int version, PInt pyTrue, PInt pyFalse) {
            this.version = version;
            this.pyTrue = pyTrue;
            this.pyFalse = pyFalse;
            this.out = new ByteArrayOutputStream();
            this.refMap = new HashMap<>();
            this.in = null;
            this.refList = null;
        }

        Marshal(byte[] in, int length) {
            this.in = new ByteArrayInputStream(in, 0, length);
            this.refList = new ArrayList<>();
            this.version = -1;
            this.pyTrue = null;
            this.pyFalse = null;
            this.out = null;
            this.refMap = null;
        }

        Marshal(Object in) {
            this.in = new FileLikeInputStream(in);
            this.refList = new ArrayList<>();
            this.version = -1;
            this.pyTrue = null;
            this.pyFalse = null;
            this.out = null;
            this.refMap = null;
        }

        private void writeByte(int v) {
            out.write(v);
        }

        private int readByte() {
            int nextByte;
            try {
                nextByte = in.read();
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            if (nextByte < 0) {
                throw new MarshalError(PythonBuiltinClassType.EOFError, ErrorMessages.BAD_MARSHAL_DATA_EOF);
            }
            return nextByte;
        }

        private void writeSize(int sz) {
            writeInt(sz);
        }

        private int readByteSize() {
            return checkSize(readByte());
        }

        private int readSize() {
            return checkSize(readInt());
        }

        private static int checkSize(int sz) {
            if (sz < 0) {
                throw new MarshalError(PythonBuiltinClassType.EOFError, ErrorMessages.BAD_MARSHAL_DATA_S, "size out of range");
            }
            return sz;
        }

        private void writeBytes(byte[] bytes) throws IOException {
            writeSize(bytes.length);
            out.write(bytes);
        }

        private byte[] readNBytes(int sz) {
            if (sz == 0) {
                return PythonUtils.EMPTY_BYTE_ARRAY;
            } else {
                if (buffer.length < sz) {
                    buffer = new byte[sz];
                }
                return readNBytes(sz, buffer);
            }
        }

        private byte[] readNBytes(int sz, byte[] output) {
            if (sz == 0) {
                return output;
            }
            int read;
            try {
                read = in.read(output, 0, sz);
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            if (read < sz) {
                throw new MarshalError(PythonBuiltinClassType.EOFError, ErrorMessages.BAD_MARSHAL_DATA_EOF);
            }
            return output;
        }

        private byte[] readBytes() {
            int sz = readSize();
            return readNBytes(sz, new byte[sz]);
        }

        private void writeInt(int v) {
            for (int i = 0; i < Integer.SIZE; i += Byte.SIZE) {
                out.write((v >> i) & 0xff);
            }
        }

        private void writeShort(short v) {
            for (int i = 0; i < Short.SIZE; i += Byte.SIZE) {
                out.write((v >> i) & 0xff);
            }
        }

        private int readInt() {
            return baSupport.getInt(readNBytes(BYTES_PER_INT), 0);
        }

        private short readShort() {
            return baSupport.getShort(readNBytes(BYTES_PER_SHORT), 0);
        }

        private void writeLong(long v) {
            for (int i = 0; i < Long.SIZE; i += Byte.SIZE) {
                out.write((int) ((v >>> i) & 0xff));
            }
        }

        private long readLong() {
            return baSupport.getLong(readNBytes(BYTES_PER_LONG), 0);
        }

        private void writeBigInteger(BigInteger v) {
            // for compatibility with cpython, we store the number in base 2**15
            BigInteger[] divRem;
            ArrayList<Integer> digits = new ArrayList<>();
            BigInteger quotient = v.abs();
            do {
                divRem = quotient.divideAndRemainder(MARSHAL_BASE);
                quotient = divRem[0];
                digits.add(divRem[1].intValue());
            } while (quotient.signum() != 0);
            int sz = digits.size();
            if (v.signum() < 0) {
                writeSize(-sz);
            } else {
                writeSize(sz);
            }
            for (int digit : digits) {
                for (int i = 0; i < Short.SIZE; i += Byte.SIZE) {
                    out.write((digit >> i) & 0xff);
                }
            }
        }

        private BigInteger readBigInteger() {
            boolean negative;
            int sz = readInt();
            if (sz < 0) {
                negative = true;
                sz = -sz;
            } else {
                negative = false;
            }

            // size is in shorts
            sz *= 2;

            byte[] data = readNBytes(sz);

            int i = 0;
            int digit = baSupport.getShort(data, i);
            i += 2;
            BigInteger result = BigInteger.valueOf(digit);

            while (i < sz) {
                int power = i / 2;
                digit = baSupport.getShort(data, i);
                i += 2;
                result = result.add(BigInteger.valueOf(digit).multiply(MARSHAL_BASE.pow(power)));
            }
            if (negative) {
                return result.negate();
            } else {
                return result;
            }
        }

        private void writeDouble(double v) {
            writeLong(Double.doubleToLongBits(v));
        }

        private double readDouble() {
            return Double.longBitsToDouble(readLong());
        }

        private void writeDoubleString(double v) throws IOException {
            writeShortString(Double.toString(v));
        }

        private double readDoubleString() throws NumberFormatException {
            return Double.parseDouble(readShortString());
        }

        private void writeReferenceOrComplexObject(Object v) {
            Integer reference = null;
            if (version < 3 || (reference = refMap.get(v)) == null) {
                int flag = 0;
                if (version >= 3) {
                    flag = FLAG_REF;
                    refMap.put(v, refMap.size());
                }
                writeComplexObject(v, flag);
            } else if (reference != null) {
                writeByte(TYPE_REF);
                writeInt(reference);
            }
        }

        private Object readReference() {
            int n = readInt();
            if (n < 0 || n >= refList.size()) {
                throw new MarshalError(ValueError, ErrorMessages.BAD_MARSHAL_DATA);
            }
            Object o = refList.get(n);
            assert o != null;
            return o;
        }

        private void writeObject(Object v) throws IOException {
            depth++;

            if (depth >= MAX_MARSHAL_STACK_DEPTH) {
                throw new MarshalError(ValueError, ErrorMessages.MAX_MARSHAL_STACK_DEPTH);
            }

            // see CPython's w_object
            if (v == null) {
                writeNull();
            } else if (v == PNone.NONE) {
                writeByte(TYPE_NONE);
            } else if (v == PNone.NO_VALUE) {
                writeByte(TYPE_NOVALUE);
            } else if (TypeNodes.IsSameTypeNode.getUncached().execute(v, PythonBuiltinClassType.StopIteration)) {
                writeByte(TYPE_STOPITER);
            } else if (TypeNodes.IsSameTypeNode.getUncached().execute(v, PythonBuiltinClassType.PEllipsis)) {
                writeByte(TYPE_ELLIPSIS);
            } else if (v == Boolean.TRUE || v == pyTrue) {
                writeByte(TYPE_TRUE);
            } else if (v == Boolean.FALSE || v == pyFalse) {
                writeByte(TYPE_FALSE);
            } else if (v instanceof Integer) {
                writeByte(TYPE_INT);
                writeInt((Integer) v);
            } else if (v instanceof Long) {
                writeByte(TYPE_INT64);
                writeLong((Long) v);
            } else if (v instanceof Double) {
                if (version > 1) {
                    writeByte(TYPE_BINARY_FLOAT);
                    writeDouble((Double) v);
                } else {
                    writeByte(TYPE_FLOAT);
                    writeDoubleString((Double) v);
                }
            } else if (v instanceof BigInteger) {
                writeByte(TYPE_BIG_INTEGER);
                writeBigInteger((BigInteger) v);
            } else {
                writeReferenceOrComplexObject(v);
            }

            depth--;
        }

        private void writeNull() {
            writeByte(TYPE_NULL);
        }

        private void writeComplexObject(Object v, int flag) {
            try {
                if (PyLongCheckExactNodeGen.getUncached().execute(v)) {
                    BigInteger bigInt = ((PInt) v).getValue();
                    if (bigInt.signum() == 0) {
                        // we don't handle ZERO in read/writeBigInteger
                        writeByte(TYPE_INT | flag);
                        writeInt(0);
                    } else {
                        // other cases are fine to not narrow here
                        writeByte(TYPE_LONG | flag);
                        writeBigInteger(((PInt) v).getValue());
                    }
                } else if (PyFloatCheckExactNodeGen.getUncached().execute(v)) {
                    if (version > 1) {
                        writeByte(TYPE_BINARY_FLOAT | flag);
                        writeDouble(((PFloat) v).getValue());
                    } else {
                        writeByte(TYPE_FLOAT | flag);
                        writeDoubleString(((PFloat) v).getValue());
                    }
                } else if (PyComplexCheckExactNodeGen.getUncached().execute(v)) {
                    if (version > 1) {
                        writeByte(TYPE_BINARY_COMPLEX | flag);
                        writeDouble(((PComplex) v).getReal());
                        writeDouble(((PComplex) v).getImag());
                    } else {
                        writeByte(TYPE_COMPLEX | flag);
                        writeDoubleString(((PComplex) v).getReal());
                        writeDoubleString(((PComplex) v).getImag());
                    }
                } else if (v instanceof String) {
                    writeByte(TYPE_UNICODE | flag);
                    writeString((String) v);
                } else if (PyUnicodeCheckExactNodeGen.getUncached().execute(v)) {
                    if (version >= 3 && IsInternedStringNodeGen.getUncached().execute((PString) v)) {
                        writeByte(TYPE_INTERNED | flag);
                    } else {
                        writeByte(TYPE_UNICODE | flag);
                    }
                    writeString(((PString) v).getValue());
                } else if (PyTupleCheckExactNodeGen.getUncached().execute(v)) {
                    Object[] items = GetObjectArrayNodeGen.getUncached().execute(v);
                    if (version >= 4 && items.length < 256) {
                        writeByte(TYPE_SMALL_TUPLE | flag);
                        writeByte(items.length);
                    } else {
                        writeByte(TYPE_TUPLE | flag);
                        writeSize(items.length);
                    }
                    for (Object item : items) {
                        writeObject(item);
                    }
                } else if (PyListCheckExactNodeGen.getUncached().execute(v)) {
                    writeByte(TYPE_LIST | flag);
                    Object[] items = GetInternalObjectArrayNodeGen.getUncached().execute(SequenceNodes.GetSequenceStorageNode.getUncached().execute(v));
                    writeSize(items.length);
                    for (Object item : items) {
                        writeObject(item);
                    }
                } else if (v instanceof PDict && PyDictCheckExactNodeGen.getUncached().execute(v)) {
                    HashingStorage dictStorage = ((PDict) v).getDictStorage();
                    // NULL terminated as in CPython
                    writeByte(TYPE_DICT | flag);
                    HashingStorageLibrary lib = HashingStorageLibrary.getFactory().getUncached(dictStorage);
                    for (DictEntry entry : lib.entries(dictStorage)) {
                        writeObject(entry.key);
                        writeObject(entry.value);
                    }
                    writeNull();
                } else if (v instanceof PBaseSet && (PySetCheckExactNodeGen.getUncached().execute(v) || PyFrozenSetCheckExactNodeGen.getUncached().execute(v))) {
                    if (PyFrozenSetCheckExactNodeGen.getUncached().execute(v)) {
                        writeByte(TYPE_FROZENSET | flag);
                    } else {
                        writeByte(TYPE_SET | flag);
                    }
                    HashingStorage dictStorage = ((PBaseSet) v).getDictStorage();
                    HashingStorageLibrary lib = HashingStorageLibrary.getFactory().getUncached(dictStorage);
                    int len = lib.length(dictStorage);
                    writeSize(len);
                    for (DictEntry entry : lib.entries(dictStorage)) {
                        writeObject(entry.key);
                    }
                } else if (v instanceof int[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_INT);
                    writeIntArray((int[]) v);
                } else if (v instanceof long[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_LONG);
                    writeLongArray((long[]) v);
                } else if (v instanceof short[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_SHORT);
                    writeShortArray((short[]) v);
                } else if (v instanceof double[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_DOUBLE);
                    writeDoubleArray((double[]) v);
                } else if (v instanceof byte[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_BYTE);
                    writeBytes((byte[]) v);
                } else if (v instanceof String[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_STRING);
                    writeStringArray((String[]) v);
                } else if (v instanceof Object[]) {
                    writeByte(TYPE_ARRAY | flag);
                    writeByte(ARRAY_TYPE_OBJECT);
                    writeObjectArray((Object[]) v);
                } else if (v instanceof PCode) {
                    // we always store code objects in our format, CPython will not read our
                    // marshalled data when that contains code objects
                    PCode c = (PCode) v;
                    writeByte(TYPE_GRAALPYTHON_CODE | flag);
                    writeString(c.getFilename());
                    writeInt(c.getFlags());
                    writeBytes(c.getCodestring());
                    writeInt(c.getFirstLineNo());
                    byte[] lnotab = c.getLnotab();
                    if (lnotab == null) {
                        lnotab = PythonUtils.EMPTY_BYTE_ARRAY;
                    }
                    writeBytes(lnotab);
                } else if (v instanceof CodeUnit) {
                    writeByte(TYPE_GRAALPYTHON_CODE_UNIT | flag);
                    writeCodeUnit((CodeUnit) v);
                } else {
                    PythonBufferAcquireLibrary acquireLib = PythonBufferAcquireLibrary.getFactory().getUncached(v);
                    if (acquireLib.hasBuffer(v)) {
                        writeByte(TYPE_STRING | flag);
                        Object buf = acquireLib.acquireReadonly(v);
                        PythonBufferAccessLibrary accessLib = PythonBufferAccessLibrary.getFactory().getUncached(buf);
                        try {
                            int len = accessLib.getBufferLength(buf);
                            writeSize(len);
                            out.write(accessLib.getInternalOrCopiedByteArray(buf), 0, len);
                        } finally {
                            accessLib.release(buf);
                        }
                    } else {
                        writeByte(TYPE_UNKNOWN);
                        throw new MarshalError(ValueError, ErrorMessages.WAS_NOT_POSSIBLE_TO_MARSHAL_P, v);
                    }
                }
            } catch (IOException e) {
                throw new MarshalError(ValueError, ErrorMessages.WAS_NOT_POSSIBLE_TO_MARSHAL_P, v);
            }
        }

        private void writeObjectArray(Object[] a) throws IOException {
            writeInt(a.length);
            for (int i = 0; i < a.length; i++) {
                writeObject(a[i]);
            }
        }

        private void writeDoubleArray(double[] a) {
            writeInt(a.length);
            for (int i = 0; i < a.length; i++) {
                writeDouble(a[i]);
            }
        }

        private void writeLongArray(long[] a) {
            writeInt(a.length);
            for (int i = 0; i < a.length; i++) {
                writeLong(a[i]);
            }
        }

        private void writeIntArray(int[] a) {
            writeInt(a.length);
            for (int i = 0; i < a.length; i++) {
                writeInt(a[i]);
            }
        }

        private void writeStringArray(String[] a) throws IOException {
            writeInt(a.length);
            for (int i = 0; i < a.length; i++) {
                writeString(a[i]);
            }
        }

        private void writeShortArray(short[] a) throws IOException {
            writeInt(a.length);
            for (int i = 0; i < a.length; i++) {
                writeShort(a[i]);
            }
        }

        @FunctionalInterface
        static interface AddRefAndReturn {
            Object run(Object o);
        }

        private Object readObject() throws NumberFormatException {
            depth++;

            if (depth >= MAX_MARSHAL_STACK_DEPTH) {
                throw new MarshalError(ValueError, ErrorMessages.MAX_MARSHAL_STACK_DEPTH);
            }

            int code = readByte();
            int flag = code & FLAG_REF;
            int type = code & ~FLAG_REF;

            if (type == TYPE_REF) {
                depth--;
                return readReference();
            } else {
                int reference = refList.size();
                if (flag != 0) {
                    refList.add(null);
                }
                Object retval = readObject(type, (o) -> {
                    if (flag != 0) {
                        refList.set(reference, o);
                    }
                    return o;
                });
                depth--;
                return retval;
            }
        }

        private Object readObject(int type, AddRefAndReturn addRef) throws NumberFormatException {
            switch (type) {
                case TYPE_NULL:
                    return null;
                case TYPE_NONE:
                    return PNone.NONE;
                case TYPE_NOVALUE:
                    return PNone.NO_VALUE;
                case TYPE_STOPITER:
                    return PythonBuiltinClassType.StopIteration;
                case TYPE_ELLIPSIS:
                    return PythonBuiltinClassType.PEllipsis;
                case TYPE_FALSE:
                    return false;
                case TYPE_TRUE:
                    return true;
                case TYPE_INT:
                    return addRef.run(readInt());
                case TYPE_INT64:
                    return addRef.run(readLong());
                case TYPE_BIG_INTEGER:
                    return readBigInteger();
                case TYPE_LONG:
                    return addRef.run(factory.createInt(readBigInteger()));
                case TYPE_FLOAT:
                    return addRef.run(readDoubleString());
                case TYPE_BINARY_FLOAT:
                    return addRef.run(readDouble());
                case TYPE_COMPLEX:
                    return addRef.run(factory.createComplex(readDoubleString(), readDoubleString()));
                case TYPE_BINARY_COMPLEX:
                    return addRef.run(factory.createComplex(readDouble(), readDouble()));
                case TYPE_STRING:
                    return addRef.run(factory.createBytes(readBytes()));
                case TYPE_ASCII_INTERNED:
                    return addRef.run(readAscii(readSize(), true));
                case TYPE_ASCII:
                    return addRef.run(readAscii(readSize(), false));
                case TYPE_SHORT_ASCII_INTERNED:
                    return addRef.run(readAscii(readByteSize(), true));
                case TYPE_SHORT_ASCII:
                    return addRef.run(readAscii(readByteSize(), false));
                case TYPE_INTERNED:
                    return addRef.run(StringNodes.InternStringNode.getUncached().execute(readString()));
                case TYPE_UNICODE:
                    return addRef.run(readString());
                case TYPE_SMALL_TUPLE:
                    int smallTupleSize = readByteSize();
                    Object[] smallTupleItems = new Object[smallTupleSize];
                    Object smallTuple = addRef.run(factory.createTuple(smallTupleItems));
                    readArray(smallTupleItems);
                    return smallTuple;
                case TYPE_TUPLE:
                    int tupleSize = readSize();
                    Object[] tupleItems = new Object[tupleSize];
                    Object tuple = addRef.run(factory.createTuple(tupleItems));
                    readArray(tupleItems);
                    return tuple;
                case TYPE_LIST:
                    int listSize = readSize();
                    Object[] listItems = new Object[listSize];
                    Object list = addRef.run(factory.createList(listItems));
                    readArray(listItems);
                    return list;
                case TYPE_DICT:
                    HashingStorage store = PDict.createNewStorage(false, 0);
                    PDict dict = factory.createDict(store);
                    addRef.run(dict);
                    HashingStorageLibrary dictLib = HashingStorageLibrary.getUncached();
                    while (true) {
                        Object key = readObject();
                        if (key == null) {
                            break;
                        }
                        Object value = readObject();
                        if (value != null) {
                            store = dictLib.setItem(store, key, value);
                        }
                    }
                    dict.setDictStorage(store);
                    return dict;
                case TYPE_SET:
                case TYPE_FROZENSET:
                    int setSz = readSize();
                    HashingStorage setStore = EconomicMapStorage.create(setSz);
                    PBaseSet set;
                    if (type == TYPE_FROZENSET) {
                        set = factory.createFrozenSet(setStore);
                    } else {
                        set = factory.createSet(setStore);
                    }
                    addRef.run(set);
                    HashingStorageLibrary setLib = HashingStorageLibrary.getFactory().getUncached(setStore);
                    for (int i = 0; i < setSz; i++) {
                        Object key = readObject();
                        if (key == null) {
                            throw new MarshalError(PythonBuiltinClassType.TypeError, ErrorMessages.BAD_MARSHAL_DATA_NULL);
                        }
                        setStore = setLib.setItem(setStore, key, PNone.NO_VALUE);
                    }
                    set.setDictStorage(setStore);
                    return set;
                case TYPE_GRAALPYTHON_CODE:
                    return addRef.run(readCode());
                case TYPE_GRAALPYTHON_CODE_UNIT:
                    return addRef.run(readCodeUnit());
                case TYPE_ARRAY: {
                    return addRef.run(readJavaArray());
                }
                default:
                    throw new MarshalError(ValueError, ErrorMessages.BAD_MARSHAL_DATA);
            }
        }

        private void writeString(String v) throws IOException {
            byte[] bytes = v.getBytes(StandardCharsets.UTF_8);
            writeSize(bytes.length);
            out.write(bytes);
        }

        private String readString() {
            int sz = readSize();
            return new String(readNBytes(sz), 0, sz, StandardCharsets.UTF_8);
        }

        private void writeShortString(String v) throws IOException {
            byte[] bytes = v.getBytes(StandardCharsets.ISO_8859_1);
            assert bytes.length < 256;
            writeByte(bytes.length);
            out.write(bytes);
        }

        private String readShortString() {
            int sz = readByteSize();
            byte[] bytes = readNBytes(sz);
            return new String(bytes, 0, sz, StandardCharsets.ISO_8859_1);
        }

        private Object readAscii(long sz, boolean intern) {
            byte[] bytes = readNBytes((int) sz);
            String value = new String(bytes, 0, (int) sz, StandardCharsets.US_ASCII);
            if (intern) {
                return StringNodes.InternStringNode.getUncached().execute(value);
            } else {
                return value;
            }
        }

        private void readArray(Object[] items) throws NumberFormatException {
            for (int i = 0; i < items.length; i++) {
                Object item = readObject();
                if (item == null) {
                    throw new MarshalError(PythonBuiltinClassType.EOFError, ErrorMessages.BAD_MARSHAL_DATA);
                }
                items[i] = item;
            }
        }

        private Object readJavaArray() {
            int type = readByte();
            switch (type) {
                case ARRAY_TYPE_BYTE:
                    return readBytes();
                case ARRAY_TYPE_INT:
                    return readIntArray();
                case ARRAY_TYPE_LONG:
                    return readLongArray();
                case ARRAY_TYPE_DOUBLE:
                    return readDoubleArray();
                case ARRAY_TYPE_SHORT:
                    return readShortArray();
                case ARRAY_TYPE_STRING:
                    return readStringArray();
                case ARRAY_TYPE_OBJECT:
                    return readObjectArray();
                default:
                    throw CompilerDirectives.shouldNotReachHere("unknown array type");
            }
        }

        private int[] readIntArray() {
            int length = readInt();
            int[] a = new int[length];
            for (int i = 0; i < length; i++) {
                a[i] = readInt();
            }
            return a;
        }

        private long[] readLongArray() {
            int length = readInt();
            long[] a = new long[length];
            for (int i = 0; i < length; i++) {
                a[i] = readLong();
            }
            return a;
        }

        private double[] readDoubleArray() {
            int length = readInt();
            double[] a = new double[length];
            for (int i = 0; i < length; i++) {
                a[i] = readDouble();
            }
            return a;
        }

        private short[] readShortArray() {
            int length = readInt();
            short[] a = new short[length];
            for (int i = 0; i < length; i++) {
                a[i] = readShort();
            }
            return a;
        }

        private String[] readStringArray() {
            int length = readInt();
            String[] a = new String[length];
            for (int i = 0; i < length; i++) {
                a[i] = readString();
            }
            return a;
        }

        private Object[] readObjectArray() {
            int length = readInt();
            Object[] a = new Object[length];
            for (int i = 0; i < length; i++) {
                a[i] = readObject();
            }
            return a;
        }

        private CodeUnit readCodeUnit() {
            int version = readByte();
            if (version != Compiler.BYTECODE_VERSION) {
                throw new MarshalError(ValueError, ErrorMessages.BYTECODE_VERSION_MISMATCH, Compiler.BYTECODE_VERSION, version);
            }
            String name = readString();
            String qualname = readString();
            int argCount = readInt();
            int kwOnlyArgCount = readInt();
            int positionalOnlyArgCount = readInt();
            int stacksize = readInt();
            byte[] code = readBytes();
            byte[] srcOffsetTable = readBytes();
            int flags = readInt();
            String[] names = readStringArray();
            String[] varnames = readStringArray();
            String[] cellvars = readStringArray();
            String[] freevars = readStringArray();
            int[] cell2arg = readIntArray();
            if (cell2arg.length == 0) {
                cell2arg = null;
            }
            Object[] constants = readObjectArray();
            long[] primitiveConstants = readLongArray();
            short[] exceptionHandlerRanges = readShortArray();
            int startOffset = readInt();
            return new CodeUnit(name, qualname, argCount, kwOnlyArgCount, positionalOnlyArgCount, stacksize, code, srcOffsetTable,
                            flags, names, varnames, cellvars, freevars, cell2arg, constants, primitiveConstants, exceptionHandlerRanges, startOffset);
        }

        private void writeCodeUnit(CodeUnit code) throws IOException {
            writeByte(Compiler.BYTECODE_VERSION);
            writeString(code.name);
            writeString(code.qualname);
            writeInt(code.argCount);
            writeInt(code.kwOnlyArgCount);
            writeInt(code.positionalOnlyArgCount);
            writeInt(code.stacksize);
            writeBytes(code.code);
            writeBytes(code.srcOffsetTable);
            writeInt(code.flags);
            writeStringArray(code.names);
            writeStringArray(code.varnames);
            writeStringArray(code.cellvars);
            writeStringArray(code.freevars);
            if (code.cell2arg != null) {
                writeIntArray(code.cell2arg);
            } else {
                writeIntArray(PythonUtils.EMPTY_INT_ARRAY);
            }
            writeObjectArray(code.constants);
            writeLongArray(code.primitiveConstants);
            writeShortArray(code.exceptionHandlerRanges);
            writeInt(code.startOffset);
        }

        private PCode readCode() {
            String fileName = readString().intern();
            int flags = readInt();

            int codeLen = readSize();
            byte[] codeString = new byte[codeLen + Long.BYTES];
            try {
                in.read(codeString, 0, codeLen);
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            // get a new ID every time we deserialize the same filename in the same context. We use
            // slow-path context lookup, since this code is likely dominated by the deserialization
            // time
            PythonContext context = PythonContext.get(null);
            ByteBuffer.wrap(codeString).putLong(codeLen, context.getDeserializationId(fileName));
            int firstLineNo = readInt();
            byte[] lnoTab = readBytes();
            return CreateCodeNode.createCode(context, flags, codeString, fileName, firstLineNo, lnoTab);
        }
    }

    @TruffleBoundary
    public static byte[] serializeCodeUnit(CodeUnit code) {
        try {
            Marshal marshal = new Marshal(CURRENT_VERSION, null, null);
            marshal.writeCodeUnit(code);
            return marshal.out.toByteArray();
        } catch (IOException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        } catch (Marshal.MarshalError me) {
            throw PRaiseNode.getUncached().raise(me.type, me.message, me.arguments);
        }
    }

    @TruffleBoundary
    public static CodeUnit deserializeCodeUnit(byte[] bytes) {
        try {
            Marshal marshal = new Marshal(bytes, bytes.length);
            return marshal.readCodeUnit();
        } catch (Marshal.MarshalError me) {
            throw PRaiseNode.getUncached().raise(me.type, me.message, me.arguments);
        } catch (NumberFormatException e) {
            throw PRaiseNode.getUncached().raise(ValueError, ErrorMessages.BAD_MARSHAL_DATA_S, e.getMessage());
        }
    }
}
