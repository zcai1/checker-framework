package org.checkerframework.framework.util;

import javax.lang.model.element.AnnotationMirror;

public abstract class FrameworkViewpointAdapter extends ViewpointAdapter<AnnotationMirror> {

    @Override
    protected AnnotationMirror extractAnnotationMirror(AnnotationMirror annotationMirror) {
        return annotationMirror;
    }
}
