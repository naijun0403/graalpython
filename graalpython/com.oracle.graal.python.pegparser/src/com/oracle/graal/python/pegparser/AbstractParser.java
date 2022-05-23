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
package com.oracle.graal.python.pegparser;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.tokenizer.Token;

/**
 * From this class is extended the generated parser. It allow access to the tokenizer. The methods
 * defined in this class are mostly equivalents to those defined in CPython's {@code pegen.c}. This
 * allows us to keep the actions and parser generator very similar to CPython for easier updating in
 * the future.
 */
abstract class AbstractParser {
    protected static final ExprTy[] EMPTY_EXPR = new ExprTy[0];
    protected static final KeywordTy[] EMPTY_KWDS = new KeywordTy[0];

    /**
     * Corresponds to TARGET_TYPES in CPython
     */
    public enum TargetsType {
        STAR_TARGETS,
        DEL_TARGETS,
        FOR_TARGETS
    }

    /**
     * Corresponds to PyPARSE_BARRY_AS_BDFL, check whether <> should be used instead != .
     */
    protected static final int PARSE_BARRY_AS_BDFL = 0x0020;

    private static final String BARRY_AS_BDFL = "with Barry as BDFL, use '<>' instead of '!='";

    private final ParserTokenizer tokenizer;
    private final ParserErrorCallback errorCb;
    private final FExprParser fexprParser;
    protected final NodeFactory factory;

    private final int flags;

    protected int level = 0;
    protected boolean callInvalidRules = false;

    /**
     * Indicates, whether there was found an error
     */
    protected boolean errorIndicator = false;
    /** Currently used just for indicating, if something was read **/
    protected int fill;

    private ExprTy.Name cachedDummyName;

    protected final RuleResultCache<Object> cache = new RuleResultCache<>(this);
    protected final Map<Integer, String> comments = new LinkedHashMap<>();

    private final Object[][][] reservedKeywords;
    private final String[] softKeywords;

    protected abstract Object[][][] getReservedKeywords();

    protected abstract String[] getSoftKeywords();

    protected abstract SSTNode runParser(InputType inputType);

    public AbstractParser(ParserTokenizer tokenizer, NodeFactory factory, FExprParser fexprParser) {
        this(tokenizer, factory, fexprParser, new DefaultParserErrorCallback(), 0);
    }

    public AbstractParser(ParserTokenizer tokenizer, NodeFactory factory, FExprParser fexprParser, int flags) {
        this(tokenizer, factory, fexprParser, new DefaultParserErrorCallback(), flags);
    }

    public AbstractParser(ParserTokenizer tokenizer, NodeFactory factory, FExprParser fexprParser, ParserErrorCallback errorCb) {
        this(tokenizer, factory, fexprParser, errorCb, 0);
    }

    public AbstractParser(ParserTokenizer tokenizer, NodeFactory factory, FExprParser fexprParser, ParserErrorCallback errorCb, int flags) {
        this.tokenizer = tokenizer;
        this.factory = factory;
        this.fexprParser = fexprParser;
        this.errorCb = errorCb;
        this.reservedKeywords = getReservedKeywords();
        this.softKeywords = getSoftKeywords();
        this.flags = flags;
        this.fill = 0;
    }

    public ParserErrorCallback getErrorCallback() {
        return errorCb;
    }

    public SSTNode parse(InputType inputType) {
        SSTNode res = runParser(inputType);
        if (res == null) {
            resetParserState();
            runParser(inputType);
            if (errorIndicator) {
                // shouldn't we return at least wrong AST based on a option?
                return null;
            }
            if (this.fill == 0) {
                raiseSyntaxError("error at start before reading any input");
            } else if (tokenizer.peekToken().type == Token.Kind.ENDMARKER) {
                // TODO we should handle this in better way. See cpython
                raiseSyntaxError("unexpected EOF while parsing");
            } else {
                // TODO check indentation errors
                raiseSyntaxError("invalid syntax");
            }
        }
        return res;
    }

    private void resetParserState() {
        errorIndicator = false;
        callInvalidRules = true;
        level = 0;
        cache.clear();
        tokenizer.reset(0);
    }

