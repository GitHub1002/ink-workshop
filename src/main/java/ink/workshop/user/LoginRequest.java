package ink.workshop.user;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "必须填写邮箱") String email,
    @NotBlank(message = "必须填写密码") String password) {}
