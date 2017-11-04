package org.checkerframework.framework.util;

import javax.lang.model.element.AnnotationMirror;

/**
 * Base class for viewpoint adaptation on framework side.
 *
 * @author tamier
 */
public abstract class FrameworkViewpointAdapter extends ViewpointAdapter<AnnotationMirror> {

    /** Framework side modifier is {@link AnnotationMirror}, so directly return it */
    @Override
    protected final AnnotationMirror getAnnotationFromModifier(AnnotationMirror t) {
        return t;
    }
}
