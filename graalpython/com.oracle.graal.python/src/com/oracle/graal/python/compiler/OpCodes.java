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
package com.oracle.graal.python.compiler;

import java.math.BigInteger;

import com.oracle.graal.python.annotations.GenerateEnumConstants;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.runtime.exception.PException;

/**
 * Operation codes of our bytecode interpreter. They are similar to CPython's, but not the same. Our
 * opcodes can have multiple bytes of immediate operands. The first operand can be variably extended
 * using {@link #EXTENDED_ARG} instruction.
 */
@GenerateEnumConstants
public enum OpCodes {
    /** Pop a single item from the stack */
    POP_TOP(0, 1, 0),
    /** Exchange two top stack items */
    ROT_TWO(0, 2, 2),
    /** Exchange three top stack items. [a, b, c] (a is top) becomes [b, c, a] */
    ROT_THREE(0, 3, 3),
    /** Duplicates the top stack item */
    DUP_TOP(0, 1, 2),
    /** Does nothing. Might still be useful to maintain a line number */
    NOP(0, 0, 0),
    /**
     * Performs a unary operation specified by the immediate operand. It has to be the ordinal of
     * one of {@link UnaryOps} constants.
     * 
     * Pops: operand
     * 
     * Pushes: result
     */
    UNARY_OP(1, 1, 1),
    /**
     * Performs a binary operation specified by the immediate operand. It has to be the ordinal of
     * one of {@link BinaryOps} constants.
     * 
     * Pops: right operand, then left operand
     * 
     * Pushes: result
     */
    BINARY_OP(1, 2, 1),
    /**
     * Performs subscript get operation - {@code a[b]}.
     * 
     * Pops: {@code b}, then {@code a}
     * 
     * Pushes: result
     */
    BINARY_SUBSCR(0, 2, 1),
    /**
     * Performs subscript set operation - {@code a[b] = c}.
     * 
     * Pops: {@code c}, then {@code b}, then {@code a}
     */
    STORE_SUBSCR(0, 3, 0),
    /**
     * Performs subscript delete operation - {@code del a[b]}.
     * 
     * Pops: {@code b}, then {@code a}
     */
    DELETE_SUBSCR(0, 2, 0),
    /**
     * Gets an iterator of an object
     * 
     * Pops: object
     * 
     * Pushes: iterator
     */
    GET_ITER(0, 1, 1),
    /**
     * Gets an awaitable of an object
     *
     * Pops: object
     *
     * Pushes: awaitable
     */
    GET_AWAITABLE(0, 1, 1),
    /**
     * Pushes: {@code __build_class__} builtin
     */
    LOAD_BUILD_CLASS(0, 0, 1),
    /**
     * Pushes: {@code AssertionError} builtin exception type
     */
    LOAD_ASSERTION_ERROR(0, 0, 1),
    /**
     * Returns the value to the caller. In generators, performs generator return.
     *
     * Pops: return value
     */
    RETURN_VALUE(0, 1, 0),
    /**
     * Reads a name from locals dict, globals or builtins determined by the immediate operand which
     * indexes the names array ({@code co_names}).
     *
     * Pushes: read object
     */
    LOAD_NAME(1, 0, 1),
    /**
     * Writes the stack top into a name in locals dict or globals determined by the immediate
     * operand which indexes the names array ({@code co_names}).
     * 
     * Pops: object to be written
     */
    STORE_NAME(1, 1, 0),
    /**
     * Deletes the name in locals dict or globals determined by the immediate operand which indexes
     * the names array ({@code co_names}).
     */
    DELETE_NAME(1, 0, 0),
    /**
     * Reads an attribute - {@code a.b}. {@code b} is determined by the immediate operand which
     * indexes the names array ({@code co_names}).
     *
     * Pops: {@code a}
     * 
     * Pushes: read attribute
     */
    LOAD_ATTR(1, 1, 1),
    /**
     * Writes an attribute - {@code a.b = c}. {@code b} is determined by the immediate operand which
     * indexes the names array ({@code co_names}).
     *
     * Pops: {@code c}, then {@code a}
     */
    STORE_ATTR(1, 2, 0),
    /**
     * Deletes an attribute - {@code del a.b}. {@code b} is determined by the immediate operand
     * which indexes the names array ({@code co_names}).
     *
     * Pops: {@code a}
     */
    DELETE_ATTR(1, 1, 0),
    /**
     * Reads a global variable. The name is determined by the immediate operand which indexes the
     * names array ({@code co_names}).
     *
     * Pushes: read object
     */
    LOAD_GLOBAL(1, 0, 1),
    /**
     * Writes a global variable. The name is determined by the immediate operand which indexes the
     * names array ({@code co_names}).
     *
     * Pops: value to be written
     */
    STORE_GLOBAL(1, 1, 0),
    /**
     * Deletes a global variable. The name is determined by the immediate operand which indexes the
     * names array ({@code co_names}).
     */
    DELETE_GLOBAL(1, 0, 0),
    /**
     * Reads a constant object from constants array ({@code co_consts}). Performs no conversion.
     * 
     * Pushes: read constant
     */
    LOAD_CONST(1, 0, 1),
    /**
     * Reads a local variable determined by the immediate operand which indexes a stack slot and a
     * variable name in varnames array ({@code co_varnames}).
     *
     * Pushes: read value
     */
    LOAD_FAST(1, 0, 1),
    /**
     * Writes a local variable determined by the immediate operand which indexes a stack slot and a
     * variable name in varnames array ({@code co_varnames}).
     *
     * Pops: value to be writen
     */
    STORE_FAST(1, 1, 0),
    /**
     * Deletes a local variable determined by the immediate operand which indexes a stack slot and a
     * variable name in varnames array ({@code co_varnames}).
     */
    DELETE_FAST(1, 0, 0),
    /**
     * Reads a local cell variable determined by the immediate operand which indexes a stack slot
     * after celloffset and a variable name in cellvars or freevars array ({@code co_cellvars},
     * {@code co_freevars}).
     *
     * Pushes: cell contents
     */
    LOAD_DEREF(1, 0, 1),
    /**
     * Writes a local cell variable determined by the immediate operand which indexes a stack slot
     * after celloffset and a variable name in cellvars or freevars array ({@code co_cellvars},
     * {@code co_freevars}).
     *
     * Pops: value to be written into the cell contents
     */
    STORE_DEREF(1, 1, 0),
    /**
     * Deletes a local cell variable determined by the immediate operand which indexes a stack slot
     * after celloffset and a variable name in cellvars or freevars array ({@code co_cellvars},
     * {@code co_freevars}). Note that it doesn't delete the cell, just its contents.
     */
    DELETE_DEREF(1, 0, 0),
    /**
     * TODO not implemented
     */
    LOAD_CLASSDEREF(1, 0, 1),
    /**
     * Raises an exception. If the immediate operand is 0, it pops nothing and is equivalent to
     * {@code raise} without arguments. If the immediate operand is 1, it is equivalent to
     * {@code raise e} and it pops {@code e}. If the immediate operand is 2, it is equivalent to
     * {@code raise e from c} and it pops {@code c}, then {@code e}. Other immediate operand values
     * are illegal.
     */
    RAISE_VARARGS(1, (oparg, followingArgs, withJump) -> oparg, 0),
    /**
     * Creates a slice object. If the immediate argument is 2, it is equivalent to a slice
     * {@code a:b}. It pops {@code b}, then {@code a}. If the immediate argument is 3, it is
     * equivalent to a slice {@code a:b:c}. It pops {@code c}, then {@code b}, then {@code a}. Other
     * immediate operand values are illegal.
     * 
     * Pushes: the created slice object
     */
    BUILD_SLICE(1, (oparg, followingArgs, withJump) -> oparg, 1),
    /**
     * Formats a value. If the immediate argument contains flag {@link FormatOptions#FVS_HAVE_SPEC},
     * it is equivalent to {@code format(conv(v), spec)}. It pops {@code spec}, then {@code v}.
     * Otherwise, it is equivalent to {@code format(conv(v), None)}. It pops {@code v}. {@code conv}
     * is determined by the immediate operand which contains one of the {@code FVC} options in
     * {@link FormatOptions}.
     *
     * Pushes: the formatted value
     */
    FORMAT_VALUE(1, (oparg, followingArgs, withJump) -> (oparg & FormatOptions.FVS_MASK) == FormatOptions.FVS_HAVE_SPEC ? 2 : 1, 1),

