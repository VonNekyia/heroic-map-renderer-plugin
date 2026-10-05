package com.nekyia.heroicmap;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
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

    private static final Gson GSON = new Gson();

    private final JavaPlugin plugin;
    private final Konfiguration konf;
    private final Download download;
    private final NamespacedKey schluessel;
    private final Map<String, Satz> saetze = new ConcurrentHashMap<>();
    private final Map<String, FileTime> gelesen = new ConcurrentHashMap<>();

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
        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
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
            if (anders) {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, this::angebotAnAlle);
            }
        });
    }

    private void angebotAnAlle() {
        var angebot = download.angebot(Instant.now());
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getListeningPluginChannels().contains(Download.KANAL)) {
                sende(p, angebot);
            }
        }
    }

    /** Der Mod meldet seinen Kanal an: Angebot, dann der tägliche Abgleich, falls fällig. */
    @EventHandler
    public void beimAnmelden(PlayerRegisterChannelEvent e) {
        if (!e.getChannel().equals(Download.KANAL)) {
            return;
        }
        var p = e.getPlayer();
        var jetzt = Instant.now();
        sende(p, download.angebot(jetzt));
        var stand = lies(p);
        var raus = download.beimJoin(p.getUniqueId(), stand, jetzt);
        if (!raus.isEmpty()) {
            schreibe(p, stand);
            raus.forEach(n -> sende(p, n));
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(Download.KANAL)) {
            return;
        }
        var stand = lies(player);
        var antwort = download.anfrage(message, player.getUniqueId(), stand, Instant.now());
        schreibe(player, stand);
        sende(player, antwort);
    }

    private Download.Spielerstand lies(Player p) {
        String json = p.getPersistentDataContainer().get(schluessel, PersistentDataType.STRING);
        if (json != null) {
            try {
                var s = GSON.fromJson(json, Download.Spielerstand.class);
                if (s != null && s.voll != null && s.abgleich != null && s.baeume != null) {
                    return s;
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Stand des Downloads von " + p.getName() + " unlesbar, neu", e);
            }
        }
        return new Download.Spielerstand();
    }

    private void schreibe(Player p, Download.Spielerstand s) {
        p.getPersistentDataContainer().set(schluessel, PersistentDataType.STRING, GSON.toJson(s));
    }

    private void sende(Player p, JsonObject nachricht) {
        p.sendPluginMessage(plugin, Download.KANAL, nachricht.toString().getBytes(StandardCharsets.UTF_8));
    }
}
