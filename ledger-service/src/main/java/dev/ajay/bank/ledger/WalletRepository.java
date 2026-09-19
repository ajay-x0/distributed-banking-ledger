package dev.ajay.bank.ledger;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface WalletRepository extends JpaRepository<Wallet,UUID> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select w from Wallet w where w.id in :ids order by w.id")
 List<Wallet> lockAll(@org.springframework.data.repository.query.Param("ids") List<UUID> ids);
}
