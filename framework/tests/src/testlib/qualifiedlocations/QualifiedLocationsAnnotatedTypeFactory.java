package testlib.qualifiedlocations;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.javacutil.AnnotationUtils;
import testlib.qualifiedlocations.qual.Bottom;
import testlib.qualifiedlocations.qual.Top;

/** Created by mier on 05/07/17. */
public class QualifiedLocationsAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    public AnnotationMirror TOP, BOTTOM;

    public QualifiedLocationsAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);
        TOP = AnnotationUtils.fromClass(elements, Top.class);
        BOTTOM = AnnotationUtils.fromClass(elements, Bottom.class);
        postInit();
    }

    @Override
    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        Set<Class<? extends Annotation>> annotations =
                new HashSet<>(Arrays.asList(Top.class, Bottom.class));
        return Collections.unmodifiableSet(annotations);
    }
}
