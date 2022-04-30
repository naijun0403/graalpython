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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.HEXDIGITS;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.digitValue;
import static com.oracle.graal.python.nodes.BuiltinNames.ENCODE;
import static com.oracle.graal.python.nodes.BuiltinNames._CODECS;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_MUST_BE_CALLABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.BYTESLIKE_OBJ_REQUIRED;
import static com.oracle.graal.python.nodes.ErrorMessages.CODEC_SEARCH_MUST_RETURN_4;
import static com.oracle.graal.python.nodes.ErrorMessages.ENCODING_ERROR_WITH_CODE;
import static com.oracle.graal.python.nodes.ErrorMessages.HANDLER_MUST_BE_CALLABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_ESCAPE_AT;
import static com.oracle.graal.python.nodes.ErrorMessages.S_MUST_RETURN_TUPLE;
import static com.oracle.graal.python.nodes.ErrorMessages.UNKNOWN_ENCODING;
import static com.oracle.graal.python.nodes.ErrorMessages.UNKNOWN_ERROR_HANDLER;
import static com.oracle.graal.python.nodes.SpecialMethodNames.DECODE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.LookupError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeDecodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeEncodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.ByteArrayBuffer;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectTypeCheck;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithRaiseAndIndirectCall;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = _CODECS)
public class CodecsModuleBuiltins extends PythonBuiltins {

    public static final String STRICT = "strict";
    public static final String IGNORE = "ignore";
    public static final String REPLACE = "replace";
    public static final String BACKSLASHREPLACE = "backslashreplace";
    public static final String NAMEREPLACE = "namereplace";
    public static final String XMLCHARREFREPLACE = "xmlcharrefreplace";
    public static final String SURROGATEESCAPE = "surrogateescape";
    public static final String SURROGATEPASS = "surrogatepass";

    public static CodingErrorAction convertCodingErrorAction(String errors) {
        CodingErrorAction errorAction;
        switch (errors) {
            // TODO: see [GR-10256] to implement the correct handling mechanics
            case IGNORE:
                errorAction = CodingErrorAction.IGNORE;
                break;
            case REPLACE:
            case NAMEREPLACE:
                errorAction = CodingErrorAction.REPLACE;
                break;
            case STRICT:
            case BACKSLASHREPLACE:
            case SURROGATEPASS:
            case SURROGATEESCAPE:
            case XMLCHARREFREPLACE:
            default:
                // Everything else will be handled by our Handle nodes
                errorAction = CodingErrorAction.REPORT;
                break;
        }
        return errorAction;
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CodecsModuleBuiltinsFactory.getFactories();
    }

    @GenerateUncached
    public abstract static class RaiseEncodingErrorNode extends Node {
        public abstract RuntimeException execute(TruffleEncoder encoder, Object inputObject);

        @Specialization
        static RuntimeException doRaise(TruffleEncoder encoder, Object inputObject,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode) {
            int start = encoder.getInputPosition();
            int end = start + encoder.getErrorLength();
            Object exception = callNode.execute(UnicodeEncodeError, encoder.getEncodingName(), inputObject, start, end, encoder.getErrorReason());
            if (exception instanceof PBaseException) {
                throw raiseNode.raiseExceptionObject((PBaseException) exception);
            } else {
                // Shouldn't happen unless the user manually replaces the method, which is really
                // unexpected and shouldn't be permitted at all, but currently it is
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(TypeError, ErrorMessages.SHOULD_HAVE_RETURNED_EXCEPTION, UnicodeEncodeError, exception);
            }
        }
    }

    @GenerateUncached
    public abstract static class HandleEncodingErrorNode extends Node {
        public abstract void execute(TruffleEncoder encoder, String errorAction, Object inputObject);

        @Specialization
        static void handle(TruffleEncoder encoder, String errorAction, Object inputObject,
                        @Cached ConditionProfile strictProfile,
                        @Cached ConditionProfile backslashreplaceProfile,
                        @Cached ConditionProfile surrogatepassProfile,
                        @Cached ConditionProfile surrogateescapeProfile,
                        @Cached ConditionProfile xmlcharrefreplaceProfile,
                        @Cached RaiseEncodingErrorNode raiseEncodingErrorNode,
                        @Cached PRaiseNode raiseNode) {
            boolean fixed;
            try {
                // Ignore and replace are handled by Java Charset
                if (strictProfile.profile(STRICT.equals(errorAction))) {
                    fixed = false;
                } else if (backslashreplaceProfile.profile(BACKSLASHREPLACE.equals(errorAction))) {
                    fixed = backslashreplace(encoder);
                } else if (surrogatepassProfile.profile(SURROGATEPASS.equals(errorAction))) {
                    fixed = surrogatepass(encoder);
                } else if (surrogateescapeProfile.profile(SURROGATEESCAPE.equals(errorAction))) {
                    fixed = surrogateescape(encoder);
                } else if (xmlcharrefreplaceProfile.profile(XMLCHARREFREPLACE.equals(errorAction))) {
                    fixed = xmlcharrefreplace(encoder);
                } else {
                    throw raiseNode.raise(LookupError, ErrorMessages.UNKNOWN_ERROR_HANDLER, errorAction);
                }
            } catch (OutOfMemoryError e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(MemoryError);
            }
            if (!fixed) {
                throw raiseEncodingErrorNode.execute(encoder, inputObject);
            }
        }

        @TruffleBoundary
        private static boolean backslashreplace(TruffleEncoder encoder) {
            String p = new String(encoder.getInputChars(encoder.getErrorLength()));
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[10];
            for (int i = 0; i < p.length();) {
                int ch = p.codePointAt(i);
                int len;
                if (ch < 0x100) {
                    len = BytesUtils.byteEscape(ch, 0, buf);
                } else {
                    len = BytesUtils.unicodeNonAsciiEscape(ch, 0, buf);
                }
                for (int j = 0; j < len; j++) {
                    sb.append((char) buf[j]);
                }
                i += Character.charCount(ch);
            }
            encoder.replace(p.length(), sb.toString());
            return true;
        }

