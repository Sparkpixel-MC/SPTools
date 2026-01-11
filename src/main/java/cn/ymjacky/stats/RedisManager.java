package cn.ymjacky.stats;

import cn.ymjacky.SPToolsPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.util.*;

public class RedisManager {

    private final SPToolsPlugin plugin;
    private JedisPool jedisPool;
    private final String host;
    private final int port;
    private final String password;
    private final int maxDatabases;
    private final long maxKeysPerDatabase;
    
    // 分库策略：UUID哈希
    private static final String STATS_PREFIX = "stats:";
    private static final String BLOCKS_MINED_PREFIX = "blocks_mined:";
    private static final String BLOCKS_PLACED_PREFIX = "blocks_placed:";
    private static final String SESSION_PREFIX = "sessions:";

    public RedisManager(SPToolsPlugin plugin) {
        this.plugin = plugin;
        this.host = plugin.getConfig().getString("redis.host", "localhost");
        this.port = plugin.getConfig().getInt("redis.port", 6379);
        this.password = plugin.getConfig().getString("redis.password", "");
        this.maxDatabases = plugin.getConfig().getInt("redis.max_databases", 16);
        this.maxKeysPerDatabase = plugin.getConfig().getLong("redis.max_keys_per_database", 10000);
        
        initialize();
    }

    private void initialize() {
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);

            if (password != null && !password.isEmpty()) {
                this.jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
            } else {
                this.jedisPool = new JedisPool(poolConfig, host, port, 2000);
            }

