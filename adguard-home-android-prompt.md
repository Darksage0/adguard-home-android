## 0\. Role

You are a senior Android engineer. You write production-quality Kotlin using Jetpack
Compose and modern Android architecture. You do not write placeholder code, `TODO()`
stubs, or pseudo-code — every file you produce compiles as written. When you are
uncertain about an external API's exact shape, you say so explicitly rather than
inventing a field name.

\---

## 1\. What I'm building

A native Android app that acts as a remote control and dashboard for a self-hosted
**AdGuard Home** instance running on a Raspberry Pi on my home LAN.

My setup:

* AdGuard Home base URL: `http://\[PI\_IP]:3000`
* Auth: HTTP Basic — username `\[USERNAME]`, password entered by me at setup
* Reachable only on my home Wi-Fi (no VPN, no public exposure)
* AdGuard Home version: `\[e.g. v0.107.x — check your admin panel footer]`

The app is for me alone. No multi-user support, no accounts, no analytics, no crash
reporting SDKs, no ads. Optimise for "glanceable status + two-tap actions", not for
feature completeness with the web UI.

\---

## 2\. Tech stack — use exactly this

|Concern|Choice|
|-|-|
|Language|Kotlin, latest stable|
|UI|Jetpack Compose + Material 3|
|Min SDK|26|
|Target / compile SDK|target 36 (Android 16); compileSdk 36 or higher|
|Architecture|Single-activity, MVVM, unidirectional data flow|
|Async|Coroutines + Flow (`StateFlow` for UI state)|
|DI|Hilt|
|Networking|Retrofit + OkHttp + kotlinx.serialization|
|Settings storage|DataStore (Preferences)|
|Credential storage|Android Keystore-backed — **not** `EncryptedSharedPreferences`, see §4.10|
|Background work|WorkManager (only if needed for widget refresh)|
|Navigation|Navigation Compose|
|Build|Gradle Kotlin DSL, version catalog (`libs.versions.toml`)|

**Do not pin library versions from memory.** Use the current stable release of each
dependency and tell me the exact versions you chose in Phase 1, so I can check them.
If a recent Compose BOM forces a higher `compileSdk` or a minimum AGP version, say so
rather than silently picking an older BOM.

Two things that follow from minSdk 26:

* **Material You dynamic colour requires API 31+.** Guard it and fall back to a defined
colour scheme on older devices — don't call `dynamicDarkColorScheme` unconditionally.
* Check any library's own minSdk before adding it.

No RxJava, no Dagger-without-Hilt, no XML layouts, no Room unless a screen genuinely
needs local persistence beyond settings.

\---

## 3\. Architecture

Use a layered architecture with a clear separation between networking, data, and UI.
The specific package structure, file layout, and class names are **your call** — pick
whatever is idiomatic for a modern Kotlin/Compose app and keep it consistent. I care
about the boundaries, not the names.

The layers I want kept distinct:

* **Remote** — the Retrofit interface, request/response models, and OkHttp interceptors.
* **Local** — persisted settings, and the credential store (see §4.10).
* **Repository** — the single source of truth the UI talks to. It owns the decision
about what to fetch and returns results the UI can render without further work.
* **Domain models** — clean types the UI consumes. Compose never sees a raw network
response model.
* **UI** — one package or module per screen, each pairing its composable with its
state holder.
* **Widget and Quick Settings tile** — kept separate from the screen code.

**Navigation.** Four bottom-nav items — **Dashboard, Query Log, Filters, Clients** —
with **Settings** behind a top-app-bar overflow menu, and the server connection screen
reached from within Settings.

**No background work, no polling.** Beyond the widget's own scheduled update, this app
makes network calls only in direct response to something I do. No auto-refresh timers,
no observers ticking in the background, no foreground services.

Rules that are about behaviour rather than naming, so these I do care about:

* Each screen's state holder exposes a single observable state object rather than
several independent streams the UI has to combine. Use `StateFlow`, not `LiveData`.
* That state object should make loading, refreshing, error, and stale-data states
explicit rather than leaving the UI to infer them from nulls.
* The repository maps network models to domain models.
* Network calls surface failure as a value, not a thrown exception. No exceptions
escaping to the UI layer.
* The base URL is runtime-configurable — it is **not** a compile-time constant. Use a
dynamic host in an OkHttp interceptor or a rebuildable Retrofit instance.

\---

## 4\. Screens and features

### 4.1 Setup (first launch)

Uses the shared connection form specified in §4.9, minus the Back action and Sign out
button. On a successful test, persist credentials per §4.10 and navigate to Dashboard.
On subsequent launches, skip straight to Dashboard if credentials exist — don't
re-validate before showing the UI, just let the dashboard's own error state handle an
unreachable server.

### 4.2 Dashboard (home) — this is the priority screen

The dashboard must mirror the AdGuard Home web dashboard, section for section, in the
same order. The web version is a two-column desktop grid; on a phone this becomes a
**single vertically scrolling column** of Material 3 cards. Everything below comes from
`GET /control/stats` unless noted.

