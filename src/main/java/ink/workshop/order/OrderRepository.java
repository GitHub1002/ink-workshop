package ink.workshop.order;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

  List<Order> findAllByUserIdOrderByIdDesc(long userId);

  Optional<Order> findByIdAndUserId(long id, long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.id = :id and o.userId = :userId")
  Optional<Order> lockByIdAndUserId(@Param("id") long id, @Param("userId") long userId);

  @EntityGraph(attributePaths = "lines")
  Optional<Order> findByUserIdAndIdempotencyKey(long userId, String idempotencyKey);
}