        @TruffleBoundary
        private static boolean surrogatepass(TruffleEncoder encoder) {
            // UTF-8 only for now. The name should be normalized already
            if (encoder.getEncodingName().equals("utf_8")) {
                String p = new String(encoder.getInputChars(encoder.getErrorLength()));
                byte[] replacement = new byte[p.length() * 3];
                int outp = 0;
                for (int i = 0; i < p.length();) {
                    int ch = p.codePointAt(i);
                    if (!(0xD800 <= ch && ch <= 0xDFFF)) {
                        // Not a surrogate
                        return false;
                    }
                    replacement[outp++] = (byte) (0xe0 | (ch >> 12));
                    replacement[outp++] = (byte) (0x80 | ((ch >> 6) & 0x3f));
                    replacement[outp++] = (byte) (0x80 | (ch & 0x3f));
                    i += Character.charCount(ch);
                }
                encoder.replace(encoder.getErrorLength(), replacement, 0, outp);
                return true;
            }
            return false;
        }

        @TruffleBoundary
        private static boolean surrogateescape(TruffleEncoder encoder) {
            String p = new String(encoder.getInputChars(encoder.getErrorLength()));
            byte[] replacement = new byte[p.length()];
            int outp = 0;
            for (int i = 0; i < p.length();) {
                int ch = p.codePointAt(i);
                if (!(0xDC80 <= ch && ch <= 0xDCFF)) {
                    // Not a surrogate
                    return false;
                }
                replacement[outp++] = (byte) (ch - 0xdc00);
                i += Character.charCount(ch);
            }
            encoder.replace(encoder.getErrorLength(), replacement, 0, outp);
            return true;
        }

        @TruffleBoundary
        private static boolean xmlcharrefreplace(TruffleEncoder encoder) {
            String p = new String(encoder.getInputChars(encoder.getErrorLength()));
            int size = 0;
            for (int i = 0; i < encoder.getErrorLength(); ++i) {
                // object is guaranteed to be "ready"
                int ch = p.codePointAt(i);
                if (ch < 10) {
                    size += 2 + 1 + 1;
                } else if (ch < 100) {
                    size += 2 + 2 + 1;
                } else if (ch < 1000) {
                    size += 2 + 3 + 1;
                } else if (ch < 10000) {
                    size += 2 + 4 + 1;
                } else if (ch < 100000) {
                    size += 2 + 5 + 1;
                } else if (ch < 1000000) {
                    size += 2 + 6 + 1;
                } else {
                    size += 2 + 7 + 1;
                }
            }

            byte[] replacement = new byte[size];
            int consumed = 0;
            // generate replacement
            for (int i = 0; i < p.length(); ++i) {
                int digits;
                int base;
                int ch = p.codePointAt(i);
                replacement[consumed++] = '&';
                replacement[consumed++] = '#';
                if (ch < 10) {
                    digits = 1;
                    base = 1;
                } else if (ch < 100) {
                    digits = 2;
                    base = 10;
                } else if (ch < 1000) {
                    digits = 3;
                    base = 100;
                } else if (ch < 10000) {
                    digits = 4;
                    base = 1000;
                } else if (ch < 100000) {
                    digits = 5;
                    base = 10000;
                } else if (ch < 1000000) {
                    digits = 6;
                    base = 100000;
                } else {
                    digits = 7;
                    base = 1000000;
                }
                while (digits-- > 0) {
                    replacement[consumed++] = (byte) ('0' + ch / base);
                    ch %= base;
                    base /= 10;
                }
                replacement[consumed++] = ';';
            }
            encoder.replace(encoder.getErrorLength(), replacement, 0, consumed);
            return true;
        }

