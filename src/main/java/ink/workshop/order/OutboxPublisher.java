package ink.workshop.order;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import ink.messaging.StockTopology;
import ink.workshop.order.OutboxClaims.Claimed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private static final Duration CLAIM_FOR = Duration.ofSeconds(30);

  private final OutboxRepository outboxRepository;
  private final OutboxClaims outboxClaims;
  private final RabbitTemplate rabbitTemplate;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final int port;

  public OutboxPublisher(
      OutboxRepository outboxRepository,
      OutboxClaims outboxClaims,
      RabbitTemplate rabbitTemplate,
      TransactionTemplate transactions,
      Clock clock,
      @Value("${server.port}") int port) {
    this.outboxRepository = outboxRepository;
    this.outboxClaims = outboxClaims;
    this.rabbitTemplate = rabbitTemplate;
    this.transactions = transactions;
    this.clock = clock;
    this.port = port;
  }

  @Scheduled(fixedDelayString = "${ink.outbox.delay:1000}")
  public void publishPending() {
    for (int n = 0; n < 20; n++) {
      Claimed claimed = outboxClaims.claimNext(clock.instant(), CLAIM_FOR);
      if (claimed == null) {
        return;
      }
      rabbitTemplate.send(
          StockTopology.EXCHANGE,
          StockTopology.DEDUCT_KEY,
          MessageBuilder.withBody(claimed.payload().getBytes(StandardCharsets.UTF_8))
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .setMessageId(Long.toString(claimed.id()))
              .build());
      transactions.executeWithoutResult(status -> outboxRepository.findById(claimed.id())
          .ifPresent(row -> row.markPublished(clock.instant())));
      log.info("outbox_published id={} port={}", claimed.id(), port);
    }
  }
}
