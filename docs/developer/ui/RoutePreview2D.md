# Route preview 2D (CONNECT)

First UI slice of the route authoring flow described in
[ADR-035](../adr/ADR-035-Route-Preview-2D.md). It previews a route on the terminal map and stores
the accepted plan, but **never builds anything**.

## Flow

| Key | Effect |
| --- | --- |
| `Shift+C` | Enter CONNECT from a normal map/edit mode (not from `COMMAND`, `PROGRAM`, `TRAINS` or `ADD`). |
| Arrows / `hjkl` / `PageUp` / `PageDown` | Move/turn the cursor or page the map. Construction and deletion are disabled. |
| `Enter` | `PICK_START`: fix A. `PICK_END`: fix B and plan. `PREVIEW`: accept the plan and leave CONNECT. |
| `←` / `→` / `h` / `l` | In `PREVIEW`, cycle the policy and recompute. Outside `PREVIEW`, `h`/`l` keep turning the cursor. |
| `Esc` | Cancel and return to the previous mode. |
| `Shift+C` | Cancel and return to the previous mode. |

The session (`RoutePreviewSession`, UI-free) owns the states and the planner. The presenter creates
it lazily from `GroundMap` + `EconomyManager`, resets it on every model swap, and keeps the accepted
plan in `pendingRoutePlan` for the future construction queue.

## Rendering

- One plan at a time. Glyphs and colours:

  | Cell | Glyph | Foreground | Background |
  | --- | --- | --- | --- |
  | Normal track | `═` | cyan bright | terrain (preserved) |
  | Bridge | `≈` | red bright | white (opaque) |
  | Tunnel | `⋂` (same as the renderer's tunnel entrance) | red bright | white (opaque) |
  | Endpoint `A`/`B` | `A` / `B` | yellow bright | terrain (preserved) |

- Normal cells and endpoints only change the foreground and glyph, so the terrain background is
  preserved. Tunnel and bridge cells use an opaque white background because no single foreground
  colour reads over both rock and water.
- Endpoint markers win if an endpoint shares a cell with a route step.
- `RoutePreviewOverlay` is the pure plan → cells mapping; `RoutePreviewStatus` formats the status
  line (`cost`, game time from `effectiveConstructionTicks()`, segment count and key hints).
- `TerminalPresenter` draws the overlay after `renderer.visitModel(model)` and before
  `view.paint()`. Nothing is drawn outside CONNECT, so the overlay cannot persist.
