package cn.ymjacky.insurance.commands;

import cn.ymjacky.insurance.InsurancePlugin;
import cn.ymjacky.insurance.gui.BackupGUI;
import cn.ymjacky.insurance.gui.InsuranceGUI;
import cn.ymjacky.insurance.gui.InsurancePurchaseGUI;
import cn.ymjacky.insurance.manager.EconomyManager;
import cn.ymjacky.insurance.manager.InsuranceManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class InsuranceCommand implements CommandExecutor {

    private final InsurancePlugin plugin;
    private final InsuranceManager insuranceManager;
    private final EconomyManager economyManager;
    private final InsuranceGUI insuranceGUI;
    private final BackupGUI backupGUI;
    private final InsurancePurchaseGUI purchaseGUI;

    public InsuranceCommand(InsurancePlugin plugin) {
        this.plugin = plugin;
        this.insuranceManager = plugin.getInsuranceManager();
        this.economyManager = plugin.getEconomyManager();
        this.insuranceGUI = new InsuranceGUI(plugin);
        this.backupGUI = new BackupGUI(plugin);
        this.purchaseGUI = new InsurancePurchaseGUI(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "toggle":
                handleToggle(sender);
                break;
            case "gui":
                handleGUI(sender);
                break;
            case "backup":
                handleBackup(sender);
                break;
            case "buy":
                handleBuy(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "admin":
                handleAdmin(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleToggle(CommandSender sender) {
        String permission = plugin.getConfigManager().getPermission("toggle");
        if (!permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        boolean newState = !plugin.isPluginEnabled();
        plugin.setPluginEnabled(newState);

        String messageKey = newState ? "plugin_enabled" : "plugin_disabled";
        sender.sendMessage(plugin.getConfigManager().getMessage(messageKey));
    }

    private void handleGUI(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player_only_command"));
            return;
        }

        String permission = plugin.getConfigManager().getPermission("gui");
        if (!permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        Player player = (Player) sender;
        insuranceGUI.openInsuranceGUI(player);
    }

    private void handleBackup(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player_only_command"));
            return;
        }

        String permission = plugin.getConfigManager().getPermission("backup");
        if (!permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        Player player = (Player) sender;
        backupGUI.openBackupGUI(player);
    }

    private void handleBuy(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player_only_command"));
            return;
        }

        String permission = plugin.getConfigManager().getPermission("use");
        if (!permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        Player player = (Player) sender;
        ItemStack item = player.getInventory().getItemInMainHand();
        purchaseGUI.openPurchaseGUI(player, item);
    }

    private void handleReload(CommandSender sender) {
        String permission = plugin.getConfigManager().getPermission("reload");
        if (!permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        plugin.getConfigManager().reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "配置文件已重新加载！");
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player_only_command"));
            return;
        }

        Player player = (Player) sender;

        String permission = plugin.getConfigManager().getPermission("admin");
        if (!permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /insurance admin <level1|level2|remove>");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            sender.sendMessage(ChatColor.RED + "请手持一个物品");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "level1":
                insuranceManager.setAdminInsurance(item, true);
                insuranceManager.setInsurance(item, 1, Integer.MAX_VALUE);
                player.getInventory().setItemInMainHand(item);
                sender.sendMessage(plugin.getConfigManager().getMessage("admin_insurance_added"));
                break;
            case "level2":
                insuranceManager.setAdminInsurance(item, true);
                insuranceManager.setInsurance(item, 2, Integer.MAX_VALUE);
                player.getInventory().setItemInMainHand(item);
                sender.sendMessage(plugin.getConfigManager().getMessage("admin_insurance_added"));
                break;
            case "remove":
                insuranceManager.removeInsurance(item);
                player.getInventory().setItemInMainHand(item);
                sender.sendMessage(ChatColor.GREEN + "已清除该物品的所有保险，现在可以重新投保");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "无效的参数。使用 level1、level2 或 remove");
                return;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== 保险插件命令 ===");
        sender.sendMessage(ChatColor.YELLOW + "/insurance toggle" + ChatColor.WHITE + " - 切换插件功能");
        sender.sendMessage(ChatColor.YELLOW + "/insurance gui" + ChatColor.WHITE + " - 打开保险信息 GUI");
        sender.sendMessage(ChatColor.YELLOW + "/insurance buy" + ChatColor.WHITE + " - 为手持物品购买保险");
        sender.sendMessage(ChatColor.YELLOW + "/insurance backup" + ChatColor.WHITE + " - 打开备份恢复 GUI");

        String adminPermission = plugin.getConfigManager().getPermission("admin");
        String reloadPermission = plugin.getConfigManager().getPermission("reload");

        boolean canUseAdmin = adminPermission.isEmpty() || sender.hasPermission(adminPermission);
        boolean canUseReload = reloadPermission.isEmpty() || sender.hasPermission(reloadPermission);

        if (canUseAdmin || canUseReload) {
            if (canUseReload) {
                sender.sendMessage(ChatColor.YELLOW + "/insurance reload" + ChatColor.WHITE + " - 重新加载配置文件");
            }
            if (canUseAdmin) {
                sender.sendMessage(ChatColor.YELLOW + "/insurance admin <level1|level2|remove>" + ChatColor.WHITE + " - 管理员保险操作");
            }
        }
    }
}