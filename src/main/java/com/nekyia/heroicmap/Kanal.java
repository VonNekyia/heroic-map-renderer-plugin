package com.nekyia.heroicmap;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Der Plugin-Kanal zum Mod: Nachrichten hin und her, der Stand je Spieler im
 * PersistentDataContainer, die Sätze aus den Manifesten. Siehe docs/download.md.
 */
final class Kanal implements Listener, PluginMessageListener {

    private final JavaPlugin plugin;
    private final Konfiguration konf;
    private final Download download;
    private final NamespacedKey schluessel;
    private final Map<String, Satz> saetze = new ConcurrentHashMap<>();
    private final Map<String, FileTime> gelesen = new ConcurrentHashMap<>();
    private final Set<UUID> gewarnt = ConcurrentHashMap.newKeySet();

    Kanal(JavaPlugin plugin, Konfiguration konf, Download download) {
        this.plugin = plugin;
        this.konf = konf;
        this.download = download;
        this.schluessel = new NamespacedKey(plugin, "download");
    }

    void starte() {
        var m = plugin.getServer().getMessenger();
        m.registerOutgoingPluginChannel(plugin, Download.KANAL);
        m.registerIncomingPluginChannel(plugin, Download.KANAL, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        aktualisiere();
    }

    /**
     * Liest die Manifeste neu, die sich geändert haben, ausserhalb des Hauptthreads, und schickt
     * dann allen mit Mod das Angebot.
     */
    void aktualisiere() {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> liesSaetze());
    }

    /** Nacheinander, damit ein älterer Satz nie einen neueren überschreibt. */
    private synchronized void liesSaetze() {
        boolean anders = false;
        for (var b : download.angeboteneBaeume()) {
            var ordner = konf.kacheln().resolve(b.ordner());
            try {
                var zeit = Files.getLastModifiedTime(ordner.resolve("manifest"));
                if (zeit.equals(gelesen.get(b.ordner()))) {
                    continue;
                }
                gelesen.put(b.ordner(), zeit);
                saetze.put(b.ordner(), Satz.lies(ordner));
                anders = true;
            } catch (NoSuchFileException e) {
                gelesen.remove(b.ordner());
                anders |= saetze.remove(b.ordner()) != null;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Manifest von " + b.ordner() + " nicht gelesen", e);
                anders |= saetze.remove(b.ordner()) != null;
            }
        }
        download.saetze(saetze);
        if (anders && plugin.isEnabled()) {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, this::angebotAnAlle);
        }
    }

    /** Nach neuen Sätzen: allen mit Mod das Angebot, und der tägliche Abgleich, wo er fällig ist. */
    private void angebotAnAlle() {
        gemessen("Angebot an alle", () -> {
            var jetzt = Instant.now();
            var angebot = download.angebot(jetzt);
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getListeningPluginChannels().contains(Download.KANAL)) {
                    sende(p, angebot);
                    taeglich(p, jetzt);
                }
            }
        });
    }

    private void taeglich(Player p, Instant jetzt) {
        var stand = lies(p);
        var raus = download.beimJoin(p.getUniqueId(), stand, jetzt);
        if (!raus.isEmpty()) {
            schreibe(p, stand);
            raus.forEach(n -> sende(p, n));
        }
    }

    /** Der Mod meldet seinen Kanal an: Angebot, dann der tägliche Abgleich, falls fällig. */
    @EventHandler
    public void beimAnmelden(PlayerRegisterChannelEvent e) {
        if (!e.getChannel().equals(Download.KANAL)) {
            return;
        }
        var p = e.getPlayer();
        gemessen("Anmelden von " + p.getName(), () -> {
            var jetzt = Instant.now();
            sende(p, download.angebot(jetzt));
            taeglich(p, jetzt);
        });
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // show, tafel und banner beantwortet HeroicMapPlugin; hier wären sie unlesbare Anfragen.
        if (!channel.equals(Download.KANAL) || Mitspieler.istShow(message) || EbenenFuerMod.istTafel(message)
                || EbenenFuerMod.istBanner(message)) {
            return;
        }
        gemessen("Anfrage von " + player.getName(), () -> {
            var stand = lies(player);
            var antwort = download.anfrage(message, player.getUniqueId(), stand, Instant.now());
            schreibe(player, stand);
            sende(player, antwort);
        });
    }

    /**
     * Ein Handler im Hauptthread, gemessen: die Dauer auf FINE, ab 1 ms auf INFO. Siehe
     * docs/download.md, „Kanal“.
     */
    private void gemessen(String was, Runnable r) {
        long beginn = System.nanoTime();
        r.run();
        long mikro = (System.nanoTime() - beginn) / 1000;
        plugin.getLogger().log(mikro >= 1000 ? Level.INFO : Level.FINE,
                () -> "Kanal: " + was + " im Hauptthread in " + mikro + " µs");
    }

    /** Ein unlesbarer Stand beginnt neu; die Warnung kommt einmal je Spieler und Start, nicht je Anfrage. */
    private Download.Spielerstand lies(Player p) {
        String json = p.getPersistentDataContainer().get(schluessel, PersistentDataType.STRING);
        var s = json == null ? new Download.Spielerstand() : Download.ausJson(json);
        if (s == null) {
            if (gewarnt.add(p.getUniqueId())) {
                plugin.getLogger().warning("Stand des Downloads von " + p.getName() + " unlesbar, er beginnt neu");
            }
            return new Download.Spielerstand();
        }
        return s;
    }

    private void schreibe(Player p, Download.Spielerstand s) {
        p.getPersistentDataContainer().set(schluessel, PersistentDataType.STRING, Download.alsJson(s));
    }

    private void sende(Player p, JsonObject nachricht) {
        p.sendPluginMessage(plugin, Download.KANAL, nachricht.toString().getBytes(StandardCharsets.UTF_8));
    }
}
