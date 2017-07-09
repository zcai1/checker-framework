package org.checkerframework.framework.util;

public enum BoundType {

    /** Indicates an upper bounded type variable or wildcard */
    UPPER,

    /** Indicates a lower bounded type variable or wildcard */
    LOWER,

    /** Neither bound is specified, BOTH are implicit */
    UNBOUND,

    /**
     * For bytecode, or trees for which we no longer have the compilation unit. We treat UNKNOWN
     * bounds as if they are an UPPER bound.
     */
    UNKNOWN;

    public boolean isOneOf(final BoundType... choices) {
        for (final BoundType choice : choices) {
            if (this == choice) {
                return true;
            }
        }

        return false;
    }
}
