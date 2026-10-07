package com.nekyia.heroicmap;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Die Brücke zu Simple Voice Chat, die einzige Klasse, die seine API berührt; geladen nur, wenn er auf dem
 * Server liegt. Einmal je Sekunde im Hauptthread: Lage und Stimme jedes Spielers, dann die Nachrichten aus
 * {@link Mitspieler#takt}. Siehe docs/mitspieler.md.
 */
final class Sprachchat implements VoicechatPlugin {

    private final JavaPlugin plugin;
    private final Mitspieler mitspieler = new Mitspieler();
    private volatile VoicechatServerApi api;

    private Sprachchat(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Meldet sich bei Simple Voice Chat an und startet den Takt. Nur aus onEnable: Simple Voice Chat nimmt
     * Plugins bis zu seinem Start im ersten Tick an. Siehe docs/mitspieler.md, „Simple Voice Chat“.
     */
    static void starte(JavaPlugin plugin) {
        var dienst = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (dienst == null) {
            plugin.getLogger().warning("autogroup: Simple Voice Chat bietet seine API nicht an; niemand sieht andere Spieler.");
            return;
        }
        var s = new Sprachchat(plugin);
        dienst.registerPlugin(s);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, Download.KANAL);
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, t -> s.takt(), 20, 20);
    }

    @Override
    public String getPluginId() {
        return "heroicmap";
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatServerApi server) {
            this.api = server;
        }
    }

    private void takt() {
        var a = api;
        if (a == null) {
            return;
        }
        var alle = new ArrayList<Mitspieler.Spieler>();
        var mitKanal = new HashSet<UUID>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            var l = p.getLocation();
            alle.add(new Mitspieler.Spieler(p.getUniqueId(), p.getName(), p.getWorld().getKey().toString(),
                    l.getX(), l.getY(), l.getZ(), stimme(a, p)));
            if (p.getListeningPluginChannels().contains(Download.KANAL)) {
                mitKanal.add(p.getUniqueId());
            }
        }
        mitspieler.takt(alle, mitKanal, a.getVoiceChatDistance(), Instant.now()).forEach((uuid, n) -> {
            var p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                p.sendPluginMessage(plugin, Download.KANAL, n.toString().getBytes(StandardCharsets.UTF_8));
            }
        });
    }

    /** Gruppe, Verbindung, Ton und Rechte, wie Simple Voice Chat sie prüft. Siehe docs/mitspieler.md, „Wer wen hört“. */
    private static Mitspieler.Stimme stimme(VoicechatServerApi a, Player p) {
        var c = a.getConnectionOf(p.getUniqueId());
        if (c == null || !c.isConnected()) {
            return Mitspieler.Stimme.STUMM;
        }
        Group g = c.getGroup();
        Mitspieler.Typ typ = g == null ? null
                : g.getType() == Group.Type.OPEN ? Mitspieler.Typ.OFFEN
                : g.getType() == Group.Type.ISOLATED ? Mitspieler.Typ.ISOLIERT
                : Mitspieler.Typ.NORMAL;
        return new Mitspieler.Stimme(g == null ? null : g.getId(), typ, p.hasPermission("voicechat.speak"),
                !c.isDisabled() && p.hasPermission("voicechat.listen"));
    }
}
