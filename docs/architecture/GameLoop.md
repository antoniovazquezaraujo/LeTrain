[ [Índice] ] [[docs/architecture/Overview|⬅️ Arquitectura]] · [[docs/Index|⬅️ Volver al Índice]]

# Bucle Principal del Juego (Game Loop)

## Visión General

LeTrain tiene dos modos de ejecución — **Terminal 2D** y **LibGDX 3D** — pero comparten el mismo motor de simulación. La diferencia principal es cómo se orquesta el bucle y cómo se renderiza.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         main() (LeTrain.java)                           │
│                                                                         │
│  Model model = new Model()                                              │
│                                                                         │
│  ┌── ¿--3d? ────────────────────────────────────────────────────────┐   │
│  │  NO                                       SÍ                     │   │
│  │  ▼                                        ▼                      │   │
│  │  TerminalPresenter(model)                 GraphicPresenter(model)│   │
│  │  presenter.start()                        Lwjgl3Application(p)   │   │
│  │    ┌──────────────┐                       ┌──────────────┐       │   │
│  │    │ Bucle propio │                       │ render()     │       │   │
│  │    │ while running│                       │ (callback    │       │   │
│  │    │              │                       │  LibGDX)     │       │   │
│  │    │ 20 FPS       │                       │ ~60 FPS      │       │   │
│  │    │ lockstep     │                       │ visual       │       │   │
│  │    └──────────────┘                       │ 20 TPS sim   │       │   │
│  │                                           └──────────────┘       │   │
│  │                                                                  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  Ambos comparten:                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │ simulationController.tick()  ← 20 TPS fijo                      │    │
│  │ Model (misma instancia)                                         │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Bucle 2D (TerminalPresenter)

```
start()
  │
  ├─ Inicializa View (LanternaScreen)
  ├─ setMode(RAILS)
  ├─ updateGroundMap() para la posición inicial
  │
  └─ while (running) ──────────────────────────────────────────────────┐
       A. pollInput() (no bloqueante)                                  │
       ├─ Si hay tecla → onChar() → handler del modo actual            │
       ├─ Drain input restante                                         │
       │                                                               │
       B. simulationController.tick()  ════════════════════┐           │
       │  ├─ scheduler.tick() (tareas programadas)         │           │
       │  ├─ trackMaker.makeTracks() (construcción auto)   │   20 TPS  │
       │  ├─ moveVehicles() (física + movimiento)          │   fijo    │
       │  ├─ handleIndustrialActions() (carga/descarga)    │           │
       │  └─ cleanupEntities() (limpieza)                  │           │
       │                                                  ═════════════┘
       C. audioController.update()
       │
       D. renderer.visitModel(model)  ── Visitor pattern
       │  └─ Recorre todas las entidades y las pinta en buffer texto
       │
       E. informer.visitModel(model)  ── InfoVisitor
       │  └─ Pinta HUD, barras de estado, modo actual
       │
       F. view.paint() ── Flush del buffer a pantalla
       │
       G. Auto-follow locomotora (si mode==DRIVE)
       │
       H. Thread.sleep(50)  ── 20 FPS (~50ms por frame)
       │
       I. view.clear() ── Limpia buffer para el siguiente frame
       │
       └─ (vuelve al inicio del while)
```

### Características del bucle 2D

| Aspecto | Valor |
|---|---|
| **FPS** | 20 fijos (bloqueante con `Thread.sleep(50)`) |
| **TPS** | 20 (atado al render, mismo hilo) |
| **Input** | Polling: `screen.pollInput()` cada frame |
| **Render** | Texto en terminal (Lanterna) |
| **Cámara** | Scroll por páginas, salto cuando el cursor/loco cambia de página |
| **Animación** | Instantánea (sin interpolación entre frames) |
| **Audio** | Posición del listener = cursor o locomotora seleccionada |

---

## Bucle 3D (GraphicPresenter via LibGDX)

