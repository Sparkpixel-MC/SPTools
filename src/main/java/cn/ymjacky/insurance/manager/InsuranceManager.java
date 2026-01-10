package cn.ymjacky.insurance.manager;

import cn.ymjacky.SPToolsPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class InsuranceManager {

    private final SPToolsPlugin plugin;
    private final NamespacedKey INSURANCE_LEVEL_KEY;
    private final NamespacedKey INSURANCE_TIMES_KEY;
    private final NamespacedKey ADMIN_INSURANCE_KEY;

    public InsuranceManager(SPToolsPlugin plugin) {
        this.plugin = plugin;
        this.INSURANCE_LEVEL_KEY = new NamespacedKey(plugin, "insurance_level");
        this.INSURANCE_TIMES_KEY = new NamespacedKey(plugin, "insurance_times");
        this.ADMIN_INSURANCE_KEY = new NamespacedKey(plugin, "admin_insurance");
    }

    public int getInsuranceLevel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        Integer level = container.get(INSURANCE_LEVEL_KEY, PersistentDataType.INTEGER);
        return level != null ? level : 0;
    }

    public int getInsuranceTimes(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        Integer times = container.get(INSURANCE_TIMES_KEY, PersistentDataType.INTEGER);
        return times != null ? times : 0;
    }

    public boolean hasAdminInsurance(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        Boolean adminInsurance = container.get(ADMIN_INSURANCE_KEY, PersistentDataType.BOOLEAN);
        return adminInsurance != null && adminInsurance;
    }

    public ItemStack setInsurance(ItemStack item, int level, int times) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(INSURANCE_LEVEL_KEY, PersistentDataType.INTEGER, level);
        container.set(INSURANCE_TIMES_KEY, PersistentDataType.INTEGER, times);

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack setAdminInsurance(ItemStack item, boolean adminInsurance) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(ADMIN_INSURANCE_KEY, PersistentDataType.BOOLEAN, adminInsurance);

        if (adminInsurance) {
            container.set(INSURANCE_TIMES_KEY, PersistentDataType.INTEGER, Integer.MAX_VALUE);
        }

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack decreaseInsuranceTimes(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }

        if (hasAdminInsurance(item)) {
            return item;
        }

        int currentLevel = getInsuranceLevel(item);
        if (currentLevel == 0) {
            return item;
        }

        int currentTimes = getInsuranceTimes(item);
        if (currentTimes <= 1) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return item;
            }

            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.remove(INSURANCE_LEVEL_KEY);
            container.remove(INSURANCE_TIMES_KEY);
            container.remove(ADMIN_INSURANCE_KEY);

            item.setItemMeta(meta);
            return item;
        }

        return setInsurance(item, currentLevel, currentTimes - 1);
    }

    public ItemStack addInsurance(ItemStack item, int level, int times) {
        if (times <= 0 || level < 1 || level > 2) {
            return item;
        }

        return setInsurance(item, level, times);
    }

    public ItemStack upgradeInsurance(ItemStack item) {
        int currentLevel = getInsuranceLevel(item);
        if (currentLevel != 1) {
            return item;
        }

        return setInsurance(item, 2, getInsuranceTimes(item));
    }

    public boolean canStack(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) {
            return false;
        }

        if (item1.getType() != item2.getType()) {
            return false;
        }

        int level1 = getInsuranceLevel(item1);
        int level2 = getInsuranceLevel(item2);

        if (level1 != level2) {
            return false;
        }

        if (hasAdminInsurance(item1) != hasAdminInsurance(item2)) {
            return false;
        }

        return true;
    }

    public void sendInsuranceExpiredMessage(Player player, ItemStack item) {
        String itemName = item.getType().toString().replace("_", " ").toLowerCase();
        String message = plugin.getInsuranceConfigManager().getMessage("insurance_expired", itemName);
        player.sendMessage(message);
    }

    public ItemStack removeInsurance(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.remove(INSURANCE_LEVEL_KEY);
        container.remove(INSURANCE_TIMES_KEY);
        container.remove(ADMIN_INSURANCE_KEY);

        item.setItemMeta(meta);
        return item;
    }
}