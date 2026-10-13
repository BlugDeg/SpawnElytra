package ch.blugdeg.spawnelytra;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Geister-Elytra:
 * - "berechtigt" wird nur, wer im Absprungbereich ERSCHEINT (Join, Respawn) – nicht wer hineinfliegt/-laeuft.
 * - Verlaesst ein Berechtigter den Bereich in der Luft, startet der Flug (Gliding wird erzwungen,
 *   der Client bekommt nur eine vorgetaeuschte Elytra, es existiert kein echtes Item).
 * - Boost mit F (Haende tauschen), Cooldown, nach Ablauf der Flugzeit Tragflaechenverlust.
 * - Bodenkontakt, Wasser, Tod, Teleport, Logout usw. beenden alles sofort.
 */
public final class FlightManager implements Listener {

    private static final ItemStack GHOST_ELYTRA = new ItemStack(Material.ELYTRA);

    private final SpawnElytraPlugin plugin;
    private final Set<UUID> armed = new HashSet<>();
    private final Map<UUID, Flight> flights = new HashMap<>();
    private final Map<UUID, Long> graceUntil = new HashMap<>();
    private long tick;
    private BukkitTask task;

    private static final class Flight {
        final long startTick;
        long lastBoostTick = Long.MIN_VALUE / 4;
        int boostTicksLeft;
        Location lastLoc;
        Vector velocity = new Vector();

        Flight(long startTick, Location loc) {
            this.startTick = startTick;
            this.lastLoc = loc;
        }
    }

