package cn.ymjacky.stats.listener;

import cn.ymjacky.SPToolsPlugin;
import cn.ymjacky.stats.StatsManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.UUID;

public class EconomyStatsListener implements Listener {

    private final SPToolsPlugin plugin;
    private final StatsManager statsManager;
    private final Economy economy;
    private final boolean isFolia;

    public EconomyStatsListener(SPToolsPlugin plugin, StatsManager statsManager, Economy economy) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.economy = economy;
        this.isFolia = checkFolia();
    }

    private boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final Player player = event.getPlayer();
        final UUID playerUUID = player.getUniqueId();

        if (economy == null) {
            return;
        }

        final double balanceBefore = economy.getBalance(player);

        if (isFolia) {
            // 使用 Folia 的 GlobalRegionScheduler
            try {
                Class<?> globalRegionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Object globalScheduler = plugin.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(plugin.getServer());
                java.lang.reflect.Method runDelayed = globalRegionSchedulerClass.getMethod("runDelayed",
                    org.bukkit.plugin.Plugin.class,
                    java.util.function.Consumer.class,
                    long.class);

                runDelayed.invoke(globalScheduler, new Object[]{
                    plugin,
                    (java.util.function.Consumer<Object>) t -> {
                        double balanceAfter = economy.getBalance(player);
                        double difference = balanceAfter - balanceBefore;

                        if (difference > 0) {
                            statsManager.addMoneyEarned(playerUUID, difference);
                        } else if (difference < 0) {
                            statsManager.addMoneySpent(playerUUID, Math.abs(difference));
                        }
                    },
                    1L
                });
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed to schedule economy stats tracking: " + ex.getMessage());
            }
        } else {
            // 使用传统调度器
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                double balanceAfter = economy.getBalance(player);
                double difference = balanceAfter - balanceBefore;

                if (difference > 0) {
                    statsManager.addMoneyEarned(playerUUID, difference);
                } else if (difference < 0) {
                    statsManager.addMoneySpent(playerUUID, Math.abs(difference));
                }
            }, 1L);
        }
    }
}