    /**
     * Extends the immediate operand of the following instruction by its own operand shifted left by
     * a byte.
     */
    EXTENDED_ARG(1, 0, 0),

    /**
     * Imports a module by name determined by the immediate operand which indexes the names array
     * ({@code co_names}).
     * 
     * Pops: fromlist (must be {@code String[]}), then level (must be {@code int})
     * 
     * Pushes: imported module
     */
    IMPORT_NAME(1, 2, 1),
    /**
     * Imports a name from a module. The name determined by the immediate operand which indexes the
     * names array ({@code co_names}).
     * 
     * Pops: module object
     * 
     * Pushes: module object, imported object
     */
    IMPORT_FROM(1, 1, 2),
    /**
     * Imports all names from a module of name determined by the immediate operand which indexes the
     * names array ({@code co_names}). The imported names are written to locals dict (can only be
     * invoked on module level).
     * 
     * Pops: level (must be {@code int})
     */
    IMPORT_STAR(1, 1, 0),

    // load bytecodes for special constants
    LOAD_NONE(0, 0, 1),
    LOAD_ELLIPSIS(0, 0, 1),
    LOAD_TRUE(0, 0, 1),
    LOAD_FALSE(0, 0, 1),
    /**
     * Loads signed byte from immediate operand.
     */
    LOAD_BYTE(1, 0, 1),
    /**
     * Loads {@code long} from primitiveConstants array indexed by the immediate operand.
     */
    LOAD_LONG(1, 0, 1),
    /**
     * Loads {@code double} from primitiveConstants array indexed by the immediate operand
     * (converted from long).
     */
    LOAD_DOUBLE(1, 0, 1),
    /**
     * Creates a {@link PInt} from a {@link BigInteger} in constants array indexed by the immediate
     * operand.
     */
    LOAD_BIGINT(1, 0, 1),
    /**
     * Currently the same as {@link #LOAD_CONST}.
     */
    LOAD_STRING(1, 0, 1),
    /**
     * Creates python {@code bytes} from a {@code byte[]} array in constants array indexed by the
     * immediate operand.
     */
    LOAD_BYTES(1, 0, 1),
    /**
     * Creates python {@code complex} from a {@code double[]} array of size 2 in constants array
     * indexed by the immediate operand.
     */
    LOAD_COMPLEX(1, 0, 1),

