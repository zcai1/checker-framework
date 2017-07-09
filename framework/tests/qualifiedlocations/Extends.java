package qualifiedlocations;

import testlib.qualifiedlocations.qual.Bottom;

//:: error: (extends.annotation.forbidden) :: error: (type_declaration.annotation.forbidden) :: error: (type.invalid)
public class Extends extends @Bottom Object {}
