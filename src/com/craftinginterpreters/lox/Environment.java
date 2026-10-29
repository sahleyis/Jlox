package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;;

public class Environment {
    public static final Object uninitialised = new Object();
    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    // creating 2 constructors depending if its a globbal or local scope


    // this is for the global scopes environment
    Environment() {
        enclosing = null;
    }

    // this is when it creates a new local scope nested inside of a scope
    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }



    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            Object value = values.get(name.lexeme);

            if ( value == uninitialised ) {
                throw new RuntimeError(name, "Must initialise variable '" + name.lexeme + "' before it can be used." );
            }

            return value;
        }  

        // if the variable is not found in the enclosing environment it looks in the we try the enclosing one
        // if we dont find the variable definition in both the innermost scope and the enclosing then we give up and report an error
        if (enclosing != null) {
            return enclosing.get(name);
        }


        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "' .");
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        // same thing but it looks through recursively, iteratively is faster but recursively is prettier
        
        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }


    void define(String name, Object value) {
        values.put(name, value);
    }
}
