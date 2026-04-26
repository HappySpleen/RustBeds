package me.happy.rustbeds.listeners;

import me.happy.rustbeds.RustBeds;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import static me.happy.rustbeds.utils.BedsUtils.handleBrokenBed;

public class BedBreakListener implements Listener {
    public BedBreakListener(RustBeds plugin) {
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        handleBrokenBed(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        handleBrokenBed(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            handleBrokenBed(block);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            handleBrokenBed(block);
        }
    }
}
