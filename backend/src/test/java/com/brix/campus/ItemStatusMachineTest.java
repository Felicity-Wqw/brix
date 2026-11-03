package com.brix.campus;

import com.brix.campus.common.BusinessException;
import com.brix.campus.model.dto.ItemSaveDTO;
import com.brix.campus.model.dto.ItemStatusDTO;
import com.brix.campus.model.vo.ItemVO;
import com.brix.campus.service.ItemService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * 商品状态机测试（B05、B07、B08）。
 *
 * <p>启用前提：先初始化数据库；种子商品 id=1 为 {@code ON_SALE}、id=29 为 {@code SOLD}、卖家 id=4。</p>
 */
@Disabled("需先初始化数据库（db/schema.sql + db/data.sql）并配置 DB_USERNAME/DB_PASSWORD 后手工启用")
@SpringBootTest
class ItemStatusMachineTest {

    @Autowired
    private ItemService itemService;

    @Test
    @Transactional
    @DisplayName("编辑商品：状态保持上架")
    void update_resetsStatusToPending() {
        ItemSaveDTO dto = new ItemSaveDTO();
        dto.setTitle("iPad 9代 64G WiFi版 国行深空灰（降价）");
        dto.setDescription("降价出，配置不变，可当面验机。");
        dto.setCategoryId(1L);
        dto.setPrice(new BigDecimal("1180.00"));
        dto.setOriginalPrice(new BigDecimal("2499.00"));
        dto.setConditionLevel("LIKE_NEW");
        dto.setCampus("东校区");
        dto.setTradeType("BOTH");
        dto.setContact("微信 aizhe_digital");
        dto.setImages(new ArrayList<>());

        itemService.update(4L, 1L, dto);
        Assertions.assertEquals("ON_SALE", itemService.getEntity(1L).getStatus(),
                "编辑后状态必须保持 ON_SALE");
    }

    @Test
    @Transactional
    @DisplayName("已售出商品：编辑被拒绝")
    void update_soldItem_throwsException() {
        ItemSaveDTO dto = new ItemSaveDTO();
        dto.setTitle("测试标题");
        dto.setDescription("测试描述");
        dto.setCategoryId(1L);
        dto.setPrice(new BigDecimal("100.00"));
        dto.setConditionLevel("LIGHT_USE");
        dto.setCampus("东校区");
        dto.setTradeType("MEET");
        dto.setImages(new ArrayList<>());

        Assertions.assertThrows(BusinessException.class,
                () -> itemService.update(8L, 29L, dto),
                "已售出商品不可编辑");
    }

    @Test
    @Transactional
    @DisplayName("已下架商品重新上架：直接回到上架状态")
    void updateStatus_relist_goesToPending() {
        ItemStatusDTO dto = new ItemStatusDTO();
        dto.setAction("ON_SALE");
        ItemVO vo = itemService.updateStatus(6L, 22L, dto);
        Assertions.assertEquals("ON_SALE", vo.getStatus(),
                "重新上架应直接 ON_SALE");
    }

    @Test
    @Transactional
    @DisplayName("在售商品直接设为已售出：非法流转被拒绝")
    void updateStatus_illegalTransition_throwsException() {
        ItemStatusDTO dto = new ItemStatusDTO();
        dto.setAction("SOLD");
        Assertions.assertThrows(BusinessException.class,
                () -> itemService.updateStatus(4L, 1L, dto),
                "非法状态流转必须抛业务异常");
    }
}