LibGDX llama a `render()` cada vez que el monitor refresca (~60 FPS). El bucle **separa** la simulación (20 TPS) del renderizado (variable).

```
LibGDX ApplicationAdapter
  │
  ├── create()
  │   ├─ Inicializa recursos 3D (modelos, texturas, shaders)
  │   ├─ Crea cámara, Scene2D HUD, InputProcessor
  │   └─ renderer (Gdx3DRenderer) preparado para el Visitor
  │
  └── render()  (~60 veces/segundo) ───────────────────────────┐
       │                                                       │
       A. stateTime += deltaTime                               │
       │                                                       │
       B. ┌─ ¿stateTime >= 50ms? ──────────────────┐           │
       │  │  SÍ                                    │           │
       │  │  ├─ groundMap.renderBlock()            │   20 TPS  │
       │  │  ├─ simulationController.tick()  ──────┤   fijo    │
       │  │  ├─ inputHandler.update()              │           │
       │  │  ├─ hud.updateIDE()                    │           │
       │  │  ├─ stateTime -= 50ms                  │           │
       │  │  └─ clamp anti-spiral                  │           │
       │  └────────────────────────────────────────┘           │
       │                                                       │
       C. alpha = stateTime / 50ms  ── factor interpolación    │
       │  renderer.setAnimationAlpha(alpha)                    │
       │                                                       │
       D. cameraController.update(alpha)  ── cámara suave      │
       │                                                       │
       E. audioController.update()                             │
       │  (posición = cámara, con ángulo)                      │
       │                                                       │
       F. glClear() + renderer.visitModel(model, cam)          │
       │  ├─ trackRenderer   (vías)                            │
       │  ├─ vehicleRenderer (trenes)                          │
       │  ├─ infrastructure  (sensores, semáforos, etc.)       │
       │  ├─ groundRenderer  (terreno)                         │
       │  └─ Frustum culling (solo lo visible)                 │
       │                                                       │
       G. modelBatch.render(instances)  ── geometría opaca     │
       │                                                       │
       H. DecalBatch.render(labels)  ── IDs de vehículos       │
       │                                                       │
       I. 2-pass transparency (montañas/túneles)               │
       │                                                       │
       J. renderCompass() + hud.render()  ── UI final          │
       │                                                       │
       └─ (vuelve al inicio por LibGDX)                        │
```

### Características del bucle 3D

| Aspecto | Valor |
|---|---|
| **FPS** | Variable (monitor refresh, ~60 FPS) |
| **TPS** | 20 fijos (acumulador de tiempo, independiente del render) |
| **Input** | Callbacks: `keyDown()`, `keyUp()`, `keyTyped()` |
| **Render** | OpenGL 3D con modelos, luces, sombras |
| **Cámara** | 3 modos: ORBIT (seguimiento), CAB (cabina), MAP (vista cenital) |
| **Animación** | Interpolación lineal `alpha` entre ticks (movimiento suave) |
| **Audio** | Posición del listener = cámara, con orientación |

---

## simulationController.tick() — El corazón de la simulación

Ambos modos llaman exactamente al mismo método, garantizando **determinismo**:

```
simulationController.tick()
  │
  ├─ 1. scheduler.tick()
  │     └─ Ejecuta tareas programadas (ej: cambio de desvío tras N ticks,
  │        acciones retardadas de automatización)
  │
  ├─ 2. trackMaker.makeTracks()
  │     └─ Construcción automática de vías (modo "caterpillar")
  │        mientras el usuario mantiene una tecla de dirección
  │
  ├─ 3. moveVehicles()
  │     └─ Por cada locomotora:
  │         ├─ locomotive.update()
  │         │   ├─ train.safetyManager → verifica bloque seguro
  │         │   ├─ train.movementManager.moveLinkers()
  │         │   │   ├─ Avanza cada linker (vagón) una celda
  │         │   │   ├─ Detecta colisiones, callejones sin salida
  │         │   │   ├─ Dispara eventos: onSensorEnter, onCrash, etc.
  │         │   │   └─ Actualiza ocupación de segmentos
  │         │   ├─ train.actionManager → verifica waypoints
  │         │   ├─ train.autopilot → decide siguiente acción
  │         │   └─ train.couplingManager → acopla/desacopla si toca
  │         ├─ Cobro de combustible (si es locomotora directora)
  │         └─ Economía: descuenta por movimiento
  │
  ├─ 4. handleIndustrialActions()
  │     └─ Para cada estación:
  │         ├─ 5% probabilidad de regenerar carga
  │         ├─ Si hay tren en estación: transfiere carga
  │         └─ Eventos económicos por carga movida
  │
  └─ 5. cleanupEntities()
        └─ Locomotoras y vagones destruidos:
            ├─ Libera bloques de seguridad
            ├─ Elimina de listas del modelo
            └─ Auto-selecciona siguiente locomotora si la actual fue destruida
```

