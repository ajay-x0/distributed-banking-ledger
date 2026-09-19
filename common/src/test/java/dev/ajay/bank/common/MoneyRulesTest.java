package dev.ajay.bank.common;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class MoneyRulesTest {
 @Test void reservationsPreventOverdraft(){assertEquals(700,MoneyRules.reserve(1000,300,400));assertThrows(IllegalArgumentException.class,()->MoneyRules.reserve(1000,700,301));}
 @Test void rejectsInvalidValuesAndOverflow(){assertThrows(IllegalArgumentException.class,()->MoneyRules.reserve(100,0,0));assertThrows(ArithmeticException.class,()->MoneyRules.credit(Long.MAX_VALUE,1));assertThrows(IllegalArgumentException.class,()->MoneyRules.available(1,2));assertThrows(IllegalArgumentException.class,()->MoneyRules.debit(2,3));}
 @Test void randomTransfersConserveMoney(){var random=new Random(42);for(int i=0;i<10000;i++){long source=1+random.nextInt(10000000),destination=random.nextInt(10000000),amount=1+random.nextInt((int)source);assertEquals(source+destination,MoneyRules.debit(source,amount)+MoneyRules.credit(destination,amount));}}
 @Test void validateTransfer(){UUID a=UUID.randomUUID(),b=UUID.randomUUID();new TransferCommand(a,b,1000000,"INR").validate();assertThrows(IllegalArgumentException.class,()->new TransferCommand(a,a,1,"INR").validate());assertThrows(IllegalArgumentException.class,()->new TransferCommand(a,b,0,"INR").validate());assertThrows(IllegalArgumentException.class,()->new TransferCommand(a,b,1,"USD").validate());}
 @Test void retryDelayIsBounded(){assertEquals(2,MoneyRules.retrySeconds(1));assertEquals(4,MoneyRules.retrySeconds(2));assertEquals(60,MoneyRules.retrySeconds(100));}
}
