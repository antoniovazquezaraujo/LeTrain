# Aplicación de ajustes (S3a, issue #763)

Base de core del diálogo **Settings en partida** (arco New Game + Settings, #742; concepto §5.2,
decisiones D4/D9/D10).

## API por estado

`Model.applyLiveSettings(Map<String,String>)` valida cada clave contra `SettingsCatalog` y devuelve
un `SettingsApplyResult` con las claves aplicadas y los rechazos con su motivo (la UI los pinta):

| Caso | Resultado |
| :--- | :--- |
| `LIVE` / `REAPPLY` | Se aplica vía `EconomyManager.applyConfig`; el reloj se re-sincroniza para `time.dayDurationSeconds` y `world.latitude`, igual que en load/newGame (`Model.syncClockWith`) |
| `NEW_GAME` (`seed`, terreno, `time.startDay` / `time.startTime`) | Rechazo `requires a new game`; la clave no se aplica y el reloj no se mueve |
| Clave definida por el `configuration { }` del escenario | Rechazo `defined by scenario` (D10) |
| Valor inválido | Rechazo con el mensaje del catálogo; nunca se aplica a medias |
| Clave desconocida | Rechazo `unknown setting` |

Al cambiar la duración del día, el reloj **rebasea el contador** (`GameTime` mostrado antes del
cambio + `GameClock.setTime`): el calendario conserva el instante y solo cambia el ritmo, sin saltos
de día/hora. `world.latitude` re-sincroniza el ciclo solar.

## Claves fijadas por el escenario (D10)

`Model.applyScenarioConfiguration(map)` sustituye al `applyConfig` directo de los presentadores (2D
y 3D): aplica el `configuration { }`, **recuerda sus claves** y cablea el reloj (duración, latitud
y origen, ya que el mundo se reconstruye desde cero). Las claves viajan dentro de la sección
`settings` del savegame en la entrada reservada `scenario.keys` (lista separada por comas), de modo
que el bloqueo sobrevive al load; los saves anteriores a S3a no la traen y cargan sin bloqueo. La UI
las consulta con `Model.getScenarioSettingKeys()`.

## Guardar como valores por defecto (D4)

`ConfigFileWriter.writeDefaults(File target, Map<String,String> values)` fusiona el
`effectiveConfig()` de la partida con el fichero: las claves ya presentes se actualizan en su propia
línea y el resto del fichero (comentarios, líneas en blanco y claves ajenas) se conserva; las claves
que faltan se añaden al final bajo un encabezado. El resultado sigue siendo un `Properties` que el
cargador actual lee igual. La ruta del cfg activo la da `EconomyManager.getConfigFile()`
(directorio de trabajo primero, luego junto a la aplicación); `null` cuando no existe y la UI decide
qué fichero crear.
