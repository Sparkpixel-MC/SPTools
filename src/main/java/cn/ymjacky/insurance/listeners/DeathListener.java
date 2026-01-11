package cn.ymjacky.insurance.listeners;

import cn.ymjacky.SPToolsPlugin;
import cn.ymjacky.insurance.manager.BackupManager;
import cn.ymjacky.insurance.manager.InsuranceManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeathListener implements Listener {

    private final SPToolsPlugin plugin;
    private final InsuranceManager insuranceManager;
    private final BackupManager backupManager;

    public DeathListener(SPToolsPlugin plugin) {
        this.plugin = plugin;
        this.insuranceManager = plugin.getInsuranceManager();
        this.backupManager = plugin.getBackupManager();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.isInsuranceEnabled()) {
            return;
        }

        Player player = event.getEntity();
        UUID playerUUID = player.getUniqueId();

        List<ItemStack> itemsToDrop = new ArrayList<>();
        List<ItemStack> itemsToBackup = new ArrayList<>();

        ItemStack[] inventoryContents = player.getInventory().getContents();

        for (int i = 0; i < inventoryContents.length; i++) {
            ItemStack item = inventoryContents[i];
            if (item == null || item.getType().isAir()) {
                continue;
            }

            int insuranceLevel = insuranceManager.getInsuranceLevel(item);

            if (insuranceLevel == 0) {
                itemsToBackup.add(item);
                player.getInventory().setItem(i, null);
            } else if (insuranceLevel == 1) {
                ItemStack decreasedItem = insuranceManager.decreaseInsuranceTimes(item);
                int newLevel = insuranceManager.getInsuranceLevel(decreasedItem);

                if (newLevel == 0 && insuranceLevel > 0) {
                    insuranceManager.sendInsuranceExpiredMessage(player, item);
                }

                itemsToDrop.add(decreasedItem);
                player.getInventory().setItem(i, null);
            } else if (insuranceLevel == 2) {
                ItemStack decreasedItem = insuranceManager.decreaseInsuranceTimes(item);
                int newLevel = insuranceManager.getInsuranceLevel(decreasedItem);

                if (newLevel == 0 && insuranceLevel > 0) {
                    insuranceManager.sendInsuranceExpiredMessage(player, item);
                }

                player.getInventory().setItem(i, decreasedItem);
            }
        }

        event.setKeepInventory(true);
        event.getDrops().clear();
        event.getDrops().addAll(itemsToDrop);

        if (!itemsToBackup.isEmpty()) {
            backupManager.saveBackup(playerUUID, itemsToBackup);
        }
    }
}