        public static HandleEncodingErrorNode create() {
            return CodecsModuleBuiltinsFactory.HandleEncodingErrorNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class RaiseDecodingErrorNode extends Node {
        public abstract RuntimeException execute(TruffleDecoder decoder, Object inputObject);

        @Specialization
        static RuntimeException doRaise(TruffleDecoder decoder, Object inputObject,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode) {
            int start = decoder.getInputPosition();
            int end = start + decoder.getErrorLength();
            Object exception = callNode.execute(UnicodeDecodeError, decoder.getEncodingName(), inputObject, start, end, decoder.getErrorReason());
            if (exception instanceof PBaseException) {
                throw raiseNode.raiseExceptionObject((PBaseException) exception);
            } else {
                // Shouldn't happen unless the user manually replaces the method, which is really
                // unexpected and shouldn't be permitted at all, but currently it is
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(TypeError, ErrorMessages.SHOULD_HAVE_RETURNED_EXCEPTION, UnicodeDecodeError, exception);
            }
        }
    }

    @GenerateUncached
    public abstract static class HandleDecodingErrorNode extends Node {
        public abstract void execute(TruffleDecoder decoder, String errorAction, Object inputObject);

        @Specialization
        static void doStrict(TruffleDecoder decoder, String errorAction, Object inputObject,
                        @Cached ConditionProfile strictProfile,
                        @Cached ConditionProfile backslashreplaceProfile,
                        @Cached ConditionProfile surrogatepassProfile,
                        @Cached ConditionProfile surrogateescapeProfile,
                        @Cached RaiseDecodingErrorNode raiseDecodingErrorNode,
                        @Cached PRaiseNode raiseNode) {
            boolean fixed;
            try {
                // Ignore and replace are handled by Java Charset
                if (strictProfile.profile(STRICT.equals(errorAction))) {
                    fixed = false;
                } else if (backslashreplaceProfile.profile(BACKSLASHREPLACE.equals(errorAction))) {
                    fixed = backslashreplace(decoder);
                } else if (surrogatepassProfile.profile(SURROGATEPASS.equals(errorAction))) {
                    fixed = surrogatepass(decoder);
                } else if (surrogateescapeProfile.profile(SURROGATEESCAPE.equals(errorAction))) {
                    fixed = surrogateescape(decoder);
                } else {
                    throw raiseNode.raise(LookupError, ErrorMessages.UNKNOWN_ERROR_HANDLER, errorAction);
                }
            } catch (OutOfMemoryError e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(MemoryError);
            }
            if (!fixed) {
                throw raiseDecodingErrorNode.execute(decoder, inputObject);
            }
        }

        @TruffleBoundary
        private static boolean backslashreplace(TruffleDecoder decoder) {
            byte[] p = decoder.getInputBytes(decoder.getErrorLength());
            char[] replacement = new char[p.length * 4];
            int outp = 0;
            byte[] buf = new byte[4];
            for (byte b : p) {
                BytesUtils.byteEscape(b, 0, buf);
                replacement[outp++] = (char) buf[0];
                replacement[outp++] = (char) buf[1];
                replacement[outp++] = (char) buf[2];
                replacement[outp++] = (char) buf[3];
            }
            decoder.replace(p.length, replacement, 0, outp);
            return true;
        }

        @TruffleBoundary
        private static boolean surrogatepass(TruffleDecoder decoder) {
            // UTF-8 only for now. The name should be normalized already
            if (decoder.getEncodingName().equals("utf_8")) {
                if (decoder.getInputRemaining() >= 3) {
                    byte[] p = decoder.getInputBytes(3);
                    if ((p[0] & 0xf0) == 0xe0 && (p[1] & 0xc0) == 0x80 && (p[2] & 0xc0) == 0x80) {
                        int codePoint = ((p[0] & 0x0f) << 12) + ((p[1] & 0x3f) << 6) + (p[2] & 0x3f);
                        if (0xD800 <= codePoint && codePoint <= 0xDFFF) {
                            decoder.replace(3, Character.toChars(codePoint));
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        @TruffleBoundary
        private static boolean surrogateescape(TruffleDecoder decoder) {
            int errorLength = decoder.getErrorLength();
            // decode up to 4 bytes
            int consumed = 0;
            boolean replaced = false;
            byte[] inputBytes = decoder.getInputBytes(errorLength);
            while (consumed < 4 && consumed < errorLength) {
                int b = inputBytes[consumed] & 0xff;
                // Refuse to escape ASCII bytes.
                if (b < 128) {
                    break;
                }
                int codePoint = 0xdc00 + b;
                decoder.replace(1, Character.toChars(codePoint));
                replaced = true;
                consumed += 1;
            }
            return replaced;
        }

        public static HandleDecodingErrorNode create() {
            return CodecsModuleBuiltinsFactory.HandleDecodingErrorNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class CodecsEncodeToJavaBytesNode extends Node {
        public abstract byte[] execute(Object self, String encoding, String errors);

        @Specialization
        byte[] encode(Object self, String encoding, String errors,
                        @Cached CastToJavaStringNode castStr,
                        @Cached HandleEncodingErrorNode errorHandler,
                        @Cached PRaiseNode raiseNode) {
            String input = castStr.execute(self);
            CodingErrorAction errorAction = convertCodingErrorAction(errors);
            Charset charset = CharsetMapping.getCharset(encoding);
            if (charset == null) {
                throw raiseNode.raise(LookupError, ErrorMessages.UNKNOWN_ENCODING, encoding);
            }
            TruffleEncoder encoder;
            try {
                encoder = new TruffleEncoder(CharsetMapping.normalize(encoding), charset, input, errorAction);
                while (!encoder.encodingStep()) {
                    errorHandler.execute(encoder, errors, self);
                }
            } catch (OutOfMemoryError e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raiseMemoryError();
            }
            return encoder.getBytes();
        }
    }

    // _codecs.encode(obj, encoding='utf-8', errors='strict')
    @Builtin(name = "__truffle_encode__", minNumOfPositionalArgs = 1, parameterNames = {"obj", "encoding", "errors"})
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"utf-8\"", useDefaultForNone = true)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"strict\"", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CodecsEncodeNode extends PythonTernaryClinicBuiltinNode {
        public abstract Object execute(Object str, Object encoding, Object errors);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CodecsEncodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"isString(self)"})
        Object encode(Object self, String encoding, String errors,
                        @Cached CastToJavaStringNode castStr,
                        @Cached CodecsEncodeToJavaBytesNode encode) {
            String input = castStr.execute(self);
            PBytes bytes = factory().createBytes(encode.execute(self, encoding, errors));
            return factory().createTuple(new Object[]{bytes, input.length()});
        }

        @Fallback
        Object encode(Object str, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors) {
            throw raise(TypeError, ErrorMessages.CANT_CONVERT_TO_STR_IMPLICITLY, str);
        }
    }

    // _codecs.decode(obj, encoding='utf-8', errors='strict', final=False)
    @Builtin(name = "__truffle_decode__", minNumOfPositionalArgs = 1, parameterNames = {"obj", "encoding", "errors", "final"})
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"utf-8\"", useDefaultForNone = true)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"strict\"", useDefaultForNone = true)
    @ArgumentClinic(name = "final", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CodecsDecodeNode extends PythonQuaternaryClinicBuiltinNode {

        public final Object call(VirtualFrame frame, Object input, Object encoding, Object errors, Object finalData) {
            return execute(frame, input, encoding, errors, finalData);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CodecsDecodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object decode(VirtualFrame frame, Object input, String encoding, String errors, boolean finalData,
                        @Cached InternalCodecsDecodeNode internalNode) {
            return internalNode.execute(frame, this, input, encoding, errors, finalData);
        }
    }

    @GenerateUncached
    public abstract static class InternalCodecsDecodeNode extends PNodeWithContext {
        abstract Object execute(Frame frame, PNodeWithRaiseAndIndirectCall node, Object input, String encoding, String errors, boolean finalData);

        public final Object call(VirtualFrame frame, PNodeWithRaiseAndIndirectCall node, Object input, String encoding, String errors, boolean finalData) {
            return execute(frame, node, input, encoding, errors, finalData);
        }

        @Specialization(limit = "3")
        Object decode(VirtualFrame frame, PNodeWithRaiseAndIndirectCall node, Object input, String encoding, String errors, boolean finalData,
                        @CachedLibrary("input") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached HandleDecodingErrorNode errorHandler,
                        @Cached PRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory) {
            Object buffer = acquireLib.acquireReadonly(input, frame, node);
            try {
                int len = bufferLib.getBufferLength(buffer);
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                CodingErrorAction errorAction = convertCodingErrorAction(errors);
                Charset charset = CharsetMapping.getCharsetForDecoding(encoding, bytes, len);
                if (charset == null) {
                    throw raiseNode.raise(LookupError, ErrorMessages.UNKNOWN_ENCODING, encoding);
                }
                TruffleDecoder decoder;
                try {
                    decoder = new TruffleDecoder(CharsetMapping.normalize(encoding), charset, bytes, len, errorAction);
                    while (!decoder.decodingStep(finalData)) {
                        errorHandler.execute(decoder, errors, input);
                    }
                } catch (OutOfMemoryError e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(MemoryError);
                }
                return factory.createTuple(new Object[]{decoder.getString(), decoder.getInputPosition()});
            } finally {
                bufferLib.release(buffer);
            }
        }
    }

    @Builtin(name = "escape_decode", minNumOfPositionalArgs = 1, parameterNames = {"data", "errors"})
    @ArgumentClinic(name = "data", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"strict\"", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CodecsEscapeDecodeNode extends PythonBinaryClinicBuiltinNode {
        enum Errors {
            ERR_STRICT,
            ERR_IGNORE,
            ERR_REPLACE,
            ERR_UNKNOWN,
        }

        private static Errors getErrors(String err) {
            switch (err) {
                case STRICT:
                    return Errors.ERR_STRICT;
                case REPLACE:
                    return Errors.ERR_REPLACE;
                case IGNORE:
                    return Errors.ERR_IGNORE;
                default:
                    return Errors.ERR_UNKNOWN;
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CodecsEscapeDecodeNodeClinicProviderGen.INSTANCE;
        }

        public final Object execute(@SuppressWarnings("unused") VirtualFrame frame, byte[] bytes, String errors) {
            return decodeBytes(bytes, bytes.length, errors);
        }

        @Specialization(limit = "3")
        Object decode(VirtualFrame frame, Object buffer, String errors,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib) {
            try {
                int len = bufferLib.getBufferLength(buffer);
                return decodeBytes(bufferLib.getInternalOrCopiedByteArray(buffer), len, errors);
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        private Object decodeBytes(byte[] bytes, int len, String errors) {
            ByteArrayBuffer result = doDecode(bytes, len, errors);
            return factory().createTuple(new Object[]{factory().createBytes(result.getInternalBytes(), result.getLength()), len});
        }

        @TruffleBoundary
        private ByteArrayBuffer doDecode(byte[] bytes, int bytesLen, String errors) {
            Errors err = getErrors(errors);
            ByteArrayBuffer buffer = new ByteArrayBuffer(bytesLen);
            for (int i = 0; i < bytesLen; i++) {
                char chr = (char) bytes[i];
                if (chr != '\\') {
                    buffer.append(chr);
                    continue;
                }

                i++;
                if (i >= bytesLen) {
                    throw raise(ValueError, ErrorMessages.TRAILING_S_IN_STR, "\\");
                }

                chr = (char) bytes[i];
                switch (chr) {
                    case '\n':
                        break;
                    case '\\':
                        buffer.append('\\');
                        break;
                    case '\'':
                        buffer.append('\'');
                        break;
                    case '\"':
                        buffer.append('\"');
                        break;
                    case 'b':
                        buffer.append('\b');
                        break;
                    case 'f':
                        buffer.append('\014');
                        break; /* FF */
                    case 't':
                        buffer.append('\t');
                        break;
                    case 'n':
                        buffer.append('\n');
                        break;
                    case 'r':
                        buffer.append('\r');
                        break;
                    case 'v':
                        buffer.append('\013');
                        break; /* VT */
                    case 'a':
                        buffer.append('\007');
                        break; /* BEL */
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                        int code = chr - '0';
                        if (i + 1 < bytesLen) {
                            char nextChar = (char) bytes[i + 1];
                            if ('0' <= nextChar && nextChar <= '7') {
                                code = (code << 3) + nextChar - '0';
                                i++;

                                if (i + 1 < bytesLen) {
                                    nextChar = (char) bytes[i + 1];
                                    if ('0' <= nextChar && nextChar <= '7') {
                                        code = (code << 3) + nextChar - '0';
                                        i++;
                                    }
                                }
                            }
                        }
                        buffer.append((char) code);
                        break;
                    case 'x':
                        if (i + 2 < bytesLen) {
                            int digit1 = digitValue(bytes[i + 1]);
                            int digit2 = digitValue(bytes[i + 2]);
                            if (digit1 < 16 && digit2 < 16) {
                                buffer.append((digit1 << 4) + digit2);
                                i += 2;
                                break;
                            }
                        }
                        // invalid hexadecimal digits
                        if (err == Errors.ERR_STRICT) {
                            throw raise(ValueError, INVALID_ESCAPE_AT, "\\x", i - 2);
                        }
                        if (err == Errors.ERR_REPLACE) {
                            buffer.append('?');
                        } else if (err == Errors.ERR_IGNORE) {
                            // do nothing
                        } else {
                            throw raise(ValueError, ENCODING_ERROR_WITH_CODE, errors);
                        }

                        // skip \x
                        if (i + 1 < bytesLen && isHexDigit((char) bytes[i + 1])) {
                            i++;
                        }
                        break;

                    default:
                        buffer.append('\\');
                        buffer.append(chr);
                }
            }

            return buffer;
        }

        private static boolean isHexDigit(char digit) {
            return ('0' <= digit && digit <= '9') || ('a' <= digit && digit <= 'f') || ('A' <= digit && digit <= 'F');
        }
    }

    @Builtin(name = "escape_encode", minNumOfPositionalArgs = 1, parameterNames = {"data", "errors"})
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"strict\"", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CodecsEscapeEncodeNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CodecsEscapeEncodeNodeClinicProviderGen.INSTANCE;
        }

        @TruffleBoundary
        @Specialization
        Object encode(PBytes data, @SuppressWarnings("unused") String errors,
                        @Cached GetInternalByteArrayNode getInternalByteArrayNode) {
            byte[] bytes = getInternalByteArrayNode.execute(data.getSequenceStorage());
            int size = bytes.length;
            ByteArrayBuffer buffer = new ByteArrayBuffer();
            char c;
            for (byte aByte : bytes) {
                // There's at least enough room for a hex escape
                c = (char) aByte;
                if (c == '\'' || c == '\\') {
                    buffer.append('\\');
                    buffer.append(c);
                } else if (c == '\t') {
                    buffer.append('\\');
                    buffer.append('t');
                } else if (c == '\n') {
                    buffer.append('\\');
                    buffer.append('n');
                } else if (c == '\r') {
                    buffer.append('\\');
                    buffer.append('r');
                } else if (c < ' ' || c >= 0x7f) {
                    buffer.append('\\');
                    buffer.append('x');
                    buffer.append(HEXDIGITS[(c & 0xf0) >> 4]);
                    buffer.append(HEXDIGITS[c & 0xf]);
                } else {
                    buffer.append(c);
                }
            }

            return factory().createTuple(new Object[]{
                            factory().createBytes(buffer.getByteArray()),
                            size
            });
        }

        @Fallback
        Object encode(Object data, @SuppressWarnings("unused") Object errors) {
            throw raise(TypeError, BYTESLIKE_OBJ_REQUIRED, data);
        }
    }

    @Builtin(name = "__truffle_lookup__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CodecsLookupNode extends PythonBuiltinNode {
        @Specialization
        static Object lookup(String encoding) {
            if (hasTruffleEncoding(encoding)) {
                return true;
            } else {
                return PNone.NONE;
            }
        }
    }

    private static boolean hasTruffleEncoding(String encoding) {
        return CharsetMapping.getCharset(encoding) != null;
    }

    @Builtin(name = "lookup", minNumOfPositionalArgs = 1, parameterNames = {"encoding"})
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.String)
    @GenerateNodeFactory
    abstract static class LookupNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        PTuple lookup(VirtualFrame frame, String encoding,
                        @Cached InternalLookupNode internalNode) {
            return internalNode.execute(frame, encoding);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.LookupNodeClinicProviderGen.INSTANCE;
        }
    }

    @GenerateUncached
    abstract static class InternalLookupNode extends PNodeWithContext {
        abstract PTuple execute(Frame frame, String encoding);

        @Specialization
        PTuple lookup(VirtualFrame frame, String encoding,
                        @Cached CallUnaryMethodNode callNode,
                        @Cached PyObjectTypeCheck typeCheck,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached ConditionProfile hasSearchPathProfile,
                        @Cached ConditionProfile hasTruffleEncodingProfile,
                        @Cached ConditionProfile isTupleProfile,
                        @Cached PRaiseNode raiseNode) {
            String normalized_encoding = normalizeString(encoding);
            PythonContext context = getContext();
            PTuple result = getSearchPath(context, normalized_encoding);
            if (hasSearchPathProfile.profile(result != null)) {
                return result;
            }
            if (hasTruffleEncodingProfile.profile(hasTruffleEncoding(encoding))) {
                PythonModule codecs = context.lookupBuiltinModule("_codecs_truffle");
                result = CodecsTruffleModuleBuiltins.codecsInfo(codecs, encoding, context, context.factory());
            } else {
                for (Object func : getSearchPaths(context)) {
                    Object obj = callNode.executeObject(func, normalized_encoding);
                    if (obj != PNone.NONE) {
                        if (isTupleProfile.profile(!isTupleInstanceCheck(frame, obj, 4, typeCheck, sizeNode))) {
                            throw raiseNode.raise(TypeError, CODEC_SEARCH_MUST_RETURN_4);
                        }
                        result = (PTuple) obj;
                        break;
                    }
                }
            }
            if (result != null) {
                putSearchPath(context, normalized_encoding, result);
                return result;
            }
            throw raiseNode.raise(LookupError, UNKNOWN_ENCODING, encoding);
        }

        @TruffleBoundary
        private static void putSearchPath(PythonContext ctx, String key, PTuple value) {
            ctx.getCodecSearchCache().put(key, value);
        }

        @TruffleBoundary
        private static PTuple getSearchPath(PythonContext ctx, String key) {
            return ctx.getCodecSearchCache().get(key);
        }

        @TruffleBoundary
        private static Object[] getSearchPaths(PythonContext ctx) {
            List<Object> l = ctx.getCodecSearchPath();
            return ctx.getCodecSearchPath().toArray(new Object[l.size()]);
        }
    }

    private static boolean isTupleInstanceCheck(VirtualFrame frame, Object result, int len, PyObjectTypeCheck typeCheck, PyObjectSizeNode sizeNode) throws PException {
        return typeCheck.execute(result, PythonBuiltinClassType.PTuple) && sizeNode.execute(frame, result) == len;
    }

    @TruffleBoundary
    private static String normalizeString(String encoding) {
        // Copied from encodings.normalize_encoding + added lowercasing
        boolean punct = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < encoding.length(); i++) {
            char c = encoding.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.') {
                if (punct && sb.length() > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
                punct = false;
            } else {
                punct = true;
            }
        }
        return sb.toString();
    }

    @Builtin(name = "register", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class RegisterNode extends PythonBuiltinNode {
        @Specialization(guards = "callableCheckNode.execute(searchFunction)")
        Object lookup(Object searchFunction,
                        @SuppressWarnings("unused") @Cached PyCallableCheckNode callableCheckNode) {
            add(PythonContext.get(this), searchFunction);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!callableCheckNode.execute(searchFunction)")
        Object lookupNoCallble(Object searchFunction,
                        @Cached PyCallableCheckNode callableCheckNode) {
            throw raise(TypeError, ARG_MUST_BE_CALLABLE);
        }

        @TruffleBoundary
        private static void add(PythonContext context, Object searchFunction) {
            context.getCodecSearchPath().add(searchFunction);
        }
    }

    @Builtin(name = "_forget_codec", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ForgetCodecNode extends PythonBuiltinNode {
        @Specialization
        Object forget(VirtualFrame frame, PBytesLike encoding,
                        @Cached AsciiDecodeNode asciiDecodeNode) {
            forget((String) ((PTuple) asciiDecodeNode.execute(frame, encoding, PNone.NO_VALUE)).getSequenceStorage().getInternalArray()[0]);
            return PNone.NONE;
        }

        @Specialization
        @TruffleBoundary
        Object forget(String encoding) {
            PythonContext.get(this).getCodecSearchCache().remove(encoding);
            return PNone.NONE;
        }
    }

    @Builtin(name = "register_error", minNumOfPositionalArgs = 2, parameterNames = {"name", "handler"})
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.String)
    @GenerateNodeFactory
    abstract static class RegisterErrorNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.RegisterErrorNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "callableCheckNode.execute(handler)")
        Object register(String name, Object handler,
                        @SuppressWarnings("unused") @Cached PyCallableCheckNode callableCheckNode) {
            put(PythonContext.get(this), name, handler);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!callableCheckNode.execute(handler)")
        Object registerNoCallable(String name, Object handler,
                        @Cached PyCallableCheckNode callableCheckNode) {
            throw raise(TypeError, HANDLER_MUST_BE_CALLABLE);
        }

        @TruffleBoundary
        private static void put(PythonContext ctx, String name, Object handler) {
            ctx.getCodecErrorRegistry().put(name, handler);
        }
    }

    @Builtin(name = "lookup_error", minNumOfPositionalArgs = 1, parameterNames = {"name"})
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.String)
    @GenerateNodeFactory
    abstract static class LookupErrorNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.LookupErrorNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object lookup(String name,
                        @Cached ConditionProfile resultProfile) {
            Object result = get(PythonContext.get(this), name);
            if (resultProfile.profile(result == null)) {
                throw raise(LookupError, UNKNOWN_ERROR_HANDLER, name);
            }
            return result;
        }

        @TruffleBoundary
        private static Object get(PythonContext ctx, String name) {
            return ctx.getCodecErrorRegistry().get(name);
        }
    }

    @Builtin(name = ENCODE, minNumOfPositionalArgs = 1, parameterNames = {"obj", "encoding", "errors"})
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"utf-8\"")
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"strict\"")
    @GenerateNodeFactory
    public abstract static class EncodeNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.EncodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object encode(VirtualFrame frame, Object obj, String encoding, String errors,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached SequenceStorageNodes.GetItemNode getResultItemNode,
                        @Cached LookupNode lookupNode,
                        @Cached CallBinaryMethodNode callEncoderNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectTypeCheck typeCheck,
                        @Cached ConditionProfile isTupleProfile) {
            Object encoder = CodecsModuleBuiltins.encoder(frame, encoding, lookupNode, getItemNode);
            Object result = callEncoderNode.executeObject(encoder, obj, errors);
            if (isTupleProfile.profile(!isTupleInstanceCheck(frame, result, 2, typeCheck, sizeNode))) {
                throw raise(TypeError, S_MUST_RETURN_TUPLE, "encoder");
            }
            return getResultItemNode.execute(frame, ((PTuple) result).getSequenceStorage(), 0);
        }
    }

