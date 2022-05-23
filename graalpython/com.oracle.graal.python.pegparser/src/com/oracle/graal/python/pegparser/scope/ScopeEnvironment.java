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
package com.oracle.graal.python.pegparser.scope;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Stack;

import com.oracle.graal.python.pegparser.ExprContext;
import com.oracle.graal.python.pegparser.scope.Scope.DefUse;
import com.oracle.graal.python.pegparser.scope.Scope.ScopeFlags;
import com.oracle.graal.python.pegparser.scope.Scope.ScopeType;
import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.SSTreeVisitor;
import com.oracle.graal.python.pegparser.sst.StmtTy;

/**
 * Roughly plays the role of CPython's {@code symtable}.
 *
 * Just like in CPython, the scope analysis uses two passes. The first simply visits everything and
 * creates {@link Scope} objects with some facts about the names. The second pass determines based
 * on the scope nestings which names are free, cells etc.
 */
public class ScopeEnvironment {
    final Scope topScope;
    final HashMap<SSTNode, Scope> blocks = new HashMap<>();

    public ScopeEnvironment(ModTy moduleNode) {
        // First pass, similar to the entry point `symtable_enter_block' on CPython
        FirstPassVisitor visitor = new FirstPassVisitor(moduleNode, this);
        topScope = visitor.currentScope;
        moduleNode.accept(visitor);

        // Second pass
        analyzeBlock(topScope, null, null, null);
    }

    @Override
    public String toString() {
        return "ScopeEnvironment\n" + topScope.toString(1);
    }

    private void addScope(SSTNode node, Scope scope) {
        blocks.put(node, scope);
    }

    public Scope lookupScope(SSTNode node) {
        return blocks.get(node);
    }

    private static void analyzeBlock(Scope scope, HashSet<String> bound, HashSet<String> free, HashSet<String> global) {
        HashSet<String> local = new HashSet<>();
        HashMap<String, DefUse> scopes = new HashMap<>();
        HashSet<String> newGlobal = new HashSet<>();
        HashSet<String> newFree = new HashSet<>();
        HashSet<String> newBound = new HashSet<>();

        if (scope.type == ScopeType.Class) {
            if (global != null) {
                newGlobal.addAll(global);
            }
            if (bound != null) {
                newBound.addAll(bound);
            }
        }

        for (Entry<String, EnumSet<DefUse>> e : scope.symbols.entrySet()) {
            analyzeName(scope, scopes, e.getKey(), e.getValue(), bound, local, free, global);
        }

        if (scope.type != ScopeType.Class) {
            if (scope.type == ScopeType.Function) {
                newBound.addAll(local);
            }
            if (bound != null) {
                newBound.addAll(bound);
            }
            if (global != null) {
                newGlobal.addAll(global);
            }
        } else {
            newBound.add("__class__");
        }

        HashSet<String> allFree = new HashSet<>();
        for (Scope s : scope.children) {
            // inline the logic from CPython's analyze_child_block
            HashSet<String> tempBound = new HashSet<>(newBound);
            HashSet<String> tempFree = new HashSet<>(newFree);
            HashSet<String> tempGlobal = new HashSet<>(newGlobal);
            analyzeBlock(s, tempBound, tempFree, tempGlobal);
            allFree.addAll(tempFree);
            if (s.flags.contains(ScopeFlags.HasFreeVars) || s.flags.contains(ScopeFlags.HasChildWithFreeVars)) {
                scope.flags.add(ScopeFlags.HasChildWithFreeVars);
            }
        }

        newFree.addAll(allFree);

        switch (scope.type) {
            case Function:
                analyzeCells(scopes, newFree);
                break;
            case Class:
                dropClassFree(scope, newFree);
                break;
            default:
                break;
        }

        updateSymbols(scope.symbols, scopes, bound, newFree, scope.type == ScopeType.Class);

        if (free != null) {
            free.addAll(newFree);
        }
    }

