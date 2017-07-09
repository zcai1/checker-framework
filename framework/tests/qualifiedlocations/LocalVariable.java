package qualifiedlocations;

import testlib.qualifiedlocations.qual.Bottom;

public class LocalVariable {

    void foo() {
        //:: error: (local_variable.annotation.forbidden)
        @Bottom Object lo;
    }
}
