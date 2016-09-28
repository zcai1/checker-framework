package org.checkerframework.dataflow.livevariable;

import org.checkerframework.dataflow.analysis.AbstractValue;
import org.checkerframework.dataflow.cfg.node.Node;

public class LiveVar implements AbstractValue<LiveVar> {

    Node liveNode;

    @Override
    public LiveVar leastUpperBound(LiveVar other) {
        throw new RuntimeException("lub of LiveVar get called!");
    }

    public LiveVar(Node n) {
        this.liveNode = n;
    }

    @Override
    public int hashCode() {
        return this.liveNode.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof LiveVar)) {
            return false;
        }

        LiveVar other = (LiveVar) obj;
        return this.liveNode.equals(other.liveNode);
    }

    @Override
    public String toString() {
        return this.liveNode.toString();
    }
}