    private static void analyzeName(Scope scope, HashMap<String, DefUse> scopes, String name, EnumSet<DefUse> flags, HashSet<String> bound, HashSet<String> local, HashSet<String> free,
                    HashSet<String> global) {
        if (flags.contains(DefUse.DefGlobal)) {
            if (flags.contains(DefUse.DefNonLocal)) {
                // TODO: SyntaxError:
                // "name '%s' is nonlocal and global", name
            }
            scopes.put(name, DefUse.GlobalExplicit);
            if (global != null) {
                global.add(name);
            }
            if (bound != null) {
                bound.remove(name);
            }
        } else if (flags.contains(DefUse.DefNonLocal)) {
            if (bound == null) {
                // TODO: SyntaxError:
                // "nonlocal declaration not allowed at module level"
            } else if (!bound.contains(name)) {
                // TODO: SyntaxError:
                // "no binding for nonlocal '%s' found", name
            }
            scopes.put(name, DefUse.Free);
            scope.flags.add(ScopeFlags.HasFreeVars);
            free.add(name);
        } else if (!Collections.disjoint(flags, DefUse.DefBound)) {
            scopes.put(name, DefUse.Local);
            local.add(name);
            if (global != null) {
                global.remove(name);
            }
        } else if (bound != null && bound.contains(name)) {
            scopes.put(name, DefUse.Free);
            scope.flags.add(ScopeFlags.HasFreeVars);
            free.add(name);
        } else if (global != null && global.contains(name)) {
            scopes.put(name, DefUse.GlobalImplicit);
        } else {
            if (scope.flags.contains(ScopeFlags.IsNested)) {
                scope.flags.add(ScopeFlags.HasFreeVars);
            }
            scopes.put(name, DefUse.GlobalImplicit);
        }
    }

    private static void analyzeCells(HashMap<String, DefUse> scopes, HashSet<String> free) {
        for (Entry<String, DefUse> e : scopes.entrySet()) {
            if (e.getValue() != DefUse.Local) {
                continue;
            }
            String name = e.getKey();
            if (!free.contains(name)) {
                continue;
            }
            scopes.put(name, DefUse.Cell);
            free.remove(name);
        }
    }

    private static void dropClassFree(Scope scope, HashSet<String> free) {
        if (free.remove("__class__")) {
            scope.flags.add(ScopeFlags.NeedsClassClosure);
        }
    }

    private static void updateSymbols(HashMap<String, EnumSet<DefUse>> symbols, HashMap<String, DefUse> scopes, HashSet<String> bound, HashSet<String> free, boolean isClass) {
        for (Entry<String, EnumSet<DefUse>> e : symbols.entrySet()) {
            String name = e.getKey();
            DefUse vScope = scopes.get(name);
            assert !vScope.toString().startsWith("Def");
            // CPython now stores the VariableScope into the DefUse flags at a shifted offset
            e.getValue().add(vScope);
        }

        for (String name : free) {
            EnumSet<DefUse> v = symbols.get(name);
            if (v != null) {
                if (isClass && (v.contains(DefUse.DefGlobal) || !Collections.disjoint(v, DefUse.DefBound))) {
                    v.add(DefUse.DefFreeClass);
                }
            } else if (bound != null && !bound.contains(name)) {
            } else {
                symbols.put(name, EnumSet.of(DefUse.Free));
            }
        }
    }

    public static String mangle(String className, String name) {
        if (className == null || !name.startsWith("__")) {
            return name;
        }
        if (name.endsWith("__") || name.contains(".")) {
            return name;
        }
        int offset = 0;
        while (className.charAt(offset) == '_') {
            offset++;
            if (offset >= className.length()) {
                return name;
            }
        }
        return "_" + className.substring(offset) + name;
    }

    private static final class FirstPassVisitor implements SSTreeVisitor<Void> {
        private final Stack<Scope> stack;
        private final HashMap<String, EnumSet<DefUse>> globals;
        private final ScopeEnvironment env;
        private Scope currentScope;
        private String currentClassName;

