package com.motaamneh.mstsocial.core.port;

import com.motaamneh.mstsocial.core.model.Platform;
import com.motaamneh.mstsocial.core.model.ProfileFetchResult;

public interface ProfileProvider {
    boolean supports(Platform platform);
    ProfileFetchResult fetchProfile(Platform platform, String normalizedHandle);

}



