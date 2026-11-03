package com.brix.campus.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.model.User;
import com.brix.campus.model.dto.ChangePasswordDTO;
import com.brix.campus.model.dto.UpdateProfileDTO;
import com.brix.campus.model.vo.UploadVO;
import com.brix.campus.model.vo.UserAnalysisVO;
import com.brix.campus.model.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户服务：个人资料、密码、头像。
 */
public interface UserService {

    /**
     * 按 ID 取用户信息（不含个人数据统计）。
     *
     * <p>方法名不用 {@code getById}，避免与 MyBatis-Plus {@code IRepository#getById(Serializable)} 重载冲突。</p>
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    UserVO getUserInfo(Long userId);

    /**
     * 取当前登录用户信息（含发布数 / 在售数 / 卖出数）。
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    UserVO getMe(Long userId);

    /**
     * 修改个人资料，只允许改本人。
     *
     * @param userId 用户 ID（取自 token）
     * @param dto    入参
     * @return 更新后信息
     */
    UserVO updateProfile(Long userId, UpdateProfileDTO dto);

    /**
     * 修改密码（校验旧密码）。
     *
     * @param userId 用户 ID
     * @param dto    入参
     */
    void changePassword(Long userId, ChangePasswordDTO dto);

    /**
     * 更新头像。
     *
     * @param userId 用户 ID
     * @param file   头像文件
     * @return 上传结果
     */
    UploadVO updateAvatar(Long userId, MultipartFile file);

    /**
     * 个人买卖可视化分析（G05）：买入/卖出两维度的汇总、分类占比与月度趋势。
     *
     * @param userId 用户 ID（取自 token）
     * @return 分析数据
     */
    UserAnalysisVO getMyAnalysis(Long userId);

    /**
     * 取用户实体，不存在返回 null。
     *
     * @param userId 用户 ID
     * @return 实体
     */
    User getEntity(Long userId);

    /**
     * 组装 UserVO（供其它服务复用，避免重复映射代码）。
     *
     * @param user 实体
     * @return VO
     */
    UserVO toVO(User user);
}