    // calling
    /**
     * Calls method on an object using an array as args. The receiver is taken from the first
     * element of the array. The method name is determined by the immediate operand which indexes
     * the names array ({@code co_names}).
     *
     * Pops: args ({@code Object[]} of size >= 1)
     * 
     * Pushes: call result
     */
    CALL_METHOD_VARARGS(1, 1, 1),
    /**
     * Calls method on an object using a number of stack args determined by the second immediate
     * operand. The method name is determined by the first immediate operand which indexes the names
     * array ({@code co_names}).
     *
     * Pops: multiple arguments depending on the second immediate operand (0 - 3), then the receiver
     *
     * Pushes: call result
     */
    CALL_METHOD(2, (oparg, followingArgs, withJump) -> Byte.toUnsignedInt(followingArgs[0]) + 1, 1),
    /**
     * Calls a callable using a number of stack args determined by the immediate operand.
     *
     * Pops: multiple arguments depending on the immediate operand (0 - 4), then the callable
     *
     * Pushes: call result
     */
    CALL_FUNCTION(1, (oparg, followingArgs, withJump) -> oparg + 1, 1),
    /**
     * Calls a callable using an arguments array and keywords array.
     *
     * Pops: keyword args ({@code PKeyword[]}), then args ({@code Object[]}), then callable
     * 
     * Pushes: call result
     */
    CALL_FUNCTION_KW(0, 3, 1),
    /**
     * Calls a callable using an arguments array. No keywords are passed.
     *
     * Pops: args ({@code Object[]}), then callable
     *
     * Pushes: call result
     */
    CALL_FUNCTION_VARARGS(0, 2, 1),

