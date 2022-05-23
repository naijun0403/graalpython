# Copyright (c) 2017, 2022, Oracle and/or its affiliates.
# Copyright (c) 2013, Regents of the University of California
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.

import os

import mx

_graalpython_suite = mx.suite('graalpython')

py = ".py"
_BASE_PATH = os.path.join(_graalpython_suite.dir, 'graalpython', 'com.oracle.graal.python.benchmarks', 'python')
HARNESS_PATH = os.path.join(_BASE_PATH, 'harness.py')

PATH_BENCH = os.path.join(_BASE_PATH, 'com.oracle.graal.python.benchmarks')
PATH_MICRO = os.path.join(_BASE_PATH, 'micro')
PATH_MESO = os.path.join(_BASE_PATH, 'meso')
PATH_MACRO = os.path.join(_BASE_PATH, 'macro')
PATH_WARMUP = os.path.join(_BASE_PATH, 'warmup')
PATH_INTEROP = os.path.join(_BASE_PATH, 'host_interop')
PATH_JAVA_EMBEDDING = os.path.join(_BASE_PATH, 'java-embedding')

# ----------------------------------------------------------------------------------------------------------------------
#
# the python micro benchmarks
#
# ----------------------------------------------------------------------------------------------------------------------
# the argument list contains both the harness and benchmark args
ITER_100 = ['-i', '100']
ITER_50 = ['-i', '50']
ITER_35 = ['-i', '35']
ITER_25 = ['-i', '25']
ITER_20 = ['-i', '20']
ITER_15 = ['-i', '15']
ITER_10 = ['-i', '10']
ITER_6 = ['-i', '6']
WARMUP_2 = ['-w', '2']

MICRO_BENCHMARKS = {
    'arith-binop': ITER_10 + ['5'],
    'arith-modulo-sized': ITER_10 + ['500'],
    'attribute-access-polymorphic': ITER_10 + ['1000'],
    'attribute-access': ITER_10 + ['5000'],
    'attribute-access-super': ITER_10 + ['5_000'],
    'attribute-bool': ITER_10 + ['3000'],
    'boolean-logic-sized': ITER_10 + ['5_000'],
    'builtin-len-tuple-sized': ITER_10 + ['1_000_000_000'],
    'builtin-len': ITER_10 + [],
    'class-access': ITER_15 + ['10_000'],
    'call-method-polymorphic': ITER_10 + ['1000'],
    'for-range': ITER_15 + ['50000'],
    'function-call-sized': ITER_10 + ['2_000_000_000'],
    'generator-expression-sized': ITER_10 + ['30_000'],
    'generator-notaligned-sized': ITER_10 + ['30_000'],
    'generator-sized': ITER_10 + ['30_000'],
    'genexp-builtin-call-sized': ITER_10 + ['50_000'],
    'list-comp': ITER_10 + ['5000'],
    'list-indexing': ITER_10 + ['1000000'],
    'list-indexing-from-constructor': ITER_10 + ['10000000'],
    'list-indexing-from-literal': ITER_10 + ['10000000'],
    'list-iterating-explicit': ITER_10 + ['1000000'],
    'list-iterating': ITER_10 + ['1000000'],
    'list-iterating-obj-sized': ITER_10 + ['100_000_000'],
    'list-constructions-sized': ITER_10 + ['10_000'],
    'list-sort-objects': ITER_10 + ['10_000'],
    'list-sort-strings': ITER_10 + ['500_000'],
    'list-sort-keyed': ITER_10 + ['50_000'],
    'dict-getitem-sized': ITER_10 + ['50_000_000'],
    'math-sqrt': ITER_10 + ['500000000'],
    'object-allocate': ITER_10 + ['5000'],
    'object-layout-change': ITER_10 + ['1000000'],
    'special-add-int-sized': ITER_10 + ['20_000'],
    'special-add-sized': ITER_10 + ['20_000'],
    'special-len': ITER_10 + ['5'],
    'member-access': ITER_10 + ['5000'],
    'magic-bool-sized': ITER_10 + ['300_000_000'],
    'magic-iter': ITER_10 + ['50000000'],
    'call-classmethod-sized': ITER_10 + ['500_000_000'],
    'mmap-anonymous-sized': ITER_10 + ['20_000'],
    'mmap-file': ITER_10 + ['1000'],
    'generate-functions-sized': ITER_15 + ['500_000_000'],
    'try-except-simple': ITER_10 + ['500_000_000'],
    'try-except-store-simple': ITER_10 + ['500_000_000'],
    'try-except-store-two-types': ITER_10 + ['100_000_000'],
    'try-except-two-types': ITER_10 + ['100_000_000'],
    'tuple-indexing-from-constructor': ITER_10 + ['10000000'],
    'tuple-indexing-from-literal': ITER_10 + ['10000000'],
    'repeated-import': ITER_10 + ['10000000'],
    'codeobject-interpretation': ITER_10 + ['2000'],
}