**The stats window is server-configured** — mine is 7 days. Read it from
`GET /control/stats/config`, which returns `enabled`, `interval`, `ignored` and
`ignored\_enabled`. Note that this `interval` is **in milliseconds** (the older,
deprecated `/control/stats\_info` returned days as an enum of 0/1/7/30/90 — don't confuse
the two). Convert it to a human period and show it as subtitle text on each card, e.g.
"for the last 7 days". Don't hardcode "24h".

**A. Action row (pinned at top, not scrolling)**

* Protection toggle. Tapping to disable opens a bottom sheet with durations: 30s, 1m,
5m, 15m, 1h, 8h, "until I turn it back on". When paused, show a live countdown and a
"resume now" button.
* "Refresh statistics" action — refreshes every card at once.

**B. Four headline stat cards**

A 2×2 grid of compact cards on phones, a single row on tablets. Each card has: a large
number, a percentage in the top-right where applicable, a **sparkline** across the
bottom, and a label underneath. Sparkline colour matches the metric.

|Card|Value|Percentage|Sparkline colour|
|-|-|-|-|
|DNS Queries|`num\_dns\_queries`|—|neutral / grey|
|Blocked by Filters|`num\_blocked\_filtering`|% of total|orange-red|
|Blocked malware/phishing|`num\_replaced\_safebrowsing`|% of total|orange|
|Blocked adult websites|`num\_replaced\_parental`|% of total|purple|

The sparklines come from the parallel time-series arrays (`dns\_queries`,
`blocked\_filtering`, `replaced\_safebrowsing`, `replaced\_parental`) — one point per time
unit, with `time\_units` telling you whether points are hours or days. Long-pressing or
dragging on a sparkline shows a tooltip with that point's value and timestamp, matching
the web UI's hover behaviour. Cards showing zero should still render a flat baseline,
not an empty box.

**C. General statistics card**

A titled card with a subtitle showing the period, a refresh icon in the top-right, and
six rows — label on the left, value right-aligned:

1. DNS Queries
2. Blocked by Filters
3. Blocked malware/phishing
4. Blocked adult websites
5. Enforced safe search — `num\_replaced\_safesearch`
6. Average processing time — `avg\_processing\_time`

**Unit trap on row 6:** the API returns `avg\_processing\_time` **in seconds** (the spec
describes it as "average time in seconds", with an example value of `0.34`), but the
web UI displays milliseconds — my dashboard reads "55 ms". Multiply by 1000 before
display. Get this wrong and the card shows "0 ms" for every server, which looks
plausible enough that neither of us would notice.

Row labels get a small "?" info affordance that opens a tooltip explaining the metric.
Rows 1–5 are tappable and navigate to the query log pre-filtered to that category.
Row 6 is not tappable.

**D. Top clients card**

Header row: "Client" / "Requests count". Then up to 5 rows, each with:

* Client IP (or name, if AdGuard has one configured for it)
* Request count and percentage of total
* A horizontal progress bar underneath, width = percentage, colour-graded by magnitude
* An overflow (⋮) menu per row with: "View in query log", "Block this client",
"Copy IP"

Scrollable within the card if more than 5, matching the web UI's inner scroll.

**E. Top queried domains card**

Same structure as Top clients — domain, count, percentage, progress bar. Domains that
are currently allowed by an explicit rule show a small "unblocked" icon next to them,
as in the web UI. Each row's overflow menu offers "Block this domain" and
"View in query log".

**F. Top blocked domains card**

Identical structure to E, but the per-row action is "Unblock this domain" and the
progress bars are red-toned.

**G. Top upstreams card**

Header: "Upstream" / "Requests count". Rows show the upstream address (which may be a
long DoH URL — ellipsize in the middle, not the end, so the host stays readable),
request count, percentage, and a green-toned progress bar. Source field:
`top\_upstreams\_responses`.

**H. Average upstream response time card**

Identical row structure to G, but showing upstream address and response time, no
progress bar. Source field: `top\_upstreams\_avg\_time`. **Same unit trap as the General
statistics card — these values are in seconds and must be multiplied by 1000** to match
the web UI, which shows mine as 517 ms, 5 ms, 1 ms and 1 ms. Sort ascending so the
fastest resolver is at the top.

**Refresh behaviour — get this right, it's how the app stays current**

There is **no auto-refresh and no background polling anywhere in this app.** Data
updates when I ask it to. That makes the refresh path the most important interaction in
the app, so it has to be completely dependable:

* **The "Refresh statistics" toolbar action refreshes every card on the screen** in one
pass. All cards come from a single `GET /control/stats` call plus `GET /control/status`
— issue both concurrently, then update all state at once so the screen doesn't
repaint piecemeal.
* **Pull-to-refresh** does exactly the same thing as the toolbar action. Same code path,
no divergence.
* **Per-card refresh icons** (as in the web UI) refresh that card only.
* The screen also refreshes **once automatically on first load and on returning to it**
(`ON\_RESUME`), so I never look at stale numbers after coming back from another screen.
That is the only non-manual refresh permitted.
* While a refresh runs: show a determinate-looking progress indicator in the toolbar and
keep the existing data visible and readable. Do **not** blank the cards, do not
replace them with skeletons on a manual refresh, and do not disable the screen.
Skeletons are for first load only.
* On success, if anything changed, the update should be visually obvious but not
jarring — animate number changes rather than snapping.
* On failure, keep the old data on screen, show a snackbar with the reason and a Retry
action, and display a subtle "last updated HH:mm" line so I can tell the data is
stale. Never wipe good data because a refresh failed.
* Refresh must be **idempotent and non-overlapping**: if I tap the button five times
quickly, that's one in-flight request, not five. Cancel-and-restart or ignore-while-
running are both fine — pick one and say which.
* Refreshing must never throw away my scroll position.
* Skeleton/shimmer placeholders on first load only — not a full-screen spinner.
* If a card's underlying field is missing from the server response (older AdGuard
version), hide that card rather than showing zeros or crashing. This applies
especially to the two upstream cards.

