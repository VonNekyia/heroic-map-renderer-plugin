package com.nekyia.heroicmap;

import com.google.gson.JsonObject;
import com.nekyia.heroicmap.api.MapButton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Die Knöpfe der API: je Besitzer, an Objekten beliebiger Ebenen, mit dem Zustand je Spieler, nur im Speicher. Jede
 * Methode sperrt die ganze Sammlung. Siehe docs/api.md, „Aktionsebenen und Knöpfe“.
 */
final class Knoepfe {

    static final int JE_BESITZER = 64;
    static final int JE_OBJEKT = 4;
    static final int LABEL = 32;

    private record Ort(String ebene, String objekt) {}

    /** Je Knopf, nach id. */
    private final Map<String, Knopf> knoepfe = new LinkedHashMap<>();
    /** Je Ort die Knöpfe in der Reihenfolge des Anhängens. */
    private final Map<Ort, List<Knopf>> an = new HashMap<>();
    /** Je Ort und Knopf die Spieler, für die er an ist; nur angehängte Knöpfe. */
    private final Map<Ort, Map<String, Set<UUID>>> zustand = new HashMap<>();

    /** Der Knopf {@code name} von {@code modname}, beim ersten Aufruf mit diesem Label angelegt. */
    synchronized MapButton knopf(String modname, String name, String de, String en) {
        if (!EbenenPruefung.teil(modname) || !EbenenPruefung.teil(name)) {
            throw new IllegalArgumentException("Knopf " + modname + ":" + name
                    + ": je Teil 1 bis 64 Zeichen aus a-z, 0-9, _, - und ., kein . vorn oder hinten, kein Gerät von Windows wie nul");
        }
        String id = modname + ":" + name;
        var k = knoepfe.get(id);
        if (k != null) {
            return k;
        }
        if (de == null && en == null) {
            throw new IllegalArgumentException("Knopf " + id + ": braucht de oder en");
        }
        var label = new JsonObject();
        for (var t : List.of(new String[] {"de", de}, new String[] {"en", en})) {
            if (t[1] == null) {
                continue;
            }
            if (t[1].isBlank() || t[1].codePointCount(0, t[1].length()) > LABEL) {
                throw new IllegalArgumentException("Knopf " + id + ": " + t[0] + " leer oder länger als 32 Zeichen");
            }
            label.addProperty(t[0], t[1]);
        }
        if (knoepfe.values().stream().filter(x -> x.modname.equals(modname)).count() >= JE_BESITZER) {
            throw new IllegalArgumentException("Knopf " + id + ": höchstens 64 Knöpfe je Plugin");
        }
        k = new Knopf(modname, id, label);
        knoepfe.put(id, k);
        return k;
    }

    /** Ein Plugin geht: Seine Knöpfe gehen mit, von jedem Objekt. */
    synchronized void entferne(String modname) {
        for (var k : List.copyOf(knoepfe.values())) {
            if (k.modname.equals(modname)) {
                loesche(k);
            }
        }
    }

    /**
     * Die Knöpfe an diesem Objekt als Bausteine der Tafel, mit dem Zustand von {@code spieler}; leer ohne. Siehe
     * docs/ebenen.md, „Tafeln“.
     */
    synchronized List<JsonObject> bloecke(UUID spieler, String ebene, String objekt) {
        var ort = new Ort(ebene, objekt);
        var hier = an.get(ort);
        if (hier == null) {
            return List.of();
        }
        var aus = new ArrayList<JsonObject>();
        for (var k : hier) {
            var b = new JsonObject();
            b.addProperty("type", "button");
            b.addProperty("id", k.id);
            b.add("label", k.label.deepCopy());
            b.addProperty("on", istAn(ort, k, spieler));
            aus.add(b);
        }
        return aus;
    }

    /** Der Knopf {@code id}, wenn er an diesem Objekt hängt; sonst null. */
    synchronized Knopf angehaengt(String id, String ebene, String objekt) {
        var hier = an.get(new Ort(ebene, objekt));
        return hier == null ? null : hier.stream().filter(k -> k.id.equals(id)).findFirst().orElse(null);
    }

