package dev.ajay.bank.account;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class AccountService {
 private final AccountRepository repo;
 public AccountService(AccountRepository repo){this.repo=repo;}
 public AccountProfile get(UUID id,String owner){var a=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found"));if(!a.owner.equals(owner))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Not your account");return a;}
 @Transactional public AccountProfile rename(UUID id,String owner,long version,String name){
  var a=get(id,owner);if(a.version!=version)throw new ResponseStatusException(HttpStatus.CONFLICT,"Profile version changed; reload before editing");
  a.displayName=name;
  try{return repo.saveAndFlush(a);}catch(org.springframework.orm.ObjectOptimisticLockingFailureException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Concurrent profile update");}
 }
}
