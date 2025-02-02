/* MIT License
 *
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2019 pyhandle
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef HPY_UNIVERSAL_HPYTYPE_H
#define HPY_UNIVERSAL_HPYTYPE_H

#include <stdbool.h>
#ifdef __GNUC__
#define HPyAPI_UNUSED __attribute__((unused)) static inline
#else
#define HPyAPI_UNUSED static inline
#endif /* __GNUC__ */

typedef struct {
    const char* name;
    int basicsize;
    int itemsize;
    unsigned long flags;
    /*
       A type whose struct starts with PyObject_HEAD is a legacy type. A
       legacy type must set .legacy = true in its HPyType_Spec.
       A type is a non-legacy type, also called HPy pure type, if its struct
       does not include PyObject_HEAD. Using pure types should be preferred.
       Legacy types are available to allow gradual porting of existing CPython
       extensions.

       A type with .legacy_slots not NULL is required to have .legacy = true and to
       include PyObject_HEAD at the start of its struct. It would be easy to
       relax this requirement on CPython (where the PyObject_HEAD fields are
       always present) but a large burden on other implementations (e.g. PyPy,
       GraalPython) where a struct starting with PyObject_HEAD might not exist.

       Types that do not define a struct of their own, should set the value of
       .legacy to the same value as the type they inherit from. If they inherit
       from a built-in type, they may set .legacy to either true or false, depending
       on whether they still use .legacy_slots or not.

       Pure HPy types that inherit a builtin type and define their own struct are
       not supported at the moment. One can use legacy types in the meanwhile.

       Types created via the old Python C API are automatically legacy types.
     */
    int legacy;
    void *legacy_slots; // PyType_Slot *
    HPyDef **defines;   /* points to an array of 'HPyDef *' */
    const char* doc;    /* UTF-8 doc string or NULL */
} HPyType_Spec;

typedef enum {
    HPyType_SpecParam_Base = 1,
    HPyType_SpecParam_BasesTuple = 2,
    //HPyType_SpecParam_Metaclass = 3,
    //HPyType_SpecParam_Module = 4,
} HPyType_SpecParam_Kind;

typedef struct {
    HPyType_SpecParam_Kind kind;
    HPy object;
} HPyType_SpecParam;

/* All types are dynamically allocated */
#define _Py_TPFLAGS_HEAPTYPE (1UL << 9)
#define _Py_TPFLAGS_HAVE_VERSION_TAG (1UL << 18)
#define HPy_TPFLAGS_DEFAULT (_Py_TPFLAGS_HEAPTYPE | _Py_TPFLAGS_HAVE_VERSION_TAG)

/* Set if the type allows subclassing */
#define HPy_TPFLAGS_BASETYPE (1UL << 10)

/* If set, the object will be tracked by CPython's GC. Probably irrelevant for
   GC-based alternative implementations */
#define HPy_TPFLAGS_HAVE_GC (1UL << 14)

/* A macro for creating (static inline) helper functions for custom types.

   Two versions of the helper exist. One for legacy types and one for pure
   HPy types.

   Example for a pure HPy custom type:

       HPyType_HELPERS(PointObject)

   This would generate the following:

   * `PointObject * PointObject_AsStruct(HPyContext *ctx, HPy h)`: a static
     inline function that uses HPy_AsStruct to return the PointObject struct
     associated with a given handle. The behaviour is undefined if `h`
     is associated with an object that is not an instance of PointObject.

   * `PointObject_IS_LEGACY`: an enum value set to 0 so that in the
     HPyType_Spec for PointObject one can write
     `.legacy = PointObject_IS_LEGACY` and not have to remember to
     update the spec when the helpers used changes.

   Example for a legacy custom type:

       HPyType_LEGACY_HELPERS(PointObject)

   This would generate the same functions and constants as above, except:

   * `HPy_AsStructLegacy` is used instead of `HPy_AsStruct`.

   * `PointObject_IS_LEGACY` is set to 1.
*/

#define HPyType_HELPERS(TYPE) \
    _HPyType_GENERIC_HELPERS(TYPE, HPy_AsStruct, 0)

#define HPyType_LEGACY_HELPERS(TYPE) \
    _HPyType_GENERIC_HELPERS(TYPE, HPy_AsStructLegacy, 1)

#define _HPyType_GENERIC_HELPERS(TYPE, CAST, IS_LEGACY)              \
                                                                     \
enum { TYPE##_IS_LEGACY = IS_LEGACY };                               \
                                                                     \
HPyAPI_UNUSED TYPE *                                                 \
TYPE##_AsStruct(HPyContext *ctx, HPy h)                              \
{                                                                    \
    return (TYPE*) CAST(ctx, h);                                     \
}

#endif /* HPY_UNIVERSAL_HPYTYPE_H */
