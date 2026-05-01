package me.happy.rustbeds.utils;

import me.happy.rustbeds.RustBeds;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

public final class RespawnSafetyUtils {
    private static final int DEFAULT_HORIZONTAL_RADIUS = 2;
    private static final double DEFAULT_VERTICAL_RADIUS = 0.5;
    private static final double DEFAULT_SAFE_SPACE_HEIGHT = 1.5;
    private static final double VERTICAL_STEP_SIZE = 0.0625;
    private static final double EPSILON = 0.0000001;

    private RespawnSafetyUtils() {
    }

    public static Location findSafeRespawnLocation(Location pointLocation, Location preferredLocation) {
        if (pointLocation == null || pointLocation.getWorld() == null) {
            return null;
        }

        Location baseLocation = getBaseLocation(pointLocation, preferredLocation);
        SafetySearchOptions options = getSearchOptions();
        if (isWithinHorizontalRange(pointLocation, baseLocation, options.horizontalRadius())
                && isSafeRespawnSpace(baseLocation, options.safeSpaceHeight())) {
            return baseLocation.clone();
        }

        World world = pointLocation.getWorld();
        Location bestLocation = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        int verticalSteps = (int) Math.ceil(options.verticalRadius() / VERTICAL_STEP_SIZE);

        for (int x = -options.horizontalRadius(); x <= options.horizontalRadius(); x++) {
            for (int z = -options.horizontalRadius(); z <= options.horizontalRadius(); z++) {
                for (int yStep = -verticalSteps; yStep <= verticalSteps; yStep++) {
                    double yOffset = yStep * VERTICAL_STEP_SIZE;
                    if (Math.abs(yOffset) > options.verticalRadius() + EPSILON) {
                        continue;
                    }

                    Location candidate = new Location(world,
                            pointLocation.getBlockX() + x + 0.5,
                            baseLocation.getY() + yOffset,
                            pointLocation.getBlockZ() + z + 0.5,
                            baseLocation.getYaw(),
                            baseLocation.getPitch());
                    if (!isSafeRespawnSpace(candidate, options.safeSpaceHeight())) {
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

    private static boolean isWithinHorizontalRange(Location pointLocation, Location location, int horizontalRadius) {
        return Math.abs(location.getX() - pointLocation.getX()) <= horizontalRadius
                && Math.abs(location.getZ() - pointLocation.getZ()) <= horizontalRadius;
    }

    private static boolean isSafeRespawnSpace(Location location, double safeSpaceHeight) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        double minY = location.getY();
        double maxY = minY + safeSpaceHeight;
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

    private static SafetySearchOptions getSearchOptions() {
        RustBeds plugin = RustBeds.getInstance();
        if (plugin == null) {
            return new SafetySearchOptions(DEFAULT_HORIZONTAL_RADIUS, DEFAULT_VERTICAL_RADIUS,
                    DEFAULT_SAFE_SPACE_HEIGHT);
        }

        return new SafetySearchOptions(
                plugin.getSafeLocationHorizontalRadiusBlocks(),
                plugin.getSafeLocationVerticalRadiusBlocks(),
                plugin.getSafeLocationRequiredSpaceHeightBlocks());
    }

    private record SafetySearchOptions(int horizontalRadius, double verticalRadius, double safeSpaceHeight) {
    }
}
