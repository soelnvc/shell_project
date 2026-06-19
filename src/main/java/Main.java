import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;

public class Main {
    static File currentDirectory = new File(System.getProperty("user.dir")).getAbsoluteFile();

    static HashSet<String> commands = new HashSet<>(
            Arrays.asList("exit", "echo", "type", "pwd", "cd", "jobs")
    );

    static List<Job> jobs = new ArrayList<>();

    static class Job {
        int jobNumber;
        long pid;
        String command;
        Process process;

        Job(int jobNumber, long pid, String command, Process process) {
            this.jobNumber = jobNumber;
            this.pid = pid;
            this.command = command;
            this.process = process;
        }
    }

    static class Token {
        String value;
        boolean isRedirectOperator;
        boolean isBackgroundOperator;
        boolean isPipeOperator;

        Token(String value, boolean isRedirectOperator) {
            this.value = value;
            this.isRedirectOperator = isRedirectOperator;
            this.isBackgroundOperator = false;
            this.isPipeOperator = false;
        }

        Token(String value, boolean isRedirectOperator, boolean isBackgroundOperator, boolean isPipeOperator) {
            this.value = value;
            this.isRedirectOperator = isRedirectOperator;
            this.isBackgroundOperator = isBackgroundOperator;
            this.isPipeOperator = isPipeOperator;
        }
    }

    static class CommandLine {
        List<String> args = new ArrayList<>();

        String stdoutFile = null;
        boolean stdoutAppend = false;

        String stderrFile = null;
        boolean stderrAppend = false;

        boolean background = false;
    }

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            reapCompletedJobs(null, false);

            System.out.print("$ ");

            String input = sc.nextLine();

            List<CommandLine> pipeline = parsePipeline(input);

            if (pipeline.isEmpty() || pipeline.get(0).args.isEmpty()) {
                continue;
            }

            if (pipeline.size() > 1) {
                runPipeline(pipeline, input.trim());
                continue;
            }

