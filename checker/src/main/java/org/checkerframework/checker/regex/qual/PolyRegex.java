package org.checkerframework.checker.regex.qual;

import org.checkerframework.framework.qual.PolymorphicQualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A polymorphic qualifier for the Regex type system.
 *
 * <p>Any method written using {@code @PolyRegex} conceptually has two versions: one in which every
 * instance of {@code @PolyRegex String} has been replaced by {@code @Regex String}, and one in
 * which every instance of {@code @PolyRegex String} has been replaced by {@code String}.
 *
 * @checker_framework.manual #regex-checker Regex Checker
 * @checker_framework.manual #qualifier-polymorphism Qualifier polymorphism
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@PolymorphicQualifier(UnknownRegex.class)
public @interface PolyRegex {}
