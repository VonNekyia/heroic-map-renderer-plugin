package com.nekyia.heroicmap;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Startet den Renderer nach Zeitplan und per Befehl. Siehe docs/laeufe.md. */
public final class HeroicMapPlugin extends JavaPlugin {

    private static final List<String> BEFEHLE = List.of("render", "update", "status", "cancel");

    private Laeufe laeufe;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Path server = Path.of("").toAbsolutePath();
        Path hauptwelt = getServer().getWorldContainer().toPath().toAbsolutePath()
                .resolve(getServer().getWorlds().getFirst().getName());
        Konfiguration konf;
        try {
            konf = Konfiguration.aus(getConfig(), server, hauptwelt);
        } catch (IllegalArgumentException e) {
            getLogger().severe("config.yml: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        laeufe = new Laeufe(konf, getLogger(), getDataFolder().toPath().resolve("renderer.pid"));
        laeufe.raeumeAuf();
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
    }

    @Override
    public void onDisable() {
        if (laeufe != null) {
            laeufe.stoppe();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return false;
        }
        String antwort = switch (args[0]) {
            case "render" -> laeufe.starte(Laeufe.Art.VOLL);
            case "update" -> laeufe.starte(Laeufe.Art.UPDATE);
            case "status" -> laeufe.status();
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
