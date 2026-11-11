package com.brix.campus.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝沙箱支付装配：仅在配置了 {@code brix.pay.alipay.app-id} 时创建 AlipayClient。
 *
 * <p>与 AI 模块同一降级哲学：无 Key 不装配、启动零报错，收银台降级为模拟支付。</p>
 */
@Slf4j
@Configuration
public class PayAlipayConfig {

    @Bean
    @ConditionalOnExpression("!'${brix.pay.alipay.app-id:} '.isBlank()")
    public AlipayClient alipayClient(@Value("${brix.pay.alipay.gateway}") String gateway,
                                     @Value("${brix.pay.alipay.app-id}") String appId,
                                     @Value("${brix.pay.alipay.private-key}") String privateKey,
                                     @Value("${brix.pay.alipay.alipay-public-key}") String alipayPublicKey) {
        log.info("已启用支付宝沙箱支付：gateway={}, appId={}", gateway, appId);
        return new DefaultAlipayClient(gateway, appId, privateKey, "json", "UTF-8", alipayPublicKey, "RSA2");
    }
}
