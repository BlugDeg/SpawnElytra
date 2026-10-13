package ch.blugdeg.spawnelytra;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class SpawnElytraCommand implements TabExecutor {

    private final SpawnElytraPlugin plugin;

    public SpawnElytraCommand(SpawnElytraPlugin plugin) {
        this.plugin = plugin;
    }

    private static void say(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Component.text(text, color));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "hilfe" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pos1", "pos2" -> {
                if (!(sender instanceof Player p) || !admin(sender)) return true;
                int corner = sub.equals("pos1") ? 1 : 2;
                plugin.setCorner(corner, p.getLocation());
                say(sender, "Insel-Ecke " + corner + " gesetzt: " + Area.BlockPos.of(p.getLocation()).serialize(),
                        NamedTextColor.GREEN);
                Area a = plugin.island();
                say(sender, a == null ? "Insel noch unvollständig (andere Ecke fehlt oder andere Welt)." : "Insel: " + a.describe(),
                        a == null ? NamedTextColor.YELLOW : NamedTextColor.GRAY);
            }
            case "setspawn" -> {
                if (!(sender instanceof Player p) || !admin(sender)) return true;
                plugin.setSpawnPoint(p.getLocation());
                say(sender, "Spawnpunkt gesetzt (Erstjoin, Respawn ohne Bett/Anker, Auffangschutz).", NamedTextColor.GREEN);
                if (!plugin.onIsland(p.getLocation())) {
                    say(sender, "Achtung: Der Spawnpunkt liegt NICHT auf der Insel – dort gibt es keine Elytra.", NamedTextColor.YELLOW);
                }
            }
            case "info" -> {
                say(sender, "Insel:      " + (plugin.island() == null ? "nicht gesetzt" : plugin.island().describe()), NamedTextColor.AQUA);
                say(sender, "Spawnpunkt: " + (plugin.spawnPoint() == null ? "nicht gesetzt" : Area.BlockPos.of(plugin.spawnPoint()).serialize()), NamedTextColor.AQUA);
                if (sender instanceof Player p) {
                    say(sender, "Du: auf Insel=" + plugin.onIsland(p.getLocation())
                            + ", berechtigt=" + plugin.flights().isArmed(p)
                            + ", fliegt=" + plugin.flights().isFlying(p)
                            + ", bypass=" + plugin.canBypass(p)
                            + ", Modus=" + p.getGameMode(), NamedTextColor.GRAY);
                }
            }
            case "bypass" -> {
                if (!(sender instanceof Player p)) return true;
                if (!p.hasPermission("spawnelytra.bypass")) {
                    say(sender, "Keine Berechtigung.", NamedTextColor.RED);
                    return true;
                }
                boolean on = plugin.toggleBypass(p);
                say(sender, "Insel-Bypass " + (on ? "AN – du darfst auf der Insel bauen." : "AUS."),
                        on ? NamedTextColor.GOLD : NamedTextColor.GREEN);
            }
            case "test" -> {
                if (!(sender instanceof Player p) || !admin(sender)) return true;
                if (plugin.flights().armIfOnIsland(p, p.getLocation())) {
                    say(sender, "Berechtigt – spring ab und drück in der Luft die Leertaste. (Überlebens-/Abenteuermodus nötig)",
                            NamedTextColor.GREEN);
                } else {
                    say(sender, "Du musst auf der Insel stehen (und darfst nicht schon fliegen).", NamedTextColor.RED);
                }
            }
            case "debug" -> {
                if (!(sender instanceof Player p) || !admin(sender)) return true;
                boolean on = plugin.flights().toggleDebug(p);
                say(sender, "Debug-Ausgaben " + (on ? "AN" : "AUS") + ".", NamedTextColor.GRAY);
            }
            case "reload" -> {
                if (!admin(sender)) return true;
                plugin.reloadAll();
                say(sender, "SpawnElytra neu geladen.", NamedTextColor.GREEN);
            }
            default -> {
                say(sender, "SpawnElytra", NamedTextColor.AQUA);
                say(sender, "/" + label + " pos1 | pos2   – Ecken der Insel (deine Position, inkl. Luftraum)", NamedTextColor.GRAY);
                say(sender, "/" + label + " setspawn      – Spawnpunkt auf der Insel", NamedTextColor.GRAY);
                say(sender, "/" + label + " info | bypass | test | debug | reload", NamedTextColor.GRAY);
            }
        }
        return true;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("spawnelytra.admin")) return true;
        say(s, "Keine Berechtigung.", NamedTextColor.RED);
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return List.of("pos1", "pos2", "setspawn", "info", "bypass", "test", "debug", "reload", "hilfe")
                    .stream().filter(o -> o.startsWith(p)).toList();
        }
        return List.of();
    }
}
