# Connected AI settings and cultivation references

This extends the existing OpenAI-compatible adapter, encrypted device preferences,
Compose settings and commit-before-narration pipeline. Campaign format **v3** and
Room schema **v1** are unchanged. Offline mechanics never depend on an API key.
See `TESTING.md` for the exact verified build and outstanding device acceptance.

## Setup and automatic restoration

Open **Menu → Settings / AI**. Enter a complete HTTPS **chat/completions** endpoint,
API key and model ID, or use an endpoint preset. Changing the endpoint's origin
clears the draft key to avoid sending one provider's credential to another host.
Presets are conveniences for compatible APIs, not native integrations with every
API offered by those companies. Native Anthropic Messages, OpenAI Responses,
OAuth/device-login flows and arbitrary provider protocols are not implemented.

The selected saved configuration loads automatically at startup, including its
key, model, endpoint, temperature, limits and optional reference settings. Keys
remain AES-GCM encrypted with Android Keystore and are not included in campaign
exports, UI saved-state bundles or prompt messages. Existing single-provider
preferences are read compatibly and migrated on the next successful settings write.
A broken key leaves endpoint/model metadata intact and disables that profile for
the session until the key is re-entered. Other encrypted keys are preserved.

When **Enable provider calls** and **Auto-connect saved provider** are on, startup
performs a cancellable, asynchronous **GET /models**, not a paid completion. A
validated network return can retry this metadata check with a 30-second cooldown.
Disabled profiles make no automatic requests. A model-list success establishes API
reachability, not permission to generate with every listed model. A denied or
unsupported catalog leaves manual configuration and offline play available.

**Detect models / check access** works before a model has been entered. **Test
generation · may cost** is an explicit, small completion with no campaign data.
Neither action submits a game command. Network/server failures never replay an
action or automatically retry a paid completion. No API credentials are supplied
with the app; provider calls may incur charges.

## Roadmap continuation: named provider profiles

The remaining Phase 3 request-profile feature is now exposed under **Provider
profiles** in the same settings panel. Up to eight profiles retain independent
endpoints, encrypted keys, models, context/output settings and knowledge sources.

- Edit a form and enter a new name, then **Save as new profile**. The new profile
  copies the form, including its credential, and becomes selected.
- Select an existing profile to restore its saved settings. Unsaved edits trigger
  a discard confirmation, rather than silently replacing an unsaved key or limit.
- Rename a profile without editing its connection. Names are unique.
- Deleting requires confirmation, preserves other profiles/campaigns, and keeps
  at least one profile. Another remaining profile becomes selected.
- The active profile ID persists across startup. Switching cancels obsolete model
  discovery, clears reference cache/results and prevents stale catalog responses
  from overwriting the new selection. A catalog is bound to endpoint and credential.
- Repairing one unreadable key does not erase other profiles' encrypted ciphertext.

This completes the named request-profile increment, not native support for every
provider protocol or the roadmap's world-generation/God Mode features.

## Models and independent token settings

For conventional compatible endpoints ending in `/chat/completions`, discovery
uses `/models` under the same API base and origin, with the same request-specific
credential. Redirects are disabled. The catalog is bounded; IDs can be filtered
and selected or entered manually even when discovery fails.

Context detection trusts only explicit positive integer fields supplied for the
exact model: `context_length`, `context_window`, or `max_context_tokens`. It does
not guess from a model name or maintain a potentially stale hardcoded capacity
list. Provider output metadata uses `max_output_tokens` or
`top_provider.max_completion_tokens` when available. Many compatible model-list
APIs expose **no** context capacity; this is shown as unknown rather than invented.

- **Context window · tokens** is editable from 1,024 to 10,000,000. A provider-
  reported or manually verified **1,000,000** works; typing commas is supported.
- Editing the box activates a manual override. Changing models restarts detection. **Use detected context when
  available** returns it to metadata-driven mode.
- An unknown model uses a saved/default budget (initially 32,768), explicitly
  labeled **not a verified model limit**. Check its documentation and edit it.
- **Maximum output · tokens** is separate (1–16,000, smaller than the context
  window). It is an upper ceiling, not a command to generate that many tokens.
- Existing task pacing remains: up to 500 output tokens for interpretation, 650
  for brief narration and 1,200 for scenes, capped by the configured maximum and
  a known provider output ceiling. Saved narration remains capped at 6,000 chars.

