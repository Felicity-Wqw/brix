package com.brix.campus.service;

import com.brix.campus.model.Campus;
import com.brix.campus.model.vo.SchoolVO;

import java.util.List;

/**
 * 学校 / 校区服务。
 *
 * <p>学校与校区是商品、用户、订单的归属维度，同时是后台统计的分组依据
 * （按学校、按校区、按区县、按办学层次），因此这里只做只读查询，
 * 变更入口留给后续的管理后台。</p>
 */
public interface SchoolService {

    /**
     * 前台学校列表（含校区），仅返回启用项，学校按 sort 升序、校区按 sort 升序。
     *
     * @return 学校树
     */
    List<SchoolVO> listForFront();

    /**
     * 取学校名称。
     *
     * @param schoolId 学校 ID，可空
     * @return 名称；不存在或为空返回 null
     */
    String nameOf(Long schoolId);

    /**
     * 批量取学校名称（列表页补名用，避免 N+1）。
     *
     * @param schoolIds 学校 ID 集合，可空
     * @return ID → 名称；空集合返回空 Map
     */
    java.util.Map<Long, String> namesOf(java.util.Collection<Long> schoolIds);

    /**
     * 取校区实体。
     *
     * @param campusId 校区 ID，可空
     * @return 校区；不存在返回 null
     */
    Campus getCampus(Long campusId);

    /**
     * 校验校区是否属于指定学校。
     *
     * <p>前端两级联动可被绕过，落库前必须用本方法校验，防止出现「A 校 + B 校校区」的脏组合。</p>
     *
     * @param schoolId 学校 ID
     * @param campusId 校区 ID
     * @return 是否匹配
     */
    boolean campusBelongsTo(Long schoolId, Long campusId);
}