    public FlightManager(SpawnElytraPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

    public void endAll() {
        for (UUID id : new ArrayList<>(flights.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) endFlight(p, false);
        }
        flights.clear();
        armed.clear();
        if (task != null) task.cancel();
    }

    public boolean isFlying(Player p) {
        return flights.containsKey(p.getUniqueId());
    }

    public boolean isArmed(Player p) {
        return armed.contains(p.getUniqueId());
    }

    // --- Berechtigung --------------------------------------------------------

    /** Berechtigt den Spieler, wenn er sich im Absprungbereich befindet. */
    public boolean armIfInside(Player p, Location where) {
        Area launch = plugin.launchArea();
        if (launch == null || !launch.contains(where)) return false;
        if (isFlying(p)) return false;
        armed.add(p.getUniqueId());
        p.sendActionBar(plugin.msg("armed"));
        return true;
    }

    private void disarm(Player p) {
        armed.remove(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Location sp = plugin.spawnPoint();
        if (!p.hasPlayedBefore() && sp != null) {
            p.teleport(sp);
        }
        armIfInside(p, p.getLocation());
    }

    /** Respawn ohne Bett/Anker -> eigener Spawnpunkt (falls gesetzt). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawnLocation(PlayerRespawnEvent e) {
        Location sp = plugin.spawnPoint();
        if (sp != null && !e.isBedSpawn() && !e.isAnchorSpawn()) {
            e.setRespawnLocation(sp);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        endFlight(p, false);
        disarm(p);
        Location to = e.getRespawnLocation();
        // Einen Tick spaeter, wenn der Spieler wirklich dort steht
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) armIfInside(p, to);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        endFlight(e.getPlayer(), false);
        armed.remove(id);
        graceUntil.remove(id);
        plugin.forgetBypass(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        endFlight(e.getEntity(), false);
        disarm(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!armed.contains(p.getUniqueId())) return;
        Area launch = plugin.launchArea();
        if (launch == null) {
            disarm(p);
            return;
        }
        Location to = e.getTo();
        if (launch.contains(to)) return;

        // Bereich verlassen: in der Luft -> Flug, sonst Berechtigung weg
        disarm(p);
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
        if (p.isInsideVehicle() || p.isFlying()) return;
        ItemStack chest = p.getInventory().getChestplate();
        if (chest != null && chest.getType() == Material.ELYTRA) return; // hat eine echte
        if (!p.isOnGround() && heightAboveGround(to) >= plugin.settings().minHeightAboveGround()) {
            startFlight(p, to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        endFlight(p, false);
        Area launch = plugin.launchArea();
        if (launch == null || !launch.contains(e.getTo())) disarm(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        GameMode gm = e.getNewGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            endFlight(e.getPlayer(), false);
        }
    }

    // --- Flug ----------------------------------------------------------------

    private void startFlight(Player p, Location loc) {
        Flight f = new Flight(tick, loc.clone());
        flights.put(p.getUniqueId(), f);
        p.setGliding(true);
        sendGhost(p);
        p.playSound(loc, Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 1.2f);
        p.sendActionBar(plugin.msg("start"));
    }

    private void endFlight(Player p, boolean notify) {
        Flight f = flights.remove(p.getUniqueId());
        if (f == null) return;
        graceUntil.put(p.getUniqueId(), tick + plugin.settings().graceTicks());
        if (!p.isOnline()) return;
        p.setGliding(false);
        restoreEquipment(p);
        if (notify) {
            p.sendActionBar(plugin.msg("end"));
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 15, 0.4, 0.4, 0.4, 0.02);
            p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.7f, 1.6f);
        }
    }

    /** Nur der Client sieht eine Elytra im Brustslot – serverseitig bleibt alles unveraendert. */
    private void sendGhost(Player p) {
        p.sendEquipmentChange(p, EquipmentSlot.CHEST, GHOST_ELYTRA);
    }

    private void restoreEquipment(Player p) {
        ItemStack real = p.getInventory().getChestplate();
        p.sendEquipmentChange(p, EquipmentSlot.CHEST, real == null ? new ItemStack(Material.AIR) : real);
        p.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!e.isGliding() && flights.containsKey(p.getUniqueId())) {
            // Server will das Gleiten beenden, weil keine echte Elytra getragen wird
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        Flight f = flights.get(p.getUniqueId());
        if (f == null) return;
        e.setCancelled(true);
        Settings s = plugin.settings();
        if (tick - f.startTick >= s.flightTicks()) {
            p.sendActionBar(plugin.msg("no-boost"));
            return;
        }
        if (tick - f.lastBoostTick < s.boostCooldownTicks()) return; // HUD zeigt den Cooldown
        f.lastBoostTick = tick;
        f.boostTicksLeft = s.boostDurationTicks();
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c != EntityDamageEvent.DamageCause.FALL && c != EntityDamageEvent.DamageCause.FLY_INTO_WALL) return;
        Long grace = graceUntil.get(p.getUniqueId());
        if (flights.containsKey(p.getUniqueId()) || (grace != null && tick <= grace)) {
            e.setCancelled(true);
        }
    }

    private void tickAll() {
        tick++;
        if (!graceUntil.isEmpty()) graceUntil.values().removeIf(t -> t < tick);
        if (flights.isEmpty()) return;

        Settings s = plugin.settings();
        for (Map.Entry<UUID, Flight> entry : new ArrayList<>(flights.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            Flight f = entry.getValue();
            if (p == null || !p.isOnline()) {
                flights.remove(entry.getKey());
                continue;
            }
            Location loc = p.getLocation();
            if (f.lastLoc.getWorld() == loc.getWorld()) {
                // Spieler-Velocity ist serverseitig unzuverlaessig -> aus der Positionsaenderung schaetzen
                f.velocity = loc.toVector().subtract(f.lastLoc.toVector());
            }
            f.lastLoc = loc;
            long age = tick - f.startTick;

            GameMode gm = p.getGameMode();
            boolean landed = age >= 2 && (p.isOnGround() || p.isInWater() || p.isInLava());
            if (landed || p.isDead() || p.isInsideVehicle() || age > s.maxFlightTicks()
                    || gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
                endFlight(p, true);
                continue;
            }
            if (!p.isGliding()) p.setGliding(true);

            boolean decaying = age >= s.flightTicks();
            if (decaying) f.boostTicksLeft = 0;

            if (f.boostTicksLeft > 0) {
                // Gleiche Formel wie eine Vanilla-Rakete beim Elytra-Flug
                Vector look = loc.getDirection();
                Vector v = f.velocity.clone();
                v.add(look.clone().multiply(0.1)
                        .add(look.clone().multiply(s.boostSpeed()).subtract(v).multiply(0.5)));
                p.setVelocity(v);
                f.boostTicksLeft--;
                p.getWorld().spawnParticle(Particle.FIREWORK, loc, 2, 0.1, 0.1, 0.1, 0.02);
            } else if (decaying) {
                double d = Math.min(1.0, (age - s.flightTicks()) / (double) s.decayRampTicks());
                Vector v = f.velocity.clone();
                double drag = 1.0 - s.decayDrag() * d;
                v.setX(v.getX() * drag);
                v.setZ(v.getZ() * drag);
                v.setY(Math.max(-s.decayMaxSink(), v.getY() - s.decaySink() * d));
                p.setVelocity(v);
            }

            if (age % 10 == 0) {
                sendGhost(p); // falls ein Inventar-Update die Fake-Elytra ueberschrieben hat
                sendHud(p, f, age, decaying);
            }
        }
    }

    private void sendHud(Player p, Flight f, long age, boolean decaying) {
        Settings s = plugin.settings();
        if (decaying) {
            p.sendActionBar(plugin.msg("hud-decay"));
            return;
        }
        long left = (s.flightTicks() - age + 19) / 20;
        long cdTicks = s.boostCooldownTicks() - (tick - f.lastBoostTick);
        if (cdTicks > 0) {
            p.sendActionBar(plugin.msg("hud-cooldown",
                    Placeholder.unparsed("time", String.valueOf(left)),
                    Placeholder.unparsed("cd", String.valueOf((cdTicks + 19) / 20))));
        } else {
            p.sendActionBar(plugin.msg("hud-ready", Placeholder.unparsed("time", String.valueOf(left))));
        }
    }

    /** Abstand nach unten bis zum ersten nicht-passierbaren Block (gedeckelt). */
    private static double heightAboveGround(Location loc) {
        int max = 16;
        Block start = loc.getBlock();
        for (int i = 0; i <= max; i++) {
            Block b = start.getRelative(0, -i, 0);
            if (b.getY() < loc.getWorld().getMinHeight()) return max;
            if (!b.isPassable()) return loc.getY() - (b.getY() + 1);
        }
        return max;
    }
}
