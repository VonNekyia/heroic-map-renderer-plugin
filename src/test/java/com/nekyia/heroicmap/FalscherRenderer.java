package com.nekyia.heroicmap;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Steht in den Tests für den Renderer: gibt Zeilen wie er aus, den Fortschritt als JSON; `exit N` endet mit Code N, `nichts` meldet ein Update ohne Änderung, `kurz` läuft 1,5 s, `sleep` eine Minute, `server` meldet eine Adresse und läuft, bis stdin schliesst, `still` ebenso ohne Adresse, `zustimmung` bricht wie der Renderer ohne Assets und Zustimmung ab, `anderer-build` wie ein Update auf einem Stand eines anderen Builds, `banners` wie --banners, siehe {@link #banners}. */
public final class FalscherRenderer {

    /**
     * Wie --banners: je Datei der Satz oben mit Stempel und satz.json unter --out, eine Zeile je Aufruf in
     * aufrufe.txt neben --out, am Ende die Meldung. Eine Ebene mit „kaputt“ in der Kennung steht unter failed,
     * eine mit „langsam“ hält den Aufruf 1,5 s an. Ohne Gson: Der Kindprozess hat nur die Testklassen.
     */
    static void banners(PrintStream out, String[] args) throws Exception {
        var dateien = new java.util.ArrayList<Path>();
        Path ziel = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--banners")) {
                while (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    dateien.add(Path.of(args[++i]));
                }
            } else if (args[i].equals("--out")) {
                ziel = Path.of(args[++i]);
            }
        }
        Files.createDirectories(ziel);
        Files.writeString(ziel.resolveSibling(ziel.getFileName() + "-aufrufe.txt"), String.join(" ", args) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        var changed = new java.util.ArrayList<String>();
        var failed = new java.util.ArrayList<String>();
        var kennung = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
        for (Path d : dateien) {
            String text = Files.readString(d);
            var m = kennung.matcher(text);
            if (!m.find()) {
                throw new IllegalArgumentException(d + ": ohne id");
            }
            String id = m.group(1);
            if (id.contains("langsam")) {
                Thread.sleep(1_500);
            }
            if (id.contains("kaputt")) {
                System.err.println(d + ": 17 Lagen, höchstens 16");
                failed.add("\"" + id + "\"");
                continue;
            }
            Path satz = ziel.resolve(id.replace(':', '/').replaceFirst("/", "/banner/")).resolve("oben");
            Files.createDirectories(satz);
            Files.writeString(satz.resolve(".stempel"), text);
            Files.writeString(satz.resolve("satz.json"), "{\"foot\": [10, 55], \"angle\": 0.0}");
            changed.add("\"" + id + "\"");
        }
        out.println("{\"changed\": [" + String.join(", ", changed) + "], \"failed\": [" + String.join(", ", failed) + "]}");
    }

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
            case "banners" -> banners(out, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "anderer-build" -> {
                var err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
                err.println("Error: kacheln/stand.bin stammt von einem anderen Build des Renderers. Erst ein voller Lauf "
                        + "zeichnet alles mit diesem, danach geht --update wieder.");
                System.exit(1);
            }
            case "zustimmung" -> {
                var err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
                err.println("Error: ohne --assets braucht der Lauf das Client-Jar von Mojang. Der Renderer lädt das Client-Jar "
                        + "von Minecraft 26.2 (41,0 MB) von Mojangs Servern und nutzt daraus Texturen, Modelle und Biome. "
                        + "Das Jar gehört Mojang und darf nicht weitergegeben werden. Mit der Zustimmung bestätigst du, dass du "
                        + "Minecraft: Java Edition besitzt, und nimmst die Minecraft-EULA an: https://www.minecraft.net/eula");
                err.println("Zustimmen mit --download-client-jar; die Assets von Hand: docs/benutzung/assets.md");
                System.exit(1);
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
