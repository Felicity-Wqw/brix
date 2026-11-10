package com.brix.campus.controller.admin;

import com.brix.campus.common.BusinessException;
import com.brix.campus.common.R;
import com.brix.campus.model.dto.LoginDTO;
import com.brix.campus.model.enums.RoleEnum;
import com.brix.campus.model.vo.LoginVO;
import com.brix.campus.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台认证（K01）。
 *
 * <p>与前台共用同一套账号体系与 JWT，不另建管理员表；区别只在于登录后必须校验角色：
 * {@code USER} 即便密码正确也返回「无后台权限」，避免普通账号误入后台。</p>
 *
 * <p>注意 URL 层已放行 {@code /admin/login}（否则拿不到 token 就没法校验角色），
 * 真正的门禁在下面这行角色判断 + 其余 {@code /admin/**} 的 hasRole 规则。</p>
 */
@RestController
@RequestMapping("/admin")
@Validated
public class AdminAuthController {

    private final AuthService authService;

    public AdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 后台登录。
     *
     * @param dto 账号密码
     * @return token + 用户信息（role 为 REVIEWER / OPERATOR）
     */
    @PostMapping("/login")
    public R<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        LoginVO vo = authService.login(dto);
        if (vo.getUser() == null || !RoleEnum.isAdmin(vo.getUser().getRole())) {
            throw BusinessException.param("无后台权限");
        }
        return R.ok(vo, "登录成功");
    }
}
