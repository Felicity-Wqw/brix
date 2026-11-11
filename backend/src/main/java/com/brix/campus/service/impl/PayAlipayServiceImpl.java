package com.brix.campus.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.brix.campus.common.BusinessException;
import com.brix.campus.model.Item;
import com.brix.campus.model.Order;
import com.brix.campus.model.dto.PayDTO;
import com.brix.campus.mapper.ItemMapper;
import com.brix.campus.mapper.OrderMapper;
import com.brix.campus.model.enums.OrderStatusEnum;
import com.brix.campus.service.OrderService;
import com.brix.campus.service.PayAlipayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 支付宝沙箱支付实现。
 *
 * <p>双保险确认支付：①收银台每 2 秒主动查单（沙箱本地无公网时的主力）；
 * ②异步回调 notify 验签（生产环境配公网域名后生效）。两条路都汇聚到
 * {@link OrderService#pay} 完成订单流转——防超卖抢占、关闭重复待付款单等口径零重复。</p>
 */
@Slf4j
@Service
public class PayAlipayServiceImpl implements PayAlipayService {

    /** 商户订单号前缀：out_trade_no = BRIX + orderId，全局唯一 */
    private static final String OUT_TRADE_PREFIX = "BRIX";

    private final ObjectProvider<AlipayClient> alipayClientProvider;
    private final OrderMapper orderMapper;
    private final ItemMapper itemMapper;
    private final OrderService orderService;
    private final String alipayPublicKey;
    private final String returnUrlBase;

    public PayAlipayServiceImpl(@Qualifier("alipayClient") ObjectProvider<AlipayClient> alipayClientProvider,
                                OrderMapper orderMapper,
                                ItemMapper itemMapper,
                                OrderService orderService,
                                @Value("${brix.pay.alipay.alipay-public-key:}") String alipayPublicKey,
                                @Value("${brix.pay.alipay.return-url:http://localhost:5173/checkout/}") String returnUrlBase) {
        this.alipayClientProvider = alipayClientProvider;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.orderService = orderService;
        this.alipayPublicKey = alipayPublicKey;
        this.returnUrlBase = returnUrlBase;
    }

    @Override
    public String pagePay(Long buyerId, Long orderId) {
        AlipayClient client = requireClient();
        Order order = requireOrder(buyerId, orderId);

        Item item = itemMapper.selectById(order.getItemId());
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        com.alipay.api.domain.AlipayTradePagePayModel model = new com.alipay.api.domain.AlipayTradePagePayModel();
        model.setOutTradeNo(OUT_TRADE_PREFIX + orderId);
        model.setTotalAmount(order.getAmount().toPlainString());
        model.setSubject("物归校园 · " + (item == null ? "商品" : item.getTitle()));
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        request.setBizModel(model);
        // 付款完成回跳：浏览器自己跳，localhost 可达（与 notify 不同，不需要公网）
        request.setReturnUrl(returnUrlBase + orderId);
        try {
            // GET 模式：getBody() 直接返回可打开的收银台 URL
            AlipayTradePagePayResponse response = client.pageExecute(request, "GET");
            if (response == null || response.getBody() == null || response.getBody().isBlank()) {
                throw BusinessException.param("支付宝收银台生成失败，请稍后重试");
            }
            return response.getBody();
        } catch (AlipayApiException e) {
            log.warn("支付宝 page.pay 异常（orderId={}）：{}", orderId, e.getMessage());
            throw BusinessException.param("支付宝网关调用失败，请稍后重试");
        }
    }

    @Override
    public String precreate(Long buyerId, Long orderId) {
        AlipayClient client = requireClient();
        Order order = requireOrder(buyerId, orderId);

        Item item = itemMapper.selectById(order.getItemId());
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        com.alipay.api.domain.AlipayTradePrecreateModel model = new com.alipay.api.domain.AlipayTradePrecreateModel();
        model.setOutTradeNo(OUT_TRADE_PREFIX + orderId);
        model.setTotalAmount(order.getAmount().toPlainString());
        model.setSubject("物归校园 · " + (item == null ? "商品" : item.getTitle()));
        request.setBizModel(model);
        try {
            AlipayTradePrecreateResponse response = client.execute(request);
            if (response == null || !response.isSuccess()
                    || response.getQrCode() == null || response.getQrCode().isBlank()) {
                log.warn("支付宝预下单失败：code={}，msg={}",
                        response == null ? "-" : response.getSubMsg(), response == null ? "-" : response.getMsg());
                throw BusinessException.param("支付宝预下单失败：" + (response == null ? "无响应" : response.getSubMsg()));
            }
            log.info("支付宝预下单成功：orderId={}，qr={}", orderId, response.getQrCode());
            return response.getQrCode();
        } catch (AlipayApiException e) {
            log.warn("支付宝预下单异常（orderId={}）：{}", orderId, e.getMessage());
            throw BusinessException.param("支付宝网关调用失败，请稍后重试");
        }
    }

    @Override
    public void refundOrder(Long orderId, java.math.BigDecimal amount, String outRequestNo) {
        AlipayClient client = requireClient();
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        com.alipay.api.domain.AlipayTradeRefundModel model = new com.alipay.api.domain.AlipayTradeRefundModel();
        model.setOutTradeNo(OUT_TRADE_PREFIX + orderId);
        model.setRefundAmount(amount.toPlainString());
        model.setOutRequestNo(outRequestNo);
        request.setBizModel(model);
        try {
            AlipayTradeRefundResponse response = client.execute(request);
            if (response != null && !response.isSuccess()
                    && response.getSubMsg() != null && response.getSubMsg().contains("交易不存在")) {
                // 历史模拟支付订单（沙箱接入前的 WECHAT/ALIPAY 标签单）没有真实交易，跳过真实退款
                log.info("历史模拟支付订单无真实交易，跳过退款：orderId={}", orderId);
                return;
            }
            if (response == null || !response.isSuccess()) {
                log.warn("支付宝退款失败：orderId={}，outRequestNo={}，subMsg={}",
                        orderId, outRequestNo, response == null ? "-" : response.getSubMsg());
                throw BusinessException.param("支付宝退款失败：" + (response == null ? "无响应" : response.getSubMsg()));
            }
            log.info("支付宝退款成功：orderId={}，amount={}，fundChange={}",
                    orderId, amount, response.getFundChange());
        } catch (AlipayApiException e) {
            log.warn("支付宝退款网关异常（orderId={}）：{}", orderId, e.getMessage());
            throw BusinessException.param("支付宝退款网关调用失败，请稍后重试");
        }
    }

    @Override
    public Map<String, Object> refundQuery(Long orderId, String outRequestNo) {
        AlipayClient client = requireClient();
        AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
        com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel model =
                new com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel();
        model.setOutTradeNo(OUT_TRADE_PREFIX + orderId);
        model.setOutRequestNo(outRequestNo);
        request.setBizModel(model);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        try {
            AlipayTradeFastpayRefundQueryResponse response = client.execute(request);
            if (response != null && response.isSuccess()) {
                result.put("refunded", true);
                result.put("refundAmount", response.getRefundAmount());
                result.put("tradeNo", response.getTradeNo());
            } else {
                result.put("refunded", false);
                result.put("reason", response == null ? "无响应" : response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            result.put("refunded", false);
            result.put("reason", "网关调用失败：" + e.getMessage());
        }
        return result;
    }

    @Override
    public boolean confirmIfPaid(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        // 已流转即视为支付完成（幂等：重复轮询/回调不重复处理）
        if (!OrderStatusEnum.UNPAID.getCode().equals(order.getStatus())) {
            return true;
        }
        AlipayClient client = requireClient();

        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        com.alipay.api.domain.AlipayTradeQueryModel model = new com.alipay.api.domain.AlipayTradeQueryModel();
        model.setOutTradeNo(OUT_TRADE_PREFIX + orderId);
        request.setBizModel(model);
        try {
            AlipayTradeQueryResponse response = client.execute(request);
            if (!response.isSuccess()) {
                // ACQ.TRADE_NOT_EXIST = 还没付过，属正常轮询态
                return false;
            }
            String status = response.getTradeStatus();
            if (!"TRADE_SUCCESS".equals(status) && !"TRADE_FINISHED".equals(status)) {
                return false;
            }
            completePayment(order);
            return true;
        } catch (AlipayApiException e) {
            log.warn("支付宝查单异常（orderId={}）：{}", orderId, e.getMessage());
            return false;
        }
    }

    @Override
    public String handleNotify(Map<String, String> params) {
        try {
            boolean verified = alipayPublicKey != null && !alipayPublicKey.isBlank()
                    && AlipaySignature.rsaCheckV1(params, alipayPublicKey, "UTF-8", "RSA2");
            if (!verified) {
                log.warn("支付宝回调验签失败：outTradeNo={}", params.get("out_trade_no"));
                return "fail";
            }
            String outTradeNo = params.get("out_trade_no");
            String tradeStatus = params.get("trade_status");
            if (outTradeNo == null || !outTradeNo.startsWith(OUT_TRADE_PREFIX)
                    || !"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                return "fail";
            }
            Long orderId = Long.valueOf(outTradeNo.substring(OUT_TRADE_PREFIX.length()));
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                return "fail";
            }
            if (!OrderStatusEnum.UNPAID.getCode().equals(order.getStatus())) {
                return "success"; // 幂等：已流转过
            }
            completePayment(order);
            return "success";
        } catch (Exception e) {
            log.warn("支付宝回调处理失败：{}", e.getMessage());
            return "fail";
        }
    }

    /**
     * 支付确认后的落账：复用 OrderService.pay() 的全部口径
     * （抢占商品防超卖 → 订单流转 → 关闭同商品其它待付款单）。
     * 并发竞态下 pay() 抛「状态已变更」时，复查订单——已流转则视为成功。
     */
    private void completePayment(Order order) {
        try {
            PayDTO dto = new PayDTO();
            dto.setPayMethod("ALIPAY");
            orderService.pay(order.getBuyerId(), order.getId(), dto);
        } catch (BusinessException e) {
            Order latest = orderMapper.selectById(order.getId());
            if (latest != null && OrderStatusEnum.UNPAID.getCode().equals(latest.getStatus())) {
                throw e; // 真失败（如商品被抢先购买），向上抛给回调/查单侧
            }
            log.info("支付宝支付落账竞态收敛（orderId={}）：订单已完成流转", order.getId());
        }
    }

    private AlipayClient requireClient() {
        AlipayClient client = alipayClientProvider.getIfAvailable();
        if (client == null) {
            throw BusinessException.param("支付宝沙箱未配置（brix.pay.alipay.app-id 为空），请使用模拟支付");
        }
        return client;
    }

    private Order requireOrder(Long buyerId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw BusinessException.forbidden("无权操作该订单");
        }
        if (!OrderStatusEnum.UNPAID.getCode().equals(order.getStatus())) {
            throw BusinessException.param("订单状态已变更，请刷新");
        }
        return order;
    }
}
