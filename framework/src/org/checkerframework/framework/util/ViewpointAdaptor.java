package org.checkerframework.framework.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedNullType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedWildcardType;
import org.checkerframework.framework.type.AnnotatedTypeReplacer;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.Pair;

/**
 * Utility class to perform viewpoint adaptation. It has two major clients: one is on framework
 * side, the other is on inference side. See framework side implementation {@link
 * FrameworkViewpointAdaptor} for more information.
 *
 * @author tamier
 * @author wmdietl
 * @param <T> Represents type that we want to perform viewpoint adaptation on, i.e.
 *     AnnotationMirror(framework side) or Slot(inference side)
 */
public abstract class ViewpointAdaptor<T> {

    // This prevents calling combineTypeWithType on type variable if it is a bound of another type variable.
    // We only process one level. TODO: why we need this mechanism?
    protected boolean isTypeVarExtends = false;

    /**
     * Viewpoint adapt declModifier to recvModifier. Modifier here is not equal to AnnotationMirror.
     * Rather, it can be AnnotationMirror or Slot.
     *
     * @param recvModifier receiver modifier
     * @param declModifier declared modifier that is being adapted
     * @param f AnnotatedTypeFactory of concrete type system
     * @return result modifier after viewpoint adaptation
     */
    // side effect free! Need to use return value of this method to change annotation of others
    protected abstract T combineModifierWithModifier(
            T recvModifier, T declModifier, AnnotatedTypeFactory f);

    /**
     * Extract modifier from AnnotatedTypeMirror. On framework side, we extract AnnotationMiror; On
     * inference side, we extract slot.
     *
     * @param atm AnnotatedTypeMirror from which modifier is going to be extracted
     * @param f AnnotatedTypeFactory of concrete type system
     * @return modifier extracted
     */
    protected abstract T getModifier(AnnotatedTypeMirror atm, AnnotatedTypeFactory f);

    /**
     * Get and return the AnnotationMirror of a modifier
     *
     * @param t Source modifier from which AnnotationMirror is being extracted
     * @return AnnotationMirror extracted
     */
    protected abstract AnnotationMirror getAnnotationFromModifier(T t);

    /**
     * Retrieve AnnotationMirror from AnnotatedTypeMirror
     *
     * @param atm AnnotatedTypeMirror from which to extract AnnotationMirror
     * @param f AnnotatedTypeFactory of concrete type system
     * @return AnnotationMirror extracted
     */
    public final AnnotationMirror getAnnotationMirror(
            AnnotatedTypeMirror atm, AnnotatedTypeFactory f) {
        return getAnnotationFromModifier(getModifier(atm, f));
    }

    /**
     * Determines in which case type should not be viewpoint adapted.
     *
     * @param type type of the element after AnnotatedTypes#asMemberOfImpl
     * @param element element whose type is being considered to be adapted or not
     * @return true if type or element should not be adapted.
     */
    public boolean shouldBeAdapted(AnnotatedTypeMirror type, Element element) {
        return true;
    }

    /**
     * Viewpoint Adapt decl to recv, and return the result atm
     *
     * @param recv receiver in the viewpoint adaptation
     * @param decl declared type in viewpoint adaptation, which needs to be adapted
     * @param f AnnotatedTypeFactory of concrete type system
     * @return AnnotatedTypeMirror after viewpoint adaptation
     */
    public AnnotatedTypeMirror combineTypeWithType(
            AnnotatedTypeMirror recv, AnnotatedTypeMirror decl, AnnotatedTypeFactory f) {
        assert recv != null && decl != null && f != null;
        AnnotatedTypeMirror result = null;
        if (recv.getKind() == TypeKind.TYPEVAR) {
            recv = ((AnnotatedTypeVariable) recv).getUpperBound();
        }
        T recvModifier = getModifier(recv, f);
        if (recvModifier != null) {
            result = combineModifierWithType(recvModifier, decl, f);
            result = substituteTVars(f, recv, result);
        }
        return result;
    }

