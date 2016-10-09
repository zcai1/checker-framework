package org.checkerframework.common.basetype;

/*>>>
import org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey;
*/

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Type.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeKind;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.DefaultQualifiers;
import org.checkerframework.framework.qual.PolyAll;
import org.checkerframework.framework.qual.TargetLocations;
import org.checkerframework.framework.qual.TypeUseLocation;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedNullType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedWildcardType;
import org.checkerframework.framework.type.AnnotatedTypeParameterBounds;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.visitor.AnnotatedTypeScanner;
import org.checkerframework.framework.util.AnnotatedTypes;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.CollectionUtils;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.InternalUtils;
import org.checkerframework.javacutil.Pair;
import org.checkerframework.javacutil.TreeUtils;

/** A visitor to validate the types in a tree. */
public class BaseTypeValidator extends AnnotatedTypeScanner<Void, Tree> implements TypeValidator {
    protected boolean isValid = true;

    protected final BaseTypeChecker checker;
    protected final BaseTypeVisitor<?> visitor;
    protected final AnnotatedTypeFactory atypeFactory;
    protected final TypeUseLocationValidator locationValidator;
    // TODO: clean up coupling between components
    public BaseTypeValidator(
            BaseTypeChecker checker,
            BaseTypeVisitor<?> visitor,
            AnnotatedTypeFactory atypeFactory) {
        this.checker = checker;
        this.visitor = visitor;
        this.atypeFactory = atypeFactory;
        locationValidator = new TypeUseLocationValidator(this);
    }

    /**
     * The entry point to the type validator. Validate the type against the given tree. Neither this
     * method nor visit should be called directly by a visitor, only use {@link
     * BaseTypeVisitor#validateTypeOf(Tree)}.
     *
     * @param type the type to validate
     * @param tree the tree from which the type originated. If the tree is a method tree, validate
     *     its return type. If the tree is a variable tree, validate its field type.
     * @return true, iff the type is valid
     */
    @Override
    public boolean isValid(AnnotatedTypeMirror type, Tree tree) {
        this.isValid = true;
        visit(type, tree);
        // TODO doesn't passin class tree!!!
        //System.out.println("Tree " + tree + " is being checked:");
        //System.out.println("\nEntry: type: " + type + " kind: " + type.getKind() + "\n");
        locationValidator.validate(type, tree);
        return this.isValid;
    }

    protected void reportValidityResult(
            final /*@CompilerMessageKey*/ String errorType,
            final AnnotatedTypeMirror type,
            final Tree p) {
        checker.report(Result.failure(errorType, type.getAnnotations(), type.toString()), p);
        isValid = false;
    }

    /**
     * Most errors reported by this class are of the form type.invalid. This method reports when the
     * bounds of a wildcard or type variable don't make sense. Bounds make sense when the effective
     * annotations on the upper bound are supertypes of those on the lower bounds for all
     * hierarchies. To ensure that this subtlety is not lost on users, we report
     * "bound.type.incompatible" and print the bounds along with the invalid type rather than a
     * "type.invalid".
     */
    protected void reportInvalidBounds(final AnnotatedTypeMirror type, final Tree tree) {
        final String label;
        final AnnotatedTypeMirror upperBound;
        final AnnotatedTypeMirror lowerBound;

        switch (type.getKind()) {
            case TYPEVAR:
                label = "type parameter";
                upperBound = ((AnnotatedTypeVariable) type).getUpperBound();
                lowerBound = ((AnnotatedTypeVariable) type).getLowerBound();
                break;

            case WILDCARD:
                label = "wildcard";
                upperBound = ((AnnotatedWildcardType) type).getExtendsBound();
                lowerBound = ((AnnotatedWildcardType) type).getSuperBound();
                break;

            default:
                ErrorReporter.errorAbort(
                        "Type is not bounded.\n" + "type=" + type + "\n" + "tree=" + tree);
                label = null; // dead code
                upperBound = null;
                lowerBound = null;
        }

        checker.report(
                Result.failure(
                        "bound.type.incompatible",
                        label,
                        type.toString(),
                        upperBound.toString(true),
                        lowerBound.toString(true)),
                tree);
        isValid = false;
    }

    protected void reportError(final AnnotatedTypeMirror type, final Tree p) {
        reportValidityResult("type.invalid", type, p);
    }

