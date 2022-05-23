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
package com.oracle.graal.python.pegparser;

import org.junit.Test;

public class GeneratorAndCompForTests extends ParserTestBase {

    @Test
    public void generator01() throws Exception {
        checkScopeAndTree("(x*x for x in range(10))");
    }

    @Test
    public void list01() throws Exception {
        checkScopeAndTree("[x**y for x in range(20)]");
    }

    @Test
    public void list02() throws Exception {
        checkScopeAndTree("[x**y for x in range(20) if x*y % 3]");
    }

    @Test
    public void argument01() throws Exception {
        checkScopeAndTree("foo(x+2 for x in range(10))");
    }

    @Test
    public void set01() throws Exception {
        checkScopeAndTree("{x**y for x in range(20)}");
    }

    @Test
    public void dict01() throws Exception {
        checkScopeAndTree("{x:x*x for x in range(20)}");
    }

    @Test
    public void dict02() throws Exception {
        checkScopeAndTree(
                        "dict1 = {'a': 1, 'b': 2, 'c': 3, 'd': 4, 'e': 5}\n" +
                                        "double_dict1 = {k:v*2 for (k,v) in dict1.items()}");
    }

    @Test
    public void generator02() throws Exception {
        checkScopeAndTree(
                        "def fn():\n" +
                                        "  (x*x for x in range(10))");
    }

    @Test
    public void generator03() throws Exception {
        checkScopeAndTree(
                        "def fn():\n" +
                                        "  c = 10\n" +
                                        "  (x + c for x in range(10))");
    }

    @Test
    public void generator04() throws Exception {
        checkScopeAndTree(
                        "def fn():\n" +
                                        "  yield 1\n" +
                                        "  yield 2");
    }

    @Test
    public void generator05() throws Exception {
        checkScopeAndTree(
                        "def fn(self):\n" +
                                        "    caretspace = (c.isspace() for c in caretspace)\n" +
                                        "    yield caretspace");
    }

    @Test
    public void generator06() throws Exception {
        checkScopeAndTree(
                        "def format_exception_only(self):\n" +
                                        "        if a():\n" +
                                        "            yield \"neco\"\n" +
                                        "            return\n" +
                                        "        if badline is not None:\n" +
                                        "            yield '\\n'\n" +
                                        "            if offset is not None:\n" +
                                        "                caretspace = (c.isspace() for c in caretspace)\n" +
                                        "                yield caretspace\n" +
                                        "        yield \"message\"");
    }

    @Test
    public void generator07() throws Exception {
        checkScopeAndTree(
                        "def merge(h):\n" +
                                        "    while len(h) > 1:\n" +
                                        "        while True:\n" +
                                        "            value, order, next = s = h[0]\n" +
                                        "            yield value");
    }

    @Test
    public void generator08() throws Exception {
        checkScopeAndTree(
                        "def merge(h):\n" +
                                        "    for order, it in b():\n" +
                                        "        value = next()\n" +
                                        "    while len(h) > 1:\n" +
                                        "        yield value");
    }

    @Test
    public void generator09() throws Exception {
        checkScopeAndTree(
                        "def merge(h):\n" +
                                        "    while True:\n" +
                                        "        value = next()\n" +
                                        "    while h > 1:\n" +
                                        "        yield value");
    }

    @Test
    public void generator10() throws Exception {
        checkScopeAndTree(
                        "def non_empty_lines(path):\n" +
                                        "    with open(path) as f:\n" +
                                        "        for line in f:\n" +
                                        "            line = line.strip()\n" +
                                        "            if line:\n" +
                                        "                yield line");
    }

    @Test
    public void generatorWithNonLocal() throws Exception {
        checkScopeAndTree(
                        "def b_func():\n" +
                                        "  exec_gen = False\n" +
                                        "  def _inner_func():\n" +
                                        "    def doit():\n" +
                                        "      nonlocal exec_gen\n" +
                                        "      exec_gen = True\n" +
                                        "      return [1]\n" +
                                        "    for A in doit():\n" +
                                        "      for C in Y:\n" +
                                        "        yield A\n" +
                                        "  gen = _inner_func()\n" +
                                        "  Y = [1, 2]\n" +
                                        "  list(gen)\n" +
                                        "  return gen");
    }

    @Test
    public void param01() throws Exception {
        checkScopeAndTree(
                        "def fn(a):\n" +
                                        "  yield a\n" +
                                        "  yield 2");
    }

    @Test
    public void param02() throws Exception {
        checkScopeAndTree(
                        "def fn(a, b):\n" +
                                        "  yield a\n" +
                                        "  yield b");
    }

    @Test
    public void param03() throws Exception {
        checkScopeAndTree(
                        "def fn(a, b=1):\n" +
                                        "  yield a\n" +
                                        "  yield b");
    }

