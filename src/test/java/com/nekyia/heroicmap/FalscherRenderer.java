package com.nekyia.heroicmap;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Steht in den Tests für den Renderer: `exit N` endet mit Code N, `sleep` wartet eine Minute. */
public final class FalscherRenderer {

    public static void main(String[] args) throws InterruptedException {
        var out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        out.println("RAYON_NUM_THREADS=" + System.getenv("RAYON_NUM_THREADS"));
        out.println("Höhen:      bereit");
        System.err.println("auf stderr");
        switch (args[0]) {
            case "exit" -> System.exit(Integer.parseInt(args[1]));
            case "sleep" -> Thread.sleep(60_000);
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
