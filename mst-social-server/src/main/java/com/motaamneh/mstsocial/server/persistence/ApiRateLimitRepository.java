package com.motaamneh.mstsocial.server.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.util.UUID;

/** Atomic PostgreSQL counters shared by all server instances and all keys of a tenant. */
@Repository
public class ApiRateLimitRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ApiRateLimitRepository(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    /** Returns zero when admitted, or the seconds until the current window ends. */
    public long acquire(UUID tenantId, String operation, int limit) {
        var parameters = new MapSqlParameterSource("tenant", tenantId)
                .addValue("operation", operation).addValue("limit", limit);
        var admitted = jdbc.query("""
                INSERT INTO api_rate_limits (tenant_id, operation, window_started_at, request_count)
                VALUES (:tenant, :operation, statement_timestamp(), 1)
                ON CONFLICT (tenant_id, operation) DO UPDATE SET
                    window_started_at = CASE
                        WHEN api_rate_limits.window_started_at <= statement_timestamp() - INTERVAL '1 minute'
                        THEN statement_timestamp() ELSE api_rate_limits.window_started_at END,
                    request_count = CASE
                        WHEN api_rate_limits.window_started_at <= statement_timestamp() - INTERVAL '1 minute'
                        THEN 1 ELSE api_rate_limits.request_count + 1 END
                WHERE api_rate_limits.window_started_at <= statement_timestamp() - INTERVAL '1 minute'
                   OR api_rate_limits.request_count < :limit
                RETURNING request_count
                """, parameters, (row, index) -> row.getInt(1));
        if (!admitted.isEmpty()) {
            return 0;
        }
        Long seconds = jdbc.queryForObject("""
                SELECT GREATEST(1, CEIL(EXTRACT(EPOCH FROM
                    (window_started_at + INTERVAL '1 minute' - statement_timestamp()))))::bigint
                FROM api_rate_limits WHERE tenant_id = :tenant AND operation = :operation
                """, parameters, Long.class);
        return seconds == null ? 1 : Math.max(1, seconds);
    }
}
