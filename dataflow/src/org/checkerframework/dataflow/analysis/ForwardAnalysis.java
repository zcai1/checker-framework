package org.checkerframework.dataflow.analysis;

import java.util.List;
import org.checkerframework.dataflow.cfg.node.ReturnNode;
import org.checkerframework.javacutil.Pair;

public interface ForwardAnalysis<
                V extends AbstractValue<V>,
                S extends Store<S>,
                T extends ForwardTransferFunction<V, S>>
        extends Analysis<V, S, T> {

    public List<Pair<ReturnNode, TransferResult<V, S>>> getReturnStatementStores();
}
