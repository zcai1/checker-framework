package testlib.qualifiedlocations;

import com.sun.source.tree.TypeCastTree;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;

/** Created by mier on 05/07/17. */
public class QualifiedLocationsVisitor
        extends BaseTypeVisitor<QualifiedLocationsAnnotatedTypeFactory> {
    public QualifiedLocationsVisitor(BaseTypeChecker checker) {
        super(checker);
    }

    @Override
    protected void checkTypecastSafety(TypeCastTree node, Void p) {
        return;
    }

    @Override
    protected Set<? extends AnnotationMirror> getExceptionParameterLowerBoundAnnotations() {
        return atypeFactory.getQualifierHierarchy().getBottomAnnotations();
    }
}
