package ink.workshop.order;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "OrderOutbox")
@Table(name = "outbox")
public class OutboxMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_type", nullable = false, length = 40)
  private String eventType;

  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  protected OutboxMessage() {}

  public OutboxMessage(String eventType, String payload, Instant createdAt) {
    this.eventType = eventType;
    this.payload = payload;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getPayload() {
    return payload;
  }

  public void markPublished(Instant publishedAt) {
    this.publishedAt = publishedAt;
  }
}
