package ch.blugdeg.spawnelytra;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Geister-Elytra auf der Insel:
 * - "Berechtigt" ist nur, wer auf der Insel SPAWNT (Erstjoin, Respawn ohne Bett/Anker, Auffangschutz).
 *   Wer hineinfliegt, -maced oder -teleportiert, bekommt nichts.
 * - Berechtigte druecken in der Luft die Leertaste -> Gleiten (wie Vanilla). Der Client bekommt nur eine
 *   vorgetaeuschte Elytra, ein echtes Item existiert nicht.
 * - Boost mit F (Haende tauschen), Cooldown, nach Ablauf der Flugzeit Tragflaechenverlust.
 * - Bodenkontakt, Wasser, Tod, Teleport, Logout usw. beenden den Flug sofort.
 * - Auffangschutz: Wer als Berechtigter nicht gleitet und mit Fallschaden aufschlaegt (oder ins Void faellt),
 *   wird ohne Schaden zum Spawn teleportiert.
 */
public final class FlightManager implements Listener {

    private static final ItemStack GHOST_ELYTRA = new ItemStack(Material.ELYTRA);
    /** So viele Ticks darf ein Berechtigter ausserhalb der Insel auf dem Boden stehen, bevor die Berechtigung verfaellt. */
    private static final int GROUND_TICKS_OUTSIDE = 5;

    private final SpawnElytraPlugin plugin;
    private final NamespacedKey keyArmed;
    private final NamespacedKey keyRescue;

    /** berechtigte Spieler -> Ticks, die sie ausserhalb der Insel auf dem Boden standen */
    private final Map<UUID, Integer> armed = new HashMap<>();
    private final Map<UUID, Flight> flights = new HashMap<>();
    private final Map<UUID, Long> graceUntil = new HashMap<>();
    private final Map<UUID, Boolean> lastJump = new HashMap<>();
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
        this.keyArmed = new NamespacedKey(plugin, "armed");
        this.keyRescue = new NamespacedKey(plugin, "rescue");
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

    public void endAll() {
        for (Player p : Bukkit.getOnlinePlayers()) saveState(p);
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
        return armed.containsKey(p.getUniqueId());
    }

    // --- Berechtigung --------------------------------------------------------

    /** Berechtigt den Spieler, wenn der Ort auf der Insel liegt. */
    public boolean armIfOnIsland(Player p, Location where) {
        if (!plugin.onIsland(where) || isFlying(p)) return false;
        armed.put(p.getUniqueId(), 0);
        p.sendActionBar(plugin.msg("armed"));
        return true;
    }

    private void disarm(Player p) {
        armed.remove(p.getUniqueId());
    }

    /** Zum Spawn teleportieren und berechtigen (Auffangschutz, Login nach Absturz im Flug). */
    private boolean rescue(Player p) {
        Location sp = plugin.spawnPoint();
        if (sp == null) return false;
        endFlight(p, false);
        disarm(p);
        p.setFallDistance(0);
        p.setVelocity(new Vector());
        p.teleport(sp);
        graceUntil.put(p.getUniqueId(), tick + plugin.settings().graceTicks());
        armIfOnIsland(p, sp);
        return true;
    }

