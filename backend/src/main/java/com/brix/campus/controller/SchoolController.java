package com.brix.campus.controller;

import com.brix.campus.common.R;
import com.brix.campus.model.vo.SchoolVO;
import com.brix.campus.service.SchoolService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开学校 / 校区接口（无需登录）。
 *
 * <p>前端用它替换原先硬编码的校区常量，做「学校 → 校区」两级联动；
 * 同一份数据也是后台按学校统计交易量的字典来源。</p>
 */
@RestController
@RequestMapping("/schools")
@Validated
public class SchoolController {

    private final SchoolService schoolService;

    public SchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    /**
     * 学校列表（含各自校区）。
     *
     * @return 学校树
     */
    @GetMapping
    public R<List<SchoolVO>> list() {
        return R.ok(schoolService.listForFront());
    }
}
