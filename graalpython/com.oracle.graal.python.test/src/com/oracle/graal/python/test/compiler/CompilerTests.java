/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.CompilationUnit;
import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.pegparser.FExprParser;
import com.oracle.graal.python.pegparser.NodeFactory;
import com.oracle.graal.python.pegparser.NodeFactoryImp;
import com.oracle.graal.python.pegparser.Parser;
import com.oracle.graal.python.pegparser.ParserErrorCallback;
import com.oracle.graal.python.pegparser.ParserTokenizer;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.test.PythonTests;

public class CompilerTests extends PythonTests {
    public CompilerTests() {
    }

    @Rule public TestName name = new TestName();

    @Test
    public void testBinaryOp() {
        doTest("1 + 1");
    }

    @Test
    public void testComplexNumber() {
        doTest("-2 + 3j");
    }

    @Test
    public void testAssignment() {
        doTest("a = 12");
    }

    @Test
    public void testAugAssignment() {
        doTest("a += 12.0");
    }

    @Test
    public void testDel() {
        doTest("del a");
    }

    @Test
    public void testGetItem() {
        doTest("a[3]");
    }

    @Test
    public void testSetItem() {
        doTest("a[3] = 1");
    }

    @Test
    public void testDelItem() {
        doTest("del a[3]");
    }

    @Test
    public void testSlice() {
        doTest("a[3:9]");
    }

    @Test
    public void testSliceStep() {
        doTest("a[3:9:2]");
    }

    @Test
    public void testCall() {
        doTest("range(num)");
    }

    @Test
    public void testLogicOperators() {
        doTest("a and b or not c");
    }

    @Test
    public void testManyArgs() {
        // Test collecting more args that a single COLLECT_FROM_STACK instruction can handle
        String source = "print(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36)";
        doTest(source);
    }

    @Test
    public void testCallKeyword() {
        doTest("print('test', end=';')");
    }

    @Test
    public void testCallMultiStarArgs() {
        doTest("foo(1, *a, 2, *b, 3)");
    }

    @Test
    public void testCallMultiStarKwargs() {
        doTest("foo(a=1, **a, b=2, **b, c=3)");
    }

    @Test
    public void testVarArgs() {
        String source = "def foo(*args):\n" +
                        "  print(*args)\n";
        doTest(source);
    }

    @Test
    public void testVarKwargs() {
        String source = "def foo(**kwargs):\n" +
                        "  print(**kwargs)\n";
        doTest(source);
    }

    @Test
    public void testArgsCombination() {
        String source = "def foo(a, /, b, *c, d, **e):\n" +
                        "  print(a, b, c, d, e)\n";
        doTest(source);
    }

    @Test
    public void testFor() {
        doTest("for i in [1,2]:\n pass");
    }

    @Test
    public void testWhile() {
        doTest("while False: pass");
    }

    @Test
    public void testForBreakContinue() {
        String source = "for i in range(10):\n" +
                        "  if i == 3:\n" +
                        "    break\n" +
                        "  else:\n" +
                        "    continue\n" +
                        "else:\n" +
                        "  print('else')";
        doTest(source);
    }

    @Test
    public void testWhileBreakContinue() {
        String source = "i = 0\n" +
                        "while i < 10:\n" +
                        "  if i == 3:\n" +
                        "    break\n" +
                        "  else:\n" +
                        "    i += 1\n" +
                        "    continue\n" +
                        "else:\n" +
                        "  print('else')";
        doTest(source);
    }

    @Test
    public void testBreakFromWith() {
        String source = "for i in range(10):\n" +
                        "  with foo() as cm:\n" +
                        "    break\n";
        doTest(source);
    }

    @Test
    public void testBreakFromTry() {
        String source = "for i in range(10):\n" +
                        "  try:\n" +
                        "    break\n" +
                        "  finally:" +
                        "    print('finally')";
        doTest(source);
    }

    @Test
    public void testBreakFromExcept() {
        String source = "for i in range(10):\n" +
                        "  try:\n" +
                        "    1 / 0\n" +
                        "  except RuntimeError as e:" +
                        "    break";
        doTest(source);
    }

    @Test
    public void testBreakFromFinally() {
        String source = "for i in range(10):\n" +
                        "  try:\n" +
                        "    if i:\n" +
                        "      break\n" +
                        "    print(i)\n" +
                        "  finally:\n" +
                        "    print('finally')\n" +
                        "    break";
        doTest(source);
    }

