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
import org.checkerframework.dataflow.qual.SideEffectFree;
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
 * Utility class to perform viewpoint adaptation.
 *
 * @param <Modifier> Represents modifier on which viewpoint adaptation is performed, namely {@link
 *     AnnotationMirror} on typechecking side and {@code Slot} on inference side
 */
public abstract class ViewpointAdapter<Modifier> {

    // This prevents calling combineTypeWithType on type variable if it is an upper bound
    // of another type variable. We only viewpoint adapt type variable that is not upper-bound.
    protected boolean isTypeVarExtends = false;

    /**
     * Viewpoint adapt declared type to receiver type, and return the result atm
     *
     * @param receiver receiver type
     * @param declared declared type
     * @param atypeFactory {@link AnnotatedTypeFactory} of concrete type system
     * @return {@link AnnotatedTypeMirror} after viewpoint adaptation
     */
    public AnnotatedTypeMirror combineTypeWithType(
            AnnotatedTypeMirror receiver,
            AnnotatedTypeMirror declared,
            AnnotatedTypeFactory atypeFactory) {
        assert receiver != null && declared != null && atypeFactory != null;

        AnnotatedTypeMirror result = declared;

        if (receiver.getKind() == TypeKind.TYPEVAR) {
            receiver = ((AnnotatedTypeVariable) receiver).getUpperBound();
        }
        Modifier recvModifier = extractModifier(receiver, atypeFactory);
        if (recvModifier != null) {
            result = combineModifierWithType(recvModifier, declared, atypeFactory);
            result = substituteTVars(receiver, result);
        }

        return result;
    }

    /**
     * Extract modifier from {@link AnnotatedTypeMirror}.
     *
     * @param atm AnnotatedTypeMirror from which modifier is extracted
     * @param atypeFactory {@link AnnotatedTypeFactory} of concrete type system
     * @return modifier extracted
     */
    protected abstract Modifier extractModifier(
            AnnotatedTypeMirror atm, AnnotatedTypeFactory atypeFactory);

    /**
     * Sub-procedure to combine receiver modifiers with declared types. Modifiers are extracted from
     * declared types to furthur perform viewpoint adaptation only between two modifiers.
     *
     * @param receiver receiver modifier
     * @param declared declared type
     * @param atypeFactory {@link AnnotatedTypeFactory} of concrete type system
     * @return {@link AnnotatedTypeMirror} after viewpoint adaptation
     */
    protected AnnotatedTypeMirror combineModifierWithType(
            Modifier receiver, AnnotatedTypeMirror declared, AnnotatedTypeFactory atypeFactory) {
        if (declared.getKind().isPrimitive()) {
            return declared;
        } else if (declared.getKind() == TypeKind.TYPEVAR) {
            if (!isTypeVarExtends) {
                isTypeVarExtends = true;
                AnnotatedTypeVariable atv = (AnnotatedTypeVariable) declared.shallowCopy();
                Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

                // For type variables, we recursively adapt upper and lower bounds
                AnnotatedTypeMirror resUpper =
                        combineModifierWithType(receiver, atv.getUpperBound(), atypeFactory);
                mapping.put(atv.getUpperBound(), resUpper);

                AnnotatedTypeMirror resLower =
                        combineModifierWithType(receiver, atv.getLowerBound(), atypeFactory);
                mapping.put(atv.getLowerBound(), resLower);

                AnnotatedTypeMirror result = AnnotatedTypeReplacer.replace(atv, mapping);

                isTypeVarExtends = false;
                return result;
            }
            return declared;
        } else if (declared.getKind() == TypeKind.DECLARED) {
            AnnotatedDeclaredType adt = (AnnotatedDeclaredType) declared.shallowCopy();

            // Mapping between declared type argument to combined type argument
            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            Modifier declModifier = extractModifier(adt, atypeFactory);
            Modifier resultModifier =
                    combineModifierWithModifier(receiver, declModifier, atypeFactory);

            // Recursively combine type arguments and store to map
            for (AnnotatedTypeMirror typeArgument : adt.getTypeArguments()) {
                // Recursively adapt the type arguments of this adt
                AnnotatedTypeMirror combinedTypeArgument =
                        combineModifierWithType(receiver, typeArgument, atypeFactory);
                mapping.put(typeArgument, combinedTypeArgument);
            }

            // Construct result type
            AnnotatedTypeMirror result = AnnotatedTypeReplacer.replace(adt, mapping);
            result.replaceAnnotation(extractAnnotationMirror(resultModifier));

            return result;
        } else if (declared.getKind() == TypeKind.ARRAY) {
            AnnotatedArrayType aat = (AnnotatedArrayType) declared.shallowCopy();

            // Replace the main modifier
            Modifier declModifier = extractModifier(aat, atypeFactory);
            Modifier resultModifier =
                    combineModifierWithModifier(receiver, declModifier, atypeFactory);
            aat.replaceAnnotation(extractAnnotationMirror(resultModifier));

            // Combine component type recursively and sets combined component type
            AnnotatedTypeMirror compo = aat.getComponentType();
            // Recursively call itself first on the component type
            AnnotatedTypeMirror combinedCompoType =
                    combineModifierWithType(receiver, compo, atypeFactory);
            aat.setComponentType(combinedCompoType);

            return aat;
        } else if (declared.getKind() == TypeKind.WILDCARD) {
            AnnotatedWildcardType awt = (AnnotatedWildcardType) declared.shallowCopy();

            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            // There is no main modifier for a wildcard

            // Adapt extend
            AnnotatedTypeMirror extend = awt.getExtendsBound();
            if (extend != null) {
                // Recursively adapt the extends bound of this awt
                AnnotatedTypeMirror combinedExtend =
                        combineModifierWithType(receiver, extend, atypeFactory);
                mapping.put(extend, combinedExtend);
            }

            // Adapt super
            AnnotatedTypeMirror zuper = awt.getSuperBound();
            if (zuper != null) {
                // Recursively adapt the lower bound of this awt
                AnnotatedTypeMirror combinedZuper =
                        combineModifierWithType(receiver, zuper, atypeFactory);
                mapping.put(zuper, combinedZuper);
            }

            AnnotatedTypeMirror result = AnnotatedTypeReplacer.replace(awt, mapping);

            return result;
        } else if (declared.getKind() == TypeKind.NULL) {
            AnnotatedNullType ant = (AnnotatedNullType) declared.shallowCopy(true);
            Modifier declModifier = extractModifier(ant, atypeFactory);
            Modifier result = combineModifierWithModifier(receiver, declModifier, atypeFactory);
            ant.replaceAnnotation(extractAnnotationMirror(result));
            return ant;
        } else {
            ErrorReporter.errorAbort(
                    "ViewpointAdaptor::combineModifierWithType: Unknown decl: "
                            + declared
                            + " of kind: "
                            + declared.getKind());
            return null;
        }
    }

