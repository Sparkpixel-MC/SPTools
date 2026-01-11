package cn.ymjacky.stats.listener;

import cn.ymjacky.SPToolsPlugin;
import cn.ymjacky.stats.StatsManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.lang.reflect.Method;

public class EconomyStatsListener implements Listener {

    private final SPToolsPlugin plugin;
    private final StatsManager statsManager;
    private final Economy economy;

    public EconomyStatsListener(SPToolsPlugin plugin, StatsManager statsManager, Economy economy) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.economy = economy;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        if (economy == null) {
            return;
        }

        double balanceBefore = economy.getBalance(player);

        try {
            // 尝试使用 Folia 的 GlobalRegionScheduler
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object server = bukkitClass.getMethod("getServer").invoke(null);

            Class<?> globalRegionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Object globalScheduler = server.getClass().getMethod("getGlobalRegionScheduler").invoke(server);

            Class<?> regionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            Method runDelayed = regionSchedulerClass.getMethod("runDelayed", 
                org.bukkit.plugin.Plugin.class, 
                java.util.function.Consumer.class, 
                long.class);

            runDelayed.invoke(globalScheduler, new Object[]{
                plugin, 
                (java.util.function.Consumer<?>) t -> {
                    double balanceAfter = economy.getBalance(player);
                    double difference = balanceAfter - balanceBefore;

                    if (difference > 0) {
                        statsManager.addMoneyEarned(player.getUniqueId(), difference);
                    } else if (difference < 0) {
                        statsManager.addMoneySpent(player.getUniqueId(), Math.abs(difference));
                    }
                }, 
                1L
            });
        } catch (Exception e) {
            // 回退到传统调度器（非 Folia 环境）
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                double balanceAfter = economy.getBalance(player);
                double difference = balanceAfter - balanceBefore;

                if (difference > 0) {
                    statsManager.addMoneyEarned(player.getUniqueId(), difference);
                } else if (difference < 0) {
                    statsManager.addMoneySpent(player.getUniqueId(), Math.abs(difference));
                }
            }, 1L);
        }
    }
}