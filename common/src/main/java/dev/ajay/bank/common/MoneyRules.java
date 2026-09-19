package dev.ajay.bank.common;
/** All amounts are integer paise. No floating point or cache participates in spending. */
public final class MoneyRules {
 private MoneyRules(){}
 public static long available(long balance,long reserved){if(balance<0||reserved<0||reserved>balance)throw new IllegalArgumentException("Invalid wallet");return balance-reserved;}
 public static long reserve(long balance,long reserved,long amount){if(amount<=0||available(balance,reserved)<amount)throw new IllegalArgumentException("Insufficient available funds");return Math.addExact(reserved,amount);}
 public static long debit(long balance,long amount){if(amount<=0||balance<amount)throw new IllegalArgumentException("Insufficient funds");return Math.subtractExact(balance,amount);}
 public static long credit(long balance,long amount){if(amount<=0)throw new IllegalArgumentException("Invalid credit");return Math.addExact(balance,amount);}
 public static long retrySeconds(int attempts){return Math.min(60L,1L<<Math.min(Math.max(attempts,0),6));}
}
