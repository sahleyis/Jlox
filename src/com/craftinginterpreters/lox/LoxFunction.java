package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    private final Stmt.Function declaration;
    private final Environment closure; 

    // Constructor
    LoxFunction(Stmt.Function declarationn, Environment closure) {
        this.closure = closure;
        this.declaration = declarationn;
    }

    @Override 
    public int arity() {
        return declaration.params.size();
    }

    @Override 
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">"; 
    }
    
    @Override 
    public Object call(Interpreter interpreter, List<Object> arguments) {
        // creates a new environment for each function call and uses the enclosing scope as part of its scope
        Environment environment = new Environment(closure);

        // maps each parameter to the argument provided for the function
        for (int i = 0; i < declaration.params.size(); i++) {
            environment.define(declaration.params.get(i).lexeme, arguments.get(i));
        }
        
        // once the body of the function has finished executing it discards that function local environment and restors
        // the previous one that was active back at the callsite
        // wrap the executeBlock in a try catch block so when it catches a return exception, it pulls out the value and makes
        // that the return value from call(), if it never reaches this exception it implicitly returns null
        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            return returnValue.value;
        }
        

        // returns nil to the caller ( no return values yet);
        return null;
    }

}