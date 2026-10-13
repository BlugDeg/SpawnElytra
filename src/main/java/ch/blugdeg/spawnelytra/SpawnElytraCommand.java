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
                if (args.length < 2 || !(args[1].equalsIgnoreCase("launch") || args[1].equalsIgnoreCase("protect"))) {
                    say(sender, "/" + label + " " + sub + " <launch|protect>", NamedTextColor.RED);
                    return true;
                }
                String area = args[1].toLowerCase(Locale.ROOT);
                int corner = sub.equals("pos1") ? 1 : 2;
                plugin.setCorner(area, corner, p.getLocation());
                Area a = area.equals("launch") ? plugin.launchArea() : plugin.protectArea();
                say(sender, "Ecke " + corner + " von '" + area + "' gesetzt: " + Area.BlockPos.of(p.getLocation()).serialize(),
                        NamedTextColor.GREEN);
                say(sender, a == null ? "Bereich noch unvollständig (andere Ecke fehlt oder andere Welt)." : "Bereich: " + a.describe(),
                        a == null ? NamedTextColor.YELLOW : NamedTextColor.GRAY);
            }
            case "setspawn" -> {
                if (!(sender instanceof Player p) || !admin(sender)) return true;
                plugin.setSpawnPoint(p.getLocation());
                say(sender, "Spawnpunkt gesetzt (Erstjoin + Respawn ohne Bett/Anker).", NamedTextColor.GREEN);
                if (plugin.launchArea() != null && !plugin.launchArea().contains(p.getLocation())) {
                    say(sender, "Achtung: Der Spawnpunkt liegt NICHT im Absprungbereich.", NamedTextColor.YELLOW);
                }
            }
            case "info" -> {
                say(sender, "Absprungbereich: " + (plugin.launchArea() == null ? "nicht gesetzt" : plugin.launchArea().describe()), NamedTextColor.AQUA);
                say(sender, "Spawnschutz:     " + (plugin.protectArea() == null ? "nicht gesetzt" : plugin.protectArea().describe()), NamedTextColor.AQUA);
                say(sender, "Spawnpunkt:      " + (plugin.spawnPoint() == null ? "Vanilla" : Area.BlockPos.of(plugin.spawnPoint()).serialize()), NamedTextColor.AQUA);
                if (sender instanceof Player p) {
                    say(sender, "Du: im Absprungbereich=" + (plugin.launchArea() != null && plugin.launchArea().contains(p.getLocation()))
                            + ", im Schutz=" + plugin.isProtected(p.getLocation())
                            + ", berechtigt=" + plugin.flights().isArmed(p)
                            + ", fliegt=" + plugin.flights().isFlying(p)
                            + ", bypass=" + plugin.canBypass(p), NamedTextColor.GRAY);
                }
            }
            case "bypass" -> {
                if (!(sender instanceof Player p)) return true;
                if (!p.hasPermission("spawnelytra.bypass")) {
                    say(sender, "Keine Berechtigung.", NamedTextColor.RED);
                    return true;
                }
                boolean on = plugin.toggleBypass(p);
                say(sender, "Spawnschutz-Bypass " + (on ? "AN – du darfst im Spawn bauen." : "AUS."),
                        on ? NamedTextColor.GOLD : NamedTextColor.GREEN);
            }
            case "test" -> {
                if (!(sender instanceof Player p) || !admin(sender)) return true;
                if (plugin.flights().armIfInside(p, p.getLocation())) {
                    say(sender, "Berechtigt – spring jetzt ab.", NamedTextColor.GREEN);
                } else {
                    say(sender, "Du musst im Absprungbereich stehen (und darfst nicht schon fliegen).", NamedTextColor.RED);
                }
            }
            case "reload" -> {
                if (!admin(sender)) return true;
                plugin.reloadAll();
                say(sender, "SpawnElytra neu geladen.", NamedTextColor.GREEN);
            }
            default -> {
                say(sender, "SpawnElytra", NamedTextColor.AQUA);
                say(sender, "/" + label + " pos1|pos2 launch   – Ecke des Absprungbereichs (deine Position)", NamedTextColor.GRAY);
                say(sender, "/" + label + " pos1|pos2 protect  – Ecke des Spawnschutzes", NamedTextColor.GRAY);
                say(sender, "/" + label + " setspawn          – Spawnpunkt für Erstjoin/Respawn", NamedTextColor.GRAY);
                say(sender, "/" + label + " info | bypass | test | reload", NamedTextColor.GRAY);
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
            return filter(List.of("pos1", "pos2", "setspawn", "info", "bypass", "test", "reload", "hilfe"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("pos1") || args[0].equalsIgnoreCase("pos2"))) {
            return filter(List.of("launch", "protect"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(p)).toList();
    }
}
