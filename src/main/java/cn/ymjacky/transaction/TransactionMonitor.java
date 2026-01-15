package cn.ymjacky.transaction;

import cn.ymjacky.transaction.listener.TransactionListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionMonitor {
    private final JavaPlugin plugin;
    private final TransactionListener listener;
    private final Economy economy;

    private final Map<UUID, Double> trackedBalances;
    private final Map<UUID, Long> lastCheckTime;

    public TransactionMonitor(JavaPlugin plugin,
                              TransactionListener listener, Economy economy) {
        this.plugin = plugin;
        this.listener = listener;
        this.economy = economy;
        this.trackedBalances = new ConcurrentHashMap<>();
        this.lastCheckTime = new ConcurrentHashMap<>();

        startMonitoring();
    }

    private void startMonitoring() {
        try {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, _ -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerTransaction(player);
                }
            }, 20L, 20L);
            plugin.getLogger().info("TransactionMonitor started");
        } catch (Exception ignored) {}
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

    public void shutdown() {
        trackedBalances.clear();
        lastCheckTime.clear();
    }
}