package cn.ymjacky.gui;

import cn.ymjacky.SPToolsPlugin;
import cn.ymjacky.manager.BackupManager;
import cn.ymjacky.manager.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class BackupGUI implements Listener {

    private final SPToolsPlugin plugin;
    private final BackupManager backupManager;
    private final EconomyManager economyManager;
    private final Map<UUID, List<ItemStack>> playerBackups;

    public BackupGUI(SPToolsPlugin plugin) {
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

        Inventory inventory = Bukkit.createInventory(null, 54, STR."\{ChatColor.GOLD}备份恢复 - 点击恢复物品");
        fillInventory(inventory, backupItems);
        player.openInventory(inventory);
    }

    private void fillInventory(Inventory inventory, List<ItemStack> backupItems) {
        inventory.clear();
        for (int i = 0; i < backupItems.size() && i < 54; i++) {
            ItemStack item = backupItems.get(i);
            ItemStack displayItem = item.clone();
            ItemMeta meta = displayItem.getItemMeta();

            if (meta != null) {
                List<String> lore = meta.lore() != null ? new ArrayList<>(Objects.requireNonNull(meta.getLore())) : new ArrayList<>();

                double recoveryPrice = economyManager.calculateRecoveryPrice(item);
                String formattedPrice = economyManager.formatMoney(recoveryPrice);

                lore.add("");
                lore.add(STR."\{ChatColor.YELLOW}=== 恢复信息 ===");
                lore.add(STR."\{ChatColor.WHITE}恢复费用: \{ChatColor.GREEN}\{formattedPrice}");
                lore.add(STR."\{ChatColor.GRAY}点击恢复此物品");

                meta.setLore(lore);
                displayItem.setItemMeta(meta);
            }

            inventory.setItem(i, displayItem);
        }
    }

    private void refreshInventory(Player player, List<ItemStack> backupItems) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        fillInventory(inventory, backupItems);
        player.updateInventory();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();

        if (!title.equals(STR."\{ChatColor.GOLD}备份恢复 - 点击恢复物品")) {
            return;
        }

        // 只处理点击备份GUI（顶部54格）的情况
        // 点击类型为OUTSIDE或QUICKBAR时，event.getSlot()可能返回负数或非0-53
        // 使用getRawSlot()来判断点击的是哪个库存区域
        int rawSlot = event.getRawSlot();

        // 如果点击的是玩家背包（rawSlot >= 54），允许操作
        // 但需要防止玩家将物品从背包拖拽到备份GUI中
        if (rawSlot >= 54) {
            // 只阻止将物品移动到备份GUI的操作
            // MOVE_TO_OTHER_INVENTORY: 按Shift点击将物品移动到另一个库存
            // COLLECT_TO_CURSOR: 双击收集同类物品到光标
            if (event.getAction().name().equals("MOVE_TO_OTHER_INVENTORY") ||
                event.getAction().name().equals("COLLECT_TO_CURSOR")) {
                event.setCancelled(true);
            }
            // 其他操作（PICKUP、PLACE、DROP等）都允许
            return;
        }

        // 点击的是备份GUI（顶部54格），取消所有操作
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
            refreshInventory(player, backupItems);
        }
    }
}