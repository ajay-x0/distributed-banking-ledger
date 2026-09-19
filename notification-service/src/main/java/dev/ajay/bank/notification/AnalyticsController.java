package dev.ajay.bank.notification;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
@RestController
public class AnalyticsController {
 private final JdbcTemplate db;public AnalyticsController(JdbcTemplate db){this.db=db;}
 @GetMapping("/api/admin/analytics") List<Map<String,Object>> analytics(){return db.queryForList("SELECT * FROM analytics_counter ORDER BY name");}
}
