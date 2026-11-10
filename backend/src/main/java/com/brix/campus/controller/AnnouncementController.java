package com.brix.campus.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.brix.campus.common.R;
import com.brix.campus.mapper.AnnouncementMapper;
import com.brix.campus.model.Announcement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台公告（用户侧）：首页公告轮播取数，游客可读。
 */
@RestController
@RequestMapping("/announcements")
public class AnnouncementController {

    private final AnnouncementMapper announcementMapper;

    public AnnouncementController(AnnouncementMapper announcementMapper) {
        this.announcementMapper = announcementMapper;
    }

    /** 已上架公告，按 sort 升序 */
    @GetMapping
    public R<List<Announcement>> listEnabled() {
        return R.ok(announcementMapper.selectList(Wrappers.<Announcement>lambdaQuery()
                .eq(Announcement::getEnabled, 1)
                .orderByAsc(Announcement::getSort)
                .orderByAsc(Announcement::getId)));
    }
}