    /**
     * Get position in the tokenizer.
     * 
     * @return the position in tokenizer.
     */
    public int mark() {
        return tokenizer.mark();
    }

    /**
     * Reset position in the tokenizer
     * 
     * @param position where the tokenizer should set the current position
     */
    public void reset(int position) {
        tokenizer.reset(position);
    }

    /**
     * Is the expected token on the current position in tokenizer? If there is the expected token,
     * then the current position in tokenizer is changed to the next token.
     * 
     * @param tokenKind - the token kind that is expected on the current position
     * @return The expected token or null if the token on the current position is not the expected
     *         one.
     */
    public Token expect(int tokenKind) {
        Token token = getAndInitializeToken();
        if (token.type == tokenKind) {
            return tokenizer.getToken();
        }
        return null;
    }

    /**
     * Is the expected token on the current position in tokenizer? If there is the expected token,
     * then the current position in tokenizer is changed to the next token.
     * 
     * @param text - the token on the current position has to have this text
     * @return The expected token or null if the token on the current position is not the expected
     *         one.
     */
    public Token expect(String text) {
        Token token = tokenizer.peekToken();
        if (text.equals(tokenizer.getText(token))) {
            return tokenizer.getToken();
        }
        return null;
    }

    /**
     * Check if the next token that'll be read is if the expected kind. This has does not advance
     * the tokenizer, in contrast to {@link #expect(int)}.
     */
    protected boolean lookahead(boolean match, int kind) {
        int pos = mark();
        Token token = expect(kind);
        reset(pos);
        return (token != null) == match;
    }

    /**
     * Check if the next token that'll be read is if the expected kind. This has does not advance
     * the tokenizer, in contrast to {@link #expect(String)}.
     */
    protected boolean lookahead(boolean match, String text) {
        int pos = mark();
        Token token = expect(text);
        reset(pos);
        return (token != null) == match;
    }

    /**
     * Shortcut to Tokenizer.getText(Token)
     */
    public String getText(Token token) {
        if (token == null) {
            return null;
        }
        return tokenizer.getText(token);
    }

    /**
     * equivalent to _PyPegen_fill_token in that it modifies the token, and does not advance
     */
    public Token getAndInitializeToken() {
        int pos = mark();
        Token token = tokenizer.getToken();
        while (token.type == Token.Kind.TYPE_IGNORE) {
            String tag = getText(token);
            comments.put(token.startLine, tag);
            pos++;
            token = tokenizer.getToken();
        }
        reset(pos);

        // TODO: handle reaching end in single_input mode
        return initializeToken(token);
    }

    /**
     * _PyPegen_get_last_nonnwhitespace_token
     */
    public Token getLastNonWhitespaceToken() {
        Token t = null;
        for (int i = mark() - 1; i >= 0; i--) {
            t = tokenizer.peekToken(i);
            if (t.type != Token.Kind.ENDMARKER && (t.type < Token.Kind.NEWLINE || t.type > Token.Kind.DEDENT)) {
                break;
            }
        }
        return t;
    }

    /**
     * _PyPegen_name_token
     */
    public ExprTy.Name name_token() {
        Token t = expect(Token.Kind.NAME);
        if (t != null) {
            return factory.createVariable(getText(t), t.startOffset, t.endOffset);
        } else {
            return null;
        }
    }

    /**
     * 
     * @return flags that influence parsing.
     */
    public int getFlags() {
        return flags;
    }

    /**
     * _PyPegen_seq_count_dots
     */
    public int countDots(Token[] tokens) {
        int cnt = 0;
        for (Token t : tokens) {
            if (t.type == Token.Kind.ELLIPSIS) {
                cnt += 3;
            } else {
                assert t.type == Token.Kind.DOT;
                cnt += 1;
            }
        }
        return cnt;
    }

    /**
     * _PyPegen_expect_soft_keyword
     */
    protected ExprTy.Name expect_SOFT_KEYWORD(String keyword) {
        Token t = tokenizer.peekToken();
        if (t.type == Token.Kind.NAME && getText(t).equals(keyword)) {
            tokenizer.getToken();
            return factory.createVariable(getText(t), t.startOffset, t.endOffset);
        }
        return null;
    }

