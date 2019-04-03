package org.checkerframework.framework.qual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Specifies kinds of types.
 *
 * <p>These correspond to the constants in {@link javax.lang.model.type.TypeKind}. However, that
 * enum is not available on Android and a warning is produced. So this enum is used instead.
 *
 * @checker_framework.manual #creating-declarative-type-introduction Declaratively specifying
 *     implicit annotations
 */
public enum TypeKind {
    /** Corresponds to {@link javax.lang.model.type.TypeKind#BOOLEAN} types. */
    BOOLEAN,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#BYTE} types. */
    BYTE,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#SHORT} types. */
    SHORT,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#INT} types. */
    INT,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#LONG} types. */
    LONG,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#CHAR} types. */
    CHAR,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#FLOAT} types. */
    FLOAT,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#DOUBLE} types. */
    DOUBLE,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#VOID} types. */
    VOID,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#NONE} types. */
    NONE,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#NULL} types. */
    NULL,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#ARRAY} types. */
    ARRAY,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#DECLARED} types. */
    DECLARED,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#ERROR} types. */
    ERROR,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#TYPEVAR} types. */
    TYPEVAR,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#WILDCARD} types. */
    WILDCARD,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#PACKAGE} types. */
    PACKAGE,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#EXECUTABLE} types. */
    EXECUTABLE,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#OTHER} types. */
    OTHER,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#UNION} types. */
    UNION,

    /** Corresponds to {@link javax.lang.model.type.TypeKind#INTERSECTION} types. */
    INTERSECTION,

    /** Corresponds to all {@link javax.lang.model.type.TypeKind} types. */
    ALL;

    /**
     * Returns a list of TypeKinds containing only ALL
     *
     * @return list of TypeKind containing only ALL
     */
    public static TypeKind[] all() {
        TypeKind[] all = {ALL};
        return all;
    }

    /**
     * Returns all TypeKinds except for ALL.
     *
     * @return list of TypeKind except for ALL
     */
    public static TypeKind[] allTypeKinds() {
        List<TypeKind> list = new ArrayList<>(Arrays.asList(values()));
        list.remove(ALL);
        return list.toArray(new TypeKind[list.size()]);
    }

    /**
     * Map between list of {@link org.checkerframework.framework.qual.TypeKind} and list of {@link
     * javax.lang.model.type.TypeKind}.
     *
     * @param typeKinds the list of Checker Framework TypeKind
     * @return the javax list of TypeKind
     */
    public static List<javax.lang.model.type.TypeKind> mapTypeKinds(TypeKind[] typeKinds) {
        List<javax.lang.model.type.TypeKind> lst = new ArrayList<>();
        if (typeKinds == null) {
            return lst;
        }

        for (TypeKind type : typeKinds) {
            if (type.equals(ALL)) {
                return new ArrayList<javax.lang.model.type.TypeKind>(
                        Arrays.asList(javax.lang.model.type.TypeKind.values()));
            }

            lst.add(mapTypeKind(type));
        }

        return lst;
    }

    /**
     * Map between {@link org.checkerframework.framework.qual.TypeKind} and {@link
     * javax.lang.model.type.TypeKind}.
     *
     * @param typeKind the Checker Framework TypeKind
     * @return the javax TypeKind
     */
    public static javax.lang.model.type.TypeKind mapTypeKind(TypeKind typeKind) {
        if (typeKind == TypeKind.ALL) {
            return null;
        }
        return javax.lang.model.type.TypeKind.valueOf(typeKind.name());
    }

    /**
     * Returns {@code true} if this kind corresponds to a primitive type and {@code false}
     * otherwise.
     *
     * @return {@code true} if this kind corresponds to a primitive type
     */
    public boolean isPrimitive() {
        switch (this) {
            case BOOLEAN:
            case BYTE:
            case SHORT:
            case INT:
            case LONG:
            case CHAR:
            case FLOAT:
            case DOUBLE:
                return true;

            default:
                return false;
        }
    }
}