**This refresh contract applies to every screen in the app** — Query log, Filters,
Clients. Each gets a refresh action and pull-to-refresh, both following the rules
above. Implement it once as a shared pattern — a common state shape that carries
refreshing, last-updated and error information, plus a repository that de-duplicates
in-flight calls — rather than reimplementing it per screen. Name and structure that
however you like.

### 4.3 Query log

Paginated list from `GET /control/querylog`. The response is
`{ "oldest": "...", "data": \[ ... ] }`, newest first.

**Pagination.** The endpoint accepts `older\_than`, `offset` and `limit`. The spec
explicitly recommends picking either `older\_than` or `offset` and sticking to it — do
not mix them. Use `older\_than` with the `oldest` value from the previous page, and say
in a comment which you chose.

**Filtering.** Use the **`reason`** query parameter, which accepts multiple values.
The older `response\_status` parameter is deprecated and **cannot be combined with
`reason`** — using both is an error, so pick `reason` and never send the other. Valid
`reason` values are exactly:

`NotFilteredNotFound`, `NotFilteredWhiteList`, `NotFilteredError`, `FilteredBlackList`,
`FilteredSafeBrowsing`, `FilteredParental`, `FilteredInvalid`, `FilteredSafeSearch`,
`FilteredBlockedService`, `Rewrite`, `RewriteEtcHosts`, `RewriteRule`.

Map these to the UI's colour coding — the `Filtered\*` values are blocks, the
`NotFiltered\*` values are allows, and the `Rewrite\*` values are rewrites.

**Field-level traps in `QueryLogItem`, all verified against the spec:**

* **`elapsedMs` is a `string`, not a number** (example: `"54.023928"`). Parse it, and
handle a parse failure without crashing the row.
* **`filterId` is camelCase**, unlike every other snake\_case field. It's deprecated
anyway — use `rules\[\*].filter\_list\_id` and `rules\[\*].text` instead of the deprecated
`filterId` / `rule` pair.
* `question.name` holds the domain — note it's `name`, not `host`, which the docs
previously got wrong. `question.unicode\_name` appears only for internationalised
domains.
* `client\_info` is optional and, when present, requires `disallowed`, `disallowed\_rule`,
`name` and `whois`. Prefer `client\_info.name` over the raw `client` IP for display.
* `time` and `oldest` are ISO-8601 strings with offsets, e.g.
`2018-11-26T00:02:41+03:00`. Parse with `OffsetDateTime`, not a naive formatter.
* `service\_name` is populated only when `reason` is `FilteredBlockedService`.
* `cached` tells you the response came from cache — worth a small badge.

**`filter\_list\_id` values worth decoding** rather than showing as raw integers:
`0` custom rules, `-1` /etc/hosts, `-2` blocked services, `-3` parental,
`-4` safe browsing, `-5` safe search. Anything positive is a subscribed blocklist —
resolve it to a name via the `id` field on filters from `/control/filtering/status`.

**UI:**

* Each row: time, client, domain, query type, and colour-coded status.
* Search box wired to the `search` param (filters by domain or client IP, server-side)
and a filter chip row driving `reason`.
* Tap a row → detail sheet showing the full record plus two actions:
**Block this domain** and **Unblock this domain**, which append
`||domain^` or `@@||domain^` to user rules.

### 4.4 Filters — three tabs

A top tab row: **Blocklists**, **Allowlists**, **Custom rules**. The first two are the
same screen with a `whitelist` boolean flipped — build one composable and one state
holder parameterised by that flag, don't duplicate the code.

#### Blocklists / Allowlists tab

The web UI is a table with columns Enabled / Name / List URL / Rules count / Last time
updated / Actions. On a phone this becomes a **list of cards**, one per blocklist:

* **Line 1** — the list's name, bold, ellipsized at one line
(e.g. "HaGeZi Multi PRO (GitLab mirror)")
* **Line 2** — the source URL, one line, smaller and dimmed, **ellipsized in the middle**
so both host and filename stay visible
* **Line 3** — rule count formatted with thousands separators (e.g. "266,279 rules"),
then a separator dot, then the last-updated time as a **relative** string
("updated 2 hours ago"). Long-press or tap reveals the absolute timestamp.
* **Trailing** — a Material 3 `Switch` bound to the list's enabled state
* **Overflow (⋮) menu** — Edit, Copy URL, Open URL in browser, Delete

Behaviours:

