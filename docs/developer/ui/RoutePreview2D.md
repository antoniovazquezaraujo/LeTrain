# Route preview 2D (CONNECT)

2D terminal slice of the route authoring flow described in
[ADR-035](../adr/ADR-035-Route-Preview-2D.md) and its asynchronous successor
[ADR-036](../adr/ADR-036-Async-Route-Preview.md). It previews a route on the terminal map and
stores the accepted plan, but **never builds anything**.

`RoutePreviewSession.State.PREVIEW` is the `CHOOSING` state from ADR-036: a plan (feasible or not)
is available and the map is navigable again.

## Flow

| Key | Effect |
| --- | --- |
| `Shift+C` | Enter CONNECT from a normal map/edit mode (not from `COMMAND`, `PROGRAM`, `TRAINS` or `ADD`). |
| `Enter` | `PICK_START`: fix A. `PICK_END`: fix B and start planning. `PREVIEW` (`CHOOSING`): select the plan and leave CONNECT. Ignored while `PLANNING`. |
| `[` / `]` | In `PREVIEW`, switch to the previous/next policy. Cached policies are shown immediately; uncached ones return to `PLANNING`. |
| `Space` | In `PREVIEW`, jump the cursor to A, then B, then A…, centring the view on the focused endpoint. Plan, endpoints, cost and policy are untouched. |
| Arrows / `hjkl` | Navigate: move or turn the cursor. Construction and deletion are disabled. |
| `PageUp` / `PageDown` | Page the map (Ctrl variants page horizontally). |
| `z` / `Z` | Cycle the camera deadzone / toggle camera pagination, like the normal map mode. |
| `:` | In `PREVIEW`, open the console without dropping the preview. |
| `Esc` | Cancel and return to the previous mode. |
| `Shift+C` | Cancel and return to the previous mode. |

While `PLANNING`, only `Esc` and `Shift+C` are accepted; movement, navigation, console and route
keys are ignored. While `PICK_START`/`PICK_END`, navigation keeps its previous behaviour (`h`/`l`
turn the cursor), the console is not available and `Space` is not intercepted.

`Space` keeps a presenter-owned A/B toggle. Manual navigation does not reset it: if A was focused
last, the next `Space` goes to B even after moving the cursor or panning. The toggle resets when
CONNECT is entered, left or the model/preview is reset.

The session (`RoutePreviewSession`, UI-free) owns the states and the asynchronous planner. The
presenter creates it lazily from `GroundMap` + `EconomyManager`, polls it once per frame
(`pollRoutePreview()` → `session.poll()`) from the UI thread, resets it on every model swap, and
keeps the accepted plan in `pendingRoutePlan` for the future construction queue.

## States and status

| State | Meaning |
| --- | --- |
| `PICK_START` | Waiting for A. |
| `PICK_END` | A is fixed; move the cursor and `Enter` to fix B. |
| `PLANNING` | A background job is computing the route. Status shows an animated bar, phase, nodes and elapsed time. |
| `PREVIEW` (`CHOOSING`) | A plan is available; the map is navigable and `[`/`]`/`Enter`/`Esc`/`:` are active. |
| `SELECTED` | Feasible plan stored; the mode closes. |
| `CANCELLED` | Preview discarded. |

`PLANNING` status (no cost, no fake percentage, no `Enter`):

```text
Connect · PLANNING · CLEARANCE · 234k nodes · 3.2 s · [░░██░░░░] · Esc cancel
```

The bar is indeterminate: a two-cell activity window moves according to the progress snapshot
(`expandedNodes`/`elapsedMillis`), so the same snapshot always renders the same frame.

`PREVIEW` status keeps the cost, game time and cell count, with the route-key hint:

```text
Connect · CHEAPEST (may cross) · $1.234.500 · 1 h 12 min · 87 cells · Enter select · Space A/B · [ ] policy · Esc discard
```

## Console whitelist

While a preview session is active, the console only accepts navigation/read-only commands:
`go`, `g`, `face`, `mark`, `m`, `info`, `ls`. The check runs in `executeCommand` before executing
anything, is case-insensitive, trims whitespace and validates every semicolon-separated statement.
A blocked command leaves the console open with:

```text
Only navigation commands are available while previewing
```

Allowed commands may move the cursor or marks; the plan and A/B stay fixed. Exiting the console
(`Esc` or a silent successful command) returns to `PREVIEW`, because `COMMAND` remembers `CONNECT`
as its previous mode.

## Rendering

- One plan at a time. Glyphs and colours:

  | Cell | Glyph | Foreground | Background |
  | --- | --- | --- | --- |
  | Normal track | `⬤` | white bright | terrain (preserved) |
  | Bridge | `⬤` | red bright | terrain (preserved) |
  | Tunnel | `⬤` | red bright | terrain (preserved) |
  | Endpoint `A`/`B` | `A` / `B` | yellow bright | terrain (preserved) |

- During `PLANNING` there is no route (`lastPlan` is null), so only A/B are painted.
- The route is a sequence of cells, not a directional line: every route cell uses the large black
  circle `⬤` (U+2B24). The circle looks continuous whichever way the route turns and avoids the
  Unicode double-diagonal problem of box-drawing glyphs.
- Only the foreground changes: white for normal track, red for bridges/tunnels. The terrain
  background is always preserved, so the route reads as an overlay.
- Endpoint markers win if an endpoint shares a cell with a route step.
- `RoutePreviewOverlay` is the pure plan → cells mapping; `RoutePreviewStatus` formats the status
  line (`cost`, game time from `effectiveConstructionTicks()`, segment count, progress and hints).
- `TerminalPresenter` draws the overlay after `renderer.visitModel(model)` and before
  `view.paint()`. Nothing is drawn outside CONNECT, so the overlay cannot persist; it is hidden
  while the console is open and returns intact on exit.
