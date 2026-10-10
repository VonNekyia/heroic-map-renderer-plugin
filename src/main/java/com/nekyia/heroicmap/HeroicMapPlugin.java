package com.nekyia.heroicmap;

import com.google.gson.JsonObject;
import com.nekyia.heroicmap.api.HeroicMapApi;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Startet den Renderer nach Zeitplan und per Befehl, dazu seinen Server und die Mitspieler auf der Karte;
 * beantwortet show vom Mod. Siehe docs/laeufe.md, docs/webserver.md, docs/mitspieler.md.
 */
public final class HeroicMapPlugin extends JavaPlugin implements PluginMessageListener, Listener {

    private static final List<String> BEFEHLE = List.of("render", "update", "compact", "status", "cancel");
    /** Wer sie hat, sieht Mitspieler und wird gesehen. Siehe docs/mitspieler.md, „Wer wen sieht“. */
    static final String SHOW = "heroicmap.show";
    /** Wer sie hat, bekommt im Mod die Ebenen. Siehe docs/ebenen.md, „Mod“. */
    static final String LAYERS = "heroicmap.layers";
    /** Die ID des Plugins auf bstats.org. Siehe docs/statistik.md. */
    private static final int BSTATS = 34598;

    private Metrics metrics;

    private final Mitspieler mitspieler = new Mitspieler();
    /** Ob die Brücke zu Simple Voice Chat läuft. */
    private boolean simpleVoiceChat;

    private Ebenen ebenen;
    private EbenenFuerMod fuerMod;
    private Laeufe laeufe;
    private volatile Webserver webserver;
    /** Warum das Plugin ohne Renderer bleibt; null mit Renderer. Jeder Befehl nennt es. */
    private String ohneRenderer;

