/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.test.parser;

import java.util.Collection;
import java.util.TreeSet;

import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.PClosureFunctionRootNode;
import com.oracle.graal.python.nodes.PClosureRootNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.ClassDefinitionPrologueNode;
import com.oracle.graal.python.nodes.control.ReturnTargetNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.AccessNameNode;
import com.oracle.graal.python.nodes.frame.FrameSlotIDs;
import com.oracle.graal.python.nodes.frame.FrameSlotNode;
import com.oracle.graal.python.nodes.frame.PythonFrame;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.WriteGlobalNode;
import com.oracle.graal.python.nodes.frame.WriteIdentifierNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode.KwDefaultExpressionNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.GeneratorExpressionNode;
import com.oracle.graal.python.nodes.function.GeneratorFunctionDefinitionNode;
import com.oracle.graal.python.nodes.generator.GeneratorReturnTargetNode;
import com.oracle.graal.python.nodes.generator.YieldNode;
import com.oracle.graal.python.nodes.literal.FormatStringLiteralNode;
import com.oracle.graal.python.nodes.literal.IntegerLiteralNode;
import com.oracle.graal.python.nodes.literal.LongLiteralNode;
import com.oracle.graal.python.nodes.literal.PIntLiteralNode;
import com.oracle.graal.python.nodes.literal.StringLiteralNode;
import com.oracle.graal.python.nodes.statement.ImportFromNode;
import com.oracle.graal.python.nodes.statement.ImportNode;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.parser.GeneratorInfo;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

public class ParserTreePrinter implements NodeVisitor {
    private final int MAX_TEXT_LENGTH = 20;
    private final StringBuilder sb;
    private final boolean printTmpSlots;
    private int level;
    protected boolean printFormatStringLiteralDetail = false;

    public ParserTreePrinter(boolean printTmpSlots) {
        this.sb = new StringBuilder();
        this.printTmpSlots = printTmpSlots;
        this.level = 0;
    }

    public String getTree() {
        return sb.toString();
    }

    public boolean visit(ModuleRootNode module) {
        nodeHeader(module, module.getName());
        level++;
        addSignature(module.getSignature());
        addInfoPClosureRootNode(module);
        indent(level);
        sb.append("Documentation: ");
        add(module.getDoc());
        newLine();
        level--;
        return true;
    }

    private void addInfoPClosureFunctionRootNode(PClosureFunctionRootNode node) {
        addSignature(node.getSignature());
        indent(level);
        sb.append("CelVars: ");
        add(node.getCellVars());
        newLine();
        addInfoPClosureRootNode(node);
    }

    private void addInfoPClosureRootNode(PClosureRootNode node) {
        indent(level);
        sb.append("FreeVars: ");
        add(node.getFreeVars());
        newLine();
        addInfoPRootNode(node);
    }

    private void addInfoPRootNode(PRootNode node) {
        indent(level);
        sb.append("NeedsCellFrame: ");
        add(node.needsCallerFrame());
        newLine();
        addInfoRootNode(node);
    }

    private void addInfoRootNode(RootNode node) {
        addIdentifiers(PythonFrame.getIdentifiersAsString(node.getFrameDescriptor()));
    }

    private void addFunctionDefinitionNode(FunctionDefinitionNode node) {
        indent(level);
        sb.append("Arguments:");
        if (node.getDefaults() == null || node.getDefaults().length == 0) {
            sb.append(" None");
            newLine();
        } else {
            newLine();
            level++;
            for (ExpressionNode arg : node.getDefaults()) {
                visit(arg);
            }
            level--;
        }
        indent(level);
        sb.append("KwArguments:");
        if (node.getKwDefaults() == null || node.getKwDefaults().length == 0) {
            sb.append(" None\n");
        } else {
            newLine();
            level++;
            for (ExpressionNode arg : node.getKwDefaults()) {
                visit(arg);
            }
            level--;
        }
        indent(level);
        sb.append("Documentation:");
        ExpressionNode doc = node.getDoc();
        if (doc == null) {
            sb.append(" None");
            newLine();
        } else if (doc instanceof StringLiteralNode) {
            sb.append(" StringLiteralNode: ");
            add(((StringLiteralNode) doc).getValue());
            newLine();
        } else {
            newLine();
            level++;
            visit(node.getDoc());
            level--;
        }
        indent(level);
        sb.append("FreeVarSlots: ");
        add(node.getFreeVarDefinitions());
        newLine();
        add(node.getExecutionCellSlots(), node.getCallTarget().getRootNode().getFrameDescriptor());
        visit(node.getFunctionRoot());
    }

