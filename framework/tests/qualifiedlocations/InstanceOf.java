package qualifiedlocations;

import testlib.qualifiedlocations.qual.Bottom;

public class InstanceOf {

    void foo(Object o) {
        //:: error: (instanceof.annotation.forbidden)
        if (o instanceof @Bottom Object) {}
    }
}
