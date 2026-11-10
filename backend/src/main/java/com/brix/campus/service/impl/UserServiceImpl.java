package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.brix.campus.common.BusinessException;
import com.brix.campus.mapper.ItemMapper;
import com.brix.campus.mapper.OrderMapper;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.model.Campus;
import com.brix.campus.model.Item;
import com.brix.campus.model.Order;
import com.brix.campus.model.User;
import com.brix.campus.model.dto.ChangePasswordDTO;
import com.brix.campus.model.dto.UpdateProfileDTO;
import com.brix.campus.model.dto.UserStatusDTO;
import com.brix.campus.model.query.UserQuery;
import com.brix.campus.model.enums.ItemStatusEnum;
import com.brix.campus.model.enums.OrderStatusEnum;
import com.brix.campus.model.enums.RoleEnum;
import com.brix.campus.model.enums.UserStatusEnum;
import com.brix.campus.model.vo.UploadVO;
import com.brix.campus.model.vo.UserAnalysisVO;
import com.brix.campus.model.vo.UserStatsVO;
import com.brix.campus.model.vo.UserVO;
import com.brix.campus.service.FileStorageService;
import com.brix.campus.service.SchoolService;
import com.brix.campus.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * 用户服务实现。
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final ItemMapper itemMapper;
    private final OrderMapper orderMapper;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final SchoolService schoolService;

    public UserServiceImpl(ItemMapper itemMapper,
                           OrderMapper orderMapper,
                           PasswordEncoder passwordEncoder,
                           FileStorageService fileStorageService,
                           SchoolService schoolService) {
        this.itemMapper = itemMapper;
        this.orderMapper = orderMapper;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.schoolService = schoolService;
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        User user = getEntity(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return toVO(user);
    }

    @Override
    public UserVO getMe(Long userId) {
        User user = getEntity(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        UserVO vo = toVO(user);
        vo.setStats(buildStats(userId));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = getEntity(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        user.setNickname(dto.getNickname().trim());
        user.setSchool(trimToNull(dto.getSchool()));
        user.setCampus(trimToNull(dto.getCampus()));
        // 学校/校区联动校验：campusId 必须属于 schoolId
        if (dto.getCampusId() != null && !schoolService.campusBelongsTo(dto.getSchoolId(), dto.getCampusId())) {
            throw BusinessException.param("所选学校与校区不匹配，请重新选择");
        }
        user.setSchoolId(dto.getSchoolId());
        user.setCampusId(dto.getCampusId());
        // 名称快照以字典为准，避免客户端传入「金鸡岭 / 金鸡岭校区」这类不统一写法
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
        user.setPhone(trimToNull(dto.getPhone()));
        user.setBio(dto.getBio() == null ? "" : dto.getBio().trim());
        updateById(user);
        return toVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User user = getEntity(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw BusinessException.param("原密码不正确");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadVO updateAvatar(Long userId, MultipartFile file) {
        User user = getEntity(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        UploadVO upload = fileStorageService.store(file, "avatar");
        user.setAvatar(upload.getUrl());
        updateById(user);
        return upload;
    }

    @Override
    public User getEntity(Long userId) {
        if (userId == null) {
            return null;
        }
        return getById(userId);
    }

    @Override
    public UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setSchool(user.getSchool());
        vo.setCampus(user.getCampus());
        vo.setSchoolId(user.getSchoolId());
        vo.setCampusId(user.getCampusId());
        vo.setPhone(user.getPhone());
        vo.setBio(user.getBio());
        vo.setRole(user.getRole());
        vo.setRoleDesc(RoleEnum.descOf(user.getRole()));
        vo.setStatus(user.getStatus());
        vo.setStatusDesc(UserStatusEnum.descOf(user.getStatus()));
        vo.setCreditScore(user.getCreditScore());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    @Override
    public UserAnalysisVO getMyAnalysis(Long userId) {
        UserAnalysisVO vo = new UserAnalysisVO();
        vo.setBuy(buildAnalysisSide(userId, "BUYER"));
        vo.setSell(buildAnalysisSide(userId, "SELLER"));
        return vo;
    }

    // ==================== 后台运营 ====================

    @Override
    public IPage<UserVO> pageForAdmin(UserQuery query) {
        Page<User> page = new Page<>(query.getCurrentPage() == null ? 1 : query.getCurrentPage(),
                query.getPageSize() == null ? 10 : query.getPageSize());
        LambdaQueryWrapper<User> wrapper = Wrappers.lambdaQuery();
        if (query.getRole() != null && !query.getRole().isBlank()) {
            wrapper.eq(User::getRole, query.getRole());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(User::getStatus, query.getStatus());
        }
        if (query.getSchoolId() != null) {
            wrapper.eq(User::getSchoolId, query.getSchoolId());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getPhone, keyword));
        }
        // 后台账号置顶：运营最常查的是「有没有异常注册」，普通用户按注册时间倒序
        wrapper.orderByAsc(User::getRole).orderByDesc(User::getCreatedAt);
        return baseMapper.selectPage(page, wrapper).convert(this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long operatorId, Long userId, UserStatusDTO dto) {
        if (operatorId.equals(userId)) {
            throw BusinessException.param("不能修改自己的账号状态");
        }
        User user = getById(userId);
        if (user == null) {
            throw BusinessException.param("用户不存在");
        }
        if (RoleEnum.isAdmin(user.getRole())) {
            throw BusinessException.param("后台账号不受用户管理约束，请由数据库或超级管理员处理");
        }
        if (!UserStatusEnum.isValid(dto.getStatus())) {
            throw BusinessException.param("目标状态不合法");
        }
        if (dto.getStatus().equals(user.getStatus())) {
            throw BusinessException.param("用户已处于该状态");
        }

        user.setStatus(dto.getStatus());
        if (UserStatusEnum.DISABLED.getCode().equals(dto.getStatus())) {
            user.setDisabledBy(operatorId);
            user.setDisabledAt(LocalDateTime.now());
        } else {
            // 解封后清空封禁痕迹，避免前端展示「曾于某时被封」
            user.setDisabledBy(null);
            user.setDisabledAt(null);
        }
        updateById(user);
    }
    /**
     * 组装买入/卖出单一维度的分析数据。
     *
     * @param userId 用户 ID
     * @param role   BUYER / SELLER
     * @return 维度统计
     */
    private UserAnalysisVO.Side buildAnalysisSide(Long userId, String role) {
        UserAnalysisVO.Side side = new UserAnalysisVO.Side();
        side.setSummary(orderMapper.selectDoneOrderSummary(userId, role));
        side.setCategories(orderMapper.selectDoneOrderCategoryStats(userId, role));
        side.setMonthly(orderMapper.selectDoneOrderMonthlyStats(userId, role));
        return side;
    }

    /**
     * 组装个人数据统计。
     *
     * @param userId 用户 ID
     * @return 统计对象
     */
    private UserStatsVO buildStats(Long userId) {
        UserStatsVO stats = new UserStatsVO();
        stats.setItemCount(itemMapper.selectCount(Wrappers.<Item>lambdaQuery()
                .eq(Item::getSellerId, userId)));
        stats.setOnSaleCount(itemMapper.selectCount(Wrappers.<Item>lambdaQuery()
                .eq(Item::getSellerId, userId)
                .eq(Item::getStatus, ItemStatusEnum.ON_SALE.getCode())));
        stats.setSoldCount(orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getSellerId, userId)
                .eq(Order::getStatus, OrderStatusEnum.DONE.getCode())));
        return stats;
    }

    /**
     * 空串转 null，便于数据库存 NULL 而不是空字符串。
     *
     * @param value 原值
     * @return trim 后的值或 null
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