            // 测试连接
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
                plugin.getLogger().info("Redis连接成功: " + host + ":" + port);
            }
        } catch (JedisException e) {
            plugin.getLogger().severe("Redis连接失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 根据UUID计算应该使用的数据库编号
     * 使用UUID的哈希值来分配数据库，确保同一玩家的数据总是在同一个数据库中
     */
    private int getDatabaseIndex(UUID playerUUID) {
        int hashCode = playerUUID.hashCode();
        // 取绝对值并使用模运算分配数据库
        int dbIndex = Math.abs(hashCode) % maxDatabases;
        return dbIndex;
    }

    /**
     * 获取指定数据库的Jedis实例
     */
    private Jedis getJedis(UUID playerUUID) {
        if (jedisPool == null) {
            return null;
        }
        Jedis jedis = jedisPool.getResource();
        int dbIndex = getDatabaseIndex(playerUUID);
        jedis.select(dbIndex);
        return jedis;
    }

    /**
     * 检查当前数据库是否已满，如果满了则返回下一个可用的数据库索引
     */
    private int checkAndFindAvailableDatabase(UUID playerUUID) {
        int currentDbIndex = getDatabaseIndex(playerUUID);
        
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.select(currentDbIndex);
            long keyCount = jedis.dbSize();
            
            if (keyCount >= maxKeysPerDatabase) {
                // 当前数据库已满，尝试查找下一个可用的数据库
                plugin.getLogger().warning("数据库 " + currentDbIndex + " 已满 (" + keyCount + " keys)，尝试分库...");
                
                // 从当前数据库的下一个开始查找
                for (int i = 1; i < maxDatabases; i++) {
                    int nextDbIndex = (currentDbIndex + i) % maxDatabases;
                    jedis.select(nextDbIndex);
                    long nextKeyCount = jedis.dbSize();
                    
                    if (nextKeyCount < maxKeysPerDatabase) {
                        plugin.getLogger().info("切换到数据库 " + nextDbIndex + " (" + nextKeyCount + " keys)");
                        return nextDbIndex;
                    }
                }
                
                // 所有数据库都满了，使用当前数据库（会继续存储）
                plugin.getLogger().warning("所有数据库都已满，继续使用数据库 " + currentDbIndex);
                return currentDbIndex;
            }
            
            return currentDbIndex;
        }
    }

    /**
     * 保存玩家统计数据
     */
    public void savePlayerStats(UUID playerUUID, PlayerStats stats) {
        try (Jedis jedis = getJedis(playerUUID)) {
            if (jedis == null) {
                return;
            }

            String key = STATS_PREFIX + playerUUID.toString();
            
            // 使用Hash存储玩家统计数据
            Map<String, String> data = new HashMap<>();
            data.put("player_name", stats.getPlayerName());
            data.put("blocks_mined", String.valueOf(stats.getBlocksMined()));
            data.put("blocks_placed", String.valueOf(stats.getBlocksPlaced()));
            data.put("online_time_seconds", String.valueOf(stats.getOnlineTimeSeconds()));
            data.put("total_money_earned", String.valueOf(stats.getTotalMoneyEarned()));
            data.put("total_money_spent", String.valueOf(stats.getTotalMoneySpent()));
            data.put("last_join_time", String.valueOf(stats.getLastJoinTime()));
            data.put("last_update_time", String.valueOf(System.currentTimeMillis()));
            
            jedis.hset(key, data);
            
            // 保存方块挖掘数据
            saveBlocksMined(jedis, playerUUID, stats);
            
            // 保存方块放置数据
            saveBlocksPlaced(jedis, playerUUID, stats);
            
            // 保存会话记录
            saveSessions(jedis, playerUUID, stats);
            
        } catch (JedisException e) {
            plugin.getLogger().severe("保存玩家统计数据失败: " + e.getMessage());
        }
    }

    /**
     * 加载玩家统计数据
     */
    public PlayerStats loadPlayerStats(UUID playerUUID) {
        try (Jedis jedis = getJedis(playerUUID)) {
            if (jedis == null) {
                return null;
            }

            String key = STATS_PREFIX + playerUUID.toString();
            
            if (!jedis.exists(key)) {
                return null;
            }
            
            Map<String, String> data = jedis.hgetAll(key);
            
            PlayerStats stats = new PlayerStats(playerUUID, data.get("player_name"));
            stats.setBlocksMined(Long.parseLong(data.getOrDefault("blocks_mined", "0")));
            stats.setBlocksPlaced(Long.parseLong(data.getOrDefault("blocks_placed", "0")));
            stats.setOnlineTimeSeconds(Long.parseLong(data.getOrDefault("online_time_seconds", "0")));
            stats.setTotalMoneyEarned(Long.parseLong(data.getOrDefault("total_money_earned", "0")));
            stats.setTotalMoneySpent(Long.parseLong(data.getOrDefault("total_money_spent", "0")));
            stats.setLastJoinTime(Long.parseLong(data.getOrDefault("last_join_time", "0")));
            
            // 加载方块挖掘数据
            loadBlocksMined(jedis, playerUUID, stats);
            
            // 加载方块放置数据
            loadBlocksPlaced(jedis, playerUUID, stats);
            
            // 加载会话记录
            loadSessions(jedis, playerUUID, stats);
            
            return stats;
            
        } catch (JedisException e) {
            plugin.getLogger().severe("加载玩家统计数据失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 保存方块挖掘数据
     */
    private void saveBlocksMined(Jedis jedis, UUID playerUUID, PlayerStats stats) {
        String key = BLOCKS_MINED_PREFIX + playerUUID.toString();
        jedis.del(key);
        
        for (Map.Entry<String, Long> entry : stats.getBlocksMinedByType().entrySet()) {
            jedis.hset(key, entry.getKey(), entry.getValue().toString());
        }
    }

    /**
     * 加载方块挖掘数据
     */
    private void loadBlocksMined(Jedis jedis, UUID playerUUID, PlayerStats stats) {
        String key = BLOCKS_MINED_PREFIX + playerUUID.toString();
        
        if (jedis.exists(key)) {
            Map<String, String> blocks = jedis.hgetAll(key);
            for (Map.Entry<String, String> entry : blocks.entrySet()) {
                stats.addBlocksMined(entry.getKey(), Long.parseLong(entry.getValue()));
            }
        }
    }

    /**
     * 保存方块放置数据
     */
    private void saveBlocksPlaced(Jedis jedis, UUID playerUUID, PlayerStats stats) {
        String key = BLOCKS_PLACED_PREFIX + playerUUID.toString();
        jedis.del(key);
        
        for (Map.Entry<String, Long> entry : stats.getBlocksPlacedByType().entrySet()) {
            jedis.hset(key, entry.getKey(), entry.getValue().toString());
        }
    }

    /**
     * 加载方块放置数据
     */
    private void loadBlocksPlaced(Jedis jedis, UUID playerUUID, PlayerStats stats) {
        String key = BLOCKS_PLACED_PREFIX + playerUUID.toString();
        
        if (jedis.exists(key)) {
            Map<String, String> blocks = jedis.hgetAll(key);
            for (Map.Entry<String, String> entry : blocks.entrySet()) {
                stats.addBlocksPlaced(entry.getKey(), Long.parseLong(entry.getValue()));
            }
        }
    }

    /**
     * 保存会话记录
     */
    private void saveSessions(Jedis jedis, UUID playerUUID, PlayerStats stats) {
        String key = SESSION_PREFIX + playerUUID.toString();
        jedis.del(key);
        
        List<PlayerStats.SessionRecord> records = stats.getSessionRecords();
        for (int i = 0; i < records.size(); i++) {
            PlayerStats.SessionRecord record = records.get(i);
            String sessionKey = i + "";
            
            Map<String, String> sessionData = new HashMap<>();
            sessionData.put("join_time", String.valueOf(record.getJoinTime()));
            sessionData.put("leave_time", record.getLeaveTime() != null ? record.getLeaveTime().toString() : "");
            
            jedis.hset(key, sessionKey, String.valueOf(record.getJoinTime()) + ":" + 
                (record.getLeaveTime() != null ? record.getLeaveTime() : "null"));
        }
    }

    /**
     * 加载会话记录
     */
    private void loadSessions(Jedis jedis, UUID playerUUID, PlayerStats stats) {
        String key = SESSION_PREFIX + playerUUID.toString();
        
        if (jedis.exists(key)) {
            Map<String, String> sessions = jedis.hgetAll(key);
            List<Map.Entry<String, String>> sortedSessions = new ArrayList<>(sessions.entrySet());
            sortedSessions.sort((a, b) -> b.getKey().compareTo(a.getKey())); // 按索引降序排序
            
            for (Map.Entry<String, String> entry : sortedSessions) {
                String[] parts = entry.getValue().split(":");
                if (parts.length >= 1) {
                    long joinTime = Long.parseLong(parts[0]);
                    PlayerStats.SessionRecord record = new PlayerStats.SessionRecord(joinTime);
                    
                    if (parts.length >= 2 && !parts[1].equals("null")) {
                        record.setLeaveTime(Long.parseLong(parts[1]));
                    }
                    
                    stats.addSessionRecord(record);
                }
            }
        }
    }

    /**
     * 添加会话记录
     */
    public void addSessionRecord(UUID playerUUID, long joinTime) {
        try (Jedis jedis = getJedis(playerUUID)) {
            if (jedis == null) {
                return;
            }

            String key = SESSION_PREFIX + playerUUID.toString();
            String sessionData = joinTime + ":null";
            
            // 获取当前会话数量
            long sessionCount = jedis.hlen(key);
            jedis.hset(key, String.valueOf(sessionCount), sessionData);
            
        } catch (JedisException e) {
            plugin.getLogger().severe("添加会话记录失败: " + e.getMessage());
        }
    }

    /**
     * 更新会话离开时间
     */
    public void updateSessionLeaveTime(UUID playerUUID, long leaveTime) {
        try (Jedis jedis = getJedis(playerUUID)) {
            if (jedis == null) {
                return;
            }

            String key = SESSION_PREFIX + playerUUID.toString();
            
            // 获取最后一个会话记录
            Map<String, String> sessions = jedis.hgetAll(key);
            if (!sessions.isEmpty()) {
                String maxIndex = Collections.max(sessions.keySet());
                String lastSession = sessions.get(maxIndex);
                
                if (lastSession != null && lastSession.endsWith(":null")) {
                    String[] parts = lastSession.split(":");
                    if (parts.length == 2) {
                        long joinTime = Long.parseLong(parts[0]);
                        String newSessionData = joinTime + ":" + leaveTime;
                        jedis.hset(key, maxIndex, newSessionData);
                    }
                }
            }
            
        } catch (JedisException e) {
            plugin.getLogger().severe("更新会话离开时间失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有玩家UUID
     */
    public Set<UUID> getAllPlayerUUIDs() {
        Set<UUID> uuids = new HashSet<>();
        
        try (Jedis jedis = jedisPool.getResource()) {
            // 遍历所有数据库
            for (int dbIndex = 0; dbIndex < maxDatabases; dbIndex++) {
                jedis.select(dbIndex);
                Set<String> keys = jedis.keys(STATS_PREFIX + "*");
                
                for (String key : keys) {
                    String uuidStr = key.substring(STATS_PREFIX.length());
                    try {
                        uuids.add(UUID.fromString(uuidStr));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("无效的UUID: " + uuidStr);
                    }
                }
            }
            
        } catch (JedisException e) {
            plugin.getLogger().severe("获取所有玩家UUID失败: " + e.getMessage());
        }
        
        return uuids;
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        if (jedisPool == null) {
            return false;
        }
        
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.ping().equals("PONG");
        } catch (JedisException e) {
            return false;
        }
    }

    /**
     * 关闭Redis连接
     */
    public void close() {
        if (jedisPool != null) {
            jedisPool.close();
            plugin.getLogger().info("Redis连接已关闭");
        }
    }
}