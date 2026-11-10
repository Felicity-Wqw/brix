package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.brix.campus.mapper.CampusMapper;
import com.brix.campus.mapper.SchoolMapper;
import com.brix.campus.model.Campus;
import com.brix.campus.model.School;
import com.brix.campus.model.vo.CampusVO;
import com.brix.campus.model.vo.SchoolVO;
import com.brix.campus.service.SchoolService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学校 / 校区服务实现。
 *
 * <p>数据量固定且很小（13 校 20 校区），不做缓存：一次列表请求固定 2 条 SQL，
 * 前端拿到后自行缓存，避免引入额外的一致性维护成本。</p>
 */
@Service
public class SchoolServiceImpl implements SchoolService {

    /** 启用标记 */
    private static final int ENABLED = 1;

    private final SchoolMapper schoolMapper;
    private final CampusMapper campusMapper;

    public SchoolServiceImpl(SchoolMapper schoolMapper, CampusMapper campusMapper) {
        this.schoolMapper = schoolMapper;
        this.campusMapper = campusMapper;
    }

    @Override
    public List<SchoolVO> listForFront() {
        List<School> schools = schoolMapper.selectList(Wrappers.<School>lambdaQuery()
                .eq(School::getEnabled, ENABLED)
                .orderByAsc(School::getSort)
                .orderByAsc(School::getId));
        if (schools.isEmpty()) {
            return new ArrayList<>();
        }

        List<Campus> campuses = campusMapper.selectList(Wrappers.<Campus>lambdaQuery()
                .eq(Campus::getEnabled, ENABLED)
                .orderByAsc(Campus::getSort)
                .orderByAsc(Campus::getId));

        Map<Long, List<CampusVO>> group = new HashMap<>(schools.size() * 2);
        for (Campus campus : campuses) {
            group.computeIfAbsent(campus.getSchoolId(), key -> new ArrayList<>()).add(toCampusVO(campus));
        }

        List<SchoolVO> result = new ArrayList<>(schools.size());
        for (School school : schools) {
            SchoolVO vo = new SchoolVO();
            vo.setId(school.getId());
            vo.setName(school.getName());
            vo.setShortName(school.getShortName());
            vo.setLevel(school.getLevel());
            vo.setNature(school.getNature());
            vo.setDistrict(school.getDistrict());
            vo.setCampuses(group.getOrDefault(school.getId(), new ArrayList<>()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public String nameOf(Long schoolId) {
        if (schoolId == null) {
            return null;
        }
        School school = schoolMapper.selectById(schoolId);
        return school == null ? null : school.getName();
    }

    @Override
    public java.util.Map<Long, String> namesOf(java.util.Collection<Long> schoolIds) {
        if (schoolIds == null || schoolIds.isEmpty()) {
            return new HashMap<>(0);
        }
        List<School> schools = schoolMapper.selectByIds(schoolIds);
        Map<Long, String> result = new HashMap<>(schools.size() * 2);
        for (School school : schools) {
            result.put(school.getId(), school.getName());
        }
        return result;
    }

    @Override
    public Campus getCampus(Long campusId) {
        return campusId == null ? null : campusMapper.selectById(campusId);
    }

    @Override
    public boolean campusBelongsTo(Long schoolId, Long campusId) {
        if (schoolId == null || campusId == null) {
            return false;
        }
        Campus campus = campusMapper.selectById(campusId);
        return campus != null && schoolId.equals(campus.getSchoolId());
    }

    /**
     * 校区实体转出参。
     *
     * @param campus 实体
     * @return 出参
     */
    private CampusVO toCampusVO(Campus campus) {
        CampusVO vo = new CampusVO();
        vo.setId(campus.getId());
        vo.setSchoolId(campus.getSchoolId());
        vo.setName(campus.getName());
        vo.setAddress(campus.getAddress());
        vo.setDistrict(campus.getDistrict());
        vo.setLat(campus.getLat());
        vo.setLng(campus.getLng());
        vo.setIsMain(campus.getIsMain());
        return vo;
    }
}