    @Override
    public Void visitDeclared(AnnotatedDeclaredType type, Tree tree) {
        if (visitedNodes.containsKey(type)) {
            return visitedNodes.get(type);
        }

        final boolean skipChecks = checker.shouldSkipUses(type.getUnderlyingType().asElement());

        if (!skipChecks) {
            // Ensure that type use is a subtype of the element type
            // isValidUse determines the erasure of the types.
            AnnotatedDeclaredType elemType =
                    (AnnotatedDeclaredType)
                            atypeFactory.getAnnotatedType(type.getUnderlyingType().asElement());

            if (!visitor.isValidUse(elemType, type, tree)) {
                reportError(type, tree);
            }
        }

        /*
         * Try to reconstruct the ParameterizedTypeTree from the given tree.
         * TODO: there has to be a nicer way to do this...
         */
        Pair<ParameterizedTypeTree, AnnotatedDeclaredType> p =
                extractParameterizedTypeTree(tree, type);
        ParameterizedTypeTree typeArgTree = p.first;
        type = p.second;

        if (typeArgTree == null) {
            return super.visitDeclared(type, tree);
        } // else

        // We put this here because we don't want to put it in visitedNodes before calling
        // super (in the else branch) because that would cause the super implementation
        // to detect that we've already visited type and to immediately return
        visitedNodes.put(type, null);

        // We have a ParameterizedTypeTree -> visit it.

        visitParameterizedType(type, typeArgTree);

        /*
         * Instead of calling super with the unchanged "tree", adapt the
         * second argument to be the corresponding type argument tree. This
         * ensures that the first and second parameter to this method always
         * correspond. visitDeclared is the only method that had this
         * problem.
         */
        List<? extends AnnotatedTypeMirror> tatypes = type.getTypeArguments();

        if (tatypes == null) {
            return null;
        }

        // May be zero for a "diamond" (inferred type args in constructor
        // invocation).
        int numTypeArgs = typeArgTree.getTypeArguments().size();
        if (numTypeArgs != 0) {
            // TODO: this should be an equality, but in
            // http://buffalo.cs.washington.edu:8080/job/jdk6-daikon-typecheck/2061/console
            // it failed with:
            // daikon/Debug.java; message: size mismatch for type arguments:
            // @NonNull Object and Class<?>
            // but I didn't manage to reduce it to a test case.
            assert tatypes.size() <= numTypeArgs || skipChecks
                    : "size mismatch for type arguments: " + type + " and " + typeArgTree;

            for (int i = 0; i < tatypes.size(); ++i) {
                scan(tatypes.get(i), typeArgTree.getTypeArguments().get(i));
            }
        }

        // Don't call the super version, because it creates a mismatch
        // between
        // the first and second parameters.
        // return super.visitDeclared(type, tree);

        return null;
    }

    private Pair<ParameterizedTypeTree, AnnotatedDeclaredType> extractParameterizedTypeTree(
            Tree tree, AnnotatedDeclaredType type) {
        ParameterizedTypeTree typeargtree = null;

        switch (tree.getKind()) {
            case VARIABLE:
                Tree lt = ((VariableTree) tree).getType();
                if (lt instanceof ParameterizedTypeTree) {
                    typeargtree = (ParameterizedTypeTree) lt;
                } else {
                    // System.out.println("Found a: " + lt);
                }
                break;
            case PARAMETERIZED_TYPE:
                typeargtree = (ParameterizedTypeTree) tree;
                break;
            case NEW_CLASS:
                NewClassTree nct = (NewClassTree) tree;
                ExpressionTree nctid = nct.getIdentifier();
                if (nctid.getKind() == Tree.Kind.PARAMETERIZED_TYPE) {
                    typeargtree = (ParameterizedTypeTree) nctid;
                    /*
                     * This is quite tricky... for anonymous class instantiations,
                     * the type at this point has no type arguments. By doing the
                     * following, we get the type arguments again.
                     */
                    type = (AnnotatedDeclaredType) atypeFactory.getAnnotatedType(typeargtree);
                }
                break;
            case ANNOTATED_TYPE:
                AnnotatedTypeTree tr = (AnnotatedTypeTree) tree;
                ExpressionTree undtr = tr.getUnderlyingType();
                if (undtr instanceof ParameterizedTypeTree) {
                    typeargtree = (ParameterizedTypeTree) undtr;
                } else if (undtr instanceof IdentifierTree) {
                    // @Something D -> Nothing to do
                } else {
                    // TODO: add more test cases to ensure that nested types are
                    // handled correctly,
                    // e.g. @Nullable() List<@Nullable Object>[][]
                    Pair<ParameterizedTypeTree, AnnotatedDeclaredType> p =
                            extractParameterizedTypeTree(undtr, type);
                    typeargtree = p.first;
                    type = p.second;
                }
                break;
            case IDENTIFIER:
            case ARRAY_TYPE:
            case NEW_ARRAY:
            case MEMBER_SELECT:
            case UNBOUNDED_WILDCARD:
            case EXTENDS_WILDCARD:
            case SUPER_WILDCARD:
            case TYPE_PARAMETER:
                // Nothing to do.
                // System.out.println("Found a: " + (tree instanceof
                // ParameterizedTypeTree));
                break;
            default:
                // the parameterized type is the result of some expression tree.
                // No need to do anything further.
                break;
                // System.err.printf("TypeValidator.visitDeclared unhandled tree: %s of kind %s\n",
                //                 tree, tree.getKind());
        }

        return Pair.of(typeargtree, type);
    }

