package com.example.desktop;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        Path logsDir = resolveLogsDirectory();
        try {
            Files.createDirectories(logsDir);
        } catch (Exception ex) {
            System.err.println("Unable to create logs directory: " + logsDir + " - " + ex.getMessage());
        }

        String startupStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String appLogFile = logsDir.resolve("application-" + startupStamp + ".log").toString();
        configureConsoleAndFileTee(appLogFile);
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS");
        System.setProperty("org.slf4j.simpleLogger.log.io.javalin", "warn");
        System.setProperty("app.logs.dir", logsDir.toString());

        System.out.println("Logs directory: " + logsDir);
        System.out.println("Application log file: " + appLogFile);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("Uncaught error on thread: " + thread.getName());
            throwable.printStackTrace(System.err);
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                DataSourceManager.shutdownAll();
            } catch (Exception ex) {
                System.err.println("Error while shutting down data sources: " + ex.getMessage());
            }
        }, "datasource-shutdown"));
        MainApp.main(args);
    }

    private static void configureConsoleAndFileTee(String appLogFile) {
        try {
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            FileOutputStream fileOut = new FileOutputStream(appLogFile, true);

            PrintStream teeOut = new PrintStream(
                    new TeeOutputStream(originalOut, fileOut, "OUT"),
                    true,
                    StandardCharsets.UTF_8
            );
            PrintStream teeErr = new PrintStream(
                    new TeeOutputStream(originalErr, fileOut, "ERR"),
                    true,
                    StandardCharsets.UTF_8
            );

            System.setOut(teeOut);
            System.setErr(teeErr);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    teeOut.flush();
                    teeErr.flush();
                    fileOut.flush();
                    fileOut.close();
                } catch (IOException ignored) {
                }
            }));
        } catch (Exception ex) {
            System.err.println("Unable to configure tee logging: " + ex.getMessage());
        }
    }

    private static Path resolveLogsDirectory() {
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            Path appDir = Paths.get(appPath).getParent();
            if (appDir != null) {
                return appDir.resolve("logs");
            }
        }
        return Paths.get(System.getProperty("user.dir"), "logs");
    }

    private static final class TeeOutputStream extends OutputStream {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        private static final byte[] ANSI_RED = "\u001B[31m".getBytes(StandardCharsets.UTF_8);
        private static final byte[] ANSI_RESET = "\u001B[0m".getBytes(StandardCharsets.UTF_8);
        private final OutputStream first;
        private final OutputStream second;
        private final String streamName;
        private final StringBuilder lineBuffer = new StringBuilder(256);
        private final StringBuilder errorConsoleBuffer = new StringBuilder(256);
        private final boolean colorizeErrors;

        private TeeOutputStream(OutputStream first, OutputStream second, String streamName) {
            this.first = first;
            this.second = second;
            this.streamName = streamName;
            this.colorizeErrors = "ERR".equals(streamName);
        }

        @Override
        public void write(int b) throws IOException {
            // Delegate to the array overload to keep a single code path
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (!colorizeErrors) {
                first.write(b, off, len);
                for (int i = off; i < off + len; i++) {
                    writeToFileWithTimestamp(b[i]);
                }
                return;
            }
            for (int i = off; i < off + len; i++) {
                writeToConsole(b[i]);
                writeToFileWithTimestamp(b[i]);
            }
        }

        @Override
        public void flush() throws IOException {
            flushErrorConsoleBuffer();
            if (lineBuffer.length() > 0) {
                String ts = LocalDateTime.now().format(FORMATTER);
                String entry = "[" + ts + "][" + streamName + "] " + lineBuffer + System.lineSeparator();
                second.write(entry.getBytes(StandardCharsets.UTF_8));
                lineBuffer.setLength(0);
            }
            first.flush();
            second.flush();
        }

        private void writeToConsole(byte value) throws IOException {
            if (!colorizeErrors) {
                first.write(value);
                return;
            }

            char ch = (char) (value & 0xFF);
            errorConsoleBuffer.append(ch);
            if (ch == '\n') {
                flushErrorConsoleBuffer();
            }
        }

        private void flushErrorConsoleBuffer() throws IOException {
            if (!colorizeErrors || errorConsoleBuffer.length() == 0) {
                return;
            }
            first.write(ANSI_RED);
            first.write(errorConsoleBuffer.toString().getBytes(StandardCharsets.UTF_8));
            first.write(ANSI_RESET);
            errorConsoleBuffer.setLength(0);
        }

        private void writeToFileWithTimestamp(byte value) throws IOException {
            char ch = (char) (value & 0xFF);
            lineBuffer.append(ch);
            if (ch == '\n') {
                String line = lineBuffer.toString();
                lineBuffer.setLength(0);
                String ts = LocalDateTime.now().format(FORMATTER);
                String entry = "[" + ts + "][" + streamName + "] " + line;
                second.write(entry.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
