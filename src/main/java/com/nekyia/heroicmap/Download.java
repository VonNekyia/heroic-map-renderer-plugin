package com.nekyia.heroicmap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Der Kartendownload für den Mod: Angebot, Anfrage, Grenzen, Token und der tägliche Abgleich.
 * Ohne Bukkit; den Kanal und den Speicher im Spieler übernimmt {@link Kanal}. Siehe docs/download.md.
 */
final class Download {

    static final String KANAL = "heroicmap:karte";
    static final Duration GUELTIG = Duration.ofHours(24);
    /** Ein Token für einen vollen Download kommt noch einmal, solange es mindestens so lange gilt. */
    static final Duration NOCH_GUELTIG = Duration.ofMinutes(10);
    static final int GROESSTE_ANFRAGE = 1024;

    private static final long ZEHN_MINUTEN = 600;
    private static final long WOCHE = 7 * 86_400;
    private static final long TAG = 86_400;

    /** Was das Plugin je Spieler weiss; liegt als JSON im PersistentDataContainer des Spielers. */
    static final class Spielerstand {
        long[] voll = {};
        long[] abgleich = {};
        Map<String, BaumStand> baeume = new HashMap<>();
    }

    /** Je Baum: der Massstab des Spielers, sein letzter Abgleich und die zuletzt ausgestellten Token. */
    static final class BaumStand {
        int massstab;
        long abgeglichen;
        Map<String, Ausgestellt> token = new HashMap<>();
    }

    record Ausgestellt(int massstab, long ablauf, long deckel, String token) {}

    private static final Gson GSON = new Gson();

    static String alsJson(Spielerstand s) {
        return GSON.toJson(s);
    }

