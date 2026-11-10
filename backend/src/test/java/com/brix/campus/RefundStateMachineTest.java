package com.brix.campus;

import com.brix.campus.common.BusinessException;
import com.brix.campus.mapper.ChatMessageMapper;
import com.brix.campus.mapper.JuryCaseMapper;
import com.brix.campus.mapper.OrderMapper;
import com.brix.campus.mapper.RefundMapper;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.model.ChatMessage;
import com.brix.campus.model.JuryCase;
import com.brix.campus.model.Order;
import com.brix.campus.model.Refund;
import com.brix.campus.model.User;
import com.brix.campus.model.dto.RefundApplyDTO;
import com.brix.campus.model.dto.RefundHandleDTO;
import com.brix.campus.model.enums.OrderStatusEnum;
import com.brix.campus.model.enums.RefundStatusEnum;
import com.brix.campus.model.vo.RefundVO;
import com.brix.campus.service.impl.RefundServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 售后状态机规则单元测试（不依赖数据库）。
 *
 * <p>覆盖点：申请资格（归属 / 状态 / 15 天窗口 / 面交仅退款 / 重复申请拦截）、
 * 撤销与重提窗口、卖家处理动作合法性、退款收尾状态白名单。</p>
 */
class RefundStateMachineTest {

    private static final Long BUYER = 1L;
    private static final Long SELLER = 2L;
    private static final Long STRANGER = 9L;
    private static final Long ORDER_ID = 100L;

    private RefundMapper refundMapper;
    private OrderMapper orderMapper;
    private UserMapper userMapper;
    private JuryCaseMapper juryCaseMapper;
    private ChatMessageMapper chatMessageMapper;
    private RefundServiceImpl service;