---

## Comparativa 2D vs 3D

```
2D TERMINAL                             3D LIBGDX
═══════════                             ═══════════

┌──────────────┐                        ┌─────────────────────────┐
│ Input poll   │      input             │ Input callback (event)  │
│ → onChar()   │◄──────────────────────►│ → keyDown/keyTyped()    │
│ Lanterna     │                        │ InputProcessor + HUD    │
└──────┬───────┘                        └────────┬────────────────┘
       │                                         │
       ▼                                         ▼
┌──────────────┐                        ┌─────────────────────────┐
│ simulation   │      same code         │ simulation              │
│ controller   │◄──────────────────────►│ controller              │
│ .tick()      │                        │ .tick() (capped 20 TPS) │
└──────┬───────┘                        └────────┬────────────────┘
       │                                         │
       ▼                                         ▼
┌──────────────┐                        ┌─────────────────────────┐
│ Audio        │      audio             │ Audio (sync per frame)  │
│ (per tick)   │◄──────────────────────►│ con posición de cámara  │
└──────┬───────┘                        └────────┬────────────────┘
       │                                         │
       ▼                                         ▼
┌──────────────┐                        ┌─────────────────────────┐
│ RenderVisitor│      visitor           │ Gdx3DRenderer.visit()   │
│ (texto)      │◄──────────────────────►│ (modelos 3D)            │
│ .visitModel()│                        │ .visitModel()           │
└──────┬───────┘                        └────────┬────────────────┘
       │                                         │
       ▼                                         ▼
┌──────────────┐                        ┌─────────────────────────┐
│ InfoVisitor  │      visitor           │ HUD (Scene2D)           │
│ (HUD texto)  │◄──────────────────────►│ .render()               │
└──────┬───────┘                        └────────┬────────────────┘
       │                                         │
       ▼                                         ▼
┌──────────────┐                        ┌─────────────────────────┐
│              │                        │ 3D pipeline:            │
│ view.paint() │                        │ modelBatch.render()     │
│ (Lanterna)   │                        │ decalBatch.render()     │
│              │                        │ transparency 2-pass     │
└──────────────┘                        └─────────────────────────┘

      20 FPS fijo                            60+ FPS variable
```

---

## Diagrama de Secuencia (1 tick de simulación)

```
                    ┌─ simulationController.tick() ───────────────────┐
                    │                                                 │
 scheduler.tick()   trackMaker       moveVehicles()   industrial    cleanup
       │               │                 │             actions        │
       ▼               ▼                 ▼               ▼            ▼
 ┌────────┐      ┌──────────┐     ┌────────────┐   ┌──────────┐  ┌──────┐
 │ Ejecuta│      │ Construye│     │ locomotive │   │ Regenera │  │Quita │
 │ tareas │      │ vía si   │     │ .update()  │   │ carga    │  │locos │
 │ pdtes. │      │ hay tecla│     │   ┌───────┐│   │ en       │  │destr.│
 └────────┘      │ pulsada  │     │   │MoveLks││   │ estación │  └──────┘
                 └──────────┘     │   └───────┘│   └──────────┘
                                  │   ┌───────┐│
                                  │   │AutoP. ││
                                  │   └───────┘│
                                  │   ┌───────┐│
                                  │   │Safety ││
                                  │   └───────┘│
                                  └────────────┘
                                    │
                                    ▼
                              Eventos disparados
                              (TrainEventDispatcher)
                              ├── onCrash → logging + economía
                              ├── onSensorEnter → scripts usuario
                              ├── onLink → audio
                              └── ...
```

