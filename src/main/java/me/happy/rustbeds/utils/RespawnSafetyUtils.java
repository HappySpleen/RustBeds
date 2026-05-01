package me.happy.rustbeds.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

public final class RespawnSafetyUtils {
    private static final int HORIZONTAL_RADIUS = 2;
    private static final int VERTICAL_STEPS = 6;
    private static final double SAFE_SPACE_HEIGHT = 1.625;
    private static final double VERTICAL_STEP_SIZE = 0.0625;

    private RespawnSafetyUtils() {
    }

    public static Location findSafeRespawnLocation(Location pointLocation, Location preferredLocation) {
        if (pointLocation == null || pointLocation.getWorld() == null) {
            return null;
        }

        Location baseLocation = getBaseLocation(pointLocation, preferredLocation);
        if (isWithinHorizontalRange(pointLocation, baseLocation) && isSafeRespawnSpace(baseLocation)) {
            return baseLocation.clone();
        }

        World world = pointLocation.getWorld();
        Location bestLocation = null;
        double bestDistanceSquared = Double.MAX_VALUE;

        for (int x = -HORIZONTAL_RADIUS; x <= HORIZONTAL_RADIUS; x++) {
            for (int z = -HORIZONTAL_RADIUS; z <= HORIZONTAL_RADIUS; z++) {
                for (int yStep = -VERTICAL_STEPS; yStep <= VERTICAL_STEPS; yStep++) {
                    Location candidate = new Location(world,
                            pointLocation.getBlockX() + x + 0.5,
                            baseLocation.getY() + (yStep * VERTICAL_STEP_SIZE),
                            pointLocation.getBlockZ() + z + 0.5,
                            baseLocation.getYaw(),
                            baseLocation.getPitch());
                    if (!isSafeRespawnSpace(candidate)) {
                        continue;
                    }

                    double distanceSquared = candidate.distanceSquared(baseLocation);
                    if (distanceSquared < bestDistanceSquared) {
                        bestLocation = candidate;
                        bestDistanceSquared = distanceSquared;
                    }
                }
            }
        }

        return bestLocation;
    }

    public static boolean hasSafeRespawnLocation(Location pointLocation, Location preferredLocation) {
        return findSafeRespawnLocation(pointLocation, preferredLocation) != null;
    }

    private static Location getBaseLocation(Location pointLocation, Location preferredLocation) {
        if (preferredLocation == null || preferredLocation.getWorld() == null
                || !preferredLocation.getWorld().equals(pointLocation.getWorld())) {
            return pointLocation;
        }

        return preferredLocation;
    }

    private static boolean isWithinHorizontalRange(Location pointLocation, Location location) {
        return Math.abs(location.getX() - pointLocation.getX()) <= HORIZONTAL_RADIUS
                && Math.abs(location.getZ() - pointLocation.getZ()) <= HORIZONTAL_RADIUS;
    }

    private static boolean isSafeRespawnSpace(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        double minY = location.getY();
        double maxY = minY + SAFE_SPACE_HEIGHT;
        if (minY < world.getMinHeight() || maxY > world.getMaxHeight()) {
            return false;
        }

        int blockMinY = (int) Math.floor(minY);
        int blockMaxY = (int) Math.floor(maxY);
        for (int y = blockMinY; y <= blockMaxY; y++) {
            double localMinY = Math.max(0.0, minY - y);
            double localMaxY = Math.min(1.0, maxY - y);
            if (localMaxY <= localMinY) {
                continue;
            }

            if (hasCollision(world.getBlockAt(location.getBlockX(), y, location.getBlockZ()),
                    localMinY, localMaxY)) {
                return false;
            }
        }

        return true;
    }

    private static boolean hasCollision(Block block, double localMinY, double localMaxY) {
        return block.getCollisionShape().overlaps(new BoundingBox(0.0, localMinY, 0.0, 1.0, localMaxY, 1.0));
    }
}
