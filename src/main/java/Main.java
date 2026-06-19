import java.io.File;
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

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String input = sc.nextLine();

            List<String> parts = parseInput(input);

            if (parts.isEmpty()) {
                continue;
            }

            String cmd = parts.get(0);

            if (cmd.equals("exit")) {
                break;
            } else if (cmd.equals("type")) {
                String arg = parts.size() > 1 ? parts.get(1) : "";
                System.out.println(type(arg));
            } else if (cmd.equals("echo")) {
                if (parts.size() > 1) {
                    System.out.println(String.join(" ", parts.subList(1, parts.size())));
                } else {
                    System.out.println();
                }
            } else if (cmd.equals("pwd")) {
                System.out.println(pwd());
            } else if (cmd.equals("cd")) {
                String arg = parts.size() > 1 ? parts.get(1) : "";
                cd(arg);
            } else if (getExecutable(cmd) != null) {
                ProcessBuilder processBuilder = new ProcessBuilder(parts);
                processBuilder.directory(currentDirectory);
                processBuilder.redirectErrorStream(true);

                Process process = processBuilder.start();
                process.getInputStream().transferTo(System.out);
                process.waitFor();
            } else {
                System.out.println(cmd + ": command not found");
            }
        }

        sc.close();
    }

    public static List<String> parseInput(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean insideSingleQuote = false;
        boolean insideDoubleQuote = false;
        boolean argStarted = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (ch == '\'' && !insideDoubleQuote) {
                insideSingleQuote = !insideSingleQuote;
                argStarted = true;
            } else if (ch == '"' && !insideSingleQuote) {
                insideDoubleQuote = !insideDoubleQuote;
                argStarted = true;
            } else if (Character.isWhitespace(ch) && !insideSingleQuote && !insideDoubleQuote) {
                if (argStarted) {
                    args.add(current.toString());
                    current.setLength(0);
                    argStarted = false;
                }
            } else {
                current.append(ch);
                argStarted = true;
            }
        }

        if (argStarted) {
            args.add(current.toString());
        }

        return args;
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