    /**
     * IMPORTANT! _PyPegen_string_token returns (through void*) a Token*. We are trying to be type
     * safe, so we create a container.
     */
    public Token string_token() {
        int pos = mark();
        Token t = expect(Token.Kind.STRING);
        if (t == null) {
            return null;
        }
        assert tokenizer.peekToken(pos) == t : ("token at " + pos + " is not equal to " + t);
        return t;
    }

    /**
     * _PyPegen_number_token
     */
    public ExprTy number_token() {
        Token t = expect(Token.Kind.NUMBER);
        if (t != null) {
            return factory.createNumber(getText(t), t.startOffset, t.endOffset);
        } else {
            return null;
        }
    }

    /**
     * _PyPegen_expect_forced_token
     */
    public Token expect_forced_token(int kind, String expected) {
        Token t = getAndInitializeToken();
        if (t.type != kind) {
            raiseSyntaxErrorKnownLocation(t, "expected '%s'", expected);
            return null;
        }
        tokenizer.getToken(); // advance
        return t;
    }

    public ExprTy.Name name_from_token(Token t) {
        if (t == null) {
            return null;
        }
        String id = getText(t);
        return factory.createVariable(id, t.startOffset, t.endOffset);
    }

    /**
     * _PyPegen_soft_keyword_token
     */
    public ExprTy.Name soft_keyword_token() {
        Token t = expect(Token.Kind.NAME);
        if (t == null) {
            return null;
        }
        String txt = getText(t);
        for (String s : softKeywords) {
            if (s.equals(txt)) {
                return name_from_token(t);
            }
        }
        return null;
    }

    /**
     * _PyPegen_dummy_name
     */
    public ExprTy.Name dummyName(@SuppressWarnings("unused") Object... args) {
        if (cachedDummyName != null) {
            return cachedDummyName;
        }
        cachedDummyName = factory.createVariable("", 0, 0);
        return cachedDummyName;
    }

    /**
     * _PyPegen_join_names_with_dot
     */
    public SSTNode joinNamesWithDot(ExprTy a, ExprTy b) {
        String id = ((ExprTy.Name) a).id + "." + ((ExprTy.Name) b).id;
        return factory.createVariable(id, a.getStartOffset(), b.getEndOffset());
    }

    /**
     * _PyPegen_seq_insert_in_front
     */
    @SuppressWarnings("unchecked")
    public <T> T[] insertInFront(T element, T[] seq, Class<T> clazz) {
        T[] result;
        if (seq == null) {
            result = (T[]) Array.newInstance(clazz, 1);
        } else {
            result = Arrays.copyOf(seq, seq.length + 1);
            System.arraycopy(seq, 0, result, 1, seq.length);
        }
        result[0] = element;
        return result;
    }

    public ExprTy[] insertInFront(ExprTy element, ExprTy[] seq) {
        return insertInFront(element, seq, ExprTy.class);
    }

    /**
     * _PyPegen_seq_append_to_end
     */
    @SuppressWarnings("unchecked")
    public <T> T[] appendToEnd(T[] seq, T element, Class<T> clazz) {
        T[] result;
        if (seq == null) {
            result = (T[]) Array.newInstance(clazz, 1);
            result[0] = element;
        } else {
            result = Arrays.copyOf(seq, seq.length + 1);
            System.arraycopy(seq, 0, result, 1, seq.length);
            result[seq.length] = element;
        }
        return result;
    }

    public ExprTy[] appendToEnd(ExprTy[] seq, ExprTy element) {
        return appendToEnd(seq, element, ExprTy.class);
    }

    /**
     * _PyPegen_concatenate_strings
     */
    public SSTNode concatenateStrings(Token[] tokens) {
        int n = tokens.length;
        String[] values = new String[n];
        Token t = tokens[0];
        int startOffset = t.startOffset;
        values[0] = getText(t);
        for (int i = 1; i < n; i++) {
            t = tokens[i];
            values[i] = getText(t);
        }
        int endOffset = t.endOffset;
        return factory.createString(values, startOffset, endOffset, fexprParser, errorCb);
    }

    /**
     * _PyPegen_check_barry_as_flufl
     */
    public boolean checkBarryAsFlufl(Token token) {
        if ((flags & PARSE_BARRY_AS_BDFL) != 0 && !getText(token).equals("<>")) {
            errorCb.onError(token.startOffset, token.endOffset, BARRY_AS_BDFL);
            return true;
        }
        return false;
    }

