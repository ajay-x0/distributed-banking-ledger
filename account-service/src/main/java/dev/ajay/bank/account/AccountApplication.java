package dev.ajay.bank.account;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages="dev.ajay.bank")
@EntityScan("dev.ajay.bank") @EnableJpaRepositories("dev.ajay.bank") @EnableScheduling
public class AccountApplication { public static void main(String[] args) { SpringApplication.run(AccountApplication.class,args); } }
