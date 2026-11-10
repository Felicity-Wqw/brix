package com.brix.campus.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.common.R;
import com.brix.campus.model.dto.UserStatusDTO;
import com.brix.campus.model.query.UserQuery;
import com.brix.campus.model.vo.UserVO;
import com.brix.campus.security.SecurityUtils;
import com.brix.campus.service.UserService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理接口（仅 OPERATOR）。
 */
@RestController
@RequestMapping("/admin/users")
@Validated
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户列表。
     *
     * @param query 关键词 / 角色 / 状态 / 学校 / 分页
     * @return 分页结果
     */
    @GetMapping
    public R<IPage<UserVO>> list(@Valid UserQuery query) {
        return R.ok(userService.pageForAdmin(query));
    }

    /**
     * 封禁 / 解封。不能操作自己，也不能操作其它后台账号（见 Service 注释）。
     *
     * @param id  用户 ID
     * @param dto 目标状态与原因
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody @Valid UserStatusDTO dto) {
        userService.updateStatus(SecurityUtils.currentUserId(), id, dto);
        return R.ok(null, "已更新用户状态");
    }
}