    @Builtin(name = DECODE, minNumOfPositionalArgs = 1, parameterNames = {"obj", "encoding", "errors"})
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"utf-8\"")
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"strict\"")
    @GenerateNodeFactory
    public abstract static class DecodeNode extends PythonTernaryClinicBuiltinNode {

        public abstract Object executeWithStrings(VirtualFrame frame, Object obj, String encoding, String errors);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.DecodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object decode(VirtualFrame frame, Object obj, String encoding, String errors,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached SequenceStorageNodes.GetItemNode getResultItemNode,
                        @Cached LookupNode lookupNode,
                        @Cached CallBinaryMethodNode callEncoderNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectTypeCheck typeCheck,
                        @Cached ConditionProfile isTupleProfile) {
            Object decoder = CodecsModuleBuiltins.decoder(frame, encoding, lookupNode, getItemNode);
            Object result = callEncoderNode.executeObject(decoder, obj, errors);
            if (isTupleProfile.profile(!isTupleInstanceCheck(frame, result, 2, typeCheck, sizeNode))) {
                throw raise(TypeError, S_MUST_RETURN_TUPLE, "decoder");
            }
            return getResultItemNode.execute(frame, ((PTuple) result).getSequenceStorage(), 0);
        }
    }

    private static Object codec_getItem(VirtualFrame frame, String encoding, int index, LookupNode lookupNode, SequenceStorageNodes.GetItemNode getItemNode) {
        PTuple t = (PTuple) lookupNode.execute(frame, encoding);
        return getItemNode.execute(frame, t.getSequenceStorage(), index);
    }

    private static Object encoder(VirtualFrame frame, String encoding, LookupNode lookupNode, SequenceStorageNodes.GetItemNode getItemNode) {
        return codec_getItem(frame, encoding, 0, lookupNode, getItemNode);
    }

    private static Object decoder(VirtualFrame frame, String encoding, LookupNode lookupNode, SequenceStorageNodes.GetItemNode getItemNode) {
        return codec_getItem(frame, encoding, 1, lookupNode, getItemNode);
    }

    @Builtin(name = "utf_8_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UTF8EncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, "utf-8", errors);
        }
    }

    @Builtin(name = "utf_8_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF8DecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, "utf-8", errors, ffinal);
        }
    }

    @Builtin(name = "utf_7_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UTF7EncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, "utf-7", errors);
        }
    }

    @Builtin(name = "utf_7_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF7DecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, "utf-7", errors, ffinal);
        }
    }

    @Builtin(name = "utf_16_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "byteorder"})
    @GenerateNodeFactory
    abstract static class UTF16EncodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, @SuppressWarnings("unused") Object byteorder,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, "utf-16", errors);
        }
    }

    @Builtin(name = "utf_16_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF16DecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, "utf-16", errors, ffinal);
        }
    }

    @Builtin(name = "utf_16_le_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UTF16LEEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, "utf-16-le", errors);
        }
    }

    @Builtin(name = "utf_16_le_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF16LEDecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, "utf-16-le", errors, ffinal);
        }
    }

    @Builtin(name = "utf_16_be_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UTF16BEEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, "utf-16-be", errors);
        }
    }

    @Builtin(name = "utf_16_be_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF16BEDecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, "utf-16-be", errors, ffinal);
        }
    }

    @Builtin(name = "utf_16_ex_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "byteorder", "final"})
    @GenerateNodeFactory
    abstract static class UTF16EXDecodeNode extends PythonQuaternaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object byteorder, Object ffinal) {
            throw raise(NotImplementedError, "utf_16_ex_decode");
        }
    }

    @Builtin(name = "utf_32_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "byteorder"})
    @GenerateNodeFactory
    abstract static class UTF32EncodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, @SuppressWarnings("unused") Object byteorder,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, "utf-32", errors);
        }
    }

    @Builtin(name = "utf_32_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF32DecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, "utf-32", errors, ffinal);
        }
    }

    @Builtin(name = "utf_32_le_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UTF32LEEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, "utf-32-le", errors);
        }
    }

    @Builtin(name = "utf_32_le_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF32LEDecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, "utf-32-le", errors, ffinal);
        }
    }

    @Builtin(name = "utf_32_be_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UTF32BEEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, "utf-32-be", errors);
        }
    }

    @Builtin(name = "utf_32_be_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF32BEDecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, "utf-32-be", errors, ffinal);
        }
    }

    @Builtin(name = "utf_32_ex_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final", "byteorder"})
    @GenerateNodeFactory
    abstract static class UTF32EXDecodeNode extends PythonQuaternaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object byteorder, Object ffinal) {
            throw raise(NotImplementedError, "utf_32_ex_decode");
        }
    }

    @Builtin(name = "unicode_internal_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UnicodeInternalEncodeNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors) {
            throw raise(NotImplementedError, "unicode_internal_encode");
        }
    }

    @Builtin(name = "unicode_internal_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UnicodeInternalDecodeNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors) {
            throw raise(NotImplementedError, "unicode_internal_decode");
        }
    }

    @Builtin(name = "raw_unicode_escape_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class RawUnicodeEscapeEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, "raw_unicode_escape", errors);
        }
    }

    @Builtin(name = "raw_unicode_escape_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class RawUnicodeEscapeDecodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, "raw_unicode_escape", errors, true);
        }
    }

    @Builtin(name = "unicode_escape_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    public abstract static class UnicodeEscapeEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, "unicode_escape", errors);
        }
    }

    @Builtin(name = "unicode_escape_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UnicodeEscapeDecodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, "unicode_escape", errors, true);
        }
    }

    @Builtin(name = "latin_1_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class Latin1EscapeEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, "latin_1", errors);
        }
    }

    @Builtin(name = "latin_1_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class Latin1EscapeDecodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, "latin_1", errors, true);
        }
    }

    @Builtin(name = "ascii_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class AsciiEscapeEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, "ascii", errors);
        }
    }

    @Builtin(name = "ascii_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class AsciiDecodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object decode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, "ascii", errors, true);
        }
    }

    @Builtin(name = "charmap_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "mapping"})
    @GenerateNodeFactory
    abstract static class CharmapEncodeNode extends PythonTernaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object encode(Object obj, Object errors, Object mapping) {
            throw raise(NotImplementedError, "charmap_encode");
        }
    }

    @Builtin(name = "charmap_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "mapping"})
    @GenerateNodeFactory
    abstract static class CharmapDecodeNode extends PythonTernaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object decode(Object obj, Object errors, Object mapping) {
            throw raise(NotImplementedError, "charmap_decode");
        }
    }

    @Builtin(name = "readbuffer_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class ReadbufferEncodeNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object encode(Object obj, Object errors) {
            throw raise(NotImplementedError, "readbuffer_encode");
        }
    }

    @Builtin(name = "mbcs_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class MBCSEncodeNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object encode(Object obj, Object errors) {
            throw raise(NotImplementedError, "mbcs_encode");
        }
    }

    @Builtin(name = "mbcs_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "ffinal"})
    @GenerateNodeFactory
    abstract static class MBCSDecodeNode extends PythonTernaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object decode(Object obj, Object errors, Object ffinal) {
            throw raise(NotImplementedError, "mbcs_decode");
        }
    }

    @Builtin(name = "oem_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class OEMEncodeNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object encode(Object obj, Object errors) {
            throw raise(NotImplementedError, "oem_encode");
        }
    }

    @Builtin(name = "oem_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "ffinal"})
    @GenerateNodeFactory
    abstract static class OEMDecodeNode extends PythonTernaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object decode(Object obj, Object errors, Object ffinal) {
            throw raise(NotImplementedError, "oem_decode");
        }
    }

    @Builtin(name = "code_page_encode", minNumOfPositionalArgs = 1, parameterNames = {"code_page", "string", "errors"})
    @GenerateNodeFactory
    abstract static class CodePageEncodeNode extends PythonTernaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object encode(Object code_page, Object string, Object errors) {
            throw raise(NotImplementedError, "code_page_encode");
        }
    }

    @Builtin(name = "code_page_decode", minNumOfPositionalArgs = 1, parameterNames = {"code_page", "string", "errors", "ffinal"})
    @GenerateNodeFactory
    abstract static class CodePageDecodeNode extends PythonQuaternaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object decode(Object code_page, Object obj, Object errors, Object ffinal) {
            throw raise(NotImplementedError, "code_page_decode");
        }
    }

    @Builtin(name = "charmap_build", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CharmapBuildNode extends PythonBuiltinNode {
        // This is replaced in the core _codecs.py with the full functionality
        @Specialization
        Object lookup(String chars,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage store = PDict.createNewStorage(false, chars.length());
            PDict dict = factory().createDict(store);
            int pos = 0;
            int num = 0;

            while (pos < chars.length()) {
                int charid = codePointAt(chars, pos);
                store = lib.setItem(store, charid, num);
                pos += charCount(charid);
                num++;
            }
            dict.setDictStorage(store);
            return dict;
        }

        @TruffleBoundary
        private static int charCount(int charid) {
            return Character.charCount(charid);
        }

        @TruffleBoundary
        private static int codePointAt(String chars, int pos) {
            return Character.codePointAt(chars, pos);
        }
    }

    static class TruffleEncoder {
        private final String encodingName;
        private final CharsetEncoder encoder;
        private CharBuffer inputBuffer;
        private ByteBuffer outputBuffer;
        private CoderResult coderResult;

        @TruffleBoundary
        public TruffleEncoder(String encodingName, Charset charset, String input, CodingErrorAction errorAction) {
            this.encodingName = encodingName;
            this.inputBuffer = CharBuffer.wrap(input);
            this.encoder = charset.newEncoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction);
            this.outputBuffer = ByteBuffer.allocate((int) (input.length() * encoder.averageBytesPerChar()));
        }

        @TruffleBoundary
        public boolean encodingStep() {
            while (true) {
                coderResult = encoder.encode(inputBuffer, outputBuffer, true);
                if (coderResult.isUnderflow()) {
                    coderResult = encoder.flush(outputBuffer);
                }

                if (coderResult.isUnderflow()) {
                    outputBuffer.flip();
                    return true;
                }

                if (coderResult.isOverflow()) {
                    grow();
                } else if (coderResult.isError()) {
                    return false;
                }
            }
        }

        private void grow() {
            int newCapacity = 2 * outputBuffer.capacity() + 1;
            // Overflow check - (2 * Integer.MAX_VALUE + 1 == -1) => overflown result cannot be
            // positive
            if (newCapacity < 0) {
                throw new OutOfMemoryError();
            }
            ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity);
            outputBuffer.flip();
            newBuffer.put(outputBuffer);
            outputBuffer = newBuffer;
        }

        @TruffleBoundary
        private String getErrorReason() {
            if (coderResult.isMalformed()) {
                return ErrorMessages.MALFORMED_INPUT;
            } else if (coderResult.isUnmappable()) {
                return ErrorMessages.UNMAPPABLE_CHARACTER;
            } else {
                throw new IllegalArgumentException("Unicode error constructed from non-error result");
            }
        }

        @TruffleBoundary
        public int getInputPosition() {
            return inputBuffer.position();
        }

        @TruffleBoundary
        public int getErrorLength() {
            return coderResult.length();
        }

        @TruffleBoundary
        public byte[] getBytes() {
            byte[] data = new byte[outputBuffer.remaining()];
            outputBuffer.get(data);
            return data;
        }

        @TruffleBoundary
        public int getInputRemaining() {
            return inputBuffer.remaining();
        }

        @TruffleBoundary
        public char[] getInputChars(int num) {
            char[] chars = new char[num];
            int pos = inputBuffer.position();
            inputBuffer.get(chars);
            inputBuffer.position(pos);
            return chars;
        }

        @TruffleBoundary
        public void replace(int skipInput, byte[] replacement, int offset, int length) {
            while (outputBuffer.remaining() < replacement.length) {
                grow();
            }
            outputBuffer.put(replacement, offset, length);
            inputBuffer.position(inputBuffer.position() + skipInput);
        }

        @TruffleBoundary
        public void replace(int skipInput, String replacement) {
            inputBuffer.position(inputBuffer.position() + skipInput);
            CharBuffer newBuffer = CharBuffer.allocate(inputBuffer.remaining() + replacement.length());
            newBuffer.put(replacement);
            newBuffer.put(inputBuffer);
            newBuffer.flip();
            inputBuffer = newBuffer;
        }

        public String getEncodingName() {
            return encodingName;
        }
    }

    static class TruffleDecoder {
        private final String encodingName;
        private final CharsetDecoder decoder;
        private final ByteBuffer inputBuffer;
        private CharBuffer outputBuffer;
        private CoderResult coderResult;

        @TruffleBoundary
        public TruffleDecoder(String encodingName, Charset charset, byte[] input, int inputLen, CodingErrorAction errorAction) {
            this.encodingName = encodingName;
            this.inputBuffer = ByteBuffer.wrap(input, 0, inputLen);
            this.decoder = charset.newDecoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction);
            this.outputBuffer = CharBuffer.allocate((int) (inputLen * decoder.averageCharsPerByte()));
        }

        @TruffleBoundary
        public boolean decodingStep(boolean finalData) {
            while (true) {
                coderResult = decoder.decode(inputBuffer, outputBuffer, finalData);
                if (finalData && coderResult.isUnderflow()) {
                    coderResult = decoder.flush(outputBuffer);
                }

                if (coderResult.isUnderflow()) {
                    outputBuffer.flip();
                    return true;
                }

                if (coderResult.isOverflow()) {
                    grow();
                } else if (coderResult.isError()) {
                    return false;
                }
            }
        }

        private void grow() {
            int newCapacity = 2 * outputBuffer.capacity() + 1;
            // Overflow check - (2 * Integer.MAX_VALUE + 1 == -1) => overflown result cannot be
            // positive
            if (newCapacity < 0) {
                throw new OutOfMemoryError();
            }
            CharBuffer newBuffer = CharBuffer.allocate(newCapacity);
            outputBuffer.flip();
            newBuffer.put(outputBuffer);
            outputBuffer = newBuffer;
        }

        @TruffleBoundary
        private String getErrorReason() {
            if (coderResult.isMalformed()) {
                return ErrorMessages.MALFORMED_INPUT;
            } else if (coderResult.isUnmappable()) {
                return ErrorMessages.UNMAPPABLE_CHARACTER;
            } else {
                throw new IllegalArgumentException("Unicode error constructed from non-error result");
            }
        }

        @TruffleBoundary
        public int getInputRemaining() {
            return inputBuffer.remaining();
        }

        @TruffleBoundary
        public byte[] getInputBytes(int num) {
            byte[] bytes = new byte[num];
            int pos = inputBuffer.position();
            inputBuffer.get(bytes);
            inputBuffer.position(pos);
            return bytes;
        }

        @TruffleBoundary
        public String getString() {
            return outputBuffer.toString();
        }

        @TruffleBoundary
        public int getInputPosition() {
            return inputBuffer.position();
        }

        @TruffleBoundary
        public int getErrorLength() {
            return coderResult.length();
        }

        public void replace(int skipInput, char[] chars) {
            replace(skipInput, chars, 0, chars.length);
        }

        @TruffleBoundary
        public void replace(int skipInput, char[] chars, int offset, int length) {
            while (outputBuffer.remaining() < chars.length) {
                grow();
            }
            outputBuffer.put(chars, offset, length);
            inputBuffer.position(inputBuffer.position() + skipInput);
        }

        public String getEncodingName() {
            return encodingName;
        }
    }
}
