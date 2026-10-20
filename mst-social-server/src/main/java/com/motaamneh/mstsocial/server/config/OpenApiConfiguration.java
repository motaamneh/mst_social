package com.motaamneh.mstsocial.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared documentation only; runtime status decisions remain in the HTTP mapper and handlers. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "mst.api-docs.enabled", havingValue = "true")
public class OpenApiConfiguration {
    @Bean
    OpenAPI verificationOpenApi(@Value("${server.servlet.context-path:}") String contextPath) {
        Components components = new Components().addSecuritySchemes("tenantKey", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer")
                .bearerFormat("mst_api_<credential-uuid>.<secret>")
                .description("Paste the complete tenant API key without the Bearer prefix. This is not a JWT. "
                        + "Provision keys with the tenant-key CLI. Keep them on your application backend."));
        return new OpenAPI().components(components)
                .info(new Info().title("MST Social verification API").version("0.0.1-SNAPSHOT")
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0"))
                        .description("""
                                Verify control of Instagram and TikTok accounts through a code in the profile bio.
                                This establishes control at verification time, not legal identity or permanent ownership.

                                Start with GET /api/v1/tenant, create a verification, copy its code into the bio,
                                then explicitly submit a verification attempt. Successful verification returns an account ID.
                                Private profiles are supported when the provider supplies a usable username and bio.
                                X and Facebook are known enum values but currently unsupported (422).

                                All API operations require a backend-only tenant Bearer credential. The consuming backend
                                must authorize its users and subject/resource IDs. Do not expose tenant keys in frontend code.
                                Tenant IDs are inferred from the credential. Mutations reject Idempotency-Key headers;
                                only creation accepts a body. Never log credentials or creation responses containing codes.
                                Limits described here are defaults and can be configured by the operator.
                                """))
                // Same origin: Docker port mappings and HTTPS work without a hardcoded localhost address.
                .addServersItem(new Server().url(contextPath.isBlank() ? "/" : contextPath)
                        .description("This server"))
                .addSecurityItem(new SecurityRequirement().addList("tenantKey"));
    }

