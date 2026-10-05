# SearchAPI profile provider

Plain Java implementation of the core `ProfileProvider` port for Instagram and
TikTok. X and Facebook return `UNSUPPORTED_PLATFORM` without a network call.

## Components

- `SearchApiSettings`: positive connection timeout, request deadline, and response
  byte limit. Defaults: 5 seconds, 20 seconds, and 1 MiB.
- `SearchApiProfileParser`: reads `profile.username` and `profile.bio`. Empty bios
  and private profiles are accepted when these fields are valid. No verification
  code matching occurs here. Stable account ID and source observation time remain
  null because the example responses do not establish those values.
- `SearchApiProfileProvider`: validates handles, selects the engine, sends Bearer
  authentication to the fixed HTTPS endpoint, and maps responses into core results.
- `LimitedBodySubscriber`: internal per-response byte collector. It cancels receipt
  when the byte budget is exceeded, even without a Content-Length header. Its future
  completes only when the full body is received, allowing a deadline over the entire
  download. The byte limit bounds body payload, not total JVM memory usage.

## Usage

```java
try (var provider = new SearchApiProfileProvider(
        System.getenv("SEARCHAPI_API_KEY"),
        SearchApiSettings.DEFAULT,
        new SearchApiProfileParser(JsonMapper.builder().build()),
        new HandleNormalizer(),
        Clock.systemUTC())) {
    ProfileFetchResult result = provider.fetchProfile(Platform.INSTAGRAM, handle);
    // Pass results through your application; do not log profile biographies.
}
```

Reuse one provider instance for the application's lifetime. The provider owns its
HTTP client; close it at application shutdown, after stopping new calls. No Spring
annotations are needed in this module. Configure the API key outside source control.

## Failure mapping

| Condition | Result | Retryable |
| --- | --- | --- |
| 401 / 403 | PROVIDER_AUTHENTICATION_FAILED | No |
| 429 | PROVIDER_RATE_LIMITED | Yes |
| 408 / 504 or local timeout | PROVIDER_TIMEOUT | Yes |
| Other 5xx / transport failure | PROVIDER_ERROR | Yes |
| Other non-200, including redirects and generic 404 | PROVIDER_ERROR | No |
| Oversized response or invalid JSON/profile | INVALID_PROVIDER_RESPONSE | No |
| Interrupted/canceled call | PROVIDER_ERROR | No |

Positive Retry-After values (seconds or HTTP date) are retained for 429 and other
5xx responses except 504. Malformed, zero, and elapsed delays are ignored.
The adapter does not implement an automatic retry loop. Redirects are disabled.
Timeout/interruption cancels the body subscription and HTTP future. Interruption
also restores the caller thread's interrupt flag.

Null arguments, invalid handles, invalid configuration, and use after shutdown are
caller errors rather than profile mismatches. Expected provider errors do not expose
response bodies or exception messages. A generic 404 or missing profile object is
not mapped to PROFILE_NOT_FOUND without confirmed provider-specific error semantics.

## Compile

From the repository root:

```sh
./mvnw -B -Dmaven.test.skip=true -pl mst-social-provider-searchapi -am compile
```

No tests or live API calls were run during implementation. Provider error-payload
semantics and runtime behavior still need validation before release.

References: [Instagram](https://www.searchapi.io/docs/instagram-profile-api),
[TikTok](https://www.searchapi.io/docs/tiktok-profile-api),
[JDK body subscriber contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpResponse.BodySubscriber.html).
