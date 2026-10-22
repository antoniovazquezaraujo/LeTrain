# ADR-032: `wait N` para el tren y espera

## Estado: IMPLEMENTADO ([#832])

## Contexto

En el DSL de itinerarios, `wait N` era una acción de waypoint que solo programaba un
temporizador (`N` segundos de simulación = `N × 20` ticks) y no frenaba. Si el tren llegaba
rodando, seguía rodando mientras la espera transcurría; la acción no cumplía la expectativa del
jugador de "parar y esperar".

## Decisión

`wait N` pasa a significar **parar el tren y esperar**:

1. Si el tren **va en marcha** al ejecutarse la acción, se guarda la velocidad objetivo (mismo
   patrón que `load`/`unload`/`park`/`couple`), se frena (`initiateBraking`) y la acción se
   **difiere** hasta que el tren está totalmente parado (`pendingCommandToResume`). Al parar se
   re-ejecuta `wait`, que ya arranca el temporizador.
2. Si el tren **ya está parado**, el temporizador (`N × 20` ticks) empieza de inmediato, sin
   frenada adicional.
3. Al terminar la espera se sigue la ruta de restauración existente del `TrainActionManager`: se
   restauran la velocidad guardada y/o el crucero del plan (`savedTargetSpeed`,
   `resumePlanCruiseIfStopped`). Un `speed` posterior gana porque pone `savedTargetSpeed = 0`.
4. `wait 0` es un **no-op** (no frena; el tren sigue).
5. Un valor negativo se **rechaza con aviso visible** (canal `Model.reportUserMessage`, además del
   log) y no espera. Se elige rechazar en vez de recortar a 0 para que el error del plan sea
   visible.

Vale igual en waypoints de estación y de sensor, con o sin `departure`, y combinado con
`load`/`unload`/`speed`.

## Consecuencias

- **Cambio semántico intencionado**: los scripts que usaban `wait` como un retardo sin frenar
  ahora paran el tren. Es el comportamiento pedido por el dueño; se documenta en
  `docs/user/grammar.md` y `grammar_es.md`.
- La **distancia de frenado** puede dejar el tren algo pasado del waypoint (igual que
  `load`/`unload`); aceptado por el dueño.
- No hay cambios de gramática, AST, `WaypointCommand` ni formato de savegame.
- La espera empieza cuando el tren está parado, no en el instante de llegar al waypoint; la
  duración visible de la parada es por tanto `frenada + N s`, que es el comportamiento natural.

## Implementación

`TrainActionManager.executeCommand`, caso `WAIT`:

- `seconds <= 0` → no-op (negativo con aviso visible).
- `director.getSpeed() > 0` → guardar velocidad, `pendingCommandToResume = command`,
  `initiateBraking()`, `return true`.
- parado → `waitTicks = seconds * WaypointCommand.TICKS_PER_SECOND` y `scheduleResume(waitTicks)`.

Tests de regresión: `WaitStopIntegrationTest` (tren en marcha, ya parado, waypoint de sensor,
`wait 0`, combinación con `departure`) y el caso "en marcha" falla con la implementación anterior.

[#832]: https://github.com/antoniovazquezaraujo/letrain/issues/832
