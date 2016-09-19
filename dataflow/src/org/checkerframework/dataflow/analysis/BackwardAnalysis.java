package org.checkerframework.dataflow.analysis;

public interface BackwardAnalysis<
                V extends AbstractValue<V>,
                S extends Store<S>,
                T extends BackwardTransferFunction<V, S>>
        extends Analysis<V, S, T> {

    public S getEntrySotre();
}