    /**
     * Extracts {@link AnnotationMirror} from a modifier.
     *
     * @param modifier Source modifier from which {@link AnnotationMirror} is extracted
     * @return {@link AnnotationMirror} extracted
     */
    protected abstract AnnotationMirror extractAnnotationMirror(Modifier modifier);

    /**
     * Viewpoint adapt declared modifier to receiver modifier.
     *
     * @param receiver receiver modifier
     * @param declared declared modifier
     * @param atypeFactory {@link AnnotatedTypeFactory} of concrete type system
     * @return result modifier after viewpoint adaptation
     */
    @SideEffectFree
    protected abstract Modifier combineModifierWithModifier(
            Modifier receiver, Modifier declared, AnnotatedTypeFactory atypeFactory);

    /**
     * If rhs is type variable use whose type arguments should be inferred from receiver - lhs, this
     * method substitutes that type argument into rhs, and return the reference to rhs. So, this
     * method is side effect free, i.e., rhs will be copied and that copy gets modified and
     * returned.
     *
     * @param lhs type from which type arguments are extracted to replace formal type parameters of
     *     rhs.
     * @param rhs AnnotatedTypeMirror that might be a formal type parameter
     * @return rhs' copy with its type parameter substituted
     */
    private AnnotatedTypeMirror substituteTVars(AnnotatedTypeMirror lhs, AnnotatedTypeMirror rhs) {
        if (rhs.getKind() == TypeKind.TYPEVAR) {
            AnnotatedTypeVariable atv = (AnnotatedTypeVariable) rhs.shallowCopy();

            // Base case where actual type argument is extracted
            if (lhs.getKind() == TypeKind.DECLARED) {
                rhs = getTypeVariableSubstitution((AnnotatedDeclaredType) lhs, atv);
            }
            // else TODO: the receiver might be another type variable... should we do something?
        } else if (rhs.getKind() == TypeKind.DECLARED) {
            //System.out.println("before: " + rhs);
            AnnotatedDeclaredType adt = (AnnotatedDeclaredType) rhs.shallowCopy();
            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            for (AnnotatedTypeMirror formalTypeParameter : adt.getTypeArguments()) {
                AnnotatedTypeMirror actualTypeArgument = substituteTVars(lhs, formalTypeParameter);
                mapping.put(formalTypeParameter, actualTypeArgument);
                // The following code does the wrong thing!
            }
            // We must use AnnotatedTypeReplacer to replace the formal type parameters with actual type
            // arguments, but not replace with its main modifier
            rhs = AnnotatedTypeReplacer.replace(adt, mapping);
        } else if (rhs.getKind() == TypeKind.WILDCARD) {
            AnnotatedWildcardType awt = (AnnotatedWildcardType) rhs.shallowCopy();
            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            AnnotatedTypeMirror extend = awt.getExtendsBound();
            if (extend != null) {
                AnnotatedTypeMirror substExtend = substituteTVars(lhs, extend);
                mapping.put(extend, substExtend);
            }

            AnnotatedTypeMirror zuper = awt.getSuperBound();
            if (zuper != null) {
                AnnotatedTypeMirror substZuper = substituteTVars(lhs, zuper);
                mapping.put(zuper, substZuper);
            }

            rhs = AnnotatedTypeReplacer.replace(awt, mapping);
        } else if (rhs.getKind() == TypeKind.ARRAY) {
            AnnotatedArrayType aat = (AnnotatedArrayType) rhs.shallowCopy();
            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> mapping = new HashMap<>();

            AnnotatedTypeMirror compnentType = aat.getComponentType();
            // Type variable of compnentType already gets substituted
            AnnotatedTypeMirror substCompnentType = substituteTVars(lhs, compnentType);
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
     * @param type type from which type arguments are extracted to replace "var"
     * @param var formal type parameter that needs real type arguments
     * @return Real type argument
     */
    private AnnotatedTypeMirror getTypeVariableSubstitution(
            AnnotatedDeclaredType type, AnnotatedTypeVariable var) {
        Pair<AnnotatedDeclaredType, Integer> res = findDeclType(type, var);

        if (res == null) {
            return var;
        }

        AnnotatedDeclaredType decltype = res.first;
        int foundindex = res.second;

        // TODO Original GUT implementation checks whether type was raw or not.
        // But that caused strange errors and caused some tests to fail unreasonably.
        List<AnnotatedTypeMirror> tas = decltype.getTypeArguments();
        // return a copy, as we want to modify the type later.
        return tas.get(foundindex).shallowCopy(true);
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
            // TODO Comparing with simple name is dangerous!
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