    @Override
    public Void visitPrimitive(AnnotatedPrimitiveType type, Tree tree) {
        if (checker.shouldSkipUses(type.getUnderlyingType().toString())) {
            return super.visitPrimitive(type, tree);
        }

        if (!visitor.isValidUse(type, tree)) {
            reportError(type, tree);
        }

        return super.visitPrimitive(type, tree);
    }

    @Override
    public Void visitArray(AnnotatedArrayType type, Tree tree) {
        // TODO: is there already or add a helper method
        // to determine the non-array component type
        AnnotatedTypeMirror comp = type;
        do {
            comp = ((AnnotatedArrayType) comp).getComponentType();
        } while (comp.getKind() == TypeKind.ARRAY);

        if (comp.getKind() == TypeKind.DECLARED
                && checker.shouldSkipUses(
                        ((AnnotatedDeclaredType) comp).getUnderlyingType().asElement())) {
            return super.visitArray(type, tree);
        }

        if (!visitor.isValidUse(type, tree)) {
            reportError(type, tree);
        }

        return super.visitArray(type, tree);
    }

    /**
     * Checks that the annotations on the type arguments supplied to a type or a method invocation
     * are within the bounds of the type variables as declared, and issues the
     * "type.argument.type.incompatible" error if they are not.
     *
     * <p>This method used to be visitParameterizedType, which incorrectly handles the main
     * annotation on generic types.
     */
    protected Void visitParameterizedType(AnnotatedDeclaredType type, ParameterizedTypeTree tree) {
        // System.out.printf("TypeValidator.visitParameterizedType: type: %s, tree: %s\n",
        // type, tree);

        if (TreeUtils.isDiamondTree(tree)) {
            return null;
        }

        final TypeElement element = (TypeElement) type.getUnderlyingType().asElement();
        if (checker.shouldSkipUses(element)) {
            return null;
        }

        List<AnnotatedTypeParameterBounds> bounds =
                atypeFactory.typeVariablesFromUse(type, element);

        visitor.checkTypeArguments(tree, bounds, type.getTypeArguments(), tree.getTypeArguments());

        return null;
    }

    @Override
    public Void visitTypeVariable(AnnotatedTypeVariable type, Tree tree) {
        if (visitedNodes.containsKey(type)) {
            return visitedNodes.get(type);
        }
        // TODO why is this not needed?
        // visitedNodes.put(type, null);

        if (type.isDeclaration() && !areBoundsValid(type.getUpperBound(), type.getLowerBound())) {
            reportInvalidBounds(type, tree);
        }

        // Keep in sync with visitWildcard
        Set<AnnotationMirror> onVar = type.getAnnotations();
        if (!onVar.isEmpty()) {
            // System.out.printf("BaseTypeVisitor.TypeValidator.visitTypeVariable(type: %s, tree: %s)%n",
            // type, tree);

            // TODO: the following check should not be necessary, once we are
            // able to
            // recurse on type parameters in AnnotatedTypes.isValidType (see
            // todo there).
            {
                // Check whether multiple qualifiers from the same hierarchy
                // appear.
                checkConflictingPrimaryAnnos(type, tree);
            }

            // TODO: because of the way AnnotatedTypeMirror fixes up the bounds,
            // i.e. an annotation on the type variable always replaces a
            // corresponding
            // annotation in the bound, some of these checks are not actually
            // meaningful.
            /*if (type.getUpperBoundField() != null) {
                AnnotatedTypeMirror upper = type.getUpperBoundField();

                for (AnnotationMirror aOnVar : onVar) {
                    if (upper.isAnnotatedInHierarchy(aOnVar) &&
                            !checker.getQualifierHierarchy().isSubtype(aOnVar,
                                    upper.findAnnotationInHierarchy(aOnVar))) {
                        this.reportError(type, tree);
                    }
                }
                upper.replaceAnnotations(onVar);
            }*/

        }

        return super.visitTypeVariable(type, tree);
    }

