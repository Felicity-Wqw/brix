package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brix.campus.common.BusinessException;
import com.brix.campus.mapper.SchoolMapper;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.mapper.WantMapper;
import com.brix.campus.model.School;
import com.brix.campus.model.User;
import com.brix.campus.model.Want;
import com.brix.campus.model.dto.WantSaveDTO;
import com.brix.campus.model.vo.WantVO;
import com.brix.campus.service.WantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 求购墙实现。
 *
 * <p>状态机很简单：OPEN →（发帖人关闭）CLOSED。私信接单走现有聊天，
 * 不在求购帖上挂交易状态，避免与订单体系重复建模。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WantServiceImpl implements WantService {

    private final WantMapper wantMapper;
    private final UserMapper userMapper;
    private final SchoolMapper schoolMapper;

    @Override
    public WantVO publish(Long userId, WantSaveDTO dto) {
        Want want = new Want();
        want.setUserId(userId);
        want.setTitle(dto.getTitle().trim());
        want.setDescription(dto.getDescription() == null ? "" : dto.getDescription().trim());
        want.setBudget(dto.getBudget());
        want.setCampus(dto.getCampus() == null ? "" : dto.getCampus().trim());
        want.setSchoolId(dto.getSchoolId());
        want.setStatus("OPEN");
        wantMapper.insert(want);
        log.info("求购帖发布：id={} user={} title={}", want.getId(), userId, want.getTitle());
        return toVO(want);
    }

    @Override
    public IPage<WantVO> page(String status, long page, long size, Long viewerId) {
        Page<Want> p = wantMapper.selectPage(new Page<>(page, size),
                Wrappers.<Want>lambdaQuery()
                        .eq(Want::getStatus, "CLOSED".equals(status) ? "CLOSED" : "OPEN")
                        .orderByDesc(Want::getCreatedAt));

        // 批量补发帖人昵称/头像 + 学校名，避免 N+1
        List<Long> userIds = p.getRecords().stream().map(Want::getUserId).distinct().toList();
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        List<Long> schoolIds = p.getRecords().stream()
                .map(Want::getSchoolId).filter(Objects::nonNull).distinct().toList();
        Map<Long, School> schools = schoolIds.isEmpty() ? Map.of()
                : schoolMapper.selectBatchIds(schoolIds).stream()
                        .collect(Collectors.toMap(School::getId, Function.identity()));

        IPage<WantVO> result = p.convert(this::toVO);
        result.getRecords().forEach(vo -> {
            User user = users.get(vo.getUserId());
            vo.setNickname(user == null ? "同学" : user.getNickname());
            vo.setAvatar(user == null ? "" : user.getAvatar());
            vo.setMine(viewerId != null && Objects.equals(vo.getUserId(), viewerId));
            School school = vo.getSchoolId() == null ? null : schools.get(vo.getSchoolId());
            vo.setSchoolName(school == null ? "" : school.getName());
        });
        return result;
    }

    @Override
    public void close(Long userId, Long id) {
        Want want = wantMapper.selectById(id);
        if (want == null) {
            throw BusinessException.notFound("求购帖不存在");
        }
        if (!Objects.equals(want.getUserId(), userId)) {
            throw BusinessException.forbidden("只能关闭自己的求购帖");
        }
        // CAS：只有求购中能关闭，防重复点击
        int updated = wantMapper.update(null, Wrappers.<Want>lambdaUpdate()
                .eq(Want::getId, id)
                .eq(Want::getStatus, "OPEN")
                .set(Want::getStatus, "CLOSED"));
        if (updated == 0) {
            throw BusinessException.param("该求购帖已结束");
        }
    }

    private WantVO toVO(Want want) {
        WantVO vo = new WantVO();
        vo.setId(want.getId());
        vo.setUserId(want.getUserId());
        vo.setTitle(want.getTitle());
        vo.setDescription(want.getDescription());
        vo.setBudget(want.getBudget());
        vo.setCampus(want.getCampus());
        vo.setSchoolId(want.getSchoolId());
        vo.setStatus(want.getStatus());
        vo.setStatusDesc("CLOSED".equals(want.getStatus()) ? "已结束" : "求购中");
        vo.setCreatedAt(want.getCreatedAt());
        return vo;
    }
}
