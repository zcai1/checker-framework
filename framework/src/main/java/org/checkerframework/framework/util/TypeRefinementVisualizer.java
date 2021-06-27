package org.checkerframework.framework.util;

import org.checkerframework.dataflow.analysis.AnalysisResult;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.block.RegularBlock;
import org.checkerframework.dataflow.cfg.node.AssignmentNode;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.framework.flow.CFAbstractStore;
import org.checkerframework.framework.flow.CFAbstractValue;
import org.checkerframework.framework.source.DiagMessage;
import org.checkerframework.framework.source.SourceChecker;

import javax.tools.Diagnostic;

public class TypeRefinementVisualizer {

    public void visualize(
            SourceChecker checker,
            ControlFlowGraph cfg,
            AnalysisResult<? extends CFAbstractValue<?>, ? extends CFAbstractStore<?, ?>>
                    analysisResult) {
        for (Block block : cfg.getDepthFirstOrderedBlocks()) {
            if (!(block instanceof RegularBlock)) continue;

            RegularBlock regularBlock = (RegularBlock) block;
            for (Node node : regularBlock.getContents()) {
                LocalVariableNode localVariableNode = null;

                // only support local variables for now
                if (node instanceof LocalVariableNode) {
                    localVariableNode = (LocalVariableNode) node;
                } else if (node instanceof AssignmentNode) {
                    Node target = ((AssignmentNode) node).getTarget();
                    if (target instanceof LocalVariableNode) {
                        localVariableNode = (LocalVariableNode) target;
                    }
                }

                if (localVariableNode == null || !localVariableNode.getInSource()) continue;

                CFAbstractValue<?> value = analysisResult.getValue(node);
                if (value == null) continue;

                checker.report(
                        localVariableNode.getTree(),
                        new DiagMessage(
                                Diagnostic.Kind.OTHER,
                                "type.refinements",
                                localVariableNode.getName(),
                                value.getAnnotations()));
            }
        }
    }
}