    @Override
    public Void visitWildcard(AnnotatedWildcardType type, Tree tree) {
        if (visitedNodes.containsKey(type)) {
            return visitedNodes.get(type);
        }
        // TODO why is this not neede?
        // visitedNodes.put(type, null);

        if (!areBoundsValid(type.getExtendsBound(), type.getSuperBound())) {
            reportInvalidBounds(type, tree);
        }

        // Keep in sync with visitTypeVariable
        Set<AnnotationMirror> onVar = type.getAnnotations();
        if (!onVar.isEmpty()) {
            // System.out.printf("BaseTypeVisitor.TypeValidator.visitWildcard(type: %s, tree: %s)",
            // type, tree);

            // TODO: the following check should not be necessary, once we are
            // able to
            // recurse on type parameters in AnnotatedTypes.isValidType (see
            // todo there).
            {
                // Check whether multiple qualifiers from the same hierarchy
                // appear.
                checkConflictingPrimaryAnnos(type, tree);
            }

            /* TODO: see note with visitTypeVariable
            if (type.getExtendsBoundField() != null) {
                AnnotatedTypeMirror upper = type.getExtendsBoundField();
                for (AnnotationMirror aOnVar : onVar) {
                    if (upper.isAnnotatedInHierarchy(aOnVar) &&
                            !atypeFactory.getQualifierHierarchy().isSubtype(aOnVar,
                                    upper.findAnnotationInHierarchy(aOnVar))) {
                        this.reportError(type, tree);
                    }
                }
                upper.replaceAnnotations(onVar);
            }
            */

            if (type.getSuperBoundField() != null) {
                AnnotatedTypeMirror lower = type.getSuperBoundField();
                for (AnnotationMirror aOnVar : onVar) {
                    if (lower.isAnnotatedInHierarchy(aOnVar)
                            && !atypeFactory
                                    .getQualifierHierarchy()
                                    .isSubtype(lower.getAnnotationInHierarchy(aOnVar), aOnVar)) {
                        this.reportError(type, tree);
                    }
                }
                lower.replaceAnnotations(onVar);
            }
        }
        return super.visitWildcard(type, tree);
    }

    @Override
    public Void visitNull(final AnnotatedNullType type, final Tree tree) {
        checkConflictingPrimaryAnnos(type, tree);

        return super.visitNull(type, tree);
    }

    /**
     * @return true if the effective annotations on the upperBound are above those on the lowerBound
     */
    public boolean areBoundsValid(
            final AnnotatedTypeMirror upperBound, final AnnotatedTypeMirror lowerBound) {
        final QualifierHierarchy qualifierHierarchy = atypeFactory.getQualifierHierarchy();
        final Set<AnnotationMirror> upperBoundAnnos =
                AnnotatedTypes.findEffectiveAnnotations(qualifierHierarchy, upperBound);
        final Set<AnnotationMirror> lowerBoundAnnos =
                AnnotatedTypes.findEffectiveAnnotations(qualifierHierarchy, lowerBound);

        if (upperBoundAnnos.size() == lowerBoundAnnos.size()) {
            return qualifierHierarchy.isSubtype(lowerBoundAnnos, upperBoundAnnos);
        } // else
        //  When upperBoundAnnos.size() != lowerBoundAnnos.size() one of the two bound types will
        //  be reported as invalid.  Therefore, we do not do any other comparisons nor do we report
        //  a bound.type.incompatible

        return true;
    }

    /**
     * Determines if there are multiple qualifiers from a single hierarchy in type's primary
     * annotations. If so, report an error.
     *
     * @param type the type to check
     * @param tree tree on which an error is reported
     * @return true if an error was reported
     */
    public boolean checkConflictingPrimaryAnnos(final AnnotatedTypeMirror type, final Tree tree) {
        boolean error = false;
        Set<AnnotationMirror> seenTops = AnnotationUtils.createAnnotationSet();
        for (AnnotationMirror aOnVar : type.getAnnotations()) {
            if (AnnotationUtils.areSameByClass(aOnVar, PolyAll.class)) {
                continue;
            }
            AnnotationMirror top = atypeFactory.getQualifierHierarchy().getTopAnnotation(aOnVar);
            if (seenTops.contains(top)) {
                this.reportError(type, tree);
                error = true;
            }
            seenTops.add(top);
        }

        return error;
    }
}

class TypeUseLocationValidator {

