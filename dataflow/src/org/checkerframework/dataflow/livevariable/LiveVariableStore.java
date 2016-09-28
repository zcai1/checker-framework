package org.checkerframework.dataflow.livevariable;

import java.util.HashSet;
import java.util.Set;
import org.checkerframework.dataflow.analysis.FlowExpressions.Receiver;
import org.checkerframework.dataflow.analysis.Store;
import org.checkerframework.dataflow.cfg.CFGVisualizer;
import org.checkerframework.dataflow.cfg.node.BinaryOperationNode;
import org.checkerframework.dataflow.cfg.node.FieldAccessNode;
import org.checkerframework.dataflow.cfg.node.InstanceOfNode;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.TernaryExpressionNode;
import org.checkerframework.dataflow.cfg.node.TypeCastNode;
import org.checkerframework.dataflow.cfg.node.UnaryOperationNode;

public class LiveVariableStore implements Store<LiveVariableStore> {

    private Set<LiveVar> liveVarSet;

    public LiveVariableStore() {
        liveVarSet = new HashSet<>();
    }

    public LiveVariableStore(Set<LiveVar> liveVarSet) {
        this.liveVarSet = liveVarSet;
    }

    /**
     * put {@code variable} into {@code liveVarSet}
     * @param variable
     */
    public void putLiveVar(LiveVar variable) {
        liveVarSet.add(variable);
    }

    /**
     * remove {@code variable} from {@code liveVarSet}
     * @param variable
     */
    public void killLiveVar(LiveVar variable) {
        liveVarSet.remove(variable);
    }

    public void addUseInExpression(Node expression) {
        if (expression instanceof BinaryOperationNode) {
            BinaryOperationNode binaryNode = (BinaryOperationNode) expression;
            addUseInExpression(binaryNode.getLeftOperand());
            addUseInExpression(binaryNode.getRightOperand());
        } else if (expression instanceof UnaryOperationNode) {
            UnaryOperationNode unaryNode = (UnaryOperationNode) expression;
            addUseInExpression(unaryNode.getOperand());
        } else if (expression instanceof TernaryExpressionNode) {
            TernaryExpressionNode ternaryNode = (TernaryExpressionNode) expression;
            addUseInExpression(ternaryNode.getConditionOperand());
            addUseInExpression(ternaryNode.getThenOperand());
            addUseInExpression(ternaryNode.getElseOperand());
        } else if (expression instanceof TypeCastNode) {
            TypeCastNode typeCastNode = (TypeCastNode) expression;
            addUseInExpression(typeCastNode.getOperand());
        } else if (expression instanceof InstanceOfNode) {
            InstanceOfNode instanceOfNode = (InstanceOfNode) expression;
            addUseInExpression(instanceOfNode.getOperand());
        } else if (expression instanceof LocalVariableNode
                || expression instanceof FieldAccessNode) {
            LiveVar liveVar = new LiveVar(expression);
            putLiveVar(liveVar);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof LiveVariableStore)) {
            return false;
        }

        LiveVariableStore other = (LiveVariableStore) obj;
        return other.liveVarSet.equals(this.liveVarSet);
    }

    @Override
    public int hashCode() {
        return this.liveVarSet.hashCode();
    }

    @Override
    public LiveVariableStore copy() {
        Set<LiveVar> liveVarSetCopy = new HashSet<>();
        liveVarSetCopy.addAll(liveVarSet);
        return new LiveVariableStore(liveVarSetCopy);
    }

    @Override
    public LiveVariableStore leastUpperBound(LiveVariableStore other) {
        Set<LiveVar> liveVarSetLub = new HashSet<>();
        liveVarSetLub.addAll(this.liveVarSet);
        liveVarSetLub.addAll(other.liveVarSet);
        return new LiveVariableStore(liveVarSetLub);
    }

    @Override
    public boolean canAlias(Receiver a, Receiver b) {
        return true;
    }

    @Override
    public void visualize(CFGVisualizer<?, LiveVariableStore, ?> viz) {
        for (LiveVar liveVar : liveVarSet) {
            viz.visualizeSotreVal(liveVar.liveNode.toString());
        }
    }

    @Override
    public String toString() {
        return liveVarSet.toString();
    }
}
