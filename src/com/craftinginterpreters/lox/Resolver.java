package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

// since we need to visit every node in the syntax tree, it impleements the visitor abstaction we already have in place
class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;

    // keeps track of the stack of scopes currently in scope. each element in the stack is a map representing a single block scope
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();

    // tracking wheter or not the code we are visiting is inside a function declaration
    private FunctionType currentFunction = FunctionType.NONE;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private enum FunctionType {
        NONE,
        FUNCTION,
        METHOD
    }

    private enum ClassType {
        NONE,
        CLASS
    }

    // its value tells us if we are currenlty inside a class declaration while traversing the syntax tree
    // its default value is NONE
    private ClassType currentClass = ClassType.NONE;

    @Override 
    public Void visitBlockStmt(Stmt.Block stmt) {

        // begins the scope
        beginScope();

        // resolves all statements in the block
        resolve(stmt.statements);

        // end the scope
        endScope();
        return null;
    }  

    @Override 
    public Void visitClassStmt(Stmt.Class stmt) {

        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;

        declare(stmt.name);
        define(stmt.name);
        
        // push a new scope and define "this" as if it were a variable
        beginScope();
        scopes.peek().put("this", true);

        // since we are already parsing method declarations inside of the cclass body, the next thing to do is to resolve them
        // we iterate through methods in the class body and call the resolveFunction method but pass in a different functionType enum value
        for (Stmt.Function method : stmt.methods) {
            FunctionType declaration = FunctionType.METHOD;
            resolveFunction(method, declaration);
        }

        endScope();

        currentClass = enclosingClass;
        return null;
    }

    @Override 
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override 
    public Void visitFunctionStmt(Stmt.Function stmt) {

        // declare the function and define it inside of the surround scope where the function is declared
        // also binding the parameters to the inner function scope
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override 
    public Void visitIfStmt(Stmt.If stmt) {
        // resolve the condition and the then bracnh
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) {
            resolve(stmt.elseBranch);
        }

        return null;
    }

    @Override 
    public Void visitPrintStmt(Stmt.Print stmt) {
        // resolve the subexpression for a print statemnet
        resolve(stmt.expression);
        return null;
    }

    @Override 
    public Void visitReturnStmt(Stmt.Return stmt) {

        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code");
        }

        // if it isnt returning null, then it will resolve the return value subexpression
        if (stmt.value != null) {
            resolve(stmt.value);
        }

        return null;
    }

    @Override 
    public Void visitVarStmt(Stmt.Var stmt) {
        // declares the variable and if the initiliazer isnt null it will resolve the initiliazer then defines the variable
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override 
    public Void visitWhileStmt(Stmt.While stmt) {

        // resolves both the condition and the body
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    @Override public Void visitBreakStmt(Stmt.Break stmt) {
        return null;
    }

    public Void visitAssignExpr(Expr.Assign expr) {

        // resolves the value then runs the resolveLocal function which checks if the variable is initialized or not
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override 
    public Void visitBinaryExpr(Expr.Binary expr) {

        // resolves the left and right operands of a binary expression
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override 
    public Void visitCallExpr(Expr.Call expr) {
        // resolves the callee
        resolve(expr.callee);

        // resolves every argument for the function
        for (Expr argument : expr.arguments) {
            resolve(argument);
        }

        return null;
    }
    
    // resolves only the expression to the left of the dot
    @Override 
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override 
    public Void visitGroupingExpr(Expr.Grouping expr) {
        //resolves the expression inside of the parenthesis
        resolve(expr.expression);
        return null;
    }

    // literals dont have any variables or any subexpressions so there is no work to be done
    @Override 
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override 
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    // reolve the object whoes property is being set and the value its being set to, since the property itself is dynamically evaluated
    @Override 
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    // using this as the name for the "variable", we resolve it like any other local variable
    @Override 
    public Void visitThisExpr(Expr.This expr) {

        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Can't use 'this' outside of a class");
            return null;
        }
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override 
    public Void visitUnaryExpr(Expr.Unary expr) {
        // resolve the one operand with the unary expression
        resolve(expr.right);
        return null;
    } 
    
    @Override 
    public Void visitVariableExpr(Expr.Variable expr) {

        // we cehck to see if the variable is being accessed inside its own initliazer, this is where the valoes of the scope map come into play
        // if the variable exists in the current scope but its value is false, that means we have declared it but not yet defined it, then we report that error
        if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
            Lox.error(expr.name, "Can't read local variable in its own initliazer");
        }

        // after the check we revolve the variable with the resolveLocal helper
        resolveLocal(expr, expr.name);
        return null;
    }

    // Three overloaded resolve functions 

    // begins a new scope, traverses the statements inside the block, then discards the scope
    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    // walks a list of statements and resolves each one
    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    // for resolving expressions
    private void resolve(Expr expr) {
        expr.accept(this);
    }

    private void resolveFunction(Stmt.Function function, FunctionType type) {

        // we stash the previous value of the field in a local variable first since Lox has local functions
        // so functions can be nested arbitrarily deeply, we need to track not just that were in a function but how deep we are

        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        // begins the scope for the function body
        beginScope();

        // for each parameter the function has it declares it and defines it inside of the scope of the function body
        for (Token param : function.params) {
            declare(param);
            define(param);
        }

        // resolves the function body
        resolve(function.body);
        endScope();
        currentFunction = enclosingFunction;
    }

    // a new block scope being created
    private void beginScope() {
        scopes.push(new HashMap<String, Boolean>());
    }

    // removes the item at the top of the stack
    private void endScope() {
        scopes.pop();
    }

    private void declare(Token name) {
        if (scopes.isEmpty()) {
            return;
        }

        // mark as not ready yet by binding it to false in the key-value pair Map
        Map<String, Boolean> scope = scopes.peek();
        scope.put(name.lexeme,false);
    }

    private void define(Token name) {
       if (scopes.isEmpty()) {
            return;
       }

       // mark it as ready by setting the boolean value to true
       scopes.peek().put(name.lexeme, true);
    }

    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }
}