package dev.ajay.bank.payment;
import dev.ajay.bank.common.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class PaymentWorkerTest {
 JdbcTemplate db;TransactionTemplate tx;Outbox outbox;PaymentWorker worker;MockRestServiceServer server;Map<String,Object> payment;UUID id;
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);tx=mock(TransactionTemplate.class);outbox=mock(Outbox.class);id=UUID.randomUUID();payment=new HashMap<>(Map.of("id",id,"owner","alice","source",UUID.randomUUID(),"destination",UUID.randomUUID(),"amount_minor",1000L,"currency","INR","state","APPROVED","attempts",0));
  doAnswer(call->{((java.util.function.Consumer<TransactionStatus>)call.getArgument(0)).accept(mock(TransactionStatus.class));return null;}).when(tx).executeWithoutResult(any());
  when(db.queryForList(anyString())).thenReturn(List.of(payment));var builder=RestClient.builder();server=MockRestServiceServer.bindTo(builder).build();worker=new PaymentWorker(db,tx,builder.build(),outbox,new SimpleMeterRegistry(),"http://ledger","http://fraud","http://account");
 }
 @Test void lostSettlementResponseRetriesSameOperation(){server.expect(requestTo("http://ledger/internal/ledger/"+id+"/settle")).andRespond(withException(new java.net.SocketTimeoutException("Lost response after commit")));worker.tick();verify(db).update(startsWith("UPDATE payment SET state=?,resume_state=?"),eq("APPROVED"),isNull(),eq(1),eq("ResourceAccessException"),anyDouble(),eq(id));verifyNoInteractions(outbox);server.verify();}
 @Test void retryAfterRemoteCommitCompletes(){server.expect(requestTo("http://ledger/internal/ledger/"+id+"/settle")).andRespond(withSuccess("{\"state\":\"POSTED\"}",MediaType.APPLICATION_JSON));worker.tick();verify(outbox).append(eq(id),eq("PaymentCOMPLETED"),anyMap());server.verify();}
 @Test void exhaustedRetriesRequireReviewWithoutReleasing(){payment.put("attempts",7);server.expect(requestTo("http://ledger/internal/ledger/"+id+"/settle")).andRespond(withServerError());worker.tick();verify(db).update(startsWith("UPDATE payment SET state=?,resume_state=?"),eq("MANUAL_REVIEW"),eq("APPROVED"),eq(8),eq("InternalServerError"),anyDouble(),eq(id));verify(outbox).append(eq(id),eq("PaymentMANUAL_REVIEW"),anyMap());server.verify();}
 @Test void fraudDenialSchedulesCompensation(){payment.put("state","RESERVED");server.expect(requestTo("http://fraud/internal/fraud/check")).andRespond(withSuccess("{\"approved\":false,\"reason\":\"threshold\"}",MediaType.APPLICATION_JSON));worker.tick();verify(outbox).append(eq(id),eq("PaymentRELEASING"),anyMap());server.verify();}
 @Test void compensationAcknowledgementRejectsPayment(){payment.put("state","RELEASING");server.expect(requestTo("http://ledger/internal/ledger/"+id+"/release")).andRespond(withSuccess("{\"state\":\"RELEASED\"}",MediaType.APPLICATION_JSON));worker.tick();verify(outbox).append(eq(id),eq("PaymentREJECTED"),anyMap());server.verify();}
 @Test void downstreamDatabaseFailureDoesNotBecomeBusinessRejection(){server.expect(requestTo("http://ledger/internal/ledger/"+id+"/settle")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));worker.tick();verifyNoInteractions(outbox);server.verify();}
}
