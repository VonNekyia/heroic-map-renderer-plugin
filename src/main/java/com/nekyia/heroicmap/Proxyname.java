package com.nekyia.heroicmap;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Der Name dieses Servers hinter einem Proxy: die Frage GetServer auf dem Kanal BungeeCord und ihre Antwort. Ohne
 * laufenden Server; nur im Hauptthread. Siehe docs/mehrere-server.md, „Name vom Proxy“.
 */
final class Proxyname {

    /** Der Kanal, wie Paper ihn führt; der Proxy meldet ihn als bungeecord:main an. */
    static final String KANAL = "BungeeCord";
    /** So lange wartet die erste Antwort auf show auf den Namen vom Proxy. */
    static final long FRIST_MS = 2000;
    /** So oft fragt das Plugin je Laufzeit, wenn keine Antwort kam. */
    static final int VERSUCHE = 3;
    /**
     * So lange nach dem ersten Join ohne den Kanal wartet die Zeile „kein Proxy“: Das Register des Proxys kommt
     * manchmal erst kurz nach dem Join, die Antwort gleich danach.
     */
    static final long KANAL_FRIST_MS = 10_000;
    /** Ein Name wie ein Teil einer Kennung, klein; so taugt er im Mod als Ordner. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,64}");

    private final boolean hinterProxy;
    private String name;
    private UUID ueber;
    private long gefragtUm;
    private int versuche;
    /** Wann ein Spieler zuerst ohne den Kanal kam, in ms; -1 vorher. */
    private long ohneKanalSeit = -1;
    private boolean ohneProxyGemeldet;

    /** @param hinterProxy aus {@link #hinterProxy(Path)}; ohne fragt es nie und nimmt keine Antwort an. */
    Proxyname(boolean hinterProxy) {
        this.hinterProxy = hinterProxy;
    }

    /**
     * Ob Paper sicher hinter einem Proxy läuft: Weiterleitung von Velocity an ({@code proxies.velocity.enabled} in
     * {@code config/paper-global.yml}) oder BungeeCord-Modus ({@code settings.bungeecord} in {@code spigot.yml}). Nur
     * dann kommt jeder Spieler über den Proxy, und der verwirft GetServer von Clients. Fehlt eine Datei, gilt sie als
     * aus.
     */
    static boolean hinterProxy(Path server) {
        return YamlConfiguration.loadConfiguration(server.resolve("config/paper-global.yml").toFile())
                .getBoolean("proxies.velocity.enabled")
                || YamlConfiguration.loadConfiguration(server.resolve("spigot.yml").toFile()).getBoolean("settings.bungeecord");
    }

