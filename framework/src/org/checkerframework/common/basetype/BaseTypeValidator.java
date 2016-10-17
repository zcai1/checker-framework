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
import org.checkerframework.framework.util.BoundType;
import org.checkerframework.framework.util.BoundTypeUtil;
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
    protected final TargetLocationValidator locationValidator;
    // TODO: clean up coupling between components
    public BaseTypeValidator(
            BaseTypeChecker checker,
            BaseTypeVisitor<?> visitor,
            AnnotatedTypeFactory atypeFactory) {
        this.checker = checker;
        this.visitor = visitor;
        this.atypeFactory = atypeFactory;
        locationValidator = new TargetLocationValidator(this);
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

class TargetLocationValidator extends AnnotatedTypeScanner<Void, Tree> {

    BaseTypeValidator validator;
    private boolean printDebug = false;
    int countEntry = 0;
    private int countScan = 0;

    TargetLocationValidator(BaseTypeValidator validator) {
        this.validator = validator;
    }

    void validate(AnnotatedTypeMirror type, Tree tree) {
        if (printDebug) {
            System.out.println(
                    "\n\n##################### ["
                            + countEntry++
                            + "] Entry: type: "
                            + type
                            + " kind: "
                            + type.getKind()
                            + " tree: "
                            + tree
                            + " treeKinid: "
                            + tree.getKind());
        }
        // scan is different from visit on not reseting first(reset includes cleaning the visitedNodes map, even though if
        // we use IdentityHashMap, it didn't act like an effective cache.)
        validateMainModifier(type, tree);
        scan(type, tree);
    };

    private void validateMainModifier(AnnotatedTypeMirror type, Tree tree) {
        Element elt = getElement(tree);
        ElementKind elementKind = elt.getKind();
        switch (elementKind) {
            case FIELD:
                // Actual location IS Field! Need to check TypeUseLocation.FIELD is
                //inside declared TypeUseLocation of the annotations on this element
                checkValidLocation(type, tree, TypeUseLocation.FIELD);
                break;
            case LOCAL_VARIABLE:
                checkValidLocation(type, tree, TypeUseLocation.LOCAL_VARIABLE);
                break;
            case RESOURCE_VARIABLE:
                checkValidLocation(type, tree, TypeUseLocation.RESOURCE_VARIABLE);
                break;
            case EXCEPTION_PARAMETER:
                checkValidLocation(type, tree, TypeUseLocation.EXCEPTION_PARAMETER);
                break;
            case PARAMETER:
                // TODO method receciver and return type
                if (elt.getSimpleName().contentEquals("this")) {
                    checkValidLocation(type, tree, TypeUseLocation.RECEIVER);
                } else {
                    checkValidLocation(type, tree, TypeUseLocation.PARAMETER);
                }
                break;
            case CONSTRUCTOR:
            case METHOD:
                // Upper bounf of type parameter declared in generic method after getting
                // nearest enclosing element is also METHOD element. BUT its tree is no
                // method tree. So, we add additional restriction: only when the tree is also
                // method tree, they use is seen to be on method return.
                if (tree.getKind() == Tree.Kind.METHOD) {
                    checkValidLocation(type, tree, TypeUseLocation.RETURN);
                }
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
                if (TreeUtils.isClassTree(tree)) {
                    checkValidLocation(type, tree, TypeUseLocation.TYPE_DECLARATION);
                }
                break;
            default:
                break;
        }
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

            default:
                // If no associated symbol was found, use the tree's (lexical)
                // scope.
                elt = nearestEnclosingExceptLocal(tree);
        }
        return elt;
    }

    private Element nearestEnclosingExceptLocal(Tree tree) {
        TreePath path = validator.atypeFactory.getPath(tree);
        if (path == null) {
            Element method = validator.atypeFactory.getEnclosingMethod(tree);
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
                    // TODO: evaluate comment-out of these lines
                    /*if (vtreeInit != null && prev == vtreeInit) {
                        Element elt = TreeUtils.elementFromDeclaration((VariableTree) t);
                        DefaultQualifier d = elt.getAnnotation(DefaultQualifier.class);
                        DefaultQualifiers ds = elt.getAnnotation(DefaultQualifiers.class);

                        if (d == null && ds == null) {
                            break;
                        }
                    }*/
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

    private void checkValidLocation(AnnotatedTypeMirror type, Tree tree, TypeUseLocation location) {
        for (AnnotationMirror am : type.getAnnotations()) {
            Element elementOfAnnotation = am.getAnnotationType().asElement();
            TargetLocations declLocations =
                    elementOfAnnotation.getAnnotation(TargetLocations.class);
            // Null means no TargetLocations specified => Any use is correct.
            if (declLocations != null) {
                Set<TypeUseLocation> set = new HashSet<>(Arrays.asList(declLocations.value()));
                if (set.contains(TypeUseLocation.ALL)) continue;
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
        if (printDebug) {
            System.out.println(
                    "-----!!!----- Error =>  type: "
                            + type
                            + " \ntree: "
                            + tree
                            + " location: "
                            + location.toString().toLowerCase());
        }
        // TODO check the effect of removing cache for type and tree while reporting error
        validator.reportValidityResult(
                location.toString().toLowerCase() + ".annotation.forbidden", type, tree);
        validator.isValid = false;
    }

    @Override
    protected Void scan(AnnotatedTypeMirror type, Tree p) {
        // The "type" here is constantly changing while visiting different types, like type arguments,
        // component, upper/lower bound. The "p" parameter is always passed the same from the entry of
        // visitXXX method from the top construct until the last any visitXXX method.
        if (printDebug) {
            System.out.println(
                    "\n===>"
                            + countScan++
                            + ") "
                            + "Visiting "
                            + type
                            + " kind: "
                            + type.getKind());
            Element elt = getElement(p);
            System.out.println("elt: " + elt + " resulteltkind: " + elt.getKind());
            System.out.println("tree is: " + p + " usedtreekind: " + p.getKind());
            if (visitedNodes.containsKey(type)) {
                System.out.println("--- Skipped because visited");
            }
        }

        return super.scan(type, p);
    }

    @Override
    public Void visitDeclared(AnnotatedDeclaredType type, Tree p) {
        if (visitedNodes.containsKey(type)) {
            return visitedNodes.get(type);
        }
        visitedNodes.put(type, null);
        // Not check if tree p is a "wide" class declaration tree
        if (!TreeUtils.isClassTree(p)) {
            for (AnnotatedTypeMirror tArg : type.getTypeArguments()) {
                checkValidLocation(tArg, p, TypeUseLocation.TYPE_ARGUMENT);
            }
            scan(type.getTypeArguments(), p);
        }
        return null;
    }

    @Override
    public Void visitArray(AnnotatedArrayType type, Tree p) {
        // Begin to check array component
        checkValidLocation(type.getComponentType(), p, TypeUseLocation.ARRAY_COMPONENT);
        scan(type.getComponentType(), p);
        return null;
    }

    @Override
    public Void visitTypeVariable(AnnotatedTypeVariable type, Tree p) {
        if (visitedNodes.containsKey(type)) {
            return visitedNodes.get(type);
        }
        visitedNodes.put(type, null);
        if (type.isDeclaration()) {
            visitBounds(type, type.getUpperBound(), type.getLowerBound(), p);
        }
        return null;
    }

    @Override
    public Void visitWildcard(AnnotatedWildcardType type, Tree p) {
        if (visitedNodes.containsKey(type)) {
            return visitedNodes.get(type);
        }
        visitedNodes.put(type, null);
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

        BoundType boundType = BoundTypeUtil.getBoundType(boundedType, validator.atypeFactory);
        // TODO Is this correct to use this as condition to check if it's type parameter declaration
        // Checking lower bound
        if (p.getKind() == Tree.Kind.TYPE_PARAMETER) {
            if (boundType.isOneOf(BoundType.LOWER)) {
                // Explicit lower bound
                checkValidLocation(lowerBound, p, TypeUseLocation.EXPLICIT_LOWER_BOUND);
            } else if (boundType.isOneOf(BoundType.UNBOUND, BoundType.UPPER, BoundType.UNKNOWN)) {
                // Implicit lower bound
                // Do nothing
            } else {
                checkValidLocation(lowerBound, p, TypeUseLocation.LOWER_BOUND);
            }
        }
        // We only need to validate explicit main annotation on lower/upper bounds. So no need to
        // visit recursively. They will be scan afterwards in different trees
        //scanAndReduce(lowerBound, p, null);

        // Checking upper bound
        if (p.getKind() == Tree.Kind.TYPE_PARAMETER) {
            if (boundType.isOneOf(BoundType.UPPER, BoundType.UNKNOWN)) {
                //Explicit upper bound
                checkValidLocation(upperBound, p, TypeUseLocation.EXPLICIT_UPPER_BOUND);
            } else if (boundType.isOneOf(BoundType.UNBOUND, BoundType.LOWER)) {
                // Implicit upper bound => Do nothing
                // Do nothing
            } else {
                // Upper bound
                checkValidLocation(upperBound, p, TypeUseLocation.UPPER_BOUND);
            }
        }
        // We only need to validate explicit main annotation on lower/upper bounds. So no need to
        // visit recursively. They will be scan afterwards in different trees from which the deeper
        // types can be validated
        //scanAndReduce(upperBound, p, null);
    }
}
