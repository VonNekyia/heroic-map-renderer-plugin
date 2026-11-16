package com.nekyia.heroicmap;

import com.nekyia.heroicmap.Einrichtung.Schritt;
import com.nekyia.heroicmap.Einrichtung.Seite;
import com.nekyia.heroicmap.Einrichtung.Wahl;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * /heroicmap setup als Dialog des Spiels: zeigt die Seiten aus {@link Einrichtung} und führt von der Übersicht durch
 * die Schritte bis zum Speichern. Jede Seite baut ein Dialog von Paper; was eine Antwort bewirkt, läuft im
 * Hauptthread, Platte und Port ausserhalb. Siehe docs/einrichten.md.
 */
final class EinrichtungDialog {

    /** Breite von Text und Eingaben in Pixeln des Dialogs. */
    private static final int BREITE = 360;
    /** Ein Knopf gilt einmal und höchstens eine Viertelstunde; danach öffnet /heroicmap setup neu. */
    private static final ClickCallback.Options EINMAL = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(15)).build();
    private static final Map<Ampeln.Farbe, NamedTextColor> FARBEN = Map.of(Ampeln.Farbe.GRUEN, NamedTextColor.GREEN,
            Ampeln.Farbe.ROT, NamedTextColor.RED, Ampeln.Farbe.GELB, NamedTextColor.YELLOW, Ampeln.Farbe.GRAU, NamedTextColor.GRAY);

    /** Wo ein Spieler in der Einrichtung steht; nur im Hauptthread geändert. */
    private static final class Sitzung {
        private final Konfiguration datei;
        private final List<Schritt> schritte;
        private final String ausDatei;
        private Wahl wahl;
        private int index;
        private String portFehler;
        private String schaetzung;

        private Sitzung(Konfiguration datei, List<Schritt> schritte, String ausDatei) {
            this.datei = datei;
            this.schritte = schritte;
            this.ausDatei = ausDatei;
            this.wahl = Wahl.aus(datei);
        }
    }

    private final HeroicMapPlugin plugin;
    private final Map<UUID, Sitzung> sitzungen = new ConcurrentHashMap<>();

    EinrichtungDialog(HeroicMapPlugin plugin) {
        this.plugin = plugin;
    }

    /** Ein Spieler geht: Seine Einrichtung ist vorbei. */
    void vergiss(UUID spieler) {
        sitzungen.remove(spieler);
    }

    private void aussen(Runnable r) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> r.run());
    }

    private void innen(Runnable r) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, r);
    }

    /** Im Hauptthread: die Übersicht mit den Ampeln. */
    void oeffne(Player p) {
        sitzungen.remove(p.getUniqueId());
        var lage = plugin.lage();
        aussen(() -> {
            var ampeln = Ampeln.pruefe(lage);
            innen(() -> zeige(p, Einrichtung.uebersicht(ampeln), Map.of("einrichten", a -> beginne(p), "pruefen", a -> oeffne(p))));
        });
    }

    /** Liest config.yml, wie sie jetzt dasteht, und beginnt beim ersten Schritt. */
    private void beginne(Player p) {
        aussen(() -> {
            Sitzung s;
            try {
                var c = new YamlConfiguration();
                c.loadFromString(Files.readString(plugin.configDatei(), StandardCharsets.UTF_8));
                var k = plugin.lies(c);
                boolean nieGezeichnet = !k.baeume().isEmpty()
                        && !Files.isRegularFile(k.kacheln().resolve(k.baeume().getFirst().ordner()).resolve("map.json"));
                var w = k.webserver();
                String ausDatei = !w.url().isEmpty() ? "Adresse für Spieler: " + w.url() + "."
                        : w.oeffentlicherPort() > 0 ? "Spieler erreichen die Karte über Port " + w.oeffentlicherPort() + " eines Proxys."
                        : w.zertifikat() != null ? "Die Karte spricht HTTPS." : null;
                s = new Sitzung(k, Einrichtung.schritte(k, nieGezeichnet), ausDatei);
            } catch (IOException | InvalidConfigurationException | IllegalArgumentException e) {
                String grund = "config.yml lässt sich so nicht einrichten: " + e.getMessage();
                innen(() -> zeige(p, new Seite("Einrichten", List.of(new Einrichtung.Zeile("✖ " + grund, Ampeln.Farbe.ROT)), List.of(),
                        List.of("schliessen")), Map.of()));
                return;
            }
            innen(() -> {
                sitzungen.put(p.getUniqueId(), s);
                zeigeSchritt(p);
            });
        });
    }

    /** Im Hauptthread: der Schritt, bei dem der Spieler steht. */
    private void zeigeSchritt(Player p) {
        var s = sitzungen.get(p.getUniqueId());
        if (s == null) {
            return;
        }
        Schritt schritt = s.schritte.get(s.index);
        Seite seite = switch (schritt) {
            case TEXTUREN -> Einrichtung.texturen(s.schritte);
            case AUSSEHEN -> Einrichtung.aussehen(s.schritte, s.wahl);
            case WEBKARTE -> Einrichtung.webkarte(s.schritte, s.wahl, s.portFehler, s.ausDatei);
            case MOD -> Einrichtung.mod(s.schritte, s.wahl, s.schaetzung);
        };
        zeige(p, seite, Map.of("weiter", a -> weiter(p, s, schritt, a), "zurueck", a -> {
            s.wahl = Einrichtung.antwort(schritt, s.wahl, a::getText);
            s.index--;
            zeigeSchritt(p);
        }, "abbrechen", a -> sitzungen.remove(p.getUniqueId()), "schaetzen", a -> schaetze(p, s, a)));
    }

    private void weiter(Player p, Sitzung s, Schritt schritt, DialogResponseView a) {
        s.wahl = Einrichtung.antwort(schritt, s.wahl, a::getText);
        if (schritt != Schritt.WEBKARTE) {
            naechster(p, s);
            return;
        }
        // Der Port: probeweise binden, nur auf dem Host aus webserver.listen. Siehe docs/einrichten.md, „Webkarte“.
        String eingabe = a.getText("port");
        String lauscht = s.datei.webserver().adresse();
        String host = lauscht.contains(":") ? lauscht.substring(0, lauscht.lastIndexOf(':') + 1) : "0.0.0.0:";
        int eigener = plugin.eigenerPort();
        int spielPort = plugin.getServer().getPort();
        aussen(() -> {
            String fehler = Einrichtung.portFehler(eingabe, eigener, spielPort, port -> Ampeln.portFrei(host + port),
                    Ampeln.freierPort(host + 1, 8080, spielPort));
            innen(() -> {
                s.portFehler = fehler;
                if (fehler != null) {
                    zeigeSchritt(p);
                } else {
                    s.wahl = Einrichtung.mitPort(s.wahl, Integer.parseInt(eingabe.strip()));
                    naechster(p, s);
                }
            });
        });
    }

    private void naechster(Player p, Sitzung s) {
        if (s.index + 1 < s.schritte.size()) {
            s.index++;
            zeigeSchritt(p);
        } else {
            zeigeZusammenfassung(p, s);
        }
    }

    /** Probiert die Wünsche an config.yml aus, ohne zu schreiben, und zeigt, was sich ändern würde. */
    private void zeigeZusammenfassung(Player p, Sitzung s) {
        var wuensche = Einrichtung.wuensche(s.schritte, s.wahl);
        aussen(() -> {
            Einrichtung.Vorschau v;
            try {
                v = Einrichtung.vorschau(Files.readString(plugin.configDatei(), StandardCharsets.UTF_8), wuensche,
                        plugin::pruefeFuerSetzer);
            } catch (IOException e) {
                v = new Einrichtung.Vorschau(List.of(), "config.yml nicht gelesen: " + e.getMessage(), List.of());
            }
            var vorschau = v;
            innen(() -> zeige(p, Einrichtung.zusammenfassung(vorschau), Map.of("speichern", a -> speichere(p, s),
                    "zurueck", a -> zeigeSchritt(p), "abbrechen", a -> sitzungen.remove(p.getUniqueId()),
                    "schliessen", a -> sitzungen.remove(p.getUniqueId()))));
        });
    }

    private void speichere(Player p, Sitzung s) {
        var wuensche = Einrichtung.wuensche(s.schritte, s.wahl);
        String wer = p.getName();
        aussen(() -> {
            Einrichtung.Vorschau abgelehnt;
            try {
                var geaendert = plugin.schreibe(wuensche, wer);
                innen(() -> {
                    sitzungen.remove(p.getUniqueId());
                    zeige(p, Einrichtung.fertig(geaendert), Map.of());
                    Einrichtung.alsNaechstes().forEach(z -> p.sendMessage(mitBefehl(z)));
                });
                return;
            } catch (Setzer.Abgelehnt e) {
                abgelehnt = new Einrichtung.Vorschau(List.of(), e.getMessage(), e.vonHand());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "config.yml nicht geschrieben", e);
                abgelehnt = new Einrichtung.Vorschau(List.of(), "config.yml liess sich nicht schreiben, siehe Log", List.of());
            }
            var v = abgelehnt;
            innen(() -> zeige(p, Einrichtung.zusammenfassung(v), Map.of("zurueck", a -> zeigeSchritt(p),
                    "schliessen", a -> sitzungen.remove(p.getUniqueId()))));
        });
    }

    /** Eine Zeile für den Chat; ein Befehl darin lässt sich anklicken und steht dann in der Eingabe. */
    private static Component mitBefehl(String zeile) {
        int i = zeile.indexOf("/heroicmap ");
        if (i < 0) {
            return Component.text(zeile);
        }
        int ende = zeile.indexOf(' ', i + "/heroicmap ".length());
        String befehl = zeile.substring(i, ende < 0 ? zeile.length() : ende);
        return Component.text(zeile.substring(0, i)).append(Component.text(befehl).decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.suggestCommand(befehl))).append(Component.text(zeile.substring(i + befehl.length())));
    }

    /**
     * „Schätzen“ im Schritt Mod: ruft --estimate für die Karte des Mods, eingereiht wie ein Lauf und nie neben einem.
     * Scheitert es, sagt der Schritt warum und geht weiter. Siehe docs/einrichten.md, „Schätzen“.
     */
    private void schaetze(Player p, Sitzung s, DialogResponseView a) {
        s.wahl = Einrichtung.antwort(Schritt.MOD, s.wahl, a::getText);
        var laeufe = plugin.laeufe();
        var baum = s.datei.baeume().stream().filter(b -> b.kamera().equals("top-north") && Integer.valueOf(4).equals(b.scale())
                && !b.cinematic() && !b.flat()).findFirst().orElse(new Konfiguration.Baum("top-north", "s", 4, false, true, false));
        String nicht = laeufe == null ? "kein Renderer" : laeufe.schaetze(baum, (zeile, ausgang) -> innen(() -> {
            s.schaetzung = zeile != null ? Einrichtung.schaetzung(zeile) : "nicht möglich: " + ausgang;
            if (sitzungen.get(p.getUniqueId()) == s) {
                zeigeSchritt(p);
            }
        }));
        if (nicht != null) {
            s.schaetzung = "gerade nicht möglich: " + nicht;
            zeigeSchritt(p);
            return;
        }
        zeige(p, Einrichtung.schaetzt(), Map.of("abbrechen", x -> laeufe.brichAb()));
    }

    /** Im Hauptthread: zeigt die Seite; {@code aktionen} nennt je Knopf, was seine Antwort bewirkt, sonst schliesst er nur. */
    private void zeige(Player p, Seite seite, Map<String, Consumer<DialogResponseView>> aktionen) {
        if (!p.isOnline()) {
            return;
        }
        UUID spieler = p.getUniqueId();
        var text = seite.zeilen().stream().map(z -> DialogBody.plainMessage(z.farbe() == null ? Component.text(z.text())
                : Component.text(z.text(), FARBEN.get(z.farbe())), BREITE)).toList();
        var eingaben = seite.eingaben().stream().map(e -> e.optionen().isEmpty()
                ? (DialogInput) DialogInput.text(e.id(), Component.text(e.label())).initial(e.wert()).maxLength(e.laenge()).width(BREITE).build()
                : DialogInput.singleOption(e.id(), Component.text(e.label()), e.optionen().stream()
                        .map(o -> SingleOptionDialogInput.OptionEntry.create(o[0], Component.text(o[1]), o[0].equals(e.wert()))).toList())
                        .width(BREITE).build()).toList();
        var knoepfe = seite.knoepfe().stream().map(k -> ActionButton.builder(Component.text(Einrichtung.LABEL.get(k)))
                .action(DialogAction.customClick((antwort, wer) -> {
                    // Die Antwort gilt nur dem Spieler, dem die Seite gezeigt wurde, und nur mit dem Recht.
                    if (wer instanceof Player q && q.getUniqueId().equals(spieler) && q.hasPermission(HeroicMapPlugin.ADMIN)) {
                        innen(() -> aktionen.getOrDefault(k, x -> {}).accept(antwort));
                    }
                }, EINMAL)).build()).toList();
        p.showDialog(Dialog.create(b -> b.empty()
                .base(DialogBase.builder(Component.text(seite.titel())).canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE).body(text).inputs(eingaben).build())
                .type(DialogType.multiAction(knoepfe).columns(Math.min(3, knoepfe.size())).build())));
    }
}
