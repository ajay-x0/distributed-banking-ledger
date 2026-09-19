package dev.ajay.bank.account;
import java.util.UUID;
public interface AccountRepository extends org.springframework.data.jpa.repository.JpaRepository<AccountProfile,UUID> {}
