package com.nekyia.heroicmap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.nekyia.heroicmap.Ebenen.Ebene;
import com.nekyia.heroicmap.EbenenPruefung.Bild;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * Die öffentlichen Ebenen der Karte eines anderen Servers, über HTTP von dessen Webserver: layers.json mit ETag, dann
 * jede Ebene, deren version neu ist, mit ihren Bildern, geprüft wie eine Datei. Nur im Speicher; {@link #hole} nie
 * nebenläufig. Siehe docs/mehrere-server.md, „Karte von“.
 */
final class KarteVon {

    private static final Duration FRIST = Duration.ofSeconds(10);
    /** So lange nach dem Start ist ein Fehler eine INFO: Der andere Server startet vielleicht noch. */
    static final Duration ANLAUF = Duration.ofSeconds(60);

    private final String tiles;
    private final String dimension;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(FRIST)
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private String etag;
    /** Je Kennung die version aus layers.json dort und die Ebene, wie sie hier gilt. */
    private Map<String, String> versionDort = Map.of();
    private volatile List<Ebene> stand = List.of();
    private final Instant start;
    /** Die Fehler, die zuletzt im Log standen, und ob als INFO; nur in einem Faden. */
    private List<String> gemeldet = List.of();
    private boolean alsInfo;

    /** {@code url} wie webserver.url des anderen Servers, ohne / am Ende; {@code dimension} die von tiles hier. */
    KarteVon(String url, String dimension) {
        this(url, dimension, Instant.now());
    }

    /** Wie oben, mit dem Start, ab dem {@link #ANLAUF} zählt. */
    KarteVon(String url, String dimension, Instant start) {
        this.tiles = url + "/tiles";
        this.dimension = dimension;
        this.start = start;
    }

    /**
     * Mit welcher Stufe die Fehler eines Holens ins Log kommen, oder null, wenn nichts neu ist: nur, wenn sie sich
     * ändern; im {@link #ANLAUF} als INFO, danach als WARNING. Ein Fehler, der den Anlauf überdauert, kommt dann
     * einmal als WARNING; keine Fehler mehr nach welchen als INFO, für die Zeile mit der Zahl. Siehe
     * docs/mehrere-server.md, „Karte von“.
     */
    Level meldung(List<String> fehler, Instant jetzt) {
        boolean anlauf = Duration.between(start, jetzt).compareTo(ANLAUF) < 0;
        if (fehler.equals(gemeldet) && !(alsInfo && !anlauf && !fehler.isEmpty())) {
            return null;
        }
        gemeldet = List.copyOf(fehler);
        alsInfo = anlauf && !fehler.isEmpty();
        return anlauf || fehler.isEmpty() ? Level.INFO : Level.WARNING;
    }

    /** Die zuletzt geholten Ebenen, sortiert wie layers.json dort. */
    List<Ebene> stand() {
        return stand;
    }

    /**
     * Holt layers.json, und ist sie neu, jede Ebene, deren version sich dort änderte. Eine Ebene, die nicht zu holen
     * oder ungültig ist, fehlt, mit einer Zeile in {@code fehler}; dann fragt der nächste Aufruf wieder ohne ETag.
     * Wirft, wenn layers.json nicht zu holen ist; dann gilt der alte Stand. True, wenn sich der Stand änderte.
     */
    boolean hole(List<String> fehler) throws IOException, InterruptedException {
        var anfrage = HttpRequest.newBuilder(URI.create(tiles + "/layers.json")).timeout(FRIST);
        if (etag != null) {
            anfrage.header("If-None-Match", etag);
        }
        var antwort = http.send(anfrage.build(), HttpResponse.BodyHandlers.ofInputStream());
        JsonElement liste;
        try (InputStream rumpf = antwort.body()) {
            if (antwort.statusCode() == 304) {
                return false;
            }
            if (antwort.statusCode() != 200) {
                throw new IOException("layers.json: HTTP " + antwort.statusCode());
            }
            liste = Ebenen.lies(new String(lies(rumpf, EbenenPruefung.EBENE_BYTES), StandardCharsets.UTF_8)).get("layers");
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("layers.json: kein gültiges JSON: " + e.getMessage());
        }
        if (liste == null || !liste.isJsonArray()) {
            throw new IOException("layers.json: ohne Liste layers");
        }
        var alt = new HashMap<String, Ebene>();
        stand.forEach(e -> alt.put(e.id(), e));
        var neu = new ArrayList<Ebene>();
        var versionen = new TreeMap<String, String>();
        int vorher = fehler.size();
        for (JsonElement x : liste.getAsJsonArray()) {
            if (neu.size() == Ebenen.HOECHSTENS) {
                break;
            }
            var eintrag = x.isJsonObject() ? x.getAsJsonObject() : new JsonObject();
            // Aktionsebenen gibt es dort nur je Spieler.
            if (eintrag.has("player")) {
                continue;
            }
            String id = text(eintrag, "id");
            String version = text(eintrag, "version");
            int doppelpunkt = id == null ? -1 : id.indexOf(':');
            if (doppelpunkt < 0 || !EbenenPruefung.teil(id.substring(0, doppelpunkt))
                    || !EbenenPruefung.teil(id.substring(doppelpunkt + 1)) || version == null) {
                fehler.add("layers.json: ein Eintrag ohne gültige id oder version: " + eintrag);
                continue;
            }
            Ebene e = version.equals(versionDort.get(id)) ? alt.get(id) : ebene(id, fehler);
            if (e != null) {
                neu.add(e);
                versionen.put(id, version);
            }
        }
        // Ohne Fehler gilt der ETag; sonst holt der nächste Aufruf alles wieder, was fehlt.
        etag = fehler.size() == vorher ? antwort.headers().firstValue("ETag").orElse(null) : null;
        versionDort = versionen;
        boolean anders = !neu.stream().map(Ebene::version).toList().equals(stand.stream().map(Ebene::version).toList())
                || !neu.stream().map(Ebene::id).toList().equals(stand.stream().map(Ebene::id).toList());
        stand = List.copyOf(neu);
        return anders;
    }

    /** Eine Ebene dort samt ihren Bildern, geprüft wie eine Datei; null mit einer Zeile in {@code fehler}. */
    private Ebene ebene(String id, List<String> fehler) throws InterruptedException {
        String modname = id.substring(0, id.indexOf(':'));
        String stelle = modname + "/" + id.substring(modname.length() + 1) + ".json";
        try {
            byte[] roh = hole(tiles + "/layers/" + stelle, EbenenPruefung.EBENE_BYTES);
            if (roh == null) {
                fehler.add(stelle + ": fehlt");
                return null;
            }
            JsonObject json = Ebenen.lies(new String(roh, StandardCharsets.UTF_8));
            var gelesen = new HashMap<String, Bild>();
            var e = EbenenPruefung.pruefe(id, json, pfad -> gelesen.computeIfAbsent(pfad, p -> bild(modname, p)));
            if (!e.fehler().isEmpty()) {
                fehler.add(stelle + ": " + e.fehler().getFirst()
                        + (e.fehler().size() > 1 ? " und " + (e.fehler().size() - 1) + " weitere Fehler" : ""));
                return null;
            }
            var benutzt = new TreeMap<String, byte[]>();
            e.bilder().forEach(b -> benutzt.put(b, gelesen.get(b).daten()));
            return new Ebene(id, json, benutzt, Ebenen.version(json, benutzt, dimension));
        } catch (IOException e) {
            fehler.add(stelle + ": nicht geholt: " + e.getMessage());
        } catch (JsonParseException | IllegalStateException e) {
            fehler.add(stelle + ": kein gültiges JSON: " + e.getMessage());
        }
        return null;
    }

    /** Ein Bild dort; null, wenn es fehlt. */
    private Bild bild(String modname, String pfad) {
        try {
            byte[] b = hole(tiles + "/layers/" + modname + "/" + pfad, EbenenPruefung.BILD_BYTES);
            return b == null ? null : new Bild(b, null);
        } catch (IOException e) {
            return new Bild(null, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Bild(null, "abgebrochen");
        }
    }

    /** Der Körper einer Antwort 200, höchstens {@code max} Byte; null bei 404. */
    private byte[] hole(String url, int max) throws IOException, InterruptedException {
        var antwort = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(FRIST).build(),
                HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream rumpf = antwort.body()) {
            if (antwort.statusCode() == 404) {
                return null;
            }
            if (antwort.statusCode() != 200) {
                throw new IOException("HTTP " + antwort.statusCode());
            }
            return lies(rumpf, max);
        }
    }

    private static byte[] lies(InputStream rumpf, int max) throws IOException {
        byte[] b = rumpf.readNBytes(max + 1);
        if (b.length > max) {
            throw new IOException("grösser als " + (max >> 10) + " KiB");
        }
        return b;
    }

    private static String text(JsonObject o, String feld) {
        var x = o.get(feld);
        return x != null && x.isJsonPrimitive() && x.getAsJsonPrimitive().isString() ? x.getAsString() : null;
    }
}
