package qualifiedlocations;

import java.util.List;
import testlib.qualifiedlocations.qual.Bottom;

public class TypeArgument {
    //:: error: (type_argument.annotation.forbidden)
    List<@Bottom Integer> list;
}
