package ch.blugdeg.spawnelytra;

import org.bukkit.Location;

/** Achsenparalleler Quader in Blockkoordinaten, beide Ecken inklusive. */
public record Area(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static Area of(BlockPos a, BlockPos b) {
        if (a == null || b == null || !a.world().equals(b.world())) return null;
        return new Area(a.world(),
                Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        return x >= minX && x < maxX + 1
                && y >= minY && y < maxY + 1
                && z >= minZ && z < maxZ + 1;
    }

    /** Wie contains, aber ueber die ganze Weltenhoehe (nur X/Z zaehlen). */
    public boolean containsColumn(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
        double x = loc.getX(), z = loc.getZ();
        return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
    }

    /** Innerhalb der X/Z-Flaeche und nicht unterhalb der unteren Ecke – nach oben offen. */
    public boolean containsAbove(Location loc) {
        return containsColumn(loc) && loc.getY() >= minY;
    }

    public String describe() {
        return world + " " + minX + "," + minY + "," + minZ + " → " + maxX + "," + maxY + "," + maxZ;
    }

    /** Eine einzelne Ecke, gespeichert als "welt,x,y,z". */
    public record BlockPos(String world, int x, int y, int z) {
        public static BlockPos parse(String s) {
            if (s == null || s.isBlank()) return null;
            String[] p = s.split(",");
            if (p.length != 4) return null;
            try {
                return new BlockPos(p[0].trim(), Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public static BlockPos of(Location loc) {
            return new BlockPos(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }

        public String serialize() {
            return world + "," + x + "," + y + "," + z;
        }
    }
}