MICRO_BENCHMARKS_SMALL = {
    'arith-modulo-sized': ITER_6 + WARMUP_2 + ['1'],
    'attribute-access-polymorphic': ITER_6 + WARMUP_2 + ['20'],
    'attribute-access': ITER_6 + WARMUP_2 + ['100'],
    'attribute-access-super': ITER_6 + WARMUP_2 + ['40'],
    'attribute-bool': ITER_6 + WARMUP_2 + ['2'],
    'boolean-logic-sized': ITER_6 + WARMUP_2 + ['10'],
    'builtin-len-tuple-sized': ITER_6 + WARMUP_2 + ['10_000_000'],
    'builtin-len': ITER_6 + WARMUP_2 + ['2_500_000'],
    'class-access': ITER_6 + WARMUP_2 + ['40'],
    'call-method-polymorphic': ITER_6 + WARMUP_2 + ['10'],
    'for-range': ITER_6 + WARMUP_2 + WARMUP_2 + ['50'],
    'function-call-sized': ITER_6 + WARMUP_2 + ['2_000_000'],
    'generator-expression-sized': ITER_6 + WARMUP_2 + ['3000', '500'],
    'generator-notaligned-sized': ITER_6 + WARMUP_2 + ['3000', '500'],
    'generator-sized': ITER_6 + WARMUP_2 + ['3000', '500'],
    'genexp-builtin-call-sized': ITER_6 + WARMUP_2 + ['3000', '500'],
    'list-comp': ITER_6 + WARMUP_2 + ['25'],
    'list-indexing': ITER_6 + WARMUP_2 + ['10_000'],
    'list-indexing-from-constructor': ITER_6 + WARMUP_2 + ['250_000'],
    'list-indexing-from-literal': ITER_6 + WARMUP_2 + ['250_000'],
    'list-iterating-explicit': ITER_6 + WARMUP_2 + ['10_000'],
    'list-iterating': ITER_6 + WARMUP_2 + ['25_000'],
    'list-iterating-obj-sized': ITER_6 + WARMUP_2 + ['1_000_000'],
    'list-constructions-sized': ITER_6 + WARMUP_2 + ['500'],
    'dict-getitem-sized': ITER_6 + WARMUP_2 + ['1_000_000'],
    'math-sqrt': ITER_6 + WARMUP_2 + ['20_000_000'],
    'object-allocate': ITER_6 + WARMUP_2 + ['50'],
    'object-layout-change': ITER_6 + WARMUP_2 + ['10_000'],
    'special-add-int-sized': ITER_6 + WARMUP_2 + ['1_000'],
    'special-add-sized': ITER_6 + WARMUP_2 + ['1_000'],
    'special-len': ITER_6 + WARMUP_2 + ['1', '1_000'],
    'member-access': ITER_6 + WARMUP_2 + ['10'],
    'magic-bool-sized': ITER_6 + WARMUP_2 + ['1_000_000'],
    'magic-iter': ITER_6 + WARMUP_2 + ['250000'],
    'call-classmethod-sized': ITER_6 + WARMUP_2 + ['1_000_000'],
    'mmap-anonymous-sized': ITER_6 + WARMUP_2 + ['1_000'],
    'mmap-file': ITER_6 + WARMUP_2 + ['100'],
    'generate-functions-sized': ITER_6 + WARMUP_2 + ['1_000_000'],
    'try-except-simple': ITER_6 + WARMUP_2 + ['2_500_000'],
    'try-except-store-simple': ITER_6 + WARMUP_2 + ['2_000_000'],
    'try-except-store-two-types': ITER_6 + WARMUP_2 + ['1_000_000'],
    'try-except-two-types': ITER_6 + WARMUP_2 + ['1_000_000'],
    'tuple-indexing-from-constructor': ITER_6 + WARMUP_2 + ['250_000'],
    'tuple-indexing-from-literal': ITER_6 + WARMUP_2 + ['400_000'],
}

def _pickling_benchmarks(module='pickle'):
    return {
        '{}-strings'.format(module): ITER_20 + ['4'],
        '{}-lists'.format(module): ITER_20 + ['4'],
        '{}-dicts'.format(module): ITER_20 + ['4'],
        '{}-objects'.format(module): ITER_20 + ['20'],
        '{}-funcs'.format(module): ITER_20 + ['30'],
    }


# MICRO_BENCHMARKS.update(_pickling_benchmarks('pickle'))
# MICRO_BENCHMARKS.update(_pickling_benchmarks('cPickle'))