    @Test
    public void param04() throws Exception {
        checkScopeAndTree(
                        "def fn(*arg):\n" +
                                        "  for p in arg:\n" +
                                        "    yield p");
    }

    @Test
    public void param05() throws Exception {
        checkScopeAndTree(
                        "def fn(**arg):\n" +
                                        "  for p in arg:\n" +
                                        "    yield p");
    }

    @Test
    public void param06() throws Exception {
        checkScopeAndTree(
                        "def fn(files, dirs):\n" +
                                        "  a = [join(dir, file) for dir in dirs for file in files]");
    }

    @Test
    public void doc01() throws Exception {
        checkScopeAndTree(
                        "def fn():\n" +
                                        "  \"This is a doc\"\n" +
                                        "  yield \"neco\"");
    }

    @Test
    public void doc02() throws Exception {
        checkScopeAndTree(
                        "def walk_stack(f):\n" +
                                        "    \"\"\"Documentation\"\"\"\n" +
                                        "    if f is None:\n" +
                                        "        f = a()\n" +
                                        "    while f is not None:\n" +
                                        "        yield f\n");
    }

    @Test
    public void class01() throws Exception {
        checkScopeAndTree(
                        "class OrderedDict(dict):\n" +
                                        "    def __reversed__(self):\n" +
                                        "        root = self.__root\n" +
                                        "        curr = root.prev\n" +
                                        "        while curr is not root:\n" +
                                        "            yield curr.key\n" +
                                        "            curr = curr.prev");
    }

    @Test
    public void class02() throws Exception {
        checkScopeAndTree(
                        "class Counter(dict):\n" +
                                        "    def _keep_positive(self):\n" +
                                        "        nonpositive = [elem for elem, count in self.items() if not count > 0]\n" +
                                        "        for elem in nonpositive:\n" +
                                        "            del self[elem]\n" +
                                        "        return self");
    }

    @Test
    public void comp01() throws Exception {
        checkScopeAndTree("resutl = {i: tuple(j for j in t if i != j)\n" +
                        "                     for t in something for i in t}");
    }

    @Test
    public void comp02() throws Exception {
        checkScopeAndTree(
                        "def mro(cls, abcs=None):\n" +
                                        "    for base in abcs:\n" +
                                        "        if not any(issubclass(b, base) for b in cls.__bases__):\n" +
                                        "            abstract_bases.append(base)\n" +
                                        "    other = [mro(base, abcs=abcs) for base in other]");
    }

    @Test
    public void fstring01() throws Exception {
        checkScopeAndTree(
                        "def fn(someset): return ', '.join(f'{name}' for name in someset)");
    }

    // @Test
    // public void genFromPip() throws Exception {
    // File testFile = getTestFileFromTestAndTestMethod();
    // checkScopeFromFile(testFile, true);
    // checkTreeFromFile(testFile, true);
    // }

    @Test
    public void issueGR18174() throws Exception {
        checkScopeAndTree("[b for b in [a for a in (1,2)]]");
    }

    @Test
    public void issueGR18309() throws Exception {
        checkScopeAndTree("[ b for a in d1 if d1 for b in d2]");
    }

    @Test
    public void issueGR16991() throws Exception {
        checkScopeAndTree(
                        "def gen(x): \n" +
                                        "  while x: \n" +
                                        "    if x == 10: \n" +
                                        "      break \n" +
                                        "    x = x - 1 \n" +
                                        "    yield x \n" +
                                        "  else: \n" +
                                        "    yield 100");
    }

    @Test
    public void listCompr() throws Exception {
        checkScopeAndTree("[i for i in range(3)]");
    }

    @Test
    public void setCompr() throws Exception {
        checkScopeAndTree("{i for i in range(3)}");
    }

    @Test
    public void dictCompr() throws Exception {
        checkScopeAndTree("{i: i + 1 for i in range(3)}");
    }

    @Test
    public void genExpr() throws Exception {
        checkScopeAndTree("(i for i in range(3))");
    }

    @Test
    public void genExpr01() throws Exception {
        checkScopeAndTree("[ (c,s) for c in ('a','b') for s in (1,2,3,4,5)]");
    }

    @Test
    public void genExpr02() throws Exception {
        checkScopeAndTree("[ (s,c) for c in ('a','b') for s in (1,2,3,4,5)]");
    }

    @Test
    public void genExpr03() throws Exception {
        checkScopeAndTree("(e+1 for e in (i*2 for i in (1,2,3)))");
    }

    @Test
    public void issueGitHub_42_v1() throws Exception {
        checkScopeAndTree("[e for e in (s for s in (1, 2, 3))]");
    }

    @Test
    public void issueGitHub_42_v2() throws Exception {
        checkScopeAndTree("[e for e in [s for s in (1, 2, 3)]]");
    }
}