            CommandLine commandLine = pipeline.get(0);
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
                createEmptyFileIfNeeded(stderrRedirectFile, commandLine.stderrAppend);

            } else if (cmd.equals("echo")) {
                String output = "";

                if (commandLine.args.size() > 1) {
                    output = String.join(" ", commandLine.args.subList(1, commandLine.args.size()));
                }

                printStdout(output, stdoutRedirectFile, commandLine.stdoutAppend);
                createEmptyFileIfNeeded(stderrRedirectFile, commandLine.stderrAppend);

            } else if (cmd.equals("pwd")) {
                printStdout(pwd(), stdoutRedirectFile, commandLine.stdoutAppend);
                createEmptyFileIfNeeded(stderrRedirectFile, commandLine.stderrAppend);

            } else if (cmd.equals("cd")) {
                String arg = commandLine.args.size() > 1 ? commandLine.args.get(1) : "";
                cd(arg, stderrRedirectFile, commandLine.stderrAppend);
                createEmptyFileIfNeeded(stdoutRedirectFile, commandLine.stdoutAppend);

            } else if (cmd.equals("jobs")) {
                reapCompletedJobs(stdoutRedirectFile, commandLine.stdoutAppend);
                printJobs(stdoutRedirectFile, true);
                createEmptyFileIfNeeded(stderrRedirectFile, commandLine.stderrAppend);

            } else if (getExecutable(cmd) != null) {
                ProcessBuilder processBuilder = new ProcessBuilder(commandLine.args);
                processBuilder.directory(currentDirectory);

                if (stdoutRedirectFile != null) {
                    if (commandLine.stdoutAppend) {
                        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(stdoutRedirectFile));
                    } else {
                        processBuilder.redirectOutput(stdoutRedirectFile);
                    }
                } else if (commandLine.background) {
                    processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }

                if (stderrRedirectFile != null) {
                    if (commandLine.stderrAppend) {
                        processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(stderrRedirectFile));
                    } else {
                        processBuilder.redirectError(stderrRedirectFile);
                    }
                } else if (commandLine.background) {
                    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
                }

                Process process = processBuilder.start();

                if (commandLine.background) {
                    int jobNumber = getNextJobNumber();
                    jobs.add(new Job(jobNumber, process.pid(), input.trim(), process));
                    System.out.println("[" + jobNumber + "] " + process.pid());

                } else {
                    if (stdoutRedirectFile == null) {
                        process.getInputStream().transferTo(System.out);
                    }

                    if (stderrRedirectFile == null) {
                        process.getErrorStream().transferTo(System.err);
                    }

                    process.waitFor();
                }

            } else {
                printStderr(cmd + ": command not found", stderrRedirectFile, commandLine.stderrAppend);
                createEmptyFileIfNeeded(stdoutRedirectFile, commandLine.stdoutAppend);
            }
        }

        sc.close();
    }

    public static int getNextJobNumber() {
        if (jobs.isEmpty()) {
            return 1;
        }

        int highestJobNumber = 0;

        for (Job job : jobs) {
            if (job.jobNumber > highestJobNumber) {
                highestJobNumber = job.jobNumber;
            }
        }

        return highestJobNumber + 1;
    }

    public static void runPipeline(List<CommandLine> pipeline, String originalInput) throws Exception {
        List<ProcessBuilder> builders = new ArrayList<>();

        boolean background = pipeline.get(pipeline.size() - 1).background;

        for (int i = 0; i < pipeline.size(); i++) {
            CommandLine commandLine = pipeline.get(i);

            if (commandLine.args.isEmpty()) {
                return;
            }

            String cmd = commandLine.args.get(0);

            if (getExecutable(cmd) == null && !commands.contains(cmd)) {
                printStderr(cmd + ": command not found", null, false);
                return;
            }

            ProcessBuilder processBuilder = new ProcessBuilder(commandLine.args);
            processBuilder.directory(currentDirectory);

            File stderrRedirectFile = commandLine.stderrFile == null
                    ? null
                    : resolvePath(commandLine.stderrFile);

            if (stderrRedirectFile != null) {
                if (commandLine.stderrAppend) {
                    processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(stderrRedirectFile));
                } else {
                    processBuilder.redirectError(stderrRedirectFile);
                }
            } else if (background) {
                processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            if (i == pipeline.size() - 1) {
                File stdoutRedirectFile = commandLine.stdoutFile == null
                        ? null
                        : resolvePath(commandLine.stdoutFile);

                if (stdoutRedirectFile != null) {
                    if (commandLine.stdoutAppend) {
                        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(stdoutRedirectFile));
                    } else {
                        processBuilder.redirectOutput(stdoutRedirectFile);
                    }
                } else if (background) {
                    processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }
            }

            builders.add(processBuilder);
        }

        List<Process> processes = ProcessBuilder.startPipeline(builders);
        Process lastProcess = processes.get(processes.size() - 1);

        if (background) {
            int jobNumber = getNextJobNumber();
            jobs.add(new Job(jobNumber, lastProcess.pid(), originalInput, lastProcess));
            System.out.println("[" + jobNumber + "] " + lastProcess.pid());
            return;
        }

        List<Thread> errorThreads = new ArrayList<>();

        for (int i = 0; i < processes.size(); i++) {
            if (pipeline.get(i).stderrFile == null) {
                Thread thread = transferAsync(processes.get(i).getErrorStream(), System.err);
                errorThreads.add(thread);
            }
        }

        CommandLine lastCommand = pipeline.get(pipeline.size() - 1);

        if (lastCommand.stdoutFile == null) {
            lastProcess.getInputStream().transferTo(System.out);
        }

        lastProcess.waitFor();

        for (int i = 0; i < processes.size() - 1; i++) {
            Process process = processes.get(i);

            if (process.isAlive()) {
                process.destroy();
            }
        }

        for (Process process : processes) {
            process.waitFor();
        }

        for (Thread thread : errorThreads) {
            thread.join();
        }
    }

    public static Thread transferAsync(InputStream inputStream, OutputStream outputStream) {
        Thread thread = new Thread(() -> {
            try {
                inputStream.transferTo(outputStream);
            } catch (Exception ignored) {
            }
        });

        thread.start();
        return thread;
    }

    public static void reapCompletedJobs(File stdoutRedirectFile, boolean stdoutAppend) throws Exception {
        StringBuilder output = new StringBuilder();

        int currentJobNumber = -1;
        int previousJobNumber = -1;

        if (jobs.size() >= 1) {
            currentJobNumber = jobs.get(jobs.size() - 1).jobNumber;
        }

        if (jobs.size() >= 2) {
            previousJobNumber = jobs.get(jobs.size() - 2).jobNumber;
        }

        List<Job> doneJobs = new ArrayList<>();

        for (Job job : jobs) {
            if (!job.process.isAlive()) {
                job.process.waitFor();
                doneJobs.add(job);

                char marker = ' ';

                if (job.jobNumber == currentJobNumber) {
                    marker = '+';
                } else if (job.jobNumber == previousJobNumber) {
                    marker = '-';
                }

                output.append("[")
                        .append(job.jobNumber)
                        .append("]")
                        .append(marker)
                        .append("  ")
                        .append(String.format("%-24s", "Done"))
                        .append(removeTrailingAmpersand(job.command))
                        .append(System.lineSeparator());
            }
        }

        jobs.removeAll(doneJobs);

        if (output.length() == 0) {
            return;
        }

        if (stdoutRedirectFile == null) {
            System.out.print(output.toString());
        } else {
            FileWriter writer = new FileWriter(stdoutRedirectFile, stdoutAppend);
            writer.write(output.toString());
            writer.close();
        }
    }

    public static void printJobs(File stdoutRedirectFile, boolean stdoutAppend) throws Exception {
        StringBuilder output = new StringBuilder();

        int currentJobNumber = -1;
        int previousJobNumber = -1;

        if (jobs.size() >= 1) {
            currentJobNumber = jobs.get(jobs.size() - 1).jobNumber;
        }

        if (jobs.size() >= 2) {
            previousJobNumber = jobs.get(jobs.size() - 2).jobNumber;
        }

        for (Job job : jobs) {
            if (job.process.isAlive()) {
                char marker = ' ';

                if (job.jobNumber == currentJobNumber) {
                    marker = '+';
                } else if (job.jobNumber == previousJobNumber) {
                    marker = '-';
                }

                output.append("[")
                        .append(job.jobNumber)
                        .append("]")
                        .append(marker)
                        .append("  ")
                        .append(String.format("%-24s", "Running"))
                        .append(job.command)
                        .append(System.lineSeparator());
            }
        }

        if (stdoutRedirectFile == null) {
            System.out.print(output.toString());
        } else {
            FileWriter writer = new FileWriter(stdoutRedirectFile, stdoutAppend);
            writer.write(output.toString());
            writer.close();
        }
    }

    public static String removeTrailingAmpersand(String command) {
        String trimmed = command.trim();

        if (trimmed.endsWith("&")) {
            return trimmed.substring(0, trimmed.length() - 1).trim();
        }

        return trimmed;
    }

    public static List<CommandLine> parsePipeline(String input) {
        List<Token> tokens = tokenize(input);

        boolean background = false;

        if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).isBackgroundOperator) {
            background = true;
            tokens.remove(tokens.size() - 1);
        }

        List<CommandLine> pipeline = new ArrayList<>();
        List<Token> currentCommandTokens = new ArrayList<>();

        for (Token token : tokens) {
            if (token.isPipeOperator) {
                CommandLine commandLine = parseCommandTokens(currentCommandTokens);
                commandLine.background = background;
                pipeline.add(commandLine);
                currentCommandTokens.clear();
            } else {
                currentCommandTokens.add(token);
            }
        }

        CommandLine commandLine = parseCommandTokens(currentCommandTokens);
        commandLine.background = background;
        pipeline.add(commandLine);

        return pipeline;
    }

    public static CommandLine parseCommandTokens(List<Token> tokens) {
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
                        commandLine.stderrAppend = false;
                    } else if (token.value.equals("2>>")) {
                        commandLine.stderrFile = tokens.get(i + 1).value;
                        commandLine.stderrAppend = true;
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

            } else if (ch == '|' && !insideSingleQuote && !insideDoubleQuote) {
                if (argStarted) {
                    tokens.add(new Token(current.toString(), false));
                    current.setLength(0);
                    argStarted = false;
                }

                tokens.add(new Token("|", false, false, true));

            } else if (ch == '&' && !insideSingleQuote && !insideDoubleQuote) {
                if (argStarted) {
                    tokens.add(new Token(current.toString(), false));
                    current.setLength(0);
                    argStarted = false;
                }

                tokens.add(new Token("&", false, true, false));

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

                    if (isAppend) {
                        tokens.add(new Token("2>>", true));
                        i++;
                    } else {
                        tokens.add(new Token("2>", true));
                    }

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

    public static void printStderr(String text, File redirectFile, boolean append) throws Exception {
        if (redirectFile == null) {
            System.err.println(text);
        } else {
            FileWriter writer = new FileWriter(redirectFile, append);
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

    public static void cd(String dir, File stderrRedirectFile, boolean stderrAppend) throws Exception {
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
            printStderr("cd: " + dir + ": No such file or directory", stderrRedirectFile, stderrAppend);
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