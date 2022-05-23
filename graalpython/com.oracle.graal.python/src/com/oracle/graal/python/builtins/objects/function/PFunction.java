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
package com.oracle.graal.python.builtins.objects.function;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.CodeNodes.GetCodeCallTargetNode;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetCallTargetNode;
import com.oracle.graal.python.nodes.generator.GeneratorFunctionRootNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

@ExportLibrary(InteropLibrary.class)
public final class PFunction extends PythonObject {
    private String name;
    private String qualname;
    private final Assumption codeStableAssumption;
    private final Assumption defaultsStableAssumption;
    private final PythonObject globals;
    @CompilationFinal private boolean isBuiltin;
    @CompilationFinal(dimensions = 1) private final PCell[] closure;
    @CompilationFinal private PCode code;
    @CompilationFinal(dimensions = 1) private Object[] defaultValues;
    @CompilationFinal(dimensions = 1) private PKeyword[] kwDefaultValues;

    public PFunction(PythonLanguage lang, String name, String qualname, PCode code, PythonObject globals, PCell[] closure) {
        this(lang, name, qualname, code, globals, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS, closure);
    }

    public PFunction(PythonLanguage lang, String name, String qualname, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues, PCell[] closure) {
        this(lang, name, qualname, code, globals, defaultValues, kwDefaultValues, closure, Truffle.getRuntime().createAssumption(), Truffle.getRuntime().createAssumption());
    }

    public PFunction(PythonLanguage lang, String name, String qualname, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues, PCell[] closure,
                    Assumption codeStableAssumption, Assumption defaultsStableAssumption) {
        super(PythonBuiltinClassType.PFunction, PythonBuiltinClassType.PFunction.getInstanceShape(lang));
        this.name = name;
        this.qualname = qualname;
        assert code != null;
        this.code = code;
        this.globals = globals;
        this.defaultValues = defaultValues == null ? PythonUtils.EMPTY_OBJECT_ARRAY : defaultValues;
        this.kwDefaultValues = kwDefaultValues == null ? PKeyword.EMPTY_KEYWORDS : kwDefaultValues;
        this.closure = closure;
        this.codeStableAssumption = codeStableAssumption;
        this.defaultsStableAssumption = defaultsStableAssumption;
    }

    public Assumption getCodeStableAssumption() {
        return codeStableAssumption;
    }

    public Assumption getDefaultsStableAssumption() {
        return defaultsStableAssumption;
    }

    public PythonObject getGlobals() {
        return globals;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQualname() {
        return this.qualname;
    }

    public void setQualname(String qualname) {
        this.qualname = qualname;
    }

    public PCell[] getClosure() {
        return closure;
    }

    public boolean isBuiltin() {
        return isBuiltin;
    }

    public void setBuiltin(boolean builtin) {
        isBuiltin = builtin;
    }

    @Override
    public final String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("PFunction %s at 0x%x", getQualname(), hashCode());
    }

    public PCode getCode() {
        return code;
    }

    @TruffleBoundary
    public void setCode(PCode code) {
        codeStableAssumption.invalidate("code changed for function " + getName());
        assert code != null : "code cannot be null";
        this.code = code;
    }

    public Object[] getDefaults() {
        return defaultValues;
    }

    @TruffleBoundary
    public void setDefaults(Object[] defaults) {
        this.defaultsStableAssumption.invalidate("defaults changed for function " + getName());
        this.defaultValues = defaults;
    }

    public PKeyword[] getKwDefaults() {
        return kwDefaultValues;
    }

    @TruffleBoundary
    public void setKwDefaults(PKeyword[] defaults) {
        this.defaultsStableAssumption.invalidate("kw defaults changed for function " + getName());
        this.kwDefaultValues = defaults;
    }

    @TruffleBoundary
    String getSourceCode() {
        RootNode rootNode = GetCallTargetNode.getUncached().execute(this).getRootNode();
        if (rootNode instanceof GeneratorFunctionRootNode) {
            rootNode = ((GeneratorFunctionRootNode) rootNode).getFunctionRootNode();
        }
        SourceSection sourceSection = rootNode.getSourceSection();
        if (sourceSection != null) {
            return sourceSection.getCharacters().toString();
        }
        return null;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasExecutableName() {
        return true;
    }

    @ExportMessage
    String getExecutableName(@Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return getName();
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public SourceSection getSourceLocation(
                    @Shared("getCt") @Cached GetCodeCallTargetNode getCt,
                    @Shared("gil") @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            SourceSection result = getSourceLocationDirect(getCt);
            if (result == null) {
                throw UnsupportedMessageException.create();
            } else {
                return result;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @TruffleBoundary
    private SourceSection getSourceLocationDirect(GetCodeCallTargetNode getCt) {
        RootNode rootNode = getCt.execute(code).getRootNode();
        SourceSection result;
        if (rootNode instanceof PRootNode) {
            result = ((PRootNode) rootNode).getSourceSection();
        } else {
            result = getForeignSourceSection(rootNode);
        }
        return result;
    }

    @TruffleBoundary
    private static SourceSection getForeignSourceSection(RootNode rootNode) {
        return rootNode.getSourceSection();
    }

    @ExportMessage
    public boolean hasSourceLocation(
                    @Shared("getCt") @Cached GetCodeCallTargetNode getCt,
                    @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return getSourceLocationDirect(getCt) != null;
        } finally {
            gil.release(mustRelease);
        }
    }
}
