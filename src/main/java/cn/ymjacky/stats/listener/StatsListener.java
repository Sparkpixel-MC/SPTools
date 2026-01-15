package cn.ymjacky.stats.listener;

import cn.ymjacky.SPToolsPlugin;
import cn.ymjacky.stats.StatsManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class StatsListener implements Listener {

    private final SPToolsPlugin plugin;
    private final StatsManager statsManager;

    public StatsListener(SPToolsPlugin plugin, StatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();
        String blockType = block.getType().name();

        statsManager.addBlocksMined(player.getUniqueId(), blockType, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();
        String blockType = block.getType().name();

        statsManager.addBlocksPlaced(player.getUniqueId(), blockType, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        statsManager.updatePlayerJoin(player.getUniqueId(), player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        statsManager.updatePlayerQuit(player.getUniqueId());
    }
}