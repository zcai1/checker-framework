package org.checkerframework.dataflow.analysis;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.node.Node;

/**
 * Interface of Abstract Analysis<V, S, T>
 * @author charleszhuochen
 *
 * @param <V>
 * @param <S>
 * @param <T>
 */
public interface Analysis<
        V extends AbstractValue<V>, S extends Store<S>, T extends TransferFunction<V, S>> {

    public static enum Direction {
        FORWARD,
        BACKWARD
    }

    public Direction getDirection();

    public boolean isRunning();

    public void performAnalysis(ControlFlowGraph cfg);

    public AnalysisResult<V, S> getResult();

    public T getTransferFunction();

    public void setTransferFunction(T transferFunction);

    public Tree getCurrentTree();

    public void setCurrentTree(Tree t);

    public TransferInput<V, S> getInput(Block b);

    public V getValue(Node n);

    public V getValue(Tree t);

    public Node getNodeForTree(Tree t);

    public MethodTree getContainingMethod(Tree t);

    public ClassTree getContainingClass(Tree t);

    public S getRegularExitStore();

    public S getExceptionalExitStore();
}
