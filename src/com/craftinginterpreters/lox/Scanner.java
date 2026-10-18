package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
 
import static com.craftinginterpreters.lox.TokenType.*;

class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;
    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and", AND);
        keywords.put("class", CLASS);
        keywords.put("else", ELSE);
        keywords.put("false", FALSE);
        keywords.put("for", FOR);
        keywords.put("fun",    FUN);
        keywords.put("if",     IF);
        keywords.put("nil",    NIL);
        keywords.put("or",     OR);
        keywords.put("print",  PRINT);
        keywords.put("return", RETURN);
        keywords.put("super",  SUPER);
        keywords.put("this",   THIS);
        keywords.put("true",   TRUE);
        keywords.put("var",    VAR);
        keywords.put("while",  WHILE);
    }


    Scanner(String source) {
        this.source = source;
    }

    private void scanToken() {
        char c = advance();
        switch(c) {
            case '(': addToken(LEFT_PAREN);
                break;
            case ')': addToken(RIGHT_PAREN); 
                break;
            
            case '{': addToken(LEFT_BRACE); 
                break;
            
            case '}': addToken(RIGHT_BRACE); 
                break;
            
            case ',': addToken(COMMA); 
                break;
            
            case '.': addToken(DOT); 
                break;
            
            case '-': addToken(MINUS); 
                break;
            
            case'+': addToken(PLUS); 
                break;
            
            case ';': addToken(SEMICOLON); 
                break;
            
            case '*': addToken(STAR); 
                break;
            
            case '!': addToken(match('=') ? BANG_EQUAL : BANG); 
                break;
            
            case '=': addToken(match('=') ? EQUAL_EQUAL : EQUAL); 
                break;
            
            case '<': addToken(match('=') ? LESS_EQUAL : LESS);
                break;

            case '>': addToken(match('=') ? GREATER_EQUAL : GREATER);
                break;
            
            case '/':

                if (match('*')) {
                    while(!isAtEnd()) {
                        if (peek() == '\n'){
                            line++; // if it finds 
                        }

                        if (peek() == '*' && peekNext() == '/'){
                            advance(); // consume the asterisk
                            advance(); // consume the /
                            break;
                        }

                        advance(); // consume the current character and continue searching
                    }
                } else if (match('/')) {
                    // a comment goes until the end of the line
                    // so the peek functions looks until it finds a new line and that runs aslong as the conditions is true
                    // cus its a while loop so aslong is it isnt a new line and it isnt at the end of the line it will advance
                    // we used peek() instead of match() so that when it finds a new line character it will increment the line counter,
                    while (peek() != '\n' && !isAtEnd()) {
                        advance();
                    } 
                } else {
                    addToken(SLASH);
                }

                

                
                break;
            
            case ' ':
            case '\r':
            case '\t':
                break;

            case '\n':
                line++;
                break;
            
            case '"': string();
                break;

            
            default: {

                // the c value here is char c = advance() its just the advance function and since its a char we can,
                // use it in the isDigit function to check if the specific number literal token is actually a digit
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    Lox.error(line, "Unexpected character");
                }
                break;
            }
                
        }
    }

    private char advance() {
        return source.charAt(current++);
    }

    private void addToken(TokenType type){
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    private void number() {

        // while the next character is a digit, continue consuming the next tokens
        // consuming as many tokens as possible before encountering the decimal point
        while (isDigit(peek())) {
            advance();
        }

        // if the next character is a decimal point
        // it uses the peekNext() function to make sure there are digits after the decimal point
        if (peek() == '.' && isDigit( peekNext()) ) {

            // consume the "."
            advance();

            // while the next characters are digitis it will continue to consume those tokens
            while ( isDigit(peek()) ) {
                advance();
            }
        }

        // converts the numeric substring into a Double and stores it as a token
        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    private void string() {

        // checks for the beginning " and makes sure we arent at the end of the file
        //  and if we are at a new line it will increase the line counter and then it will advance to the next token
        // since this is a while loop it will run until this condition is false so until it either sees the closing " or reaches the end of the file
        while(peek() != '"' && !isAtEnd() ) {
            if (peek() == '\n') {
                line++;
            }
            advance();
        }

        // if its at the end of the file it will give an error cus theres no string to read
        if (isAtEnd()) {
            Lox.error(line, "Unterminated String");
            return;
        }

        // it goes to the next token after the closing "
        advance();

        // this trims the surrounding quotation marks and makes the text inside it an actual string token
        String value = source.substring(start + 1, current -1);
        addToken(STRING, value);
    }

    // checks if we are at the end of the source file
    private boolean isAtEnd() {
        return current >= source.length();
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) {
            return '\0';
        }
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) {
            return '\0';
        }
        return source.charAt(current + 1);
    }

    private boolean isAlpha(char c) {
        return ( c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private boolean isDigit(char c){
        return c >= '0' && c <= '9';
    }
    
    List<Token> scanTokens() {
        while(!isAtEnd()) {
            start = current;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) {
            advance();
        }

        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null) {
            type = IDENTIFIER;
        }

        addToken(type);
    }
    
}