    TypeUseLocationValidatorImpl impl;
    AnnotatedTypeMirror typeToValidateItsMainModifier;
    BaseTypeValidator validator;

    TypeUseLocationValidator(BaseTypeValidator validator) {
        impl = new TypeUseLocationValidatorImpl(validator);
    }

    void validate(AnnotatedTypeMirror type, Tree tree) {
        typeToValidateItsMainModifier = type;
        // scan is different from visit on not reseting first(reset includes cleaning the visitedNodes map, even though if
        // we use IdentityHashMap, it didn't act like an effective cache.)
        impl.scan(typeToValidateItsMainModifier, tree);
    };

    // Only validates annotation against TypeUseLocatioins defined in TypeUseLocations enum.
    // If in other locations, they are used, it's the type system specific validator's job to
    // raise error. So, it's not TypeUseLocationValidator's job
    class TypeUseLocationValidatorImpl extends AnnotatedTypeScanner<Void, Tree> {

        Set<com.sun.tools.javac.util.Pair<AnnotatedTypeMirror, Tree>> checkedLocations;
        private boolean isCheckingTypeArgument;
        private boolean isCheckingArrayComponent;
        // are we currently defaulting the lower bound of a type variable or wildcard
        private boolean isLowerBound = false;
        // are we currently defaulting the upper bound of a type variable or wildcard
        private boolean isUpperBound = false;
        // the bound type of the current wildcard or type variable being defaulted
        private BoundType boundType = BoundType.UNBOUND;
        private int count = 0;
        private boolean printDebug = true;
        private BaseTypeValidator typeValidator;
        private static final int CACHE_SIZE = 300;
        private final Map<Element, BoundType> elementToBoundType =
                CollectionUtils.createLRUCache(CACHE_SIZE);
        Map<AnnotatedTypeMirror, Void> visitedTypes;

        public TypeUseLocationValidatorImpl(BaseTypeValidator typeValidator) {
            this.typeValidator = typeValidator;
            checkedLocations = new HashSet<>();
            visitedTypes = new HashMap<>();
        }

        private Element getElement(Tree tree) {
            Element elt;
            switch (tree.getKind()) {
                case MEMBER_SELECT:
                    elt = TreeUtils.elementFromUse((MemberSelectTree) tree);
                    break;

                case IDENTIFIER:
                    elt = TreeUtils.elementFromUse((IdentifierTree) tree);
                    break;

                case METHOD_INVOCATION:
                    elt = TreeUtils.elementFromUse((MethodInvocationTree) tree);
                    break;

                    // TODO cases for array access, etc. -- every expression tree
                    // (The above probably means that we should use defaults in the
                    // scope of the declaration of the array.  Is that right?  -MDE)
                    /*case TYPE_PARAMETER:
                    elt = TreeUtils.elementFromDeclaration((TypeParameterTree) tree);*/
                default:
                    // If no associated symbol was found, use the tree's (lexical)
                    // scope.
                    elt = nearestEnclosingExceptLocal(tree);
                    // elt = nearestEnclosing(tree);
            }
            return elt;
        }

        private Element nearestEnclosingExceptLocal(Tree tree) {
            TreePath path = typeValidator.atypeFactory.getPath(tree);
            if (path == null) {
                Element method = typeValidator.atypeFactory.getEnclosingMethod(tree);
                if (method != null) {
                    return method;
                } else {
                    return InternalUtils.symbol(tree);
                }
            }

            Tree prev = null;

            for (Tree t : path) {
                switch (t.getKind()) {
                    case VARIABLE:
                        VariableTree vtree = (VariableTree) t;
                        ExpressionTree vtreeInit = vtree.getInitializer();
                        if (vtreeInit != null && prev == vtreeInit) {
                            Element elt = TreeUtils.elementFromDeclaration((VariableTree) t);
                            DefaultQualifier d = elt.getAnnotation(DefaultQualifier.class);
                            DefaultQualifiers ds = elt.getAnnotation(DefaultQualifiers.class);

                            if (d == null && ds == null) {
                                break;
                            }
                        }
                        if (prev != null && prev.getKind() == Tree.Kind.MODIFIERS) {
                            // Annotations are modifiers. We do not want to apply the local variable default to
                            // annotations. Without this, test fenum/TestSwitch failed, because the default for
                            // an argument became incompatible with the declared type.
                            break;
                        }
                        return TreeUtils.elementFromDeclaration((VariableTree) t);
                    case METHOD:
                        return TreeUtils.elementFromDeclaration((MethodTree) t);
                    case CLASS:
                    case ENUM:
                    case INTERFACE:
                    case ANNOTATION_TYPE:
                        //System.out.println("Hit!");
                        return TreeUtils.elementFromDeclaration((ClassTree) t);
                        //case TYPE_PARAMETER:
                        //return TreeUtils.elementFromUse((TypeParameterTree)t);
                    default: // Do nothing. continue finding parent paths
                }
                prev = t;
            }
            // Seems like dead code because there must be a matching case in the for loop and return immediately
            return null;
        }

