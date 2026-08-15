# AdGuard Home Android app — session handoff

Continuation context for Claude Code. Previous session ran in a cloud sandbox with **no Android
SDK and no access to Google Maven / Maven Central / Gradle distributions**, so every change below
is hand-verified only — **nothing has been through a compiler.** First job is to build.

## Project

- Root: `C:\Users\Kesav Prasad\Downloads\Project\Rando\adguard home app`
- Spec: `adguard-home-android-prompt.md` in the project root — this is the authoritative
  requirements document, written by the user. Section numbers below refer to it.
- Stack: Kotlin, Jetpack Compose + Material 3, Hilt, Retrofit + OkHttp + kotlinx.serialization,
  DataStore + Tink, Glance widget, QS tile. minSdk 26, compileSdk/targetSdk 35, JVM 17.
- Build: `.\gradlew.bat assembleDebug` → `app\build\outputs\apk\debug\app-debug.apk`
- Target server: self-hosted AdGuard Home on a Raspberry Pi, HTTP Basic auth, LAN only.

## First actions

1. `.\gradlew.bat assembleDebug` and fix any compile errors in the 8 files listed below.
2. Install and confirm **the dashboard now populates** (this was the reported bug — see "Crash
   that was just fixed").
3. Confirm **pause protection** works from the dashboard and the Quick Settings tile. The
   `protection_disabled_duration` fix only takes effect once protection is actually paused, so
   it is untested.

## Crash that was just fixed (most important context)

The dashboard was permanently blank with:

```
Unexpected JSON token at offset 376: Unexpected symbol '.' in numeric literal at path: $.start_time
JSON input: .....bled_duration":0,"start_time":1786713564601.578,"protection_.....
```

**This user's server serializes millisecond time/duration fields as JSON floats**, not integers,
despite the OpenAPI spec typing them as int64. kotlinx.serialization cannot parse `.578` into a
`Long`, and because the failure happens while decoding the response body it destroys the **entire**
`/control/status` call — not just that field. Query Log and Filters kept working because they are
the only screens that never call `/control/status`.

**Carry this forward: any `Long` DTO field holding milliseconds is suspect on this server.** Use
`Double` in the DTO and convert with `.toLong()` at the point of use. Already applied to
`start_time`, `protection_disabled_duration`, and `stats/config.interval`. Counters
(`num_dns_queries` etc.) are Go `uint64` and safe as `Long`.

## Changes made this session (8 files, none compiled)

### Network layer

**`app/src/main/java/com/adguard/home/data/remote/ssl/SslConfig.kt`**
Added `DynamicTrustManager`, `dynamicSslSocketFactory()`, `dynamicHostnameVerifier()`.
Fixes: the "trust self-signed certificate" setting was read **once** via
`runBlocking { credentialStore.serverConfigFlow.firstOrNull() }` while Hilt built the singleton
OkHttpClient. Two bugs — blocking DataStore disk I/O on the main thread (spec §4.10 forbids), and
the decision was frozen for the process lifetime so toggling the setting did nothing until a
force-kill. Now re-read on every TLS handshake (which always runs on an OkHttp background thread).

**`app/src/main/java/com/adguard/home/di/NetworkModule.kt`**
Removed the one-shot `runBlocking` trust read; always installs `DynamicTrustManager` (delegates to
the system default when the setting is off, so HTTP-only setups are unaffected). Added
`RefreshEndpointTimeoutInterceptor` giving `POST /filtering/refresh` a 45 s timeout — spec §4.4
warns it takes 10+ s on large blocklists, but the app-wide read timeout is 10 s (§7.4), so
"Check for updates" could fail spuriously while the server was still working.

### UI

**`app/src/main/java/com/adguard/home/ui/dashboard/DashboardScreen.kt`** (largely rewritten)
- Card order corrected to spec §4.2: D = Top clients, E = Top queried domains, F = Top blocked
  domains. Was rendering queried → blocked → clients with mislabelled comments.
- Upstream cards G/H now hidden when their data is absent (§4.2 requires *hiding*, not "No data
  available"; acceptance criteria require graceful degradation on servers without upstream stats).
- Action row (protection toggle + **Refresh statistics**) pinned above the scroll area. §4.2.A
  requires it pinned; previously it scrolled away and there was **no refresh action at all**.
- "Last updated HH:mm" line added, turns red with "Stale —" when a refresh failed.
  `lastUpdatedFormatted` was already computed in the repository and silently discarded.
- General-statistics rows now pass their `reason` through to the query log. The card was already
  emitting `FilteredBlackList` etc., but the screen called `onNavigateToQueryLog(null)`, so every
  row landed on an unfiltered log (§4.2 C requires pre-filtering).
- Live pause countdown in `ProtectionMasterCard` (§4.2.A). UI-local ticker only — no network.
- Removed `state.data!!` in favour of a single local read.
- Scroll area uses `Modifier.weight(1f)`, **not** `fillMaxSize()` — in a Column the latter measures
  against full height, not remaining height, and pushes content off-screen.

**`app/src/main/java/com/adguard/home/ui/querylog/QueryLogScreen.kt`**
`items` → `itemsIndexed` with the index in the key. The old key
(`rawIsoTime + domain + clientIp`) is **not unique**: a dual-stack client fires A and AAAA for one
domain in the same instant. Duplicate keys make LazyColumn throw.

**`app/src/main/java/com/adguard/home/ui/querylog/QueryLogViewModel.kt`**
Added `reasonsFor()`. The `reason` param accepts multiple values and two chips were
under-selecting: "Allowed" sent only `NotFilteredNotFound` (omitting `NotFilteredWhiteList`, so
queries allowed by the user's own `@@` rules never appeared); "Rewritten" sent only `Rewrite`
(omitting `RewriteEtcHosts`, `RewriteRule`).

### Data layer

**`app/src/main/java/com/adguard/home/data/remote/model/ServerStatusDto.kt`**
`start_time` and `protection_disabled_duration`: `Long?` → `Double?`. See crash section above.

**`app/src/main/java/com/adguard/home/data/remote/model/StatsDto.kt`**
`StatsConfigDto.interval`: `Long` → `Double`. Same class of field; its failure was silent —
swallowed by a try/catch that fell back to a hardcoded 7 days, so every card would claim
"for the last 7 days" regardless of the server's real retention.

**`app/src/main/java/com/adguard/home/data/repository/AdGuardRepository.kt`**
- `pauseRemainingMs` local via `status.protectionDisabledDuration?.toLong()`.
- `config?.interval?.toLong()`.
- Added `catch (e: SerializationException)` → `INVALID_RESPONSE` with a plain-language message.
  Previously a decode failure fell to the generic handler and dumped a slice of raw response JSON
  into a snackbar (visible in the user's screenshot).

## Known remaining gaps (deliberately not fixed — no compiler available)

- **Query log pagination** uses the last row's timestamp as `older_than`; spec §4.3 says use the
  response's `oldest` field. Requires changing `AdGuardRepository.getQueryLog` to return the
  `oldest` value alongside the list, which ripples into `QueryLogViewModel`.
- **Theme setting** (system / light / dark) missing entirely — spec §4.6. Needs a DataStore pref
  plus wiring `AdGuardHomeTheme(darkTheme = …)` through `MainActivity`.
- **Per-row overflow menus** on Top clients / Top domains — spec §4.2 D–F wants "View in query
  log", "Block this client", "Copy IP". Currently only Block/Unblock domain exists, and Top
  clients has no menu. Needs a generic menu in `TopMetricCard`.
- **Custom rules tab**: no warning on back-navigation with unsaved changes (§4.4).
- **About**: shows app version only, not the connected server version (§4.6).
- **`TopDomainItem.isExplicitlyAllowed`** exists in the model but is never set or rendered — spec
  §4.2 E wants an "unblocked" icon on explicitly-allowed domains.
- **Client editing** not implemented; spec §4.5 explicitly permits read-only for v1.

## Spec traps already handled correctly (do not "fix" these)

- `elapsedMs` is a **string** in the query log — parsed with `toDoubleOrNull()`.
- `avg_processing_time` and `top_upstreams_avg_time` are **seconds** — multiplied by 1000 for display.
- `TopArrayEntry` is an open map (`{"example.com": 1234}`) — custom serializer in
  `TopArrayEntrySerializer.kt`.
- `POST /filtering/set_url` needs the **nested** `data` object with all three of `enabled`, `name`,
  `url` populated.
- Bodyless POSTs must **not** carry `Content-Type` — `EmptyBodyContentTypeInterceptor`.
- **Never auto-retry 401 or 429** — spec §6.2; 5 failures locks the user out of AdGuard for 15 min.
- Credentials: DataStore + Tink, Keystore-backed. Never log the password;
  `HttpLoggingInterceptor` is debug-only and redacts `Authorization`. `ServerConfig.toString()` is
  hand-written to redact.
- `dynamicDarkColorScheme` is guarded behind API 31 (minSdk is 26).

## Housekeeping

`_to_delete/src_backup.zip` in the project root is a scratch file from the previous session —
safe to delete.
