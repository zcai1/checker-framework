package qualifiedlocations;

import testlib.qualifiedlocations.qual.Bottom;
import testlib.qualifiedlocations.qual.Top;

public class New {
    {
        //:: error: (new.annotation.forbidden)
        new @Bottom Object();
        //:: error: (new.annotation.forbidden)
        int a = new @Top String @Bottom [] {"string"}.length;
    }
}