MICRO_NATIVE_BENCHMARKS = {
    'c_member_access': ITER_10 + ['5'],
    'c-list-iterating-obj': ITER_10 + ['50000000'],
    'c-magic-bool': ITER_10 + ['100000000'],
    'c-magic-iter': ITER_10 + ['50000000'],
    'c_arith-binop': ITER_10 + ['5'],
    'c_arith_binop_2': ITER_10 + ['50'],
    'c-call-classmethod': ITER_10 + ['50000000'],
    'c-issubtype-polymorphic-forced-to-native': ITER_10 + ['50000000'],
    'c-issubtype-polymorphic': ITER_10 + ['50000000'],
    'c-issubtype-monorphic': ITER_10 + ['50000000'],
    'c-call-method': ITER_15 + ['5000000'],
    'c-instantiate-large': ITER_15 + ['1000'],
}


MESO_BENCHMARKS = {
    # -------------------------------------------------------
    # generator benchmarks
    # -------------------------------------------------------
    'euler31': ITER_10 + ['200'],
    'euler11': ITER_10 + ['10000'],
    'ai-nqueen': ITER_10 + ['10'],
    'pads-eratosthenes': ITER_10 + ['100000'],
    'pads-integerpartitions': ITER_10 + ['700'],
    'pads-bipartite-sized': ITER_10 + ['100_000'],
    'pads-lyndon': ITER_15 + ['10000000'],
    # -------------------------------------------------------
    # object benchmarks
    # -------------------------------------------------------
    'richards3': ITER_10 + ['200'],
    'bm-float': ITER_10 + ['1000'],
    # -------------------------------------------------------
    # normal benchmarks
    # -------------------------------------------------------
    'binarytrees3': ITER_25 + ['18'],
    'fannkuchredux3': ITER_10 + ['11'],
    'fasta3': ITER_10 + ['25000000'],
    'mandelbrot3': ITER_10 + ['4000'],
    'meteor3': ITER_15 + ['2098'],
    'nbody3': ITER_10 + ['5000000'],
    'spectralnorm3': ITER_10 + ['3000'],
    'pidigits': ITER_10 + [],
    'sieve-sized': ITER_15 + ['500_000'],
    'image-magix-sized2': ITER_10 + ['30000'],
    'parrot-b2': ITER_10 + ['200'],
    'threadring': ITER_25 + ['100_000_000'],
    'regexdna-sized2': ITER_25 + ['4'],
    'knucleotide': ITER_25 + [],
    'chaos-sized2': ITER_10 + ['500'],
    'go-sized2': ITER_15 + ['50'],
    'raytrace-simple': ITER_10 + [],
    'lud-sized2': ITER_10 + ['1536'],
    'mm-sized2': ITER_15 + ['350'],
    # Rodinia
    'backprop_rodinia-sized2': ITER_15 + ['8388608'],
    'lavaMD_rodinia-sized2': ITER_15 + ['48'],
    'pathfinder_rodinia-sized2': ITER_25 + ['50'],
    'particlefilter_rodinia': ITER_10 + ['2048'],
    'srad_rodinia-sized2': ITER_15 + ['1000'],
}


MESO_BENCHMARKS_SMALL = {
    'pads-eratosthenes': ITER_10 + WARMUP_2 + ['2000'],
    'richards3': ITER_10 + WARMUP_2 + ['2'],
    'chaos': ITER_10 + WARMUP_2 + ['2'],
    'image-magix': ITER_10 + WARMUP_2 + ['100'],
    'raytrace-simple': ITER_10 + WARMUP_2 + ['110', '110'],
}


MACRO_BENCHMARKS = {
    'gcbench': ITER_10 + ['10'],
}


WARMUP_BENCHMARKS = {
    'gcbench': ITER_100 + ["--startup=1,10,100"] + ['10'],
    'binarytrees3': ITER_100 + ["--startup=1,10,100"] + ['18'],
    'pads-integerpartitions': ITER_100  + ["--startup=1,10,100"] + ['700'],
}


INTEROP_BENCHMARKS = {
    'euler_java': ITER_10 + ['200'],
    'image-magix': ITER_10 + ['10000'],
    'image-magix-java': ITER_10 + ['10000'],
}


_INTEROP_JAVA_PACKAGE = 'com.oracle.graal.python.benchmarks.interop.'
INTEROP_JAVA_BENCHMARKS = {
    'richards3': [_INTEROP_JAVA_PACKAGE + 'PyRichards'] + MESO_BENCHMARKS['richards3'],
    'euler31': [_INTEROP_JAVA_PACKAGE + 'PyEuler31'] + MESO_BENCHMARKS['euler31'],
    'euler11': [_INTEROP_JAVA_PACKAGE + 'PyEuler11'] + MESO_BENCHMARKS['euler11'],
    'nbody3': [_INTEROP_JAVA_PACKAGE + 'PyNbody'] + MESO_BENCHMARKS['nbody3'],
    'fannkuchredux3': [_INTEROP_JAVA_PACKAGE + 'PyFannkuchredux'] + MESO_BENCHMARKS['fannkuchredux3'],
}

