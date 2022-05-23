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
package com.oracle.graal.python.nodes.subscript;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.slice.PIntSlice;
import com.oracle.graal.python.builtins.objects.slice.PObjectSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNodeGen.CastToSliceComponentNodeGen;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNodeGen.CoerceToIntSliceFactory;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNodeGen.CoerceToObjectSliceFactory;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNodeGen.ComputeIndicesFactory;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

@NodeChild(value = "first", type = ExpressionNode.class)
@NodeChild(value = "second", type = ExpressionNode.class)
@NodeChild(value = "third", type = ExpressionNode.class)
@TypeSystemReference(PythonArithmeticTypes.class)
public abstract class SliceLiteralNode extends ExpressionNode {
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();

    @Specialization
    public PSlice doInt(int start, int stop, int step) {
        return factory.createIntSlice(start, stop, step);
    }

    @Specialization(guards = "isNoValue(step)")
    @SuppressWarnings("unused")
    public PSlice doInt(int start, int stop, PNone step) {
        return factory.createIntSlice(start, stop, 1, false, true);
    }

    @Specialization
    @SuppressWarnings("unused")
    public PSlice doInt(PNone start, int stop, int step) {
        return factory.createIntSlice(0, stop, step, true, false);
    }

    @Specialization(guards = {"isNoValue(second)", "isNoValue(third)"})
    @SuppressWarnings("unused")
    public PSlice sliceStop(int first, PNone second, PNone third) {
        return factory.createIntSlice(0, first, 1, true, true);
    }

    @Specialization(guards = {"!isNoValue(stop)", "!isNoValue(step)"})
    public PSlice doGeneric(Object start, Object stop, Object step) {
        return factory.createObjectSlice(start, stop, step);
    }

    public abstract PNode getFirst();

    public abstract PNode getSecond();

    public abstract PNode getThird();

    public static SliceLiteralNode create(ExpressionNode lower, ExpressionNode upper, ExpressionNode step) {
        return SliceLiteralNodeGen.create(lower, upper, step);
    }

    public static SliceLiteralNode create() {
        return SliceLiteralNodeGen.create(null, null, null);
    }

    /**
     * Adopting logic from PySlice_AdjustIndices (sliceobject.c:248)
     */
    @GenerateNodeFactory
    @GenerateUncached
    public abstract static class AdjustIndices extends PNodeWithContext {

        public abstract SliceInfo execute(int length, SliceInfo slice);

        @Specialization
        SliceInfo calc(int length, SliceInfo slice,
                        @Cached PRaiseNode raiseNode) {
            int start = slice.start;
            int stop = slice.stop;
            int step = slice.step;

            if (step == 0) {
                raiseNode.raise(ValueError, ErrorMessages.SLICE_STEP_CANNOT_BE_ZERO);
            }
            assert step > Integer.MIN_VALUE : "step must not be minimum integer value";

            int len = 0;
            if (start < 0) {
                start += length;
                if (start < 0) {
                    start = (step < 0) ? -1 : 0;
                }
            } else if (start >= length) {
                start = (step < 0) ? length - 1 : length;
            }

            if (stop < 0) {
                stop += length;
                if (stop < 0) {
                    stop = (step < 0) ? -1 : 0;
                }
            } else if (stop >= length) {
                stop = (step < 0) ? length - 1 : length;
            }

            if (step < 0) {
                if (stop < start) {
                    len = (start - stop - 1) / (-step) + 1;
                }
            } else {
                if (start < stop) {
                    len = (stop - start - 1) / step + 1;
                }
            }
            return new SliceInfo(start, stop, step, len);
        }
    }

    /**
     * Coerce indices computation to lossy integer values
     */
    @GenerateNodeFactory
    @GenerateUncached
    public abstract static class ComputeIndices extends PNodeWithContext {

        public abstract SliceInfo execute(Frame frame, PSlice slice, int i);

        @Specialization(guards = "length >= 0")
        SliceInfo doSliceInt(PIntSlice slice, int length) {
            return slice.computeIndices(length);
        }

        @Specialization(guards = "length >= 0")
        SliceInfo doSliceObject(VirtualFrame frame, PObjectSlice slice, int length,
                        @Cached SliceExactCastToInt castStartNode,
                        @Cached SliceExactCastToInt castStopNode,
                        @Cached SliceExactCastToInt castStepNode) {
            Object startIn = castStartNode.execute(frame, slice.getStart());
            Object stopIn = castStopNode.execute(frame, slice.getStop());
            Object stepIn = castStepNode.execute(frame, slice.getStep());
            return PObjectSlice.computeIndices(startIn, stopIn, stepIn, length);
        }

