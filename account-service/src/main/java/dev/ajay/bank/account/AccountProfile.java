package dev.ajay.bank.account;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="account_profile")
public class AccountProfile {
 @Id public UUID id;public String owner;
 @Column(name="display_name") public String displayName;
 @Version public long version;
 protected AccountProfile(){}
}
