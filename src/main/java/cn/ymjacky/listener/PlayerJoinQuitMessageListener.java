package cn.ymjacky.listener;

import cn.ymjacky.utils.PlayerMessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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
        boolean hitokotoEnabled = plugin.getConfig().getBoolean("hitokoto-enabled", true);
        if (hitokotoEnabled) {
            PlayerMessageUtil.handlePlayerJoin(plugin, event.getPlayer());
        } else {
            Player player = event.getPlayer();
            Component joinMessage = Component.text("[ + ] ", NamedTextColor.GREEN)
                    .append(Component.text(player.getName()));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.sendMessage(joinMessage);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        boolean hitokotoEnabled = plugin.getConfig().getBoolean("hitokoto-enabled", true);
        if (hitokotoEnabled) {
            PlayerMessageUtil.handlePlayerQuit(event.getPlayer());
        } else {
            Player player = event.getPlayer();
            Component quitMessage = Component.text("[ - ] ", NamedTextColor.RED)
                    .append(Component.text(player.getName()));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.sendMessage(quitMessage);
                }
            }
        }
    }
}