    private boolean istAn(Ort ort, Knopf k, UUID spieler) {
        var z = zustand.get(ort);
        var s = z == null ? null : z.get(k.id);
        return s != null && s.contains(spieler);
    }

    private void loesche(Knopf k) {
        k.geloescht = true;
        knoepfe.remove(k.id);
        for (var e : List.copyOf(an.entrySet())) {
            if (e.getValue().remove(k)) {
                abhaengen(e.getKey(), k);
            }
        }
    }

    /** Nach dem Entfernen aus {@code an}: die Zustände dort mit, leere Einträge fallen weg. */
    private void abhaengen(Ort ort, Knopf k) {
        if (an.get(ort).isEmpty()) {
            an.remove(ort);
        }
        var z = zustand.get(ort);
        if (z != null) {
            z.remove(k.id);
            if (z.isEmpty()) {
                zustand.remove(ort);
            }
        }
    }

    /** Die Kennung einer Ebene und die id eines Objekts, wie das Format sie erlaubt. */
    private static Ort ort(String id, String ebene, String objekt) {
        Objects.requireNonNull(ebene);
        Objects.requireNonNull(objekt);
        int i = ebene.indexOf(':');
        if (i < 0 || !EbenenPruefung.teil(ebene.substring(0, i)) || !EbenenPruefung.teil(ebene.substring(i + 1))) {
            throw new IllegalArgumentException("Knopf " + id + ": " + ebene + " ist keine Kennung wie modname:ebene");
        }
        if (objekt.isEmpty() || objekt.codePointCount(0, objekt.length()) > 64) {
            throw new IllegalArgumentException("Knopf " + id + ": die id eines Objekts hat 1 bis 64 Zeichen");
        }
        return new Ort(ebene, objekt);
    }

    /** Ein Knopf der API. */
    final class Knopf implements MapButton {

        private final String modname;
        private final String id;
        private final JsonObject label;
        private volatile boolean geloescht;

        private Knopf(String modname, String id, JsonObject label) {
            this.modname = modname;
            this.id = id;
            this.label = label;
        }

        @Override
        public String id() {
            return id;
        }

        private void lebt() {
            if (geloescht) {
                throw new IllegalStateException("Knopf " + id + " ist gelöscht");
            }
        }

        @Override
        public void attach(String ebene, String objekt) {
            synchronized (Knoepfe.this) {
                lebt();
                var o = ort(id, ebene, objekt);
                var hier = an.computeIfAbsent(o, x -> new ArrayList<>());
                if (hier.contains(this)) {
                    return;
                }
                if (hier.size() >= JE_OBJEKT) {
                    throw new IllegalArgumentException("Knopf " + id + ": " + objekt + " in " + ebene + " hat schon 4 Knöpfe");
                }
                hier.add(this);
            }
        }

        @Override
        public void detach(String ebene, String objekt) {
            synchronized (Knoepfe.this) {
                lebt();
                var o = ort(id, ebene, objekt);
                var hier = an.get(o);
                if (hier != null && hier.remove(this)) {
                    abhaengen(o, this);
                }
            }
        }

        @Override
        public void state(UUID spieler, String ebene, String objekt, boolean ein) {
            Objects.requireNonNull(spieler);
            synchronized (Knoepfe.this) {
                lebt();
                var o = ort(id, ebene, objekt);
                if (!an.getOrDefault(o, List.of()).contains(this)) {
                    throw new IllegalArgumentException("Knopf " + id + ": hängt nicht an " + objekt + " in " + ebene);
                }
                var s = zustand.computeIfAbsent(o, x -> new HashMap<>()).computeIfAbsent(id, x -> new HashSet<>());
                if (ein) {
                    s.add(spieler);
                } else {
                    s.remove(spieler);
                }
            }
        }

        @Override
        public boolean state(UUID spieler, String ebene, String objekt) {
            synchronized (Knoepfe.this) {
                lebt();
                return istAn(ort(id, ebene, objekt), this, spieler);
            }
        }

        @Override
        public void delete() {
            synchronized (Knoepfe.this) {
                if (!geloescht) {
                    loesche(this);
                }
            }
        }
    }
}
