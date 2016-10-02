package org.checkerframework.dataflow.analysis;

import java.util.List;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.node.ReturnNode;

public interface BackwardTransferFunction<V extends AbstractValue<V>, S extends Store<S>>
        extends TransferFunction<V, S> {

    S initialNormalExitStore(UnderlyingAST underlyingAST, List<ReturnNode> returnNodes);

    S initialExceptionalExitStore(UnderlyingAST underlyingAST);
}
