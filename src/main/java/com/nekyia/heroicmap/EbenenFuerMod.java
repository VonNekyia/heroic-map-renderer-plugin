package com.nekyia.heroicmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nekyia.heroicmap.Ebenen.Ebene;
import com.nekyia.heroicmap.Ebenen.Eigene;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Die Ebenen für den Mod: welche ein Spieler sehen darf, die Liste ebenen, jede Ebene in Teilen als ebene und auf
 * Anfrage die Tafel eines Objekts und das Sprite eines Banners einer geheimen Ebene. Ausserhalb des Hauptthreads entstehen Teile und Antworten; der Hauptthread
 * schickt nur noch. Ohne Bukkit.
 * Siehe docs/ebenen.md, „Mod“.
 */
final class EbenenFuerMod {

    /** Höchstens so viele Byte Objekte je Teil; der Rest der Nachricht ist kleiner als 1 KiB. */
    static final int TEIL = (64 << 10) - 1024;
    /** Ein Objekt für den Mod darf allein höchstens so gross sein, damit eine Nachricht unter 1 MiB bleibt. */
    static final int OBJEKT = (1 << 20) - 1024;
    /** Anfragen nach Tafeln je Spieler und Sekunde; der Rest fällt weg. Siehe docs/ebenen.md, „Tafeln“. */
    static final int TAFELN_JE_SEKUNDE = 20;
    /** Je Spieler und Sekunde höchstens so viele Byte an Teilen und Antworten auf Tafeln; eine Ebene geht aber immer ganz. */
    static final int JE_SEKUNDE = 1 << 20;
    /**
     * Anfragen nach Bannern je Spieler und Sekunde, getrennt von den Tafeln gezählt; der Rest fällt weg. Siehe
     * docs/ebenen.md, „Banner im Mod“.
     */
    static final int BANNER_JE_SEKUNDE = 20;
    /** Ein Sprite über so vielen Byte geht nicht hinaus, wie die Grenze eines Bilds. */
    static final int SPRITE = 256 << 10;
    /** Klicks auf Knöpfe je Spieler und Sekunde, getrennt gezählt; der Rest fällt weg. Siehe docs/ebenen.md, „Knöpfe“. */
    static final int KLICKS_JE_SEKUNDE = 20;

    /** Ein Klick aus dem Mod auf einen Knopf an einem Objekt. */
    record Klick(String ebene, String version, String id, String knopf) {}

    /**
     * Ein vorbereiteter Stand: die Ebenen und ihre Teile, je Kennung, die Fassung eines Spielers unter
     * {@link #schluessel}; dazu je Ebene ihre Objekte mit Tafel nach id, ohne die eigenen.
     */
    private record Fertig(List<Ebene> stand, Map<String, Teile> teile, Map<String, Map<String, JsonObject>> tafeln) {}

    /** Die Teile einer Ebene als JSON-Listen, mit ihrer version und ihren Bytes zusammen. */
    record Teile(String version, List<String> teile, long bytes) {}

    /** Was ein Spieler hat: die Unterschrift der letzten Liste und je Ebene die version. */
    private static final class Gesendet {
        private String liste = "";
        private final Map<String, String> ebenen = new HashMap<>();
        /** Die Sekunde und was darin schon ging: Takt und Antworten auf Tafeln teilen das Budget. */
        private long sekunde;
        private long verbraucht;

        /** Was in der Sekunde {@code jetzt} noch geht; negativ, wenn eine Ebene allein grösser war. */
        long rest(long jetzt) {
            if (sekunde != jetzt) {
                sekunde = jetzt;
                verbraucht = 0;
            }
            return JE_SEKUNDE - verbraucht;
        }
    }

    /** url oder port wie in freigabe; leer, solange kein Webserver bereit ist. */
    private final Supplier<JsonObject> adresse;
    private volatile Fertig fertig = new Fertig(List.of(), Map.of(), Map.of());
    /** Nur im Hauptthread. */
    private final Map<UUID, Gesendet> gesendet = new HashMap<>();
    /** Nur im Hauptthread: je Spieler die Sekunde seiner letzten Anfrage nach einer Tafel und die Zahl darin. */
    private final Map<UUID, long[]> fenster = new HashMap<>();
    /** Dasselbe für Anfragen nach Bannern. */
    private final Map<UUID, long[]> bannerFenster = new HashMap<>();
    /** Je Spieler die Sekunde und die Klicks darin; nur im Hauptthread. */
    private final Map<UUID, long[]> klickFenster = new HashMap<>();
    private final Knoepfe knoepfe;
    /** Wo die Sprites geheimer Ebenen liegen, --out ihres Aufrufs, siehe {@link Banner}; null: keine. */
    private final Path sprites;
    private final Logger log;
    private final Queue<Frage> fragen = new ConcurrentLinkedQueue<>();
    private final Map<UUID, Queue<Antwort>> antworten = new ConcurrentHashMap<>();