* **Toggle**: flips optimistically in the UI, calls the API, and reverts with a snackbar
if the call fails. Don't block the switch behind a spinner.
* **Edit**: opens a dialog pre-filled with the current name and URL, both editable.
Validate that the URL is well-formed and non-empty before enabling Save.
* **Delete**: confirmation dialog naming the list, then delete with an "Undo" snackbar
that re-adds it if tapped within the snackbar's lifetime.
* **Add** (FAB): dialog with Name and URL fields. Trim whitespace on paste — URLs copied
from a browser routinely carry a trailing newline. Reject a URL already in the list
with an inline error rather than letting the server 400.
* **Check for updates**: toolbar action calling the refresh endpoint. Show progress
while it runs, then a snackbar reporting how many lists were actually updated (the
endpoint returns that count). This can take 10+ seconds on large lists — use a
generous timeout for this one call specifically, overriding the short LAN default
in §6.
* Pull-to-refresh re-reads filtering status.
* **No pagination.** The web UI paginates at 10 rows; a phone should just scroll.
* Empty state: an illustration plus "No blocklists yet" and a button to add one.
* Show an aggregate summary at the top of the tab: total lists, how many enabled, and
the combined rule count across enabled lists.

#### Custom rules tab

* A monospace, multi-line text editor holding the user rules, one per line.
* Line numbers in a gutter if that's not disproportionate effort; skip if it is.
* A Save action in the toolbar, enabled only when the text differs from what was
loaded. Warn on back-navigation with unsaved changes.
* Below the editor, a small helper card noting that AdGuard Home understands adblock
syntax and hosts-file syntax, with two tappable examples that insert a template:
`||example.com^` to block and `@@||example.com^` to allow.
* The "Block/Unblock this domain" actions from the dashboard and query log append to
these rules — make sure they read current rules, append, and write back the full list
rather than clobbering it. This is the single easiest place in the app to destroy my
configuration, so be careful here.

#### Filter update interval

A setting on this screen (or in Settings) for how often AdGuard auto-updates lists, sent
via `POST /filtering/config` as `{enabled, interval}`. The spec types `interval` as a
plain integer of hours, and as of v0.107.78 it accepts **any integer from 0 to 8760**
(365 days), where `0` disables auto-updating. Older versions accepted only the enum
0/1/12/24/72/168. Offer those familiar presets in the UI — off, 1 hour, 12 hours, daily,
every 3 days, weekly — since arbitrary values gain nothing here.

### 4.5 Clients

* List of configured clients and auto-discovered clients, with name, IP/MAC, and
whether filtering is enabled for them.
* Tap → detail showing per-client settings (blocked services, filtering toggles).
* Editing clients is optional for v1 — read-only is acceptable if it keeps the
scope tight. Tell me if you're skipping it.

### 4.6 Settings

* **Server connection** — a row that opens the dedicated screen in §4.9.
* Theme: system / light / dark.
* Trust self-signed certificate (see §7), default off.
* Require biometric unlock to view credentials, default off.
* About: app version, connected server version.

### 4.7 Home-screen widget (Glance)

Small widget showing: protection state, today's blocked count, block percentage.
Tapping the shield toggles protection with a 5-minute pause default. Refresh every
15 minutes via WorkManager, plus on tap.

### 4.8 Quick Settings tile

A tile that toggles protection. Label reflects current state. This is the feature I'll
use most — make sure it works without opening the app.

\---

### 4.9 Server connection — its own screen

A dedicated screen where I point the app at my AdGuard Home instance and manage
credentials. Reachable from the overflow menu, and it's the **same composable** used by
the first-launch setup flow (§4.1) — one form, two entry points, differing only in
whether a Back action and a "Sign out" button are shown.

**Fields**

|Field|Type|Default|Validation|
|-|-|-|-|
|Protocol|segmented toggle, HTTP / HTTPS|HTTP|—|
|Host|text, `KeyboardType.Uri`, no autocorrect, no autocapitalise|empty|non-empty; accepts IPv4, IPv6 in brackets, or a hostname like `pi.local`|
|Port|text, `KeyboardType.Number`|3000|1–65535|
|Username|text, no autocapitalise|empty|non-empty|
|Password|text, `PasswordVisualTransformation`, with a visibility toggle|empty|non-empty|

Trim whitespace on every field. Strip a leading `http://` or `https://` and any trailing
slash or path if I paste a full URL into the Host field, and set the protocol toggle
accordingly rather than erroring — pasting the browser URL is the obvious thing to do
and the app should just handle it.

Below the fields, show a **live preview of the resolved base URL** in monospace
(`http://192.168.0.110:3000`) so I can sanity-check it before saving.

**Test connection**

A button that calls `GET /control/status` with the values currently in the form —
*not* the saved ones. Report the outcome with a specific, distinguishable message:

* Success → green state showing the AdGuard Home version and whether protection is
currently on, e.g. "Connected — AdGuard Home v0.107.x, protection on"
* Host unreachable / timeout → "Couldn't reach that address. Check the IP and that
you're on your home Wi-Fi."
* 401 → "Server found, but that username or password was rejected."
* 200 but the response doesn't parse as AdGuard Home → "Something's answering on that
port, but it isn't AdGuard Home."
* TLS failure on HTTPS → "Certificate rejected — enable 'trust self-signed certificate'
below if this is your own certificate."

Save is enabled only after a successful test, or via a "save anyway" secondary action.

**Status card at the top of the screen**

When already configured: current base URL, connection state (connected / unreachable),
server version, and time since last successful contact.

