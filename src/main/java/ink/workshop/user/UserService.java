package ink.workshop.user;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private static final Duration SESSION_LIFETIME = Duration.ofDays(7);
  private static final int TOKEN_BYTES = 32;

  private final UserRepository userRepository;
  private final SessionRepository sessionRepository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  public UserService(
      UserRepository userRepository,
      SessionRepository sessionRepository,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.userRepository = userRepository;
    this.sessionRepository = sessionRepository;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Transactional
  public UserResponse register(RegisterRequest request) {
    String email = normalizeEmail(request.email());
    if (userRepository.existsByEmail(email)) {
      throw new EmailAlreadyUsedException();
    }
    User user = User.register(
        email,
        passwordEncoder.encode(request.password()),
        request.name().trim(),
        clock.instant());
    return UserResponse.from(userRepository.save(user));
  }

  @Transactional
  public SessionResponse login(LoginRequest request) {
    String email = normalizeEmail(request.email());
    User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    Instant now = clock.instant();
    String token = newToken();
    sessionRepository.save(Session.issue(user.getId(), token, now.plus(SESSION_LIFETIME), now));
    return new SessionResponse(token);
  }

  @Transactional(readOnly = true)
  public Optional<AuthenticatedUser> authenticate(String token) {
    return sessionRepository.findByToken(token)
        .filter(session -> session.getExpiresAt().isAfter(clock.instant()))
        .map(session -> new AuthenticatedUser(session.getUserId()));
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String newToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