    @Override
    public void onEnable() {
        metrics = new Metrics(this, BSTATS);
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
        simpleVoiceChat = Mitspieler.starte(getServer().getPluginManager().isPluginEnabled("voicechat"), getLogger(),
                () -> Sprachchat.starte(this, mitspieler));
        // Zum Empfangen auch ohne Simple Voice Chat, damit show eine Antwort mit Grund bekommt.
        getServer().getMessenger().registerOutgoingPluginChannel(this, Download.KANAL);
        getServer().getMessenger().registerIncomingPluginChannel(this, Download.KANAL, this);
        getServer().getPluginManager().registerEvents(this, this);
        // Ebenen brauchen keinen Renderer; geladen wird ausserhalb des Hauptthreads. Siehe docs/ebenen.md.
        Path ebenenOrdner = getDataFolder().toPath().resolve("ebenen");
        ebenen = new Ebenen(ebenenOrdner, konf.dimension(), new EbenenSchreiber(konf.kacheln(), konf.dimension()), getLogger());
        // Die API für andere Plugins; ihre Ebenen gehen mit dem Plugin, dem sie gehören. Siehe docs/api.md.
        getServer().getServicesManager().register(HeroicMapApi.class, ebenen.api(), this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(ebenen.api(), this);
        getServer().getAsyncScheduler().runNow(this, t -> {
            String geladen = ebenen.ladeNeu();
            if (Files.isDirectory(ebenenOrdner)) {
                getLogger().info(geladen);
            }
        });
        // Die Adresse erst, wenn der Webserver bereit ist, wie bei freigabe.
        var webKonf = konf.webserver();
        fuerMod = new EbenenFuerMod(() -> webserver != null && webserver.bereit() ? EbenenFuerMod.adresse(webKonf) : new JsonObject());
        getServer().getAsyncScheduler().runAtFixedRate(this, t -> {
            ebenen.takt();
            fuerMod.bereite(ebenen.stand());
        }, 1, 1, TimeUnit.SECONDS);
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, t -> ebenenAnMod(), 20, 20);
        try {
            konf = konf.mitRenderer(Binaer.waehle(konf.renderer(), getFile().toPath(), getDataFolder().toPath().resolve("bin"),
                    System.getProperty("os.name"), System.getProperty("os.arch"), getPluginMeta().getVersion()));
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

    /**
     * Die Wahl show des Mods, im Hauptthread; die Antwort sagt, ob die Mitspieler hier gehen. Eine Anfrage nach einer
     * Tafel nimmt fuerMod nur an; beantwortet wird sie gleich ausserhalb, geschickt gleich danach im Hauptthread.
     * Siehe docs/entscheidungen/0010-tafeln-ueber-den-kanal.md.
     */
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (EbenenFuerMod.istTafel(message)) {
            if (fuerMod != null && fuerMod.frage(player.getUniqueId(), message, Instant.now().getEpochSecond())) {
                getServer().getAsyncScheduler().runNow(this, t -> {
                    fuerMod.beantworte(Instant.now().getEpochSecond());
                    getServer().getGlobalRegionScheduler().execute(this, () -> tafelnAn(player));
                });
            }
            return;
        }
        var antwort = mitspieler.show(message, player.getUniqueId(), player.hasPermission(SHOW), simpleVoiceChat, Instant.now());
        if (antwort != null) {
            player.sendPluginMessage(this, Download.KANAL, antwort.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    @EventHandler
    public void beimVerlassen(PlayerQuitEvent e) {
        mitspieler.vergiss(e.getPlayer().getUniqueId());
        if (fuerMod != null) {
            fuerMod.vergiss(e.getPlayer().getUniqueId());
        }
    }

    /** Im Hauptthread: die fertigen Antworten auf Tafeln an {@code p}, im Budget der laufenden Sekunde. */
    private void tafelnAn(Player p) {
        if (!p.isOnline() || !p.getListeningPluginChannels().contains(Download.KANAL)) {
            return;
        }
        for (String n : fuerMod.tafeln(p.getUniqueId(), p.hasPermission(LAYERS), p::hasPermission, Instant.now().getEpochSecond())) {
            p.sendPluginMessage(this, Download.KANAL, n.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Im Hauptthread, jede Sekunde: jedem Spieler mit offenem Kanal die Ebenen, die er sehen darf. Siehe docs/ebenen.md, „Mod“. */
    private void ebenenAnMod() {
        long jetzt = Instant.now().getEpochSecond();
        for (Player p : getServer().getOnlinePlayers()) {
            if (!p.getListeningPluginChannels().contains(Download.KANAL)) {
                fuerMod.vergiss(p.getUniqueId());
                continue;
            }
            for (String n : fuerMod.nachrichten(p.getUniqueId(), p.hasPermission(LAYERS), p::hasPermission, jetzt)) {
                p.sendPluginMessage(this, Download.KANAL, n.getBytes(StandardCharsets.UTF_8));
            }
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
        if (metrics != null) {
            metrics.shutdown();
        }
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
        if (args[0].equals("layers")) {
            sender.sendMessage("Ebenen werden neu geladen.");
            getServer().getAsyncScheduler().runNow(this, t -> {
                String z = ebenen.ladeNeu();
                getServer().getGlobalRegionScheduler().execute(this, () -> sender.sendMessage(z));
            });
            return true;
        }
        if (ohneRenderer != null && BEFEHLE.contains(args[0])) {
            sender.sendMessage(ohneRenderer + (args[0].equals("status") ? "\n" + ebenen.status() : ""));
            return true;
        }
        String antwort = switch (args[0]) {
            case "render" -> laeufe.starte(Laeufe.Art.VOLL);
            case "update" -> laeufe.starte(Laeufe.Art.UPDATE);
            case "compact" -> laeufe.starte(Laeufe.Art.VERDICHTEN);
            case "status" -> laeufe.status() + (webserver != null ? "\n" + webserver.status() : "") + "\n" + ebenen.status();
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
        return args.length == 1 ? Stream.concat(BEFEHLE.stream(), Stream.of("layers"))
                .filter(b -> b.startsWith(args[0])).toList() : List.of();
    }
}
