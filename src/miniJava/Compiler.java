package miniJava;

import miniJava.SyntacticAnalyzer.Parser;
import miniJava.SyntacticAnalyzer.Scanner;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class Compiler {
    // Main function, the file to compile will be an argument.
    public static void main(String[] args) {
        // TODO: Instantiate the ErrorReporter object
        ErrorReporter errors = new ErrorReporter();
        // TODO: Check to make sure a file path is given in args
        if (args.length == 0) return;
        // TODO: Create the inputStream using new FileInputStream
        try {
            InputStream in = new FileInputStream(args[0]);
// TODO: Instantiate the scanner with the input stream and error object
            Scanner scanner = new Scanner(in, errors);
// TODO: Instantiate the parser with the scanner and error object
            Parser parser = new Parser(scanner, errors);
            // TODO: Call the parser's parse function

            parser.parse();

            if (errors.hasErrors()) {
                // TODO: Check if any errors exist, if so, println("Error")
                //  then output the errors
                System.out.println("Error");
                errors.outputErrors();
            }        // TODO: If there are no errors, println("Success")
            else System.out.println("Success");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }


    }
}
