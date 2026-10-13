package ch.blugdeg.spawnelytra;

import org.bukkit.configuration.file.FileConfiguration;

/** Zahlenwerte aus der config.yml, bereits in Ticks umgerechnet. */
public record Settings(
        int flightTicks,
        int maxFlightTicks,
        int graceTicks,
        int decayRampTicks,
        double decaySink,
        double decayDrag,
        double decayMaxSink,
        int boostCooldownTicks,
        int boostDurationTicks,
        double boostSpeed,
        boolean allowFood) {

    public static Settings load(FileConfiguration c) {
        return new Settings(
                (int) Math.round(c.getDouble("flight.duration-seconds", 60) * 20),
                (int) Math.round(c.getDouble("flight.max-seconds", 300) * 20),
                (int) Math.round(c.getDouble("flight.grace-seconds", 3) * 20),
                Math.max(1, (int) Math.round(c.getDouble("flight.decay.ramp-seconds", 5) * 20)),
                c.getDouble("flight.decay.sink-per-tick", 0.08),
                c.getDouble("flight.decay.horizontal-drag", 0.04),
                c.getDouble("flight.decay.max-sink", 1.2),
                (int) Math.round(c.getDouble("boost.cooldown-seconds", 5) * 20),
                c.getInt("boost.duration-ticks", 35),
                c.getDouble("boost.speed", 1.5),
                c.getBoolean("protection.allow-food", true));
    }
}
