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
        String stderrFile = null;
        boolean stdoutAppend = false;
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

            File stderrRedirectFile = commandLine.stderrFile == null
                    ? null
                    : resolvePath(commandLine.stderrFile);

            if (cmd.equals("exit")) {
                break;
            } else if (cmd.equals("type")) {
                String arg = commandLine.args.size() > 1 ? commandLine.args.get(1) : "";
                printStdout(type(arg), stdoutRedirectFile, commandLine.stdoutAppend);
                createEmptyFileIfNeeded(stderrRedirectFile, false);
            } else if (cmd.equals("echo")) {
                String output = "";

                if (commandLine.args.size() > 1) {
                    output = String.join(" ", commandLine.args.subList(1, commandLine.args.size()));
                }

                printStdout(output, stdoutRedirectFile, commandLine.stdoutAppend);
                createEmptyFileIfNeeded(stderrRedirectFile, false);
            } else if (cmd.equals("pwd")) {
                printStdout(pwd(), stdoutRedirectFile, commandLine.stdoutAppend);
                createEmptyFileIfNeeded(stderrRedirectFile, false);
            } else if (cmd.equals("cd")) {
                String arg = commandLine.args.size() > 1 ? commandLine.args.get(1) : "";
                cd(arg, stderrRedirectFile);
                createEmptyFileIfNeeded(stdoutRedirectFile, commandLine.stdoutAppend);
            } else if (getExecutable(cmd) != null) {
                ProcessBuilder processBuilder = new ProcessBuilder(commandLine.args);
                processBuilder.directory(currentDirectory);

                if (stdoutRedirectFile != null) {
                    if (commandLine.stdoutAppend) {
                        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(stdoutRedirectFile));
                    } else {
                        processBuilder.redirectOutput(stdoutRedirectFile);
                    }
                }

                if (stderrRedirectFile != null) {
                    processBuilder.redirectError(stderrRedirectFile);
                }

                Process process = processBuilder.start();

                if (stdoutRedirectFile == null) {
                    process.getInputStream().transferTo(System.out);
                }

                if (stderrRedirectFile == null) {
                    process.getErrorStream().transferTo(System.err);
                }

                process.waitFor();
            } else {
                printStderr(cmd + ": command not found", stderrRedirectFile);
                createEmptyFileIfNeeded(stdoutRedirectFile, commandLine.stdoutAppend);
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
                    if (token.value.equals(">") || token.value.equals("1>")) {
                        commandLine.stdoutFile = tokens.get(i + 1).value;
                        commandLine.stdoutAppend = false;
                    } else if (token.value.equals(">>") || token.value.equals("1>>")) {
                        commandLine.stdoutFile = tokens.get(i + 1).value;
                        commandLine.stdoutAppend = true;
                    } else if (token.value.equals("2>")) {
                        commandLine.stderrFile = tokens.get(i + 1).value;
                    }

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
                boolean isAppend = i + 1 < input.length() && input.charAt(i + 1) == '>';

                if (argStarted && current.toString().equals("1")) {
                    current.setLength(0);
                    argStarted = false;

                    if (isAppend) {
                        tokens.add(new Token("1>>", true));
                        i++;
                    } else {
                        tokens.add(new Token("1>", true));
                    }
                } else if (argStarted && current.toString().equals("2")) {
                    current.setLength(0);
                    argStarted = false;
                    tokens.add(new Token("2>", true));
                } else {
                    if (argStarted) {
                        tokens.add(new Token(current.toString(), false));
                        current.setLength(0);
                        argStarted = false;
                    }

                    if (isAppend) {
                        tokens.add(new Token(">>", true));
                        i++;
                    } else {
                        tokens.add(new Token(">", true));
                    }
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

    public static void printStdout(String text, File redirectFile, boolean append) throws Exception {
        if (redirectFile == null) {
            System.out.println(text);
        } else {
            FileWriter writer = new FileWriter(redirectFile, append);
            writer.write(text);
            writer.write(System.lineSeparator());
            writer.close();
        }
    }

    public static void printStderr(String text, File redirectFile) throws Exception {
        if (redirectFile == null) {
            System.err.println(text);
        } else {
            FileWriter writer = new FileWriter(redirectFile, false);
            writer.write(text);
            writer.write(System.lineSeparator());
            writer.close();
        }
    }

    public static void createEmptyFileIfNeeded(File file, boolean append) throws Exception {
        if (file != null) {
            FileWriter writer = new FileWriter(file, append);
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

    public static void cd(String dir, File stderrRedirectFile) throws Exception {
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
            printStderr("cd: " + dir + ": No such file or directory", stderrRedirectFile);
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