    @Bean
    OpenApiCustomizer verificationResponses() {
        return api -> api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            var responses = operation.getResponses();
            responses.addApiResponse("401", problem(401, "UNAUTHORIZED", "Missing, invalid, expired, or revoked tenant key.")
                    .addHeaderObject("WWW-Authenticate", new Header().schema(new StringSchema()
                            .example("Bearer realm=\"mst-social\""))));
            responses.addApiResponse("403", problem(403, "FORBIDDEN", "Authenticated principal lacks the required role."));
            responses.addApiResponse("406", problem(406, "NOT_ACCEPTABLE", "Requested response media type is unsupported."));
            responses.addApiResponse("500", problem(500, "INTERNAL_ERROR", "Unexpected server failure; no internal details are returned."));
            responses.addApiResponse("503", problem(503, "STORAGE_UNAVAILABLE",
                    "STORAGE_UNAVAILABLE or AUTHENTICATION_UNAVAILABLE. Read status before retrying a mutation."));

            String id = operation.getOperationId();
            if (!"getTenantIdentity".equals(id)) {
                responses.addApiResponse("400", problem(400, "INVALID_REQUEST",
                        "INVALID_REQUEST (UUID/JSON), VALIDATION_FAILED (fields), INVALID_HANDLE, "
                                + "UNEXPECTED_BODY, or IDEMPOTENCY_NOT_SUPPORTED."));
                responses.addApiResponse("429", problem(429, "RATE_LIMIT_EXCEEDED",
                        "Tenant rate limit exceeded (defaults per minute: create 10, verify 10, read 120, mutate 30). "
                                + "Creation can also return ACTIVE_REQUEST_LIMIT_REACHED; that error has no Retry-After.")
                        .addHeaderObject("Retry-After", retryAfter()));
            }
            if (!"createVerification".equals(id) && !"getTenantIdentity".equals(id)) {
                boolean account = id.contains("Account");
                responses.addApiResponse("404", problem(404, account ? "ACCOUNT_NOT_FOUND" : "REQUEST_NOT_FOUND",
                        "Resource does not exist or belongs to another tenant; the response is identical."));
            }
            if ("createVerification".equals(id)) {
                responses.get("201").addHeaderObject("Location", new Header()
                        .description("Relative URI of the new verification")
                        .schema(new StringSchema().format("uri-reference")));
                responses.addApiResponse("409", problem(409, "DUPLICATE_ACTIVE_REQUEST",
                        "An active request already exists for this subject, platform, and handle."));
                responses.addApiResponse("415", problem(415, "UNSUPPORTED_MEDIA_TYPE", "Send Content-Type: application/json."));
                responses.addApiResponse("422", problem(422, "UNSUPPORTED_PLATFORM", "Known platform without an enabled provider."));
            }
            if ("verifyProfile".equals(id)) {
                responses.addApiResponse("409", problem(409, "LOCKED",
                        "IN_PROGRESS, STALE_ATTEMPT, ACCOUNT_ALREADY_LINKED, CANCELED, or LOCKED. Includes verification status."));
                responses.addApiResponse("410", problem(410, "EXPIRED", "Request expired; create a new one."));
                responses.addApiResponse("422", problem(422, "CODE_NOT_FOUND",
                        "Code mismatch, or PROVIDER_FAILURE with PROFILE_NOT_FOUND, PROFILE_UNAVAILABLE, or UNSUPPORTED_PLATFORM."));
                responses.addApiResponse("502", problem(502, "PROVIDER_FAILURE",
                        "Provider authentication failed or the provider returned an invalid response."));
                responses.addApiResponse("503", problem(503, "PROVIDER_FAILURE",
                        "Provider rate limited/error, STORAGE_UNAVAILABLE, or AUTHENTICATION_UNAVAILABLE. "
                                + "Provider failures include providerFailure.reason and retryable; they consume no mismatch attempts.")
                        .addHeaderObject("Retry-After", retryAfter()));
                responses.addApiResponse("504", problem(504, "PROVIDER_FAILURE", "Provider timed out; inspect providerFailure.retryable."));
                for (String status : new String[]{"422", "502", "503", "504"}) {
                    responses.get(status).addHeaderObject("Retry-After", retryAfter());
                }
            }
            responses.addApiResponse("default", problem(400, "INVALID_REQUEST",
                    "Other HTTP failures use this problem shape (for example 405 METHOD_NOT_ALLOWED)."));
            responses.values().forEach(response -> response
                    .addHeaderObject("X-Request-Id", new Header()
                            .description("Server-generated correlation UUID; use it to find request logs")
                            .schema(new StringSchema().format("uuid")))
                    .addHeaderObject("Cache-Control", new Header()
                    .description("Responses must not be cached").schema(new StringSchema().example("no-store"))));
        }));
    }

    private static ApiResponse problem(int status, String code, String description) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("type", "about:blank");
        example.put("title", HttpStatus.valueOf(status).getReasonPhrase());
        example.put("status", status);
        example.put("code", code);
        if ("PROVIDER_FAILURE".equals(code)) {
            String reason = switch (status) {
                case 502 -> "INVALID_PROVIDER_RESPONSE";
                case 504 -> "PROVIDER_TIMEOUT";
                default -> "PROVIDER_RATE_LIMITED";
            };
            example.put("detail", "The profile provider could not supply usable profile evidence.");
            example.put("providerFailure", Map.of("reason", reason, "retryable", status != 502));
        }
        return new ApiResponse().description(description).content(new Content()
                .addMediaType("application/problem+json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))
                        .example(example)));
    }

    private static Header retryAfter() {
        return new Header().description("Optional delay in seconds; present for tenant rate limits or a provider-supplied delay")
                .schema(new IntegerSchema().minimum(BigDecimal.ONE));
    }
}
