package qualifiedlocations;

import testlib.qualifiedlocations.qual.Bottom;

public class Throws {

    //:: error: (throws.annotation.forbidden)
    void foo() throws @Bottom Exception {}
}
