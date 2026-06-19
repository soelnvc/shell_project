import java.io.File;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String input = scanner.nextLine().trim();

            if (input.equals("exit") || input.equals("exit 0")) {
                break;
            } 
            else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            } 
            else if (input.startsWith("type ")) {
                String command = input.substring(5).trim();

                if (command.equals("echo") || command.equals("exit") || command.equals("type")) {
                    System.out.println(command + " is a shell builtin");
                } else {
                    System.out.println(command + ": not found");
                }
            } 
            else {
                System.out.println(input + ": command not found");
            }
        }
        scanner.close();
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
}