package cn.ymjacky.transaction;

import java.util.UUID;

public class TransactionRecord {
    private final String transactionId;
    private final UUID playerUuid;
    private final String playerName;
    private final TransactionType type;
    private final double amount;
    private final double balanceBefore;
    private final double balanceAfter;
    private final String description;

    public TransactionRecord(UUID playerUuid, String playerName, TransactionType type,
                            double amount, double balanceBefore, double balanceAfter, String description) {
        this.transactionId = UUID.randomUUID().toString();
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.type = type;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.description = description;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public TransactionType getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public double getBalanceBefore() {
        return balanceBefore;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public enum TransactionType {
        ORDER,
        TRANSFER,
        PAYMENT_ORDER,
        REFUND
    }
}