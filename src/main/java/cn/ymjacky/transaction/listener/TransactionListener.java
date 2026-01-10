package cn.ymjacky.transaction.listener;

import cn.ymjacky.transaction.TransactionRecord;
import cn.ymjacky.transaction.TransactionUploadManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TransactionListener implements Listener {
    private final TransactionUploadManager uploadManager;
    private final Economy economy;
    private final Map<UUID, Double> playerBalances;

    public TransactionListener(TransactionUploadManager uploadManager, Economy economy) {
        this.uploadManager = uploadManager;
        this.economy = economy;
        this.playerBalances = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        double balance = economy.getBalance(player);
        playerBalances.put(uuid, balance);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        playerBalances.remove(uuid);
    }

    public void recordTransaction(UUID playerUuid, String playerName, TransactionRecord.TransactionType type,
                                 double amount, String description) {
        Player player = Bukkit.getPlayer(playerUuid);
        double balanceBefore = playerBalances.getOrDefault(playerUuid, 0.0);
        double balanceAfter = economy.getBalance(playerUuid);

        if (player != null) {
            playerBalances.put(playerUuid, balanceAfter);
        }

        TransactionRecord record = new TransactionRecord(
                playerUuid,
                playerName,
                type,
                amount,
                balanceBefore,
                balanceAfter,
                description
        );

        uploadManager.addTransaction(record);
    }

    public void updatePlayerBalance(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            playerBalances.put(uuid, economy.getBalance(player));
        }
    }
}