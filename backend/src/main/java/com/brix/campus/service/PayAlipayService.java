package com.brix.campus.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝沙箱支付服务：预下单出二维码 / 主动查单确认 / 异步回调验签。
 *
 * <p>支付成功后的订单流转<strong>完全复用</strong> {@code OrderService.pay()} ——
 * 防超卖抢占、关闭同商品其它待付款单、状态流转口径零重复。</p>
 */
public interface PayAlipayService {

    /**
     * 当面付预下单：返回二维码码串（前端渲染成扫码图）。
     *
     * @param buyerId 买家 ID（校验订单归属）
     * @param orderId 订单 ID
     * @return qrCode 码串
     */
    String precreate(Long buyerId, Long orderId);

    /**
     * 电脑网站支付（page.pay）：返回沙箱收银台跳转 URL，
     * 浏览器新窗口打开 → 沙箱买家账号登录付款 → 回跳 return_url。
     *
     * @param buyerId 买家 ID（校验订单归属）
     * @param orderId 订单 ID
     * @return 收银台跳转 URL
     */
    String pagePay(Long buyerId, Long orderId);

    /**
     * 主动查单：调 alipay.trade.query；确认支付成功则复用 pay() 完成订单流转。
     *
     * @param orderId 订单 ID
     * @return true=已支付（含之前已完成）；false=尚未支付
     */
    boolean confirmIfPaid(Long orderId);

    /**
     * 真实退款：alipay.trade.refund，把指定金额原路退回买家。
     * outRequestNo 幂等号保证同一笔退款重试不会重复退钱。
     * 失败抛业务异常（调用方事务回滚，状态不虚报「已退款」）。
     *
     * @param orderId      订单 ID（out_trade_no = BRIX + orderId）
     * @param amount       退款金额（元）
     * @param outRequestNo 退款幂等号
     */
    void refundOrder(Long orderId, BigDecimal amount, String outRequestNo);

    /**
     * 退款查询（alipay.trade.fastpay.refund.query）：管理端核验退款是否到账。
     *
     * @param orderId      订单 ID
     * @param outRequestNo 退款幂等号
     * @return refunded=true 时含 refundAmount/tradeNo
     */
    Map<String, Object> refundQuery(Long orderId, String outRequestNo);

    /**
     * 异步回调验签与处理（生产环境公网可达时由支付宝 POST 调用）。
     *
     * @param params 回调表单参数
     * @return "success" 或 "fail"（支付宝约定响应体）
     */
    String handleNotify(Map<String, String> params);
}
