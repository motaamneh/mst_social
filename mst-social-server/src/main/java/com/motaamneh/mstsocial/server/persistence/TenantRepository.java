package com.motaamneh.mstsocial.server.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.UUID;

@Repository
public class TenantRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TenantRepository(DataSource dataSource) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(
                Objects.requireNonNull(dataSource, "dataSource must not be null")
        );
    }

    /**
     * Inserts a tenant and returns the database-generated identifier. V2 sets the
     * column default, so the application never supplies an ID. Provisioning a tenant
     * requires trusted database access; no HTTP endpoint reaches this operation.
     */
    public UUID insertReturningId() {
        UUID id = jdbcTemplate.queryForObject("""
                INSERT INTO tenants DEFAULT VALUES
                RETURNING id
                """, new MapSqlParameterSource(), UUID.class);
        if (id == null) {
            throw new IllegalStateException("Expected a generated tenant identifier");
        }
        return id;
    }
}
