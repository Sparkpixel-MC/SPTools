package cn.ymjacky.stats.command;

import cn.ymjacky.SPToolsPlugin;
import cn.ymjacky.stats.PlayerStats;
import cn.ymjacky.stats.StatsManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class StatsCommand implements CommandExecutor, TabCompleter {

    private final SPToolsPlugin plugin;
    private final StatsManager statsManager;
    private final Economy economy;
    private final SimpleDateFormat dateFormat;

    public StatsCommand(SPToolsPlugin plugin, StatsManager statsManager, Economy economy) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.economy = economy;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sptools.stats")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令!");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行!");
                return true;
            }
            Player player = (Player) sender;
            showPlayerStats(sender, player.getUniqueId());
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "view":
            case "查看":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /stats view <玩家名>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "玩家不在线!");
                    return true;
                }
                showPlayerStats(sender, target.getUniqueId());
                break;

            case "top":
            case "排行":
                showTopStats(sender, args.length > 1 ? args[1] : "money");
                break;

            case "sessions":
            case "会话":
                if (args.length < 2) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "用法: /stats sessions <玩家名>");
                        return true;
                    }
                    Player player = (Player) sender;
                    showSessionRecords(sender, player.getUniqueId());
                } else {
                    Player target2 = Bukkit.getPlayer(args[1]);
                    if (target2 == null) {
                        sender.sendMessage(ChatColor.RED + "玩家不在线!");
                        return true;
                    }
                    showSessionRecords(sender, target2.getUniqueId());
                }
                break;

            case "reload":
            case "重载":
                if (!sender.hasPermission("sptools.stats.admin")) {
                    sender.sendMessage(ChatColor.RED + "你没有权限使用此命令!");
                    return true;
                }
                statsManager.saveStats();
                sender.sendMessage(ChatColor.GREEN + "统计数据已保存!");
                break;

            case "help":
            case "帮助":
                showHelp(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "未知命令! 使用 /stats help 查看帮助");
                break;
        }

        return true;
    }

    private void showPlayerStats(CommandSender sender, UUID playerUUID) {
        PlayerStats stats = statsManager.getPlayerStats(playerUUID);
        if (stats == null) {
            sender.sendMessage(ChatColor.RED + "找不到该玩家的统计数据!");
            return;
        }

        String playerName = stats.getPlayerName();
        double currentBalance = economy != null ? economy.getBalance(Bukkit.getPlayer(playerUUID)) : 0;

        sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.YELLOW + "玩家统计" + ChatColor.GOLD + " ==========");
        sender.sendMessage(ChatColor.WHITE + "玩家: " + ChatColor.GREEN + playerName);
        sender.sendMessage(ChatColor.WHITE + "当前余额: " + ChatColor.GREEN + (economy != null ? economy.format(currentBalance) : "N/A"));
        sender.sendMessage(ChatColor.WHITE + "挖掘方块总数: " + ChatColor.GREEN + stats.getBlocksMined());
        sender.sendMessage(ChatColor.WHITE + "放置方块总数: " + ChatColor.GREEN + stats.getBlocksPlaced());
        sender.sendMessage(ChatColor.WHITE + "在线时长: " + ChatColor.GREEN + stats.getOnlineTimeFormatted());
        sender.sendMessage(ChatColor.WHITE + "累计收入: " + ChatColor.GREEN + (economy != null ? economy.format(stats.getTotalMoneyEarned()) : stats.getTotalMoneyEarned()));
        sender.sendMessage(ChatColor.WHITE + "累计支出: " + ChatColor.GREEN + (economy != null ? economy.format(stats.getTotalMoneySpent()) : stats.getTotalMoneySpent()));
        sender.sendMessage(ChatColor.WHITE + "会话次数: " + ChatColor.GREEN + stats.getSessionRecords().size());
        sender.sendMessage(ChatColor.GOLD + "====================================");
    }

    private void showSessionRecords(CommandSender sender, UUID playerUUID) {
        PlayerStats stats = statsManager.getPlayerStats(playerUUID);
        if (stats == null) {
            sender.sendMessage(ChatColor.RED + "找不到该玩家的统计数据!");
            return;
        }

        List<PlayerStats.SessionRecord> records = stats.getSessionRecords();
        if (records.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "该玩家暂无会话记录!");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.YELLOW + "会话记录 (" + stats.getPlayerName() + ")" + ChatColor.GOLD + " ==========");
        
        int count = Math.min(10, records.size());
        for (int i = Math.max(0, records.size() - count); i < records.size(); i++) {
            PlayerStats.SessionRecord record = records.get(i);
            String joinTime = dateFormat.format(new Date(record.getJoinTime()));
            String leaveTime = record.getLeaveTime() != null ? dateFormat.format(new Date(record.getLeaveTime())) : "在线中";
            
            sender.sendMessage(ChatColor.WHITE + "#" + (i + 1) + " " + ChatColor.GRAY + "进入: " + ChatColor.GREEN + joinTime);
            sender.sendMessage(ChatColor.WHITE + "    " + ChatColor.GRAY + "离开: " + ChatColor.GREEN + leaveTime);
            sender.sendMessage(ChatColor.WHITE + "    " + ChatColor.GRAY + "时长: " + ChatColor.YELLOW + record.getDurationFormatted());
            sender.sendMessage(" ");
        }

        if (records.size() > 10) {
            sender.sendMessage(ChatColor.GRAY + "仅显示最近 10 条记录，共 " + records.size() + " 条");
        }
        sender.sendMessage(ChatColor.GOLD + "====================================");
    }

    private void showTopStats(CommandSender sender, String type) {
        Map<UUID, PlayerStats> allStats = statsManager.getAllPlayerStats();

        if (allStats.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "暂无统计数据!");
            return;
        }

        List<PlayerStats> sortedStats = new ArrayList<>(allStats.values());

        switch (type.toLowerCase()) {
            case "mine":
            case "挖掘":
                sortedStats.sort((a, b) -> Long.compare(b.getBlocksMined(), a.getBlocksMined()));
                sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.YELLOW + "挖掘排行榜" + ChatColor.GOLD + " ==========");
                break;
            case "place":
            case "放置":
                sortedStats.sort((a, b) -> Long.compare(b.getBlocksPlaced(), a.getBlocksPlaced()));
                sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.YELLOW + "放置排行榜" + ChatColor.GOLD + " ==========");
                break;
            case "online":
            case "在线":
                sortedStats.sort((a, b) -> Long.compare(b.getOnlineTimeSeconds(), a.getOnlineTimeSeconds()));
                sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.YELLOW + "在线时长排行榜" + ChatColor.GOLD + " ==========");
                break;
            case "earn":
            case "收入":
                sortedStats.sort((a, b) -> Long.compare(b.getTotalMoneyEarned(), a.getTotalMoneyEarned()));
                sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.YELLOW + "收入排行榜" + ChatColor.GOLD + " ==========");
                break;
            case "spend":
            case "支出":
                sortedStats.sort((a, b) -> Long.compare(b.getTotalMoneySpent(), a.getTotalMoneySpent()));
                sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.YELLOW + "支出排行榜" + ChatColor.GOLD + " ==========");
                break;
            case "money":
            case "金钱":
            default:
                sortedStats.sort((a, b) -> Long.compare(b.getTotalMoneyEarned() - b.getTotalMoneySpent(), a.getTotalMoneyEarned() - a.getTotalMoneySpent()));
                sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.YELLOW + "净收入排行榜" + ChatColor.GOLD + " ==========");
                break;
        }

        int count = Math.min(10, sortedStats.size());
        for (int i = 0; i < count; i++) {
            PlayerStats stats = sortedStats.get(i);
            ChatColor medalColor = i == 0 ? ChatColor.GOLD : (i == 1 ? ChatColor.GRAY : (i == 2 ? ChatColor.RED : ChatColor.WHITE));
            String value;
            switch (type.toLowerCase()) {
                case "mine":
                case "挖掘":
                    value = String.valueOf(stats.getBlocksMined());
                    break;
                case "place":
                case "放置":
                    value = String.valueOf(stats.getBlocksPlaced());
                    break;
                case "online":
                case "在线":
                    value = stats.getOnlineTimeFormatted();
                    break;
                case "earn":
                case "收入":
                    value = economy != null ? economy.format(stats.getTotalMoneyEarned()) : String.valueOf(stats.getTotalMoneyEarned());
                    break;
                case "spend":
                case "支出":
                    value = economy != null ? economy.format(stats.getTotalMoneySpent()) : String.valueOf(stats.getTotalMoneySpent());
                    break;
                default:
                    value = economy != null ? economy.format(stats.getTotalMoneyEarned() - stats.getTotalMoneySpent()) : String.valueOf(stats.getTotalMoneyEarned() - stats.getTotalMoneySpent());
                    break;
            }
            sender.sendMessage(medalColor + String.valueOf(i + 1) + ". " + ChatColor.GREEN + stats.getPlayerName() + ChatColor.WHITE + " - " + value);
        }
        sender.sendMessage(ChatColor.GOLD + "====================================");
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.YELLOW + "统计命令帮助" + ChatColor.GOLD + " ==========");
        sender.sendMessage(ChatColor.WHITE + "/stats - 查看自己的统计");
        sender.sendMessage(ChatColor.WHITE + "/stats view <玩家名> - 查看指定玩家的统计");
        sender.sendMessage(ChatColor.WHITE + "/stats top [类型] - 查看排行榜");
        sender.sendMessage(ChatColor.WHITE + "  类型: mine(挖掘), place(放置), online(在线), earn(收入), spend(支出), money(金钱)");
        sender.sendMessage(ChatColor.WHITE + "/stats sessions [玩家名] - 查看会话记录");
        sender.sendMessage(ChatColor.WHITE + "/stats reload - 保存统计数据 (需要权限)");
        sender.sendMessage(ChatColor.GOLD + "====================================");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("view", "top", "sessions", "reload", "help"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            completions.addAll(Arrays.asList("mine", "place", "online", "earn", "spend", "money"));
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}