        @Specialization(guards = "length < 0")
        SliceInfo doSliceInt(@SuppressWarnings("unused") PSlice slice, @SuppressWarnings("unused") int length,
                        @Cached PRaiseNode raise) {
            throw raise.raise(ValueError, ErrorMessages.LENGTH_SHOULD_NOT_BE_NEG);
        }

        public static ComputeIndices create() {
            return ComputeIndicesFactory.create();
        }
    }

    /**
     * This is only applicable to slow path <i><b>internal</b></i> computations.
     */
    @GenerateNodeFactory
    @GenerateUncached
    public abstract static class CoerceToObjectSlice extends PNodeWithContext {

        public abstract PObjectSlice execute(PSlice slice);

        @Specialization
        PObjectSlice doSliceInt(PIntSlice slice,
                        @Cached SliceCastToToBigInt start,
                        @Cached SliceCastToToBigInt stop,
                        @Cached SliceCastToToBigInt step,
                        @Cached PythonObjectFactory factory) {
            return factory.createObjectSlice(start.execute(slice.getStart()), stop.execute(slice.getStop()), step.execute(slice.getStep()));
        }

        protected static boolean isBigInt(PObjectSlice slice) {
            return slice.getStart() instanceof BigInteger && slice.getStop() instanceof BigInteger && slice.getStep() instanceof BigInteger;
        }

        @Specialization(guards = "isBigInt(slice)")
        PObjectSlice doSliceObject(PObjectSlice slice) {
            return slice;
        }

        @Specialization
        PObjectSlice doSliceObject(PObjectSlice slice,
                        @Cached SliceCastToToBigInt start,
                        @Cached SliceCastToToBigInt stop,
                        @Cached SliceCastToToBigInt step,
                        @Cached PythonObjectFactory factory) {
            return factory.createObjectSlice(start.execute(slice.getStart()), stop.execute(slice.getStop()), step.execute(slice.getStep()));
        }

        public static CoerceToObjectSlice create() {
            return CoerceToObjectSliceFactory.create();
        }
    }

    /**
     * This is only applicable to slow path <i><b>internal</b></i> computations.
     */
    @GenerateNodeFactory
    @GenerateUncached
    public abstract static class CoerceToIntSlice extends PNodeWithContext {

        public abstract PSlice execute(PSlice slice);

        @Specialization
        PSlice doSliceInt(PIntSlice slice) {
            return slice;
        }

        @Specialization
        PSlice doSliceObject(PObjectSlice slice,
                        @Cached SliceLossyCastToInt start,
                        @Cached SliceLossyCastToInt stop,
                        @Cached SliceLossyCastToInt step,
                        @Cached PythonObjectFactory factory) {
            return factory.createObjectSlice(start.execute(slice.getStart()), stop.execute(slice.getStop()), step.execute(slice.getStep()));
        }

        public static CoerceToIntSlice create() {
            return CoerceToIntSliceFactory.create();
        }
    }

    @GenerateNodeFactory
    @GenerateUncached
    public abstract static class SliceUnpack extends PNodeWithContext {

        public abstract SliceInfo execute(PSlice slice);

        @Specialization
        SliceInfo doSliceInt(PIntSlice slice) {
            return new SliceInfo(slice.getIntStart(), slice.getIntStop(), slice.getIntStep());
        }

        @Specialization
        SliceInfo doSliceObject(PObjectSlice slice,
                        @Cached SliceLossyCastToInt toInt,
                        @Cached PRaiseNode raiseNode) {
            /* this is harder to get right than you might think */
            int start, stop, step;
            if (slice.getStep() == PNone.NONE) {
                step = 1;
            } else {
                step = (int) toInt.execute(slice.getStep());
                if (step == 0) {
                    raiseNode.raise(ValueError, ErrorMessages.SLICE_STEP_CANNOT_BE_ZERO);
                }
            }

            if (slice.getStart() == PNone.NONE) {
                start = step < 0 ? Integer.MAX_VALUE : 0;
            } else {
                start = (int) toInt.execute(slice.getStart());
            }

            if (slice.getStop() == PNone.NONE) {
                stop = step < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            } else {
                stop = (int) toInt.execute(slice.getStop());
            }

            return new SliceInfo(start, stop, step);
        }
    }

    @GenerateNodeFactory
    @GenerateUncached
    @ImportStatic({PythonOptions.class, PGuards.class})
    public abstract static class SliceCastToToBigInt extends Node {