    // destructuring bytecodes
    /**
     * Unpacks an iterable into multiple stack items.
     * 
     * Pops: iterable
     * 
     * Pushed: unpacked items, the count is determined by the immediate operand
     */
    UNPACK_SEQUENCE(1, 1, (oparg, followingArgs, withJump) -> oparg),
    /**
     * Unpacks an iterable into multiple stack items with a star item that gets the rest. The first
     * immediate operand determines the count before the star item, the second determines the count
     * after.
     *
     * Pops: iterable
     *
     * Pushed: unpacked items (count = first operand), star item, unpacked items (count = second
     * operand)
     */
    UNPACK_EX(2, 1, (oparg, followingArgs, withJump) -> oparg + 1 + Byte.toUnsignedInt(followingArgs[0])),

    // jumps
    /**
     * Get next value from an iterator. If the iterable is exhausted, jump forward by the offset in
     * the immediate argument.
     *
     * Pops: iterator
     *
     * Pushes (only if not jumping): the iterator, then the next value
     */
    FOR_ITER(1, 1, (oparg, followingArgs, withJump) -> withJump ? 0 : 2),
    /**
     * Jump forward by the offset in the immediate operand.
     */
    JUMP_FORWARD(1, 0, 0),
    /**
     * Jump backward by the offset in the immediate operand. May trigger OSR compilation.
     */
    JUMP_BACKWARD(1, 0, 0),
    /**
     * Jump forward by the offset in the immediate operand if the top of the stack is false (in
     * Python sense).
     * 
     * Pops (if not jumping): top of the stack
     */
    JUMP_IF_FALSE_OR_POP(1, (oparg, followingArgs, withJump) -> withJump ? 0 : 1, 0),
    /**
     * Jump forward by the offset in the immediate operand if the top of the stack is false (in
     * Python sense).
     *
     * Pops (if not jumping): top of the stack
     */
    JUMP_IF_TRUE_OR_POP(1, (oparg, followingArgs, withJump) -> withJump ? 0 : 1, 0),
    /**
     * Jump forward by the offset in the immediate operand if the top of the stack is false (in
     * Python sense).
     *
     * Pops: top of the stack
     */
    POP_AND_JUMP_IF_FALSE(1, 1, 0),
    /**
     * Jump forward by the offset in the immediate operand if the top of the stack is true (in
     * Python sense).
     *
     * Pops: top of the stack
     */
    POP_AND_JUMP_IF_TRUE(1, 1, 0),

    // making callables
    /**
     * Like {@link #LOAD_DEREF}, but loads the cell itself, not the contents.
     * 
     * Pushes: the cell object
     */
    LOAD_CLOSURE(1, 0, 1),
    /**
     * Reduces multiple stack items into an array of cell objects.
     * 
     * Pops: multiple cells (count = immediate argument)
     * 
     * Pushes: cell object array ({@code PCell[]})
     */
    CLOSURE_FROM_STACK(1, (oparg, followingArgs, withJump) -> oparg, 1),
    /**
     * Creates a function object. The first immediate argument is an index to the constants array
     * that determines the {@link CodeUnit} object that will provide the function's code.
     * 
     * Pops: The second immediate arguments contains flags (defined in {@link CodeUnit}) that
     * determine whether it will need to pop (in this order): closure, annotations, keyword only
     * defaults, defaults.
     * 
     * Pushes: created function
     */
    MAKE_FUNCTION(2, (oparg, followingArgs, withJump) -> Integer.bitCount(followingArgs[0]), 1),

