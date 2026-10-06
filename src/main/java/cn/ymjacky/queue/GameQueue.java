package cn.ymjacky.queue;

import cn.ymjacky.config.QueueConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GameQueue {
    private final QueueConfig config;
    private final Set<QueuePlayer> players;

    public GameQueue(QueueConfig config) {
        this.config = config;
        this.players = ConcurrentHashMap.newKeySet();
    }

    public boolean addPlayer(QueuePlayer player) {
        if (players.size() >= config.getMaxPlayers()) {
            return false;
        }

        boolean added = players.add(player);
        if (added) {
            player.setQueue(this);
        }

        return added;
    }

    public void removePlayer(QueuePlayer player) {
        boolean removed = players.remove(player);
        if (removed) {
            player.setQueue(null);
        }
    }

    public void clear() {
        for (QueuePlayer player : players) {
            player.setQueue(null);
        }
        players.clear();
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
