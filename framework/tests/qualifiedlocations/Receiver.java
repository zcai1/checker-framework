package qualifiedlocations;

import testlib.qualifiedlocations.qual.Bottom;

public class Receiver {

    //:: error: (receiver.annotation.forbidden)
    void foo(@Bottom Receiver this) {}
}