    /** Der Stand aus dem PDC; null, wenn er unlesbar ist. */
    static Spielerstand ausJson(String json) {
        try {
            var s = GSON.fromJson(json, Spielerstand.class);
            if (s == null || s.voll == null || s.abgleich == null || s.baeume == null
                    || s.baeume.values().stream().anyMatch(b -> b == null || b.token == null)) {
                return null;
            }
            return s;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private final Konfiguration konf;
    private final byte[] geheimnis;
    private final Supplier<Optional<String>> webserver;
    private final Function<String, Optional<Instant>> erfolgreichSeit;
    private final ZoneId zone;
    private final SecureRandom zufall = new SecureRandom();
    private volatile Map<String, Satz> saetze = Map.of();
    private volatile Map<String, Optional<Instant>> abgedeckt = Map.of();
    private long[] serverVoll = {};

    /**
     * @param webserver die Adresse des Servers, an dem der Mod lädt; leer, solange keiner läuft
     * @param erfolgreichSeit je Baum der Beginn des letzten Laufs, der ihn auf den Stand brachte
     */
    Download(Konfiguration konf, byte[] geheimnis, Supplier<Optional<String>> webserver,
            Function<String, Optional<Instant>> erfolgreichSeit, ZoneId zone) {
        this.konf = konf;
        this.geheimnis = geheimnis.clone();
        this.webserver = webserver;
        this.erfolgreichSeit = erfolgreichSeit;
        this.zone = zone;
    }

    /**
     * Die angebotenen Bäume: nur mit {@code download: true} und mit gelesenem Manifest. Für jeden neu
     * gelesenen Satz gilt der Beginn des Laufs, der ihn schrieb; so gehören Manifest und
     * {@code abdeckt_bis} zusammen. Siehe docs/download.md, „Was die Kacheln abdecken“.
     */
    synchronized void saetze(Map<String, Satz> neu) {
        var alt = saetze;
        var ab = new HashMap<String, Optional<Instant>>();
        for (var e : neu.entrySet()) {
            ab.put(e.getKey(), e.getValue() == alt.get(e.getKey())
                    ? abgedeckt.getOrDefault(e.getKey(), Optional.empty())
                    : erfolgreichSeit.apply(e.getKey()));
        }
        abgedeckt = Map.copyOf(ab);
        saetze = Map.copyOf(neu);
    }

    List<Konfiguration.Baum> angeboteneBaeume() {
        return konf.baeume().stream().filter(Konfiguration.Baum::download).toList();
    }

    JsonObject angebot(Instant jetzt) {
        var baeume = new JsonArray();
        for (var b : angeboteneBaeume()) {
            Satz satz = saetze.get(b.ordner());
            if (satz == null) {
                continue;
            }
            var o = new JsonObject();
            o.addProperty("id", b.ordner());
            o.addProperty("name", b.ordner());
            o.addProperty("dimension", konf.dimension());
            o.addProperty("stand", satz.stand());
            abdecktBis(b.ordner()).ifPresent(t -> o.addProperty("abdeckt_bis", t));
            var massstaebe = new JsonObject();
            for (int m : new int[] {1, 2, 4}) {
                int stufe = satz.stufe(m);
                if (stufe >= 0) {
                    var g = new JsonObject();
                    g.addProperty("bytes", satz.bytesBis(stufe));
                    g.addProperty("kacheln", satz.kachelnBis(stufe));
                    massstaebe.add(Integer.toString(m), g);
                }
            }
            o.add("massstaebe", massstaebe);
            baeume.add(o);
        }
        var a = nachricht("angebot", jetzt);
        a.add("baeume", baeume);
        return a;
    }

    /**
     * Beantwortet eine Anfrage des Mods; ändert dabei den Stand des Spielers.
     * Siehe docs/download.md, „Anfrage“.
     */
    JsonObject anfrage(byte[] daten, UUID spieler, Spielerstand stand, Instant jetzt) {
        String baum;
        String art;
        int massstab;
        try {
            if (daten.length > GROESSTE_ANFRAGE) {
                throw new IllegalArgumentException("zu gross");
            }
            var a = JsonParser.parseString(new String(daten, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!a.get("v").getAsString().equals("1") || !a.get("typ").getAsString().equals("anfrage")) {
                throw new IllegalArgumentException("v oder typ");
            }
            baum = a.get("baum").getAsString();
            art = a.get("art").getAsString();
            massstab = switch (a.get("massstab").getAsString()) {
                case "1" -> 1;
                case "2" -> 2;
                case "4" -> 4;
                default -> throw new IllegalArgumentException("massstab");
            };
            if (!art.equals("voll") && !art.equals("abgleich")) {
                throw new IllegalArgumentException("art");
            }
        } catch (RuntimeException e) {
            return abgelehnt(jetzt, "Die Anfrage ist nicht lesbar.", null);
        }

        Satz satz = saetze.get(baum);
        if (satz == null) {
            return abgelehnt(jetzt, "Diese Karte wird hier nicht zum Download angeboten.", null);
        }
        int stufe = satz.stufe(massstab);
        if (stufe < 0) {
            return abgelehnt(jetzt, "Diesen Massstab gibt es für diese Karte nicht.", null);
        }
        Optional<String> url = webserver.get();
        if (url.isEmpty()) {
            return abgelehnt(jetzt, "Webserver aus.", null);
        }

        long s = jetzt.getEpochSecond();
        var bs = stand.baeume.get(baum);
        // Ein anderer Massstab ist ein neuer Satz, also ein voller Download.
        boolean voll = art.equals("voll") || bs == null || bs.massstab != massstab;
        String wirklich = voll ? "voll" : "abgleich";

        // Fortsetzen zählt nicht: voll, solange das Token noch 10 min gilt; ein Abgleich, solange es jünger
        // als 10 min ist. Siehe docs/download.md, „Anfrage“.
        var alt = bs == null ? null : bs.token.get(wirklich);
        if (alt != null && alt.massstab() == massstab && (voll
                ? alt.ablauf() - s >= NOCH_GUELTIG.toSeconds()
                : s - (alt.ablauf() - GUELTIG.toSeconds()) < NOCH_GUELTIG.toSeconds())) {
            return freigabe(jetzt, baum, massstab, wirklich, url.get(), alt, satz, stufe);
        }

        var d = konf.download();
        if (voll) {
            if (d.vollJeWoche() == 0 || d.vollJe10Min() == 0) {
                return abgelehnt(jetzt, "Volle Downloads sind auf diesem Server abgeschaltet.", null);
            }
            long[] spielerNeu = mitNeuer(stand.voll, s, WOCHE, d.vollJeWoche());
            if (spielerNeu == null) {
                return abgelehnt(jetzt, "Du hast in den letzten 7 Tagen schon " + d.vollJeWoche()
                        + " volle Downloads geholt.", wiederAb(stand.voll, s, WOCHE, d.vollJeWoche()));
            }
            synchronized (this) {
                long[] serverNeu = mitNeuer(serverVoll, s, ZEHN_MINUTEN, d.vollJe10Min());
                if (serverNeu == null) {
                    return abgelehnt(jetzt, "Der Server hat in den letzten 10 Minuten schon " + d.vollJe10Min()
                            + " volle Downloads ausgegeben.", wiederAb(serverVoll, s, ZEHN_MINUTEN, d.vollJe10Min()));
                }
                serverVoll = serverNeu;
            }
            stand.voll = spielerNeu;
        } else {
            if (d.abgleichJeTag() == 0) {
                return abgelehnt(jetzt, "Abgleiche von Hand sind auf diesem Server abgeschaltet.", null);
            }
            long[] neu = mitNeuer(stand.abgleich, s, TAG, d.abgleichJeTag());
            if (neu == null) {
                return abgelehnt(jetzt, "Du hast in den letzten 24 Stunden schon " + d.abgleichJeTag()
                        + " Abgleiche geholt.", wiederAb(stand.abgleich, s, TAG, d.abgleichJeTag()));
            }
            stand.abgleich = neu;
        }
        var neu = stelleAus(spieler, s, baum, satz, massstab, voll);
        if (bs == null) {
            bs = new BaumStand();
            stand.baeume.put(baum, bs);
        }
        bs.massstab = massstab;
        bs.abgeglichen = s;
        bs.token.put(wirklich, neu);
        return freigabe(jetzt, baum, massstab, wirklich, url.get(), neu, satz, stufe);
    }

    /**
     * Der tägliche Abgleich beim ersten Join nach der Uhrzeit aus der Konfiguration: je Baum, den der
     * Spieler schon hat, eine Freigabe, ohne Grenze. Siehe docs/download.md, „Täglicher Abgleich“.
     */
    List<JsonObject> beimJoin(UUID spieler, Spielerstand stand, Instant jetzt) {
        Optional<String> url = webserver.get();
        if (url.isEmpty()) {
            return List.of();
        }
        long grenze = letzteGrenze(jetzt);
        long s = jetzt.getEpochSecond();
        List<JsonObject> raus = new ArrayList<>();
        for (var b : angeboteneBaeume()) {
            var bs = stand.baeume.get(b.ordner());
            Satz satz = saetze.get(b.ordner());
            if (bs == null || satz == null || bs.abgeglichen >= grenze || !List.of(1, 2, 4).contains(bs.massstab)
                    || satz.stufe(bs.massstab) < 0) {
                continue;
            }
            int stufe = satz.stufe(bs.massstab);
            var neu = stelleAus(spieler, s, b.ordner(), satz, bs.massstab, false);
            bs.abgeglichen = s;
            bs.token.put("abgleich", neu);
            raus.add(freigabe(jetzt, b.ordner(), bs.massstab, "abgleich", url.get(), neu, satz, stufe));
        }
        return raus;
    }

    /** Der letzte Zeitpunkt der Uhrzeit aus der Konfiguration, heute oder gestern, in Epoch s. */
    long letzteGrenze(Instant jetzt) {
        var heute = ZonedDateTime.ofInstant(jetzt, zone).with(konf.download().abgleichAb());
        if (heute.toInstant().isAfter(jetzt)) {
            heute = heute.minusDays(1);
        }
        return heute.toEpochSecond();
    }

    /** Voll: Deckel das 1,5-Fache des Satzes; Abgleich: 10 %. Siehe docs/download.md, „Token“. */
    private Ausgestellt stelleAus(UUID spieler, long s, String baum, Satz satz, int massstab, boolean voll) {
        int stufe = satz.stufe(massstab);
        long bytes = satz.bytesBis(stufe);
        long deckel = voll ? bytes + bytes / 2 : bytes / 10;
        long ablauf = s + GUELTIG.toSeconds();
        byte[] z = new byte[Token.ZUFALL];
        zufall.nextBytes(z);
        return new Ausgestellt(massstab, ablauf, deckel, Token.stelleAus(geheimnis, spieler, ablauf, deckel, stufe, z, baum));
    }

    private JsonObject freigabe(Instant jetzt, String baum, int massstab, String art, String url, Ausgestellt t,
            Satz satz, int stufe) {
        var f = nachricht("freigabe", jetzt);
        f.addProperty("baum", baum);
        f.addProperty("massstab", massstab);
        f.addProperty("art", art);
        abdecktBis(baum).ifPresent(a -> f.addProperty("abdeckt_bis", a));
        f.addProperty("url", url + "/" + baum);
        f.addProperty("token", t.token());
        f.addProperty("ablauf", t.ablauf());
        f.addProperty("manifest_sha256", satz.sha256());
        f.addProperty("bytes", art.equals("voll") ? satz.bytesBis(stufe) : t.deckel());
        return f;
    }

    private Optional<Long> abdecktBis(String baum) {
        long reserve = Duration.ofMinutes(konf.download().reserveMinuten()).toSeconds();
        return abgedeckt.getOrDefault(baum, Optional.empty()).map(i -> i.getEpochSecond() - reserve);
    }

    private static JsonObject abgelehnt(Instant jetzt, String grund, Long wieder) {
        var a = nachricht("abgelehnt", jetzt);
        a.addProperty("grund", grund);
        if (wieder != null) {
            a.addProperty("wieder", wieder);
        }
        return a;
    }

    private static JsonObject nachricht(String typ, Instant jetzt) {
        var o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("typ", typ);
        o.addProperty("jetzt", jetzt.getEpochSecond());
        return o;
    }

    /**
     * Die Zeiten im Fenster vor {@code jetzt} samt {@code jetzt}, älteste zuerst; null, wenn schon
     * {@code hoechstens} im Fenster liegen.
     */
    static long[] mitNeuer(long[] zeiten, long jetzt, long fenster, int hoechstens) {
        long[] drin = Arrays.stream(zeiten).filter(t -> t > jetzt - fenster).sorted().toArray();
        if (drin.length >= hoechstens) {
            return null;
        }
        long[] neu = Arrays.copyOf(drin, drin.length + 1);
        neu[drin.length] = jetzt;
        return neu;
    }

    /**
     * Wann im Fenster wieder Platz ist: wenn so viele Zeiten herausgefallen sind, dass weniger als
     * {@code hoechstens} übrig bleiben. Nur für {@code hoechstens} ab 1 und ein volles Fenster.
     */
    static long wiederAb(long[] zeiten, long jetzt, long fenster, int hoechstens) {
        long[] drin = Arrays.stream(zeiten).filter(t -> t > jetzt - fenster).sorted().toArray();
        return drin[drin.length - hoechstens] + fenster;
    }

    /**
     * Das Geheimnis für die Token: 32 Byte in der Datei, beim ersten Start erzeugt, unter POSIX gleich
     * nur für den Besitzer. Siehe docs/download.md, „Token“.
     */
    static byte[] geheimnis(Path datei, Logger log) throws IOException {
        if (Files.exists(datei)) {
            byte[] g = Files.readAllBytes(datei);
            if (g.length == Token.GEHEIMNIS) {
                return g;
            }
            log.warning(datei + " hat " + g.length + " statt " + Token.GEHEIMNIS
                    + " Byte; ein neues Geheimnis, ältere Token gelten nicht mehr");
        }
        byte[] g = new byte[Token.GEHEIMNIS];
        new SecureRandom().nextBytes(g);
        Files.createDirectories(datei.getParent());
        Path neu = datei.resolveSibling(datei.getFileName() + ".neu");
        Files.deleteIfExists(neu);
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(neu, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } else {
            // Windows kennt keine POSIX-Rechte; die Datei liegt im Ordner des Plugins.
            Files.createFile(neu);
        }
        Files.write(neu, g);
        Files.move(neu, datei, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return g;
    }
}