    public boolean visit(GeneratorFunctionDefinitionNode node) {
        nodeHeader(node, node.getFunctionName());
        level++;
        addIdentifiers(PythonFrame.getIdentifiersAsString(node.getFrameDescriptor()));
        indent(level);
        GeneratorInfo generatorInfo = node.getGeneratorInfo();
        sb.append("Active Flags: ").append(generatorInfo.getNumOfActiveFlags());
        newLine();
        indent(level);
        sb.append("For Nodes: ").append(generatorInfo.getNumOfIteratorSlots());
        newLine();
        indent(level);
        sb.append("Block Nodes: ").append(generatorInfo.getNumOfBlockNodes());
        newLine();
        addFunctionDefinitionNode(node);
        level--;
        return false;
    }

    public boolean visit(FunctionDefinitionNode node) {
        nodeHeader(node, node.getFunctionName());
        level++;
        addFunctionDefinitionNode(node);
        level--;
        return false;
    }

    public boolean visit(FunctionRootNode node) {
        nodeHeader(node);
        level++;
        indent(level);
        sb.append("Name: ").append(node.getName());
        newLine();
        addInfoPClosureFunctionRootNode(node);
        add(node.getExecutionCellSlots(), node.getFrameDescriptor());
        level--;
        return true;
    }

    public boolean visit(ReadGlobalOrBuiltinNode node) {
        nodeHeader(node);
        level++;
        indent(level);
        sb.append("Identifier: ").append(node.getAttributeId());
        newLine();
        level--;
        return true;
    }

    public boolean visit(ExpressionNode.ExpressionWithSideEffect node) {
        nodeHeader(node);
        level += 2;
        indent(level - 1);
        sb.append("Expression:");
        newLine();
        visit(node.getExpression());
        indent(level - 1);
        sb.append("SideEffect:");
        newLine();
        visit(node.getSideEffect());
        level -= 2;
        return false;
    }

    public boolean visit(ReturnTargetNode node) {
        nodeHeader(node);
        level++;
        indent(level);
        sb.append("Body: ");
        visit(node.getBody());
        indent(level);
        sb.append("Return Expresssion: ");
        visit(node.getReturn());
        level--;
        return false;
    }

    public boolean visit(ImportNode node) {
        nodeHeader(node);
        level++;
        indent(level);
        sb.append("Module: ").append(node.getModuleName());
        newLine();
        level--;
        return true;
    }

    public boolean visit(ImportFromNode node) {
        nodeHeader(node);
        level++;
        indent(level);
        sb.append("Importee: ").append(node.getImportee());
        newLine();
        indent(level);
        sb.append("Level: ").append(node.getLevel());
        newLine();
        indent(level);
        sb.append("FromList: ");
        add(node.getFromlist());
        newLine();
        level--;
        return true;
    }

    public boolean visit(LookupAndCallBinaryNode node) {
        nodeHeader(node);
        level++;
        indent(level);
        sb.append("Op: ").append(node.getName());
        newLine();
        level--;
        return true;
    }

    public boolean visit(LookupAndCallUnaryNode node) {
        nodeHeader(node);
        level++;
        indent(level);
        sb.append("Op: ").append(node.getMethodName());
        newLine();
        level--;
        return true;
    }

    public boolean visit(KwDefaultExpressionNode node) {
        nodeHeader(node);
        level++;
        indent(level);
        sb.append("Name: ").append(node.name);
        newLine();
        indent(level);
        sb.append("Value: ");
        visit(node.exprNode);
        level--;
        return false;
    }

    public boolean visit(SetAttributeNode node) {
        nodeHeader(node);
        level++;
        indent(level);
        sb.append("Attribute: ").append(node.getAttributeId());
        newLine();
        level--;
        return true;
    }

