package ink.inventory;

import ink.messaging.StockDeductionRequested;
import ink.messaging.StockTopology;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class StockDeductionListener {

  private final DeductionService deductionService;
  private final JsonMapper jsonMapper;

  public StockDeductionListener(DeductionService deductionService, JsonMapper jsonMapper) {
    this.deductionService = deductionService;
    this.jsonMapper = jsonMapper;
  }

  @RabbitListener(queues = StockTopology.DEDUCT_QUEUE)
  public void onDeduction(Message message) {
    StockDeductionRequested request = jsonMapper.readValue(message.getBody(), StockDeductionRequested.class);
    deductionService.accept(request);
  }
}
