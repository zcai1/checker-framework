package testlib.qualifiedlocations.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.framework.qual.ImplicitFor;
import org.checkerframework.framework.qual.LiteralKind;
import org.checkerframework.framework.qual.QualifiedLocations;
import org.checkerframework.framework.qual.SubtypeOf;

/** Created by mier on 05/07/17. */
@SubtypeOf({Top.class})
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@QualifiedLocations({})
@ImplicitFor(literals = {LiteralKind.NULL})
public @interface Bottom {}
