package org.checkerframework.framework.util.defaults;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.framework.qual.TypeKind;
import org.checkerframework.framework.qual.TypeUseLocation;
import org.checkerframework.javacutil.AnnotationUtils;

/**
 * Represents a mapping from an Annotation to a TypeUseLocation it should be applied to during
 * defaulting. The Comparable ordering of this class first tests location then tests annotation
 * ordering (via {@link org.checkerframework.javacutil.AnnotationUtils}).
 *
 * <p>It also has a handy toString method that is useful for debugging.
 */
public class Default implements Comparable<Default> {
    // please remember to add any fields to the hashcode calculation
    public final AnnotationMirror anno;
    public final TypeUseLocation location;
    public final TypeKind[] types;

    public Default(final AnnotationMirror anno, final TypeUseLocation location) {
        this(anno, location, TypeKind.all());
    }

    public Default(
            final AnnotationMirror anno, final TypeUseLocation location, final TypeKind[] types) {
        this.anno = anno;
        this.location = location;
        this.types = types;
    }

    @Override
    public int compareTo(Default other) {
        int locationOrder = location.compareTo(other.location);
        int typesOrder = 1;
        Set<TypeKind> thisTypeSet = new HashSet<>(Arrays.asList(types));
        Set<TypeKind> otherTypeSet = new HashSet<>(Arrays.asList(other.types));
        if (thisTypeSet.containsAll(otherTypeSet)
                || thisTypeSet.contains(TypeKind.ALL)
                || otherTypeSet.containsAll(thisTypeSet)
                || otherTypeSet.contains(TypeKind.ALL)) {
            typesOrder = 0;
        }
        if (locationOrder == 0 && typesOrder == 0) {
            return AnnotationUtils.annotationOrdering().compare(anno, other.anno);
        } else {
            return (locationOrder * typesOrder) + (locationOrder + typesOrder);
        }
    }

    @Override
    public boolean equals(Object thatObj) {
        if (thatObj == this) {
            return true;
        }

        if (thatObj == null || !thatObj.getClass().equals(Default.class)) {
            return false;
        }

        return compareTo((Default) thatObj) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(anno, location);
    }

    @Override
    public String toString() {
        return "( " + location.name() + " => " + anno + " )";
    }
}
