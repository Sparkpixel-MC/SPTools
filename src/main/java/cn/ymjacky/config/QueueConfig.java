package cn.ymjacky.config;

public class QueueConfig {
    private final String name;
    private final int maxPlayers;
    private final int minPlayers;
    private final String gameCommand;
    private final int confirmationTime;
    private final int countdownTime;
    private final int bufferTime;
    private final boolean requireConfirmation;

    public QueueConfig(String name, int maxPlayers, int minPlayers, String gameCommand,
                       int confirmationTime, int countdownTime, int bufferTime,
                       boolean requireConfirmation) {
        this.name = name;
        this.maxPlayers = maxPlayers;
        this.minPlayers = minPlayers;
        this.gameCommand = gameCommand;
        this.confirmationTime = confirmationTime;
        this.countdownTime = countdownTime;
        this.bufferTime = bufferTime;
        this.requireConfirmation = requireConfirmation;
    }

    // Getters
    public String getName() { return name; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getMinPlayers() { return minPlayers; }
    public String getGameCommand() { return gameCommand; }
    public int getConfirmationTime() { return confirmationTime; }
    public int getCountdownTime() { return countdownTime; }
    public int getBufferTime() { return bufferTime; }
    public boolean requiresConfirmation() { return requireConfirmation; }
}