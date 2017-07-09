package qualifiedlocations;

import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;
import testlib.qualifiedlocations.qual.Bottom;

@DefaultQualifier(value = Bottom.class, locations = TypeUseLocation.UPPER_BOUND)
//:: error: (implicit_upper_bound.annotation.forbidden)
public class ImplicitUpperBound<T> {}