JAVA_EMBEDDING_MESO_BENCHMARKS = {
    'chaos': ITER_6 + WARMUP_2 + [],
    'richards3': ITER_6 + WARMUP_2 + [],
    'image-magix': ITER_6 + WARMUP_2 + [],
    'raytrace-simple': ITER_6 + WARMUP_2 + [],
}

JAVA_EMBEDDING_MESO_BENCHMARKS_SMALL = {
    'chaos': ITER_6 + WARMUP_2 + ['--', '1'],
    'richards3': ITER_6 + WARMUP_2 + ['--', '2'],
    'image-magix': ITER_6 + WARMUP_2 + ['--', '100'],
    'raytrace-simple': ITER_6 + WARMUP_2 + ['--', '110', '110'],
}

# -------------------------------------------------------
# Parameters for parsing bench marks
# Parameters for parsing bench marks
# 1. full qualified name of class that define the benchmark
# 2. -i number : is number of benchmark iterations (defualt 5)
# 3. -w number : is number of warmup iterations, in these cases should not be necessary (default 0)
# 4. -n number : is number of parsing cycles of on file in one iteration (default 1)
# 5. -r : if it's present, then folders on the defined paths are parsed recursively
# 6. : files or directories, that will be processed
# 7. -e : after this mark all the listed files and directories are excluded from the benchmark
# 8. : files or directories that will be excluded from the benchmark
# -------------------------------------------------------
_PARSER_JAVA_PACKAGE = 'com.oracle.graal.python.benchmarks.parser.'
PATH_RUNTIME_FILES_PARSER_TESTS = os.path.join(_graalpython_suite.dir, 'graalpython', 'com.oracle.graal.python.test', 'testData', 'testFiles', 'RuntimeFileTests')
PATH_PYTHON_LIB = os.path.join(_graalpython_suite.dir, 'graalpython', 'lib-python', '3')
PARSER_JAVA_BENCHMARKS = {
    'whole-parsing-test-files': [_PARSER_JAVA_PACKAGE + 'ParsingAndTranslating'] + ITER_10 + ['-n', '10'] + [PATH_RUNTIME_FILES_PARSER_TESTS],
    'whole-parsing-lib-files': [_PARSER_JAVA_PACKAGE + 'ParsingAndTranslating'] + ITER_10 + ['-r'] + [PATH_PYTHON_LIB],
    'antlr-parsing-lib-files': [_PARSER_JAVA_PACKAGE + 'AntlrParsing'] + ITER_10 + ['-r'] + [PATH_PYTHON_LIB],
    'sst-translating-lib-files': [_PARSER_JAVA_PACKAGE + 'SSTTranslating'] + ITER_10 + ['-r'] + [PATH_PYTHON_LIB],
    'serializing-lib-files': [_PARSER_JAVA_PACKAGE + 'Serializing'] + ITER_10 + ['-r'] + [PATH_PYTHON_LIB],
    'deserializing-lib-files': [_PARSER_JAVA_PACKAGE + 'Deserializing'] + ITER_10 + ['-r'] + [PATH_PYTHON_LIB],
}

# ----------------------------------------------------------------------------------------------------------------------
#
# the benchmarks
#
# ----------------------------------------------------------------------------------------------------------------------
BENCHMARKS = {
    "micro": [PATH_MICRO, MICRO_BENCHMARKS],
    "micro-native": [PATH_MICRO, MICRO_NATIVE_BENCHMARKS],
    "meso": [PATH_MESO, MESO_BENCHMARKS],
    "macro": [PATH_MACRO, MACRO_BENCHMARKS],
    "interop": [PATH_INTEROP, INTEROP_BENCHMARKS],
    "micro-small": [PATH_MICRO, MICRO_BENCHMARKS_SMALL],
    "meso-small": [PATH_MESO, MESO_BENCHMARKS_SMALL],
}

JAVA_DRIVER_BENCHMARKS = {
    "java-embedding-meso": [PATH_MESO, JAVA_EMBEDDING_MESO_BENCHMARKS],
    "java-embedding-meso-small": [PATH_MESO, JAVA_EMBEDDING_MESO_BENCHMARKS_SMALL],
}

WARMUP_BENCHMARKS = {
    "python-warmup": [PATH_WARMUP, WARMUP_BENCHMARKS],
}

JBENCHMARKS = {
    "pyjava": [INTEROP_JAVA_BENCHMARKS],
}

PARSER_BENCHMARKS = {
    "python-parser" : [PARSER_JAVA_BENCHMARKS],
}
