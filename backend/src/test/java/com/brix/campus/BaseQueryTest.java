package com.brix.campus;

import com.brix.campus.model.query.BaseQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分页基类别名兼容测试：page/size 必须等价于 currentPage/pageSize。
 */
class BaseQueryTest {

    @Test
    void pageAliasWritesToCurrentPage() {
        BaseQuery q = new BaseQuery();
        q.setPage(3);
        assertEquals(3, q.getCurrentPage());
        assertEquals(3, q.getPage());
        assertEquals((3L - 1) * 10, q.offset());
    }

    @Test
    void sizeAliasWritesToPageSize() {
        BaseQuery q = new BaseQuery();
        q.setSize(20);
        assertEquals(20, q.getPageSize());
        assertEquals(20, q.getSize());
    }

    @Test
    void nativeFieldsStillPreferredWhenBothSet() {
        BaseQuery q = new BaseQuery();
        q.setPage(2);
        q.setCurrentPage(5);
        assertEquals(5, q.getCurrentPage());
        q.setSize(30);
        q.setPageSize(40);
        assertEquals(40, q.getPageSize());
    }

    @Test
    void defaultsRemainUnchanged() {
        BaseQuery q = new BaseQuery();
        assertEquals(1, q.getCurrentPage());
        assertEquals(10, q.getPageSize());
        assertEquals(0L, q.offset());
    }
}
