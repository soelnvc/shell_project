import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;

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

            if (input.trim().isEmpty()) {
                continue;
            }

            String cmd = input.indexOf(" ") == -1
                    ? input
                    : input.substring(0, input.indexOf(" "));

            String rem = input.indexOf(" ") == -1
                    ? ""
                    : input.substring(input.indexOf(" ") + 1);

            if (cmd.equals("exit")) {
                break;
            } else if (cmd.equals("type")) {
                System.out.println(type(rem));
            } else if (cmd.equals("echo")) {
                System.out.println(rem);
            } else if (cmd.equals("pwd")) {
                System.out.println(pwd());
            } else if (cmd.equals("cd")) {
                cd(rem);
            } else if (getExecutable(cmd) != null) {
                String[] parts = input.split(" ");

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

    public static String pwd() {
        return currentDirectory.getAbsolutePath();
    }

    public static void cd(String dir) throws Exception {
        File newDirectory;

        if (dir.startsWith("/")) {
            // Absolute path: /usr/local/bin
            newDirectory = new File(dir);
        } else {
            // Relative path: ./, ../, ./dir, dirname
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