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
    private Area launchArea;
    private Area protectArea;
    private Location spawnPoint;
    private FlightManager flights;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadAll();

        flights = new FlightManager(this);
        getServer().getPluginManager().registerEvents(flights, this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        flights.start();

        PluginCommand cmd = getCommand("spawnelytra");
        if (cmd != null) {
            SpawnElytraCommand handler = new SpawnElytraCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        if (launchArea == null) getLogger().warning("Absprungbereich ist noch nicht gesetzt (/se pos1|pos2 launch).");
        if (protectArea == null) getLogger().warning("Spawnschutz-Bereich ist noch nicht gesetzt (/se pos1|pos2 protect).");
    }

    @Override
    public void onDisable() {
        if (flights != null) flights.endAll();
    }

    public void reloadAll() {
        reloadConfig();
        settings = Settings.load(getConfig());
        launchArea = loadArea("launch");
        protectArea = loadArea("protect");
        spawnPoint = parseSpawn(getConfig().getString("spawn-point", ""));
    }

    private Area loadArea(String key) {
        return Area.of(
                Area.BlockPos.parse(getConfig().getString("areas." + key + ".pos1", "")),
                Area.BlockPos.parse(getConfig().getString("areas." + key + ".pos2", "")));
    }

    /** Setzt eine Ecke, speichert die Config und laedt neu. */
    public void setCorner(String area, int corner, Location loc) {
        getConfig().set("areas." + area + ".pos" + corner, Area.BlockPos.of(loc).serialize());
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

    // --- Spawnschutz-Helfer -------------------------------------------------

    public boolean isProtected(Location loc) {
        return protectArea != null && protectArea.contains(loc);
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
    public Area launchArea() { return launchArea; }
    public Area protectArea() { return protectArea; }
    public Location spawnPoint() { return spawnPoint == null ? null : spawnPoint.clone(); }
    public FlightManager flights() { return flights; }
}