    @Test
    public void testReturnFromWith() {
        String source = "for i in range(10):\n" +
                        "  with foo() as cm:\n" +
                        "    return a\n";
        doTest(source);
    }

    @Test
    public void testReturnFromTry() {
        String source = "for i in range(10):\n" +
                        "  try:\n" +
                        "    return a\n" +
                        "  finally:" +
                        "    print('finally')";
        doTest(source);
    }

    @Test
    public void testReturnFromExcept() {
        String source = "for i in range(10):\n" +
                        "  try:\n" +
                        "    1 / 0\n" +
                        "  except RuntimeError as e:" +
                        "    return a";
        doTest(source);
    }

    @Test
    public void testReturnFromFinally() {
        String source = "for i in range(10):\n" +
                        "  try:\n" +
                        "    if i:\n" +
                        "      return a\n" +
                        "    print(i)\n" +
                        "  finally:\n" +
                        "    print('finally')\n" +
                        "    return b";
        doTest(source);
    }

    @Test
    public void testFinallyCancelReturn() {
        String source = "for i in range(10):\n" +
                        "  try:\n" +
                        "    return a\n" +
                        "  finally:" +
                        "    continue";
        doTest(source);
    }

    @Test
    public void testTryExcept() {
        String s = "print('before')\n" +
                        "try:\n" +
                        "  print('try')\n" +
                        "except TypeError as e:\n" +
                        "  print('except1')\n" +
                        "except ValueError as e:\n" +
                        "  print('except2')\n" +
                        "print('after')\n";
        doTest(s);
    }

    @Test
    public void testTryExceptBare() {
        String s = "print('before')\n" +
                        "try:\n" +
                        "  print('try')\n" +
                        "except TypeError as e:\n" +
                        "  print('except1')\n" +
                        "except:\n" +
                        "  print('except bare')\n" +
                        "print('after')\n";
        doTest(s);
    }

    @Test
    public void testTryFinally() {
        String s = "print('before')\n" +
                        "try:\n" +
                        "  print('try')\n" +
                        "finally:\n" +
                        "  print('finally')\n" +
                        "print('after')\n";
        doTest(s);
    }

    @Test
    public void testTryFinallyNested() {
        String source = "def foo(obj):\n" +
                        "    for x in obj:\n" +
                        "        print(x)\n" +
                        "    try:\n" +
                        "        try:\n" +
                        "            print('try')\n" +
                        "        finally:\n" +
                        "            print('finally1')\n" +
                        "    finally:\n" +
                        "        print('finally2')\n";
        doTest(source);
    }

    @Test
    public void testTryExceptFinally() {
        String s = "print('before')\n" +
                        "try:\n" +
                        "  print('try')\n" +
                        "except TypeError as e:\n" +
                        "  print('except1')\n" +
                        "except ValueError as e:\n" +
                        "  print('except2')\n" +
                        "finally:\n" +
                        "  print('finally')\n" +
                        "print('after')\n";
        doTest(s);
    }

    @Test
    public void testTryExceptElse() {
        String s = "print('before')\n" +
                        "try:\n" +
                        "  print('try')\n" +
                        "except TypeError as e:\n" +
                        "  print('except1')\n" +
                        "except ValueError as e:\n" +
                        "  print('except2')\n" +
                        "else:\n" +
                        "  print('else')\n" +
                        "print('after')\n";
        doTest(s);
    }

    @Test
    public void testTryExceptElseFinally() {
        String s = "print('before')\n" +
                        "try:\n" +
                        "  print('try')\n" +
                        "except TypeError as e:\n" +
                        "  print('except1')\n" +
                        "except ValueError as e:\n" +
                        "  print('except2')\n" +
                        "else:\n" +
                        "  print('else')\n" +
                        "finally:\n" +
                        "  print('finally')\n" +
                        "print('after')\n";
        doTest(s);
    }

    @Test
    public void testWith() {
        String s = "print('before')\n" +
                        "with open('/dev/null') as f:\n" +
                        "  f.write('foo')\n" +
                        "print('after')";
        doTest(s);
    }

    @Test
    public void testWithMultiple() {
        String s = "print('before')\n" +
                        "with open('/dev/null') as f, open('/tmp/foo'):\n" +
                        "  f.write('foo')\n" +
                        "print('after')";
        doTest(s);
    }

