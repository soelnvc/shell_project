import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        String path = System.getenv("PATH");
        String[] pathDirs = path.split(":");

        while (true) {
            System.out.print("$ ");
            String command = sc.nextLine();

            if (command.equals("exit")) {
                break;
            } else if (command.startsWith("echo")) {
                String[] parts = command.trim().split("\\s+");
                if (parts.length <= 1) {
                    System.out.println();
                } else {
                    for (int i = 1; i < parts.length; i++) {
                        if (i > 1) System.out.print(" ");
                        System.out.print(parts[i]);
                    }
                    System.out.println();
                }
            } else if (command.startsWith("type")) {
                String typeArg = command.substring(5);
                System.out.println(type(typeArg));
            } else {
                System.out.println(command + ": command not found");
            }
        }

        sc.close();
    }

    public static String type(String command) {
        String[] commands = {"exit", "echo", "type"};
        String path = System.getenv("PATH");
        String[] pathDirs = path.split(":");

        boolean isBuiltIn = false;
        for (int i = 0; i < commands.length; i++) {
            if (commands[i].equals(command)) {
                return command + " is a shell builtin";
            }
        }

        for (int i = 0; i < pathDirs.length; i++) {
            File file = new File(pathDirs[i], command);
            if (file.exists() && file.canExecute()) {
                return command + " is " + file.getAbsolutePath();
            }
        }

        return command + ": not found";
    }

    public static boolean isBuiltin(String command) {
        return command.equals("exit") ||
               command.equals("echo") ||
               command.equals("type");
    }
    public static void runExternalProgram(String input) {
        try {
            String[] parts = input.split("\\s+");
            String commandName = parts[0];

            String executablePath = findExecutableInPath(commandName);

            if (executablePath == null) {
                System.out.println(commandName + ": command not found");
                return;
            }

            List<String> commandWithArgs = new ArrayList<>();
            commandWithArgs.add(executablePath);

            for (int i = 1; i < parts.length; i++) {
                commandWithArgs.add(parts[i]);
            }

            ProcessBuilder processBuilder = new ProcessBuilder(commandWithArgs);

            processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

            Process process = processBuilder.start();
            process.waitFor();

        } catch (Exception e) {
            System.out.println(input + ": command not found");
        }
    }

    public static String findExecutableInPath(String command) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        String[] pathDirs = path.split(":");
        for (String dir : pathDirs) {
            File file = new File(dir, command);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}