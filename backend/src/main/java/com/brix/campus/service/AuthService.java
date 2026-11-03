package com.brix.campus.service;

import com.brix.campus.model.dto.LoginDTO;
import com.brix.campus.model.dto.RegisterDTO;
import com.brix.campus.model.vo.LoginVO;

/**
 * 认证服务：注册、登录。
 */
public interface AuthService {

    /**
     * 注册（注册即登录，返回 token）。
     *
     * @param dto 注册入参
     * @return 登录信息
     */
    LoginVO register(RegisterDTO dto);

    /**
     * 登录。
     *
     * @param dto 登录入参
     * @return 登录信息
     */
    LoginVO login(LoginDTO dto);
}
