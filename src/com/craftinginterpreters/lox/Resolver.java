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

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    @Override 
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }  

    @Override 
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    // Three overloaded resolve functions 

    // begins a new scope, traverses the statements inside the block, then discards the scope
    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statements);
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

    // a new block scope being created
    private void beginScope() {
        scopes.push(new HashMap<String, Boolean>());
    }

    // removes the item at the top of the stack
    private void endScope() {
        scopes.pop();
    }
}