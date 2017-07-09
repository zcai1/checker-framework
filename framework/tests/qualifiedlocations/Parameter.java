package qualifiedlocations;

import testlib.qualifiedlocations.qual.Bottom;

public class Parameter {

    //:: error: (parameter.annotation.forbidden)
    void foo(@Bottom Object p) {}
}