    /**
     * Viewpoint adapt decl to recvModifier. Side effect free, i.e. doesn't modify passed-in decl;
     * Instead create a shallow copy, modify that copy and return it
     *
     * @param recvModifier modifier of receiver in the viewpoint adaptation
     * @param decl declared type in viewpoint adaptation, which needs to be adapted
     * @param f AnnotatedTypeFactory of concrete type system
     * @return AnnotatedTypeMirror after viewpoint adaptation
     */
    protected AnnotatedTypeMirror combineModifierWithType(
            T recvModifier, AnnotatedTypeMirror decl, AnnotatedTypeFactory f) {
        if (decl.getKind().isPrimitive()) {
            return decl;
        } else if (decl.getKind() == TypeKind.TYPEVAR) {
            if (!isTypeVarExtends) {
                isTypeVarExtends = true;
                AnnotatedTypeVariable atv = (AnnotatedTypeVariable) decl.shallowCopy();
                Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

                // For type variables, we recursively adapt upper and lower bounds
                AnnotatedTypeMirror resUpper =
                        combineModifierWithType(recvModifier, atv.getUpperBound(), f);
                mapping.put(atv.getUpperBound(), resUpper);

                AnnotatedTypeMirror resLower =
                        combineModifierWithType(recvModifier, atv.getLowerBound(), f);
                mapping.put(atv.getLowerBound(), resLower);

                AnnotatedTypeMirror result = AnnotatedTypeReplacer.replace(atv, mapping);

                isTypeVarExtends = false;
                return result;
            }
            return decl;
        } else if (decl.getKind() == TypeKind.DECLARED) {
            AnnotatedDeclaredType adt = (AnnotatedDeclaredType) decl.shallowCopy();

            // Mapping between declared type argument to combined type argument
            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            T declModifier = getModifier(adt, f);
            T resultModifier = combineModifierWithModifier(recvModifier, declModifier, f);

            // Recursively combine type arguments and store to map
            for (AnnotatedTypeMirror typeArgument : adt.getTypeArguments()) {
                // Recursively adapt the type arguments of this adt
                AnnotatedTypeMirror combinedTypeArgument =
                        combineModifierWithType(recvModifier, typeArgument, f);
                mapping.put(typeArgument, combinedTypeArgument);
            }

            // Construct result type
            AnnotatedTypeMirror result = AnnotatedTypeReplacer.replace(adt, mapping);
            result.replaceAnnotation(getAnnotationFromModifier(resultModifier));

            return result;
        } else if (decl.getKind() == TypeKind.ARRAY) {
            AnnotatedArrayType aat = (AnnotatedArrayType) decl.shallowCopy();

            // Replace the main modifier
            T declModifier = getModifier(aat, f);
            T resultModifier = combineModifierWithModifier(recvModifier, declModifier, f);
            aat.replaceAnnotation(getAnnotationFromModifier(resultModifier));

            // Combine component type recursively and sets combined component type
            AnnotatedTypeMirror compo = aat.getComponentType();
            // Recursively call itself first on the component type
            AnnotatedTypeMirror combinedCompoType = combineModifierWithType(recvModifier, compo, f);
            aat.setComponentType(combinedCompoType);

            return aat;
        } else if (decl.getKind() == TypeKind.WILDCARD) {
            AnnotatedWildcardType awt = (AnnotatedWildcardType) decl.shallowCopy();

            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            // There is no main modifier for a wildcard

            // Adapt extend
            AnnotatedTypeMirror extend = awt.getExtendsBound();
            if (extend != null) {
                // Recursively adapt the extends bound of this awt
                AnnotatedTypeMirror combinedExtend =
                        combineModifierWithType(recvModifier, extend, f);
                mapping.put(extend, combinedExtend);
            }

            // Adapt super
            AnnotatedTypeMirror zuper = awt.getSuperBound();
            if (zuper != null) {
                // Recursively adapt the lower bound of this awt
                AnnotatedTypeMirror combinedZuper = combineModifierWithType(recvModifier, zuper, f);
                mapping.put(zuper, combinedZuper);
            }

            AnnotatedTypeMirror result = AnnotatedTypeReplacer.replace(awt, mapping);

            return result;
        } else if (decl.getKind() == TypeKind.NULL) {
            AnnotatedNullType ant = (AnnotatedNullType) decl.shallowCopy(true);
            T declModifier = getModifier(ant, f);
            T result = combineModifierWithModifier(recvModifier, declModifier, f);
            ant.replaceAnnotation(getAnnotationFromModifier(result));
            return ant;
        } else {
            ErrorReporter.errorAbort(
                    "ViewpointAdaptor::combineModifierWithType: Unknown decl: "
                            + decl
                            + " of kind: "
                            + decl.getKind());
            return null;
        }
    }

