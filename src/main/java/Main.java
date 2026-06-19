import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;

public class Main {
    static File currentDirectory = new File(System.getProperty("user.dir")).getAbsoluteFile();

    static HashSet<String> commands = new HashSet<>(
            Arrays.asList("exit", "echo", "type", "pwd", "cd")
    );

    static class Token {
        String value;
        boolean isRedirectOperator;

        Token(String value, boolean isRedirectOperator) {
            this.value = value;
            this.isRedirectOperator = isRedirectOperator;
        }
    }

    static class CommandLine {
        List<String> args = new ArrayList<>();
        String stdoutFile = null;
    }

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String input = sc.nextLine();

            CommandLine commandLine = parseCommandLine(input);

            if (commandLine.args.isEmpty()) {
                continue;
            }

            String cmd = commandLine.args.get(0);
            File stdoutRedirectFile = commandLine.stdoutFile == null
                    ? null
                    : resolvePath(commandLine.stdoutFile);

            if (cmd.equals("exit")) {
                break;
            } else if (cmd.equals("type")) {
                String arg = commandLine.args.size() > 1 ? commandLine.args.get(1) : "";
                printStdout(type(arg), stdoutRedirectFile);
            } else if (cmd.equals("echo")) {
                String output = "";

                if (commandLine.args.size() > 1) {
                    output = String.join(" ", commandLine.args.subList(1, commandLine.args.size()));
                }

                printStdout(output, stdoutRedirectFile);
            } else if (cmd.equals("pwd")) {
                printStdout(pwd(), stdoutRedirectFile);
            } else if (cmd.equals("cd")) {
                String arg = commandLine.args.size() > 1 ? commandLine.args.get(1) : "";
                cd(arg);
            } else if (getExecutable(cmd) != null) {
                ProcessBuilder processBuilder = new ProcessBuilder(commandLine.args);
                processBuilder.directory(currentDirectory);

                if (stdoutRedirectFile != null) {
                    processBuilder.redirectOutput(stdoutRedirectFile);
                }

                Process process = processBuilder.start();

                if (stdoutRedirectFile == null) {
                    process.getInputStream().transferTo(System.out);
                }

                process.getErrorStream().transferTo(System.err);
                process.waitFor();
            } else {
                System.out.println(cmd + ": command not found");
            }
        }

        sc.close();
    }

    public static CommandLine parseCommandLine(String input) {
        List<Token> tokens = tokenize(input);

        CommandLine commandLine = new CommandLine();

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            if (token.isRedirectOperator) {
                if (i + 1 < tokens.size()) {
                    commandLine.stdoutFile = tokens.get(i + 1).value;
                    i++;
                }
            } else {
                commandLine.args.add(token.value);
            }
        }

        return commandLine;
    }

    public static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean insideSingleQuote = false;
        boolean insideDoubleQuote = false;
        boolean argStarted = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (ch == '\\' && insideDoubleQuote) {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);

                    if (next == '"' || next == '\\') {
                        current.append(next);
                        i++;
                    } else {
                        current.append(ch);
                    }
                } else {
                    current.append(ch);
                }

                argStarted = true;
            } else if (ch == '\\' && !insideSingleQuote && !insideDoubleQuote) {
                if (i + 1 < input.length()) {
                    current.append(input.charAt(i + 1));
                    i++;
                } else {
                    current.append(ch);
                }

                argStarted = true;
            } else if (ch == '\'' && !insideDoubleQuote) {
                insideSingleQuote = !insideSingleQuote;
                argStarted = true;
            } else if (ch == '"' && !insideSingleQuote) {
                insideDoubleQuote = !insideDoubleQuote;
                argStarted = true;
            } else if (ch == '>' && !insideSingleQuote && !insideDoubleQuote) {
                if (argStarted && current.toString().equals("1")) {
                    current.setLength(0);
                    argStarted = false;
                    tokens.add(new Token("1>", true));
                } else {
                    if (argStarted) {
                        tokens.add(new Token(current.toString(), false));
                        current.setLength(0);
                        argStarted = false;
                    }

                    tokens.add(new Token(">", true));
                }
            } else if (Character.isWhitespace(ch) && !insideSingleQuote && !insideDoubleQuote) {
                if (argStarted) {
                    tokens.add(new Token(current.toString(), false));
                    current.setLength(0);
                    argStarted = false;
                }
            } else {
                current.append(ch);
                argStarted = true;
            }
        }

        if (argStarted) {
            tokens.add(new Token(current.toString(), false));
        }

        return tokens;
    }

    public static void printStdout(String text, File redirectFile) throws Exception {
        if (redirectFile == null) {
            System.out.println(text);
        } else {
            FileWriter writer = new FileWriter(redirectFile, false);
            writer.write(text);
            writer.write(System.lineSeparator());
            writer.close();
        }
    }

    public static File resolvePath(String path) {
        if (path.startsWith("/")) {
            return new File(path);
        }

        return new File(currentDirectory, path);
    }

    public static String pwd() {
        return currentDirectory.getAbsolutePath();
    }

    public static void cd(String dir) throws Exception {
        File newDirectory;

        if (dir.equals("~")) {
            String home = System.getenv("HOME");
            newDirectory = new File(home);
        } else if (dir.startsWith("~/")) {
            String home = System.getenv("HOME");
            newDirectory = new File(home, dir.substring(2));
        } else if (dir.startsWith("/")) {
            newDirectory = new File(dir);
        } else {
            newDirectory = new File(currentDirectory, dir);
        }

        if (newDirectory.exists() && newDirectory.isDirectory()) {
            currentDirectory = newDirectory.getCanonicalFile();
        } else {
            System.out.println("cd: " + dir + ": No such file or directory");
        }
    }

    public static String type(String cmd) {
        if (commands.contains(cmd)) {
            return cmd + " is a shell builtin";
        }

        String path = System.getenv("PATH");
        String[] pathDir = path.split(":");

        for (String dir : pathDir) {
            File file = new File(dir, cmd);

            if (file.exists() && file.canExecute()) {
                return cmd + " is " + file.getAbsolutePath();
            }
        }

        return cmd + ": not found";
    }

    public static String getExecutable(String cmd) {
        String path = System.getenv("PATH");
        String[] pathDir = path.split(":");

        for (String dir : pathDir) {
            File file = new File(dir, cmd);

            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }
}