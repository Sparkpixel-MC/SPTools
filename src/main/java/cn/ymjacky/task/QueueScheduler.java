package cn.ymjacky.task;

import org.bukkit.Bukkit;
import cn.ymjacky.SPMinigamesPlugin;
import cn.ymjacky.queue.QueueGroup;
import cn.ymjacky.queue.QueueManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.*;

public class QueueScheduler {

    private final SPMinigamesPlugin plugin;
    private final QueueManager queueManager;
    private final Map<String, ScheduledTask> activeTasks;
    private final Queue<String> groupQueue;
    private boolean isProcessing;

    public QueueScheduler(SPMinigamesPlugin plugin, QueueManager queueManager) {
        this.plugin = plugin;
        this.queueManager = queueManager;
        this.activeTasks = new HashMap<>();
        this.groupQueue = new LinkedList<>();
        this.isProcessing = false;
    }

    public void scheduleGroup(QueueGroup group) {
        synchronized (groupQueue) {
            groupQueue.add(group.getId());
        }

        if (!isProcessing) {
            processNextGroup();
        }
    }

    private void processNextGroup() {
        synchronized (groupQueue) {
            if (isProcessing || groupQueue.isEmpty()) {
                return;
            }
            isProcessing = true;
            String groupId = groupQueue.poll();
            ScheduledTask task = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
                QueueGroup group = queueManager.getGroup(groupId);
                if (group != null) {
                    startConfirmation(group);
                }
                synchronized (groupQueue) {
                    isProcessing = false;
                    if (!groupQueue.isEmpty()) {
                        processNextGroup();
                    }
                }
            }, 1L);

            activeTasks.put(groupId + "_process", task);
        }
    }

    private void startConfirmation(QueueGroup group) {
        String groupId = group.getId();
        int confirmationTicks = group.getConfirmationTime() * 20;
        ScheduledTask timeoutTask = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
            QueueGroup currentGroup = queueManager.getGroup(groupId);
            if (currentGroup != null && !currentGroup.allConfirmed()) {
                currentGroup.timeout();
                queueManager.removeGroup(groupId);
                cancelGroupTasks(groupId);
                scheduleBufferPeriod();
            }
        }, confirmationTicks);

        activeTasks.put(groupId + "_timeout", timeoutTask);
    }

    public void startCountdown(QueueGroup group) {
        String groupId = group.getId();
        ScheduledTask timeoutTask = activeTasks.remove(groupId + "_timeout");
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }

        ScheduledTask countdownTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> {
            QueueGroup currentGroup = queueManager.getGroup(groupId);
            if (currentGroup != null) {
                currentGroup.updateCountdown();
            } else {
                scheduledTask.cancel();
            }
        }, 1L, 20L);

        activeTasks.put(groupId + "_countdown", countdownTask);
    }

    private void scheduleBufferPeriod() {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
            synchronized (groupQueue) {
                if (!groupQueue.isEmpty()) {
                    processNextGroup();
                }
            }
        }, 20L);
    }

    public void cancelGroupTasks(String groupId) {
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, ScheduledTask> entry : activeTasks.entrySet()) {
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
        for (ScheduledTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
        groupQueue.clear();
        isProcessing = false;
    }
}