package dev.ajay.bank.common;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataAccessException;
@RestControllerAdvice
public class ApiError {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<ProblemDetail> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason()==null?"Request failed":e.getReason()));}
 @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class}) ResponseEntity<ProblemDetail> invalid(Exception e){return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Invalid input; check UUIDs, positive minor-unit amount and INR currency"));}
 @ExceptionHandler(DataAccessException.class) ResponseEntity<ProblemDetail> database(DataAccessException e){return ResponseEntity.status(503).body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"Database unavailable or transaction conflicted; retry using the same idempotency key"));}
}
