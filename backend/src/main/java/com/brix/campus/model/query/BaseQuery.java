package com.brix.campus.model.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询基类：{@code currentPage} 从 1 开始，{@code pageSize} 默认 10、最大 50。
 *
 * <p>查询条件对象与实体严格分离，<b>绝不复用实体当查询对象</b>。</p>
 *
 * <p>兼容别名：{@code page} 与 {@code size} 是社区常见分页命名，直接映射到
 * {@code currentPage} / {@code pageSize}，避免调用方拼错参数名时分页被静默忽略、
 * 拿到看似正常实则错误的默认页。别名写入后同样受 {@code @Min} / {@code @Max} 校验约束，
 * 行为与原生字段完全一致。</p>
 */
@Data
public class BaseQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页，从 1 开始 */
    @Min(value = 1, message = "页码最小为 1")
    private Integer currentPage = 1;

    /** 每页条数，默认 10，最大 50 */
    @Max(value = 50, message = "每页最多 50 条")
    private Integer pageSize = 10;

    /**
     * 计算偏移量（供 XML 手写分页 SQL 使用）。
     *
     * @return 偏移行数
     */
    public long offset() {
        int page = currentPage == null ? 1 : currentPage;
        int size = pageSize == null ? 10 : pageSize;
        return (long) (page - 1) * size;
    }

    /**
     * 别名读取：{@code page} 即 {@code currentPage}。
     *
     * @return 当前页
     */
    public Integer getPage() {
        return currentPage;
    }

    /**
     * 别名写入：{@code page} 映射到 {@code currentPage}。
     *
     * @param page 页码
     */
    public void setPage(Integer page) {
        this.currentPage = page;
    }

    /**
     * 别名读取：{@code size} 即 {@code pageSize}。
     *
     * @return 每页条数
     */
    public Integer getSize() {
        return pageSize;
    }

    /**
     * 别名写入：{@code size} 映射到 {@code pageSize}。
     *
     * @param size 每页条数
     */
    public void setSize(Integer size) {
        this.pageSize = size;
    }
}
