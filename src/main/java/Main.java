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
    }
}