**Sign out**

Clears the stored password, the username, and the host details, wipes the Keystore key
alias, cancels any scheduled widget work, and returns to setup. Confirmation dialog
first — this is destructive.

Out of scope for v1: multiple saved server profiles. One server, mine.

### 4.10 Credential storage — security requirements

The password authenticates against a service on my home network. It must never be
recoverable from the device by anything other than the app.

**Storage.** Encrypt at rest with a key held in the **Android Keystore**, so the key
material never enters app memory in extractable form.

**Do not use `EncryptedSharedPreferences`.** I checked: the
`androidx.security:security-crypto` library was deprecated in April 2025 at
`1.1.0-alpha07`, taking `EncryptedSharedPreferences` and `EncryptedFile` with it. It
never reached a stable 1.1.0, it had documented problems with main-thread I/O and with
Keystore keyset corruption on some OEM devices, and it is not receiving updates to its
transitive Tink dependency. Don't build a new app on it in 2026.

Use one of these instead, and tell me which and why:

1. **DataStore + Tink** — DataStore holds the ciphertext, Tink handles encryption with
a Keystore-backed key. This is the combination Google's own migration guidance points
at, and it keeps I/O off the main thread.
2. **DataStore + hand-rolled AES-256-GCM** against a `SecretKey` generated in the
Android Keystore, with a fresh random 12-byte IV per write stored alongside the
ciphertext. Fewer dependencies, more code you have to get right.

Prefer option 1 unless you have a specific reason. If you do reach for a third option,
justify it.

**Non-negotiable rules, all of which I will check:**

* The password is **never** written to Logcat, at any log level, in any build variant.
Redact it in every `toString()` — implement those manually on any data class holding
it rather than relying on the generated one.
* If OkHttp's `HttpLoggingInterceptor` is included at all, it must call
`redactHeader("Authorization")`, and it must be gated to debug builds only.
* `android:allowBackup="false"` in the manifest, **and** explicit backup rules
excluding the credential store, so the password can never leave the device via cloud
backup or a device-to-device transfer.
* Nothing credential-related in `SharedPreferences` plaintext, in a file under
`getExternalFilesDir()`, or in a Room table.
* Hold the plaintext password in memory only for as long as it takes to build the
`Authorization` header. Don't cache it in a long-lived state holder field or a
companion object.
* The Basic auth header is constructed in an OkHttp interceptor that reads from the
credential store on demand, so a credential change takes effect on the next request
with no Retrofit rebuild and no app restart.
* `android:excludeFromRecents` is not needed, but do set `FLAG\_SECURE` on the server
connection screen so the password field doesn't appear in the app-switcher snapshot.

**Optional, offer it as a setting:** require biometric or device-credential
authentication before the server connection screen will reveal or edit the stored
password. Default off.

\---

## 5\. AdGuard Home API reference — verified

I have read the OpenAPI specification (v0.107.78, `openapi: 3.0.3`, spec `version: 0.107`). The following is transcribed from it, not from memory. Base path is
`/control/`. The declared security scheme is `basicAuth` (HTTP Basic), applied globally.

Still verify against **my** version from §1 before implementing — but treat the table
below as accurate for a current 0.107.x server, and §6 as the list of things that will
bite you.

### Endpoints

