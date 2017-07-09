package qualifiedlocations;

import testlib.qualifiedlocations.qual.Bottom;

public class Cast {
    {
        //:: error: (cast.annotation.forbidden)
        ((@Bottom Object) new Object()).toString();
    }
}
