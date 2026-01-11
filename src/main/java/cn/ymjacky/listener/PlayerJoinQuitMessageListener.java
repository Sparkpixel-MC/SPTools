package cn.ymjacky.listener;

import cn.ymjacky.utils.PlayerMessageUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerJoinQuitMessageListener implements Listener {

    private final JavaPlugin plugin;

    public PlayerJoinQuitMessageListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        PlayerMessageUtil.handlePlayerJoin(plugin, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        PlayerMessageUtil.handlePlayerQuit(event.getPlayer());
    }
}