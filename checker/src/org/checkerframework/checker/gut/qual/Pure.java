package org.checkerframework.checker.gut.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a side-effect free (pure) method.
 *
 * @see <a
 *     href="http://www.eecs.ucf.edu/~leavens/JML/jmlrefman/jmlrefman_7.html#SEC59">Definition</a>
 * @author wmdietl
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Pure {}
