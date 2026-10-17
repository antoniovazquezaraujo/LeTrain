# ADR-030: Catálogo único de ajustes (Nueva partida, Settings y escenarios)

## Estado: ACEPTADO — implementado en el arco #742 (rebanadas E1–E2 y S1–S5, PRs #741–#783)

> **Nota.** Este ADR **registra a posteriori** la arquitectura que quedó integrada en el arco de
> ajustes (issue #742). No abre trabajo nuevo: documenta las decisiones ya implementadas en
> `develop` tras los PRs #741–#783, para que el "por qué" del catálogo, la precedencia y el
> `configuration { }` generado no se pierdan.

## Contexto

Hasta el arco #742, los ajustes del juego vivían **dispersos y sin una fuente de metadatos común**:

- `letrain.cfg` (un `Properties` plano) como único sitio editable a mano, leído por
  `EconomyManager.applyProperties`.
- **Defaults cableados en código** (`EconomyManager`, `GroundMap`, `GameClock`…), cada uno en su
  sitio y sin rango ni unidad declarados.
- Los **diálogos de UI** (2D y 3D) conocían los campos por su cuenta, así que la paridad entre
  clientes dependía de mantener dos listas a mano.
- El bloque `configuration { }` del **escenario** (`ScenarioFile`) y la sección `settings` del
  **savegame** eran terceros y cuartos lugares donde los mismos valores viajaban con su propio
  formato.

Ese modelo no cubría las necesidades que planteó el arco:

- La pantalla **Nueva partida** (#282) exige un formulario con defaults pre-rellenados, rangos,
  unidades y preview del terreno.
- **Editar ajustes en partida** (#709, #713) exige saber, por ajuste, si se aplica en vivo, si
  conviene avisar o si solo tiene efecto al generar el mundo.
- Las **proporciones de terreno** (agua %, montaña %) no eran intuitivas: los umbrales crudos
  `threshold.WATER`/`threshold.ROCK` producían mundos de más de mitad agua y casi sin montañas; el
  jugador quiere pedir porcentajes, no umbrales de ruido.
- La **paridad 2D/3D** (principio rector del proyecto, ADR-006) debe salir **por construcción**, no
  de dos implementaciones paralelas.
- Los **escenarios** ([[ADR-020-Command-Journal-Scenarios]]) deben seguir siendo deterministas y
  reproducibles, y la serialización ([[ADR-021-Model-Decomposition-Strategy]]) y el reloj
  ([[ADR-022-Game-Time]]) no pueden romperse al añadir ajustes nuevos.

## Decisión

### 1. `SettingsCatalog` (core) como fuente única de metadatos

`letrain.settings.SettingsCatalog` es un **catálogo puro de datos, sin UI ni dependencias del
motor**. Cada ajuste se describe con un `SettingDefinition` (record con clave, categoría, nombre
visible, tipo, unidad, rango `min`/`max`, default y estado de aplicación). El **estado** de
aplicación es un `SettingApplyState`:

| Estado | Significado | UI en partida |
|---|---|---|
| `LIVE` | Se aplica al momento (economía, UI, duración del día…) | Editable + **Aplicar** |
| `REAPPLY` | Se puede recalcular, pero merece aviso (p. ej. latitud → salto solar) | Editable con **confirmación** |
| `NEW_GAME` | Solo tiene efecto al generar el mundo (semilla, terreno, `time.startDay`/`time.startTime`) | Deshabilitado + «requiere partida nueva» |

El catálogo **lo consumen** todos los puntos de entrada, de modo que ninguno mantiene su propia
lista de campos:

- **Nueva partida** (`NewGameForm` en core + `NewGameDialog` en `ui-terminal`, S2; arranque 3D en
  S4a/S4c).
- **Settings en partida** (2D `SettingsDialog`, 3D `Gdx3DHud`, S3b/S4b).
- La pestaña **Config** del editor de escenarios (`ScenarioFile`, S5).
- La lectura/escritura de `letrain.cfg` (`ConfigFileWriter`, D4) y la construcción del bloque
  `configuration { }`.

Además del estado, el catálogo resuelve los **estados de campo de la UI en partida**
(`SettingFieldState`, que añade `SCENARIO` a `LIVE`/`REAPPLY`/`NEW_GAME`) y la **validación
centralizada**: tipo, rango y las reglas cruzadas (agua + montaña ≤ 95 %). `validateAll` **ignora
las claves desconocidas**, de modo que un savegame o un escenario de una versión con más ajustes que
la actual sigue cargando sin romperse.

### 2. Precedencia de ajustes

Los ajustes efectivos se resuelven en este orden, **de mayor a menor prioridad**:

```
escenario (configuration { })  >  savegame (sección settings)  >  letrain.cfg  >  defaults de código
```

- El **savegame** viaja con su propia sección `settings` (`Model.effectiveConfig()`), para que una
  partida conserve sus reglas aunque el `letrain.cfg` local cambie.
- El **escenario** sigue siendo la vía determinista: su `configuration { }` manda sobre cfg y
  defaults y fija las claves que el jugador verá bloqueadas.
- `letrain.cfg` deja de ser el único sitio editable: la UI puede leerlo y —con **«guardar como
  valores por defecto»**— escribirlo.

### 3. Estados, bloqueo y aplicación explícita

- **Solo las claves que el escenario define** en su `configuration { }` quedan bloqueadas (grises)
  en el diálogo de Settings; el resto sigue editable (**D10**). El bloqueo **sobrevive al load**
  porque las claves viajan en la sección `settings` del savegame (entrada reservada
  `scenario.keys`, que no es un ajuste del catálogo).
- La aplicación es **explícita** (**D9**): los ajustes en vivo se aplican con **Aplicar** y se
  revierten con **Restaurar**, sin efecto al teclear.
- Un ajuste `REAPPLY` modificado **pide confirmación** antes de aplicarse (`changedReapplyKeys`).
- El estado `NEW_GAME` **no se aplica nunca en caliente**: `applyLiveSettings` lo rechaza con
  «requires a new game» y la UI ofrece el atajo *«Empezar partida nueva con estos valores»*.

### 4. Terreno en porcentajes

- `terrain.waterPercent` y `terrain.mountainPercent` son las claves **autoritativas** (defaults
  **agua 25 % · montaña 10 %**, D2). El rango es 0–95 % con la regla cruzada agua + montaña ≤ 95 %
  (queda al menos un 5 % de campo).
- Un **`TerrainCalibrator`** (core) traduce los porcentajes pedidos a los **umbrales crudos** de
  ruido que consume `GroundMap`: muestrea el mismo ruido y mapeo que el generador y toma los
  percentiles por bisección sobre la muestra, de modo que el mundo tiene *estadísticamente* las
  proporciones pedidas (determinista para el mismo seed).
- Las claves **`threshold.WATER` / `threshold.ROCK` son legacy y solo valen como entrada**: dentro
  de una misma fuente los porcentajes ganan; una fuente que **solo** trae umbrales (partida o
  escenario antiguos, `letrain.cfg` actual) conserva el aspecto histórico, sin forzar la
  calibración. Desde S1 los ajustes de terreno son de estado `NEW_GAME`.

### 5. Escenarios: `configuration { }` generado del catálogo

- El bloque `configuration { }` de un escenario exportado se **genera del catálogo**
  (`SettingsCatalog.scenarioConfiguration`): **todas las claves menos `seed`** (el escenario ya
  escribe su propia línea `seed`), con el **valor efectivo** de la partida sobre el default, de modo
  que el bloque siempre queda **completo y determinista**. `ScenarioFile` lo **escribe ordenado por
  clave**.
- Al **reproducir** un escenario, esas claves se aplican y quedan **fijadas** (bloqueadas) para la
  partida.
- **Sin migración de claves antiguas** (decisión explícita del owner): las claves legacy que no
  están en el catálogo —los `threshold.*` de terreno— **se descartan** al exportar. Re-exportar un
  modelo legacy **no reescribe sus umbrales crudos**: emite los defaults del catálogo (25/10) y, por
  tanto, un mundo calibrado por porcentajes. Es un coste asumido a cambio de un bloque siempre
  válido y legible.

### 6. UI: un catálogo, ambos formularios

- Un **único catálogo** genera los formularios de 2D y 3D, así que la **paridad es por construcción**
  (mismos campos, mismas secciones, mismos rangos y estados).
- La **semilla es visible y copiable** siempre (D3); el default `random` se resuelve a un valor
  concreto al abrir el formulario.
- **«Guardar como valores por defecto»** escribe `letrain.cfg` (D4): `ConfigFileWriter` **fusiona**
  los valores efectivos sobre el fichero conservando comentarios, líneas en blanco y claves ajenas,
  y añade las que falten bajo un encabezado.

## Consecuencias

- **Positivas**
  - **Una sola fuente de verdad** para clave/tipo/rango/unidad/default/estado: añadir un ajuste es
    tocar el catálogo y, como mucho, su consumidor de motor.
  - **Paridad 2D/3D por construcción**: ningún cliente puede quedar por detrás del otro sin dejar
    de consumir el catálogo.
  - **Validación centralizada** (tipo, rango y agua + montaña ≤ 95 %), con mensajes reutilizables
    por ambos clientes.
  - **Escenarios deterministas** y completos: el `configuration { }` generado no depende del orden
    de un `Map` ni de claves sueltas.
  - **Proporciones de terreno intuitivas** con preview objetivo/medido, manteniendo la lectura de
    umbrales para mundos existentes.
  - **Bloqueo de escenario preciso** (solo las claves definidas, D10) que sobrevive al load.
- **Costes y riesgos**
  - **Aceptado: sin compatibilidad de claves legacy en escenarios.** Un escenario antiguo con
    `threshold.*` pierde esos valores al re-exportarse (sale 25/10). La lectura sigue siendo
    tolerante (claves desconocidas ignoradas), pero la migración no existe.
  - `letrain.cfg` se sigue editando a mano: una clave fuera de rango se valida en la UI, pero el
    fichero crudo lo lee el loader histórico; el catálogo acota al *canal de UI*, no al fichero.
  - `ConfigFileWriter` reescribe línea a línea: preserva el fichero, pero no entiende semántica más
    allá de `clave=valor`/`clave: valor`.
  - La UI **no debe** sortear el catálogo: la validación y los estados viven ahí y los tests
    (`SettingsCatalogTest`, `SettingsFieldStateTest`, `NewGameDialogTest`) lo congelan.
- **Dependencias**: se apoya en [[ADR-020-Command-Journal-Scenarios]] (escenario), su precedencia
  histórica «configuration gana sobre cfg local» y su re-ejecución determinista;
  [[ADR-021-Model-Decomposition-Strategy]] (serialización tolerante a campos/claves nuevos);
  [[ADR-022-Game-Time]] (`time.dayDurationSeconds`, `world.latitude`, `time.startDay`/
  `time.startTime` como ajustes del catálogo ya cableados al reloj). No cambia física, rutas ni
  cantones.

## Alternativas consideradas

- **Mantener listas de campos por diálogo**: menos trabajo inicial, pero la paridad 2D/3D quedaría a
  merced de mantener dos listas y los rangos seguirían duplicados. Descartado.
- **Anotaciones/reflexión sobre los campos del motor** como fuente de metadatos: acopla el catálogo
  al motor y complica el determinismo y los tests puros. Descartado.
- **Quedarse con `threshold.WATER`/`threshold.ROCK` como interfaz del jugador**: no son intuitivos
  (medido: >50 % agua, ~3 % montaña con los defaults históricos). Se mantienen solo como entrada.
- **Migrar automáticamente los umbrales legacy a porcentajes** al exportar: exigiría calibrar el
  seed para deducir los porcentajes efectivos y añadiría azar/deriva al bloque; el owner prefirió
  **no migrar** y asumir los defaults 25/10 en re-exportaciones legacy.

## Referencias

- **Arco #742** (issue paraguas): rebanadas **E1/E2** (editor `Programming` y menú/portada) y
  **S1–S5** (catálogo, Nueva partida 2D, Settings 2D, 3D, escenario/editor), PRs **#741–#783**.
- Concepto de trabajo: `letrain-lab/notes/letrain-settings-concept-draft.md` (§1 principios, §2
  estados, §5 flujos, §6 precedencia, §9 decisiones D1–D11).
- Documentación relacionada: `docs/developer/ui/NewGameDialog.md`,
  `docs/developer/ui/SettingsDialog.md`, `docs/developer/systems/SettingsApply.md`,
  `docs/developer/ui/RenderingDeepDive.md`, `docs/user/manual.md`, `docs/user/scenarios.md`.
- ADRs: [[ADR-020-Command-Journal-Scenarios]], [[ADR-021-Model-Decomposition-Strategy]],
  [[ADR-022-Game-Time]].
