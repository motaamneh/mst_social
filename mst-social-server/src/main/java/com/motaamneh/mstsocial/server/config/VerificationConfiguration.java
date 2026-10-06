package com.motaamneh.mstsocial.server.config;

import com.motaamneh.mstsocial.core.model.VerificationPolicy;
import com.motaamneh.mstsocial.core.port.ProfileProvider;
import com.motaamneh.mstsocial.core.port.VerificationStore;
import com.motaamneh.mstsocial.core.security.VerificationCodeGenerator;
import com.motaamneh.mstsocial.core.security.VerificationCodeHasher;
import com.motaamneh.mstsocial.core.service.VerificationLimits;
import com.motaamneh.mstsocial.core.service.VerificationService;
import com.motaamneh.mstsocial.core.validation.HandleNormalizer;
import com.motaamneh.mstsocial.provider.searchapi.SearchApiProfileParser;
import com.motaamneh.mstsocial.provider.searchapi.SearchApiProfileProvider;
import com.motaamneh.mstsocial.provider.searchapi.SearchApiSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** Constructs the application components; provider calls use the core's transaction boundaries. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SearchApiProperties.class, VerificationProperties.class})
public class VerificationConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    HandleNormalizer handleNormalizer() {
        return new HandleNormalizer();
    }

    @Bean
    VerificationPolicy verificationPolicy() {
        return VerificationPolicy.DEFAULT;
    }

    @Bean
    VerificationLimits verificationLimits() {
        return VerificationLimits.DEFAULT;
    }

    @Bean
    VerificationCodeGenerator verificationCodeGenerator(SecureRandom secureRandom) {
        return new VerificationCodeGenerator(secureRandom);
    }

    @Bean
    VerificationCodeHasher verificationCodeHasher(VerificationProperties properties) {
        Map<String, SecretKey> keys = new HashMap<>();
        properties.keys().forEach((version, encodedKey) -> {
            byte[] keyBytes = HexFormat.of().parseHex(encodedKey);
            try {
                // SecretKeySpec copies the bytes; retain each version for pending requests.
                keys.put(version, new SecretKeySpec(keyBytes, "HmacSHA256"));
            } finally {
                Arrays.fill(keyBytes, (byte) 0);
            }
        });
        return new VerificationCodeHasher(keys);
    }

    @Bean
    SearchApiSettings searchApiSettings(SearchApiProperties properties) {
        return properties.toSettings();
    }

    @Bean
    SearchApiProfileParser searchApiProfileParser() {
        // Dedicated mapper keeps provider parsing independent of MVC JSON customization.
        return new SearchApiProfileParser(JsonMapper.builder().build());
    }

    @Bean(destroyMethod = "close")
    SearchApiProfileProvider searchApiProfileProvider(
            SearchApiProperties properties,
            SearchApiSettings settings,
            SearchApiProfileParser parser,
            HandleNormalizer normalizer,
            Clock clock
    ) {
        return new SearchApiProfileProvider(properties.apiKey(), settings, parser, normalizer, clock);
    }

    @Bean
    VerificationService verificationService(
            VerificationStore store,
            ProfileProvider provider,
            VerificationCodeGenerator generator,
            VerificationCodeHasher hasher,
            HandleNormalizer normalizer,
            VerificationPolicy policy,
            VerificationLimits limits,
            Clock clock,
            VerificationProperties properties
    ) {
        return new VerificationService(store, provider, generator, hasher, normalizer,
                policy, limits, clock, properties.activeKeyVersion());
    }

}
