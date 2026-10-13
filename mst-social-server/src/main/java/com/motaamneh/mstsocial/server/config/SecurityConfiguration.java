package com.motaamneh.mstsocial.server.config;

import com.motaamneh.mstsocial.server.security.SecurityErrorResponse;
import com.motaamneh.mstsocial.server.security.TenantApiKeyAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TenantApiKeyProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain tenantSecurityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager)
            throws Exception {
        return http
                // Credentials are explicit bearer headers; cookie/session and browser login are disabled.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/api/v1", "/api/v1/**").hasRole("TENANT")
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                SecurityErrorResponse.UNAUTHORIZED.writeTo(response))
                        .accessDeniedHandler((request, response, exception) ->
                                SecurityErrorResponse.FORBIDDEN.writeTo(response)))
                .addFilterBefore(new TenantApiKeyAuthenticationFilter(authenticationManager),
                        AnonymousAuthenticationFilter.class)
                .build();
    }
}
