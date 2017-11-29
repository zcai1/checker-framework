package org.checkerframework.framework.util;

/**
 * Specifies whether the type variable or wildcard has an explicit upper bound (UPPER), an explicit
 * lower bound (LOWER), or no explicit bounds (UNBOUNDED).
 */
public enum BoundType {

    /** Indicates an upper bounded type variable or wildcard */
    UPPER,

    /** Indicates a lower bounded type variable or wildcard */
    LOWER,

    /**
     * Neither bound is specified, BOTH are implicit. (If a type variable is declared in bytecode
     * and the type of the upper bound is Object, then the checker assumes that the bound was not
     * explicitly written in source code.)
     */
    UNBOUNDED
}
