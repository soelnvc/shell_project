# Java POSIX Shell

A custom, robust shell environment built from scratch in Java. This project implements core operating system interactions, process management, and a custom command-line parser without relying on external libraries. 

It was developed to deeply understand how shells manage standard streams, handle concurrent processes, and interact directly with the underlying host OS.

## ⚙️ Core Engineering Features

* **Custom Lexer & Tokenizer:** Parses user input with full support for single quotes (`'`), double quotes (`"`), and escape characters (`\`), ensuring arguments are passed exactly as intended.
* **Pipeline Execution (`|`):** Implements multi-stage command piping. Leverages `ProcessBuilder.startPipeline` alongside asynchronous stream handling to prevent deadlocks during complex I/O chaining.
* **Process Management & Background Jobs (`&`):** Tracks background processes by assigning Job Numbers and PIDs. Includes a dynamic reaping mechanism to gracefully clean up and report on terminated background tasks.
* **I/O Redirection:** Granular control over standard output and error streams. Supports overriding (`>`) and appending (`>>`) for both `stdout` (`1>`, `1>>`) and `stderr` (`2>`, `2>>`).
* **Built-in Commands:** Native implementations of essential shell commands: `cd`, `pwd`, `echo`, `type`, `jobs`, and `exit`.

## 🛠️ Technical Architecture

The architecture is built around a continuous REPL (Read-Eval-Print Loop) that evaluates input through a multi-stage pipeline:

1.  **Tokenization:** The raw input string is broken down into a `List<Token>`, identifying regular arguments versus control operators (redirects, pipes, background flags).
2.  **Command Parsing:** Tokens are grouped into `CommandLine` objects, representing individual stages of a pipeline alongside their specific I/O routing requirements.
3.  **Execution:**
    * *Built-ins* are intercepted and executed within the main JVM thread to manipulate shell state (e.g., changing the `currentDirectory`).
    * *External Commands* are resolved against the system `$PATH` and launched via `ProcessBuilder`.
4.  **Asynchronous Stream Transfer:** For non-piped commands or mixed built-in/external pipelines, background threads manage `InputStream.transferTo(OutputStream)` to ensure non-blocking I/O flow.

## 🚀 Getting Started

### Prerequisites
* Java 21 or higher (Uses preview features and native access)
* Maven (`mvn`)

### Build & Run Locally

1. **Compile the project:**
   The repository includes a helper script to build the target jar using Maven.
   ```bash
   ./.codecrafters/compile.sh