---

## Modos de Juego (GameMode)

El enum `Model.GameMode` define 11 estados que determinan qué entrada de teclado procesa cada modo:

```
MENU ──→ RAILS ──→ DRIVE ──→ FORKS ──→ SEMAPHORES ──→ TRAINS ──→  LINK
  ↑        │         │         │            │             │         │
  │        │         │         │            │             │         │
  │        ▼         ▼         ▼            ▼             ▼         ▼
  │    Editar    Conducir   Desvíos    Semáforos       Crear     Acoplar
  │    vías       trenes                               trenes
  │
  └──── Enter vuelve al menú (excepto en DRIVE)

Teclas de cambio rápido: r(d.), d(rive), f(orks), s(emaphores),
t(rains), l(ink), u(nlink), n(stations), p(rogram)
```

La transición de modos **no pausa** la simulación — los trenes siguen moviéndose mientras el usuario cambia de herramienta.

---

## Gestión de Tiempo

### Scheduler de Ticks

El `SimulationScheduler` permite programar tareas para ejecutarse tras N ticks:

```java
// Ejemplo: cambiar un desvío dentro de 15 ticks
model.getScheduler().schedule(15, () -> fork.setAlternativeRoute(false));
```

Se usa para: retardos en cambios de desvío, temporizadores en scripts de automatización, y secuencias de acciones con retardo.

### Anti Spiral of Death (3D)

```java
stateTime -= 0.05f;
if (stateTime > 0.05f)
    stateTime = 0.05f;  // si el frame tardó más de 50ms en total
```

Si el renderizado se atrasa (ej: escena muy compleja), el acumulador evita que se ejecuten múltiples ticks de simulación en un solo frame, manteniendo la simulación estable.

---

## Archivos Clave

| Componente | Ruta |
|---|---|
| Main entry point | `src/main/java/letrain/LeTrain.java` |
| Presenter interface | `src/main/java/letrain/mvp/Presenter.java` |
| View interface | `src/main/java/letrain/mvp/View.java` |
| Model interface | `src/main/java/letrain/mvp/Model.java` |
| Model impl | `src/main/java/letrain/mvp/impl/Model.java` |
| SimulationController | `src/main/java/letrain/mvp/impl/SimulationController.java` |
| SimulationService | `src/main/java/letrain/mvp/impl/services/SimulationService.java` |
| SimulationScheduler | `src/main/java/letrain/utils/impl/SimulationScheduler.java` |
| TerminalPresenter | `src/main/java/letrain/mvp/impl/terminal/TerminalPresenter.java` |
| GraphicPresenter | `src/main/java/letrain/mvp/impl/graphic/GraphicPresenter.java` |
| TerminalView | `src/main/java/letrain/mvp/impl/terminal/TerminalView.java` |
| Gdx3DInputHandler | `src/main/java/letrain/mvp/impl/graphic/Gdx3DInputHandler.java` |
| CameraController | `src/main/java/letrain/mvp/impl/graphic/CameraController.java` |
| Gdx3DHud | `src/main/java/letrain/mvp/impl/graphic/Gdx3DHud.java` |
| Gdx3DRenderer | `src/main/java/letrain/visitor/gdx3d/Gdx3DRenderer.java` |
| RenderVisitor (2D) | `src/main/java/letrain/visitor/terminal/RenderVisitor.java` |
| RailTrackMaker | `src/main/java/letrain/mvp/impl/RailTrackMaker.java` |
| AutomationEngine | `src/main/java/letrain/mvp/impl/services/AutomationEngine.java` |
