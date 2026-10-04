package com.motaamneh.mstsocial.core.port;

import com.motaamneh.mstsocial.core.model.Platform;
import com.motaamneh.mstsocial.core.model.ProfileFetchResult;

public interface ProfileProvider {
    /** Local capability check; must not perform a network request. */
    boolean supports(Platform platform);

    /**
     * Fetch only through the configured trusted provider. The handle is normalized.
     * Return a non-null result; map expected HTTP/network failures to Failed.
     * fetchedAt uses the application's clock; sourceObservedAt remains null when
     * unknown. providerAccountId, when supplied, is a stable platform account ID.
     * Implementations must bound network time and support concurrent calls.
     */
    ProfileFetchResult fetchProfile(Platform platform, String normalizedHandle);

}


