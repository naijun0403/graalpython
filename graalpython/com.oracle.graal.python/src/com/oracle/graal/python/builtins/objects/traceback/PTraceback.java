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
package com.oracle.graal.python.builtins.objects.traceback;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public final class PTraceback extends PythonBuiltinObject {
    public static final int UNKNOWN_LINE_NUMBER = -2;

    private PFrame frame;
    private PFrame.Reference frameInfo;
    private int lineno = UNKNOWN_LINE_NUMBER;
    private int lasti;
    private PTraceback next;
    private LazyTraceback lazyTraceback;

    public PTraceback(PythonLanguage lang, PFrame frame, int lineno, PTraceback next) {
        this(lang, frame, lineno, -1, next);
    }

    public PTraceback(PythonLanguage lang, PFrame frame, int lineno, int lasti, PTraceback next) {
        super(PythonBuiltinClassType.PTraceback, PythonBuiltinClassType.PTraceback.getInstanceShape(lang));
        this.frame = frame;
        this.lineno = lineno;
        this.lasti = lasti;
        this.next = next;
    }

    public PTraceback(PythonLanguage lang, LazyTraceback lazyTraceback) {
        super(PythonBuiltinClassType.PTraceback, PythonBuiltinClassType.PTraceback.getInstanceShape(lang));
        this.lazyTraceback = lazyTraceback;
        this.frameInfo = lazyTraceback.getFrameInfo();
        this.frame = lazyTraceback.getFrame();
        this.lineno = lazyTraceback.getLineNo();
    }

    public PFrame getFrame() {
        return frame;
    }

    public void setFrame(PFrame frame) {
        this.frame = frame;
    }

    public PFrame.Reference getFrameInfo() {
        return frameInfo;
    }

    public int getLineno() {
        return lineno;
    }

    public int getLasti() {
        return lasti;
    }

    public LazyTraceback getLazyTraceback() {
        return lazyTraceback;
    }

    public PTraceback getNext() {
        return next;
    }

    public void setNext(PTraceback next) {
        this.next = next;
    }

    public void setLineno(int lineno) {
        this.lineno = lineno;
    }

    public void markMaterialized() {
        this.lazyTraceback = null;
    }

    public boolean isMaterialized() {
        return lazyTraceback == null;
    }

    public static final String TB_FRAME = "tb_frame";
    public static final String TB_NEXT = "tb_next";
    public static final String TB_LASTI = "tb_lasti";
    public static final String TB_LINENO = "tb_lineno";

    @CompilationFinal(dimensions = 1) private static final Object[] TB_DIR_FIELDS = new Object[]{TB_FRAME, TB_NEXT, TB_LASTI, TB_LINENO};

    static Object[] getTbFieldNames() {
        return TB_DIR_FIELDS.clone();
    }
}
