package com.brix.campus.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.model.Order;
import com.brix.campus.model.dto.CancelOrderDTO;
import com.brix.campus.model.dto.OrderCreateDTO;
import com.brix.campus.model.dto.PayDTO;
import com.brix.campus.model.query.OrderQuery;
import com.brix.campus.model.vo.OrderDetailVO;
import com.brix.campus.model.vo.OrderVO;

/**
 * 订单服务（E01-E10）。
 */
public interface OrderService {

    /**
     * 下单：生成 {@code UNPAID} 订单，商品不锁定（PRD 7.3）。
     *
     * @param buyerId 买家 ID
     * @param dto     入参
     * @return 订单
     */
    OrderVO create(Long buyerId, OrderCreateDTO dto);

    /**
     * 收银台模拟支付：抢占商品 + 订单流转 + 关闭同商品其它待付款单，同一事务。
     *
     * @param userId  操作人（买家）
     * @param orderId 订单 ID
     * @param dto     支付方式
     * @return 支付后的订单
     */
    OrderVO pay(Long userId, Long orderId, PayDTO dto);

    /**
     * 卖家发货：{@code TO_SHIP → TO_RECEIVE}。
     *
     * @param userId  操作人（卖家）
     * @param orderId 订单 ID
     * @return 订单
     */
    OrderVO ship(Long userId, Long orderId);

    /**
     * 买家确认完成：邮寄单 {@code TO_RECEIVE → DONE}，面交单 {@code TO_MEET → DONE}。
     *
     * @param userId  操作人（买家）
     * @param orderId 订单 ID
     * @return 订单
     */
    OrderVO confirm(Long userId, Long orderId);

    /**
     * 取消订单：取消后若商品为 {@code SOLD} 则回滚为 {@code ON_SALE}。
     *
     * @param userId  操作人（买家或卖家）
     * @param orderId 订单 ID
     * @param dto     取消原因
     * @return 订单
     */
    OrderVO cancel(Long userId, Long orderId, CancelOrderDTO dto);

    /**
     * 我买到的订单。
     *
     * @param buyerId 买家 ID
     * @param query   查询条件
     * @return 分页结果
     */
    IPage<OrderVO> pageBought(Long buyerId, OrderQuery query);

    /**
     * 我卖出的订单。
     *
     * @param sellerId 卖家 ID
     * @param query    查询条件
     * @return 分页结果
     */
    IPage<OrderVO> pageSold(Long sellerId, OrderQuery query);

    /**
     * 后台订单列表（仅 OPERATOR）：全平台订单，只读排查用。
     *
     * <p>刻意不提供「后台改订单状态」的接口——订单状态只能由买卖双方的
     * 支付 / 发货 / 确认 / 取消动作驱动，运营介入会破坏资金链路的一致性。</p>
     *
     * @param query 状态 / 关键词 / 买卖家 / 分页
     * @return 分页结果
     */
    IPage<OrderVO> pageForAdmin(OrderQuery query);

    /**
     * 订单详情：仅买卖双方与后台角色可见。
     *
     * @param userId  当前用户 ID
     * @param role    当前用户角色码
     * @param orderId 订单 ID
     * @return 详情
     */
    OrderDetailVO detail(Long userId, String role, Long orderId);

    /**
     * 关闭超时未支付订单（定时任务调用）。
     *
     * @param timeoutMinutes 超时分钟数
     * @return 关闭条数
     */
    int closeTimeoutOrders(int timeoutMinutes);

    /**
     * 取订单实体。
     *
     * @param orderId 订单 ID
     * @return 实体，不存在返回 null
     */
    Order getEntity(Long orderId);
}
