package com.brix.campus.config;

import com.brix.campus.security.JwtAuthFilter;
import com.brix.campus.security.RestAccessDeniedHandler;
import com.brix.campus.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security 配置（Boot 3 / Security 6 写法，无 WebSecurityConfigurerAdapter）。
 *
 * <p>权限分三层（架构 5.6）：URL 层（本类）→ 数据归属层（Service）→ 前端路由守卫（仅体验）。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    /** 密码编码器：库中密码全部为 BCrypt 密文（PRD N04） */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(registry -> registry
                        // 1. 完全公开的入口
                        .requestMatchers("/auth/register", "/auth/login").permitAll()
                        .requestMatchers("/uploads/**", "/ws/**", "/error").permitAll()
                        // 2. 公开的只读接口（注意 /items/mine 必须先于 /items/** 声明）
                        .requestMatchers(HttpMethod.GET, "/categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/schools").permitAll()
                        .requestMatchers(HttpMethod.GET, "/items/mine").authenticated()
                        .requestMatchers(HttpMethod.GET, "/items/*/comments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/items/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/users/*/reviews").permitAll()
                        .requestMatchers(HttpMethod.GET, "/wants").permitAll()
                        .requestMatchers(HttpMethod.GET, "/announcements").permitAll()
                        // 3. 管理后台：登录入口公开（服务端校验角色），其余按最小权限矩阵拆分
                        //    审核员只管内容，运营只管经营，交叉访问一律 403
                        .requestMatchers("/admin/login").permitAll()
                        .requestMatchers("/admin/items/**", "/admin/reports/**",
                                "/admin/audit-logs/**", "/admin/refunds/**").hasRole("REVIEWER")
                        .requestMatchers("/admin/dashboard/**", "/admin/users/**",
                                "/admin/orders/**", "/admin/categories/**", "/admin/announcements/**").hasRole("OPERATOR")
                        // 兜底：/admin 下新增但未归类的接口，两个后台角色都放行（避免新接口默认裸奔）
                        .requestMatchers("/admin/**").hasAnyRole("REVIEWER", "OPERATOR")
                        // 4. 其余全部需要登录
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
