package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录出参（A02）：token + 用户信息。
 */
@Data
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JWT */
    private String token;

    /** 用户信息 */
    private UserVO user;
}