        private FirstPassVisitor(ModTy moduleNode, ScopeEnvironment env) {
            this.stack = new Stack<>();
            this.env = env;
            enterBlock(null, Scope.ScopeType.Module, moduleNode);
            this.globals = this.currentScope.symbols;
        }

        private void enterBlock(String name, Scope.ScopeType type, SSTNode ast) {
            Scope scope = new Scope(name, type, ast);
            env.addScope(ast, scope);
            stack.add(scope);
            if (type == Scope.ScopeType.Annotation) {
                return;
            }
            if (currentScope != null) {
                scope.comprehensionIterExpression = currentScope.comprehensionIterExpression;
                currentScope.children.add(scope);
            }
            currentScope = scope;
        }

        private void exitBlock() {
            stack.pop();
            currentScope = stack.peek();
        }

        private String mangle(String name) {
            return ScopeEnvironment.mangle(currentClassName, name);
        }

        private void addDef(String name, DefUse flag) {
            addDef(name, flag, currentScope);
        }

        private void addDef(String name, DefUse flag, Scope scope) {
            String mangled = mangle(name);
            EnumSet<DefUse> flags = scope.getUseOfName(mangled);
            if (flags != null) {
                if (flag == DefUse.DefParam && flags.contains(DefUse.DefParam)) {
                    // TODO: raises SyntaxError:
                    // "duplicate argument '%s' in function definition", name
                }
                flags.add(flag);
            } else {
                flags = EnumSet.of(flag);
            }
            if (scope.flags.contains(ScopeFlags.IsVisitingIterTarget)) {
                if (flags.contains(DefUse.DefGlobal) || flags.contains(DefUse.DefNonLocal)) {
                    // TODO: raises SyntaxError:
                    // "comprehension inner loop cannot rebind assignment expression target '%s'",
                    // name
                }
                flags.add(DefUse.DefCompIter);
            }
            scope.symbols.put(mangled, flags);
            switch (flag) {
                case DefParam:
                    scope.varnames.add(mangled);
                    break;
                case DefGlobal:
                    EnumSet<DefUse> globalFlags = globals.get(mangled);
                    if (globalFlags != null) {
                        globalFlags.add(flag);
                    } else {
                        globalFlags = EnumSet.of(flag);
                    }
                    globals.put(mangled, globalFlags);
                    break;
                default:
                    break;
            }
        }

        private void handleComprehension(ExprTy e, String scopeName, ComprehensionTy[] generators, ExprTy element, ExprTy value) {
            boolean isGenerator = e instanceof ExprTy.GeneratorExp;
            ComprehensionTy outermost = generators[0];
            currentScope.comprehensionIterExpression++;
            outermost.iter.accept(this);
            currentScope.comprehensionIterExpression--;
            enterBlock(scopeName, Scope.ScopeType.Function, e);
            try {
                if (outermost.isAsync) {
                    currentScope.flags.add(ScopeFlags.IsCoroutine);
                }
                currentScope.flags.add(ScopeFlags.IsComprehension);
                addDef(".0", DefUse.DefParam);
                currentScope.flags.add(ScopeFlags.IsVisitingIterTarget);
                outermost.target.accept(this);
                currentScope.flags.remove(ScopeFlags.IsVisitingIterTarget);
                visitSequence(outermost.ifs);
                for (int i = 1; i < generators.length; i++) {
                    generators[i].accept(this);
                }
                if (value != null) {
                    value.accept(this);
                }
                element.accept(this);
                if (currentScope.flags.contains(ScopeFlags.IsGenerator)) {
                    // TODO: syntax error 'yield' inside something
                }
                if (isGenerator) {
                    currentScope.flags.add(ScopeFlags.IsGenerator);
                }
            } finally {
                exitBlock();
            }
        }

        private void visitAnnotation(ExprTy expr) {
            enterBlock("_annotation", ScopeType.Annotation, expr);
            try {
                expr.accept(this);
            } finally {
                exitBlock();
            }
        }