    @Test
    public void testDefun() {
        String source = "def docompute(num, num2=5):\n" +
                        "   return (num, num2)\n";
        doTest(source);
    }

    @Test
    public void testReturnPlain() {
        String source = "def foo():\n" +
                        "   return\n";
        doTest(source);
    }

    @Test
    public void testClosure() {
        String s = "def foo():\n" +
                        "    x = 1\n" +
                        "    def bar():\n" +
                        "        nonlocal x\n" +
                        "        print(x)\n" +
                        "        x = 2\n" +
                        "    bar()\n" +
                        "    print(x)\n" +
                        "    x = 3\n";
        doTest(s);
    }

    @Test
    public void testIf() {
        String source = "if False:\n" +
                        "   print(True)\n" +
                        "else:\n" +
                        "   print(False)\n";
        doTest(source);
    }

    @Test
    public void testIfExpression() {
        doTest("t if cond else f\n");
    }

    @Test
    public void testClass() {
        String source = "class Foo:\n" +
                        "    c = 64\n" +
                        "    def __init__(self, arg):\n" +
                        "        self.var = arg\n";
        doTest(source);
    }

    @Test
    public void testSuper() {
        String source = "class Foo:\n" +
                        "    def boo(self):\n" +
                        "        print('boo')\n" +
                        "class Bar(Foo):\n" +
                        "    def boo(self):\n" +
                        "        super().boo()\n";
        doTest(source);
    }

    @Test
    public void testEmptyList() {
        doTest("[]");
    }

    @Test
    public void testEmptyTuple() {
        doTest("()");
    }

    @Test
    public void testEmptyDict() {
        doTest("{}");
    }

    @Test
    public void testTupleLiteral() {
        doTest("(1, 2, 3)");
    }

    @Test
    public void testTupleLiteralExpand() {
        doTest("(1, 2, 3, *a, 5)");
    }

    @Test
    public void testListLiteral() {
        doTest("[1, 2, 3]");
    }

    @Test
    public void testListLiteralExpand() {
        doTest("[1, 2, 3, *a, 5]");
    }

    @Test
    public void testSetLiteral() {
        doTest("{1, 2, 3}");
    }

    @Test
    public void testSetLiteralExpand() {
        doTest("{1, 2, 3, *a, 5}");
    }

    @Test
    public void testDictLiteral() {
        doTest("{'a': 'b', 1: 2}");
    }

    @Test
    public void testDictLiteralExpand() {
        doTest("{'a': 'b', 1: 2, **a, None: True}");
    }

    @Test
    public void testUnpack() {
        doTest("a, b = 1, 2");
    }

    @Test
    public void testUnpackEx() {
        doTest("a, *b, c = 1, 2, 3, 4, 5");
    }

    @Test
    public void testListComprehension() {
        String source = "[str(x) for y in [[1, 2, 3], [4, 5, 6]] for x in y if x < 5]";
        doTest(source);
    }

    @Test
    public void testFString() {
        doTest("f'before{a}middle{b!r:5}after'");
    }

    @Test
    public void testNestedListComprehension() {
        String source = "[[x for x in range(5)] for y in range(3)]";
        doTest(source);
    }

    @Test
    public void testSetComprehension() {
        String source = "{x * 2 for x in range(10) if x % 2 == 0}";
        doTest(source);
    }

    @Test
    public void testDictComprehension() {
        String source = "{x: str(x) for x in range(10)}";
        doTest(source);
    }

    @Test
    public void testLambda() {
        doTest("lambda x, *args: args[x]");
    }

    @Test
    public void testYieldPlain() {
        String source = "def gen(a):\n" +
                        "    yield\n";
        doTest(source);
    }

    @Test
    public void testYieldValue() {
        String source = "def gen(a):\n" +
                        "    yield a + 1\n";
        doTest(source);
    }

    @Test
    public void testYieldFrom() {
        String source = "def gen(a):\n" +
                        "    yield from a\n";
        doTest(source);
    }

    @Test
    public void testCoroutine() {
        String source = "async def foo(a):\n" +
                        "    await a\n";
        doTest(source);
    }

    @Test
    public void testYieldExpression() {
        String source = "def gen(a):\n" +
                        "    b = yield a\n";
        doTest(source);
    }

    @Test
    public void testGeneratorComprehension() {
        String source = "(str(x) for y in [[1, 2, 3], [4, 5, 6]] for x in y if x < 5)";
        doTest(source);
    }

