package org.checkerframework.checker.gut.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.lang.model.type.TypeKind;
import org.checkerframework.framework.qual.DefaultFor;
import org.checkerframework.framework.qual.ImplicitFor;
import org.checkerframework.framework.qual.LiteralKind;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TargetLocations;
import org.checkerframework.framework.qual.TypeUseLocation;

/**
 * The bottom of the type hierarchy is only used internally.
 *
 * @author wmdietl
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
@TargetLocations({TypeUseLocation.EXPLICIT_LOWER_BOUND, TypeUseLocation.EXPLICIT_UPPER_BOUND})
@SubtypeOf({Self.class, Rep.class})
@DefaultFor({TypeUseLocation.IMPLICIT_LOWER_BOUND})
@ImplicitFor(
    literals = {LiteralKind.NULL, LiteralKind.STRING},
    types = {
        TypeKind.INT,
        TypeKind.BYTE,
        TypeKind.SHORT,
        TypeKind.BOOLEAN,
        TypeKind.LONG,
        TypeKind.CHAR,
        TypeKind.FLOAT,
        TypeKind.DOUBLE
    }
)
public @interface Bottom {}
