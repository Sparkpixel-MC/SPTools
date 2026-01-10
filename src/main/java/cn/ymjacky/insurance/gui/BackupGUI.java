package cn.ymjacky.insurance.gui;

import cn.ymjacky.insurance.InsurancePlugin;
import cn.ymjacky.insurance.manager.BackupManager;
import cn.ymjacky.insurance.manager.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BackupGUI implements Listener {

    private final InsurancePlugin plugin;
    private final BackupManager backupManager;
    private final EconomyManager economyManager;
    private final Map<UUID, List<ItemStack>> playerBackups;

    public BackupGUI(InsurancePlugin plugin) {
        this.plugin = plugin;
        this.backupManager = plugin.getBackupManager();
        this.economyManager = plugin.getEconomyManager();
        this.playerBackups = new HashMap<>();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openBackupGUI(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (!backupManager.hasBackup(playerUUID)) {
            player.sendMessage(plugin.getConfigManager().getMessage("backup_empty"));
            return;
        }

        List<ItemStack> backupItems = backupManager.loadBackup(playerUUID);
        playerBackups.put(playerUUID, backupItems);

        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.GOLD + "备份恢复 - 点击恢复物品");

        for (int i = 0; i < backupItems.size() && i < 54; i++) {
            ItemStack item = backupItems.get(i);
            ItemStack displayItem = item.clone();
            ItemMeta meta = displayItem.getItemMeta();

            if (meta != null) {
                List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

                double recoveryPrice = economyManager.calculateRecoveryPrice(item);
                String formattedPrice = economyManager.formatMoney(recoveryPrice);

                lore.add("");
                lore.add(ChatColor.YELLOW + "=== 恢复信息 ===");
                lore.add(ChatColor.WHITE + "恢复费用: " + ChatColor.GREEN + formattedPrice);
                lore.add(ChatColor.GRAY + "点击恢复此物�?);

                meta.setLore(lore);
                displayItem.setItemMeta(meta);
            }

            inventory.setItem(i, displayItem);
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (!title.equals(ChatColor.GOLD + "备份恢复 - 点击恢复物品")) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getSlot();
        if (slot < 0 || slot >= 54) {
            return;
        }

        UUID playerUUID = player.getUniqueId();
        List<ItemStack> backupItems = playerBackups.get(playerUUID);

        if (backupItems == null || slot >= backupItems.size()) {
            return;
        }

        ItemStack item = backupItems.get(slot);
        double recoveryPrice = economyManager.calculateRecoveryPrice(item);
        String formattedPrice = economyManager.formatMoney(recoveryPrice);

        if (!economyManager.hasEnoughMoney(player, recoveryPrice)) {
            player.sendMessage(plugin.getConfigManager().getMessage("not_enough_money", formattedPrice));
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(plugin.getConfigManager().getMessage("inventory_full"));
            return;
        }

        economyManager.withdrawMoney(player, recoveryPrice);
        player.getInventory().addItem(item.clone());
        backupItems.remove(slot);

        player.sendMessage(plugin.getConfigManager().getMessage("item_recovered", formattedPrice));

        if (backupItems.isEmpty()) {
            player.closeInventory();
            backupManager.deleteBackup(playerUUID);
            playerBackups.remove(playerUUID);
        } else {
            backupManager.saveBackup(playerUUID, backupItems);
            player.openInventory(event.getInventory());
        }
    }
}