        private void checkValidLocation(
                AnnotatedTypeMirror type, Tree tree, TypeUseLocation location) {
            //System.out.println("AnnotatedTypeVariable: " + type);
            for (AnnotationMirror am : type.getAnnotations()) {
                //System.out.println("Other than type variable hits");
                Element elementOfAnnotation = am.getAnnotationType().asElement();
                TargetLocations declLocations =
                        elementOfAnnotation.getAnnotation(TargetLocations.class);
                // Null means no TargetLocations specified => Any use is correct.
                if (declLocations != null) {
                    Set<TypeUseLocation> set = new HashSet<>(Arrays.asList(declLocations.value()));
                    if (set.contains(TypeUseLocation.ALL)) continue;
                    //System.out.println("contain?: " + set.contains(location) + "\n");
                    //System.out.println("location: " + location);
                    //System.out.println("^^^^^^^^^^^^^^^^location is: " + location);
                    if (((location == TypeUseLocation.EXPLICIT_LOWER_BOUND)
                                    || (location == TypeUseLocation.IMPLICIT_LOWER_BOUND))
                            && set.contains(TypeUseLocation.LOWER_BOUND)) {
                        // TypeUseLocation.LOWER_BOUND already covers both explicit and implicit lower bounds, so no need to check containment
                        continue;
                    } else if (((location == TypeUseLocation.EXPLICIT_UPPER_BOUND)
                                    || (location == TypeUseLocation.IMPLICIT_UPPER_BOUND))
                            && set.contains(TypeUseLocation.UPPER_BOUND)) {
                        // TypeUseLocation.UPPER_BOUND already covers both explicit and implicit lower bounds, so no need to check containment
                        continue;
                    } else if (!set.contains(location)) reportLocationError(type, tree, location);
                }
            }
        }

        private void reportLocationError(
                AnnotatedTypeMirror type, Tree tree, TypeUseLocation location) {
            //System.out.println("Error! =>  type: " + type + " tree: " + tree + " location: " + location);
            //System.out.println("Error: " + location.toString().toLowerCase());
            com.sun.tools.javac.util.Pair<AnnotatedTypeMirror, Tree> target =
                    new com.sun.tools.javac.util.Pair<>(type, tree);
            if (checkedLocations.contains(target)) return;
            typeValidator.reportValidityResult(
                    location.toString().toLowerCase() + ".annotation.forbidden", type, tree);
            checkedLocations.add(target);
            typeValidator.isValid = false;
        }

