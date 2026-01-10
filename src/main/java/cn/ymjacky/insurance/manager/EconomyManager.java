package cn.ymjacky.insurance.manager;

import cn.ymjacky.SPToolsPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class EconomyManager {

    private final SPToolsPlugin plugin;
    private final Economy economy;

    public EconomyManager(SPToolsPlugin plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public boolean hasEnoughMoney(Player player, double amount) {
        if (economy == null) {
            return true;
        }
        return economy.has(player, amount);
    }

    public boolean withdrawMoney(Player player, double amount) {
        if (economy == null) {
            return true;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public double calculateBasePrice(ItemStack item) {
        Material material = item.getType();
        return plugin.getInsuranceConfigManager().getItemPrice(material);
    }

    public double calculateEnchantmentMultiplier(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasEnchants()) {
            return 1.0;
        }

        int enchantmentCount = meta.getEnchants().size();
        double multiplier = 1.0 + (enchantmentCount * plugin.getInsuranceConfigManager().getEnchantmentCostMultiplier());
        return multiplier;
    }

    public double calculateInsurancePrice(ItemStack item, int level, int times) {
        double basePrice = calculateBasePrice(item);
        double enchantmentMultiplier = calculateEnchantmentMultiplier(item);
        double totalPrice = basePrice * enchantmentMultiplier;

        double costPercentage;
        if (level == 1) {
            costPercentage = plugin.getInsuranceConfigManager().getLevel1CostPercentage();
        } else if (level == 2) {
            costPercentage = plugin.getInsuranceConfigManager().getLevel2CostPercentage();
        } else {
            return 0.0;
        }

        return totalPrice * costPercentage * times;
    }

    public double calculateUpgradePrice(ItemStack item, int remainingTimes) {
        double basePrice = calculateBasePrice(item);
        double enchantmentMultiplier = calculateEnchantmentMultiplier(item);
        double totalPrice = basePrice * enchantmentMultiplier;
        double upgradePercentage = plugin.getInsuranceConfigManager().getUpgradeCostPercentage();

        return totalPrice * upgradePercentage * remainingTimes;
    }

    public double calculateRecoveryPrice(ItemStack item) {
        double basePrice = calculateBasePrice(item);
        double enchantmentMultiplier = calculateEnchantmentMultiplier(item);
        double totalPrice = basePrice * enchantmentMultiplier;
        double recoveryPercentage = plugin.getInsuranceConfigManager().getBackupRecoveryCostPercentage();

        return totalPrice * recoveryPercentage;
    }

    public String formatMoney(double amount) {
        if (economy == null) {
            return String.format("%.2f", amount);
        }
        return economy.format(amount);
    }
}