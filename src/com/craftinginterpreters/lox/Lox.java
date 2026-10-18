package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;



public class Lox {
    // variables

    // so we dont excecute code that has a known erorr we initialize a hadError variable that we can use to track
    static boolean hadError = false;

    // The main function loop, when the argument number is greater than 1 in prints usage and exists, but if its just 1,
    // then it runs the file assuming its a file path and if it has no arguments then it runs a prompt where the user can,
    // enter code line by line and excecute the code, kinda like a code editor like python
    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }


    // this reads an entire source file into memory and excecutes
    private static void runFile(String path) throws IOException {
        
        // reads the raw bytes of the file
        byte[] bytes = Files.readAllBytes(Paths.get(path));

        // converts the raw byte array into a string and passes it into the run Function
        run(new String(bytes, Charset.defaultCharset()));
        // if theres an error then it will exit the program
        if (hadError) {
            System.exit(65);
        }
    }

    // sets up a session where the user can type code line by line like Python or Node.js terminal
    private static void runPrompt() throws IOException {

        // System.in is the raw byte stream and InputStreamReader decodes those bytes into characters so its easier to read those lines using .readLine()
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        // an infinite for Loop
        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            run(line);
            hadError = false;
        }
    }

    // it passses the line where the error happened, where it happened and its message,
    // then it prints it out to the System and makes the boolean value hadError to True
    private static void report(int line, String where, String message) {
        System.err.println("[Line " + line + " ] Error" + where + ": " + message );
        hadError = true;
    }

    // the error function that reports where an error happened and the message to the error using the report function as a helper
    static void error(int line, String message) {
        report(line, "", message);
    }

    // takes the raw source code and interprets it, right now it only does scanning/lexing
    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        for (Token token: tokens) {
            System.out.println(token);
        }
    }


    
    
}