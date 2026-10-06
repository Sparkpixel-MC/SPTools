package cn.ymjacky.task;

import cn.ymjacky.SPToolsPlugin;
import cn.ymjacky.queue.QueueGroup;
import cn.ymjacky.queue.QueueManager;
import cn.ymjacky.utils.SchedulerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QueueScheduler {

    private final SPToolsPlugin plugin;
    private final QueueManager queueManager;
    private final Map<String, SchedulerUtil.TaskHandle> activeTasks;

    public QueueScheduler(SPToolsPlugin plugin, QueueManager queueManager) {
        this.plugin = plugin;
        this.queueManager = queueManager;
        this.activeTasks = new ConcurrentHashMap<>();
    }

    public void scheduleGroup(QueueGroup group) {
        String groupId = group.getId();
        // Folia 下至少 1 tick 延迟，让确认窗口在下一个 tick 开始
        SchedulerUtil.TaskHandle processTask = SchedulerUtil.runGlobalDelayed(() -> {
            activeTasks.remove(groupId + "_process");
            QueueGroup currentGroup = queueManager.getGroup(groupId);
            if (currentGroup != null) {
                startConfirmation(currentGroup);
            }
        }, 1L);
        activeTasks.put(groupId + "_process", processTask);
    }

    private void startConfirmation(QueueGroup group) {
        String groupId = group.getId();

        // 队列配置为无需确认时，直接进入倒计时
        if (!group.getQueue().getConfig().requiresConfirmation()) {
            startCountdown(group);
            return;
        }

        long confirmationTicks = Math.max(1L, group.getConfirmationTime() * 20L);
        SchedulerUtil.TaskHandle timeoutTask = SchedulerUtil.runGlobalDelayed(() -> {
            activeTasks.remove(groupId + "_timeout");
            QueueGroup currentGroup = queueManager.getGroup(groupId);
            if (currentGroup != null && !currentGroup.allConfirmed()) {
                currentGroup.timeout();
                queueManager.disbandGroup(groupId);
            }
        }, confirmationTicks);
        activeTasks.put(groupId + "_timeout", timeoutTask);
    }

    public void startCountdown(QueueGroup group) {
        String groupId = group.getId();
        SchedulerUtil.TaskHandle timeoutTask = activeTasks.remove(groupId + "_timeout");
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }

        SchedulerUtil.TaskHandle countdownTask = SchedulerUtil.runGlobalAtFixedRate(() -> {
            QueueGroup currentGroup = queueManager.getGroup(groupId);
            if (currentGroup != null) {
                currentGroup.updateCountdown();
            } else {
                stopCountdown(groupId);
            }
        }, 1L, 20L);
        activeTasks.put(groupId + "_countdown", countdownTask);
    }

    public void stopCountdown(String groupId) {
        SchedulerUtil.TaskHandle countdownTask = activeTasks.remove(groupId + "_countdown");
        if (countdownTask != null) {
            countdownTask.cancel();
        }
    }

    public void cancelGroupTasks(String groupId) {
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, SchedulerUtil.TaskHandle> entry : activeTasks.entrySet()) {
            if (entry.getKey().startsWith(groupId)) {
                entry.getValue().cancel();
                keysToRemove.add(entry.getKey());
            }
        }
        for (String key : keysToRemove) {
            activeTasks.remove(key);
        }
    }

    public void shutdown() {
        for (SchedulerUtil.TaskHandle task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
