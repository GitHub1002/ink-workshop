package ink.workshop.order;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

  List<Order> findAllByUserIdOrderByIdDesc(long userId);

  Optional<Order> findByIdAndUserId(long id, long userId);
}
