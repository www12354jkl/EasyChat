package com.faust.easychat.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @Email(message = "邮箱格式不正确")
    @NotBlank(message = "邮箱不能为空")
    private String email;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 20, message = "昵称长度不能超过20")
    private String nickname;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度须在8-20之间")
    private String password;
    
    @NotBlank(message = "验证码不能为空")
    private String code;
}
