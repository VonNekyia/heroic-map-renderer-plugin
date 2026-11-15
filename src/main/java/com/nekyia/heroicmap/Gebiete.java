package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.nekyia.heroicmap.api.Quality;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Die Gebiete mit eigener Qualität: Regionen der API mit einer Stufe, als Datei für den Renderer. Die Datei ist
 * zugleich das Gedächtnis über einen Neustart; ein Gebiet fällt erst nach einer Frist weg. Siehe docs/qualitaet.md.
 */
final class Gebiete {

    /** Eine Region mit Stufe in ihrer Ebene: ihre id und ihre Vielecke wie im Format der Ebene. */
    record Gebiet(String id, Quality stufe, JsonArray vielecke) {}

    /** So lange muss ein Gebiet fehlen, bevor es wegfällt. Siehe docs/qualitaet.md, „Gedächtnis“. */
    static final Duration FRIST = Duration.ofMinutes(10);
    /** Grösser liest der Renderer die Datei nicht. */
    static final long HOECHSTENS = 64L << 20;

    private final Path datei;
    private final long hoechstens;
    private final Logger log;
    /** Die Gebiete der Datei je Kennung {@code <ebene>/<id der Region>}; null vor dem ersten Lesen. */
    private TreeMap<String, JsonObject> stand;
    /** Was in der Datei liegt; null, wenn sie fehlt oder nicht zu lesen war. */
    private String inhalt;
    /** Seit wann ein Gebiet der Datei fehlt, das seine Ebene nicht mehr nennt. */
    private final Map<String, Instant> fehltSeit = new HashMap<>();
    /** Die Ebenen, die es in dieser Sitzung gab. */
    private final Set<String> gesehen = new HashSet<>();
    private Map<String, List<Gebiet>> letzteEbenen;
    private Set<String> letzteInstalliert;
    private boolean zuGross;
    private boolean scheiterte;
    private volatile String status;

    Gebiete(Path datei, Logger log) {
        this(datei, HOECHSTENS, log);
    }

    /** Mit einer eigenen Grenze der Datei in Bytes, für die Tests. */
    Gebiete(Path datei, long hoechstens, Logger log) {
        this.datei = datei;
        this.hoechstens = hoechstens;
        this.log = log;
    }

    Path datei() {
        return datei;
    }

    /**
     * Bringt die Datei auf den Stand der Ebenen. {@code ebenen} nennt jede Ebene der API, die es gerade gibt, mit
     * ihren Gebieten; {@code installiert} die modname aller Plugins, null, solange der Server noch lädt.
     * Neues und Geändertes gilt sofort. Was fehlt, fällt verzögert weg, siehe docs/qualitaet.md, „Gedächtnis“.
     */
    synchronized void pruefe(Map<String, List<Gebiet>> ebenen, Set<String> installiert, Instant jetzt) {
        if (stand == null) {
            lies();
        }
        // Gleiche Listen sind dieselben Listen, siehe EbenenApi#gebiete; der Vergleich kostet dann nichts.
        if (fehltSeit.isEmpty() && ebenen.equals(letzteEbenen) && Objects.equals(installiert, letzteInstalliert)) {
            return;
        }
        letzteEbenen = ebenen;
        letzteInstalliert = installiert;
        gesehen.addAll(ebenen.keySet());
        var neu = new TreeMap<String, JsonObject>();
        ebenen.forEach((ebene, liste) -> liste.forEach(g -> neu.put(ebene + "/" + g.id(), json(ebene + "/" + g.id(), g))));
        var ohnePlugin = new TreeMap<String, Integer>();
        for (var alt : stand.entrySet()) {
            String kennung = alt.getKey();
            if (neu.containsKey(kennung)) {
                fehltSeit.remove(kennung);
                continue;
            }
            String ebene = ebene(kennung);
            if (gesehen.contains(ebene)) {
                // Die Ebene gab es in dieser Sitzung: Erst wer bei einer Prüfung nach der Frist noch fehlt, fällt weg.
                Instant seit = fehltSeit.putIfAbsent(kennung, jetzt);
                if (seit == null || Duration.between(seit, jetzt).compareTo(FRIST) < 0) {
                    neu.put(kennung, alt.getValue());
                } else {
                    fehltSeit.remove(kennung);
                }
            } else if (installiert == null || installiert.contains(ebene.substring(0, ebene.indexOf(':')))) {
                // Ihr Plugin hat die Ebene noch nicht angelegt: Es gilt, was der letzte Lauf sah.
                neu.put(kennung, alt.getValue());
            } else {
                ohnePlugin.merge(ebene.substring(0, ebene.indexOf(':')), 1, Integer::sum);
            }
        }
        ohnePlugin.forEach((modname, zahl) -> log.info("Qualität: " + modname
                + " ist nicht mehr installiert, seine Gebiete fallen weg: " + zahl));
        schreibe(neu);
        status = status();
    }

    /** Die Ebene {@code <modname>:<name>} einer Kennung; weder modname noch Name enthalten einen Schrägstrich. */
    private static String ebene(String kennung) {
        return kennung.substring(0, kennung.indexOf('/'));
    }

