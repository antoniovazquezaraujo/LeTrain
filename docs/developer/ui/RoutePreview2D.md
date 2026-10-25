# Route preview 2D (CONNECT)

First UI slice of the route authoring flow described in
[ADR-035](../adr/ADR-035-Route-Preview-2D.md). It previews a route on the terminal map and stores
the accepted plan, but **never builds anything**.

## Flow

| Key | Effect |
| --- | --- |
| `Shift+C` | Enter CONNECT from a normal map/edit mode (not from `COMMAND`, `PROGRAM`, `TRAINS` or `ADD`). |
| Arrows / `hjkl` / `PageUp` / `PageDown` | Move the cursor or page the map. Construction and deletion are disabled. |
| `Enter` | `PICK_START`: fix A. `PICK_END`: fix B and plan. `PREVIEW`: accept the plan and leave CONNECT. |
| `←` / `→` | In `PREVIEW`, cycle the policy and recompute. |
| `Esc` | Cancel and return to the previous mode. |
| `Shift+C` | Cancel and return to the previous mode. |

The session (`RoutePreviewSession`, UI-free) owns the states and the planner. The presenter creates
it lazily from `GroundMap` + `EconomyManager`, resets it on every model swap, and keeps the accepted
plan in `pendingRoutePlan` for the future construction queue.

## Rendering

- One plan at a time: `A`/`B` endpoints, `═` normal, `≈` bridge, `▒` tunnel.
- The overlay only changes the foreground and glyph; the terrain background is preserved.
- `RoutePreviewOverlay` is the pure plan → cells mapping; `RoutePreviewStatus` formats the status
  line (`cost`, game time from `effectiveConstructionTicks()`, segment count and key hints).
- `TerminalPresenter` draws the overlay after `renderer.visitModel(model)` and before
  `view.paint()`. Nothing is drawn outside CONNECT, so the overlay cannot persist.
