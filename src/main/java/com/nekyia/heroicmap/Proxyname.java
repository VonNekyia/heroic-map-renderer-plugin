package com.nekyia.heroicmap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Der Name dieses Servers hinter einem Proxy: die Frage GetServer auf dem Kanal BungeeCord und ihre Antwort. Ohne
 * Bukkit; nur im Hauptthread. Siehe docs/mehrere-server.md, „Name vom Proxy“.
 */
final class Proxyname {

    /** Der Kanal, wie Paper ihn führt; der Proxy meldet ihn als bungeecord:main an. */
    static final String KANAL = "BungeeCord";
    /** So lange wartet die erste Antwort auf show auf den Namen vom Proxy. */
    static final long FRIST_MS = 2000;
    /** So oft fragt das Plugin je Laufzeit, wenn keine Antwort kam. */
    static final int VERSUCHE = 3;
    /** Ein Name wie ein Teil einer Kennung, klein; so taugt er im Mod als Ordner. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,64}");

    private String name;
    private UUID ueber;
    private long gefragtUm;
    private int versuche;

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
     * Ob jetzt über {@code spieler} gefragt werden soll: noch kein Name, keine Frage offen, höchstens {@link #VERSUCHE}
     * Mal. Wenn ja, gilt die Frage als gestellt.
     */
    boolean frage(UUID spieler, long ms) {
        if (name != null || versuche >= VERSUCHE || ueber != null && ms - gefragtUm < FRIST_MS) {
            return false;
        }
        ueber = spieler;
        gefragtUm = ms;
        versuche++;
        return true;
    }

    /**
     * Nimmt die Antwort an: nur nach einer Frage, über den Spieler, über den gefragt wurde, GetServer mit einem Namen,
     * der taugt. True, wenn der Name jetzt gilt. Velocity verwirft solche Nachrichten von Clients; sie kommen nur vom
     * Proxy.
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

    /** Der Name vom Proxy, oder null. */
    String name() {
        return name;
    }

    /**
     * Der Name der Karte für den Mod: mit karte.von deren, sonst {@code vomProxy}, sonst karte.name; null ohne. Siehe
     * docs/mehrere-server.md, „Format“.
     */
    static String karte(Konfiguration.Karte k, String vomProxy) {
        if (k.fremd()) {
            return k.von();
        }
        return vomProxy != null ? vomProxy : k.name().isEmpty() ? null : k.name();
    }
}
