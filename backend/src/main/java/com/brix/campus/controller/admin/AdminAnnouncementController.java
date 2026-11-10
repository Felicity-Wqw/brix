package com.brix.campus.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.brix.campus.common.BusinessException;
import com.brix.campus.common.R;
import com.brix.campus.mapper.AnnouncementMapper;
import com.brix.campus.model.Announcement;
import com.brix.campus.model.dto.AnnouncementDTO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台公告管理（运营端）：首页公告轮播文字的增删改查。
 */
@RestController
@RequestMapping("/admin/announcements")
@Validated
public class AdminAnnouncementController {

    private final AnnouncementMapper announcementMapper;

    public AdminAnnouncementController(AnnouncementMapper announcementMapper) {
        this.announcementMapper = announcementMapper;
    }

    /** 全量公告（含下架），按 sort 升序 */
    @GetMapping
    public R<List<Announcement>> list() {
        return R.ok(announcementMapper.selectList(Wrappers.<Announcement>lambdaQuery()
                .orderByAsc(Announcement::getSort)
                .orderByAsc(Announcement::getId)));
    }

    /** 新增公告 */
    @PostMapping
    public R<Void> create(@RequestBody @Valid AnnouncementDTO dto) {
        announcementMapper.insert(apply(new Announcement(), dto));
        return R.ok(null, "公告已新增");
    }

    /** 修改公告 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody @Valid AnnouncementDTO dto) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw BusinessException.notFound("公告不存在");
        }
        announcementMapper.updateById(apply(announcement, dto));
        return R.ok(null, "公告已更新");
    }

    /** 删除公告（逻辑删除） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        announcementMapper.deleteById(id);
        return R.ok(null, "公告已删除");
    }

    private Announcement apply(Announcement announcement, AnnouncementDTO dto) {
        announcement.setTitle(dto.getTitle().trim());
        announcement.setContent(dto.getContent().trim());
        announcement.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        announcement.setSort(dto.getSort() == null ? 0 : dto.getSort());
        return announcement;
    }
}
