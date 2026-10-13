package ink.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StockTopology {

  public static final String EXCHANGE = "ink.stock";
  public static final String DEAD_LETTER_EXCHANGE = "ink.stock.dlx";
  public static final String DEDUCT_QUEUE = "ink.stock.deduct";
  public static final String RESULT_QUEUE = "ink.stock.result";
  public static final String DEDUCT_DLQ = "ink.stock.deduct.dlq";
  public static final String RESULT_DLQ = "ink.stock.result.dlq";
  public static final String RELEASE_QUEUE = "ink.stock.release";
  public static final String RELEASE_DLQ = "ink.stock.release.dlq";
  public static final String DEDUCT_KEY = "deduct";
  public static final String RELEASE_KEY = "release";
  public static final String RESULT_KEY = "result";

  @Bean
  DirectExchange stockExchange() {
    return new DirectExchange(EXCHANGE);
  }

  @Bean
  DirectExchange deadLetterExchange() {
    return new DirectExchange(DEAD_LETTER_EXCHANGE);
  }

  @Bean
  Queue deductQueue() {
    return QueueBuilder.durable(DEDUCT_QUEUE)
        .deadLetterExchange(DEAD_LETTER_EXCHANGE)
        .deadLetterRoutingKey(DEDUCT_KEY)
        .build();
  }

  @Bean
  Queue releaseQueue() {
    return QueueBuilder.durable(RELEASE_QUEUE)
        .deadLetterExchange(DEAD_LETTER_EXCHANGE)
        .deadLetterRoutingKey(RELEASE_KEY)
        .build();
  }

  @Bean
  Queue resultQueue() {
    return QueueBuilder.durable(RESULT_QUEUE)
        .deadLetterExchange(DEAD_LETTER_EXCHANGE)
        .deadLetterRoutingKey(RESULT_KEY)
        .build();
  }

  @Bean
  Queue deductDeadLetterQueue() {
    return QueueBuilder.durable(DEDUCT_DLQ).build();
  }

  @Bean
  Queue releaseDeadLetterQueue() {
    return QueueBuilder.durable(RELEASE_DLQ).build();
  }

  @Bean
  Queue resultDeadLetterQueue() {
    return QueueBuilder.durable(RESULT_DLQ).build();
  }

  @Bean
  Binding deductBinding() {
    return BindingBuilder.bind(deductQueue()).to(stockExchange()).with(DEDUCT_KEY);
  }

  @Bean
  Binding releaseBinding() {
    return BindingBuilder.bind(releaseQueue()).to(stockExchange()).with(RELEASE_KEY);
  }

  @Bean
  Binding resultBinding() {
    return BindingBuilder.bind(resultQueue()).to(stockExchange()).with(RESULT_KEY);
  }

  @Bean
  Binding deductDeadLetterBinding() {
    return BindingBuilder.bind(deductDeadLetterQueue()).to(deadLetterExchange()).with(DEDUCT_KEY);
  }

  @Bean
  Binding releaseDeadLetterBinding() {
    return BindingBuilder.bind(releaseDeadLetterQueue()).to(deadLetterExchange()).with(RELEASE_KEY);
  }

  @Bean
  Binding resultDeadLetterBinding() {
    return BindingBuilder.bind(resultDeadLetterQueue()).to(deadLetterExchange()).with(RESULT_KEY);
  }
}
