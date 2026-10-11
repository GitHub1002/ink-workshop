package ink.workshop.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "必须填写邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 120, message = "邮箱最长 120 个字符")
    String email,
    @NotBlank(message = "必须填写密码")
    String password,
    @NotBlank(message = "必须填写昵称")
    @Size(max = 40, message = "昵称最长 40 个字符")
    String name) {}