    public boolean visit(GeneratorExpressionNode node) {
        nodeHeader(node);
        level++;
        indent(level);
        sb.append("Name: ").append(node.getName());
        newLine();
        addIdentifiers(PythonFrame.getIdentifiersAsString(node.getFrameDescriptor()));
        indent(level);
        sb.append("Enclosing");
        newLine();
        level++;
        addIdentifiers(node.getEnclosingFrameDescriptor());
        level--;
        indent(level);
        GeneratorInfo generatorInfo = node.getGeneratorInfo();
        sb.append("Active Flags: ").append(generatorInfo.getNumOfActiveFlags());
        newLine();
        indent(level);
        sb.append("For Nodes: ").append(generatorInfo.getNumOfIteratorSlots());
        newLine();
        indent(level);
        sb.append("Block Nodes: ").append(generatorInfo.getNumOfBlockNodes());
        newLine();
        indent(level);
        sb.append("Is Enclosing Frame Generator: ").append(node.isEnclosingFrameGenerator());
        newLine();
        visit(node.getCallTarget().getRootNode());
        level--;
        return true;
    }

    public boolean visit(YieldNode node) {
        nodeHeader(node);
        level++;
        indent(level);
        sb.append("flagSlot: ").append(node.getFlagSlot());
        newLine();
        level--;
        return true;
    }

    public boolean visit(GeneratorReturnTargetNode node) {
        nodeHeader(node);
        level++;
        indent(level);
        sb.append("flagSlot: ").append(node.getFlagSlot());
        newLine();
        level--;
        return true;
    }

    public boolean visit(GetFixedAttributeNode node) {
        nodeHeader(node);
        level++;
        indent(level);
        sb.append("Key: ").append(node.getKey());
        newLine();
        level--;
        return true;
    }

    public boolean visit(FormatStringLiteralNode node) {
        nodeHeader(node);
        level++;
        indent(level);
        sb.append("Parts: ").append(node.getParts().length);
        newLine();
        level--;
        return printFormatStringLiteralDetail;
    }

    private boolean visitChildren(Node node) {
        for (Node child : node.getChildren()) {
            visit(child);
        }
        return true;
    }

    private void nodeHeader(Node node) {
        addNodeClassName(node);
        sb.append(" ");
        addSourceSection(node.getSourceSection());
        newLine();
    }

    private void nodeHeader(Node node, String name) {
        addNodeClassName(node);
        sb.append(" Name: ").append(name).append(" ");
        addSourceSection(node.getSourceSection());
        newLine();
    }

    private void addNodeClassName(Node node) {
        sb.append(node.getClass().getSimpleName());
    }

    private void addSourceSection(SourceSection ss) {
        if (ss != null) {
            sb.append("SourceSection: [").append(ss.getCharIndex()).append(",").append(ss.getCharEndIndex()).append("]");
            add(ss.getCharacters().toString());
        } else {
            sb.append("SourceSection: None");
        }
    }

    private void addSignature(Signature signature) {
        indent(level);
        sb.append("Signature: ");
        sb.append("varArgs=");
        add(signature.takesVarArgs());
        sb.append(", varKeywordArgs=");
        add(signature.takesVarKeywordArgs());
        sb.append(", noArguments=");
        add(signature.takesNoArguments());
        sb.append(", positionalOnly=");
        add(signature.takesPositionalOnly());
        sb.append(", requiresKeywordArgs=");
        add(signature.takesRequiredKeywordArgs());
        if (signature.getVarargsIdx() > -1) {
            sb.append(", varArgsIdx=").append(signature.getVarargsIdx());
        }
        if (signature.getPositionalOnlyArgIndex() > -1) {
            sb.append(", positionalOnlyIdx=").append(signature.getPositionalOnlyArgIndex());
        }
        newLine();
        level++;
        if (signature.getParameterIds() != null && signature.getParameterIds().length > 0) {
            indent(level);
            sb.append("Param Names: ");
            add(signature.getParameterIds());
            newLine();
        }
        if (signature.takesKeywordArgs()) {
            indent(level);
            sb.append("Keyword Names: ");
            add(signature.getKeywordNames());
            newLine();
        }
        level--;
    }

