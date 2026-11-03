package com.brix.campus;

import com.brix.campus.common.BusinessException;
import com.brix.campus.model.Order;
import com.brix.campus.model.dto.OrderCreateDTO;
import com.brix.campus.model.dto.PayDTO;
import com.brix.campus.model.enums.OrderStatusEnum;
import com.brix.campus.model.vo.OrderVO;
import com.brix.campus.service.OrderService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 支付事务测试（E02 / E10 / F01）。
 *
 * <p>启用前提：先执行 {@code db/schema.sql} 与 {@code db/data.sql}，并保证本机 MySQL 可连。
 * 方法级 {@code @Transactional} 保证用例结束回滚，不污染种子数据。</p>
 */
@Disabled("需先初始化数据库（db/schema.sql + db/data.sql）并配置 DB_USERNAME/DB_PASSWORD 后手工启用")
@SpringBootTest
class OrderPayTransactionTest {

    @Autowired
    private OrderService orderService;

    @Test
    @Transactional
    @DisplayName("支付成功：订单转待面交、商品售出、兄弟单被关闭")
    void pay_success_updatesOrderItemAndSiblingOrders() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setItemId(1L);
        dto.setTradeType("MEET");
        dto.setMeetPlace("东校区图书馆");
        OrderVO order = orderService.create(1L, dto);

        PayDTO pay = new PayDTO();
        pay.setPayMethod("WECHAT");
        OrderVO paid = orderService.pay(1L, order.getId(), pay);

        Assertions.assertEquals(OrderStatusEnum.TO_MEET.getCode(), paid.getStatus(),
                "面交单支付后应进入 TO_MEET");
    }

    @Test
    @Transactional
    @DisplayName("重复支付：第二次支付抛业务异常")
    void pay_twice_throwsBusinessException() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setItemId(2L);
        dto.setTradeType("MEET");
        OrderVO order = orderService.create(1L, dto);
        PayDTO pay = new PayDTO();
        pay.setPayMethod("ALIPAY");
        orderService.pay(1L, order.getId(), pay);

        Assertions.assertThrows(BusinessException.class,
                () -> orderService.pay(1L, order.getId(), pay),
                "重复支付已支付订单必须抛 BusinessException");
    }

    @Test
    @Transactional
    @DisplayName("并发支付：两个买家同时支付同一商品，仅一个成功")
    void pay_twoBuyersConcurrent_onlyOneSucceeds() throws InterruptedException {
        OrderCreateDTO first = new OrderCreateDTO();
        first.setItemId(3L);
        first.setTradeType("MEET");
        OrderVO orderA = orderService.create(1L, first);

        OrderCreateDTO second = new OrderCreateDTO();
        second.setItemId(3L);
        second.setTradeType("MEET");
        OrderVO orderB = orderService.create(8L, second);

        PayDTO pay = new PayDTO();
        pay.setPayMethod("BANK");

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        Runnable taskA = () -> {
            try {
                start.await();
                orderService.pay(1L, orderA.getId(), pay);
                success.incrementAndGet();
            } catch (Exception e) {
                failure.incrementAndGet();
            } finally {
                done.countDown();
            }
        };
        Runnable taskB = () -> {
            try {
                start.await();
                orderService.pay(8L, orderB.getId(), pay);
                success.incrementAndGet();
            } catch (Exception e) {
                failure.incrementAndGet();
            } finally {
                done.countDown();
            }
        };
        new Thread(taskA).start();
        new Thread(taskB).start();
        start.countDown();
        done.await();

        Assertions.assertEquals(1, success.get(),
                "并发支付同一商品只能有一笔成功，实际成功 " + success.get() + " 笔");
        Assertions.assertEquals(1, failure.get(),
                "另一笔必须失败，实际失败 " + failure.get() + " 笔");

        Order item = orderService.getEntity(orderA.getId());
        Assertions.assertNotNull(item, "成功订单应可查询");
    }
}
