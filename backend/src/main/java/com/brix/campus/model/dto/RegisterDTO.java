package com.brix.campus.model.dto;

import com.brix.campus.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 注册入参（A01）。
 */
@Data
public class RegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录名 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需为 3-20 个字符")
    private String username;

    /** 密码（明文传输，落库为 BCrypt） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度不能少于 6 位")
    private String password;

    /** 昵称 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 20, message = "昵称不能超过 20 个字")
    private String nickname;

    /** 学校 */
    @Size(max = 100, message = "学校名称过长")
    private String school;

    /** 校区 */
    @Size(max = 50, message = "校区名称过长")
    private String campus;

    /** 所属学校 ID（两级联动，可空） */
    private Long schoolId;

    /** 所属校区 ID（两级联动，可空） */
    private Long campusId;

    /** 手机号：空 或 11 位大陆手机号（号段 1[3-9]） */
    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    private String phone;
}