    private record Frage(UUID spieler, byte[] nachricht) {}

    /** Eine fertige Antwort auf eine Anfrage nach einer Tafel; die Rechte prüft der Hauptthread beim Senden. */
    record Antwort(String permission, String text, int bytes) {}

    EbenenFuerMod(Supplier<JsonObject> adresse) {
        this(adresse, null, Logger.getLogger(EbenenFuerMod.class.getName()), new Knoepfe());
    }

    /**
     * Mit {@code sprites}, dem Ordner der Sprites geheimer Ebenen, beantwortet es auch Anfragen nach Bannern; die
     * Tafeln tragen die Knöpfe aus {@code knoepfe}.
     */
    EbenenFuerMod(Supplier<JsonObject> adresse, Path sprites, Logger log, Knoepfe knoepfe) {
        this.adresse = adresse;
        this.sprites = sprites;
        this.log = log;
        this.knoepfe = knoepfe;
    }

    /** Unter diesem Schlüssel stehen die Teile der Fassung von {@code spieler}; die Teile einer Ebene unter ihrer id. */
    private static String schluessel(String ebene, UUID spieler) {
        return ebene + " " + spieler;
    }

    /**
     * Ausserhalb des Hauptthreads: rechnet die Teile jeder geänderten Ebene und veröffentlicht den Stand. Teile
     * entfernter Ebenen fallen dabei weg.
     */
    void bereite(List<Ebene> stand) {
        var alt = fertig;
        var neu = new HashMap<String, Teile>();
        var tafeln = new HashMap<String, Map<String, JsonObject>>();
        for (Ebene e : stand) {
            var t = alt.teile().get(e.id());
            boolean gleich = t != null && t.version().equals(e.version());
            var gemeinsam = gleich ? t : teile(e);
            neu.put(e.id(), gemeinsam);
            tafeln.put(e.id(), gleich ? alt.tafeln().get(e.id()) : tafeln(e.json().getAsJsonArray("objects")));
            if (e.aktion()) {
                e.spieler().forEach((u, f) -> {
                    var s = schluessel(e.id(), u);
                    var ts = alt.teile().get(s);
                    // ponytail: rechnet die eigenen Teile neu, auch wenn sich nur die gemeinsamen änderten; getrennt
                    // merken, wenn viele Spieler viele eigene haben.
                    neu.put(s, ts != null && ts.version().equals(f.version()) ? ts : fassung(gemeinsam, f));
                });
            }
        }
        fertig = new Fertig(List.copyOf(stand), Map.copyOf(neu), Map.copyOf(tafeln));
    }

    /**
     * Die Teile der Fassung: die der Ebene, dieselben Strings, dahinter die der eigenen Objekte. So kostet ein
     * Spieler nur seine eigenen. Siehe docs/ebenen.md, „Aktionsebenen im Mod“.
     */
    private static Teile fassung(Teile gemeinsam, Eigene f) {
        var eigene = teile(f.version(), f.objekte().values());
        // Ohne gemeinsame Objekte nur die eigenen; sonst stünde vorn ein leerer Teil.
        boolean leer = gemeinsam.teile().equals(List.of("[]"));
        var alle = new ArrayList<String>(leer ? List.of() : gemeinsam.teile());
        alle.addAll(eigene.teile());
        return new Teile(f.version(), List.copyOf(alle), (leer ? 0 : gemeinsam.bytes()) + eigene.bytes());
    }

    /** Die Objekte mit Tafel nach id. */
    private static Map<String, JsonObject> tafeln(Iterable<? extends JsonElement> objekte) {
        var m = new HashMap<String, JsonObject>();
        for (JsonElement x : objekte) {
            var o = x.getAsJsonObject();
            if (tafelArt(o)) {
                m.put(o.get("id").getAsString(), o);
            }
        }
        return m;
    }

