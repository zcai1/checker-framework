package qualifiedlocations;

import java.util.Set;
import testlib.qualifiedlocations.qual.Bottom;

public class ExplicitLowerBound {

    //:: error: (explicit_lower_bound.annotation.forbidden)
    Set<? super @Bottom Object> foo() {
        return null;
    }
}
