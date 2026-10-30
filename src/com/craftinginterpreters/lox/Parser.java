package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

// Recursive descent parser
class Parser {
    // tracking how deep we are into a loop
    private int loopDepth = 0;
    private static class ParseError extends RuntimeException {}
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens){
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while(!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    // lowest priority in the context free grammar
    private Expr expression() {
        return assignment();
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) {
                return varDeclaration();
            }

            return statement();
        } catch(ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt statement() {

        if (match(FOR)) {
            return forStatement();
        }
        if (match(IF)) {
            return ifStatement();
        }


        if (match(PRINT)) {
            return printStatement();
        }

        if (match(WHILE)) {
            return whileStatement();
        } 

        if (match(BREAK)) {
            return breakStatement();
        }

        if (match(LEFT_BRACE)) {
            return new Stmt.Block(block());
        }



        return expressionStatement();
    };

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        // this is for the first part of the for loop which is the initializer
        // if its a semicolon the the initializer has been ommiteed, otherwise we check for the var keyword
        // to see if its a variable declaration, and if its neither then it must be an expression so we convert it to an expression statement
        // so that the initializer is always some type of Stmt
        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        // it checks if there isnt a semicolon and if there isnt then theres a condition and the condition
        // is an expression
        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");
        
        // same thing as the condition except its terminated by the closign parenthesis instead of the next semicolon
        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')'' after for clauses");

        Stmt body;

        // when it enters a loop it increase the loop depth, parses the statements inside it and stores it into the body variable and then when it leaves it reduces the loop depth
        try {
            loopDepth++;
            body = statement();
        } finally {
            loopDepth--;
        }

        // if there is an increment we execute it after each iteration, we do that by replacing the body
        // with a little block that contains the original body followed by an expression statement that evaluates the increment
        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }
        
        // if there is no condition then the condition becomes a true value and we create a while loop
        // with the condition to be true and the body as what executes, it becomes an infinite loop
        if (condition == null) {
            condition = new Expr.Literal(true);
        }
        body = new Stmt.While(condition, body);
        // if theres an initializer it runs once efore the entire loop, we do that by replacing the whole statement
        // with a block that runs the initliazer then executes the loop
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body; 
    }

    private Stmt ifStatement() {
        
        // consumes the left parenthesis after the if keyword
        consume(LEFT_PAREN, "Expect '(' after 'if'.");

        // the condition inside of the brackets is an expression that evaluates to true or false
        Expr condition = expression();

        // consumes the right parenthesis to make sure the condition is closed
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        // the thenBranch is a statement after the condition inside of a block
        Stmt thenBranch = statement();

        // sets the else branch to null unless theres an else keyword after the statement that executes in the then branch
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        // instantiating a nested class and returning the new object
        return new Stmt.If(condition, thenBranch, elseBranch);

    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';'' after value.");

        return new Stmt.Print(value);
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration");
        return new Stmt.Var(name, initializer);
    }

    private Stmt  whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'" );
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expectt ')' after condition");

        Stmt body;

        // enters a loop and increments it, parses the statements inside it and stores it into the body variable then when its done it leaves the loop depth
        try {
            loopDepth++;
            body = statement();
        } finally {
            loopDepth--;
        }

        return new Stmt.While(condition, body);
    }

    private Stmt breakStatement() {
        Token breakToken = previous();
        if (loopDepth == 0) {
            throw error(breakToken, "Cannot use 'break' outside of a loop");
        }
        consume(SEMICOLON, "Expect ';' after break.");

        return new Stmt.Break(breakToken);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private List<Stmt> block() {

        // we create an empty list 
        List<Stmt> statements = new ArrayList<>();

        // we then parse statements and add them to the list until we reach the end of the block
        // the !isAtEnd() is so we dont get stuck in an infinite loop
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");

        // returns the list of statements inside of the block
        return statements;
    }

    private Expr assignment() {
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {    
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target");
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }
    
    private Expr equality() {
        Expr expr = comparison();

        while(match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();

            // combining the operands and the operator into a new Binay Syntax Tree node
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();

        while(match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match (SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {

        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) {
            return new Expr.Literal(false);
        }
        if (match(TRUE)) {
            return new Expr.Literal(true);
        }
        if (match(NIL)) {
            return new Expr.Literal(null);
        }

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)){
            return new Expr.Variable(previous());
        }
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression");
    }

    // this checks to see if any of the current token has any of the given types ( The parameters/arguments)
    // if so it consumes the tokens and returns true
    private boolean match(TokenType... types){
        for (TokenType type: types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }

        throw error(peek(), message);
    }

    // returns true if the current token is of the given type
    private boolean check(TokenType type) {
        if (isAtEnd()) {
            return false;
        }

        return peek().type == type;
    }

    // consumes the current token and returns it
    private Token advance(){
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current -1);
    }

    // checks to see if the next token is of the expected type, if so then it consumes the token
    // if not then we have hit an error and then we report it
    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();


        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) {
                return;
            }

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }
}