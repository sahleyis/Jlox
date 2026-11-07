package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    
    // store it as a field directly in interpereter so that the variable stays in memory as long as the interpreter is still running
    final Environment globals = new Environment();

    // this changes as we enter and leave local scope, it tracks the current environment but the new globals
    // holds a fixed reference to the outermost global environment
    private Environment environment = globals;

    // a map that associates each syntax tree node with its resolved data
    private final Map<Expr, Integer> locals = new HashMap<>();

    Interpreter() {
        globals.define("clock", new LoxCallable(){
            @Override 
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override 
            public String toString() {
                return "<native fn>";
            }
        });
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement: statements) {
                execute(statement);
            }
        } catch (RuntimeError error){
            Lox.runtimeError(error);
        }
    }

    private String stringify(Object object) {
        if (object == null) {
            return "nil";
        }

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() -2);
            }

            return text;
        }

        return object.toString();
    }
    @Override 
    public Object visitLiteralExpr(Expr.Literal expr ) {
        return expr.value;
    }

    @Override 
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        // we check if we can short-circut as if its true then the OR operator will evaluate to true
        // if it cant be evaluated to true then thats when we evaluate the right operator
        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) {
                return left;
            } else {
                if (!isTruthy(left)) {
                    return left;
                }
            }
        }

        return evaluate(expr.right);
    }

    @Override 
    public Object visitUnaryExpr(Expr.Unary expr ) {
        Object right = evaluate(expr.right);

        // if the symbol is a negative sign it will return the negative value of the expression to the right
        switch(expr.operator.type) {
            case MINUS:
                return -(double)right;
            case BANG:
                return !isTruthy(right);
        }

        return null;
    }

    @Override 
    public Object visitVariableExpr(Expr.Variable expr) { 
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr) {

        // look up the resolved distance in the map, remember only local variables are resolved
        Integer distance = locals.get(expr);

        // so if we dont find a distance in the map it must be global, in that case we look it up, dynamically, directly in the global 
        // environment
        if (distance != null) {
            return environment.getAt(distance, name.lexeme);
        } else {
            return globals.get(name);
        }
    }

    private Boolean isTruthy(Object object) {
        if (object == null) {
            return false;
        }

        if (object instanceof Boolean ) {
            return (boolean)object;
        }

        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null)  {
            return true;
        }

        if (a == null) {
            return false;
        }

        return a.equals(b);
    }

    
    // the Grouping Expressiono( Parenthesis ) returns a function that evaluates the expression inside the parenthesis
    @Override 
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    // evalutes a given expression
    private Object evaluate (Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        
        try {
            this.environment = environment;

            for (Stmt statement: statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override 
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    // statements produce no values, so the return type of their visit methods is void
    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt){
        evaluate(stmt.expression);
        return null;
    }

    @Override 
    public Void visitFunctionStmt(Stmt.Function stmt) {
        // we pass in the statement and the environment that is active   when the function is declared not called
        LoxFunction function = new LoxFunction(stmt, environment);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override 
    public Void visitIfStmt(Stmt.If stmt){

        // first evaluates the condition and if its true it will execute the thenBranch
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);

            // then if there is an else branch it will execute that too
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }

        // returns null if the condition evaluates to false
        return null;
    } 

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override 
    public Void visitReturnStmt(Stmt.Return stmt) {

        // if we have a reutrn value we evaluate it other wise we return nil, we take that value
        // then we wrap in in a custom exception class and return it
        Object value = null;
        
        if (stmt.value != null) {
            value = evaluate(stmt.value);
        }

        throw new Return(value);
    }

    @Override 
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = Environment.uninitialised;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }  

    @Override 
    public Void visitWhileStmt(Stmt.While stmt) {
        
        // we use the break exeption to eject us from the loop
        try {
            while (isTruthy(evaluate(stmt.condition))) {
                execute(stmt.body);
            } 
        } catch (BreakException exception) {
            // do nothing and it ejeccts us from the while loop
        }

        return null;
    }

    // throws the break exception in order to leave the loop
    @Override 
    public Void visitBreakStmt(Stmt.Break stmt) {
        throw new BreakException();
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);

        // loop up the variables scope distance, if not found then we assume its global and handle it the same way as before
        // otherwise we call the new method
        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }
        return value;
    }

    
    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch(expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right; 
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:

                if (left instanceof String && right instanceof Double) {
                    return (String)left + (double)right;
                } else if (left instanceof Double && right instanceof String) {
                    return (double)left + (String)right;
                }
 
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }

                if (left instanceof String && right instanceof String) {
                    return (String)left + (String)right;
                }

                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);

                if ((double)right == 0) {
                    throw new RuntimeError(expr.operator, "Cannot divide 0 by 0");
                }
                
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
        }

        return null;
    }

    @Override 
    public Object visitCallExpr(Expr.Call expr) {

        // first we evaluate the expression for the callee, normally its just an identifier
        Object callee = evaluate(expr.callee);

        // create a list full of objects by instantiating a new instance of ArrayList then looping over it
        // so that for every arguemnt in the list arguments we evaluate that specific argument
        List<Object> arguments = new ArrayList<>();
        for (Expr argument: expr.arguments) {
            arguments.add(evaluate(argument));
        }

        // once we have the callee and arguments the next thing is to perform the call, this is done by casting
        // the callee to a LoxCallable type nad invoking the call() method on it
        LoxCallable function = (LoxCallable)callee;

        // make sures the number of arguments matches the arity ( number of paramters )
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
        }
        return function.call(this, arguments);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) {
            return;
        }

        throw new RuntimeError(operator, "Operand must be a number");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) {
            return;
        }

        throw new RuntimeError(operator, "Operands must be numbers");
    }
}