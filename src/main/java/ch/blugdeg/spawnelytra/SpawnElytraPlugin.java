package ch.blugdeg.spawnelytra;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SpawnElytraPlugin extends JavaPlugin {

    private final Set<UUID> bypassing = new HashSet<>();
    private Settings settings;
    private Area island;
    private Location spawnPoint;
    private FlightManager flights;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        reloadAll();

        flights = new FlightManager(this);
        getServer().getPluginManager().registerEvents(flights, this);
        ProtectionListener protection = new ProtectionListener(this);
        getServer().getPluginManager().registerEvents(protection, this);
        flights.start();
        protection.startMobSweep();

        PluginCommand cmd = getCommand("spawnelytra");
        if (cmd != null) {
            SpawnElytraCommand handler = new SpawnElytraCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        if (island == null) getLogger().warning("Insel ist noch nicht gesetzt (/se pos1 und /se pos2).");
        if (spawnPoint == null) getLogger().warning("Spawnpunkt ist noch nicht gesetzt (/se setspawn).");
    }

    @Override
    public void onDisable() {
        if (flights != null) flights.endAll();
    }

    /** Alte Konfiguration (launch/protect) auf die Insel umstellen und neue Standardwerte ergaenzen. */
    private void migrateConfig() {
        var c = getConfig();
        boolean changed = false;
        if (c.getString("island.pos1", "").isBlank() && !c.getString("areas.protect.pos1", "").isBlank()) {
            c.set("island.pos1", c.getString("areas.protect.pos1"));
            c.set("island.pos2", c.getString("areas.protect.pos2"));
            getLogger().info("Alter Schutzbereich als Insel uebernommen – bitte mit /se info pruefen.");
            changed = true;
        }
        if (c.contains("areas")) {
            c.set("areas", null);
            c.set("messages", null); // Texte haben sich geaendert -> neue Standardtexte uebernehmen
            changed = true;
        }
        if (c.contains("protection.full-height")) {
            c.set("protection.full-height", null);
            changed = true;
        }
        c.options().copyDefaults(true);
        saveConfig();
        if (changed) getLogger().info("config.yml auf das Insel-Format umgestellt.");
    }

    public void reloadAll() {
        reloadConfig();
        settings = Settings.load(getConfig());
        island = Area.of(
                Area.BlockPos.parse(getConfig().getString("island.pos1", "")),
                Area.BlockPos.parse(getConfig().getString("island.pos2", "")));
        spawnPoint = parseSpawn(getConfig().getString("spawn-point", ""));
    }

    /** Setzt eine Ecke der Insel, speichert die Config und laedt neu. */
    public void setCorner(int corner, Location loc) {
        getConfig().set("island.pos" + corner, Area.BlockPos.of(loc).serialize());
        saveConfig();
        reloadAll();
    }

    public void setSpawnPoint(Location loc) {
        String s = loc.getWorld().getName() + "," + round(loc.getX()) + "," + round(loc.getY()) + ","
                + round(loc.getZ()) + "," + round(loc.getYaw()) + "," + round(loc.getPitch());
        getConfig().set("spawn-point", s);
        saveConfig();
        reloadAll();
    }

    private static String round(double d) {
        return String.valueOf(Math.round(d * 100) / 100.0);
    }

    private static Location parseSpawn(String s) {
        if (s == null || s.isBlank()) return null;
        String[] p = s.split(",");
        if (p.length != 6) return null;
        World w = Bukkit.getWorld(p[0].trim());
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()),
                    Double.parseDouble(p[3].trim()), Float.parseFloat(p[4].trim()), Float.parseFloat(p[5].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Component msg(String key, TagResolver... resolvers) {
        String raw = getConfig().getString("messages." + key, key);
        return MiniMessage.miniMessage().deserialize(raw, resolvers);
    }

    // --- Insel / Schutz -----------------------------------------------------

    public boolean onIsland(Location loc) {
        return island != null && island.contains(loc);
    }

    /** Schutz gilt genau im Insel-Quader. */
    public boolean isProtected(Location loc) {
        return onIsland(loc);
    }

    public boolean canBypass(Player p) {
        return p != null && bypassing.contains(p.getUniqueId()) && p.hasPermission("spawnelytra.bypass");
    }

    /** true = jetzt aktiv */
    public boolean toggleBypass(Player p) {
        if (bypassing.remove(p.getUniqueId())) return false;
        bypassing.add(p.getUniqueId());
        return true;
    }

    public void forgetBypass(UUID id) {
        bypassing.remove(id);
    }

    // --- Getter --------------------------------------------------------------

    public Settings settings() { return settings; }
    public Area island() { return island; }
    public Location spawnPoint() { return spawnPoint == null ? null : spawnPoint.clone(); }
    public FlightManager flights() { return flights; }
}
