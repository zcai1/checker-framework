package qualifiedlocations;

import testlib.qualifiedlocations.qual.Bottom;

public class Field {
    //:: error: (field.annotation.forbidden)
    @Bottom Object o;
}
