package cn.ymjacky.transaction;

import cn.ymjacky.transaction.listener.TransactionListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionMonitor {
    private final JavaPlugin plugin;
    private final TransactionUploadManager uploadManager;
    private final TransactionListener listener;
    private final Economy economy;

    private final Map<UUID, Double> trackedBalances;
    private final Map<UUID, Long> lastCheckTime;

    public TransactionMonitor(JavaPlugin plugin, TransactionUploadManager uploadManager,
                             TransactionListener listener, Economy economy) {
        this.plugin = plugin;
        this.uploadManager = uploadManager;
        this.listener = listener;
        this.economy = economy;
        this.trackedBalances = new ConcurrentHashMap<>();
        this.lastCheckTime = new ConcurrentHashMap<>();

        startMonitoring();
    }

    private void startMonitoring() {
        try {
            // 尝试使用传统调度器
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerTransaction(player);
                }
            }, 20L, 20L);

            plugin.getLogger().info("交易记录监控已启动 (传统异步调度器)");
        } catch (UnsupportedOperationException e) {
            // Folia 环境下不支持传统调度器，使用 Folia 的 GlobalRegionScheduler
            try {
                Class<?> globalRegionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Object globalScheduler = plugin.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(plugin.getServer());
                java.lang.reflect.Method runAtFixedRate = globalRegionSchedulerClass.getMethod("runAtFixedRate",
                    org.bukkit.plugin.Plugin.class,
                    java.util.function.Consumer.class,
                    long.class,
                    long.class);

                runAtFixedRate.invoke(globalScheduler, new Object[]{
                    plugin,
                    (java.util.function.Consumer<Object>) t -> {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            checkPlayerTransaction(player);
                        }
                    },
                    20L,
                    20L
                });

                plugin.getLogger().info("交易记录监控已启动 (Folia GlobalRegionScheduler)");
            } catch (Exception ex) {
                plugin.getLogger().warning("Async scheduler not supported, transaction monitoring disabled");
            }
        }
    }

    private void checkPlayerTransaction(Player player) {
        UUID uuid = player.getUniqueId();
        double currentBalance = economy.getBalance(player);

        Double lastBalance = trackedBalances.get(uuid);
        if (lastBalance == null) {
            trackedBalances.put(uuid, currentBalance);
            lastCheckTime.put(uuid, System.currentTimeMillis());
            return;
        }

        double balanceChange = currentBalance - lastBalance;

        if (Math.abs(balanceChange) > 0.0001) {
            TransactionRecord.TransactionType type;
            if (balanceChange > 0) {
                type = TransactionRecord.TransactionType.ORDER;
            } else {
                type = TransactionRecord.TransactionType.PAYMENT_ORDER;
            }

            listener.recordTransaction(
                    uuid,
                    player.getName(),
                    type,
                    Math.abs(balanceChange),
                    "余额变动监控"
            );

            trackedBalances.put(uuid, currentBalance);
        }

        lastCheckTime.put(uuid, System.currentTimeMillis());
    }

    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        trackedBalances.put(uuid, economy.getBalance(player));
        lastCheckTime.put(uuid, System.currentTimeMillis());
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        trackedBalances.remove(uuid);
        lastCheckTime.remove(uuid);
    }

    public void shutdown() {
        trackedBalances.clear();
        lastCheckTime.clear();
    }
}