    private void addIdentifiers(Collection<? extends Object> identifiers) {
        indent(level);
        sb.append("FrameDescriptor: ");
        if (identifiers.isEmpty()) {
            sb.append(" Empty");
            newLine();
            return;
        }
        sb.append(identifiers.size()).append(" slots [");
        TreeSet<String> names = new TreeSet<>();
        for (Object identifier : identifiers) {
            String name = identifier.toString();
            if (printTmpSlots || (!printTmpSlots && !name.startsWith("<>temp"))) {
                names.add(name);
            }
        }
        add(names);
        sb.append("]");
        newLine();
    }

    private void add(String text) {
        if (text != null && text.length() > 0) {
            String textToPrint = text.length() < MAX_TEXT_LENGTH ? text : text.subSequence(0, MAX_TEXT_LENGTH).toString() + "...";
            textToPrint = textToPrint.replaceAll("\\r\\n|\\r|\\n", "\u21b5");
            sb.append("`").append(textToPrint).append("`");
        } else if (text == null) {
            sb.append("None");
        } else {
            sb.append("Empty");
        }
    }

    private void add(String[] array) {
        if (array == null || array.length == 0) {
            sb.append("None");
        } else {
            boolean first = true;
            for (String text : array) {
                if (!first) {
                    sb.append(", ");
                } else {
                    first = false;
                }
                sb.append(text);
            }
        }
    }

    private void add(Collection<String> array) {
        if (array == null || array.isEmpty()) {
            sb.append("None");
        } else {
            boolean first = true;
            for (String text : array) {
                if (!first) {
                    sb.append(", ");
                } else {
                    first = false;
                }
                sb.append(text);
            }
        }
    }

    private void add(FrameSlotNode slot) {
        FrameDescriptor descriptor = ((Node) slot).getRootNode().getFrameDescriptor();
        String full = "[" + slot.getSlotIndex() + "," + slot.getSlotIdentifier(descriptor) + "," + slot.getSlotKind(descriptor) + "]";
        if (printTmpSlots) {
            sb.append(full);
        } else {
            Object identifier = slot.getSlotIdentifier(descriptor);
            if (identifier instanceof String) {
                sb.append(full);
            } else if (identifier == FrameSlotIDs.RETURN_SLOT_ID) {
                sb.append("<return_val>");
            } else if (identifier == FrameSlotIDs.FREEVAR__CLASS__) {
                sb.append("<>freevar__class__");
            } else if (identifier.toString().startsWith("<>temp")) {
                sb.append("<>temp");
            } else {
                sb.append(full);
            }
        }
    }

    private void add(ExecutionCellSlots executionCellSlots, FrameDescriptor descriptor) {
        indent(level);
        sb.append("ExecutionSlots:");
        newLine();
        level++;
        indent(level);
        sb.append("FreeVarsSlots: ");
        add(PythonFrame.extractSlotNames(descriptor, executionCellSlots.getFreeVarSlots()));
        newLine();
        indent(level);
        sb.append("CellVarsSlots: ");
        add(PythonFrame.extractSlotNames(descriptor, executionCellSlots.getCellVarSlots()));
        newLine();
        level--;
    }

    private void add(boolean value) {
        if (value) {
            sb.append("True");
        } else {
            sb.append("False");
        }
    }

    private void newLine() {
        sb.append("\n");
    }

