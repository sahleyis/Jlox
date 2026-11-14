package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;

class LoxClass implements LoxCallable{
    final String name;
    // wrapping all the methods into a map keyed by the method naes, stored in LoxClass
    private final Map<String, LoxFunction> methods;

    // construction
    LoxClass(String name, Map<String, LoxFunction> methods) {
        this.name = name;
        this.methods = methods;
    }

    // a map lookup that finds a specific method and returns it, if not it returns null
    LoxFunction findMethod(String name) {
        if (methods.containsKey(name)) {
            return methods.get(name);
        }

        return null;
    }

    // returns the name of a class
    @Override 
    public String toString() {
        return name;
    }

    // returns an instance of a class when called
    @Override 
    public Object call(Interpreter interpreter, List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);
        return instance;
    }

    @Override 
    public int arity() {
        return 0;
    }
}