    /** Ein Gebiet, wie es in der Datei steht; {@code holes} immer, auch leer. */
    private static JsonObject json(String kennung, Gebiet g) {
        var o = new JsonObject();
        o.addProperty("id", kennung);
        o.addProperty("quality", g.stufe().name().toLowerCase(Locale.ROOT));
        var vielecke = new JsonArray();
        for (JsonElement v : g.vielecke()) {
            var alt = v.getAsJsonObject();
            var p = new JsonObject();
            p.add("outer", alt.get("outer"));
            p.add("holes", alt.has("holes") ? alt.get("holes") : new JsonArray());
            vielecke.add(p);
        }
        o.add("polygons", vielecke);
        return o;
    }

    /** Die Datei als Gedächtnis. Fehlt sie, gibt es keine Gebiete; ist sie nicht zu lesen, ersetzt sie die nächste. */
    private void lies() {
        stand = new TreeMap<>();
        if (!Files.exists(datei)) {
            return;
        }
        try {
            String text = Files.readString(datei, StandardCharsets.UTF_8);
            var o = Ebenen.lies(text);
            if (!(o.get("v") instanceof JsonPrimitive v) || !v.isNumber() || v.getAsInt() != 1) {
                throw new IllegalStateException("v ist nicht 1");
            }
            for (JsonElement e : o.getAsJsonArray("areas")) {
                String kennung = e.getAsJsonObject().get("id").getAsString();
                if (kennung.indexOf('/') <= kennung.indexOf(':') || kennung.indexOf(':') <= 0) {
                    throw new IllegalStateException("id " + kennung + " ist keine Kennung <modname>:<ebene>/<id>");
                }
                // Der Status liest die Stufe; fehlt sie, wirft das hier.
                e.getAsJsonObject().get("quality").getAsString();
                stand.put(kennung, e.getAsJsonObject());
            }
            inhalt = text;
        } catch (IOException | RuntimeException e) {
            stand.clear();
            log.log(Level.WARNING, "Qualität: " + datei + " nicht gelesen, die Gebiete kommen neu aus den Ebenen", e);
        }
    }

    /**
     * Schreibt {@code neu}, wenn es anders ist als die Datei: daneben, dann getauscht. Ohne Gebiete und ohne Datei
     * entsteht keine. Wäre sie zu gross, bleibt die alte.
     */
    private void schreibe(TreeMap<String, JsonObject> neu) {
        var o = new JsonObject();
        o.addProperty("v", 1);
        var a = new JsonArray();
        neu.values().forEach(a::add);
        o.add("areas", a);
        String text = o.toString();
        if (text.equals(inhalt) || neu.isEmpty() && !Files.exists(datei)) {
            stand = neu;
            zuGross = false;
            return;
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > hoechstens) {
            if (!zuGross) {
                log.warning("Qualität: die Datei der Gebiete wäre " + bytes.length + " Bytes gross, mehr als die "
                        + hoechstens + " des Renderers; es gilt die alte, bis Regionen ihre Stufe verlieren");
            }
            zuGross = true;
            // Sonst liefe jede Frist ab, ohne dass etwas wegfällt, und die Prüfung baute die Datei jede Sekunde neu.
            fehltSeit.clear();
            return;
        }
        try {
            Files.createDirectories(datei.getParent());
            Path daneben = datei.resolveSibling("." + datei.getFileName() + ".neu");
            Files.write(daneben, bytes);
            Files.move(daneben, datei, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            stand = neu;
            inhalt = text;
            zuGross = false;
            if (scheiterte) {
                log.info("Qualität: die Datei der Gebiete ist wieder geschrieben");
                scheiterte = false;
            }
        } catch (IOException e) {
            // Nur der erste Fehlschlag steht im Log; der nächste Takt versucht es wieder.
            if (!scheiterte) {
                log.log(Level.WARNING, "Qualität: " + datei + " nicht geschrieben, neuer Versuch jede Sekunde", e);
                scheiterte = true;
            }
            letzteEbenen = null;
        }
    }

    /** Die Zeile für {@code /heroicmap status}; null ohne Gebiete. Siehe docs/qualitaet.md, „Status“. */
    String zeile() {
        return status;
    }

    private String status() {
        if (stand.isEmpty() && !zuGross) {
            return null;
        }
        var jeEbene = new TreeMap<String, Set<String>>();
        var zahl = new HashMap<String, Integer>();
        stand.forEach((kennung, g) -> {
            jeEbene.computeIfAbsent(ebene(kennung), k -> new TreeSet<>()).add(g.get("quality").getAsString());
            zahl.merge(ebene(kennung), 1, Integer::sum);
        });
        String teile = jeEbene.entrySet().stream().map(e -> zahl.get(e.getKey()) + " aus " + e.getKey() + " ("
                + String.join(", ", e.getValue()) + ")" + (gesehen.contains(e.getKey()) ? "" : ", wartet auf die Ebene"))
                .collect(Collectors.joining("; "));
        return "Gebiete: " + (teile.isEmpty() ? "keine" : teile)
                + (zuGross ? "; neue gelten nicht, die Datei wäre grösser als " + (hoechstens >> 20) + " MiB" : "");
    }
}
