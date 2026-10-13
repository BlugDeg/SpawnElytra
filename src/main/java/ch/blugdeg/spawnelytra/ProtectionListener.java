package ch.blugdeg.spawnelytra;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Eigener Spawnschutz im Bereich "protect":
 * kein Bauen/Abbauen, kein PvP, keine Gegenstaende (Raketen, Windkugeln, Perlen, Boegen ...),
 * keine Truhen/Tueren/Knoepfe, kein Mob-Spawning, keine Explosionen/Feuer/Fluessigkeiten von aussen.
 * Ausnahme: Spieler mit spawnelytra.bypass, die /se bypass aktiviert haben.
 */
public final class ProtectionListener implements Listener {

    private static final Set<CreatureSpawnEvent.SpawnReason> ALLOWED_SPAWNS = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.CUSTOM,
            CreatureSpawnEvent.SpawnReason.COMMAND,
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);

    private final SpawnElytraPlugin plugin;

    public ProtectionListener(SpawnElytraPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean prot(Location loc) {
        return plugin.isProtected(loc);
    }

    private boolean prot(Block b) {
        return b != null && plugin.isProtected(b.getLocation());
    }

    /** Spieler darf hier nicht – mit Hinweis in der Actionbar. */
    private boolean deny(Player p) {
        if (plugin.canBypass(p)) return false;
        p.sendActionBar(plugin.msg("protected"));
        return true;
    }

    private static Player playerOf(Entity e) {
        if (e instanceof Player p) return p;
        if (e instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
        }
        return null;
    }

    // --- Bauen / Abbauen -----------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (prot(e.getBlock()) && deny(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { // deckt auch BlockMultiPlaceEvent ab
        if (prot(e.getBlock()) && deny(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (prot(e.getBlock()) && deny(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (prot(e.getBlock()) && deny(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (prot(e.getEntity().getLocation()) && (e.getPlayer() == null || deny(e.getPlayer()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (!prot(e.getEntity().getLocation())) return;
        if (e instanceof HangingBreakByEntityEvent be) {
            Player p = playerOf(be.getRemover());
            if (p != null && plugin.canBypass(p)) return;
        }
        e.setCancelled(true);
    }

    // --- Benutzen von Bloecken und Gegenstaenden ----------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (plugin.canBypass(p)) return;
        Block clicked = e.getClickedBlock();
        boolean inside = prot(p.getLocation()) || prot(clicked);
        if (!inside) return;

        if (e.getAction() == Action.PHYSICAL) {
            // Ackerboden zertrampeln, Druckplatten, Stolperdraht
            e.setCancelled(true);
            return;
        }
        boolean right = e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_AIR;
        if (!right) return; // Linksklick = Abbauen -> BlockBreakEvent

        // Truhen, Tueren, Knoepfe, Hebel, Werkbaenke ... alles gesperrt
        boolean blockUse = clicked != null && clicked.getType().isInteractable();
        e.setUseInteractedBlock(Event.Result.DENY);

        ItemStack item = e.getItem();
        boolean food = item != null && item.getType().isEdible() && plugin.settings().allowFood();
        if (item != null && !food) {
            e.setUseItemInHand(Event.Result.DENY);
            p.sendActionBar(plugin.msg("protected"));
        } else if (blockUse) {
            p.sendActionBar(plugin.msg("protected"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) { // deckt auch PlayerInteractAtEntityEvent ab
        if (prot(e.getRightClicked().getLocation()) && deny(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p)) return;
        if ((prot(p.getLocation()) || prot(e.getEntity().getLocation())) && deny(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p && prot(p.getLocation()) && deny(p)) e.setCancelled(true);
    }

    // --- PvP und Schaden an Entities ----------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        Player attacker = playerOf(e.getDamager());
        if (attacker == null || plugin.canBypass(attacker)) return;
        Entity victim = e.getEntity();
        boolean pvp = victim instanceof Player && victim != attacker;
        boolean inside = prot(victim.getLocation()) || (pvp && prot(attacker.getLocation()));
        if (inside) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotion(PotionSplashEvent e) {
        if (!(e.getPotion().getShooter() instanceof Player shooter) || plugin.canBypass(shooter)) return;
        for (LivingEntity le : e.getAffectedEntities()) {
            if (le != shooter && prot(le.getLocation())) e.setIntensity(le, 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent e) {
        if (!prot(e.getVehicle().getLocation())) return;
        Player p = playerOf(e.getAttacker());
        if (p == null || !plugin.canBypass(p)) e.setCancelled(true);
    }

    // --- Kein Schaden, kein Hunger -----------------------------------------

    /** Spieler im Spawn nehmen keinen Schaden – ausser Void und /kill, damit niemand feststeckt. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.VOID || c == EntityDamageEvent.DamageCause.KILL) return;
        if (prot(p.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getFoodLevel() < p.getFoodLevel() && prot(p.getLocation())) e.setCancelled(true);
    }

    // --- Mobs ---------------------------------------------------------------

    /** Feindliche Mobs, die von aussen hineinlaufen, entfernen (benannte Mobs bleiben). */
    public void startMobSweep() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweepMobs, 20L, 20L);
    }

    private void sweepMobs() {
        Area a = plugin.protectArea();
        if (a == null) return;
        World w = Bukkit.getWorld(a.world());
        if (w == null) return;
        for (int cx = a.minX() >> 4; cx <= a.maxX() >> 4; cx++) {
            for (int cz = a.minZ() >> 4; cz <= a.maxZ() >> 4; cz++) {
                if (!w.isChunkLoaded(cx, cz)) continue;
                for (Entity ent : w.getChunkAt(cx, cz).getEntities()) {
                    if (ent instanceof Enemy && ent.customName() == null && prot(ent.getLocation())) {
                        ent.remove();
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getEntity() instanceof Enemy && e.getTarget() instanceof Player p && prot(p.getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (prot(e.getLocation()) && !ALLOWED_SPAWNS.contains(e.getSpawnReason())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!prot(e.getBlock())) return;
        if (e.getEntity() instanceof FallingBlock) return; // Sand/Kies vom Bauen
        if (e.getEntity() instanceof Player p && plugin.canBypass(p)) return;
        e.setCancelled(true); // Enderman, Ravager, Silberfische, Zertrampeln ...
    }

    // --- Umwelt -------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::prot);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::prot);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!prot(e.getBlock())) return;
        if (e.getPlayer() != null && plugin.canBypass(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (prot(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (prot(e.getBlock()) && e.getSource().getType() == Material.FIRE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeaves(LeavesDecayEvent e) {
        if (prot(e.getBlock())) e.setCancelled(true);
    }

    /** Wasser/Lava von aussen in den Spawn fliessen lassen */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (prot(e.getToBlock()) && !prot(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (crossesBorder(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (crossesBorder(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    private boolean crossesBorder(Block piston, List<Block> moved, BlockFace dir) {
        boolean pistonIn = prot(piston);
        if (pistonIn != prot(piston.getRelative(dir))) return true;
        for (Block b : moved) {
            if (prot(b) != pistonIn || prot(b.getRelative(dir)) != pistonIn) return true;
        }
        return false;
    }
}
