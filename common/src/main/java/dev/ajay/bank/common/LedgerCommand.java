package dev.ajay.bank.common;
import java.util.UUID;
public record LedgerCommand(UUID paymentId,String owner,TransferCommand transfer) {}
