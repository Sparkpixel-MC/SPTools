package cn.ymjacky.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class PlayerKeyboardMenuListener implements Listener {

    private final Plugin plugin;

    public PlayerKeyboardMenuListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            event.setCancelled(true);
            Bukkit.getGlobalRegionScheduler().run(plugin, runnable -> player.performCommand("cd"));
        }
    }
}