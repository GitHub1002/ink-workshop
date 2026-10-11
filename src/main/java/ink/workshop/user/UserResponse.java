package ink.workshop.user;

public record UserResponse(long id, String email, String name) {

  static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getName());
  }
}
