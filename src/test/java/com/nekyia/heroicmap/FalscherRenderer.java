package com.nekyia.heroicmap;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Steht in den Tests für den Renderer: gibt Zeilen wie er aus; `exit N` endet mit Code N, `nichts` meldet ein Update ohne Änderung, `sleep` wartet eine Minute. */
public final class FalscherRenderer {

    public static void main(String[] args) throws InterruptedException {
        var out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        out.println("RAYON_NUM_THREADS=" + System.getenv("RAYON_NUM_THREADS"));
        out.println("Höhen:      bereit");
        System.err.println("auf stderr");
        out.println("            200/400 Kacheln");
        switch (args[0]) {
            case "exit" -> System.exit(Integer.parseInt(args[1]));
            case "nichts" -> out.println("Update:     nichts zu zeichnen");
            case "sleep" -> Thread.sleep(60_000);
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
