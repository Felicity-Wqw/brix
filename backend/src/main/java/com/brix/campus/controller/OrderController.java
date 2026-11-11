package com.brix.campus.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.common.R;
import com.brix.campus.model.dto.CancelOrderDTO;
import com.brix.campus.model.dto.OrderCreateDTO;
import com.brix.campus.model.dto.PayDTO;
import com.brix.campus.service.PayAlipayService;
import com.brix.campus.model.dto.ReviewCreateDTO;
import com.brix.campus.model.query.OrderQuery;
import com.brix.campus.model.vo.OrderDetailVO;
import com.brix.campus.model.vo.OrderVO;
import com.brix.campus.model.vo.ReviewVO;
import com.brix.campus.security.SecurityUtils;
import com.brix.campus.service.OrderService;
import com.brix.campus.service.ReviewService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口（E01-E09、G01）。
 */
@RestController
@RequestMapping("/orders")
@Validated
public class OrderController {

    private final OrderService orderService;
    private final ReviewService reviewService;
    private final PayAlipayService payAlipayService;

    public OrderController(OrderService orderService,
                           ReviewService reviewService,
                           PayAlipayService payAlipayService) {
        this.orderService = orderService;
        this.reviewService = reviewService;
        this.payAlipayService = payAlipayService;
    }

    /**
     * 下单。
     *
     * @param dto 入参
     * @return 订单（UNPAID，前端跳收银台）
     */
    @PostMapping
    public R<OrderVO> create(@RequestBody @Valid OrderCreateDTO dto) {
        return R.ok(orderService.create(SecurityUtils.currentUserId(), dto), "下单成功");
    }

    /**
     * 收银台支付（模拟）。
     *
     * @param id  订单 ID
     * @param dto 支付方式
     * @return 支付后的订单
     */
    @PostMapping("/{id}/pay")
    public R<OrderVO> pay(@PathVariable("id") Long id, @RequestBody @Valid PayDTO dto) {
        return R.ok(orderService.pay(SecurityUtils.currentUserId(), id, dto), "支付成功");
    }

    /**
     * 支付宝沙箱预下单：返回二维码码串（前端渲染扫码图）。
     *
     * @param id 订单 ID
     * @return qrCode 码串
     */
    /**
     * 支付宝电脑网站支付（page.pay）：返回沙箱收银台跳转 URL，新窗口打开付款。
     *
     * @param id 订单 ID
     * @return 收银台 URL
     */
    @GetMapping("/{id}/alipay/pagepay")
    public R<String> alipayPagePay(@PathVariable("id") Long id) {
        return R.ok(payAlipayService.pagePay(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id}/alipay/precreate")
    public R<String> alipayPrecreate(@PathVariable("id") Long id) {
        return R.ok(payAlipayService.precreate(SecurityUtils.currentUserId(), id), "预下单成功");
    }

    /**
     * 支付宝支付状态主动查单（收银台 2 秒轮询；查到已支付则完成订单流转）。
     *
     * @param id 订单 ID
     * @return true=已支付（订单已流转）
     */
    @GetMapping("/{id}/alipay/status")
    public R<Boolean> alipayStatus(@PathVariable("id") Long id) {
        return R.ok(payAlipayService.confirmIfPaid(id));
    }

    /**
     * 支付宝异步回调（生产环境公网可达时支付宝 POST 调用；permitAll + RSA2 验签自证身份）。
     * 响应体为支付宝约定的明文 success/fail，不走 R 包装。
     *
     * @param params 回调表单
     * @return success / fail
     */
    @PostMapping(value = "/alipay/notify", produces = "text/plain;charset=UTF-8")
    public String alipayNotify(@RequestParam Map<String, String> params) {
        return payAlipayService.handleNotify(params);
    }

    /**
     * 卖家发货。
     *
     * @param id 订单 ID
     * @return 订单
     */
    @PostMapping("/{id}/ship")
    public R<OrderVO> ship(@PathVariable("id") Long id) {
        return R.ok(orderService.ship(SecurityUtils.currentUserId(), id), "已发货");
    }

    /**
     * 买家确认收货。
     *
     * @param id 订单 ID
     * @return 订单
     */
    @PostMapping("/{id}/confirm")
    public R<OrderVO> confirm(@PathVariable("id") Long id) {
        return R.ok(orderService.confirm(SecurityUtils.currentUserId(), id), "确认收货成功");
    }

    /**
     * 取消订单。
     *
     * @param id  订单 ID
     * @param dto 取消原因
     * @return 订单
     */
    @PostMapping("/{id}/cancel")
    public R<OrderVO> cancel(@PathVariable("id") Long id,
                             @RequestBody(required = false) @Valid CancelOrderDTO dto) {
        CancelOrderDTO body = dto == null ? new CancelOrderDTO() : dto;
        return R.ok(orderService.cancel(SecurityUtils.currentUserId(), id, body), "订单已取消");
    }

    /**
     * 我买到的订单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/bought")
    public R<IPage<OrderVO>> bought(@Valid OrderQuery query) {
        return R.ok(orderService.pageBought(SecurityUtils.currentUserId(), query));
    }

    /**
     * 我卖出的订单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/sold")
    public R<IPage<OrderVO>> sold(@Valid OrderQuery query) {
        return R.ok(orderService.pageSold(SecurityUtils.currentUserId(), query));
    }

    /**
     * 订单详情。
     *
     * @param id 订单 ID
     * @return 详情
     */
    @GetMapping("/{id}")
    public R<OrderDetailVO> detail(@PathVariable("id") Long id) {
        return R.ok(orderService.detail(SecurityUtils.currentUserId(),
                SecurityUtils.currentRoleOrNull(), id));
    }

    /**
     * 买家评价卖家。
     *
     * @param id  订单 ID
     * @param dto 入参
     * @return 评价
     */
    @PostMapping("/{id}/review")
    public R<ReviewVO> review(@PathVariable("id") Long id, @RequestBody @Valid ReviewCreateDTO dto) {
        return R.ok(reviewService.create(SecurityUtils.currentUserId(), id, dto), "评价成功");
    }
}
