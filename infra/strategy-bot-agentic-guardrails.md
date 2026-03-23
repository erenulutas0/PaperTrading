# Strategy Bot Agentic Guardrails

This platform is paper-only, server-executed, and audit-first. Any future LLM-assisted strategy-bot runtime must preserve those constraints.

## Non-Negotiables
- No free-form brokerage or order-routing autonomy.
- No client-side execution authority.
- Every model proposal must be validated server-side before it can affect a paper portfolio.
- Prompt and action metadata must be audit-linked to the bot run and request correlation id.

## Provider Abstraction
Future model integrations must implement:

- `com.finance.core.service.StrategyBotAgentModelProvider`

This keeps provider selection replaceable instead of coupling the runtime to one vendor, one model id, or one prompt format.

## Allowed Tool Sandbox
The model-facing tool envelope is intentionally narrow. The current bounded scopes are:

- `MARKET_CANDLES`
- `MARKET_SNAPSHOT`
- `BOT_CONFIG`
- `RUN_JOURNAL`
- `PORTFOLIO_STATE`

Anything outside these scopes should be treated as a runtime contract violation, not silently ignored.

## Bounded Action Schema
Future model output must stay inside:

- `BUY`
- `SELL`
- `HOLD`
- `UPDATE_STOPS`

The action payload is represented by:

- `StrategyBotAgentActionProposal`
- `StrategyBotAgentDecisionContext`

Server-side validation is enforced by:

- `StrategyBotAgentActionValidator`

Current guardrails:

- `BUY` requires a flat position and a positive `sizePercent` within the bot's configured max-position cap.
- `SELL` requires an open position and a bounded `closePercent`.
- `HOLD` cannot mutate size or stop/take fields.
- `UPDATE_STOPS` requires an open position and can only tighten risk inside the bot's configured stop/take envelope.
- Audit metadata is mandatory:
  - provider name
  - provider model
  - prompt version
  - rationale
  - bounded matched-signal list
  - bounded tool-scope list

## Audit Requirements
Before any live agentic execution slice is enabled, each proposal must be persisted or audit-linked with:

- request id
- bot id
- run id
- provider name/model
- prompt version
- provider response id when available
- tool scopes used
- validated action payload
- validator outcome (`accepted` / `rejected`)
- prompt hash

Raw prompt/response retention can be redacted or access-restricted, but the audit trail cannot be optional.

## Rollout Rule
Do not wire a provider directly into run execution until:

1. proposal validation is mandatory
2. audit logging is append-only
3. rejected proposals are observable
4. runtime defaults remain disabled unless explicitly enabled by config
