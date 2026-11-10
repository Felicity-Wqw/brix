package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.brix.campus.common.BusinessException;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.model.Campus;
import com.brix.campus.model.User;
import com.brix.campus.model.dto.LoginDTO;
import com.brix.campus.model.dto.RegisterDTO;
import com.brix.campus.model.enums.RoleEnum;
import com.brix.campus.model.enums.UserStatusEnum;
import com.brix.campus.model.vo.LoginVO;
import com.brix.campus.model.vo.UserVO;
import com.brix.campus.security.JwtUtil;
import com.brix.campus.service.AuthService;
import com.brix.campus.service.SchoolService;
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
    private final SchoolService schoolService;

    public AuthServiceImpl(PasswordEncoder passwordEncoder, JwtUtil jwtUtil, UserService userService,
                           SchoolService schoolService) {
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.schoolService = schoolService;
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
        // 学校/校区两级联动：传了 campusId 就必须与 schoolId 匹配，防止前端被绕过后写入跨校脏数据
        if (dto.getCampusId() != null && !schoolService.campusBelongsTo(dto.getSchoolId(), dto.getCampusId())) {
            throw BusinessException.param("所选学校与校区不匹配，请重新选择");
        }
        user.setSchoolId(dto.getSchoolId());
        user.setCampusId(dto.getCampusId());
        // 名称快照以字典为准：带了 ID 就用库里的规范名称覆盖客户端字符串，
        // 否则同一个校区可能被写成「金鸡岭校区 / 金鸡岭 / 桂电金鸡岭」多个版本，统计时对不上
        if (dto.getSchoolId() != null) {
            String schoolName = schoolService.nameOf(dto.getSchoolId());
            if (schoolName != null) {
                user.setSchool(schoolName);
            }
        }
        if (dto.getCampusId() != null) {
            Campus campus = schoolService.getCampus(dto.getCampusId());
            if (campus != null) {
                user.setCampus(campus.getName());
            }
        }
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
