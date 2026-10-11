package ink.workshop.user;

public class EmailAlreadyUsedException extends RuntimeException {

  public EmailAlreadyUsedException() {
    super("邮箱已注册");
  }
}