|Purpose|Method + path|Request / response|
|-|-|-|
|Server status|`GET /status`|`ServerStatus` — see fields below|
|DNS settings|`GET /dns\_info`|`DNSConfig` + `default\_local\_ptr\_upstreams`|
|Set protection|`POST /protection`|`{"enabled": bool, "duration": ms}` — `enabled` required; `duration` is the pause length and `enabled` should be `false` when using it|
|Statistics|`GET /stats`|`Stats`; optional `recent` param|
|Stats config|`GET /stats/config`|`{enabled, interval, ignored, ignored\_enabled}`|
|Query log|`GET /querylog`|params `older\_than`, `offset`, `limit`, `search`, `reason\[]`; returns `{oldest, data\[]}`|
|Query log config|`GET /querylog/config`|`{enabled, interval, anonymize\_client\_ip, ignored, ignored\_enabled}`|
|Filtering status|`GET /filtering/status`|`{enabled, interval, filters\[], whitelist\_filters\[], user\_rules\[]}`|
|Add filter|`POST /filtering/add\_url`|`{name, url, whitelist}`|
|Remove filter|`POST /filtering/remove\_url`|`{url, whitelist}`|
|Edit / toggle filter|`POST /filtering/set\_url`|`FilterSetUrl` — nested, see below|
|Refresh filters|`POST /filtering/refresh`|`{whitelist}` → `{updated: int}`|
|Filtering config|`POST /filtering/config`|`{enabled, interval}` — `interval` is an integer|
|Set user rules|`POST /filtering/set\_rules`|`{"rules": \["|
|Check a host|`GET /filtering/check\_host`|params `name` (required), `client`, `qtype`|
|Clients|`GET /clients`|`{clients\[], auto\_clients\[], supported\_tags\[]}`|
|Search clients|`POST /clients/search`|`{"clients": \[{"id": "192.0.2.1"}]}`|
|Blocked services (available)|`GET /blocked\_services/all`|`{blocked\_services\[], groups\[]}`|
|Blocked services (current)|`GET /blocked\_services/get`|`{schedule, ids\[]}`|
|Update blocked services|`PUT /blocked\_services/update`|`{schedule, ids\[]}`|
|Login (fallback auth)|`POST /login`|`{"name": "...", "password": "..."}` — note the field is **`name`**, not `username`|

### `ServerStatus` (`GET /status`)

Required: `dns\_addresses`, `dns\_port`, `http\_port`, `protection\_enabled`, `running`,
`version`, `language`. Optional: `protection\_disabled\_duration` (int64 ms),
`dhcp\_available`, `start\_time` (Unix ms).

**A genuine inconsistency in the spec:** the `required` list names
`protection\_disabled\_until`, but the `properties` block defines
`protection\_disabled\_duration` and no `protection\_disabled\_until`. (The latter does
exist, but on `DNSConfig` via `GET /dns\_info`, as a nullable timestamp string.) Treat
**both** as optional and nullable, and drive the pause countdown from whichever the
server actually sends.

### `Stats` (`GET /stats`)

Scalars: `num\_dns\_queries`, `num\_blocked\_filtering`, `num\_replaced\_safebrowsing`,
`num\_replaced\_safesearch`, `num\_replaced\_parental`, `avg\_processing\_time`
(float, **seconds**). `time\_units` is `"hours"` or `"days"`.

Equal-length integer time series: `dns\_queries`, `blocked\_filtering`,
`replaced\_safebrowsing`, `replaced\_parental`. **There is no `replaced\_safesearch` time
series** — safe-search has a scalar but no sparkline data, so don't try to chart it.

Top-N arrays of `TopArrayEntry`: `top\_queried\_domains`, `top\_clients`,
`top\_blocked\_domains`, `top\_upstreams\_responses`, `top\_upstreams\_avg\_time` (the last two
capped at 100 items).

**`TopArrayEntry` is an open map, not a record.** The schema is an object with
`additionalProperties: number` — so each entry is `{"example.com": 1234}`, one dynamic
key per object. Write a custom kotlinx.serialization deserializer that flattens these
into a simple name-and-value pair type of your own design. The values in
`top\_upstreams\_avg\_time` are **seconds**, not counts.

The `recent` parameter is a lookback in milliseconds, and the spec constrains it: it
must be a multiple of one hour and must not exceed the configured `statistics.interval`.
Violating either returns **400**. If you use it, validate before sending.

### `FilterSetUrl` (`POST /filtering/set\_url`) — confirmed nested

```json
{
  "url": "<the URL as it exists on the server right now>",
  "whitelist": false,
  "data": { "enabled": true, "name": "New name", "url": "<new or unchanged URL>" }
}
```

`data` is a `FilterSetUrlData` with **all three of `enabled`, `name` and `url`
required** — you cannot send a partial update, so always populate all three from current
state even when only toggling `enabled`. A flat body will not work.

### `Filter`

Required: `enabled`, `id` (int64), `name`, `rules\_count` (uint32), `url`. Optional:
`last\_updated` (date-time). The `id` identifies a filter for display purposes, but
`set\_url` / `remove\_url` key off the **URL**, not the id.

### Auth

Send `Authorization: Basic <base64 of username:password>` on every request. The spec
declares this as the global security scheme, so it is the documented approach — but read
§6.2 before assuming it will always hold.

\---

## 6\. Known limitations — read this before scoping anything

I have researched the AdGuard Home API. The following is verified from the project's
own `openapi/CHANGELOG.md` and issue tracker, and it constrains what this app can
honestly be. Do not design around capabilities that aren't in this list.

### 6.1 There is no stable API. Treat every endpoint as version-specific.

AdGuard Home describes its own interface as a "REST-ish API" whose stated purpose is
backing its web UI — it is not a published integration surface. The OpenAPI document
has been stuck at version `0.107` across dozens of releases, there is no `v1`, and when
the team removed the experimental beta install APIs they noted that a future version of
the API "will probably be different."

Practical consequences you must design for:

* **Pin to my version.** I gave it in §1. Verify each endpoint against that version's
spec, not against master.
* **Every DTO field must be nullable or defaulted.** A missing field must degrade the
UI, never crash it. This is not defensive over-engineering; fields genuinely appear
and disappear between point releases.
* **Never fail a whole screen because one field is absent.** Parse leniently, ignore
unknown keys (`ignoreUnknownKeys = true` in the kotlinx Json config), and hide what
you can't populate.

### 6.2 Authentication: documented, but historically fragile

Basic auth **is** the officially declared scheme — the spec sets
`security: \[{basicAuth: \[]}]` globally and defines it as `type: http, scheme: basic`.
So implement Basic auth; it's the right choice, not a hack.

That said, `POST /login` exists alongside it and issues a session cookie, and the web UI
uses that path. Two things follow:

* In v0.107.65, `/control/profile` and `/control/tls/status` began returning 401 to
correctly-formed Basic auth headers. It was filed as a P2 bug and fixed in v0.107.66.
Assume this class of regression can recur on endpoints the web UI doesn't exercise
via Basic auth.
* Keep the auth mechanism behind a single swappable abstraction, so a cookie-session
implementation could replace Basic auth without rewriting the repository. If some
endpoints 401 while others succeed, that's this bug class — surface it clearly rather
than bouncing me to the login screen.

**Login rate limiting is the single most likely way this app ruins my evening.**
The spec documents `429 Out of login attempts` on `POST /login`, and AdGuard Home
defaults to `auth\_attempts: 5` with `block\_auth\_min: 15` — five failures and you're
blocked for **15 minutes**, with the server reporting the remaining time. Therefore:

* **Never automatically retry a request that returned 401 or 429.** Not once, not with
backoff. A retry loop against a mistyped password will lock me out of my own DNS
server's admin panel.
* On 429, read `Retry-After` if present, fall back to the message body, and show
something specific: "Too many failed attempts. Locked out for another 12 minutes."
Disable the Test and Save buttons until it elapses, with a visible countdown.
* The "Test connection" button must be debounced and single-flight — one test at a
time, no rapid re-firing.
* Note the login body field is **`name`**, not `username`.

### 6.3 CSRF protections make Retrofit's defaults wrong

Two breaking changes make request framing unusually strict, and OkHttp's defaults
violate both:

1. **v0.107.14** — every JSON API expecting a body now *requires*
`Content-Type: application/json`. Requests without it are rejected.
2. **v0.107.15** — state-changing POSTs that have **no body** must **not** have a
`Content-Type` header at all.

Rule 2 is the trap. OkHttp attaches a `Content-Type` to an empty `RequestBody` by
default, so the natural Retrofit signature for a bodyless POST produces a request
AdGuard Home refuses. Handle it explicitly — a `@Headers`-stripping interceptor, or
`RequestBody.create(null, ByteArray(0))` — and add a comment explaining why, so nobody
"fixes" it later.

### 6.4 Deprecated endpoints and fields — never use these

Verified from the spec, where each is explicitly marked `deprecated: true`:

|Deprecated|Use instead|
|-|-|
|`response\_status` param on `/querylog`|`reason` param (and never both together)|
|`GET /querylog\_info`|`GET /querylog/config`|
|`POST /querylog\_config`|`PUT /querylog/config/update`|
|`GET /stats\_info`|`GET /stats/config`|
|`POST /stats\_config`|`PUT /stats/config/update`|
|`GET /clients/find`|`POST /clients/search`|
|`GET /blocked\_services/list`|`GET /blocked\_services/get`|
|`POST /blocked\_services/set`|`PUT /blocked\_services/update`|
|`GET /blocked\_services/services`|`GET /blocked\_services/all`|
|`POST /safesearch/enable` and `/disable`|`PUT /safesearch/settings`|
|`GET /i18n/current\_language`|`GET /profile`|
|`POST /i18n/change\_language`|`PUT /profile/update`|
|`filterId` and `rule` on query log items|`rules\[\*].filter\_list\_id` and `rules\[\*].text`|
|`filter\_id` and `rule` on check-host results|`rules\[\*].filter\_list\_id` and `rules\[\*].text`|
|`safesearch\_enabled` on clients|the `safe\_search` object|
|`upstream\_mode: ""`|`upstream\_mode: "load\_balance"`|

Note the two config endpoints above are **`PUT`**, not `POST`. Getting the verb wrong
is an easy way to spend an hour on a 404.

### 6.5 Functional ceilings — things the app simply cannot do

State these back to me so I know you've registered them:

* **No push, no webhooks, no streaming.** AdGuard Home cannot notify a client of
anything. Every update is a poll. This is why the app is manual-refresh only, and why
notifications like "your Pi went down" or "a new client appeared" are impossible
without a background polling service I've explicitly ruled out.
* **Statistics are bucketed by hour or day only** — `time\_units` is `"hours"` or
`"days"`. There is no minute-level granularity, so sparklines will be coarse. Don't
promise a smooth real-time graph.
* **Statistics and query logging can be switched off server-side.** `GET /stats/config`
and `GET /querylog/config` each return an `enabled` flag and an `interval` (in
milliseconds) where the underlying retention can be set to 0 to disable. Clients can
also carry `ignore\_querylog` / `ignore\_statistics`, and both configs support an
`ignored` host list gated by `ignored\_enabled`. Empty or partial data is a legitimate
server state, not a bug — detect it and explain it rather than showing zeros.
* **Query log retention is finite**, rotating on the configured interval (historically
0.25/1/7/30/90 days). Don't build features that assume arbitrary history.
* **`POST /filtering/refresh` is rate-limited server-side.** The spec says so explicitly
and notes it can be called freely as a result — but it also means a refresh may return
without having actually re-fetched anything. Report the `updated` count honestly
rather than claiming success.
* **Nothing works off the LAN.** No remote access, no relay. Away from home the app is
a static shell. Design every screen's error state accordingly.
* **Out of scope, do not implement:** `POST /control/update` (updates AdGuard Home
itself — too dangerous to trigger from a phone), DHCP configuration, TLS
configuration, and the install wizard APIs.

### 6.6 What I expect to actually work

So we're calibrated, here is my honest expectation of this build. Tell me now if you
disagree with any of it.

**Should work reliably** — these are stable, long-lived endpoints:
protection toggle with timed pause, dashboard statistics, query log browsing and
search, blocklist CRUD and refresh, custom rules editing, client listing.

**Expect friction, budget time for it** — the bodyless-POST content-type rule, the
nested `set\_url` payload, the awkward `top\_\*` array shape, and any endpoint whose name
changed in the table above. I expect at least one round of "this 400s and the error
message is unhelpful" per screen.

**Version-fragile, may need adjusting after I test** — upstream statistics cards,
blocked services, per-client settings, anything touching `safe\_search`.

**Will not work, don't try** — real-time updates, notifications, remote access,
sub-hourly statistics.

**Success for v1** is: I open the app on my home Wi-Fi, see accurate current numbers,
pause protection for five minutes from the Quick Settings tile, and add or toggle a
blocklist. Everything else is a bonus. If any part of this spec threatens that core,
say so and cut it rather than half-building it.

\---

## 7\. Networking constraints — do not skip this section

This is a LAN app talking to a self-hosted box, so the defaults fight you:

1. **Cleartext HTTP is blocked by default** on API 28+. Add a network security config
XML resource that permits cleartext and reference it from the manifest via
`android:networkSecurityConfig`. Scope it as narrowly as you can — ideally to private
IP ranges rather than `cleartextTrafficPermitted="true"` globally. Explain the
trade-off in a comment.
2. **Self-signed certificates**, if I later enable HTTPS. Provide an *opt-in* setting
("trust self-signed certificate") that installs a permissive `TrustManager`, default
OFF, with a clear warning in the UI. Do not ship a blanket trust-all `TrustManager`
as the default.
3. **The server is unreachable when I'm off my home Wi-Fi.** This is the normal case,
not an error state to panic about. Detect connection failures and show a calm
"Can't reach your server — are you on your home network?" state with a retry button.
No red error screens, no crash.
4. Set OkHttp timeouts to something short (5s connect, 10s read). A LAN box either
answers fast or isn't there.
5. Handle 401 by routing back to setup with a "credentials rejected" message.

\---

## 8\. How to deliver the code

Work in phases. **Stop after each phase and wait for me to say continue.**

* **Phase 1** — Project skeleton: `libs.versions.toml`, both `build.gradle.kts` files,
`AndroidManifest.xml`, network security config, Application class, Hilt modules,
theme. Plus your list of API endpoints you want me to verify (see §5).
* **Phase 2** — Data layer: network models, the Retrofit interface, interceptors,
credential store, repository.
* **Phase 3** — The shared server connection form (§4.9) and credential store (§4.10),
the setup flow that wraps it, then the Dashboard. The dashboard is the largest single
piece of work here — deliver it as its own sub-phase if it's cleaner: reusable card
components (stat card with sparkline, top-N list card) first, then the screen that
assembles them.
* **Phase 4** — Query log + Filters screens.
* **Phase 5** — Clients + Settings.
* **Phase 6** — Glance widget + Quick Settings tile.

For every file: give the **full file path** as a header, then the complete file
contents in a single code block. No fragments, no "add this to your existing file"
without showing me the surrounding context.

At the end of each phase, list any new dependencies added and any manifest changes.

\---

## 9\. Acceptance criteria

* Project builds and installs on a physical device running Android 14 with no manual
fixes beyond entering my server details.
* Toggling protection from the Quick Settings tile takes effect on the Pi within 2s.
* The app never crashes when the server is unreachable.
* No credential is written to Logcat, DataStore plaintext, or a backup-eligible file.
I will verify this by running `adb shell run-as <pkg> find . -type f` and grepping
every readable file for my password, and by watching Logcat during a full session
including a failed login. Neither may surface it.
* Changing the server address or password takes effect immediately, with no app restart.
* No hardcoded IP addresses, ports, or passwords anywhere in the source.
* The dashboard shows all eight sections from §4.2 (A–H) and matches the numbers in the
AdGuard Home web dashboard exactly for the same time window.
* Sparklines render from real time-series data, not a placeholder or a static image.
* The dashboard degrades gracefully on a server that doesn't return upstream stats.
* Every blocklist operation — add, rename, change URL, enable, disable, delete, refresh
— round-trips correctly and is reflected in the AdGuard Home web UI after a reload.
* Editing custom rules never drops rules that were already there.
* Tapping refresh on any screen updates every value on it, on the first tap, every time
— no half-updated screens, no stale cards, no need to leave and come back.
* Rapidly tapping refresh does not fire overlapping requests or corrupt the UI state.
* A failed refresh leaves the previously loaded data visible and labelled as stale.
* The app makes no network requests while it is in the background.
* **The app never locks me out of AdGuard Home.** No automatic retry on 401 or 429,
ever, anywhere in the codebase.
* A server response missing an unexpected field degrades one card or row — it never
blanks a screen or crashes the app.

\---

## 10\. Start here

Before writing any code, respond with:

1. Confirmation that you've read §5 and §6, restating the functional ceilings in §6.5 so
I know we agree on what this app cannot do.
2. Any endpoint in §5 you believe is wrong or has changed in my version — §5 was
transcribed from the v0.107.78 spec, so flag differences against the version in §1
rather than re-deriving it from memory.
3. The exact library versions you intend to use (see §2), since I'm not pinning them.
4. Anything in this spec you think is a bad idea, over-scoped for v1, or likely to
break against my AdGuard Home version — I would rather cut features now than
discover them broken on device.
5. Then deliver Phase 1.