    /**
     * If rhs is type variable use whose type arguments should be inferred from receiver - lhs, this
     * method substitutes that type argument into rhs, and return the reference to rhs. So, this
     * method is side effect free, i.e., rhs will be copied and that copy gets modified and
     * returned.
     *
     * @param f AnnotatedTypeFactory of concrete type system
     * @param lhs type from which type arguments are extracted to replace formal type parameters of
     *     rhs.
     * @param rhs AnnotatedTypeMirror that might be a formal type parameter
     * @return rhs' copy with its type parameter substituted
     */
    private AnnotatedTypeMirror substituteTVars(
            AnnotatedTypeFactory f, AnnotatedTypeMirror lhs, AnnotatedTypeMirror rhs) {
        if (rhs.getKind() == TypeKind.TYPEVAR) {
            AnnotatedTypeVariable atv = (AnnotatedTypeVariable) rhs.shallowCopy();

            // Base case where actual type argument is extracted
            if (lhs.getKind() == TypeKind.DECLARED) {
                rhs = getTypeVariableSubstitution(f, (AnnotatedDeclaredType) lhs, atv);
            }
            // else TODO: the receiver might be another type variable... should we do something?
            // TODO: does that really happen?
        } else if (rhs.getKind() == TypeKind.DECLARED) {
            //System.out.println("before: " + rhs);
            AnnotatedDeclaredType adt = (AnnotatedDeclaredType) rhs.shallowCopy();
            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            for (AnnotatedTypeMirror formalTypeParameter : adt.getTypeArguments()) {
                AnnotatedTypeMirror actualTypeArgument =
                        substituteTVars(f, lhs, formalTypeParameter);
                //System.out.println("actual type argument: " + actualTypeArgument);
                mapping.put(formalTypeParameter, actualTypeArgument);
                // The following code does the wrong thing!
                /*T modifier = getModifier(actualTypeArgument, f);
                System.out.println("modifier: " + modifier);
                // Formally replace formal type parameter with actual type argument
                System.out.println("am: " + getAnnotationFromModifier(modifier));
                formalTypeArgument.replaceAnnotation(getAnnotationFromModifier(modifier));*/
            }
            // We must use AnnotatedTypeReplacer to replace the formal type parameters with actual type
            // arguments, but not replace with its main modifier
            rhs = AnnotatedTypeReplacer.replace(adt, mapping);
        } else if (rhs.getKind() == TypeKind.WILDCARD) {
            AnnotatedWildcardType awt = (AnnotatedWildcardType) rhs.shallowCopy();
            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            AnnotatedTypeMirror extend = awt.getExtendsBound();
            if (extend != null) {
                AnnotatedTypeMirror substExtend = substituteTVars(f, lhs, extend);
                mapping.put(extend, substExtend);
            }

            AnnotatedTypeMirror zuper = awt.getSuperBound();
            if (zuper != null) {
                AnnotatedTypeMirror substZuper = substituteTVars(f, lhs, zuper);
                mapping.put(zuper, substZuper);
            }

            rhs = AnnotatedTypeReplacer.replace(awt, mapping);
        } else if (rhs.getKind() == TypeKind.ARRAY) {
            AnnotatedArrayType aat = (AnnotatedArrayType) rhs.shallowCopy();
            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            AnnotatedTypeMirror compnentType = aat.getComponentType();
            // Type variable of compnentType already gets substituted
            AnnotatedTypeMirror substCompnentType = substituteTVars(f, lhs, compnentType);
            mapping.put(compnentType, substCompnentType);

            // Construct result type
            rhs = AnnotatedTypeReplacer.replace(aat, mapping);
        } else if (rhs.getKind().isPrimitive() || rhs.getKind() == TypeKind.NULL) {
            // nothing to do for primitive types and the null type
        } else {
            ErrorReporter.errorAbort(
                    "ViewpointAdaptor::substituteTVars: Cannot handle rhs: "
                            + rhs
                            + " of kind: "
                            + rhs.getKind());
        }

        return rhs;
    }

    /**
     * Return actual type argument for formal type parameter "var" from 'type"
     *
     * @param f AnnotatedTypeFactory of concrete type system
     * @param type type from which type arguments are extracted to replace "var"
     * @param var formal type parameter that needs real type arguments
     * @return Real type argument
     */
    private AnnotatedTypeMirror getTypeVariableSubstitution(
            AnnotatedTypeFactory f, AnnotatedDeclaredType type, AnnotatedTypeVariable var) {
        Pair<AnnotatedDeclaredType, Integer> res = findDeclType(type, var);

        if (res == null) {
            return var;
        }

        AnnotatedDeclaredType decltype = res.first;
        int foundindex = res.second;

        if (!decltype.wasRaw()) {
            // Explicitly provide actual type arguments
            List<AnnotatedTypeMirror> tas = decltype.getTypeArguments();
            // CAREFUL: return a copy, as we want to modify the type later.
            // TODO what's the difference for AnnotatedTypeReplacer?
            return tas.get(foundindex).shallowCopy(true);
        } else {
            // Type arguments not explicitly provided => use upper bound of var
            // TODO why?
            return var.getUpperBound();
        }
    }

    /**
     * Find the index(position) of this type variable from type
     *
     * @param type type from which we infer actual type arguments
     * @param var formal type parameter
     * @return index(position) of this type variable from type
     */
    private static Pair<AnnotatedDeclaredType, Integer> findDeclType(
            AnnotatedDeclaredType type, AnnotatedTypeVariable var) {
        Element varelem = var.getUnderlyingType().asElement();

        DeclaredType dtype = type.getUnderlyingType();
        TypeElement el = (TypeElement) dtype.asElement();
        List<? extends TypeParameterElement> tparams = el.getTypeParameters();
        int foundindex = 0;

        for (TypeParameterElement tparam : tparams) {
            if (tparam.equals(varelem) || tparam.getSimpleName().equals(varelem.getSimpleName())) {
                // we found the right index!
                break;
            }
            ++foundindex;
        }

        if (foundindex >= tparams.size()) {
            // Didn't find the desired type => Head for super type of "type"!
            for (AnnotatedDeclaredType sup : type.directSuperTypes()) {
                Pair<AnnotatedDeclaredType, Integer> res = findDeclType(sup, var);
                if (res != null) {
                    return res;
                }
            }
            // We reach this point if the variable wasn't found in any recursive call on ALL direct supertypes.
            return null;
        }

        return Pair.of(type, foundindex);
    }
}