        private void visitAnnotations(ArgTy[] args) {
            if (args != null) {
                for (ArgTy arg : args) {
                    if (arg.annotation != null) {
                        arg.annotation.accept(this);
                    }
                }
            }
        }

        private void visitAnnotations(StmtTy node, ArgumentsTy args, ExprTy returns) {
            if (args != null) {
                enterBlock("_annotation", ScopeType.Annotation, node);
                try {
                    visitAnnotations(args.posOnlyArgs);
                    visitAnnotations(args.args);
                    if (args.varArg != null && args.varArg.annotation != null) {
                        args.varArg.annotation.accept(this);
                    }
                    if (args.kwArg != null && args.kwArg.annotation != null) {
                        args.kwArg.annotation.accept(this);
                    }
                    visitAnnotations(args.kwOnlyArgs);
                } finally {
                    exitBlock();
                }
            }
            if (returns != null) {
                visitAnnotation(returns);
            }
        }

        @Override
        public Void visit(AliasTy node) {
            String importedName = node.asName == null ? node.name : node.asName;
            int dotIndex = importedName.indexOf('.');
            if (dotIndex >= 0) {
                importedName = importedName.substring(0, dotIndex);
            }
            if ("*".equals(importedName)) {
                if (!currentScope.isModule()) {
                    // TODO: syntax error: IMPORT_STAR not in module scope
                }
            } else {
                addDef(importedName, DefUse.DefImport);
            }
            return null;
        }

        @Override
        public Void visit(ArgTy node) {
            addDef(node.arg, DefUse.DefParam);
            return null;
        }

        @Override
        public Void visit(ArgumentsTy node) {
            visitSequence(node.posOnlyArgs);
            visitSequence(node.args);
            visitSequence(node.kwOnlyArgs);
            if (node.varArg != null) {
                node.varArg.accept(this);
                currentScope.flags.add(ScopeFlags.HasVarArgs);
            }
            if (node.kwArg != null) {
                node.kwArg.accept(this);
                currentScope.flags.add(ScopeFlags.HasVarKeywords);
            }
            return null;
        }

        @Override
        public Void visit(ExprTy.Attribute node) {
            node.value.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.Await node) {
            if (currentScope.type == ScopeType.Annotation) {
                // TODO: raise syntax error
            }
            node.value.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.BinOp node) {
            node.left.accept(this);
            node.right.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.BoolOp node) {
            visitSequence(node.values);
            return null;
        }

        @Override
        public Void visit(ExprTy.Call node) {
            node.func.accept(this);
            visitSequence(node.args);
            visitSequence(node.keywords);
            return null;
        }

        @Override
        public Void visit(ExprTy.Compare node) {
            node.left.accept(this);
            visitSequence(node.comparators);
            return null;
        }

        @Override
        public Void visit(ExprTy.Constant node) {
            return null;
        }

        @Override
        public Void visit(ExprTy.Dict node) {
            visitSequence(node.keys);
            visitSequence(node.values);
            return null;
        }

        @Override
        public Void visit(ExprTy.DictComp node) {
            handleComprehension(node, "dictcomp", node.generators, node.key, node.value);
            return null;
        }

        @Override
        public Void visit(ExprTy.FormattedValue node) {
            node.value.accept(this);
            if (node.formatSpec != null) {
                node.formatSpec.accept(this);
            }
            return null;
        }

        @Override
        public Void visit(ExprTy.GeneratorExp node) {
            handleComprehension(node, "genexp", node.generators, node.element, null);
            return null;
        }

        @Override
        public Void visit(ExprTy.IfExp node) {
            node.test.accept(this);
            node.body.accept(this);
            node.orElse.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.JoinedStr node) {
            visitSequence(node.values);
            return null;
        }

