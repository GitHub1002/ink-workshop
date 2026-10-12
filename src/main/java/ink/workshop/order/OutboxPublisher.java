package ink.workshop.order;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import ink.messaging.StockTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxRepository outboxRepository;
  private final RabbitTemplate rabbitTemplate;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public OutboxPublisher(
      OutboxRepository outboxRepository,
      RabbitTemplate rabbitTemplate,
      TransactionTemplate transactions,
      Clock clock) {
    this.outboxRepository = outboxRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.transactions = transactions;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${ink.outbox.delay:1000}")
  public void publishPending() {
    for (OutboxMessage message : outboxRepository.findTop20ByPublishedAtIsNullOrderByIdAsc()) {
      rabbitTemplate.send(
          StockTopology.EXCHANGE,
          StockTopology.DEDUCT_KEY,
          MessageBuilder.withBody(message.getPayload().getBytes(StandardCharsets.UTF_8))
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .setMessageId(Long.toString(message.getId()))
              .build());
      transactions.executeWithoutResult(status -> outboxRepository.findById(message.getId())
          .ifPresent(row -> row.markPublished(clock.instant())));
      log.info("outbox_published id={}", message.getId());
    }
  }
}
