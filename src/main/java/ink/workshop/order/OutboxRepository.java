package ink.workshop.order;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

  List<OutboxMessage> findTop20ByPublishedAtIsNullOrderByIdAsc();
}
