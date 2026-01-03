package cn.ymjacky.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerMessageUtil {
    private PlayerMessageUtil() {}

    public static boolean isFolia() {
        return true;
    }

    public static Component createGradientMessage(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        TextColor startColor = TextColor.color(0xFFB6C1);
        TextColor endColor = TextColor.color(0x87CEFA);
        Component gradientComponent = Component.empty();
        int length = message.length();
        for (int i = 0; i < length; i++) {
            char c = message.charAt(i);
            float ratio = (float) i / Math.max(1, length - 1);
            int red = (int) (startColor.red() * (1 - ratio) + endColor.red() * ratio);
            int green = (int) (startColor.green() * (1 - ratio) + endColor.green() * ratio);
            int blue = (int) (startColor.blue() * (1 - ratio) + endColor.blue() * ratio);
            TextColor charColor = TextColor.color(red, green, blue);
            gradientComponent = gradientComponent.append(
                    Component.text(c)
                            .color(charColor)
                            .decoration(TextDecoration.ITALIC, false)
            );
        }
        return gradientComponent;
    }

    public static void broadcastMessageExcluding(Component message, Player excludedPlayer) {
        if (message == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(excludedPlayer)) {
                player.sendMessage(message);
            }
        }
    }
    public static void handlePlayerJoin(JavaPlugin plugin, Player player) {
        Component joinMessage = createGradientMessage(player.getName() + " 加入了生存区");
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
                broadcastMessageExcluding(joinMessage, player);
            }, 2L);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    broadcastMessageExcluding(joinMessage, player);
                }
            }.runTaskLater(plugin, 2L);
        }
    }

    public static void handlePlayerQuit(JavaPlugin plugin, Player player) {
        Component quitMessage = createGradientMessage(player.getName() + " 离开了生存区");
        broadcastMessageExcluding(quitMessage, player);
    }
}