        @Override
        public Void visit(ExprTy.Lambda node) {
            if (node.args != null) {
                visitSequence(node.args.defaults);
                visitSequence(node.args.kwDefaults);
            }
            enterBlock("lambda", ScopeType.Function, node);
            try {
                if (node.args != null) {
                    node.args.accept(this);
                }
                node.body.accept(this);
            } finally {
                exitBlock();
            }
            return null;
        }

        @Override
        public Void visit(ExprTy.List node) {
            visitSequence(node.elements);
            return null;
        }

        @Override
        public Void visit(ExprTy.ListComp node) {
            handleComprehension(node, "listcomp", node.generators, node.element, null);
            return null;
        }

        @Override
        public Void visit(ExprTy.Name node) {
            addDef(node.id, node.context == ExprContext.Load ? DefUse.Use : DefUse.DefLocal);
            // Special-case super: it counts as a use of __class__
            if (node.context == ExprContext.Load && currentScope.type == ScopeType.Function &&
                            node.id.equals("super")) {
                addDef("__class__", DefUse.Use);
            }
            return null;
        }

        @Override
        public Void visit(ExprTy.NamedExpr node) {
            if (currentScope.type == ScopeType.Annotation) {
                // TODO: raise syntax error
            }
            if (currentScope.comprehensionIterExpression > 0) {
                // TODO: raise syntax error NAMED_EXPR_COMP_ITER_EXPR
            }
            if (currentScope.flags.contains(ScopeFlags.IsComprehension)) {
                // symtable_extend_namedexpr_scope
                String targetName = ((ExprTy.Name) node.target).id;
                for (int i = stack.size() - 1; i >= 0; i--) {
                    Scope s = stack.get(i);
                    // If we find a comprehension scope, check for conflict
                    if (s.flags.contains(ScopeFlags.IsComprehension)) {
                        if (s.getUseOfName(targetName).contains(DefUse.DefCompIter)) {
                            // TODO: raise NAMED_EXPR_COMP_CONFLICT
                        }
                        continue;
                    }
                    // If we find a FunctionBlock entry, add as GLOBAL/LOCAL or NONLOCAL/LOCAL
                    if (s.type == ScopeType.Function) {
                        EnumSet<DefUse> uses = s.getUseOfName(targetName);
                        if (uses.contains(DefUse.DefGlobal)) {
                            addDef(targetName, DefUse.DefGlobal);
                        } else {
                            addDef(targetName, DefUse.DefNonLocal);
                        }
                        currentScope.recordDirective(mangle(targetName), node.getStartOffset(), node.getEndOffset());
                        addDef(targetName, DefUse.DefLocal, s);
                        break;
                    }
                    // If we find a ModuleBlock entry, add as GLOBAL
                    if (s.type == ScopeType.Module) {
                        addDef(targetName, DefUse.DefGlobal);
                        currentScope.recordDirective(mangle(targetName), node.getStartOffset(), node.getEndOffset());
                        addDef(targetName, DefUse.DefGlobal, s);
                        break;
                    }
                    // Disallow usage in ClassBlock
                    if (s.type == ScopeType.Class) {
                        // TODO: Syntax error NAMED_EXPR_COMP_IN_CLASS
                    }
                }
            }
            node.value.accept(this);
            node.target.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.Set node) {
            visitSequence(node.elements);
            return null;
        }

        @Override
        public Void visit(ExprTy.SetComp node) {
            handleComprehension(node, "setcomp", node.generators, node.element, null);
            return null;
        }

        @Override
        public Void visit(ExprTy.Slice node) {
            if (node.lower != null) {
                node.lower.accept(this);
            }
            if (node.upper != null) {
                node.upper.accept(this);
            }
            if (node.step != null) {
                node.step.accept(this);
            }
            return null;
        }

        @Override
        public Void visit(ExprTy.Starred node) {
            node.value.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.Subscript node) {
            node.value.accept(this);
            node.slice.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.Tuple node) {
            visitSequence(node.elements);
            return null;
        }

        @Override
        public Void visit(ExprTy.UnaryOp node) {
            node.operand.accept(this);
            return null;
        }

