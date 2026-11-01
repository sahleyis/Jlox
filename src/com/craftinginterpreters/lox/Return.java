package com.craftinginterpreters.lox;

// wraps the return value with the thing java requires for a runtime execption class
class Return extends  RuntimeException {
    final Object value;

    Return(Object value) {
        // disables some JVM machinery that we dont need
        super(null, null, false, false);
        this.value = value;
    }
}