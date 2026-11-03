package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.brix.campus.common.BusinessException;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.model.User;
import com.brix.campus.model.dto.LoginDTO;
import com.brix.campus.model.dto.RegisterDTO;
import com.brix.campus.model.enums.RoleEnum;
import com.brix.campus.model.enums.UserStatusEnum;
import com.brix.campus.model.vo.LoginVO;
import com.brix.campus.model.vo.UserVO;
import com.brix.campus.security.JwtUtil;
import com.brix.campus.service.AuthService;
import com.brix.campus.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务实现。
 */
@Service
public class AuthServiceImpl extends ServiceImpl<UserMapper, User> implements AuthService {

    /** 初始信用分 */
    private static final int INITIAL_CREDIT_SCORE = 100;

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    public AuthServiceImpl(PasswordEncoder passwordEncoder, JwtUtil jwtUtil, UserService userService) {
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        String username = dto.getUsername().trim();
        Long exists = baseMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
        if (exists != null && exists > 0) {
            throw BusinessException.param("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname().trim());
        user.setAvatar("");
        user.setSchool(dto.getSchool() == null ? null : dto.getSchool().trim());
        user.setCampus(dto.getCampus() == null ? null : dto.getCampus().trim());
        user.setPhone(dto.getPhone() == null || dto.getPhone().isBlank() ? null : dto.getPhone().trim());
        user.setBio("");
        user.setRole(RoleEnum.USER.getCode());
        user.setStatus(UserStatusEnum.NORMAL.getCode());
        user.setCreditScore(INITIAL_CREDIT_SCORE);
        save(user);

        return buildLoginVO(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = loadForLogin(dto);
        return buildLoginVO(user);
    }

    /**
     * 账号密码校验（不区分账号是否存在，统一提示）。
     *
     * @param dto 登录入参
     * @return 校验通过的用户实体
     */
    private User loadForLogin(LoginDTO dto) {
        User user = baseMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, dto.getUsername().trim())
                .last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw BusinessException.param("用户名或密码错误");
        }
        if (UserStatusEnum.DISABLED.getCode().equals(user.getStatus())) {
            throw BusinessException.param("账号已被禁用");
        }
        return user;
    }

    /**
     * 组装登录返回体：token + 用户信息。
     *
     * @param user 用户实体
     * @return 登录信息
     */
    private LoginVO buildLoginVO(User user) {
        String token = jwtUtil.generate(user.getId(), user.getRole(), user.getNickname());
        UserVO userVO = userService.toVO(user);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(userVO);
        return vo;
    }
}