    /**
     * _PyPegen_get_expr_name
     */
    public String getExprName(ExprTy e) {
        if (e instanceof ExprTy.Attribute || e instanceof ExprTy.Subscript || e instanceof ExprTy.Starred || e instanceof ExprTy.Name || e instanceof ExprTy.Tuple || e instanceof ExprTy.List ||
                        e instanceof ExprTy.Lambda) {
            return e.getClass().getSimpleName().toLowerCase();
        }
        if (e instanceof ExprTy.Call) {
            return "function call";
        }
        if (e instanceof ExprTy.BoolOp || e instanceof ExprTy.BinOp || e instanceof ExprTy.UnaryOp) {
            return "expression";
        }
        if (e instanceof ExprTy.GeneratorExp) {
            return "generator expression";
        }
        if (e instanceof ExprTy.Yield || e instanceof ExprTy.YieldFrom) {
            return "yield expression";
        }
        if (e instanceof ExprTy.Await) {
            return "await expression";
        }
        if (e instanceof ExprTy.ListComp) {
            return "list comprehension";
        }
        if (e instanceof ExprTy.SetComp) {
            return "set comprehension";
        }
        if (e instanceof ExprTy.DictComp) {
            return "dict comprehension";
        }
        if (e instanceof ExprTy.Dict) {
            return "dict literal";
        }
        if (e instanceof ExprTy.Set) {
            return "set display";
        }
        if (e instanceof ExprTy.JoinedStr || e instanceof ExprTy.FormattedValue) {
            return "f-string expression";
        }
        if (e instanceof ExprTy.Constant) {
            ExprTy.Constant constant = (ExprTy.Constant) e;
            switch (constant.kind) {
                case NONE:
                    return "None";
                case BOOLEAN:
                    boolean value = (Boolean) constant.value;
                    return value ? "True" : "False";
                case ELLIPSIS:
                    return "Ellipsis";
            }
            return "literal";
        }
        if (e instanceof ExprTy.Compare) {
            return "comparision";
        }
        if (e instanceof ExprTy.IfExp) {
            return "conditional expression";
        }
        if (e instanceof ExprTy.NamedExpr) {
            return "named expression";
        }
        // TODO Rise system error
// PyErr_Format(PyExc_SystemError,
// "unexpected expression in assignment %d (line %d)",
// e->kind, e->lineno);
        return null;
    }

    /**
     * equivalent to initialize_token
     */
    private Token initializeToken(Token token) {
        if (token.type == Token.Kind.NAME) {
            String txt = getText(token);
            int l = txt.length();
            Object[][] kwlist;
            if (l < reservedKeywords.length && (kwlist = reservedKeywords[l]) != null) {
                for (Object[] kwAssoc : kwlist) {
                    if (txt.equals(kwAssoc[0])) {
                        token.type = (int) kwAssoc[1];
                        break;
                    }
                }
            }
        }
        this.fill++;
        return token;
    }

    /**
     * _PyPegen_new_type_comment
     */
    protected String newTypeComment(Object token) {
        return getText((Token) token);
    }

