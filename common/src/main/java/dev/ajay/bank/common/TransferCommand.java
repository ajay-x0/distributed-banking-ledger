package dev.ajay.bank.common;
import java.util.UUID;
import jakarta.validation.constraints.*;
public record TransferCommand(@NotNull UUID source,@NotNull UUID destination,@Min(1) @Max(100000000000L) long amountMinor,@Pattern(regexp="INR") @NotNull String currency) {
 public void validate(){if(source==null||destination==null||source.equals(destination)||amountMinor<1||amountMinor>100000000000L||!"INR".equals(currency))throw new IllegalArgumentException("Invalid transfer");}
}
