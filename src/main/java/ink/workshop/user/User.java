package ink.workshop.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 120)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(nullable = false, length = 40)
  private String name;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected User() {}

  public static User register(String email, String passwordHash, String name, Instant createdAt) {
    User user = new User();
    user.email = email;
    user.passwordHash = passwordHash;
    user.name = name;
    user.createdAt = createdAt;
    return user;
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getName() {
    return name;
  }
}
