package com.nekyia.heroicmap;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Steht in den Tests für den Renderer: gibt Zeilen wie er aus, den Fortschritt als JSON; `exit N` endet mit Code N, `nichts` meldet ein Update ohne Änderung, `kurz` läuft 1,5 s, `sleep` eine Minute, `server` meldet eine Adresse und läuft, bis stdin schliesst, `still` ebenso ohne Adresse. */
public final class FalscherRenderer {

    public static void main(String[] args) throws Exception {
        var out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        out.println("RAYON_NUM_THREADS=" + System.getenv("RAYON_NUM_THREADS"));
        out.println("Höhen:      bereit");
        System.err.println("auf stderr");
        out.println("{\"phase\":\"base\",\"tiles\":200,\"of\":400,\"rate\":12.5,\"eta_s\":16}");
        switch (args[0]) {
            case "exit" -> System.exit(Integer.parseInt(args[1]));
            case "nichts" -> out.println("Update:     nichts zu zeichnen");
            case "sleep" -> Thread.sleep(60_000);
            case "kurz" -> Thread.sleep(1_500);
            case "server" -> {
                out.println("Server:     http://127.0.0.1:1234 mit kacheln unter /tiles/, 1 Threads");
                out.println("Server:     Zertifikat neu geladen");
                System.in.readAllBytes();
            }
            case "still" -> System.in.readAllBytes();
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