    /** Das Objekt {@code id} mit Tafel in der Fassung von {@code spieler}; null, wenn es dort fehlt oder keine haben kann. */
    private static JsonObject tafelObjekt(Fertig f, Ebene e, UUID spieler, String id) {
        var o = f.tafeln().get(e.id()).get(id);
        var mein = e.eigene(spieler);
        if (o == null && mein != null) {
            o = mein.objekte().get(id);
        }
        return o != null && tafelArt(o) ? o : null;
    }

    /** Ob {@code nachricht} eine Anfrage nach einer Tafel ist. */
    static boolean istTafel(byte[] nachricht) {
        return lies(nachricht, "tafel") != null;
    }

    /** Ob {@code nachricht} eine Anfrage nach dem Sprite eines Banners ist. Siehe docs/ebenen.md, „Banner im Mod“. */
    static boolean istBanner(byte[] nachricht) {
        return lies(nachricht, "banner") != null;
    }

    /** Ob {@code nachricht} ein Klick auf einen Knopf ist. Siehe docs/ebenen.md, „Knöpfe“. */
    static boolean istKnopf(byte[] nachricht) {
        return lies(nachricht, "knopf") != null;
    }

    /** Der Klick in {@code nachricht}; null, wenn ein Feld fehlt oder keins ein Text ist. */
    static Klick klick(byte[] nachricht) {
        var o = lies(nachricht, "knopf");
        if (o == null) {
            return null;
        }
        String ebene = text(o, "ebene");
        String version = text(o, "version");
        String id = text(o, "id");
        String knopf = text(o, "knopf");
        return ebene == null || version == null || id == null || knopf == null ? null : new Klick(ebene, version, id, knopf);
    }

    /** Ein Klick von der Webkarte: der Spieler aus seiner Sitzung beim Server, der Klick ohne version. */
    record WebKlick(UUID spieler, Klick klick) {}

    /**
     * Der Klick im Rest einer Zeile Aktion: des Servers; null, wenn er nicht genau die Felder spieler, ebene, objekt
     * und knopf als Texte hat oder spieler keine UUID klein mit Bindestrichen ist. Siehe docs/anmeldung.md, „Klicks“.
     */
    static WebKlick webKlick(String json) {
        if (json.length() > Download.GROESSTE_ANFRAGE) {
            return null;
        }
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
        String s = text(o, "spieler");
        String ebene = text(o, "ebene");
        String objekt = text(o, "objekt");
        String knopf = text(o, "knopf");
        var u = s == null ? null : SpielerOrdner.uuid(s);
        return o.size() != 4 || u == null || ebene == null || objekt == null || knopf == null ? null
                : new WebKlick(u, new Klick(ebene, "", objekt, knopf));
    }

    /** Im Hauptthread: ob {@code spieler} in {@code sekunde} noch klicken darf, höchstens {@link #KLICKS_JE_SEKUNDE}. */
    boolean darfKlicken(UUID spieler, long sekunde) {
        long[] f = klickFenster.computeIfAbsent(spieler, k -> new long[2]);
        if (f[0] != sekunde) {
            f[0] = sekunde;
            f[1] = 0;
        }
        return ++f[1] <= KLICKS_JE_SEKUNDE;
    }

    /**
     * Im Hauptthread: der Knopf des Klicks mit dem Zustand von {@code spieler}, wenn er ihn drücken darf. Die Ebene
     * steht im Stand, er hat ihre permission, falls sie eine hat, das Objekt steht in seiner Fassung und hat eine
     * Tafel, und der Knopf hängt dort; mit {@code web} steht die Ebene dazu auf der Webkarte. Sonst null. Die
     * Permission heroicmap.layers, die Rate und die version prüft es nicht. Siehe docs/ebenen.md, „Knöpfe“.
     */
    Knoepfe.Gedrueckt klickbar(UUID spieler, Klick k, Predicate<String> hat, boolean web) {
        var f = fertig;
        for (Ebene e : f.stand()) {
            if (e.id().equals(k.ebene())) {
                if (web && !e.web()) {
                    return null;
                }
                var p = e.json().get("permission");
                if (p != null && !hat.test(p.getAsString()) || tafelObjekt(f, e, spieler, k.id()) == null) {
                    return null;
                }
                return knoepfe.angehaengt(k.knopf(), k.ebene(), k.id(), spieler);
            }
        }
        return null;
    }