        @Override
        public Void visit(ExprTy.Yield node) {
            if (currentScope.type == ScopeType.Annotation) {
                // TODO: raise syntax error
            }
            if (node.value != null) {
                node.value.accept(this);
            }
            currentScope.flags.add(ScopeFlags.IsGenerator);
            return null;
        }

        @Override
        public Void visit(ExprTy.YieldFrom node) {
            if (currentScope.type == ScopeType.Annotation) {
                // TODO: raise syntax error
            }
            if (node.value != null) {
                node.value.accept(this);
            }
            currentScope.flags.add(ScopeFlags.IsGenerator);
            return null;
        }

        @Override
        public Void visit(KeywordTy node) {
            node.value.accept(this);
            return null;
        }

        @Override
        public Void visit(ModTy.Expression node) {
            node.body.accept(this);
            return null;
        }

        @Override
        public Void visit(ModTy.FunctionType node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(ModTy.Interactive node) {
            visitSequence(node.body);
            return null;
        }

        @Override
        public Void visit(ModTy.Module node) {
            visitSequence(node.body);
            return null;
        }

        @Override
        public Void visit(ModTy.TypeIgnore node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.AnnAssign node) {
            if (node.target instanceof ExprTy.Name) {
                ExprTy.Name name = (ExprTy.Name) node.target;
                EnumSet<DefUse> cur = currentScope.getUseOfName(mangle(name.id));
                if (cur.contains(DefUse.DefGlobal) || cur.contains(DefUse.DefNonLocal) &&
                                currentScope.symbols != globals &&
                                node.isSimple) {
                    // TODO: syntax error GLOBAL_ANNOT : NONLOCAL_ANNOT
                }
                if (node.isSimple) {
                    addDef(name.id, DefUse.DefAnnot);
                    addDef(name.id, DefUse.DefLocal);
                } else {
                    if (node.value != null) {
                        addDef(name.id, DefUse.DefLocal);
                    }
                }
            } else {
                node.target.accept(this);
            }
            visitAnnotation(node.annotation);
            if (node.value != null) {
                node.value.accept(this);
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Assert node) {
            node.test.accept(this);
            if (node.msg != null) {
                node.msg.accept(this);
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Assign node) {
            visitSequence(node.targets);
            node.value.accept(this);
            return null;
        }

        @Override
        public Void visit(StmtTy.AsyncFor node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.AsyncFunctionDef node) {
            addDef(node.name, DefUse.DefLocal);
            if (node.args != null) {
                visitSequence(node.args.defaults);
                visitSequence(node.args.kwDefaults);
            }
            visitAnnotations(node, node.args, node.returns);
            visitSequence(node.decoratorList);
            enterBlock(node.name, ScopeType.Function, node);
            try {
                currentScope.flags.add(ScopeFlags.IsCoroutine);
                if (node.args != null) {
                    node.args.accept(this);
                }
                visitSequence(node.body);
            } finally {
                exitBlock();
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.AsyncWith node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.AugAssign node) {
            node.target.accept(this);
            node.value.accept(this);
            return null;
        }

        @Override
        public Void visit(StmtTy.ClassDef node) {
            addDef(node.name, DefUse.DefLocal);
            visitSequence(node.bases);
            visitSequence(node.keywords);
            visitSequence(node.decoratorList);
            String tmp = currentClassName;
            enterBlock(node.name, ScopeType.Class, node);
            try {
                currentClassName = node.name;
                visitSequence(node.body);
            } finally {
                currentClassName = tmp;
                exitBlock();
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Delete node) {
            visitSequence(node.targets);
            return null;
        }

        @Override
        public Void visit(StmtTy.Expr node) {
            node.value.accept(this);
            return null;
        }

        @Override
        public Void visit(StmtTy.For node) {
            node.target.accept(this);
            node.iter.accept(this);
            visitSequence(node.body);
            visitSequence(node.orElse);
            return null;
        }

        @Override
        public Void visit(StmtTy.FunctionDef node) {
            addDef(node.name, DefUse.DefLocal);
            if (node.args != null) {
                visitSequence(node.args.defaults);
                visitSequence(node.args.kwDefaults);
            }
            visitAnnotations(node, node.args, node.returns);
            visitSequence(node.decoratorList);
            enterBlock(node.name, ScopeType.Function, node);
            try {
                if (node.args != null) {
                    node.args.accept(this);
                }
                visitSequence(node.body);
            } finally {
                exitBlock();
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Global node) {
            for (String n : node.names) {
                // EnumSet<DefUse> f = currentScope.symbols.get(mangle(n));
                // TODO: error if DEF_PARAM | DEF_LOCAL | USE | DEF_ANNOT
                addDef(n, DefUse.DefGlobal);
                currentScope.recordDirective(n, node.getStartOffset(), node.getEndOffset());
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.If node) {
            node.test.accept(this);
            visitSequence(node.body);
            visitSequence(node.orElse);
            return null;
        }

        @Override
        public Void visit(StmtTy.Import node) {
            visitSequence(node.names);
            return null;
        }

        @Override
        public Void visit(StmtTy.ImportFrom node) {
            visitSequence(node.names);
            return null;
        }

        @Override
        public Void visit(StmtTy.Match node) {
            node.subject.accept(this);
            visitSequence(node.cases);
            return null;
        }

        @Override
        public Void visit(StmtTy.Match.Case node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchAs node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchClass node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchMapping node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchOr node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchSequence node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchSingleton node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchStar node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.Match.Pattern.MatchValue node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Void visit(StmtTy.NonLocal node) {
            for (String n : node.names) {
                // EnumSet<DefUse> f = currentScope.symbols.get(mangle(n));
                // TODO: error if DEF_PARAM | DEF_LOCAL | USE | DEF_ANNOT
                addDef(n, DefUse.DefNonLocal);
                currentScope.recordDirective(n, node.getStartOffset(), node.getEndOffset());
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Raise node) {
            if (node.exc != null) {
                node.exc.accept(this);
                if (node.cause != null) {
                    node.cause.accept(this);
                }
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Return node) {
            if (node.value != null) {
                node.value.accept(this);
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Try node) {
            visitSequence(node.body);
            visitSequence(node.orElse);
            visitSequence(node.handlers);
            visitSequence(node.finalBody);
            return null;
        }

        @Override
        public Void visit(StmtTy.Try.ExceptHandler node) {
            if (node.type != null) {
                node.type.accept(this);
            }
            if (node.name != null) {
                addDef(node.name, DefUse.Local);
            }
            visitSequence(node.body);
            return null;
        }

        @Override
        public Void visit(StmtTy.While node) {
            node.test.accept(this);
            visitSequence(node.body);
            visitSequence(node.orElse);
            return null;
        }

        @Override
        public Void visit(StmtTy.With node) {
            visitSequence(node.items);
            visitSequence(node.body);
            return null;
        }

        @Override
        public Void visit(StmtTy.With.Item node) {
            node.contextExpr.accept(this);
            if (node.optionalVars != null) {
                node.optionalVars.accept(this);
            }
            return null;
        }

        @Override
        public Void visit(ComprehensionTy node) {
            currentScope.flags.add(ScopeFlags.IsVisitingIterTarget);
            node.target.accept(this);
            currentScope.flags.remove(ScopeFlags.IsVisitingIterTarget);
            currentScope.comprehensionIterExpression++;
            node.iter.accept(this);
            currentScope.comprehensionIterExpression--;
            visitSequence(node.ifs);
            if (node.isAsync) {
                currentScope.flags.add(ScopeFlags.IsCoroutine);
            }
            return null;
        }

        @Override
        public Void visit(StmtTy.Break aThis) {
            return null;
        }

        @Override
        public Void visit(StmtTy.Continue aThis) {
            return null;
        }

        @Override
        public Void visit(StmtTy.Pass aThis) {
            return null;
        }
    }
}
