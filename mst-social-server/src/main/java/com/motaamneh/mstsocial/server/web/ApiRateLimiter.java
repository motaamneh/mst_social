package com.motaamneh.mstsocial.server.web;

import com.motaamneh.mstsocial.server.config.ApiRateLimitProperties;
import com.motaamneh.mstsocial.server.persistence.ApiRateLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Runs before core service calls, outside the verification workflow's transactions. */
@Component
@RequiredArgsConstructor
@Slf4j
public final class ApiRateLimiter {
    public enum Operation { CREATE, VERIFY, READ, MUTATE }

    private final ApiRateLimitRepository repository;
    private final ApiRateLimitProperties properties;

    public void check(UUID tenantId, Operation operation) {
        int limit = switch (operation) {
            case CREATE -> properties.getCreatePerMinute();
            case VERIFY -> properties.getVerifyPerMinute();
            case READ -> properties.getReadPerMinute();
            case MUTATE -> properties.getMutationPerMinute();
        };
        long retryAfter = repository.acquire(tenantId, operation.name(), limit);
        if (retryAfter > 0) {
            log.atWarn().addKeyValue("event", "rate_limit.exceeded").addKeyValue("operation", operation)
                    .addKeyValue("limitPerMinute", limit).addKeyValue("retryAfterSeconds", retryAfter)
                    .log("Tenant rate limit exceeded");
            throw new ApiRateLimitException(retryAfter);
        }
    }
}
