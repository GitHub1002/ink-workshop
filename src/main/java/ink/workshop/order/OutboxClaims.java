package ink.workshop.order;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxClaims {

  private final EntityManager entityManager;

  public OutboxClaims(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Transactional
  public Claimed claimNext(Instant now, Duration claimFor) {
    OffsetDateTime staleBefore = now.minus(claimFor).atOffset(ZoneOffset.UTC);
    @SuppressWarnings("unchecked")
    List<Number> ids = entityManager.createNativeQuery("""
        select id from outbox
        where published_at is null
          and (claimed_at is null or claimed_at < :staleBefore)
        order by id
        for update skip locked
        limit 1
        """)
        .setParameter("staleBefore", staleBefore)
        .getResultList();
    if (ids.isEmpty()) {
      return null;
    }
    OutboxMessage row = entityManager.find(OutboxMessage.class, ids.get(0).longValue());
    if (row == null) {
      return null;
    }
    row.claim(now);
    return new Claimed(row.getId(), row.getEventType(), row.getPayload());
  }

  public record Claimed(long id, String eventType, String payload) {}
}
