package cn.ymjacky.insurance.gui;

import cn.ymjacky.insurance.InsurancePlugin;
import cn.ymjacky.insurance.manager.EconomyManager;
import cn.ymjacky.insurance.manager.InsuranceManager;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InsurancePurchaseGUI implements Listener {

    private final InsurancePlugin plugin;
    private final InsuranceManager insuranceManager;
    private final EconomyManager economyManager;
    private final Map<Player, ItemStack> playerItems;

    public InsurancePurchaseGUI(InsurancePlugin plugin) {
        this.plugin = plugin;
        this.insuranceManager = plugin.getInsuranceManager();
        this.economyManager = plugin.getEconomyManager();
        this.playerItems = new HashMap<>();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openPurchaseGUI(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "请手持一个物�?);
            return;
        }

        if (insuranceManager.hasAdminInsurance(item)) {
            player.sendMessage(ChatColor.RED + "管理员保险的物品无法购买保险");
            return;
        }

        playerItems.put(player, item.clone());

        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.GOLD + "购买保险");

        Material limeBundle = Material.matchMaterial("minecraft:lime_bundle");
        Material redBundle = Material.matchMaterial("minecraft:red_bundle");
        Material pinkBundle = Material.matchMaterial("minecraft:pink_bundle");

        if (limeBundle == null) limeBundle = Material.BUNDLE;
        if (redBundle == null) redBundle = Material.BUNDLE;
        if (pinkBundle == null) pinkBundle = Material.BUNDLE;

        ItemStack level1Item = createInsuranceItem(limeBundle, 1,
                ChatColor.GREEN + "等级 1 保险",
                ChatColor.WHITE + "物品将在死亡时掉�?,
                ChatColor.YELLOW + "左键: 购买1�?| 右键: 购买�?,
                ChatColor.YELLOW + "费用(1�?: " + ChatColor.GREEN + calculatePrice(item, 1, 1),
                ChatColor.YELLOW + "费用(�?: " + ChatColor.GREEN + calculatePrice(item, 1, plugin.getConfigManager().getMaxInsuranceTimes()));

        ItemStack level2Item = createInsuranceItem(redBundle, 2,
                ChatColor.RED + "等级 2 保险",
                ChatColor.WHITE + "物品将在死亡时保�?,
                ChatColor.YELLOW + "左键: 购买1�?| 右键: 购买�?,
                ChatColor.YELLOW + "费用(1�?: " + ChatColor.RED + calculatePrice(item, 2, 1),
                ChatColor.YELLOW + "费用(�?: " + ChatColor.RED + calculatePrice(item, 2, plugin.getConfigManager().getMaxInsuranceTimes()));

        ItemStack upgradeItem;
        if (insuranceManager.getInsuranceLevel(item) == 1) {
            upgradeItem = createInsuranceItem(pinkBundle, 3,
                    ChatColor.RED + "升级到等�?2",
                    ChatColor.WHITE + "从等�?1 升级到等�?2",
                    ChatColor.WHITE + "剩余保险次数: " + ChatColor.RED + insuranceManager.getInsuranceTimes(item),
                    ChatColor.YELLOW + "费用: " + ChatColor.RED + calculateUpgradePrice(item));
        } else {
            upgradeItem = createInsuranceItem(pinkBundle, 3,
                    ChatColor.GRAY + "不可升级",
                    ChatColor.WHITE + "当前物品不符合升级条�?,
                    ChatColor.YELLOW + "需要等�?1 保险");
        }

        ItemStack closeItem = createInsuranceItem(Material.BARRIER, 4,
                ChatColor.RED + "关闭",
                ChatColor.WHITE + "关闭此界�?);

        inventory.setItem(11, level1Item);
        inventory.setItem(13, level2Item);
        inventory.setItem(15, upgradeItem);
        inventory.setItem(26, closeItem);

        player.openInventory(inventory);
    }

    private ItemStack createInsuranceItem(Material material, int slot, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            List<String> itemLore = new ArrayList<>();
            for (String line : lore) {
                itemLore.add(line);
            }
            meta.setLore(itemLore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private String calculatePrice(ItemStack item, int level, int times) {
        double price = economyManager.calculateInsurancePrice(item, level, times);
        return economyManager.formatMoney(price);
    }

    private String calculateUpgradePrice(ItemStack item) {
        double price = economyManager.calculateUpgradePrice(item, insuranceManager.getInsuranceTimes(item));
        return economyManager.formatMoney(price);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (!title.equals(ChatColor.GOLD + "购买保险")) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getSlot();
        ItemStack originalItem = playerItems.get(player);

        if (originalItem == null) {
            player.closeInventory();
            return;
        }

        boolean isRightClick = event.isRightClick();

        switch (slot) {
            case 11:
                purchaseInsurance(player, originalItem, 1, isRightClick);
                break;
            case 13:
                purchaseInsurance(player, originalItem, 2, isRightClick);
                break;
            case 15:
                upgradeInsurance(player, originalItem);
                break;
            case 26:
                player.closeInventory();
                break;
        }
    }

    private void purchaseInsurance(Player player, ItemStack item, int level, boolean buyMax) {
        int currentLevel = insuranceManager.getInsuranceLevel(item);
        int currentTimes = insuranceManager.getInsuranceTimes(item);
        int maxTimes = plugin.getConfigManager().getMaxInsuranceTimes();
        int itemAmount = item.getAmount();

        if (currentLevel == level && currentTimes >= maxTimes) {
            player.sendMessage(ChatColor.RED + "该物品已达到最大保险次�?);
            return;
        }

        if (currentLevel > level) {
            player.sendMessage(ChatColor.RED + "不能降低保险等级！请使用升级功能");
            return;
        }

        if (currentLevel == 1 && level == 2) {
            player.sendMessage(ChatColor.RED + "不能直接购买2级保险！请使用升级功�?);
            return;
        }

        int timesToAdd;
        if (buyMax) {
            timesToAdd = maxTimes - (currentLevel == level ? currentTimes : 0);
        } else {
            timesToAdd = 1;
        }

        if (timesToAdd <= 0) {
            player.sendMessage(ChatColor.RED + "该物品已达到最大保险次�?);
            return;
        }

        double pricePerItem = economyManager.calculateInsurancePrice(item, level, timesToAdd);
        double totalPrice = pricePerItem * itemAmount;
        String formattedPrice = economyManager.formatMoney(totalPrice);

        if (!economyManager.hasEnoughMoney(player, totalPrice)) {
            player.sendMessage(plugin.getConfigManager().getMessage("not_enough_money", formattedPrice));
            return;
        }

        economyManager.withdrawMoney(player, totalPrice);

        int newTimes;
        if (currentLevel == level) {
            newTimes = currentTimes + timesToAdd;
            if (newTimes > maxTimes) {
                newTimes = maxTimes;
            }
        } else {
            newTimes = timesToAdd;
        }

        ItemStack newItem = insuranceManager.addInsurance(item.clone(), level, newTimes);
        player.getInventory().setItemInMainHand(newItem);

        player.sendMessage(plugin.getConfigManager().getMessage("insurance_added", level, newTimes, formattedPrice));

        // 在下一个tick重新打开GUI，避免InventoryClickEvent冲突
        player.closeInventory();
        scheduleDelayed(player, () -> {
            ItemStack currentItem = player.getInventory().getItemInMainHand();
            if (currentItem != null && !currentItem.getType().isAir()) {
                openPurchaseGUI(player, currentItem);
            }
        });
    }

    private void upgradeInsurance(Player player, ItemStack item) {
        if (insuranceManager.hasAdminInsurance(item)) {
            player.sendMessage(ChatColor.RED + "管理员保险的物品无法升级");
            return;
        }

        if (insuranceManager.getInsuranceLevel(item) != 1) {
            player.sendMessage(ChatColor.RED + "只有等级 1 保险才能升级");
            return;
        }

        int remainingTimes = insuranceManager.getInsuranceTimes(item);
        double price = economyManager.calculateUpgradePrice(item, remainingTimes);
        String formattedPrice = economyManager.formatMoney(price);

        if (!economyManager.hasEnoughMoney(player, price)) {
            player.sendMessage(plugin.getConfigManager().getMessage("not_enough_money", formattedPrice));
            return;
        }

        economyManager.withdrawMoney(player, price);

        ItemStack newItem = insuranceManager.upgradeInsurance(item.clone());
        player.getInventory().setItemInMainHand(newItem);

        player.sendMessage(plugin.getConfigManager().getMessage("insurance_upgraded", 2, formattedPrice));

        // 在下一个tick重新打开GUI，避免InventoryClickEvent冲突
        player.closeInventory();
        scheduleDelayed(player, () -> {
            ItemStack currentItem = player.getInventory().getItemInMainHand();
            if (currentItem != null && !currentItem.getType().isAir()) {
                openPurchaseGUI(player, currentItem);
            }
        });
    }

    /**
     * 兼容Folia和普通Spigot服务器的调度�?     * 使用反射来检测并使用正确的调度器
     */
    private void scheduleDelayed(Player player, Runnable task) {
        try {
            // 尝试使用Folia的EntityScheduler
            Method getScheduler = player.getClass().getMethod("getScheduler");
            Object scheduler = getScheduler.invoke(player);

            // Folia的runDelayed方法签名: runDelayed(Plugin, Consumer, Object, long)
            Method runDelayed = scheduler.getClass().getMethod("runDelayed", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, Object.class, long.class);
            runDelayed.invoke(scheduler, plugin, (java.util.function.Consumer<?>) t -> task.run(), null, 1L);
        } catch (Exception e) {
            // 如果Folia API不可用，回退到传统调度器
            try {
                Bukkit.getScheduler().runTask(plugin, task);
            } catch (Exception ex) {
                // 如果传统调度器也失败（Folia），尝试使用GlobalRegionScheduler
                try {
                    Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
                    Object globalScheduler = getGlobalRegionScheduler.invoke(null);
                    Method runDelayed = globalScheduler.getClass().getMethod("runDelayed", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class);
                    runDelayed.invoke(globalScheduler, plugin, (java.util.function.Consumer<?>) t -> task.run(), 1L);
                } catch (Exception ex2) {
                    plugin.getLogger().severe("无法调度任务: " + ex2.getMessage());
                }
            }
        }
    }
}