        public abstract Object execute(Object x);

        @Specialization
        protected Object doNone(@SuppressWarnings("unused") PNone i) {
            return PNone.NONE;
        }

        @Specialization(guards = "!isPNone(i)")
        protected Object doGeneric(Object i,
                        @Cached BranchProfile exceptionProfile,
                        @Cached PRaiseNode raise,
                        @Cached CastToJavaBigIntegerNode cast) {
            try {
                return cast.execute(i);
            } catch (PException e) {
                exceptionProfile.enter();
                throw raise.raise(TypeError, ErrorMessages.SLICE_INDICES_MUST_BE_INT_NONE_HAVE_INDEX);
            }
        }
    }

    @GenerateNodeFactory
    @GenerateUncached
    @ImportStatic({PythonOptions.class, PGuards.class})
    public abstract static class SliceExactCastToInt extends Node {

        public abstract Object execute(Frame frame, Object x);

        @Specialization
        protected Object doNone(@SuppressWarnings("unused") PNone i) {
            return PNone.NONE;
        }

        @Specialization(guards = "!isPNone(i)")
        protected Object doGeneric(Object i,
                        @Cached PRaiseNode raise,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            if (indexCheckNode.execute(i)) {
                return asSizeNode.executeExact(null, i);
            }
            throw raise.raise(TypeError, ErrorMessages.SLICE_INDICES_MUST_BE_INT_NONE_HAVE_INDEX);
        }
    }

    @GenerateNodeFactory
    @GenerateUncached
    @ImportStatic({PythonOptions.class, PGuards.class})
    public abstract static class SliceLossyCastToInt extends Node {

        public abstract Object execute(Object x);

        @Specialization
        protected Object doNone(@SuppressWarnings("unused") PNone i) {
            return PNone.NONE;
        }

        @Specialization(guards = "!isPNone(i)")
        protected Object doGeneric(Object i,
                        @Cached BranchProfile exceptionProfile,
                        @Cached PRaiseNode raise,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            if (indexCheckNode.execute(i)) {
                return asSizeNode.executeLossy(null, i);
            }
            exceptionProfile.enter();
            throw raise.raise(TypeError, ErrorMessages.SLICE_INDICES_MUST_BE_INT_NONE_HAVE_INDEX);

        }
    }

    @ImportStatic({PythonOptions.class, PGuards.class})
    public abstract static class CastToSliceComponentNode extends PNodeWithContext {
        private final int defaultValue;
        private final int overflowValue;

        public CastToSliceComponentNode(int defaultValue, int overflowValue) {
            this.defaultValue = defaultValue;
            this.overflowValue = overflowValue;
        }

        public abstract int execute(VirtualFrame frame, int i);

        public abstract int execute(VirtualFrame frame, long i);

        public abstract int execute(VirtualFrame frame, Object i);

        @Specialization
        int doNone(@SuppressWarnings("unused") PNone i) {
            return defaultValue;
        }

        @Specialization
        int doBoolean(boolean i) {
            return PInt.intValue(i);
        }

        @Specialization
        int doInt(int i) {
            return i;
        }

        @Specialization
        int doLong(long i,
                        @Shared("indexErrorProfile") @Cached BranchProfile indexErrorProfile) {
            try {
                return PInt.intValueExact(i);
            } catch (OverflowException e) {
                indexErrorProfile.enter();
                return overflowValue;
            }
        }

        @Specialization
        int doPInt(PInt i,
                        @Shared("indexErrorProfile") @Cached BranchProfile indexErrorProfile) {
            try {
                return i.intValueExact();
            } catch (OverflowException e) {
                indexErrorProfile.enter();
                return overflowValue;
            }
        }

        @Specialization(guards = "!isPNone(i)", replaces = {"doBoolean", "doInt", "doLong", "doPInt"})
        int doGeneric(VirtualFrame frame, Object i,
                        @Cached PRaiseNode raise,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            if (indexCheckNode.execute(i)) {
                try {
                    return asSizeNode.executeExact(frame, i);
                } catch (PException e) {
                    e.expect(PythonBuiltinClassType.OverflowError, errorProfile);
                    return overflowValue;
                }
            } else {
                throw raise.raise(TypeError, ErrorMessages.SLICE_INDICES_MUST_BE_INT_NONE_HAVE_INDEX);
            }
        }

        public static CastToSliceComponentNode create(int defaultValue, int overflowValue) {
            return CastToSliceComponentNodeGen.create(defaultValue, overflowValue);
        }
    }
}
