package qualifiedlocations;

import java.util.Set;
import testlib.qualifiedlocations.qual.Bottom;

//:: error: (explicit_upper_bound.annotation.forbidden)
public class ExplicitUpperBound<T extends @Bottom Object> {
    //:: error: (explicit_upper_bound.annotation.forbidden)
    Set<? extends @Bottom Object> foo() {
        return null;
    }
}