        @Override
        protected Void scan(AnnotatedTypeMirror type, Tree p) {
            // The "type" here is constantly changing while visiting different types, like type arguments,
            // component, upper/lower bound. The "p" parameter is always passed the same from the entry of
            // visitXXX method from the top construct until the last any visitXXX method.
            Element elt = getElement(p);
            ElementKind elementKind = elt.getKind();
            if (printDebug) {
                System.out.println(
                        "\n===>"
                                + count++
                                + ") "
                                + "Visiting "
                                + type
                                + " kind: "
                                + type.getKind());
                System.out.println("elt: " + elt + " resulteltkind: " + elt.getKind());
                System.out.println("tree is: " + p + " usedtreekind: " + p.getKind());
                if (visitedTypes.containsKey(type)) {
                    System.out.println("--- Skipped because visited");
                }
            }
            if (isCheckingTypeArgument) {
                checkValidLocation(type, p, TypeUseLocation.TYPE_ARGUMENT);
            }
            if (isCheckingArrayComponent) {
                checkValidLocation(type, p, TypeUseLocation.ARRAY_COMPONENT);
            }
            if (isCheckingTypeArgument || isCheckingArrayComponent) {
                return super.scan(type, p);
            }
            if (p instanceof TypeParameterTree || p instanceof ClassTree) {
                if (isUpperBound && boundType.isOneOf(BoundType.UPPER, BoundType.UNKNOWN)) {
                    //Explicit upper bound
                    checkValidLocation(type, p, TypeUseLocation.EXPLICIT_UPPER_BOUND);
                } else if (isUpperBound && boundType.isOneOf(BoundType.UNBOUND, BoundType.LOWER)) {
                    // Implicit upper bound => Do nothing
                    // Do nothing
                } else if (isUpperBound) {
                    // Upper bound
                    checkValidLocation(type, p, TypeUseLocation.UPPER_BOUND);
                }

                if (isLowerBound && boundType.isOneOf(BoundType.LOWER)) {
                    // Explicit lower bound
                    checkValidLocation(type, p, TypeUseLocation.EXPLICIT_LOWER_BOUND);
                } else if (isLowerBound
                        && boundType.isOneOf(
                                BoundType.UNBOUND, BoundType.UPPER, BoundType.UNKNOWN)) {
                    // Implicit lower bound
                    // Do nothing
                } else if (isLowerBound) {
                    checkValidLocation(type, p, TypeUseLocation.LOWER_BOUND);
                }
                if (isUpperBound || isLowerBound) {
                    return super.scan(type, p);
                }
            } else if (p instanceof AnnotatedTypeTree) {
                return super.scan(type, p);
            }
            if (type == typeToValidateItsMainModifier) {
                switch (elementKind) {
                    case FIELD:
                        // Actual location IS Field! Need to check TypeUseLocation.FIELD is
                        //inside declared TypeUseLocation of the annotations on this element
                        checkValidLocation(type, p, TypeUseLocation.FIELD);
                        break;
                    case LOCAL_VARIABLE:
                        checkValidLocation(type, p, TypeUseLocation.LOCAL_VARIABLE);
                        break;
                    case RESOURCE_VARIABLE:
                        checkValidLocation(type, p, TypeUseLocation.RESOURCE_VARIABLE);
                        break;
                    case EXCEPTION_PARAMETER:
                        checkValidLocation(type, p, TypeUseLocation.EXCEPTION_PARAMETER);
                        break;
                    case PARAMETER:
                        // TODO method receciver and return type
                        if (elt.getSimpleName().contentEquals("this")) {
                            checkValidLocation(type, p, TypeUseLocation.RECEIVER);
                        } else {
                            checkValidLocation(type, p, TypeUseLocation.PARAMETER);
                        }
                        break;
                    case CONSTRUCTOR:
                    case METHOD:
                        checkValidLocation(type, p, TypeUseLocation.RETURN);
                        break;
                    case CLASS:
                    case INTERFACE:
                    case ANNOTATION_TYPE:
                    case ENUM:
                        // Not covered since type validator doesn't pass in class tree and a type to validate
                        //System.out.println("@@@tree: " + p);
                        // TODO validate class tree also in BaseTypeVisitor
                        //TODO: we get CLASS element kind for both type parameter tree and annotated type tree.
                        // The two tress are correct, and consistent with type. BUT, the Element gotton from tree
                        // has errors. And we use Element to process each location checking, so there are false
                        // warnings. Originally, these four cases are not supported, so didn't encountered this problem
                        // Update: this isClassTree if statement is basically because for upper bounds trees, it will
                        // extract class_name as element, and its kind is type_declaration. The major reason is that:
                        // getElement() methods returns wrong element for like List<String> as Locations. So, we need
                        // to make sure we are using class trees, to ensure that we are really at type declaration position.
                        if (TreeUtils.isClassTree(p)) {
                            checkValidLocation(type, p, TypeUseLocation.TYPE_DECLARATION);
                        }
                        break;
                    default:
                        break;
                }
            }
            return super.scan(type, p);
        }

        @Override
        public void reset() {
            // We override one method not only it might be explicitly called in this subclass, but also may be
            // a method which is called in superclass. Overriding this method will change the behaviour even if
            // it's not explicitly called in overriding subclass.
            super.reset();
            visitedTypes.clear();
            resetStates();
        }

        private void resetStates() {
            isLowerBound = false;
            isUpperBound = false;
            boundType = BoundType.UNBOUND;
            isCheckingTypeArgument = false;
            isCheckingArrayComponent = false;
        }

        @Override
        public Void visitDeclared(AnnotatedDeclaredType type, Tree p) {
            if (visitedTypes.containsKey(type)) {
                return visitedTypes.get(type);
            }
            visitedTypes.put(type, null);
            resetStates(); // Reset to check in clean environment without the correlating effect of fact like isCheckingArrayComponent is true, to avoid reporting error in wrong case
            isCheckingTypeArgument = true;
            scan(type.getTypeArguments(), p);
            isCheckingTypeArgument = false;
            return null;
        }

        @Override
        public Void visitArray(AnnotatedArrayType type, Tree p) {
            resetStates(); // Reset to check in immediate context
            isCheckingArrayComponent = true;
            // Begin to check array component
            scan(type.getComponentType(), p);
            isCheckingArrayComponent = false;
            return null;
        }