    @BeforeEach
    void setUp() {
        // 纯单测环境无 MyBatis 上下文，手动初始化 lambda 缓存（LambdaUpdateWrapper 需要）
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""), Refund.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""), Order.class);

        refundMapper = Mockito.mock(RefundMapper.class);
        orderMapper = Mockito.mock(OrderMapper.class);
        userMapper = Mockito.mock(UserMapper.class);
        juryCaseMapper = Mockito.mock(JuryCaseMapper.class);
        chatMessageMapper = Mockito.mock(ChatMessageMapper.class);
        service = new RefundServiceImpl(refundMapper, orderMapper, userMapper,
                juryCaseMapper, chatMessageMapper, new ObjectMapper());

        // platform 系统账号 + 双方用户（通知链路）
        User platform = new User();
        platform.setId(99L);
        platform.setUsername("platform");
        lenient().when(userMapper.selectOne(any())).thenReturn(platform);
        User anyone = new User();
        anyone.setId(BUYER);
        anyone.setNickname("买家甲");
        lenient().when(userMapper.selectById(anyLong())).thenReturn(anyone);
        // 无进行中售后（重复申请拦截用例单独覆盖）
        lenient().when(refundMapper.selectList(any())).thenReturn(List.of());
        lenient().when(juryCaseMapper.selectOne(any())).thenReturn(null);
        lenient().when(chatMessageMapper.insert(any(ChatMessage.class))).thenReturn(1);
    }

    private Order doneOrder(String tradeType) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setBuyerId(BUYER);
        order.setSellerId(SELLER);
        order.setStatus(OrderStatusEnum.DONE.getCode());
        order.setFinishTime(LocalDateTime.now());
        order.setTradeType(tradeType);
        order.setAmount(new BigDecimal("100.00"));
        order.setItemTitle("测试商品");
        return order;
    }

    private RefundApplyDTO applyDTO(String type) {
        RefundApplyDTO dto = new RefundApplyDTO();
        dto.setOrderId(ORDER_ID);
        dto.setType(type);
        dto.setReason("货不对板");
        return dto;
    }

    @Test
    @DisplayName("非本人订单申请售后 → 403")
    void apply_notOwner_forbidden() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(doneOrder("SHIP"));
        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.apply(STRANGER, applyDTO("REFUND_ONLY")));
        Assertions.assertTrue(e.getMessage().contains("自己的订单"));
    }

    @Test
    @DisplayName("未完成订单不能申请售后")
    void apply_notDone_rejected() {
        Order order = doneOrder("SHIP");
        order.setStatus(OrderStatusEnum.TO_RECEIVE.getCode());
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.apply(BUYER, applyDTO("REFUND_ONLY")));
        Assertions.assertTrue(e.getMessage().contains("完成后"));
    }

    @Test
    @DisplayName("超过 15 天售后有效期 → 拒绝")
    void apply_expiredWindow_rejected() {
        Order order = doneOrder("SHIP");
        order.setFinishTime(LocalDateTime.now().minusDays(16));
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.apply(BUYER, applyDTO("REFUND_ONLY")));
        Assertions.assertTrue(e.getMessage().contains("15 天"));
    }

    @Test
    @DisplayName("面交订单申请退货退款 → 拒绝（面交仅支持仅退款）")
    void apply_meetReturnRefund_rejected() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(doneOrder("MEET"));
        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.apply(BUYER, applyDTO("RETURN_REFUND")));
        Assertions.assertTrue(e.getMessage().contains("仅退款"));
    }

    @Test
    @DisplayName("已有进行中售后 → 不允许重复申请")
    void apply_ongoingExists_rejected() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(doneOrder("SHIP"));
        Refund ongoing = new Refund();
        ongoing.setStatus(RefundStatusEnum.APPLYING.getCode());
        when(refundMapper.selectList(any())).thenReturn(List.of(ongoing));
        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.apply(BUYER, applyDTO("REFUND_ONLY")));
        Assertions.assertTrue(e.getMessage().contains("进行中"));
    }

    @Test
    @DisplayName("合法申请：邮寄单退货退款 → 落 APPLYING，金额为订单快照")
    void apply_ok() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(doneOrder("SHIP"));
        RefundVO result = service.apply(BUYER, applyDTO("RETURN_REFUND"));
        Assertions.assertEquals(RefundStatusEnum.APPLYING.getCode(), result.getStatus());
        Assertions.assertEquals(new BigDecimal("100.00"), result.getAmount());
        Assertions.assertEquals("RETURN_REFUND", result.getType());
    }

    @Test
    @DisplayName("撤销：非申请人 → 403")
    void cancel_notBuyer_forbidden() {
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setBuyerId(BUYER);
        refund.setStatus(RefundStatusEnum.APPLYING.getCode());
        when(refundMapper.selectById(1L)).thenReturn(refund);
        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.cancel(STRANGER, 1L));
        Assertions.assertTrue(e.getMessage().contains("申请人"));
    }

    @Test
    @DisplayName("重提：超过 5 日响应期 → 拒绝")
    void revise_lateWindow_rejected() {
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setBuyerId(BUYER);
        refund.setStatus(RefundStatusEnum.REJECTED.getCode());
        refund.setRejectAt(LocalDateTime.now().minusDays(6));
        when(refundMapper.selectById(1L)).thenReturn(refund);
        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.revise(BUYER, 1L, applyDTO("REFUND_ONLY")));
        Assertions.assertTrue(e.getMessage().contains("5 日"));
    }

    @Test
    @DisplayName("卖家处理：非法动作 → 拒绝")
    void handle_badAction_rejected() {
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setBuyerId(BUYER);
        refund.setSellerId(SELLER);
        refund.setStatus(RefundStatusEnum.APPLYING.getCode());
        refund.setType("REFUND_ONLY");
        refund.setOrderId(ORDER_ID);
        when(refundMapper.selectById(1L)).thenReturn(refund);
        RefundHandleDTO dto = new RefundHandleDTO();
        dto.setAction("WHATEVER");
        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.handle(SELLER, 1L, dto));
        Assertions.assertTrue(e.getMessage().contains("处理动作"));
    }

    @Test
    @DisplayName("卖家同意仅退款 → 状态置 REFUNDED 且订单写 refund_time")
    void handle_agreeRefundOnly_refunded() {
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setBuyerId(BUYER);
        refund.setSellerId(SELLER);
        refund.setOrderId(ORDER_ID);
        refund.setStatus(RefundStatusEnum.APPLYING.getCode());
        refund.setType("REFUND_ONLY");
        // 第一次查询用于校验，更新后的查询返回 REFUNDED（mock 不会真的改对象）
        Refund updated = new Refund();
        org.springframework.beans.BeanUtils.copyProperties(refund, updated);
        updated.setStatus(RefundStatusEnum.REFUNDED.getCode());
        updated.setRefundTime(LocalDateTime.now());
        when(refundMapper.selectById(1L)).thenReturn(refund, updated);
        // 条件更新成功
        when(refundMapper.update(Mockito.isNull(), any())).thenReturn(1);
        when(orderMapper.update(Mockito.isNull(), any())).thenReturn(1);

        RefundHandleDTO dto = new RefundHandleDTO();
        dto.setAction("AGREE");
        RefundVO vo = service.handle(SELLER, 1L, dto);
        Assertions.assertEquals(RefundStatusEnum.REFUNDED.getCode(), vo.getStatus());
        Mockito.verify(orderMapper).update(Mockito.isNull(), any());
    }

    @Test
    @DisplayName("退款收尾：非白名单状态（WAIT_RETURN）→ 拒绝")
    void markRefunded_badState_rejected() {
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setOrderId(ORDER_ID);
        refund.setStatus(RefundStatusEnum.WAIT_RETURN.getCode());
        when(refundMapper.selectById(1L)).thenReturn(refund);
        when(refundMapper.update(Mockito.isNull(), any())).thenReturn(0);
        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.markRefunded(1L));
        Assertions.assertTrue(e.getMessage().contains("不可退款"));
    }
}
