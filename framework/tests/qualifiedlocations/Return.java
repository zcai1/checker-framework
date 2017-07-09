package qualifiedlocations;

import testlib.qualifiedlocations.qual.Bottom;

public class Return {

    //:: error: (return.annotation.forbidden)
    @Bottom Object foo() {
        return null;
    }
}
