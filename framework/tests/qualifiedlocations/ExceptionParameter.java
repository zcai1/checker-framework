package qualifiedlocations;

import testlib.qualifiedlocations.qual.Bottom;

public class ExceptionParameter {

    void foo() {
        try {
            //:: error: (exception_parameter.annotation.forbidden)
        } catch (@Bottom Exception e) {

        }
    }
}
