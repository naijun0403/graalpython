/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FILE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

@GenerateUncached
public abstract class ImportFromNode extends PNodeWithContext {
    public abstract Object execute(Frame frame, Object module, String name);

    @Specialization
    Object doImport(VirtualFrame frame, Object module, String name,
                    @Cached PyObjectLookupAttr lookupAttr,
                    @Cached BranchProfile maybeCircularProfile) {
        Object result = lookupAttr.execute(frame, module, name);
        if (result != PNone.NO_VALUE) {
            return result;
        }
        maybeCircularProfile.enter();
        return tryResolveCircularImport(module, name);
    }

    @TruffleBoundary
    private Object tryResolveCircularImport(Object module, String name) {
        Object pkgnameObj;
        Object pkgpathObj = null;
        String pkgname = "<unknown module name>";
        String pkgpath = "unknown location";
        try {
            pkgnameObj = PyObjectGetAttr.getUncached().execute(null, module, __NAME__);
            pkgname = CastToJavaStringNode.getUncached().execute(pkgnameObj);
        } catch (PException | CannotCastException e) {
            pkgnameObj = null;
        }
        if (pkgnameObj != null) {
            String fullname = pkgname + "." + name;
            Object imported = PyDictGetItem.getUncached().execute(null, getContext().getSysModules(), fullname);
            if (imported != null) {
                return imported;
            }
            try {
                pkgpathObj = PyObjectGetAttr.getUncached().execute(null, module, __FILE__);
                pkgpath = CastToJavaStringNode.getUncached().execute(pkgpathObj);
            } catch (PException | CannotCastException e) {
                pkgpathObj = null;
            }
        }
        if (pkgnameObj == null) {
            pkgnameObj = PNone.NONE;
        }
        if (pkgpathObj != null && AbstractImportNode.PyModuleIsInitializing.getUncached().execute(null, module)) {
            throw PConstructAndRaiseNode.getUncached().raiseImportError(null, pkgnameObj, pkgpathObj, ErrorMessages.CANNOT_IMPORT_NAME_CIRCULAR, name, pkgname, pkgpath);
        } else {
            if (pkgpathObj == null) {
                pkgnameObj = PNone.NONE;
            }
            throw PConstructAndRaiseNode.getUncached().raiseImportError(null, pkgnameObj, pkgpathObj, ErrorMessages.CANNOT_IMPORT_NAME, name, pkgname, pkgpath);
        }
    }

    public static ImportFromNode create() {
        return ImportFromNodeGen.create();
    }

    public static ImportFromNode getUncached() {
        return ImportFromNodeGen.getUncached();
    }
}
