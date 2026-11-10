package com.brix.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 小法庭投票入参。
 */
@Data
public class JuryVoteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 立场：BUYER 支持买家 / SELLER 支持卖家 */
    @NotBlank(message = "投票立场不能为空")
    private String vote;
}
