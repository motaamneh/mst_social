package com.motaamneh.mstsocial.server.admin;

import com.motaamneh.mstsocial.server.config.TenantApiKeyProperties;
import com.motaamneh.mstsocial.server.persistence.TenantApiKeyRepository;
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

/** Local administration using database credentials. Never starts the HTTP server. */
public final class TenantKeyCommand {
    private TenantKeyCommand() {
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
            System.err.println("Invalid tenant-key arguments. UUIDs must be canonical; expiry must be an ISO-8601 instant.");
            printUsage();
            return 2;
        }

        Console console = System.console();
        if (options.create() && console == null) {
            System.err.println("Run key creation in an interactive terminal so the credential is not written to redirected logs.");
            return 2;
        }

        SpringApplication application = new SpringApplication(AdminBootstrap.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        // Do not pass command options to Spring; they are not application properties.
        try (var context = application.run()) {
            var provisioning = context.getBean(TenantApiKeyProvisioningService.class);
            if (options.create()) {
                var generated = provisioning.create(options.tenantId(), options.name(), options.expiresAt());
                console.printf("Credential ID: %s%nTenant ID: %s%nExpires at: %s%n",
                        generated.metadata().getId(), generated.metadata().getTenantId(),
                        generated.metadata().getExpiresAt() == null ? "never" : generated.metadata().getExpiresAt());
                console.printf("Store this credential securely; it cannot be retrieved again:%n%s%n", generated.credential());
                console.flush();
                if (console.writer().checkError()) {
                    System.err.println("The key was stored but could not be displayed. Revoke credential ID: "
                            + generated.metadata().getId());
                    return 1;
                }
                return 0;
            }
            if (provisioning.revoke(options.tenantId(), options.credentialId())) {
                System.out.println("Credential revoked: " + options.credentialId());
                return 0;
            }
            System.err.println("No credential changed. Check the tenant and credential IDs; the key may already be revoked.");
            return 3;
        } catch (DataAccessException exception) {
            System.err.println("Database operation failed. For creation, ensure the tenant already exists and V3 has been applied.");
            return 1;
        } catch (IllegalArgumentException exception) {
            System.err.println("Invalid credential metadata. The name must be nonblank and expiry must be in the future.");
            return 2;
        } catch (BeansException | IllegalStateException exception) {
            System.err.println("Administration could not start or finish. Check database configuration and startup diagnostics.");
            return 1;
        }
    }

    private static Options parse(String[] args) {
        if (args.length == 0 || !(args[0].equals("create") || args[0].equals("revoke"))) {
            throw new IllegalArgumentException("Expected create or revoke");
        }
        boolean create = args[0].equals("create");
        Set<String> allowed = create
                ? Set.of("tenant-id", "name", "expires-at")
                : Set.of("tenant-id", "credential-id");
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
        UUID tenantId = uuid(required(values, "tenant-id"));
        if (create) {
            Instant expiresAt = values.containsKey("expires-at") ? Instant.parse(values.get("expires-at")) : null;
            return new Options(true, tenantId, required(values, "name"), expiresAt, null);
        }
        return new Options(false, tenantId, null, null, uuid(required(values, "credential-id")));
    }

    private static String required(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing option");
        }
        return value;
    }

    private static UUID uuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("UUID must use the canonical format");
        }
        return id;
    }

    private static void printUsage() {
        System.out.println("""
                Usage (after java -jar <server.jar>):
                  tenant-key create --tenant-id=<uuid> --name=<name> [--expires-at=<ISO-8601 instant>]
                  tenant-key revoke --tenant-id=<uuid> --credential-id=<uuid>
                  tenant-key --help

                Configure MST_DB_URL, MST_DB_USERNAME, and MST_DB_PASSWORD in the environment.
                Set MST_TENANT_API_KEY_HASH_KEY to the same 64-hex-character secret used by the server.
                Creation requires an existing tenant and an interactive terminal.
                Exit codes: 0 success, 1 operation failure, 2 invalid input, 3 no credential revoked.
                """);
    }

    private record Options(boolean create, UUID tenantId, String name, Instant expiresAt, UUID credentialId) {
    }

    // Deliberately no @Configuration/@Component: the web application's component scan must not load this.
    // Passed explicitly to SpringApplication, this is a separate, minimal application context.
    @EnableAutoConfiguration
    @EnableConfigurationProperties(TenantApiKeyProperties.class)
    @Import({TenantApiKeyRepository.class, TenantApiKeyCodec.class, TenantApiKeyProvisioningService.class})
    public static class AdminBootstrap {
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
