package com.nekyia.heroicmap;

import com.google.gson.JsonObject;
import com.nekyia.heroicmap.api.HeroicMapApi;
import com.nekyia.heroicmap.api.MapButtonClickEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
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
    static final String WEB = "heroicmap.web";
    /** Ein Anmeldelink gilt 5 min; einen neuen gibt es frühestens 15 s nach dem letzten. Siehe docs/anmeldung.md. */
    private static final long ANMELDUNG_GILT = 300;
    private static final long ANMELDUNG_ABSTAND = 15;
    /** Die ID des Plugins auf bstats.org. Siehe docs/statistik.md. */
    private static final int BSTATS = 34598;

    private Metrics metrics;

    private final Mitspieler mitspieler = new Mitspieler();
    /** Ob die Brücke zu Simple Voice Chat läuft. */
    private boolean simpleVoiceChat;

    private Ebenen ebenen;
    private EbenenFuerMod fuerMod;
    private Laeufe laeufe;
    private Banner banner;
    private volatile Webserver webserver;
    /** Der Abschnitt karte aus config.yml. Siehe docs/mehrere-server.md. */
    private Konfiguration.Karte kartenKonf = Konfiguration.Karte.OHNE;
    /** Nur im Hauptthread. */
    private Proxyname proxyname = new Proxyname(false);
    /** Die Anmeldung auf der Webkarte; null ohne. Siehe docs/anmeldung.md. */
    private volatile SpielerOrdner spieler;
    /** Die Köpfe der Spieler und der Nadeln von Ebenen; null mit karte.von. Siehe docs/koepfe.md. */
    private volatile Koepfe koepfe;
    private byte[] geheimnis;
    private String kartenUrl;
    /** url oder port für den Mod, wie in der Liste ebenen; leer, wenn er keine Adresse bekäme. */
    private JsonObject modAdresse = new JsonObject();
    /** Was beim Schreiben je Spieler gerade scheitert; so steht jeder Fehler einmal im Log. */
    private final Set<String> spielerScheitert = ConcurrentHashMap.newKeySet();
    /** Die IPs der Spieler online, zuletzt im Hauptthread gelesen; geschrieben wird immer die neueste. */
    private final AtomicReference<Map<UUID, InetAddress>> ips = new AtomicReference<>(Map.of());
    private final SecureRandom zufall = new SecureRandom();
    /** Nur im Hauptthread: je Spieler die Sekunde seines letzten Anmeldelinks. */
    private final Map<UUID, Long> letzteAnmeldung = new HashMap<>();
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
        // Für GetServer, den Namen dieses Servers vom Proxy; nur sicher hinter einem. Siehe docs/mehrere-server.md,
        // „Name vom Proxy“.
        boolean hinterProxy = Proxyname.hinterProxy(server);
        proxyname = new Proxyname(hinterProxy);
        if (hinterProxy) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, Proxyname.KANAL);
            getServer().getMessenger().registerIncomingPluginChannel(this, Proxyname.KANAL, this);
        }
        kartenKonf = konf.karte();
        getServer().getPluginManager().registerEvents(this, this);
        // Ebenen brauchen keinen Renderer; geladen wird ausserhalb des Hauptthreads. Siehe docs/ebenen.md.
        Path ebenenOrdner = getDataFolder().toPath().resolve("ebenen");
        // Mit karte.von und ihrer url die öffentlichen Ebenen der anderen Karte dazu. Siehe docs/mehrere-server.md,
        // „Karte von“.
        var von = konf.karte().vonUrl().isEmpty() ? null : new KarteVon(konf.karte().vonUrl(), konf.dimension());
        ebenen = new Ebenen(ebenenOrdner, konf.dimension(), new EbenenSchreiber(konf.kacheln(), konf.dimension(),
                Set.copyOf(konf.webserver().ebenenAn())), getLogger(),
                von == null ? List::of : von::stand);
        // Die Köpfe nur unter der eigenen Wurzel: Mit karte.von schreibt und löscht dieses Plugin keine. Die der
        // Ebenen holt es einen je Sekunde. Siehe docs/koepfe.md.
        if (konf.karte().von().isEmpty()) {
            var k = new Koepfe(konf.kacheln(), URI.create("https://textures.minecraft.net"), getLogger());
            koepfe = k;
            ebenen.api().koepfe(k);
            getServer().getAsyncScheduler().runAtFixedRate(this, t -> raeumeKoepfeAuf(k), 1, 24 * 60, TimeUnit.MINUTES);
            getServer().getAsyncScheduler().runAtFixedRate(this, t -> k.holeEinen(Instant.now()), 1, 1, TimeUnit.SECONDS);
        }
        // Die API für andere Plugins; ihre Ebenen gehen mit dem Plugin, dem sie gehören. Siehe docs/api.md.
        getServer().getServicesManager().register(HeroicMapApi.class, ebenen.api(), this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(ebenen.api(), this);
        getServer().getAsyncScheduler().runNow(this, t -> {
            String geladen = ebenen.ladeNeu();
            if (Files.isDirectory(ebenenOrdner)) {
                getLogger().info(geladen);
            }
        });
        // Die Adresse erst, wenn der Webserver bereit ist, wie bei freigabe. Mit karte.von-oeffentlich die der anderen
        // Karte, und ohne „Im Browser öffnen“: Das Token dieses Servers gilt dort nicht. Siehe docs/mehrere-server.md,
        // „Karte von“.
        var webKonf = konf.webserver();
        var vonAdresse = EbenenFuerMod.adresse(konf.karte());
        modAdresse = EbenenFuerMod.adresseFuerWeb(webKonf, konf.karte());
        fuerMod = new EbenenFuerMod(() -> vonAdresse.size() > 0 ? vonAdresse
                : webserver != null && webserver.bereit() ? EbenenFuerMod.adresse(webKonf) : new JsonObject(),
                Banner.geheim(getDataFolder().toPath()), getLogger(), ebenen.api().knoepfe());
        if (von != null) {
            getServer().getAsyncScheduler().runAtFixedRate(this, t -> holeVon(von), 0, 10, TimeUnit.SECONDS);
        }
        getServer().getAsyncScheduler().runAtFixedRate(this, t -> {
            ebenen.takt();
            fuerMod.bereite(ebenen.stand());
            var s = spieler;
            if (s != null) {
                schreibeSpieler("Dateien je Spieler", () -> s.schreibe(ebenen.stand(), ebenen.api().knoepfe(),
                        Instant.now().getEpochSecond()));
            }
        }, 1, 1, TimeUnit.SECONDS);
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, t -> sichtTakt(), 20, 20);
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, t -> ebenenAnMod(), 20, 20);
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
        // Die Sprites der Banner: nach dem ersten Schreiben der Ebenen, nach neuen Entwürfen und nach einem Lauf mit
        // neuen Bäumen; nie vor dem ersten Laden. Siehe docs/laeufe.md, „Banner“.
        // Mit karte.von-oeffentlich holt der Mod die Sprites bei der anderen Karte.
        if (konf.karte().vonOeffentlich().isEmpty()) {
            banner = new Banner(konf, List.of(konf.renderer().toString()), laeufe.quellen(), getLogger(),
                    getDataFolder().toPath(), ebenen::stand, ebenen::sprites);
            laeufe.nachLauf(banner::nachLauf);
            ebenen.nachEntwuerfen(banner::bestelle);
        }
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
        // Das Geheimnis für Download und Anmeldung; siehe docs/download.md und docs/anmeldung.md.
        boolean download = konf.baeume().stream().anyMatch(Konfiguration.Baum::download);
        Path geheimnisDatei = null;
        byte[] g = null;
        if (download || konf.webserver().an()) {
            geheimnisDatei = getDataFolder().toPath().resolve("token.geheimnis");
            try {
                g = Download.geheimnis(geheimnisDatei, getLogger());
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Geheimnis für die Token nicht gelesen, kein Download und keine Anmeldung", e);
                geheimnisDatei = null;
            }
        }
        if (download && g != null) {
            starteDownload(konf, g);
        }
        if (konf.webserver().an()) {
            starteWebserver(konf, geheimnisDatei, g);
        }
    }

    /**
     * Die Wahl show des Mods, im Hauptthread; die Antwort sagt, ob die Mitspieler hier gehen. Eine Anfrage nach einer
     * Tafel nimmt fuerMod nur an; beantwortet wird sie gleich ausserhalb, geschickt gleich danach im Hauptthread.
     * Siehe docs/entscheidungen/0010-tafeln-ueber-den-kanal.md.
     */
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals(Proxyname.KANAL) || channel.equals("bungeecord:main")) {
            if (proxyname.antwort(player.getUniqueId(), message)) {
                getLogger().info("Der Proxy meldet den Namen dieses Servers: " + proxyname.name());
            }
            return;
        }
        if (EbenenFuerMod.istKnopf(message)) {
            klick(player, message);
            return;
        }
        if (EbenenFuerMod.istTafel(message) || EbenenFuerMod.istBanner(message)) {
            frageTafel(player, message);
            return;
        }
        if (Download.istWeb(message)) {
            web(player);
            return;
        }
        // web: Dieser Spieler bekäme einen Link für „Im Browser öffnen“. Siehe docs/download.md, „Kanal“.
        boolean web = player.hasPermission(WEB) && spieler != null && modAdresse.size() > 0;
        var antwort = mitspieler.show(message, player.getUniqueId(), player.hasPermission(SHOW), simpleVoiceChat, web, Instant.now());
        if (antwort != null) {
            frageProxy(player);
            sendeShow(player, antwort);
        }
    }

    /**
     * Im Hauptthread: die Antwort auf show mit karte, dem Namen der Karte für den Ordner im Mod, und backend, dem
     * Namen dieses Servers. Wartet der Name noch
     * auf den Proxy, höchstens {@link Proxyname#FRIST_MS}, kommt sie danach; sonst schickt der Mod show erst nach einer
     * Änderung der Wahl. Siehe docs/mehrere-server.md, „Format“.
     */
    private void sendeShow(Player p, JsonObject antwort) {
        if (proxyname.wartet(System.currentTimeMillis())) {
            getServer().getGlobalRegionScheduler().runDelayed(this, t -> sendeShow(p, antwort), 2);
            return;
        }
        Proxyname.nenne(antwort, kartenKonf, proxyname.name());
        if (p.isOnline()) {
            p.sendPluginMessage(this, Download.KANAL, antwort.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Ausserhalb des Hauptthreads, alle 10 s: die öffentlichen Ebenen der anderen Karte; neu laden, wenn sie sich
     * änderten. Fehler stehen im Log, nur wenn sie sich ändern, im Anlauf nach dem Start als INFO
     * ({@link KarteVon#meldung}).
     */
    private void holeVon(KarteVon von) {
        var fehler = new ArrayList<String>();
        try {
            if (von.hole(fehler)) {
                ebenen.ladeNeu();
            }
        } catch (IOException e) {
            fehler.add(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        Level stufe = von.meldung(fehler, von.stand().size(), Instant.now());
        if (stufe != null) {
            fehler.forEach(f -> getLogger().log(stufe, "Ebenen von " + kartenKonf.von() + ": " + f));
            if (fehler.isEmpty()) {
                getLogger().info("Ebenen von " + kartenKonf.von() + ": " + von.stand().size() + " geholt");
            }
        }
    }

    /**
     * Im Hauptthread: fragt den Proxy über {@code p} nach dem Namen dieses Servers, wenn er noch fehlt. Nur hinter
     * einem Proxy ({@link Proxyname#frage}), und nur, wenn er den Kanal bei diesem Spieler angemeldet hat; Paper schickt
     * sonst nichts. Siehe docs/mehrere-server.md, „Name vom Proxy“.
     */
    private void frageProxy(Player p) {
        // Auch mit karte.von: Den eigenen Namen braucht backend an show.
        if (!proxyname.hinterProxy() || proxyname.name() != null) {
            return;
        }
        if (!p.getListeningPluginChannels().contains(Proxyname.KANAL)) {
            // Erst nach der Frist ins Log: Das Register des Proxys kommt manchmal kurz nach dem Join.
            if (proxyname.ohneKanal(System.currentTimeMillis())) {
                getServer().getGlobalRegionScheduler().runDelayed(this, t -> {
                    if (proxyname.ohneProxy(System.currentTimeMillis())) {
                        getLogger().info(Proxyname.ohneProxyZeile(kartenKonf));
                    }
                }, Proxyname.KANAL_FRIST_MS / 50 + 1);
            }
            return;
        }
        if (proxyname.frage(p.getUniqueId(), System.currentTimeMillis())) {
            p.sendPluginMessage(this, Proxyname.KANAL, Proxyname.frage());
        }
    }

    /**
     * Wer heroicmap.web nicht mehr hat, verliert beim Join jede Sitzung auf der Webkarte, wie mit /karte abmelden.
     * Siehe docs/anmeldung.md, „Befehl“.
     */
    @EventHandler
    public void beimBetreten(PlayerJoinEvent e) {
        var s = spieler;
        UUID u = e.getPlayer().getUniqueId();
        if (s != null && s.bekannt(u) && !e.getPlayer().hasPermission(WEB)) {
            getServer().getAsyncScheduler().runNow(this, t -> {
                try {
                    s.meldeAb(u);
                } catch (IOException ex) {
                    getLogger().log(Level.WARNING, "Abmelden von " + u + " ohne heroicmap.web ging nicht", ex);
                }
            });
        }
        ipsNeu(null);
        frageProxy(e.getPlayer());
        kopf(e.getPlayer());
    }

    /**
     * Im Hauptthread, nur mit Anmeldung: den Skin aus dem Profil lesen, den Kopf ausserhalb holen. Siehe
     * docs/koepfe.md.
     */
    private void kopf(Player p) {
        var k = koepfe;
        if (k == null || spieler == null) {
            return;
        }
        UUID u = p.getUniqueId();
        URL skin = p.getPlayerProfile().getTextures().getSkin();
        getServer().getAsyncScheduler().runNow(this, t -> k.betreten(u, skin));
    }

    /** Ausserhalb des Hauptthreads, beim Start und dann täglich: alte Köpfe weg. Siehe docs/koepfe.md, „Aufräumen“. */
    private void raeumeKoepfeAuf(Koepfe k) {
        try {
            int weg = k.raeumeAuf(Instant.now(), Koepfe.genannt(ebenen.stand()));
            if (weg > 0) {
                getLogger().info("Köpfe: " + weg + " alte gelöscht");
            }
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Köpfe nicht aufgeräumt", e);
        }
    }

    @EventHandler
    public void beimVerlassen(PlayerQuitEvent e) {
        mitspieler.vergiss(e.getPlayer().getUniqueId());
        var k = koepfe;
        if (k != null) {
            k.verlassen(e.getPlayer().getUniqueId());
        }
        letzteAnmeldung.remove(e.getPlayer().getUniqueId());
        if (fuerMod != null) {
            fuerMod.vergiss(e.getPlayer().getUniqueId());
        }
        ipsNeu(e.getPlayer().getUniqueId());
    }

    /**
     * Im Hauptthread: liest die IPs der Spieler online, ohne {@code geht}, und schreibt ips ausserhalb neu. Zwei
     * schnelle Aufrufe schreiben beide den neuesten Stand. Siehe docs/anmeldung.md, „Erkennung per IP“.
     */
    private void ipsNeu(UUID geht) {
        var s = spieler;
        if (s == null) {
            return;
        }
        var online = new HashMap<UUID, InetAddress>();
        for (Player p : getServer().getOnlinePlayers()) {
            var a = p.getAddress();
            if (!p.getUniqueId().equals(geht) && a != null && a.getAddress() != null) {
                online.put(p.getUniqueId(), a.getAddress());
            }
        }
        ips.set(Map.copyOf(online));
        getServer().getAsyncScheduler().runNow(this, t -> schreibeSpieler("ips", () -> s.schreibeIps(ips.get())));
    }

    /**
     * Im Hauptthread, jede Sekunde: die Sicht jedes Zuschauers online für sicht.json, aus seiner Lage und dem, was
     * Mitspieler im letzten Takt für ihn rechnete; geschrieben ausserhalb. Siehe docs/anmeldung.md, „Mitspieler“.
     */
    private void sichtTakt() {
        var s = spieler;
        if (s == null) {
            return;
        }
        long jetzt = Instant.now().getEpochSecond();
        var zuschauer = s.zuschauer(jetzt);
        var inhalte = new HashMap<UUID, JsonObject>();
        var namen = new HashMap<UUID, String>();
        for (Player p : getServer().getOnlinePlayers()) {
            UUID u = p.getUniqueId();
            if (zuschauer.contains(u)) {
                var l = p.getLocation();
                var ich = new Mitspieler.Spieler(u, p.getName(), p.getWorld().getKey().toString(), l.getX(), l.getY(),
                        l.getZ(), Mitspieler.Stimme.STUMM, true, false);
                var k = koepfe;
                inhalte.put(u, Mitspieler.sicht(ich, mitspieler.imWeb(u), s.dimension(), k == null ? v -> null : k::von));
                namen.put(u, p.getName());
            }
        }
        mitspieler.betrachter(inhalte.keySet());
        getServer().getAsyncScheduler().runNow(this, t -> schreibeSpieler("sicht.json", () -> s.schreibeSicht(inhalte, namen, jetzt)));
    }

    /** Im Faden des Webservers: eine Zeile Web:, also schaut dieser Spieler gerade. Siehe docs/anmeldung.md, „Zuschauer“. */
    private void zuschauer(String rest) {
        var s = spieler;
        var u = rest.length() == 36 ? SpielerOrdner.uuid(rest) : null;
        if (u == null) {
            getLogger().warning("Webserver: eine Zeile Web ohne gültige UUID");
        } else if (s != null) {
            s.schaut(u, Instant.now().getEpochSecond());
        }
    }

    /** Nimmt die Anfrage nach einer Tafel oder einem Banner an, beantwortet sie ausserhalb und schickt sie gleich danach. */
    private void frageTafel(Player player, byte[] message) {
        if (fuerMod != null && fuerMod.frage(player.getUniqueId(), message, Instant.now().getEpochSecond())) {
            getServer().getAsyncScheduler().runNow(this, t -> {
                fuerMod.beantworte(Instant.now().getEpochSecond());
                getServer().getGlobalRegionScheduler().execute(this, () -> tafelnAn(player));
            });
        }
    }

    /**
     * Im Hauptthread: ein Klick aus dem Mod. Höchstens 20 je Sekunde, mit heroicmap.layers, und nur, was
     * {@link EbenenFuerMod#klickbar} durchlässt; dann das Ereignis, und die Tafel geht neu an den Mod. Siehe
     * docs/ebenen.md, „Knöpfe“.
     */
    private void klick(Player player, byte[] message) {
        var k = EbenenFuerMod.klick(message);
        UUID u = player.getUniqueId();
        if (fuerMod == null || k == null || !fuerMod.darfKlicken(u, Instant.now().getEpochSecond())
                || !player.hasPermission(LAYERS)) {
            return;
        }
        var g = fuerMod.klickbar(u, k, player::hasPermission, false);
        if (g == null) {
            return;
        }
        getServer().getPluginManager().callEvent(new MapButtonClickEvent(g.knopf(), u, k.ebene(), k.id(), g.an()));
        frageTafel(player, EbenenFuerMod.tafelNach(k));
    }

    /**
     * Im Faden des Webservers: ein Klick von der Webkarte aus einer Zeile Aktion:. Im Hauptthread geprüft wie ein Klick
     * aus dem Mod, nur für einen angemeldeten Spieler und nur auf Ebenen der Webkarte ohne permission. Siehe
     * docs/anmeldung.md, „Klicks“.
     */
    private void webKlick(String json) {
        var w = EbenenFuerMod.webKlick(json);
        if (w == null) {
            getLogger().warning("Webserver: eine Zeile Aktion ohne gültigen Klick");
            return;
        }
        getServer().getGlobalRegionScheduler().execute(this, () -> {
            var s = spieler;
            UUID u = w.spieler();
            var k = w.klick();
            if (s == null || !s.bekannt(u) || !fuerMod.darfKlicken(u, Instant.now().getEpochSecond())) {
                return;
            }
            var g = fuerMod.klickbar(u, k, p -> false, true);
            if (g != null) {
                getServer().getPluginManager().callEvent(new MapButtonClickEvent(g.knopf(), u, k.ebene(), k.id(), g.an()));
            }
        });
    }

    /** Etwas, das unter plugins/HeroicMap/spieler/ schreibt. */
    private interface Schreiben {
        void run() throws IOException;
    }

    /** Ausserhalb des Hauptthreads: schreibt für die Webkarte; ein Fehler je {@code was} steht einmal im Log. */
    private void schreibeSpieler(String was, Schreiben s) {
        try {
            s.run();
            if (spielerScheitert.remove(was)) {
                getLogger().info(was + " für die Webkarte wieder geschrieben");
            }
        } catch (IOException | RuntimeException e) {
            if (spielerScheitert.add(was)) {
                getLogger().log(Level.WARNING, was + " für die Webkarte nicht geschrieben", e);
            }
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

    /**
     * Packt die Karte aus dem Jar aus und startet den Server des Renderers; mit Karte und Geheimnis samt Anmeldung.
     * Siehe docs/webserver.md.
     */
    private void starteWebserver(Konfiguration konf, Path geheimnisDatei, byte[] g) {
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
        Path ordner = web != null && g != null ? starteAnmeldung(konf, web, g) : null;
        webserver = new Webserver(Webserver.befehl(konf, web, geheimnisDatei, ordner), konf.renderer(), getLogger(),
                getDataFolder().toPath().resolve("webserver.pid"), Webserver.ERSTE_PAUSE, Webserver.STABIL,
                Map.of(Webserver.AKTION, this::webKlick, Webserver.WEB, this::zuschauer));
        ipsNeu(null);
        webserver.raeumeAuf();
        webserver.starte();
    }

    /**
     * Der Ordner je Spieler für --player-dir, null ohne Anmeldung: nie unter den Kacheln oder der Karte, sonst startete
     * der Server nicht. Siehe docs/anmeldung.md.
     */
    private Path starteAnmeldung(Konfiguration konf, Path web, byte[] g) {
        Path ordner = getDataFolder().toPath().resolve("spieler").toAbsolutePath().normalize();
        for (Path p : List.of(konf.kacheln(), web)) {
            if (ordner.startsWith(p.toAbsolutePath().normalize())) {
                getLogger().warning("Keine Anmeldung auf der Webkarte: " + ordner + " liegt unter " + p);
                return null;
            }
        }
        geheimnis = g;
        kartenUrl = konf.webserver().url();
        try {
            Files.createDirectories(ordner);
            // Zuletzt: Mit spieler gilt die Anmeldung als bereit.
            spieler = new SpielerOrdner(ordner, konf.dimension(), g);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Ordner je Spieler nicht gelesen, keine Anmeldung auf der Webkarte", e);
            return null;
        }
        if (kartenUrl.isEmpty()) {
            getLogger().warning("Ohne webserver.url gibt /karte anmelden keinen Link.");
        }
        // Erst jetzt, mit spieler: Köpfe der Spieler gibt es nur mit Anmeldung. Siehe docs/koepfe.md.
        getServer().getOnlinePlayers().forEach(this::kopf);
        return ordner;
    }

    /** Den Kanal zum Mod gibt es nur, wenn ein Baum zum Download angeboten wird. Siehe docs/download.md. */
    private void starteDownload(Konfiguration konf, byte[] geheimnis) {
        // Token gibt es erst, wenn der Webserver lauscht; er startet nach dem Kanal.
        var w = konf.webserver();
        var ziel = w.url().isEmpty() ? new Download.Ziel(null, w.portFuerMod()) : new Download.Ziel(w.url() + "/download", 0);
        Download.warnung(konf.download()).ifPresent(getLogger()::warning);
        var download = new Download(konf, geheimnis,
                () -> webserver != null && webserver.bereit() ? Optional.of(ziel) : Optional.empty(),
                laeufe::erfolgreichSeit, ZoneId.systemDefault());
        var kanal = new Kanal(this, konf, download);
        kanal.starte();
        laeufe.nachLauf(kanal::aktualisiere);
    }

    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics.shutdown();
        }
        if (laeufe != null) {
            laeufe.stoppe();
        }
        if (banner != null) {
            banner.stoppe();
        }
        if (webserver != null) {
            webserver.stoppe();
        }
        // Nach dem Server: Ohne ihn fragt niemand mehr, und keine IP bleibt in ips.
        var s = spieler;
        if (s != null) {
            schreibeSpieler("ips", () -> s.schreibeIps(Map.of()));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("karte")) {
            return karte(sender, args);
        }
        // Nur render nimmt ein zweites Wort, den Ordner eines Baums. Siehe docs/laeufe.md, „Befehle“.
        if (args.length != 1 && !(args.length == 2 && args[0].equals("render"))) {
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
            case "render" -> laeufe.starte(Laeufe.Art.VOLL, args.length == 2 ? args[1] : null);
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

    /** /karte anmelden|abmelden: die Anmeldung auf der Webkarte. Siehe docs/anmeldung.md, „Befehl“. */
    private boolean karte(CommandSender sender, String[] args) {
        boolean ip = args.length == 2 && args[0].equals("ip") && (args[1].equals("an") || args[1].equals("aus"));
        if (!ip && (args.length != 1 || !args[0].equals("anmelden") && !args[0].equals("abmelden"))) {
            return false;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur ein Spieler meldet sich auf der Webkarte an.");
            return true;
        }
        var s = spieler;
        if (s == null || !ip && kartenUrl.isEmpty()) {
            p.sendMessage("Eine Anmeldung auf der Webkarte gibt es auf diesem Server nicht.");
            return true;
        }
        UUID u = p.getUniqueId();
        if (ip) {
            boolean an = args[1].equals("an");
            getServer().getAsyncScheduler().runNow(this, t -> {
                String z;
                try {
                    s.ip(u, an);
                    z = an ? "Erkennung per IP an: Bist du als einziger Spieler mit deiner IP online, zeigt dir die Webkarte "
                            + "deine Sicht und deine Ebenen ohne Anmeldung. Klicken geht nur nach /karte anmelden."
                            : "Erkennung per IP aus: Die Webkarte erkennt dich nur nach /karte anmelden.";
                } catch (IOException e) {
                    getLogger().log(Level.WARNING, "Erkennung per IP für " + u + " nicht umgestellt", e);
                    z = "Das ging nicht; sag es bitte einem Admin.";
                }
                String antwort = z;
                getServer().getGlobalRegionScheduler().execute(this, () -> {
                    ipsNeu(null);
                    p.sendMessage(antwort);
                });
            });
            return true;
        }
        if (args[0].equals("abmelden")) {
            getServer().getAsyncScheduler().runNow(this, t -> {
                String z;
                try {
                    z = s.meldeAb(u) ? "Abgemeldet: Jede Sitzung auf der Webkarte ist beendet." : "Du bist auf der Webkarte nicht angemeldet.";
                } catch (IOException e) {
                    getLogger().log(Level.WARNING, "Abmelden von " + u + " ging nicht", e);
                    z = "Abmelden ging nicht; sag es bitte einem Admin.";
                }
                String antwort = z;
                getServer().getGlobalRegionScheduler().execute(this, () -> p.sendMessage(antwort));
            });
            return true;
        }
        String grund = stelleAnmeldungAus(p, token -> p.sendMessage(token == null
                ? Component.text("Anmelden ging nicht; sag es bitte einem Admin.")
                // Im Fragment: Es steht in keinem Log und geht in keinem Referer mit.
                : Component.text("Anmelden auf der Webkarte, 5 min lang und nur einmal: ").append(Component.text("Link öffnen")
                        .decorate(TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl(kartenUrl + "/#anmelden=" + token)))));
        if ("rate".equals(grund)) {
            p.sendMessage("Einen neuen Link gibt es frühestens 15 s nach dem letzten.");
        } else if (grund != null) {
            p.sendMessage("Die Webkarte läuft gerade nicht; versuch es gleich noch einmal.");
        }
        return true;
    }

    /**
     * Im Hauptthread: ein Token der Anmeldung für {@code p}, für /karte anmelden und die Nachricht web des Mods, mit
     * derselben Rate. Gibt, warum nicht: webserver, wenn es keine Anmeldung gibt oder der Webserver nicht bereit ist,
     * rate, wenn der letzte Link keine 15 s her ist. Sonst null, und {@code fertig} bekommt im Hauptthread das Token,
     * oder null, wenn die Platte nicht ging. Siehe docs/anmeldung.md, „Befehl“.
     */
    private String stelleAnmeldungAus(Player p, Consumer<String> fertig) {
        var s = spieler;
        if (s == null || webserver == null || !webserver.bereit()) {
            return "webserver";
        }
        UUID u = p.getUniqueId();
        long jetzt = Instant.now().getEpochSecond();
        if (wieder(u, jetzt) > 0) {
            return "rate";
        }
        letzteAnmeldung.put(u, jetzt);
        String name = p.getName();
        getServer().getAsyncScheduler().runNow(this, t -> {
            String token = null;
            try {
                long zaehler = s.meldeAn(u, name);
                byte[] z = new byte[Token.ZUFALL];
                zufall.nextBytes(z);
                token = Token.anmeldung(geheimnis, u, jetzt + ANMELDUNG_GILT, zaehler, z);
            } catch (IOException e) {
                // Nie das Token ins Log; hier gibt es noch keins.
                getLogger().log(Level.WARNING, "Anmelden von " + u + " ging nicht", e);
            }
            String fertigesToken = token;
            getServer().getGlobalRegionScheduler().execute(this, () -> fertig.accept(fertigesToken));
        });
        return null;
    }

    /** Wie viele Sekunden {@code u} noch auf den nächsten Anmeldelink wartet; 0, wenn er jetzt einen bekommt. */
    private long wieder(UUID u, long jetzt) {
        Long vorher = letzteAnmeldung.get(u);
        return vorher == null ? 0 : Math.max(0, ANMELDUNG_ABSTAND - (jetzt - vorher));
    }

    /**
     * Im Hauptthread: die Nachricht web aus dem Mod, „Im Browser öffnen“. Dieselbe Logik und Rate wie /karte anmelden;
     * den Link baut der Mod aus der Adresse, von der er die Kacheln holt. Siehe docs/download.md, „Kanal“.
     */
    private void web(Player p) {
        String grund = !p.hasPermission(WEB) ? "permission" : modAdresse.size() == 0 ? "webserver" : null;
        if (grund == null) {
            grund = stelleAnmeldungAus(p, token -> antworteWeb(p, token, "sonst"));
        }
        if (grund != null) {
            antworteWeb(p, null, grund);
        }
    }

    private void antworteWeb(Player p, String token, String grund) {
        if (!p.isOnline()) {
            return;
        }
        long jetzt = Instant.now().getEpochSecond();
        var a = Download.web(token, grund, "rate".equals(grund) ? wieder(p.getUniqueId(), jetzt) : null, Instant.ofEpochSecond(jetzt));
        p.sendPluginMessage(this, Download.KANAL, a.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("karte")) {
            var w = args.length == 1 ? Stream.of("anmelden", "abmelden", "ip")
                    : args.length == 2 && args[0].equals("ip") ? Stream.of("an", "aus") : Stream.<String>empty();
            return w.filter(b -> b.startsWith(args[args.length - 1])).toList();
        }
        if (args.length == 2 && args[0].equals("render") && laeufe != null) {
            return laeufe.ordner().stream().filter(o -> o.startsWith(args[1])).toList();
        }
        return args.length == 1 ? Stream.concat(BEFEHLE.stream(), Stream.of("layers"))
                .filter(b -> b.startsWith(args[0])).toList() : List.of();
    }
}