    // collection literals
    /**
     * Creates a collection from multiple elements from the stack. Collection type is determined by
     * {@link CollectionBits} in immediate operand.
     *
     * Pops: items for the collection (count = immediate argument)
     *
     * Pushes: new collection
     */
    COLLECTION_FROM_STACK(1, (oparg, followingArgs, withJump) -> CollectionBits.elementCount(oparg), 1),
    /**
     * Add multiple elements from the stack to the collection below them. Collection type is
     * determined by {@link CollectionBits} in immediate operand. Tuple is not supported.
     * 
     * Pops: items to be added (count = immediate argument)
     */
    COLLECTION_ADD_STACK(1, (oparg, followingArgs, withJump) -> CollectionBits.elementCount(oparg) + 1, 1),
    /**
     * Concatenates two collection of the same type. Collection type is determined by
     * {@link CollectionBits} in immediate operand. Tuple is not supported.
     * 
     * Pops: second collection, first collection
     * 
     * Pushes: concatenated collection
     */
    COLLECTION_ADD_COLLECTION(1, 2, 1),
    /**
     * Converts collection to another type determined by {@link CollectionBits} in immediate
     * operand. The converted collection is expected to be an independent copy (they don't share
     * storage).
     * 
     * Pops: original collection
     * 
     * Pushes: converted collection
     */
    COLLECTION_FROM_COLLECTION(1, 1, 1),
    /**
     * Adds an item to a collection that is multiple items deep under the top of the stack,
     * determined by the immediate argument.
     * 
     * Pops: item to be added
     */
    ADD_TO_COLLECTION(1, (oparg, followingArgs, withJump) -> CollectionBits.elementType(oparg) == CollectionBits.DICT ? 2 : 1, 0),
    /**
     * Like {@link #COLLECTION_ADD_COLLECTION} for dicts, but with checks for duplicate keys
     * necessary for keyword arguments merge. Note it works with dicts. Keyword arrays need to be
     * converted to dicts first.
     */
    KWARGS_DICT_MERGE(0, 2, 1),
    /**
     * Create a single {@link PKeyword} object. The name is determined by the immediate operand
     * which indexes the names array ({@code co_names})
     * 
     * Pops: keyword value
     * 
     * Pushes: keyword object
     */
    MAKE_KEYWORD(1, 1, 1),

    // exceptions
    /**
     * Jump forward by the offset in the immediate argument if the exception doesn't match the
     * expected type. The exception object is {@link PException}, not a python exception.
     * 
     * Pops: expected type, then exception
     * 
     * Pushes (if jumping): the exception
     */
    MATCH_EXC_OR_JUMP(1, 2, 1),
    /**
     * Save the current exception state on the stack and set it to the exception on the stack. The
     * exception object is {@link PException}, not a python exception. The exception is pushed back
     * to the top.
     * 
     * Pops: the exception
     * 
     * Pushes: the saved exception state, the exception
     */
    PUSH_EXC_INFO(0, 0, 1),
    /**
     * Sets the current exception state to the saved state (by {@link #PUSH_EXC_INFO}) on the stack
     * and pop it.
     * 
     * Pops: save exception state
     */
    POP_EXCEPT(0, 1, 0),
    /**
     * Restore exception state and reraise exception.
     * 
     * Pops: exception to reraise, then saved exception state
     */
    END_EXC_HANDLER(0, 2, 0),
    /**
     * Gets the python-level exception object from a {@link PException}.
     * 
     * Pops: a {@link PException} Pushes: python exception
     */
    UNWRAP_EXC(0, 1, 1),

