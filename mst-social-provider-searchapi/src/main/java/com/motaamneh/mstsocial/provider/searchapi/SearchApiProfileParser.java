package com.motaamneh.mstsocial.provider.searchapi;

import com.motaamneh.mstsocial.core.model.ProfileFailureReason;
import com.motaamneh.mstsocial.core.model.ProfileFetchResult;
import com.motaamneh.mstsocial.core.model.ProfileObservation;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Objects;

public class SearchApiProfileParser {
    private final JsonMapper jsonMapper;

    public SearchApiProfileParser(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(
                jsonMapper, "jsonMapper must not be null"
        );
    }

    public ProfileFetchResult parse(
            byte[] responseBody,
            Instant fetchedAt
    ) {
        Objects.requireNonNull(responseBody, "responseBody must not be null");
        Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
        if (responseBody.length == 0) {
            return invalidResponse();
        }

        JsonNode root;
        try {
            root = jsonMapper.readTree(responseBody);
        } catch (JacksonException exception) {
            return invalidResponse();
        }

        if (root == null || !root.isObject()) {
            return invalidResponse();
        }
        JsonNode profile = root.path("profile");
        if (!profile.isObject()) {
            return invalidResponse();
        }

        JsonNode usernameNode = profile.path("username");
        JsonNode biographyNode = profile.path("bio");
        if (!usernameNode.isString() || !biographyNode.isString()) {
            return invalidResponse();
        }

        String username = usernameNode.stringValue();
        String biography = biographyNode.stringValue();
        if (username.isBlank()) {
            return invalidResponse();
        }

        // Empty biographies and private profiles are valid when the required data exists.
        // Search IDs and account/search creation times are not account IDs or observation times.
        ProfileObservation observation = new ProfileObservation(
                username, biography, null, "searchapi", fetchedAt, null
        );
        return new ProfileFetchResult.Found(observation);
    }

    private static ProfileFetchResult.Failed invalidResponse() {
        return new ProfileFetchResult.Failed(
                ProfileFailureReason.INVALID_PROVIDER_RESPONSE, false, null
        );
    }
}
