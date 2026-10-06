package cn.ymjacky.command;

import cn.ymjacky.utils.BossBarRemoveUtil;
import cn.ymjacky.utils.SchedulerUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

public class BossBarRemoveCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    public BossBarRemoveCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!sender.hasPermission("removebossbars.use")) {
            sender.sendMessage("你没有权限执行此命令。");
            return true;
        }
        // BossBar 是全局状态，统一在全局线程处理
        SchedulerUtil.runGlobal(() -> {
            int count = BossBarRemoveUtil.removeAllBossBars();
            sender.sendMessage("已移除 " + count + " 个 BossBar。");
        });
        return true;
    }
}