    @Test
    public void testExtendedArgs() {
        StringBuilder source = new StringBuilder();
        source.append("if a:\n");
        for (int i = 0; i < 260; i++) {
            source.append(String.format("   a.f%d('%d')\n", i, i));
        }
        source.append("else:\n");
        source.append("    print('else')");
        doTest(source.toString());
    }

    @Test
    public void testBenchmark() {
        String source = "def docompute(num):\n" +
                        "    for i in range(num):\n" +
                        "        sum_ = 0.0\n" +
                        "        j = 0\n" +
                        "        while j < num:\n" +
                        "            sum_ += 1.0 / (((i + j) * (i + j + 1) >> 1) + i + 1)\n" +
                        "            j += 1\n" +
                        "\n" +
                        "    return sum_\n" +
                        "\n" +
                        "\n" +
                        "def measure(num):\n" +
                        "    for run in range(num):\n" +
                        "        sum_ = docompute(10000)  # 10000\n" +
                        "    print('sum', sum_)\n" +
                        "\n" +
                        "\n" +
                        "def __benchmark__(num=5):\n" +
                        "    measure(num)\n";
        doTest(source);
    }

    @Test
    public void testBenchmark2() {
        String source = "" +
                        "class HandlerTask(Task):\n" +
                        "    def __init__(self,i,p,w,s,r):\n" +
                        "        global Task\n" +
                        "        x = 0\n" +
                        "        raise ValueError\n" +
                        // " def f():\n" +
                        // " nonlocal x\n" +
                        // " x = 1\n" +
                        "        Task.__init__(self,i,p,w,s,r)\n";
        doTest(source);
    }

    @Test
    public void testImport() {
        String source = "" +
                        "if __name__ == '__main__':\n" +
                        "    import sys\n" +
                        "    if not (len(sys.argv) == 1 and sys.argv[0] == 'java_embedding_bench'):\n" +
                        "        import time\n" +
                        "        start = time.time()\n" +
                        "        if len(sys.argv) >= 2:\n" +
                        "            num = int(sys.argv[1])\n" +
                        "            __benchmark__(num)\n" +
                        "        else:\n" +
                        "            __benchmark__()\n" +
                        "        print(\"%s took %s s\" % (__file__, time.time() - start))\n";
        doTest(source);
    }

    @Test
    public void testImportAs() {
        doTest("import a.b.c as d");
    }

    @Test
    public void testImportFrom() {
        doTest("from math import sqrt, sin as sine");
    }

    @Test
    public void testImportStar() {
        doTest("from a.b import *");
    }

    private void doTest(String src) {
        ParserTokenizer tokenizer = new ParserTokenizer(src);
        NodeFactory factory = new NodeFactoryImp();
        ParserErrorCallback errorCb = new ParserErrorCallback() {
            @Override
            public void onError(ParserErrorCallback.ErrorType type, int start, int end, String message) {
                System.err.println(String.format("TODO: %s[%d:%d]: %s", type.name(), start, end, message));
            }
        };
        FExprParser fexpParser = new FExprParser() {
            @Override
            public ExprTy parse(String code) {
                ParserTokenizer tok = new ParserTokenizer(code);
                return new Parser(tok, factory, this, errorCb).fstring_rule();
            }
        };
        Parser parser = new Parser(tokenizer, factory, fexpParser, errorCb);
        ModTy result = parser.file_rule();
        Compiler compiler = new Compiler();
        CompilationUnit cu = compiler.compile(result, EnumSet.noneOf(Compiler.Flags.class), 2);
        CodeUnit co = cu.assemble(0);
        checkCodeUnit(co);
    }

    private void checkCodeUnit(CodeUnit co) {
        String coString = co.toString();
        Path goldenFile = Paths.get(System.getProperty("org.graalvm.language.python.home"),
                        "com.oracle.graal.python.test", "testData", "goldenFiles",
                        this.getClass().getSimpleName(),
                        name.getMethodName() + ".co");
        try {
            if (!Files.exists(goldenFile)) {
                Files.createDirectories(goldenFile.getParent());
                Files.writeString(goldenFile, coString);
            } else {
                Assert.assertEquals(Files.readString(goldenFile), coString);
            }
        } catch (IOException ex) {
            Assert.assertTrue(ex.getMessage(), false);
        }
    }

}
