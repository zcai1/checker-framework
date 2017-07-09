package qualifiedlocations;

import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;
import testlib.qualifiedlocations.qual.Top;

@DefaultQualifier(value = Top.class, locations = TypeUseLocation.UPPER_BOUND)
//:: error: (implicit_lower_bound.annotation.forbidden)
public class ImplicitLowerBound<T> {}
