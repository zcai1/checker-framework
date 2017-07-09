package qualifiedlocations;

import java.util.ArrayList;
import java.util.List;
import testlib.qualifiedlocations.qual.Bottom;

//:: error: (array_component.annotation.forbidden) :: error: (type_argument.annotation.forbidden) :: error: (explicit_upper_bound.annotation.forbidden)
public class Locations<T extends @Bottom List<@Bottom Number @Bottom []>>
        //:: error: (type_argument.annotation.forbidden) :: error: (extends.annotation.forbidden) :: error: (implements.annotation.forbidden)
        extends @Bottom ArrayList<@Bottom Object> implements @Bottom Iterable<@Bottom Object> {
    //:: error: (field.annotation.forbidden)
    @Bottom T t;
    //:: error: (field.annotation.forbidden) :: error: (type_argument.annotation.forbidden)
    @Bottom List<@Bottom ArrayList<@Bottom String>> l;
    //:: error: (type_argument.annotation.forbidden)
    List<@Bottom String @Bottom [] @Bottom []>
            f; // It's strange that array component doesn't show error

    //:: error: (throws.annotation.forbidden)
    void foo() throws @Bottom Exception {
        //:: error: (local_variable.annotation.forbidden) :: error: (type_argument.annotation.forbidden) :: error: (new.annotation.forbidden)
        @Bottom Object l = new @Bottom ArrayList<@Bottom Object>();
        //:: error: (instanceof.annotation.forbidden) :: error: (cast.annotation.forbidden)
        boolean b = (@Bottom Object) l instanceof @Bottom List @Bottom [];
    }

    //:: error: (parameter.annotation.forbidden)
    void bar(@Bottom Object p) {
        try {
            foo();
            //:: error: (exception_parameter.annotation.forbidden)
        } catch (@Bottom Exception e) {
        }
    }

    //:: error: (return.annotation.forbidden) :: error: (explicit_upper_bound.annotation.forbidden) :: error: (type_argument.annotation.forbidden) :: error: (receiver.annotation.forbidden)
    <S extends @Bottom List<@Bottom Object>> @Bottom Object hey(Locations<@Bottom T> this, S s) {
        return null;
    }
}
