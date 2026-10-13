package ink.inventory;

import ink.messaging.StockReleaseRequested;
import ink.messaging.StockTopology;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class StockReleaseListener {

  private final ReleaseService releaseService;
  private final JsonMapper jsonMapper;

  public StockReleaseListener(ReleaseService releaseService, JsonMapper jsonMapper) {
    this.releaseService = releaseService;
    this.jsonMapper = jsonMapper;
  }

  @RabbitListener(queues = StockTopology.RELEASE_QUEUE)
  public void onRelease(Message message) {
    StockReleaseRequested request = jsonMapper.readValue(message.getBody(), StockReleaseRequested.class);
    releaseService.accept(request);
  }
}