A larger context number does not upload the full save or all campaign history.
The same relevant context remains bounded. A conservative UTF-8-byte estimate plus
message/output reserve checks the configured capacity; this is **not an exact
model tokenizer**. Optional references, old openings, memories and scenery are
removed in that order if necessary. Authoritative facts and the submitted intention
are never silently discarded. If essential content cannot fit, no request is sent.

## Responsiveness and streaming

The application shares an OkHttp pool and dispatcher across storyteller instances.
Authorization belongs to each request, never the shared client. Compatible HTTP/2
connections can multiplex requests; HTTP/1.1 connections can be reused. Requests
use asynchronous callbacks and coroutine cancellation, with bounded deadlines.
Model checks do not block local gameplay. Web parsing runs away from the UI thread.

**Stream narration previews** requests standard chat-completion SSE. Partial text
appears in the story area, throttled to avoid recomposing on every byte, and is
clearly labeled **unverified / not saved**. Structured action proposals are never
executed incrementally. Only a complete response that passes the existing
numeric/agency/size guards is committed as narration.

Truncation, missing stream termination, tool-call frames, oversized frames,
provider errors and cancellation discard the preview. The durable offline result
remains intact. JSON responses from endpoints that ignore the stream request are
accepted as a single response. If the provider rejects streaming, turn it off and
retry **narration only**; there is no automatic second paid POST.

Pooling and streaming improve client overhead and time to visible text; they
cannot guarantee a particular provider's latency. Real-model prose quality and
semantic fidelity still need review; regex/size guards are not a proof that every
sentence agrees with game facts.

## Optional cultivation knowledge

Expand **Optional cultivation web knowledge**, enable reference lookup, and add
public HTTPS sources—one URL per line. Nothing is fetched merely by opening settings.
Save settings to use them in subsequent narration, or run **Test knowledge lookup**
with an explicit focused query.

Supported sources:

- Direct HTML/text pages, including accessible Fandom articles.
- MediaWiki `api.php` endpoints: a small search selects an article at that site's
  installation rather than downloading the wiki.
- Fandom URLs: try a focused wiki API search, falling back to the specific supplied
  article when its API is unavailable and the page itself is accessible.
- Optional general web search through a user-configured **SearXNG-compatible JSON**
  `/search` endpoint. It must support `q` and `format=json`. Leave it blank to use
  direct/wiki sources only. No search subscription, search key or guaranteed public
  SearXNG instance is bundled.

Automatic lookups extract generic concepts such as cultivation, spiritual roots,
meridians or tribulations from the submitted action. They do **not** send the full
action, player/NPC names, private journal or memories to search/wiki services.
Explicit test queries are sent as typed. Results are external references for
terminology/atmosphere, not campaign canon, NPC knowledge, rewards or executable
instructions. They are supplied only to narration, never as mechanical state
patches or authoritative action interpretation.

Retrieval is intentionally small:

- Up to eight saved sources; at most two selected per lookup, prioritizing URL
  relevance and then list order, plus the optional search service.
- Bounded public HTTP redirects, request deadlines and transfer sizes (1 MiB per
  fetched document, not an unlimited site download).
- Short relevant excerpts (at most three references), stripped of scripts,
  navigation/forms and unrelated paragraphs; no JavaScript execution or crawling.
- A 16-entry, 30-minute in-memory cache for focused lookups. Profile changes clear
  it. References and lookup history are not a new campaign persistence system.
- Per-source timeouts retain other successful results. Empty/protected/irrelevant
  pages yield no invented information; narration continues from saved game facts.

The public-document client is separate from the authenticated provider pool. It
never receives an AI key or cookies. HTTPS, URL and DNS/address checks reject
credential-bearing URLs and common private/local destinations; redirects are
checked again. This is for public references, not authenticated intranet scraping.
Fandom/other sites can block access, require JavaScript, disable search or change
layouts. The app does not bypass CAPTCHAs, logins, access controls, paywalls
or anti-bot defenses. Add another accessible source if needed.

Extracted text is untrusted. Prompt instructions separate it from authoritative
facts, and the engine has no path for web text to grant stats/items/consent. The
provider can still write inaccurate prose, so expandable action details remain
authoritative. The reference preview exposes source links for inspection.
