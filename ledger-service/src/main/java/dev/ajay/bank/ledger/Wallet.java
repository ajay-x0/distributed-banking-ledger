package dev.ajay.bank.ledger;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="wallet")
public class Wallet {
 @Id public UUID id;
 @Column(nullable=false) public String owner;
 @Column(nullable=false) public String currency;
 @Column(name="balance_minor",nullable=false) public long balance;
 @Column(name="reserved_minor",nullable=false) public long reserved;
 @Version public long version;
 protected Wallet(){}
}
