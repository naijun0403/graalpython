/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.object;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.expression.BinaryOp;
import com.oracle.graal.python.nodes.generator.GeneratorFunctionRootNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@ImportStatic(PythonOptions.class)
@GenerateUncached
public abstract class IsNode extends Node implements BinaryOp {

    protected abstract boolean executeInternal(Object left, Object right);

    protected abstract boolean executeInternal(boolean left, Object right);

    @Override
    public final Object executeObject(VirtualFrame frame, Object left, Object right) {
        return execute(left, right);
    }

    public final boolean execute(Object left, Object right) {
        return left == right || executeInternal(left, right);
    }

    public boolean isTrue(Object object) {
        return executeInternal(true, object);
    }

    public boolean isFalse(Object object) {
        return executeInternal(false, object);
    }

    // Primitives
    @Specialization
    static boolean doBB(boolean left, boolean right) {
        return left == right;
    }

    @Specialization
    boolean doBP(boolean left, PInt right) {
        Python3Core core = PythonContext.get(this);
        if (left) {
            return right == core.getTrue();
        } else {
            return right == core.getFalse();
        }
    }

    @Specialization
    static boolean doII(int left, int right) {
        return left == right;
    }

    @Specialization
    static boolean doIL(int left, long right) {
        return left == right;
    }

    @Specialization
    static boolean doIP(int left, PInt right,
                    @Cached.Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
        if (isBuiltin.profileIsAnyBuiltinObject(right)) {
            try {
                return right.intValueExact() == left;
            } catch (OverflowException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    @Specialization
    static boolean doLI(long left, int right) {
        return left == right;
    }

    @Specialization
    static boolean doLL(long left, long right) {
        return left == right;
    }

    @Specialization
    static boolean doLP(long left, PInt right,
                    @Cached.Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
        if (isBuiltin.profileIsAnyBuiltinObject(right)) {
            try {
                return left == right.longValueExact();
            } catch (OverflowException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    @Specialization
    static boolean doDD(double left, double right) {
        // n.b. we simulate that the primitive NaN is a singleton; this is required to make
        // 'nan = float("nan"); nan is nan' work
        return left == right || (Double.isNaN(left) && Double.isNaN(right));
    }

    @Specialization
    boolean doPB(PInt left, boolean right) {
        return doBP(right, left);
    }

    @Specialization
    static boolean doPI(PInt left, int right,
                    @Cached.Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
        return doIP(right, left, isBuiltin);
    }

    @Specialization
    static boolean doPL(PInt left, long right,
                    @Cached.Shared("isBuiltin") @Cached IsBuiltinClassProfile isBuiltin) {
        return doLP(right, left, isBuiltin);
    }

    // types
    @Specialization
    static boolean doCT(PythonBuiltinClass left, PythonBuiltinClassType right) {
        return left.getType() == right;
    }

    @Specialization
    static boolean doTC(PythonBuiltinClassType left, PythonBuiltinClass right) {
        return right.getType() == left;
    }

    // native objects
    @Specialization
    static boolean doNative(PythonAbstractNativeObject left, PythonAbstractNativeObject right,
                    @Cached CExtNodes.PointerCompareNode isNode) {
        return isNode.execute(__EQ__, left, right);
    }

    // code
    @Specialization
    static boolean doCode(PCode left, PCode right,
                    @Cached CodeNodes.GetCodeCallTargetNode getCt) {
        // Special case for code objects: Frames create them on-demand even if they refer to the
        // same function. So we need to compare the root nodes.
        if (left != right) {
            RootCallTarget leftCt = getCt.execute(left);
            RootCallTarget rightCt = getCt.execute(right);
            if (leftCt != null && rightCt != null) {
                // TODO: handle splitting, i.e., cloned root nodes
                RootNode leftRootNode = leftCt.getRootNode();
                RootNode rightRootNode = rightCt.getRootNode();
                if (leftRootNode instanceof GeneratorFunctionRootNode) {
                    leftRootNode = ((GeneratorFunctionRootNode) leftRootNode).getFunctionRootNode();
                }
                if (rightRootNode instanceof GeneratorFunctionRootNode) {
                    rightRootNode = ((GeneratorFunctionRootNode) rightRootNode).getFunctionRootNode();
                }
                return leftRootNode == rightRootNode;
            } else {
                return false;
            }
        }
        return true;
    }

    // none
    @Specialization
    boolean doObjectPNone(Object left, PNone right) {
        TruffleLanguage.Env env = PythonContext.get(this).getEnv();
        if (PythonLanguage.get(this).getEngineOption(PythonOptions.EmulateJython) && env.isHostObject(left) && env.asHostObject(left) == null &&
                        right == PNone.NONE) {
            return true;
        }
        return left == right;
    }

    @Specialization
    boolean doPNoneObject(PNone left, Object right) {
        return doObjectPNone(right, left);
    }

    // pstring (may be interned)
    @Specialization
    static boolean doPString(PString left, PString right,
                    @Cached StringNodes.StringMaterializeNode materializeNode,
                    @Cached StringNodes.IsInternedStringNode isInternedStringNode) {
        if (isInternedStringNode.execute(left) && isInternedStringNode.execute(right)) {
            return materializeNode.execute(left).equals(materializeNode.execute(right));
        }
        return left == right;
    }

    // everything else
    @Fallback
    static boolean doOther(Object left, Object right,
                    @Cached IsForeignObjectNode isForeignObjectNode,
                    @CachedLibrary(limit = "3") InteropLibrary lib) {
        if (left == right) {
            return true;
        }
        if (isForeignObjectNode.execute(left)) {
            return lib.isIdentical(left, right, lib);
        }
        return false;
    }

    public static IsNode create() {
        return IsNodeGen.create();
    }

    public static IsNode getUncached() {
        return IsNodeGen.getUncached();
    }
}
