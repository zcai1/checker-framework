package org.checkerframework.framework.util;

import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;

/**
 * Base class for viewpoint adaptation on framework side. Subclass should extend this class and
 * provide implementations for {@link ViewpointAdaptor#getModifier(AnnotatedTypeMirror,
 * AnnotatedTypeFactory)} and {@link ViewpointAdaptor#combineModifierWithModifier(Object, Object,
 * AnnotatedTypeFactory)}to perform type system specific viewpoint adaptation. Depending on type
 * system, one might also wants to override {@link
 * ViewpointAdaptor#shouldNotBeAdapted(AnnotatedTypeMirror, javax.lang.model.element.Element)} to
 * specify which types or elements should not be adapted.
 *
 * @author tamier
 */
public abstract class FrameworkViewpointAdaptor extends ViewpointAdaptor<AnnotationMirror> {

    /** Framework side modifier is {@link AnnotationMirror}, so directly return it */
    @Override
    protected final AnnotationMirror getAnnotationFromModifier(AnnotationMirror t) {
        return t;
    }
}
