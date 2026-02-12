package cn.ymjacky.command;

import cn.ymjacky.SPToolsPlugin;
import cn.ymjacky.gui.BackupGUI;
import cn.ymjacky.gui.InsuranceGUI;
import cn.ymjacky.gui.InsurancePurchaseGUI;
import cn.ymjacky.manager.EconomyManager;
import cn.ymjacky.manager.InsuranceManager;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class InsuranceCommand implements CommandExecutor, TabCompleter {

    private final SPToolsPlugin plugin;
    private final InsuranceManager insuranceManager;
    private final EconomyManager economyManager;
    private final InsuranceGUI insuranceGUI;
    private final BackupGUI backupGUI;
    private final InsurancePurchaseGUI purchaseGUI;

    private static final List<String> MAIN_COMMANDS = Arrays.asList(
            "toggle", "gui", "backup", "buy", "reload", "admin"
    );
    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList(
            "level1", "level2", "remove"
    );

    public InsuranceCommand(@UnknownNullability SPToolsPlugin plugin) {
        this.plugin = plugin;
        this.insuranceManager = plugin.getInsuranceManager();
        this.economyManager = plugin.getEconomyManager();
        this.insuranceGUI = new InsuranceGUI(plugin);
        this.backupGUI = new BackupGUI(plugin);
        this.purchaseGUI = new InsurancePurchaseGUI(plugin);
    }

    /**
     * 检查物品是否是粘液科技（Slimefun）的灵魂绑定物品
     */
    private boolean isSoulbound(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // 方法1: 检查 slimefun:soulbound 布尔值标记
        try {
            NamespacedKey slimefunSoulboundKey = NamespacedKey.fromString("slimefun:soulbound");
            if (slimefunSoulboundKey != null) {
                Boolean isSoulbound = container.get(slimefunSoulboundKey, PersistentDataType.BOOLEAN);
                if (isSoulbound != null && isSoulbound) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        // 方法2: 检查 slimefun:slimefun_item 字符串值是否包含 BOUND
        try {
            NamespacedKey slimefunItemKey = NamespacedKey.fromString("slimefun:slimefun_item");
            if (slimefunItemKey != null) {
                String slimefunItemId = container.get(slimefunItemKey, PersistentDataType.STRING);
                if (slimefunItemId != null && slimefunItemId.contains("BOUND")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    // ==================== 命令执行 ====================
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
        boolean newState = plugin.isPluginEnabled();
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
        plugin.getConfigManager().reloadInsuranceConfig();
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

        if (isSoulbound(item)) {
            sender.sendMessage(ChatColor.RED + "灵魂绑定物品无法设置管理员保险");
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

    // ==================== Tab 补全 ====================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            completions = MAIN_COMMANDS.stream()
                    .filter(cmd -> cmd.startsWith(input))
                    .collect(Collectors.toList());

            // 根据配置中的权限动态移除无权限的命令
            if (!hasPermission(sender, "toggle")) {
                completions.remove("toggle");
            }
            if (!hasPermission(sender, "reload")) {
                completions.remove("reload");
            }
            if (!hasPermission(sender, "admin")) {
                completions.remove("admin");
            }
            // 对于普通玩家，可能还需要考虑其他权限（如gui/backup/buy），但此处保留完整列表
        } else if (args.length == 2) {
            String firstArg = args[0].toLowerCase();
            if (firstArg.equals("admin") && hasPermission(sender, "admin")) {
                String input = args[1].toLowerCase();
                completions = ADMIN_SUBCOMMANDS.stream()
                        .filter(sub -> sub.startsWith(input))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }

    /**
     * 统一的权限检查工具方法，从配置管理器获取权限字符串
     * @param sender 命令发送者
     * @param key 权限配置键（如 "admin", "reload" 等）
     * @return 如果无需权限或拥有权限则返回 true
     */
    private boolean hasPermission(CommandSender sender, String key) {
        String permission = plugin.getConfigManager().getPermission(key);
        return permission.isEmpty() || sender.hasPermission(permission);
    }
}