package qualifiedlocations;

import testlib.qualifiedlocations.qual.Bottom;

public class ArrayComponent {
    //:: error: (array_component.annotation.forbidden)
    @Bottom Object[] array;
    //:: error: (array_component.annotation.forbidden)
    @Bottom Number[] @Bottom [] twoDimensionArray;
    //:: error: (array_component.annotation.forbidden)
    @Bottom Object[] foo() {
        return null;
    }
}