    @Override
    public boolean visit(Node node) {
        if (!node.getClass().getPackage().getName().startsWith("com.oracle.graal.python")) {
            // print only python nodes
            visitChildren(node);
            return false;
        }
        if (node instanceof ExecutionContext.CalleeContext) {
            // these nodes shouldn't be printed in the result.
            return false;
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            indent(level);
        }
        boolean visitChildren = true;
        if (node instanceof ModuleRootNode) {
            visitChildren = visit((ModuleRootNode) node);
        } else if (node instanceof GeneratorFunctionDefinitionNode) {
            visitChildren = visit((GeneratorFunctionDefinitionNode) node);
        } else if (node instanceof FunctionDefinitionNode) {
            visitChildren = visit((FunctionDefinitionNode) node);
        } else if (node instanceof FunctionRootNode) {
            visitChildren = visit((FunctionRootNode) node);
        } else if (node instanceof ReturnTargetNode) {
            visitChildren = visit((ReturnTargetNode) node);
        } else if (node instanceof ExpressionNode.ExpressionWithSideEffect) {
            visitChildren = visit((ExpressionNode.ExpressionWithSideEffect) node);
        } else if (node instanceof ReadGlobalOrBuiltinNode) {
            visitChildren = visit((ReadGlobalOrBuiltinNode) node);
        } else if (node instanceof ImportNode) {
            visitChildren = visit((ImportNode) node);
        } else if (node instanceof ImportFromNode) {
            visitChildren = visit((ImportFromNode) node);
        } else if (node instanceof LookupAndCallBinaryNode) {
            visitChildren = visit((LookupAndCallBinaryNode) node);
        } else if (node instanceof LookupAndCallUnaryNode) {
            visitChildren = visit((LookupAndCallUnaryNode) node);
        } else if (node instanceof KwDefaultExpressionNode) {
            visitChildren = visit((KwDefaultExpressionNode) node);
        } else if (node instanceof SetAttributeNode) {
            visitChildren = visit((SetAttributeNode) node);
        } else if (node instanceof GeneratorExpressionNode) {
            visitChildren = visit((GeneratorExpressionNode) node);
        } else if (node instanceof YieldNode) {
            visitChildren = visit((YieldNode) node);
        } else if (node instanceof GeneratorReturnTargetNode) {
            visitChildren = visit((GeneratorReturnTargetNode) node);
        } else if (node instanceof GetFixedAttributeNode) {
            visitChildren = visit((GetFixedAttributeNode) node);
        } else if (node instanceof FormatStringLiteralNode) {
            visitChildren = visit((FormatStringLiteralNode) node);
        } else {
            nodeHeader(node);
            level++;
            if (node instanceof PIntLiteralNode) {
                indent(level);
                sb.append("Value: ").append(((PIntLiteralNode) node).getValue().toString());
                newLine();
            } else if (node instanceof IntegerLiteralNode) {
                indent(level);
                sb.append("Value: ").append(((IntegerLiteralNode) node).getValue());
                newLine();
            } else if (node instanceof LongLiteralNode) {
                indent(level);
                sb.append("Value: ").append(((LongLiteralNode) node).getValue());
                newLine();
            } else if (node instanceof ClassDefinitionPrologueNode) {
                indent(level);
                sb.append("QualName: ").append(((ClassDefinitionPrologueNode) node).getQualName());
                newLine();
            } else {
                if (node instanceof WriteIdentifierNode) {
                    indent(level);
                    sb.append("Identifier: ");
                    if (printTmpSlots) {
                        sb.append(((WriteIdentifierNode) node).getIdentifier());
                    } else {
                        String identifier = ((WriteIdentifierNode) node).getIdentifier().toString();
                        if (identifier.startsWith("<>temp")) {
                            sb.append("<>temp");
                        } else {
                            sb.append(identifier);
                        }
                    }
                    newLine();
                }
                if (node instanceof AccessNameNode) {
                    indent(level);
                    sb.append("Identifier: ").append(((AccessNameNode) node).getAttributeId());
                    newLine();
                }
                if (node instanceof FrameSlotNode) {
                    indent(level);
                    sb.append("Frame: ");
                    add((FrameSlotNode) node);
                    newLine();
                }
                if (node instanceof WriteGlobalNode) {
                    indent(level);
                    sb.append("Identifier: ").append(((WriteGlobalNode) node).getAttributeId());
                    newLine();
                }
                if (node instanceof ReadIndexedArgumentNode) {
                    indent(level);
                    sb.append("Index: ").append(((ReadIndexedArgumentNode) node).getIndex());
                    newLine();
                }
            }
            level--;
        }
        if (visitChildren) {
            level++;
            visitChildren(node);
            level--;
        }
        return false;
    }

    private void indent(int indentLevel) {
        for (int i = 0; i < indentLevel; i++) {
            sb.append("  ");
        }
    }

}