    // generators
    /**
     * Yield value from the stack to the caller. Saves execution state. The generator will resume at
     * the next instruction.
     * 
     * Pops: yielded value
     */
    YIELD_VALUE(0, 1, 0),
    /**
     * Resume after yield. Will raise exception passed by {@code throw} if any.
     * 
     * Pushes: value received from {@code send} or {@code None}.
     */
    RESUME_YIELD(0, 0, 1),
    /**
     * Send value into a generator. Jumps forward by the offset in the immediate argument if the
     * generator is exhausted. Used to implement {@code yield from}.
     * 
     * Pops: value to be sent, then generator
     * 
     * Pushes (if not jumping): the generator, then the yielded value
     * 
     * Pushes (if jumping): the generator return value
     */
    SEND(1, 2, (oparg, followingArgs, withJump) -> withJump ? 1 : 2),

    // with statements
    /**
     * Enter a context manager and save data for its exit.
     * 
     * Pops: the context manager
     * 
     * Pushes: the context manager, then maybe-bound {@code __exit__}, then the result of
     * {@code __enter__}
     */
    SETUP_WITH(0, 1, 3),
    /**
     * Run the exit handler of a context manager and reraise if necessary.
     * 
     * Pops: exception or {@code None}, then maybe-bound {@code __exit__}, then the context manager
     */
    EXIT_WITH(0, 3, 0);

    public static final class CollectionBits {
        public static final int MAX_STACK_ELEMENT_COUNT = 0b00011111;
        public static final int LIST = 0b00100000;
        public static final int TUPLE = 0b01000000;
        public static final int SET = 0b01100000;
        public static final int DICT = 0b10000000;
        public static final int KWORDS = 0b10100000;
        public static final int OBJECT = 0b11000000;

        public static int elementCount(int oparg) {
            return oparg & MAX_STACK_ELEMENT_COUNT;
        }

        public static int elementType(int oparg) {
            return oparg & ~MAX_STACK_ELEMENT_COUNT;
        }
    }

    public static final OpCodes[] VALUES = new OpCodes[values().length];

    static {
        assert values().length < 256;
        System.arraycopy(values(), 0, VALUES, 0, VALUES.length);
    }

    public final StackEffect consumesStackItems;
    public final StackEffect producesStackItems;

    /**
     * Instruction argument length in bytes
     */
    public final int argLength;

    OpCodes(int argLength, int consumesStackItems, int producesStackItems) {
        this(argLength, (oparg, followingArgs, withJump) -> consumesStackItems, (oparg, followingArgs, withJump) -> producesStackItems);
    }

    OpCodes(int argLength, StackEffect consumesStackItems, int producesStackItems) {
        this(argLength, consumesStackItems, (oparg, followingArgs, withJump) -> producesStackItems);
    }

    OpCodes(int argLength, int consumesStackItems, StackEffect producesStackItems) {
        this(argLength, (oparg, followingArgs, withJump) -> consumesStackItems, producesStackItems);
    }

    OpCodes(int argLength, StackEffect consumesStackItems, StackEffect producesStackItems) {
        this.argLength = argLength;
        this.consumesStackItems = consumesStackItems;
        this.producesStackItems = producesStackItems;
    }

    @FunctionalInterface
    private interface StackEffect {
        int stackEffect(int oparg, byte[] followingArgs, boolean withJump);
    }

    public boolean hasArg() {
        return argLength > 0;
    }

    public int length() {
        return argLength + 1;
    }

    public int getNumberOfConsumedStackItems(int oparg, byte[] followingArgs, boolean withJump) {
        return consumesStackItems.stackEffect(oparg, followingArgs, withJump);
    }

    public int getNumberOfProducedStackItems(int oparg, byte[] followingArgs, boolean withJump) {
        return producesStackItems.stackEffect(oparg, followingArgs, withJump);
    }

    public int getStackEffect(int oparg, byte[] followingArgs, boolean withJump) {
        return getNumberOfProducedStackItems(oparg, followingArgs, withJump) - getNumberOfConsumedStackItems(oparg, followingArgs, withJump);
    }
}
