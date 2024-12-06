import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

class ScannerException extends RuntimeException {
    public ScannerException(String message) {
        super(message);
    }
}

class Token {
    String value;
    String type;
    int position; // Added to track token position for error reporting

    public Token(String value, String type, int position) {
        this.value = value;
        this.type = type;
        this.position = position;
    }
}

class TinyLangScanner {
    public static final String[] RESERVED_WORDS = {
            "if", "then","else" ,"end", "repeat", "until", "read", "write"
    };

    public static Map<Character, String> SYMBOLIC_WORDS = new HashMap<>();

    public static void initSymbolicWords() {
        SYMBOLIC_WORDS.put(';', "SEMICOLON");
        SYMBOLIC_WORDS.put('<', "LESSTHAN");
        //SYMBOLIC_WORDS.put('>', "GREATERTHAN");
        SYMBOLIC_WORDS.put('=', "EQUAL");
        SYMBOLIC_WORDS.put('+', "PLUS");
        SYMBOLIC_WORDS.put('-', "MINUS");
        SYMBOLIC_WORDS.put('*', "MULT");
        SYMBOLIC_WORDS.put('/', "DIV");
        SYMBOLIC_WORDS.put('(', "OPENBRACKET");
        SYMBOLIC_WORDS.put(')', "CLOSEDBRACKET");
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\n' || c == '\t' || c == '\r';
    }

    private static void addTokenIfNotEmpty(String currToken, List<String> tokens, int position) {
        if (!currToken.isEmpty()) {
            // Check for invalid characters in the token
            if (!currToken.startsWith("{") && !isValidToken(currToken)) {
                throw new ScannerException("Invalid token '" + currToken + "' at position " + position);
            }
            tokens.add(currToken);
        }
    }

    private static boolean isValidToken(String token) {
        // Check if token is a number
        if (token.matches("\\d+")) return true;

        // Check if token is a special symbol
        if (token.length() == 1 && SYMBOLIC_WORDS.containsKey(token.charAt(0))) return true;

        // Check if token is assignment operator
        if (token.equals(":=")) return true;

        // Check if token is a reserved word
        for (String reservedWord : RESERVED_WORDS) {
            if (token.equals(reservedWord)) return true;
        }

        // Check if token is a valid identifier (starts with letter, contains only letters)
        return token.matches("[a-zA-Z]+");
    }

    private static Token classifyToken(String token, int position) {
        // Numbers
        if (token.matches("\\d+")) {
            return new Token(token, "NUMBER", position);
        }

        // Special symbols
        if (token.length() == 1 && SYMBOLIC_WORDS.containsKey(token.charAt(0))) {
            return new Token(token, SYMBOLIC_WORDS.get(token.charAt(0)), position);
        }

        // Assignment operator
        if (token.equals(":=")) {
            return new Token(token, "ASSIGN", position);
        }

        // Comments
        if (token.startsWith("{") && token.endsWith("}")) {
            return null; // Comments are ignored
        }

        // Check for unclosed comment
        if (token.startsWith("{") && !token.endsWith("}")) {
            throw new ScannerException("Unclosed comment starting at position " + position);
        }

        // Reserved words
        for (String reservedWord : RESERVED_WORDS) {
            if (token.equals(reservedWord)) {
                return new Token(token, token.toUpperCase(), position);
            }
        }

        // Identifiers (must start with a letter and contain only letters)
        if (token.matches("[a-zA-Z]+")) {
            return new Token(token, "IDENTIFIER", position);
        }

        // If we reach here, the token is invalid
        throw new ScannerException("Invalid token '" + token + "' at position " + position);
    }

    public static List<Token> parseFile(String fileContent) {
        initSymbolicWords();
        List<Token> result = new ArrayList<>();
        List<String> tokens = new ArrayList<>();

        char state = 's';
        StringBuilder currToken = new StringBuilder();
        int position = 0;

        for (int i = 0; i < fileContent.length(); i++) {
            char curr = fileContent.charAt(i);
            position = i;

            // Handle special symbols first
            if (SYMBOLIC_WORDS.containsKey(curr) && state != 'a' && state != 'c') {
                addTokenIfNotEmpty(currToken.toString(), tokens, position - currToken.length());
                tokens.add(String.valueOf(curr));
                currToken.setLength(0);
                state = 's';
                continue;
            }

            switch (state) {
                case 's': // Start state
                    if (isWhitespace(curr)) {
                        continue;
                    }

                    if (Character.isAlphabetic(curr)) {
                        currToken.append(curr);
                        state = 'i';
                    } else if (Character.isDigit(curr)) {
                        currToken.append(curr);
                        state = 'n';
                    } else if (curr == ':') {
                        currToken.append(curr);
                        state = 'a';
                    } else if (curr == '{') {
                        currToken.append(curr);
                        state = 'c';
                    } else if (!isWhitespace(curr) && !SYMBOLIC_WORDS.containsKey(curr)) {
                        throw new ScannerException("Invalid character '" + curr + "' at position " + position);
                    }
                    break;
                case 'i': // Identifier state
                    if (Character.isAlphabetic(curr)) {
                        currToken.append(curr);
                    } else {
                        addTokenIfNotEmpty(currToken.toString(), tokens, position - currToken.length());
                        currToken.setLength(0);
                        state = 's';
                        i--; // Reprocess current character
                    }
                    break;
                case 'n': // Number state
                    if (Character.isDigit(curr)) {
                        currToken.append(curr);
                    } else {
                        addTokenIfNotEmpty(currToken.toString(), tokens, position - currToken.length());
                        currToken.setLength(0);
                        state = 's';
                        i--; // Reprocess current character
                    }
                    break;
                case 'a': // Assignment state
                    if (curr == '=') {
                        currToken.append(curr);
                        addTokenIfNotEmpty(currToken.toString(), tokens, position - currToken.length());
                        currToken.setLength(0);
                    } else {
                        throw new ScannerException("Invalid assignment operator at position " + (position - 1));
                    }
                    state = 's';
                    break;
                case 'c': // Comment state
                    currToken.append(curr);
                    if (curr == '}') {
                        addTokenIfNotEmpty(currToken.toString(), tokens, position - currToken.length());
                        currToken.setLength(0);
                        state = 's';
                    }
                    break;
            }
        }

        // Check for unclosed comment at end of file
        if (state == 'c') {
            throw new ScannerException("Unclosed comment at end of file");
        }

        // Add final token if exists
        addTokenIfNotEmpty(currToken.toString(), tokens, position - currToken.length());

        // Convert string tokens to Token objects
        for (String token : tokens) {
            Token tok = classifyToken(token, position);
            if (tok != null) {
                result.add(tok);
            }
        }

        return result;
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter the file path (N.B remove double quotes): \n ");
        String filePath = scanner.nextLine();
        String fileContent = "";

        try {
            fileContent = Files.readString(Paths.get(filePath));
            List<Token> tokens = parseFile(fileContent);

            System.out.print("Enter path to export the output file (N.B remove double quotes) : \n");
            String outputPath = scanner.nextLine();

            StringBuilder content = new StringBuilder();
            for (Token token : tokens) {
                content.append(token.value).append(" , ").append(token.type).append("\n");
            }

            FileWriter writer = new FileWriter(outputPath);
            writer.write(content.toString());
            writer.close();

        } catch (IOException e) {
            System.out.println("Error reading/writing file: " + e.getMessage());
        } catch (ScannerException e) {
            System.out.println("Scanner error: " + e.getMessage());
        } finally {
            // when in exe don't close the program directly after finishing to view the output
            System.out.println("Press Enter key to continue...");
            scanner.nextLine();
            scanner.close();
        }



    }
}