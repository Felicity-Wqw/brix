package com.brix.campus.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.common.R;
import com.brix.campus.model.query.OrderQuery;
import com.brix.campus.model.vo.OrderVO;
import com.brix.campus.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台订单查询接口（仅 OPERATOR，只读）。
 *
 * <p>只提供查询不提供改状态：订单状态迁移必须由买卖双方的真实动作驱动，
 * 后台直接改会绕过托管结算与超时规则，资金链路就对不上了。</p>
 */
@RestController
@RequestMapping("/admin/orders")
@Validated
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 全平台订单列表。
     *
     * @param query 状态 / 关键词（订单号、商品标题）/ 买卖家 / 分页
     * @return 分页结果
     */
    @GetMapping
    public R<IPage<OrderVO>> list(@Valid OrderQuery query) {
        return R.ok(orderService.pageForAdmin(query));
    }
}
