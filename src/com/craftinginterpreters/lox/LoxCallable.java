package com.craftinginterpreters.lox;

import java.util.List;


interface LoxCallable {
    
    // number of parameters
    int arity();

    // the Constructor
    // we pass in the interpreter incase the class implementing it needs it, we also give it the list
    // of the evaluated argument values.
    Object call(Interpreter interpreter, List<Object> arguments);
}
