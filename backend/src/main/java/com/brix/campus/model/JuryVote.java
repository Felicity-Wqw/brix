package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 小法庭陪审投票（物理表 {@code t_jury_vote}）。
 *
 * <p>每人每案一票（唯一索引 {@code uk_vote_case_user} 兜底），投后不可改；
 * 本案买卖双方不可投票（Service 层保证）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JuryVote extends BaseEntity {

    /** 案件 ID */
    @TableField("case_id")
    private Long caseId;

    /** 陪审用户 ID */
    @TableField("user_id")
    private Long userId;

    /** 立场：BUYER 支持买家 / SELLER 支持卖家 */
    private String vote;
}
