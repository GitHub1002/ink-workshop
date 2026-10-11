package ink.workshop.user;

public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("邮箱或密码不正确");
  }
}
