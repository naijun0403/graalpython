# Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.



def prints_like_iter(obj):
    s = str(obj)
    assert s.startswith("<class '")
    assert s.endswith("iterator'>")

def test_iter_printing_bytes():
    bytes_iterator = type(iter(b''))
    prints_like_iter(bytes_iterator)
    # <class 'bytes_iterator'>

    # bytearray_iterator = type(iter(bytearray()))
    # print(bytearray_iterator)

def test_iter_printing_dict():
    dict_iterator = type(iter({}))
    prints_like_iter(dict_iterator)
    # <class 'dict_keyiterator'>

    dict_keyiterator = type(iter({}.keys()))
    prints_like_iter(dict_keyiterator)
    # <class 'dict_keyiterator'>

    dict_valueiterator = type(iter({}.values()))
    prints_like_iter(dict_valueiterator)
    # <class 'dict_valueiterator'>

    dict_itemiterator = type(iter({}.items()))
    prints_like_iter(dict_itemiterator)
    # <class 'dict_itemiterator'>

def test_iter_printing_list():
    list_iterator = type(iter([]))
    prints_like_iter(list_iterator)
    # <class 'list_iterator'>

    # list_reverseiterator = type(iter(reversed([])))
    # prints_like_iter(list_reverseiterator)

def test_iter_printing_range():
    range_iterator = type(iter(range(0)))
    prints_like_iter(range_iterator)
    # <class 'range_iterator'>

    longrange_iterator = type(iter(range(1 << 1000)))
    prints_like_iter(longrange_iterator)
    # <class 'longrange_iterator'>

def test_iter_printing_set():
    set_iterator = type(iter(set()))
    prints_like_iter(set_iterator)
    # <class 'set_iterator'>

def test_iter_printing_str():
    str_iterator = type(iter(""))
    prints_like_iter(str_iterator)
    # <class 'str_iterator'>

def test_iter_printing_tuple():
    tuple_iterator = type(iter(()))
    prints_like_iter(tuple_iterator)
    # <class 'tuple_iterator'>

def test_iter_printing_zip():
    zip_iterator = type(iter(zip()))
    assert str(zip_iterator) == "<class 'zip'>"
    # <class 'zip'>


def test_zip_no_args():
    assert list(zip(*[])) == []


def test_iter_try_except():
    it = iter(range(3))
    exit_via_break = False
    while 1:
        try:
            next(it)
        except StopIteration:
            exit_via_break = True
            break

    assert exit_via_break


def test_iterator_in():
    assert 1 not in (i for i in range(1))
    assert 1 in (i for i in range(2))
    assert 1 in iter(range(2))
    assert 1 not in iter(range(1))


def test_itertools_zip_longest():
    from itertools import zip_longest
    x = [1,2,3]
    y = [4,5,6,7]
    assert list(zip_longest(x,y)) == [(1, 4), (2, 5), (3, 6), (None, 7)], list(zip_longest(x,y))


def test_itertools_cycle():
    from itertools import cycle
    x = [1,2,3]
    r = []
    for i in cycle(x):
        r.append(i)
        if len(r) > 10:
            break
    assert r == [1,2,3,1,2,3,1,2,3,1,2], r
    assert [x for x in cycle([])] == []


def test_class_with_contains():
    class HasContains():
        def __contains__(self, x):
            return True
    assert 1 in HasContains()


def test_class_with_odd_contains():
    class HasContains():
        def __contains__(self, x):
            return 12
    assert 1 in HasContains()


def test_class_with_none_contains():
    class HasContains():
        __contains__ = None
    try:
        1 in HasContains()
    except Exception as e:
        assert type(e) == TypeError, type(e)


def test_class_with_iter_no_contains():
    class HasIter():
        def __iter__(self):
            self.i = 0
            return self
        def __next__(self):
            self.i += 1
            return self.i

    assert 2 in HasIter()


def test_class_with_iter_no_contains_no_next():
    class HasIter():
        def __iter__(self):
            self.i = 0
            return self

    try:
        2 in HasIter()
    except Exception as e:
        assert type(e) is TypeError
