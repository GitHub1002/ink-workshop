package ink.workshop.user;

public class PasswordConstraintException extends RuntimeException {

  public PasswordConstraintException() {
    super("密码需为 8 到 72 个字节");
  }
}
