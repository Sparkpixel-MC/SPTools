package cn.ymjacky.utils;

import cn.ymjacky.SPToolsPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

/**
 * 统一调度入口：所有业务代码必须通过本类调度，禁止直接调用
 * Bukkit.getScheduler() 或 Folia 调度器。
 * Paper 路径回退到传统 BukkitScheduler；Folia 路径使用原生
 * Global/Entity/Async 调度器。Folia 检测只做一次并缓存。
 */
public final class SchedulerUtil {

    /** 可取消的任务句柄，屏蔽 ScheduledTask 与 BukkitTask 的差异。 */
    public interface TaskHandle {
        void cancel();
    }

    private static final boolean IS_FOLIA = detectFolia();

    private SchedulerUtil() {
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    private static SPToolsPlugin plugin() {
        return SPToolsPlugin.getInstance();
    }

    // ---------- 全局 ----------

    public static TaskHandle runGlobal(Runnable task) {
        if (IS_FOLIA) {
            return wrap(Bukkit.getGlobalRegionScheduler().run(plugin(), t -> task.run()));
        }
        return wrap(Bukkit.getScheduler().runTask(plugin(), task));
    }

    public static TaskHandle runGlobalDelayed(Runnable task, long ticks) {
        if (ticks < 1) ticks = 1;
        if (IS_FOLIA) {
            return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin(), t -> task.run(), ticks));
        }
        return wrap(Bukkit.getScheduler().runTaskLater(plugin(), task, ticks));
    }

    public static TaskHandle runGlobalAtFixedRate(Runnable task, long initialDelayTicks, long periodTicks) {
        if (initialDelayTicks < 1) initialDelayTicks = 1;
        if (periodTicks < 1) periodTicks = 1;
        if (IS_FOLIA) {
            return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin(), t -> task.run(), initialDelayTicks, periodTicks));
        }
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin(), task, initialDelayTicks, periodTicks));
    }

    // ---------- 实体（玩家） ----------

    public static TaskHandle runAtEntity(Entity entity, Runnable task) {
        if (IS_FOLIA) {
            return wrap(entity.getScheduler().run(plugin(), t -> task.run(), null));
        }
        return wrap(Bukkit.getScheduler().runTask(plugin(), task));
    }

    public static TaskHandle runAtEntityDelayed(Entity entity, Runnable task, long ticks) {
        if (ticks < 1) ticks = 1;
        if (IS_FOLIA) {
            return wrap(entity.getScheduler().runDelayed(plugin(), t -> task.run(), null, ticks));
        }
        return wrap(Bukkit.getScheduler().runTaskLater(plugin(), task, ticks));
    }

    // ---------- 异步（禁止触碰任何 Region/Entity 实时状态） ----------

    public static TaskHandle runAsync(Runnable task) {
        if (IS_FOLIA) {
            return wrap(Bukkit.getAsyncScheduler().runNow(plugin(), t -> task.run()));
        }
        return wrap(Bukkit.getScheduler().runTaskAsynchronously(plugin(), task));
    }

    public static TaskHandle runAsyncDelayed(Runnable task, long delay, TimeUnit unit) {
        if (IS_FOLIA) {
            return wrap(Bukkit.getAsyncScheduler().runDelayed(plugin(), t -> task.run(), delay, unit));
        }
        return wrap(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin(), task, toTicks(delay, unit)));
    }

    public static TaskHandle runAsyncAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        if (IS_FOLIA) {
            return wrap(Bukkit.getAsyncScheduler().runAtFixedRate(plugin(), t -> task.run(), initialDelay, period, unit));
        }
        return wrap(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin(), task, toTicks(initialDelay, unit), toTicks(period, unit)));
    }

    private static long toTicks(long duration, TimeUnit unit) {
        return Math.max(1, unit.toMillis(duration) / 50);
    }

    private static TaskHandle wrap(ScheduledTask task) {
        return task::cancel;
    }

    private static TaskHandle wrap(BukkitTask task) {
        return task::cancel;
    }
}
