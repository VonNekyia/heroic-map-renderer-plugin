package com.nekyia.heroicmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Startet den Renderer nach Zeitplan und per Befehl, dazu seinen Server und autogroup. Siehe docs/laeufe.md,
 * docs/webserver.md, docs/mitspieler.md.
 */
public final class HeroicMapPlugin extends JavaPlugin {

    private static final List<String> BEFEHLE = List.of("render", "update", "status", "cancel");

    private Laeufe laeufe;
    private volatile Webserver webserver;
    /** Warum das Plugin ohne Renderer bleibt; null mit Renderer. Jeder Befehl nennt es. */
    private String ohneRenderer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ergaenzeKonfiguration();
        Path server = Path.of("").toAbsolutePath();
        Path hauptwelt = getServer().getWorldContainer().toPath().toAbsolutePath().normalize()
                .resolve(getServer().getWorlds().getFirst().getName());
        Konfiguration konf;
        try {
            konf = Konfiguration.aus(getConfig(), server, hauptwelt);
        } catch (IllegalArgumentException e) {
            getLogger().severe("config.yml: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        // Sprachchat lädt Klassen von Simple Voice Chat und läuft darum nur mit ihm. Siehe docs/mitspieler.md, „Simple Voice Chat“.
        Mitspieler.starte(konf.autogroup(), getServer().getPluginManager().isPluginEnabled("voicechat"), getLogger(),
                () -> Sprachchat.starte(this));
        try {
            konf = konf.mitRenderer(Binaer.waehle(konf.renderer(), getFile().toPath(), getDataFolder().toPath().resolve("bin"),
                    System.getProperty("os.name"), System.getProperty("os.arch")));
        } catch (IOException e) {
            ohneRenderer = "Kein Renderer: " + e.getMessage() + ". renderer.binary in config.yml setzen, siehe docs/konfiguration.md.";
            getLogger().log(Level.SEVERE, ohneRenderer, e);
            return;
        }
        laeufe = new Laeufe(konf, getLogger(), getDataFolder().toPath());
        laeufe.raeumeAuf();
        laeufe.markiere();
        for (var w : getServer().getWorlds()) {
            if (!w.isAutoSave() && w.getWorldFolder().toPath().toAbsolutePath().normalize().equals(konf.welt().normalize())) {
                getLogger().warning("Der Autosave der Welt " + w.getName() + " ist aus. Änderungen kommen erst beim "
                        + "Entladen oder Stoppen auf die Platte und erst dann auf die Karte.");
            }
        }
        if (konf.updateMinuten() > 0) {
            getServer().getAsyncScheduler().runAtFixedRate(this, t -> laeufe.starte(Laeufe.Art.UPDATE),
                    konf.updateMinuten(), konf.updateMinuten(), TimeUnit.MINUTES);
        }
        Path geheimnis = null;
        if (konf.baeume().stream().anyMatch(Konfiguration.Baum::download)) {
            geheimnis = starteDownload(konf);
        }
        if (konf.webserver().an()) {
            starteWebserver(konf, geheimnis);
        }
    }

    /** Neue Schlüssel aus der Vorlage im Jar, nach einem Update. Siehe docs/konfiguration.md, „Nach einem Update“. */
    private void ergaenzeKonfiguration() {
        try (var ein = getResource("config.yml")) {
            var e = Vorlage.ergaenze(getDataFolder().toPath().resolve("config.yml"),
                    new String(ein.readAllBytes(), StandardCharsets.UTF_8));
            if (!e.neu().isEmpty()) {
                getLogger().info("config.yml: aus der Vorlage ergänzt: " + String.join(", ", e.neu()));
            }
            if (!e.fehlen().isEmpty()) {
                getLogger().warning("config.yml: nicht ergänzt, es gilt die Vorgabe: " + String.join(", ", e.fehlen()));
            }
            if (!e.unbekannt().isEmpty()) {
                getLogger().warning("config.yml: unbekannt, das Plugin liest sie nicht: " + String.join(", ", e.unbekannt()));
            }
        } catch (IOException | InvalidConfigurationException | IllegalStateException e) {
            getLogger().log(Level.WARNING, "config.yml nicht ergänzt, es gelten die Vorgaben für fehlende Schlüssel", e);
        }
    }

    /** Packt die Karte aus dem Jar aus und startet den Server des Renderers. Siehe docs/webserver.md. */
    private void starteWebserver(Konfiguration konf, Path geheimnis) {
        Webserver.warnung(konf.webserver()).ifPresent(getLogger()::warning);
        Path web = getDataFolder().toPath().resolve("web");
        try {
            if (!Webserver.packeKarteAus(getFile().toPath(), web)) {
                getLogger().warning("Das Jar enthält keine Karte; der Webserver liefert nur /tiles/ aus.");
                web = null;
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Karte nicht ausgepackt; der Webserver liefert nur /tiles/ aus", e);
            web = null;
        }
        webserver = new Webserver(Webserver.befehl(konf, web, geheimnis), konf.renderer(), getLogger(),
                getDataFolder().toPath().resolve("webserver.pid"), Webserver.ERSTE_PAUSE, Webserver.STABIL);
        webserver.raeumeAuf();
        webserver.starte();
    }

    /**
     * Den Kanal zum Mod gibt es nur, wenn ein Baum zum Download angeboten wird. Gibt die Datei des
     * Geheimnisses für den Webserver, null ohne Geheimnis. Siehe docs/download.md.
     */
    private Path starteDownload(Konfiguration konf) {
        Path datei = getDataFolder().toPath().resolve("token.geheimnis");
        byte[] geheimnis;
        try {
            geheimnis = Download.geheimnis(datei, getLogger());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Geheimnis für die Token nicht gelesen, kein Download", e);
            return null;
        }
        // Token gibt es erst, wenn der Webserver lauscht; er startet nach dem Kanal.
        var w = konf.webserver();
        var ziel = w.url().isEmpty() ? new Download.Ziel(null, w.portFuerMod()) : new Download.Ziel(w.url() + "/download", 0);
        var download = new Download(konf, geheimnis,
                () -> webserver != null && webserver.bereit() ? Optional.of(ziel) : Optional.empty(),
                laeufe::erfolgreichSeit, ZoneId.systemDefault());
        var kanal = new Kanal(this, konf, download);
        kanal.starte();
        laeufe.nachLauf(kanal::aktualisiere);
        return datei;
    }

    @Override
    public void onDisable() {
        if (laeufe != null) {
            laeufe.stoppe();
        }
        if (webserver != null) {
            webserver.stoppe();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return false;
        }
        if (ohneRenderer != null && BEFEHLE.contains(args[0])) {
            sender.sendMessage(ohneRenderer);
            return true;
        }
        String antwort = switch (args[0]) {
            case "render" -> laeufe.starte(Laeufe.Art.VOLL);
            case "update" -> laeufe.starte(Laeufe.Art.UPDATE);
            case "status" -> laeufe.status() + (webserver != null ? "\n" + webserver.status() : "");
            case "cancel" -> laeufe.brichAb() ? "Der Lauf wird abgebrochen." : "Es läuft kein Lauf.";
            default -> null;
        };
        if (antwort == null) {
            return false;
        }
        sender.sendMessage(antwort);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? BEFEHLE.stream().filter(b -> b.startsWith(args[0])).toList() : List.of();
    }
}
