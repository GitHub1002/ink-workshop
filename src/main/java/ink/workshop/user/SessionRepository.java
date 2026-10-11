package ink.workshop.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, Long> {

  Optional<Session> findByToken(String token);
}
