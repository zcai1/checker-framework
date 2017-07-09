package org.checkerframework.framework.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
/**
 * Applied to the declaration of a type qualifier. It specifies the qualifier is qualified to be
 * used on the specified locations. It will be enforced towards all kinds of annotation sources -
 * explicitly written, implicit, defaulted etc.
 */
public @interface QualifiedLocations {
    TypeUseLocation[] value();
}
