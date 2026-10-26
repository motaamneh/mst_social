package com.motaamneh.mstsocial.server.admin;

import com.motaamneh.mstsocial.server.config.TenantApiKeyProperties;
import com.motaamneh.mstsocial.server.persistence.TenantApiKeyRepository;
import com.motaamneh.mstsocial.server.persistence.TenantRepository;
import com.motaamneh.mstsocial.server.security.TenantApiKeyCodec;
import com.motaamneh.mstsocial.server.security.TenantApiKeyProvisioningService;
import org.springframework.beans.BeansException;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;

import java.io.Console;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * First-run provisioning: creates a tenant and issues its first credential in one step.
 * Use {@code tenant-key create} to issue further credentials for an existing tenant,
 * which is the normal path for rotation. Never starts the HTTP server.
 */
public final class TenantCommand {
    private TenantCommand() {
    }

    public static int run(String[] args) {
        if (args.length == 1 && (args[0].equals("--help") || args[0].equals("help"))) {
            printUsage();
            return 0;
        }
        Options options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException | DateTimeException exception) {
            System.err.println("Invalid tenant arguments. A name is required; expiry must be an ISO-8601 instant.");
            printUsage();
            return 2;
        }

        Console console = System.console();
        if (console == null) {
            System.err.println("Run tenant creation in an interactive terminal so the credential is not written to redirected logs.");
            return 2;
        }

        SpringApplication application = new SpringApplication(TenantBootstrap.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        // Do not pass command options to Spring; they are not application properties.
        try (var context = application.run()) {
            UUID tenantId = context.getBean(TenantRepository.class).insertReturningId();

            // Two autocommitted statements, not one transaction: credential provisioning
            // deliberately refuses to run inside an existing transaction, so that the
            // plaintext credential is only ever returned after its insert has committed.
            TenantApiKeyCodec.GeneratedCredential generated;
            try {
                generated = context.getBean(TenantApiKeyProvisioningService.class)
                        .create(tenantId, options.name(), options.expiresAt());
            } catch (RuntimeException exception) {
                System.err.println("Tenant " + tenantId + " was created, but issuing its credential failed."
                        + " Retry with: tenant-key create --tenant-id=" + tenantId + " --name=<name>");
                throw exception;
            }

            console.printf("Tenant ID:     %s%nCredential ID: %s%nExpires at:    %s%n",
                    tenantId, generated.metadata().getId(),
                    generated.metadata().getExpiresAt() == null ? "never" : generated.metadata().getExpiresAt());
            console.printf("%nStore this credential securely; it cannot be retrieved again:%n%s%n", generated.credential());
            console.flush();
            if (console.writer().checkError()) {
                System.err.println("The tenant and key were stored but could not be displayed. Revoke credential ID: "
                        + generated.metadata().getId());
                return 1;
            }
            return 0;
        } catch (DataAccessException exception) {
            System.err.println("Database operation failed. Ensure the database is reachable and migrations V1-V3 have been applied.");
            return 1;
        } catch (IllegalArgumentException exception) {
            System.err.println("Invalid credential metadata. The name must be nonblank and expiry must be in the future.");
            return 2;
        } catch (BeansException | IllegalStateException exception) {
            System.err.println("Provisioning could not start or finish. Check database configuration and startup diagnostics.");
            return 1;
        }
    }

    private static Options parse(String[] args) {
        if (args.length == 0 || !args[0].equals("create")) {
            throw new IllegalArgumentException("Expected create");
        }
        Set<String> allowed = Set.of("name", "expires-at");
        Map<String, String> values = new HashMap<>();
        for (int index = 1; index < args.length; index++) {
            String argument = args[index];
            int equals = argument.indexOf('=');
            if (!argument.startsWith("--") || equals < 3) {
                throw new IllegalArgumentException("Expected --option=value");
            }
            String name = argument.substring(2, equals);
            String value = argument.substring(equals + 1);
            if (!allowed.contains(name) || value.isBlank() || values.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Unknown, blank, or duplicate option");
            }
        }
        String name = values.get("name");
        if (name == null) {
            throw new IllegalArgumentException("Missing option");
        }
        Instant expiresAt = values.containsKey("expires-at") ? Instant.parse(values.get("expires-at")) : null;
        return new Options(name, expiresAt);
    }

    private static void printUsage() {
        System.out.println("""
                Usage (after java -jar <server.jar>):
                  tenant create --name=<name> [--expires-at=<ISO-8601 instant>]
                  tenant --help

                Creates a tenant and issues its first API key in one step. The name labels
                the credential; the tenants table has no name column.

                Configure MST_DB_URL, MST_DB_USERNAME, and MST_DB_PASSWORD in the environment.
                Set MST_TENANT_API_KEY_HASH_KEY to the same 64-hex-character secret used by the server.
                Requires an interactive terminal. To issue more keys for an existing tenant,
                use: tenant-key create --tenant-id=<uuid> --name=<name>
                Exit codes: 0 success, 1 operation failure, 2 invalid input.
                """);
    }

    private static final class Options {
        private final String name;
        private final Instant expiresAt;

        private Options(String name, Instant expiresAt) {
            this.name = name;
            this.expiresAt = expiresAt;
        }

        public String name() {
            return name;
        }

        public Instant expiresAt() {
            return expiresAt;
        }
    }

    // Deliberately no @Configuration/@Component: the web application's component scan must not load this.
    // Passed explicitly to SpringApplication, this is a separate, minimal application context.
    @EnableAutoConfiguration
    @EnableConfigurationProperties(TenantApiKeyProperties.class)
    @Import({TenantRepository.class, TenantApiKeyRepository.class, TenantApiKeyCodec.class,
            TenantApiKeyProvisioningService.class})
    public static class TenantBootstrap {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        SecureRandom secureRandom() {
            return new SecureRandom();
        }
    }
}