    /** Nur Nadeln, Banner, Regionen und Kreise haben eine Tafel und damit Knöpfe. Siehe docs/ebenen.md, „Knöpfe“. */
    static boolean tafelArt(JsonObject objekt) {
        return switch (objekt.get("type").getAsString()) {
            case "pin", "banner", "region", "circle" -> true;
            default -> false;
        };
    }

    /** Eine Anfrage nach der Tafel des Objekts, wie der Mod sie schickt; nach einem Klick, damit der Knopf umspringt. */
    static byte[] tafelNach(Klick k) {
        var o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("typ", "tafel");
        o.addProperty("ebene", k.ebene());
        o.addProperty("version", k.version());
        o.addProperty("id", k.id());
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject lies(byte[] nachricht, String typ) {
        if (nachricht.length > Download.GROESSTE_ANFRAGE) {
            return null;
        }
        try {
            var o = JsonParser.parseString(new String(nachricht, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.get("typ") instanceof JsonPrimitive t && t.isString() && t.getAsString().equals(typ) ? o : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Im Hauptthread: nimmt eine Anfrage nach einer Tafel an, höchstens {@link #TAFELN_JE_SEKUNDE} je Spieler und
     * {@code sekunde}; false, wenn sie wegfällt. Beantwortet wird sie in {@link #beantworte}.
     */
    boolean frage(UUID spieler, byte[] nachricht, long sekunde) {
        boolean banner = istBanner(nachricht);
        long[] f = (banner ? bannerFenster : fenster).computeIfAbsent(spieler, k -> new long[2]);
        if (f[0] != sekunde) {
            f[0] = sekunde;
            f[1] = 0;
        }
        if (++f[1] > (banner ? BANNER_JE_SEKUNDE : TAFELN_JE_SEKUNDE)) {
            return false;
        }
        fragen.add(new Frage(spieler, nachricht));
        return true;
    }

    /**
     * Ausserhalb des Hauptthreads: beantwortet jede offene Anfrage aus dem veröffentlichten Stand. Geschickt werden
     * die Antworten gleich danach mit {@link #tafeln}, was nicht passt, im Takt.
     */
    void beantworte(long jetzt) {
        var f = fertig;
        for (Frage q; (q = fragen.poll()) != null; ) {
            var a = istBanner(q.nachricht()) ? banner(f.stand(), q.nachricht(), jetzt, q.spieler(), sprites, log)
                    : antwort(f, q.nachricht(), jetzt, q.spieler(), knoepfe);
            if (a != null) {
                antworten.computeIfAbsent(q.spieler(), k -> new ConcurrentLinkedQueue<>()).add(a);
            }
        }
    }

    /**
     * Die Antwort mit {@code ebene}, {@code version} und {@code id} der Anfrage, dazu {@code panel}, wenn das Objekt
     * eine Tafel oder Knöpfe hat und die version die der Fassung von {@code spieler} ist; die Knöpfe mit seinem
     * Zustand unter der Tafel. Null für eine unlesbare Anfrage oder eine Ebene, die es nicht gibt. Siehe
     * docs/ebenen.md, „Tafeln“.
     */
    private static Antwort antwort(Fertig f, byte[] nachricht, long jetzt, UUID spieler, Knoepfe knoepfe) {
        var o = lies(nachricht, "tafel");
        String ebene = o == null ? null : text(o, "ebene");
        String version = o == null ? null : text(o, "version");
        String id = o == null ? null : text(o, "id");
        if (ebene == null || version == null || id == null) {
            return null;
        }
        for (Ebene e : f.stand()) {
            if (!e.id().equals(ebene)) {
                continue;
            }
            var a = kopf("tafel", jetzt);
            a.addProperty("ebene", ebene);
            a.addProperty("version", version);
            a.addProperty("id", id);
            var objekt = e.fuer(spieler).version().equals(version) ? tafelObjekt(f, e, spieler, id) : null;
            if (objekt != null) {
                var knoepfeHier = knoepfe.bloecke(spieler, ebene, id);
                if (!knoepfeHier.isEmpty()) {
                    // Unter die Tafel, ein Objekt ohne bekommt eine nur mit den Knöpfen; die Tafel der Ebene bleibt, wie sie ist.
                    var tafel = objekt.has("panel") ? objekt.getAsJsonObject("panel").deepCopy() : new JsonObject();
                    var bausteine = tafel.has("blocks") ? tafel.getAsJsonArray("blocks") : new JsonArray();
                    knoepfeHier.forEach(bausteine::add);
                    tafel.add("blocks", bausteine);
                    a.add("panel", tafel);
                } else if (objekt.has("panel")) {
                    a.add("panel", objekt.get("panel"));
                }
            }
            var p = e.json().get("permission");
            String text = a.toString();
            return new Antwort(p == null ? null : p.getAsString(), text, text.getBytes(StandardCharsets.UTF_8).length);
        }
        return null;
    }

    /**
     * Die Antwort auf eine Anfrage nach einem Banner: {@code ebene}, {@code version}, {@code entwurf} und
     * {@code krone} der Anfrage, dazu {@code satz} und {@code png} als Base64, wenn die version die der Fassung von
     * {@code spieler} ist, die Ebene den Entwurf hat und das Sprite im Satz oben unter {@code sprites} liegt. Null
     * für eine unlesbare Anfrage, eine Ebene, die es nicht gibt, und eine ohne permission: Deren Sprites holt der Mod
     * per HTTP. Den Pfad bilden nur Namen, die die Ebene nennt. Siehe docs/ebenen.md, „Banner im Mod“.
     */
    private static Antwort banner(List<Ebene> stand, byte[] nachricht, long jetzt, UUID spieler, Path sprites, Logger log) {
        var o = lies(nachricht, "banner");
        String ebene = o == null ? null : text(o, "ebene");
        String version = o == null ? null : text(o, "version");
        String entwurf = o == null ? null : text(o, "entwurf");
        Boolean krone = o != null && o.get("krone") instanceof JsonPrimitive p && p.isBoolean() ? p.getAsBoolean() : null;
        if (ebene == null || version == null || entwurf == null || krone == null) {
            return null;
        }
        for (Ebene e : stand) {
            if (!e.id().equals(ebene)) {
                continue;
            }
            var permission = e.json().get("permission");
            if (permission == null) {
                return null;
            }
            var a = kopf("banner", jetzt);
            a.addProperty("ebene", ebene);
            a.addProperty("version", version);
            a.addProperty("entwurf", entwurf);
            a.addProperty("krone", krone);
            if (sprites != null && e.fuer(spieler).version().equals(version) && e.json().get("designs") instanceof JsonObject d
                    && d.has(entwurf) && EbenenPruefung.teil(entwurf)) {
                Path satz = sprites.resolve(e.modname()).resolve("banner").resolve(e.name()).resolve("oben");
                Path png = (krone ? satz.resolve("krone") : satz).resolve(entwurf + ".png");
                try {
                    long groesse = Files.size(png);
                    if (groesse > SPRITE) {
                        log.warning("Banner für den Mod: " + ebene + ", " + entwurf + " hat " + groesse
                                + " Byte, höchstens " + SPRITE);
                    } else {
                        var s = JsonParser.parseString(Files.readString(satz.resolve("satz.json"))).getAsJsonObject();
                        a.add("satz", s);
                        a.addProperty("png", Base64.getEncoder().encodeToString(Files.readAllBytes(png)));
                    }
                } catch (IOException | RuntimeException x) {
                    // Noch kein Sprite: die Antwort ohne png, der Mod fragt für diese version nicht wieder.
                    a.remove("satz");
                }
            }
            String text = a.toString();
            return new Antwort(permission.getAsString(), text, text.getBytes(StandardCharsets.UTF_8).length);
        }
        return null;
    }

    private static String text(JsonObject o, String feld) {
        return o.get(feld) instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    /**
     * Im Hauptthread: die Nachrichten, die {@code spieler} jetzt braucht. Mit {@code alle} darf er Ebenen sehen,
     * dazu muss {@code hat} jede permission einer Ebene bejahen. Nichts, wenn sich für ihn nichts geändert hat;
     * sonst die Liste und die Teile neuer oder geänderter Ebenen, je Aufruf höchstens {@link #JE_SEKUNDE} Byte.
     * Zuerst noch offene Antworten auf Tafeln, mit derselben Prüfung der Rechte. Das Budget teilt er mit
     * {@link #tafeln} in derselben Sekunde.
     */
    List<String> nachrichten(UUID spieler, boolean alle, Predicate<String> hat, long jetzt) {
        var f = fertig;
        // Je Ebene die Fassung dieses Spielers; bei einer gewöhnlichen Ebene sie selbst.
        var sicht = new ArrayList<Ebene>();
        if (alle) {
            for (Ebene e : f.stand()) {
                var p = e.json().get("permission");
                if (p == null || hat.test(p.getAsString())) {
                    sicht.add(e.fuer(spieler));
                }
            }
        }
        var g = gesendet.computeIfAbsent(spieler, k -> new Gesendet());
        var a = adresse.get();
        // Ohne Ebenen und ohne Adresse keine Liste; mit Adresse auch ohne Ebenen, für die Basis von „Im Browser öffnen“.
        // Ändert sich die Adresse, kommt die Liste neu. Siehe docs/ebenen.md, „Mod“.
        String unterschrift = sicht.isEmpty() && a.size() == 0 ? ""
                : a + "|" + sicht.stream().map(e -> e.id() + "=" + e.version()).collect(Collectors.joining(","));
        var aus = new ArrayList<String>();
        if (!unterschrift.equals(g.liste)) {
            aus.add(liste(sicht, a, jetzt));
            g.liste = unterschrift;
        }
        g.ebenen.keySet().retainAll(sicht.stream().map(Ebene::id).toList());
        antworten(spieler, g, alle, hat, jetzt, aus);
        long rest = g.rest(jetzt);
        for (Ebene e : sicht) {
            var eigene = f.teile().get(schluessel(e.id(), spieler));
            var t = eigene != null && eigene.version().equals(e.version()) ? eigene : f.teile().get(e.id());
            // Passen Teile und Fassung nicht, etwa bevor bereite den neuen Stand kennt, kommt die Ebene im nächsten Takt.
            if (e.version().equals(g.ebenen.get(e.id())) || t == null || !t.version().equals(e.version())) {
                continue;
            }
            if (t.bytes() > rest && rest < JE_SEKUNDE) {
                break;
            }
            for (int i = 0; i < t.teile().size(); i++) {
                aus.add(teil(e, i + 1, t.teile().size(), t.teile().get(i), jetzt));
            }
            rest -= t.bytes();
            g.ebenen.put(e.id(), e.version());
        }
        g.verbraucht = JE_SEKUNDE - rest;
        return aus;
    }

    /**
     * Im Hauptthread, gleich nach {@link #beantworte}: die Antworten auf Tafeln, die ins Budget der laufenden
     * Sekunde passen; der Rest bleibt für den Takt. Siehe docs/entscheidungen/0010-tafeln-ueber-den-kanal.md.
     */
    List<String> tafeln(UUID spieler, boolean alle, Predicate<String> hat, long jetzt) {
        var aus = new ArrayList<String>();
        antworten(spieler, gesendet.computeIfAbsent(spieler, k -> new Gesendet()), alle, hat, jetzt, aus);
        return aus;
    }

    /** Hängt Antworten auf Tafeln an, solange sie ins Budget passen; die Rechte wie bei ebenen. */
    private void antworten(UUID spieler, Gesendet g, boolean alle, Predicate<String> hat, long jetzt, List<String> aus) {
        var offen = antworten.get(spieler);
        long rest = g.rest(jetzt);
        for (Antwort x; offen != null && (x = offen.peek()) != null; ) {
            if (x.bytes() > rest && rest < JE_SEKUNDE) {
                break;
            }
            offen.poll();
            if (alle && (x.permission() == null || hat.test(x.permission()))) {
                aus.add(x.text());
                rest -= x.bytes();
            }
        }
        g.verbraucht = JE_SEKUNDE - rest;
    }

    /** Nach dem Verlassen oder ohne offenen Kanal: beim nächsten Mal bekommt er alles neu. */
    void vergiss(UUID spieler) {
        gesendet.remove(spieler);
        fenster.remove(spieler);
        bannerFenster.remove(spieler);
        klickFenster.remove(spieler);
        antworten.remove(spieler);
    }

    private static String liste(List<Ebene> ebenen, JsonObject adresse, long jetzt) {
        var o = kopf("ebenen", jetzt);
        var l = new JsonArray();
        // secret: Die Sprites dieser Ebene kommen über den Kanal, nicht per HTTP. Siehe docs/ebenen.md, „Banner im Mod“.
        for (Ebene e : ebenen) {
            var eintrag = EbenenSchreiber.eintrag(e);
            if (e.json().has("permission")) {
                eintrag.addProperty("secret", true);
            }
            l.add(eintrag);
        }
        o.add("ebenen", l);
        adresse.entrySet().forEach(a -> o.add(a.getKey(), a.getValue()));
        return o.toString();
    }

    private static String teil(Ebene e, int teil, int teile, String objekte, long jetzt) {
        var o = kopf("ebene", jetzt);
        o.addProperty("id", e.id());
        o.addProperty("version", e.version());
        o.addProperty("teil", teil);
        o.addProperty("teile", teile);
        String k = o.toString();
        // Die Objekte stehen schon als JSON bereit; nur angehängt, nicht neu geschrieben.
        return k.substring(0, k.length() - 1) + ",\"objects\":" + objekte + "}";
    }

    /**
     * Die Objekte für den Mod, jede Art ohne panel, der Reihe nach in Teile von höchstens
     * {@link #TEIL} Byte; ein Objekt, das allein grösser ist, steht allein. Eine leere Ebene ist ein leerer Teil.
     */
    static Teile teile(Ebene e) {
        return teile(e.version(), e.json().getAsJsonArray("objects"));
    }

    private static Teile teile(String version, Iterable<? extends JsonElement> objekte) {
        var aus = new ArrayList<String>();
        var teil = new StringBuilder("[");
        int bytes = 1;
        long summe = 0;
        for (JsonElement x : objekte) {
            String objekt = fuerMod(x.getAsJsonObject());
            int n = objekt.getBytes(StandardCharsets.UTF_8).length + 1;
            if (bytes > 1 && bytes + n > TEIL) {
                aus.add(teil.append(']').toString());
                summe += bytes;
                teil = new StringBuilder("[");
                bytes = 1;
            }
            teil.append(bytes > 1 ? "," : "").append(objekt);
            bytes += n;
        }
        aus.add(teil.append(']').toString());
        return new Teile(version, List.copyOf(aus), summe + bytes);
    }

    /** Ein Objekt, wie der Mod es bekommt: ohne panel; die Tafel fragt er einzeln an, siehe {@link #frage}. */
    static String fuerMod(JsonObject o) {
        if (!o.has("panel")) {
            return o.toString();
        }
        var ohne = o.deepCopy();
        ohne.remove("panel");
        return ohne.toString();
    }

    /**
     * url oder port wie in freigabe, siehe docs/download.md, „Kanal“: url ist die Wurzel der Kacheln,
     * {@code webserver.url} mit /tiles. Ohne Webserver leer, ebenso mit HTTPS ohne url: Aus port baute der Mod
     * http://, und der Server spricht dann nur HTTPS.
     */
    static JsonObject adresse(Konfiguration.Webserver w) {
        var a = new JsonObject();
        if (w.an() && w.url().isEmpty() && w.zertifikat() == null) {
            a.addProperty("port", w.portFuerMod());
        } else if (w.an() && !w.url().isEmpty()) {
            a.addProperty("url", w.url() + "/tiles");
        }
        return a;
    }

    /**
     * Mit karte.von-oeffentlich die Adresse der anderen Karte als url mit /tiles: Bilder und Banner ihrer Ebenen liegen
     * dort. Sonst leer. Siehe docs/mehrere-server.md, „Karte von“.
     */
    static JsonObject adresse(Konfiguration.Karte k) {
        var a = new JsonObject();
        if (!k.vonOeffentlich().isEmpty()) {
            a.addProperty("url", k.vonOeffentlich() + "/tiles");
        }
        return a;
    }

    /**
     * Die Adresse für „Im Browser öffnen“: die eigene; mit karte.von-oeffentlich keine, denn den Link baut der Mod aus
     * der Adresse der anderen Karte, und das Token dieses Servers gilt dort nicht.
     */
    static JsonObject adresseFuerWeb(Konfiguration.Webserver w, Konfiguration.Karte k) {
        return k.vonOeffentlich().isEmpty() ? adresse(w) : new JsonObject();
    }

    private static JsonObject kopf(String typ, long jetzt) {
        var o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("typ", typ);
        o.addProperty("jetzt", jetzt);
        return o;
    }
}