    /** Beim Verlassen merken, damit ein Relog nichts verschenkt und niemand in der Luft einloggt. */
    private void saveState(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.remove(keyArmed);
        pdc.remove(keyRescue);
        UUID id = p.getUniqueId();
        boolean onIsland = plugin.onIsland(p.getLocation());
        if (flights.containsKey(id) || (armed.containsKey(id) && !onIsland)) {
            pdc.set(keyRescue, PersistentDataType.BYTE, (byte) 1);
        } else if (armed.containsKey(id)) {
            pdc.set(keyArmed, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        boolean wasArmed = pdc.has(keyArmed, PersistentDataType.BYTE);
        boolean needsRescue = pdc.has(keyRescue, PersistentDataType.BYTE);
        pdc.remove(keyArmed);
        pdc.remove(keyRescue);

        if (!p.hasPlayedBefore() || needsRescue) {
            if (rescue(p)) return;
        }
        // Erstjoin ohne gesetzten Spawnpunkt: Vanilla-Spawn zaehlt, falls er auf der Insel liegt
        if (wasArmed || !p.hasPlayedBefore()) armIfOnIsland(p, p.getLocation());
    }

    /** Respawn ohne Bett/Anker -> Spawnpunkt auf der Insel. */
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
            if (p.isOnline()) armIfOnIsland(p, to);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        saveState(p);
        endFlight(p, false);
        armed.remove(id);
        graceUntil.remove(id);
        lastJump.remove(id);
        plugin.forgetBypass(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        endFlight(e.getEntity(), false);
        disarm(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        endFlight(p, false);
        // Teleport INNERHALB der Insel behaelt die Berechtigung, hinein gibt es keine neue.
        if (!plugin.onIsland(e.getTo())) disarm(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        GameMode gm = e.getNewGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            endFlight(e.getPlayer(), false);
            disarm(e.getPlayer());
        }
    }

    // --- Start per Leertaste ---------------------------------------------------

    @EventHandler
    public void onInput(PlayerInputEvent e) {
        Player p = e.getPlayer();
        boolean jump = e.getInput().isJump();
        boolean wasJump = Boolean.TRUE.equals(lastJump.put(p.getUniqueId(), jump));
        if (!jump || wasJump) return; // nur beim Druecken, nicht beim Halten
        if (!armed.containsKey(p.getUniqueId()) || isFlying(p)) return;
        if (canStartGlide(p)) {
            disarm(p);
            startFlight(p, p.getLocation());
        }
    }

    /** Gleiche Bedingungen wie Vanilla: in der Luft, nicht im Wasser, kein Fahrzeug, keine Schwebe. */
    private boolean canStartGlide(Player p) {
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return false;
        if (p.isOnGround() || p.isGliding() || p.isFlying() || p.isInsideVehicle()) return false;
        if (p.isInWater() || p.isInLava() || p.isClimbing()) return false;
        if (p.hasPotionEffect(PotionEffectType.LEVITATION)) return false;
        ItemStack chest = p.getInventory().getChestplate();
        return chest == null || chest.getType() != Material.ELYTRA; // mit echter Elytra greift Vanilla
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
        // Gelandet auf der Insel (nie weg gewesen) -> wieder berechtigt
        if (notify && plugin.onIsland(p.getLocation())) armIfOnIsland(p, p.getLocation());
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

    // --- Schaden: Flug-Schutz und Auffangschutz -----------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        UUID id = p.getUniqueId();
        EntityDamageEvent.DamageCause c = e.getCause();

        // Void: Fliegende und Berechtigte werden gerettet
        if (c == EntityDamageEvent.DamageCause.VOID && (flights.containsKey(id) || armed.containsKey(id))) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> rescue(p));
            return;
        }
        if (c != EntityDamageEvent.DamageCause.FALL && c != EntityDamageEvent.DamageCause.FLY_INTO_WALL) return;

        Long grace = graceUntil.get(id);
        if (flights.containsKey(id) || (grace != null && tick <= grace)) {
            e.setCancelled(true);
            return;
        }
        // Auffangschutz: von der Insel gefallen, ohne zu gleiten
        if (c == EntityDamageEvent.DamageCause.FALL && armed.containsKey(id) && !plugin.onIsland(p.getLocation())) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && rescue(p)) p.sendActionBar(plugin.msg("rescued"));
            });
        }
    }

    // --- Tick -------------------------------------------------------------------

    private void tickAll() {
        tick++;
        if (!graceUntil.isEmpty()) graceUntil.values().removeIf(t -> t < tick);
        if (!armed.isEmpty()) tickArmed();
        if (!flights.isEmpty()) tickFlights();
    }

    /** Berechtigte ausserhalb der Insel, die stehen bleiben (ohne Fallschaden), verlieren die Berechtigung. */
    private void tickArmed() {
        for (Map.Entry<UUID, Integer> entry : new ArrayList<>(armed.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline() || plugin.island() == null) {
                armed.remove(entry.getKey());
                continue;
            }
            Location loc = p.getLocation();
            if (plugin.onIsland(loc)) {
                armed.put(entry.getKey(), 0);
            } else if (p.isOnGround() || p.isInWater() || p.isInLava() || p.isInsideVehicle()) {
                int ground = entry.getValue() + 1;
                if (ground > GROUND_TICKS_OUTSIDE) armed.remove(entry.getKey());
                else armed.put(entry.getKey(), ground);
                continue;
            }
            // Hinweis beim Fallen
            if (!p.isOnGround() && p.getFallDistance() > 3f && tick % 10 == 0) {
                p.sendActionBar(plugin.msg("falling"));
            }
        }
    }

    private void tickFlights() {
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
}