        @Override
        public Void visitTypeVariable(AnnotatedTypeVariable type, Tree p) {
            if (visitedTypes.containsKey(type)) {
                return visitedTypes.get(type);
            }
            visitedTypes.put(type, null);
            resetStates();
            visitBounds(type, type.getUpperBound(), type.getLowerBound(), p);
            return null;
        }

        @Override
        public Void visitWildcard(AnnotatedWildcardType type, Tree p) {
            if (visitedTypes.containsKey(type)) {
                return visitedTypes.get(type);
            }
            visitedTypes.put(type, null);
            resetStates();
            visitBounds(type, type.getExtendsBound(), type.getSuperBound(), p);
            return null;
        }

        /**
         * Visit the bounds of a type variable or a wildcard and potentially apply qual to those
         * bounds.  This method will also update the boundType, isLowerBound, and isUpperbound
         * fields.
         */
        protected void visitBounds(
                AnnotatedTypeMirror boundedType,
                AnnotatedTypeMirror upperBound,
                AnnotatedTypeMirror lowerBound,
                Tree p) {
            //System.out.println("upper bound: " + upperBound);
            final boolean prevIsUpperBound = isUpperBound;
            final boolean prevIsLowerBound = isLowerBound;
            final BoundType prevBoundType = boundType;

            boundType = getBoundType(boundedType, typeValidator.atypeFactory);

            try {
                isLowerBound = true;
                isUpperBound = false;

                scanAndReduce(lowerBound, p, null);

                //visitedTypes.put(boundedType, null);

                isLowerBound = false;
                isUpperBound = true;
                scanAndReduce(upperBound, p, null);

                //visitedTypes.put(boundedType, null);

            } finally {
                isUpperBound = prevIsUpperBound;
                isLowerBound = prevIsLowerBound;
                boundType = prevBoundType;
            }
        }
        /**
         * @param type the type whose boundType is returned.
         *             type must be an AnnotatedWildcardType or AnnotatedTypeVariable
         * @return the boundType for type
         */
        private BoundType getBoundType(
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

        /**
         * @return the bound type of the input typeVar
         */
        private BoundType getTypeVarBoundType(
                final AnnotatedTypeVariable typeVar, final AnnotatedTypeFactory typeFactory) {
            return getTypeVarBoundType(
                    (TypeParameterElement) typeVar.getUnderlyingType().asElement(), typeFactory);
        }

        /**
         * @return the boundType (UPPER, UNBOUND, or UNKNOWN) of the declaration of typeParamElem
         */
        // Results are cached in {@link elementToBoundType}.
        private BoundType getTypeVarBoundType(
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
                // when the compilation unit is no longer available.
                boundType = BoundType.UNKNOWN;

            } else {
                if (typeParamDecl.getKind() == Tree.Kind.TYPE_PARAMETER) {
                    final TypeParameterTree tptree = (TypeParameterTree) typeParamDecl;

                    List<? extends Tree> bnds = tptree.getBounds();
                    if (bnds != null && !bnds.isEmpty()) {
                        boundType = BoundType.UPPER;
                    } else {
                        boundType = BoundType.UNBOUND;
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
         * @return the BoundType of annotatedWildcard.  If it is unbounded, use the type parameter to
         * which its an argument
         */
        public BoundType getWildcardBoundType(
                final AnnotatedWildcardType annotatedWildcard,
                final AnnotatedTypeFactory typeFactory) {

            final WildcardType wildcard = (WildcardType) annotatedWildcard.getUnderlyingType();

            final BoundType boundType;
            if (wildcard.isUnbound() && wildcard.bound != null) {
                boundType =
                        getTypeVarBoundType(
                                (TypeParameterElement) wildcard.bound.asElement(), typeFactory);

            } else {
                // note: isSuperBound will be true for unbounded and lowers, but the unbounded case is already handled
                boundType = wildcard.isSuperBound() ? BoundType.LOWER : BoundType.UPPER;
            }

            return boundType;
        }
    }

    enum BoundType {

        /**
         * Indicates an upper bounded type variable or wildcard
         */
        UPPER,

        /**
         * Indicates a lower bounded type variable or wildcard
         */
        LOWER,

        /**
         * Neither bound is specified, BOTH are implicit
         */
        UNBOUND,

        /**
         * For bytecode, or trees for which we no longer have the compilation unit.
         * We treat UNKNOWN bounds as if they are an UPPER bound.
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
}
