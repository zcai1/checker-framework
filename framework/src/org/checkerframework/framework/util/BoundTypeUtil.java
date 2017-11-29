package org.checkerframework.framework.util;

import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Type.WildcardType;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeParameterElement;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedWildcardType;
import org.checkerframework.javacutil.CollectionUtils;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.TypesUtils;

/** Utility class to get {@link BoundType} of a type variable or wildcard */
public class BoundTypeUtil {

    /** Mapping from an Element to the source Tree of the declaration. */
    private static final int CACHE_SIZE = 300;

    protected static final Map<Element, BoundType> elementToBoundType =
            CollectionUtils.createLRUCache(CACHE_SIZE);

    public static boolean isOneOf(final BoundType target, final BoundType... choices) {
        for (final BoundType choice : choices) {
            if (target == choice) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param type the type whose boundType is returned. type must be an AnnotatedWildcardType or
     *     AnnotatedTypeVariable.
     * @return the boundType for type
     */
    public static BoundType getBoundType(
            final AnnotatedTypeMirror type, final AnnotatedTypeFactory typeFactory) {
        if (type instanceof AnnotatedTypeVariable) {
            return getTypeVarBoundType((AnnotatedTypeVariable) type, typeFactory);
        }

        if (type instanceof AnnotatedWildcardType) {
            return getWildcardBoundType((AnnotatedWildcardType) type, typeFactory);
        }

        ErrorReporter.errorAbort("Unexpected type kind: type=" + type);
        return null; // dead code
    }

    /** @return the bound type of the input typeVar */
    private static BoundType getTypeVarBoundType(
            final AnnotatedTypeVariable typeVar, final AnnotatedTypeFactory typeFactory) {
        return getTypeVarBoundType(
                (TypeParameterElement) typeVar.getUnderlyingType().asElement(), typeFactory);
    }

    /** @return the boundType (UPPER or UNBOUNDED) of the declaration of typeParamElem */
    // Results are cached in {@link elementToBoundType}.
    private static BoundType getTypeVarBoundType(
            final TypeParameterElement typeParamElem, final AnnotatedTypeFactory typeFactory) {
        final BoundType prev = elementToBoundType.get(typeParamElem);
        if (prev != null) {
            return prev;
        }

        TreePath declaredTypeVarEle = typeFactory.getTreeUtils().getPath(typeParamElem);
        Tree typeParamDecl = declaredTypeVarEle == null ? null : declaredTypeVarEle.getLeaf();

        final BoundType boundType;
        if (typeParamDecl == null) {
            // This is not only for elements from binaries, but also
            // when the compilation unit is no-longer available.
            if (typeParamElem.getBounds().size() == 1
                    && TypesUtils.isObject(typeParamElem.getBounds().get(0))) {
                // If the bound was Object, then it may or may not have been explicitly written.
                // Assume that it was not.
                boundType = BoundType.UNBOUNDED;
            } else {
                // The bound is not Object, so it must have been explicitly written and thus the
                // type variable has an upper bound.
                boundType = BoundType.UPPER;
            }

        } else {
            if (typeParamDecl.getKind() == Tree.Kind.TYPE_PARAMETER) {
                final TypeParameterTree tptree = (TypeParameterTree) typeParamDecl;

                List<? extends Tree> bnds = tptree.getBounds();
                if (bnds != null && !bnds.isEmpty()) {
                    boundType = BoundType.UPPER;
                } else {
                    boundType = BoundType.UNBOUNDED;
                }
            } else {
                ErrorReporter.errorAbort(
                        "Unexpected tree type for typeVar Element:\n"
                                + "typeParamElem="
                                + typeParamElem
                                + "\n"
                                + typeParamDecl);
                boundType = null; // dead code
            }
        }

        elementToBoundType.put(typeParamElem, boundType);
        return boundType;
    }

    /**
     * @return the BoundType of annotatedWildcard. If it is unbounded, use the type parameter to
     *     which its an argument.
     */
    public static BoundType getWildcardBoundType(
            final AnnotatedWildcardType annotatedWildcard, final AnnotatedTypeFactory typeFactory) {

        final WildcardType wildcard = (WildcardType) annotatedWildcard.getUnderlyingType();

        final BoundType boundType;
        if (wildcard.isUnbound() && wildcard.bound != null) {
            boundType =
                    getTypeVarBoundType(
                            (TypeParameterElement) wildcard.bound.asElement(), typeFactory);

        } else {
            // note: isSuperBound will be true for unbounded and lowers, but the unbounded case is
            // already handled
            boundType = wildcard.isSuperBound() ? BoundType.LOWER : BoundType.UPPER;
        }

        return boundType;
    }
}