    /** Die Frage GetServer, wie der Proxy sie liest: ein String nach DataOutput.writeUTF. */
    static byte[] frage() {
        var b = new ByteArrayOutputStream();
        try (var o = new DataOutputStream(b)) {
            o.writeUTF("GetServer");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return b.toByteArray();
    }

    /** {@code name} klein, wenn er als Name einer Karte taugt; sonst null. */
    static String gueltig(String name) {
        if (name == null) {
            return null;
        }
        String klein = name.strip().toLowerCase(Locale.ROOT);
        return NAME.matcher(klein).matches() ? klein : null;
    }

    /**
     * Ob jetzt über {@code spieler} gefragt werden soll: hinter einem Proxy, noch kein Name, keine Frage offen,
     * höchstens {@link #VERSUCHE} Mal. Wenn ja, gilt die Frage als gestellt.
     */
    boolean frage(UUID spieler, long ms) {
        if (!hinterProxy || name != null || versuche >= VERSUCHE || ueber != null && ms - gefragtUm < FRIST_MS) {
            return false;
        }
        ueber = spieler;
        gefragtUm = ms;
        versuche++;
        return true;
    }

    /**
     * Nimmt die Antwort an: nur nach einer Frage, über den Spieler, über den gefragt wurde, GetServer mit einem Namen,
     * der taugt; ohne Proxy gab es keine. True, wenn der Name jetzt gilt. Velocity und BungeeCord verwerfen solche
     * Nachrichten von Clients; sie kommen nur vom Proxy.
     */
    boolean antwort(UUID spieler, byte[] nachricht) {
        if (ueber == null || !ueber.equals(spieler) || name != null) {
            return false;
        }
        try (var i = new DataInputStream(new ByteArrayInputStream(nachricht))) {
            if (!i.readUTF().equals("GetServer")) {
                return false;
            }
            name = gueltig(i.readUTF());
        } catch (IOException e) {
            return false;
        }
        return name != null;
    }

    /** Ob die Antwort auf show noch auf den Namen wartet: gefragt, keine Antwort, die Frist läuft. */
    boolean wartet(long ms) {
        return name == null && ueber != null && ms - gefragtUm < FRIST_MS;
    }

    /**
     * Ein Spieler kam ohne den Kanal. True nur beim ersten Mal; dann fragt der Aufrufer nach {@link #KANAL_FRIST_MS}
     * mit {@link #ohneProxy}, ob die Zeile „kein Proxy“ ins Log gehört.
     */
    boolean ohneKanal(long ms) {
        if (ohneKanalSeit >= 0) {
            return false;
        }
        ohneKanalSeit = ms;
        return true;
    }

    /**
     * Gehört „kein Proxy“ jetzt ins Log? Nur einmal, erst {@link #KANAL_FRIST_MS} nach dem ersten Spieler ohne den
     * Kanal, und nur, wenn bis dahin kein Name vom Proxy kam. Siehe docs/mehrere-server.md, „Name vom Proxy“.
     */
    boolean ohneProxy(long ms) {
        if (ohneProxyGemeldet || name != null || ohneKanalSeit < 0 || ms - ohneKanalSeit < KANAL_FRIST_MS) {
            return false;
        }
        ohneProxyGemeldet = true;
        return true;
    }

    /** Ob Paper sicher hinter einem Proxy läuft, siehe {@link #hinterProxy(Path)}. */
    boolean hinterProxy() {
        return hinterProxy;
    }

    /** Der Name vom Proxy, oder null. */
    String name() {
        return name;
    }

    /**
     * Der Name der Karte für den Mod: karte.von, sonst {@code vomProxy}, sonst karte.name; null ohne. Siehe
     * docs/mehrere-server.md, „Format“.
     */
    static String karte(Konfiguration.Karte k, String vomProxy) {
        return !k.von().isEmpty() ? k.von() : backend(k, vomProxy);
    }

    /**
     * Der Name dieses Servers selbst: {@code vomProxy}, sonst karte.name; null ohne. Anders als {@link #karte} nie
     * karte.von. Siehe docs/mehrere-server.md, „Format“.
     */
    static String backend(Konfiguration.Karte k, String vomProxy) {
        return vomProxy != null ? vomProxy : k.name().isEmpty() ? null : k.name();
    }

    /** Setzt karte und backend an die Antwort auf show, soweit es sie gibt. */
    static void nenne(JsonObject antwort, Konfiguration.Karte k, String vomProxy) {
        String karte = karte(k, vomProxy);
        if (karte != null) {
            antwort.addProperty("karte", karte);
        }
        String backend = backend(k, vomProxy);
        if (backend != null) {
            antwort.addProperty("backend", backend);
        }
    }

    /** Die Zeile im Log, wenn kein Proxy den Kanal meldet; ohne karte.name mit der Folge im Mod. */
    static String ohneProxyZeile(Konfiguration.Karte k) {
        return false
                ? "Kein Proxy meldet den Kanal BungeeCord, und karte.name ist leer: Ohne Namen hält der Mod diesen Server "
                        + "nicht von anderen mit derselben Welt hinter derselben Adresse auseinander."
                : "Kein Proxy meldet den Kanal BungeeCord; der Name dieses Servers kommt aus karte.name.";
    }
}