    /**
     * _PyPegen_join_sequences
     *
     */
    protected <T> T[] join(T[] a, T[] b) {
        if (a == null && b != null) {
            return b;
        }
        if (a != null && b == null) {
            return a;
        }

        if (a != null) {
            T[] result = Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, result, a.length, b.length);
            return result;
        }
        return null;
    }

    /**
     * _PyPegen_set_expr_context
     *
     * TODO: (tfel) We should try to avoid having to walk the parse tree so often. The git history
     * includes an attempt with a symbol and a scope stream synchronized to the token stream, but it
     * doesn't really work with the pegen generator.
     */
    protected ExprTy setExprContext(ExprTy node, ExprContext context) {
        return node.copyWithContext(context);
    }

    // debug methods
    private void indent(StringBuffer sb) {
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
    }

    void debugMessageln(String text, Object... args) {
        StringBuffer sb = new StringBuffer();
        indent(sb);
        sb.append(String.format(text, args));
        System.out.println(sb);
    }

    // Helper classes that are not really meaningful parts of the AST, just containers to move the
    // data where we need it.

    public static final class CmpopExprPair {
        final ExprTy.Compare.Operator op;
        final ExprTy expr;

        CmpopExprPair(ExprTy.Compare.Operator op, ExprTy expr) {
            this.op = op;
            this.expr = expr;
        }
    }

    public static final class KeyValuePair {
        final ExprTy key;
        final ExprTy value;

        KeyValuePair(ExprTy key, ExprTy value) {
            this.key = key;
            this.value = value;
        }

    }

    static ExprTy[] extractKeys(KeyValuePair[] l) {
        int len = l == null ? 0 : l.length;
        ExprTy[] keys = new ExprTy[len];
        for (int i = 0; i < len; i++) {
            keys[i] = l[i].key;
        }
        return keys;
    }

    static ExprTy[] extractValues(KeyValuePair[] l) {
        int len = l == null ? 0 : l.length;
        ExprTy[] values = new ExprTy[len];
        for (int i = 0; i < len; i++) {
            values[i] = l[i].value;
        }
        return values;
    }

    public static final class KeyPatternPair {
        final ExprTy key;
        final StmtTy.Match.Pattern pattern;

        KeyPatternPair(ExprTy key, StmtTy.Match.Pattern pattern) {
            this.key = key;
            this.pattern = pattern;
        }
    }

    public static final class NameDefaultPair {
        final ArgTy name;
        final ExprTy def;

        NameDefaultPair(ArgTy name, ExprTy def) {
            this.name = name;
            this.def = def;
        }
    }

    public static final class SlashWithDefault {
        final ArgTy[] plainNames;
        final NameDefaultPair[] namesWithDefaults;

        SlashWithDefault(ArgTy[] plainNames, NameDefaultPair[] namesWithDefaults) {
            this.plainNames = plainNames;
            this.namesWithDefaults = namesWithDefaults;
        }
    }

    public static final class StarEtc {
        final ArgTy varArg;
        final NameDefaultPair[] kwOnlyArgs;
        final ArgTy kwArg;

        StarEtc(ArgTy varArg, NameDefaultPair[] kwOnlyArgs, ArgTy kwArg) {
            this.varArg = varArg;
            this.kwOnlyArgs = kwOnlyArgs;
            this.kwArg = kwArg;
        }
    }

    public static final class KeywordOrStarred {
        final SSTNode element;
        final boolean isKeyword;

        KeywordOrStarred(SSTNode element, boolean isKeyword) {
            this.element = element;
            this.isKeyword = isKeyword;
        }
    }

    /**
     * _PyPegen_seq_extract_starred_exprs
     */
    static ExprTy[] extractStarredExpressions(KeywordOrStarred[] kwds) {
        List<ExprTy> list = new ArrayList<>();
        for (KeywordOrStarred n : kwds) {
            if (!n.isKeyword) {
                ExprTy element = (ExprTy) n.element;
                list.add(element);
            }
        }
        return list.toArray(new ExprTy[0]);
    }

    /**
     * _PyPegen_seq_delete_starred_exprs
     */
    static KeywordTy[] deleteStarredExpressions(KeywordOrStarred[] kwds) {
        List<KeywordTy> list = new ArrayList<>();
        for (KeywordOrStarred n : kwds) {
            if (n.isKeyword) {
                KeywordTy element = (KeywordTy) n.element;
                list.add(element);
            }
        }
        return list.toArray(new KeywordTy[0]);
    }

    /**
     * _PyPegen_map_names_to_ids
     */
    static String[] extractNames(ExprTy[] seq) {
        List<String> list = new ArrayList<>();
        for (ExprTy e : seq) {
            String id = ((ExprTy.Name) e).id;
            list.add(id);
        }
        return list.toArray(new String[0]);
    }

    /**
     * _PyPegen_collect_call_seqs
     */
    final ExprTy collectCallSequences(ExprTy[] a, KeywordOrStarred[] b, int startOffset, int endOffset) {
        if (b == null) {
            return factory.createCall(dummyName(), a, EMPTY_KWDS, startOffset, endOffset);
        } else {
            ExprTy[] starred = extractStarredExpressions(b);
            ExprTy[] args;
            if (starred.length > 0) {
                args = Arrays.copyOf(a, a.length + starred.length);
                System.arraycopy(starred, 0, args, a.length, starred.length);
            } else {
                args = a;
            }
            return factory.createCall(dummyName(), args, deleteStarredExpressions(b), startOffset, endOffset);
        }
    }

    private ExprTy visitContainer(ExprTy[] elements, TargetsType type) {
        if (elements == null) {
            return null;
        }
        ExprTy child;
        for (ExprTy expr : elements) {
            child = getInvalidTarget(expr, type);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private ExprTy getInvalidTarget(ExprTy expr, TargetsType type) {
        if (expr == null) {
            return null;
        }
        if (expr instanceof ExprTy.List) {
            return visitContainer(((ExprTy.List) expr).elements, type);
        }
        if (expr instanceof ExprTy.Tuple) {
            return visitContainer(((ExprTy.Tuple) expr).elements, type);
        }
        if (expr instanceof ExprTy.Starred) {
            if (type == TargetsType.DEL_TARGETS) {
                return expr;
            }
            return getInvalidTarget(((ExprTy.Starred) expr).value, type);
        }
        if (expr instanceof ExprTy.Compare) {
            if (type == TargetsType.FOR_TARGETS) {
                ExprTy.Compare compare = (ExprTy.Compare) expr;
                if (compare.ops[0] == ExprTy.Compare.Operator.IN) {
                    return getInvalidTarget(compare.left, type);
                }
                return null;
            }
            return expr;
        }
        if (expr instanceof ExprTy.Name || expr instanceof ExprTy.Subscript || expr instanceof ExprTy.Attribute) {
            return null;
        }
        return expr;
    }

    /**
     * RAISE_SYNTAX_ERROR_INVALID_TARGET
     */
    SSTNode raiseSyntaxErrorInvalidTarget(TargetsType type, ExprTy expr) {
        ExprTy invalidTarget = getInvalidTarget(expr, type);
        if (invalidTarget != null) {
            String message = (type == TargetsType.STAR_TARGETS || type == TargetsType.FOR_TARGETS)
                            ? "cannot assign to %s"
                            : "cannot delete %s";
            raiseSyntaxErrorKnownLocation(invalidTarget, message, getExprName(invalidTarget));
        }
        return raiseSyntaxError("invalid syntax");
    }

    /**
     * RAISE_SYNTAX_ERROR
     */
    SSTNode raiseSyntaxError(String msg, Object... arguments) {
        errorIndicator = true;
        Token errorToken = tokenizer.peekToken();
        errorCb.onError(ParserErrorCallback.ErrorType.Syntax, errorToken.startOffset, errorToken.endOffset, msg, arguments);
        return null;
    }

    /**
     * RAISE_ERROR_KNOWN_LOCATION the first param is a token, where error begins
     */
    SSTNode raiseSyntaxErrorKnownLocation(Token errorToken, String msg, Object... argument) {
        errorIndicator = true;
        errorCb.onError(ParserErrorCallback.ErrorType.Syntax, errorToken.startOffset, errorToken.endOffset, msg, argument);
        return null;
    }

    /**
     * RAISE_ERROR_KNOWN_LOCATION the first param is node, where error begins
     */
    SSTNode raiseSyntaxErrorKnownLocation(SSTNode where, String msg, Object... argument) {
        errorIndicator = true;
        errorCb.onError(ParserErrorCallback.ErrorType.Syntax, where.getStartOffset(), where.getEndOffset(), msg, argument);
        return null;
    }

    /**
     * RAISE_ERROR_KNOWN_LOCATION
     */
    SSTNode raiseErrorKnownLocation(ParserErrorCallback.ErrorType typeError, SSTNode where, String msg, Object... argument) {
        errorIndicator = true;
        errorCb.onError(typeError, where.getStartOffset(), where.getEndOffset(), msg, argument);
        return null;
    }

    /**
     * CHECK Simple check whether the node is not null.
     */
    <T> T check(T node) {
        if (node == null) {
            errorIndicator = true;
        }
        return node;
    }

    @SuppressWarnings("unused")
    // TODO implement the check
    static <T> T checkVersion(int version, String msg, T node) {
        return node;
    }
}
