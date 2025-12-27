package cn.ymjacky.queue;

import cn.ymjacky.config.QueueConfig;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameQueue {
    private final QueueConfig config;
    private final Set<QueuePlayer> players;
    private final Queue<QueuePlayer> playerQueue;

    public GameQueue(QueueConfig config) {
        this.config = config;
        this.players = ConcurrentHashMap.newKeySet();
        this.playerQueue = new LinkedList<>();
    }

    public boolean addPlayer(QueuePlayer player) {
        if (players.size() >= config.getMaxPlayers()) {
            return false;
        }

        boolean added = players.add(player);
        if (added) {
            playerQueue.add(player);
            player.setQueue(this);
        }

        return added;
    }

    public void removePlayer(QueuePlayer player) {
        boolean removed = players.remove(player);
        if (removed) {
            playerQueue.remove(player);
            player.setQueue(null);
        }
    }

    public void clear() {
        for (QueuePlayer player : players) {
            player.setQueue(null);
        }
        players.clear();
        playerQueue.clear();
    }

    public boolean isFull() {
        return players.size() >= config.getMaxPlayers();
    }

    public int getPlayerCount() {
        return players.size();
    }

    // Getters
    public String getName() { return config.getName(); }
    public int getMaxPlayers() { return config.getMaxPlayers(); }
    public QueueConfig getConfig() { return config; }
    public List<QueuePlayer> getPlayers() { return new ArrayList<>(players); }
}