# ADR-028: Packs de sonido externos cifrados (contenido propietario bajo EULA)

## Estado: PROPUESTO (borrador para revisión)

## Contexto

- **Audio actual** (repartido según [[ADR-026-Audio-Domains]], aún en migración):
  - `core` (`letrain.audio`): síntesis procedural del tren (`TrainSynth`, `TrainSynthesizer`,
    `GrainEngine`), fuentes WAV puntuales (`WavSource`) y el motor de mezcla legacy.
  - `soundscape`: decorado aislado ([[ADR-023-Soundscape-Isolation]]) con muestras WAV **en el
    classpath del módulo** (`sounds/`), resueltas por `SampleResolver` (patrones tipo
    `sea/waves-*.wav`) y cargadas por `SampleLoader` a `SoundSample` (float mono).
  - `game-audio`: glue actual entre el juego y el decorado (`SoundscapeAmbience`), que carga el
    estilo `styles/valle-norte.sound` y el reproductor `AmbientPlayer`.
  - Disciplina de licencias vigente: `docs/developer/soundscape/catalog.csv` + `sounds/CREDITS.md`;
    solo CC0, CC BY, CC BY-SA, dominio público o síntesis propia. Regla de
    [[ADR-024-Soundscape-Material-Model]]: lo que no se pueda distribuir no entra en `sounds/`.
- **Caso nuevo**: el propietario de unas grabaciones de trenes licencia su uso para LeTrain. El
  **acuerdo escrito está pendiente**: hasta firmarlo, **ningún asset real entra en el proyecto**,
  ni siquiera temporalmente (los tests usarán WAVs sintéticos).
- **Frontera de licencias**:
  - El repositorio (código, docs y assets abiertos) es Apache-2.0 / licencias por fichero, y
    `docs/` se publica en GitHub Pages: todo lo que entra en el repo es público y redistribuible.
  - El pack propietario se distribuye **fuera del repo**, como artefacto separado y con **EULA
    propio**. Sus audios no son Apache-2.0 y no pueden acabar en los recursos de ningún módulo.
  - El **código** del contenedor y de las herramientas sí puede ser Apache-2.0: lo propietario es
    el asset y la clave, no la técnica.
- **[[ADR-023-Soundscape-Isolation]] §8** ("los assets de sonido viven en el repo, con licencias
  que permitan distribuir el juego") queda **superado para este caso**: el asset ni vive en el
  repo ni se distribuye bajo la licencia del juego. Para el catálogo abierto sigue vigente.
- **Modelo de amenaza** (se asume explícitamente; no se intenta vencer):
  - La JVM es decompilable y depurable: el nivel N3 (punto 3) encarece la **extracción estática**
    (literales, `strings`, decompilación simple), pero un depurador o un volcado de heap recuperan
    el material de clave. Esto **no es DRM**.
  - Los samples se descifran **en memoria**; un volcado de proceso o un depurador pueden
    capturarlos.
  - **Agujero analógico**: la salida de audio siempre se puede grabar. Es irreducible.
  - Un usuario puede copiar el `.ltsp` y redistribuirlo.
  - **Objetivo**: encarecer la extracción **casual** (abrir el pack, sacar los WAV y reutilizarlos)
    y detectar manipulación. La técnica **complementa** al acuerdo de licencia/EULA; **no lo
    sustituye**. Frente a un atacante motivado, la protección real es el contrato.

## Decisión (propuesta)

1. **Formato contenedor `.ltsp` (LeTrain Sound Pack)**: un único fichero binario con cabecera
   mágica, versión, índice de entradas y payloads cifrados. Los IDs de muestra son **rutas lógicas
   estables** compatibles con los patrones de material de los estilos
   (p. ej. `train/rodadura-01.wav`, `sea/waves-05.wav`). Esquema propuesto (enteros big-endian):

   | Campo | Tamaño | Descripción |
   |---|---|---|
   | `magic` | 4 | `LTSP` |
   | `formatVersion` | u16 | versión del contenedor (`1` inicial) |
   | `keyId` | u16 | identificador de la clave usada (rotación) |
   | `flags` | u16 | reservado, `0` |
   | `entryCount` | u32 | número de entradas |
   | `manifestLen` | u32 | longitud del manifiesto UTF-8 |
   | `manifest` | var | JSON: `packId`, `packVersion`, `createdAt`, `kdfSalt`, EULA/URL, procedencia |
   | `index` | var | una entrada por muestra (detalle abajo) |
   | `payloads` | var | por entrada: ciphertext + tag GCM (16 B) |

   Cada descriptor del índice contiene: `idLen` u16 + id UTF-8 (ruta lógica), `codec` u8
   (0 = PCM16 LE, 1 = float32 LE), `sampleRate` u32, `channels` u8, `plainLength` u32,
   `nonce` 12 B y `offset` u64 + `length` u32 dentro de `payloads`.

   El manifiesto incluye **`kdfSalt`** (32 B `SecureRandom`, base64) que liga la clave derivada a
   cada pack (punto 3).

   Límites defensivos al parsear (id ≤ 1 KiB, entradas ≤ 10 000, payload ≤ 100 MB). El detalle fino
   puede ajustarse en implementación sin reabrir este ADR.

2. **Cifrado e integridad**: **AES-256-GCM** con **nonce de 12 bytes único por entrada**
   (`SecureRandom` en el empaquetado; nunca reutilizar con la misma clave; la clave es la derivada
   en runtime del punto 3, nunca un literal). El tag de 128 bits
   autentica cada payload. Además, la cabecera, el manifiesto y el descriptor de cada entrada se
   pasan como **AAD**: manipular el índice, cambiar un `keyId` o mover un payload invalida el
   descifrado. Descifrado **siempre en memoria**; prohibido escribir a disco o a directorios
   temporales (nada de `AudioSystem.getAudioInputStream(File)` sobre el pack). Un fallo de
   integridad no revienta el juego: la entrada queda no disponible, se registra un `WARN` y se
   aplica la regla de fallback del punto 4.

3. **Clave y custodia (nivel N3 acordado)**. El nivel de protección es **N3** =
   **N1** (la clave no existe como literal en código/binario) + **N2** (build de release ofuscado)
   + **N3** (derivación en runtime e higiene de memoria). No se sube a **N4** (nativo/JNI) ni a
   **N5** (claves por usuario/servidor): el coste no cambia el techo real de la JVM pura (ver más
   abajo). La clave puede acabar en el **binario público** —es inseparable del modelo—, pero
   **nunca en el código fuente público**: la separación protege el proceso y la rotación, no la
   extracción final.
   - **Sin clave estática en ningún sitio (N1).** El build de release recibe el secreto por
     entorno (`LETRAIN_SOUNDPACK_KEY`) o `--key-file` (ruta fuera del repo) y genera en
     `target/generated-sources` fragmentos compilables (p. ej. `KeyFragments`):
     - cada pieza va **codificada** (rotaciones/XOR/máscaras derivadas de otras constantes del
       propio código), nunca como bytes de clave reconocibles;
     - el **layout es aleatorio por release** y se intercalan **constantes señuelo**;
     - el repositorio público no contiene la clave ni fragmentos reales: sin secreto se compila
       igual y el soporte de pack queda deshabilitado con un aviso (el repo se compila y testea
       sin secretos).
   - **Derivación en runtime (N3).** Los fragmentos se ensamblan y la clave final se deriva con
     JCA (`Mac`/SHA-256, sin dependencias):
     `K = HMAC-SHA256(fragmentos, "letrain-pack-v1" || packId || versiones || keyId || kdfSalt)`.
     Se elige HMAC-SHA256 (y no HKDF) porque una única salida de 32 B no necesita
     extract/expand separados y evita más superficie; si en el futuro hacen falta
     subclaves, se migrará a HKDF-SHA256.
     `kdfSalt` son **32 B aleatorios del manifiesto del pack**: el mismo secreto produce una clave
     distinta por pack y el prefijo fijo da **separación de dominio**. El ensamblado y la
     derivación se **ofuscan e inlinean** en el build de release, sin una función única y obvia
     tipo `getKey()` que sirva de punto de entrada.
   - **Ofuscación de release (N2) como requisito acompañante.** El build de release aplica
     renombrado y cifrado de strings (p. ej. ProGuard/R8 + transformación de strings, o
     herramienta comercial). Sin N2, N3 pierde gran parte de su valor: localizar el breakpoint en
     el bytecode sin ofuscar es trivial.
   - **Higiene de memoria (N3).** El material de clave nunca pasa por `String`/`StringBuilder`
     (inmutables, no borrables): se usan `byte[]`/`int[]`; se borran explícitamente
     (`Arrays.fill(..., 0)`) en cuanto el `Cipher` está inicializado; no hay logs ni stack traces
     con buffers. Sigue prohibido el volcado a disco o a temporales (punto 2).
   - **Límites de la JVM, asumidos.** `SecretKeySpec` guarda una copia no borrable, el JIT puede
     dejar copias en registros/pila y hay una ventana de heap dump durante el descifrado. **El
     análisis dinámico (breakpoint en `Cipher.init`/`doFinal`, volcado de heap) es el techo real
     en Java puro.** N3 frena la extracción estática (`strings`, `grep`, decompilación simple),
     **no** al atacante con depurador; el agujero analógico sigue irreducible. Se acepta como
     **riesgo residual**, sin prometer más de lo que da.
   - **Secreto en CI.** El workflow de release usa un **environment protegido con revisores** (no
     un secreto accesible desde cualquier rama); la clave no aparece en git, `docs/`, issues,
     PRs ni logs.

4. **Abstracción `SoundProvider` y fallback**:

   ```java
   public interface SoundProvider {
       List<String> find(String material);              // IDs que casan con el patrón
       SoundSample load(String id) throws IOException;  // descifra/carga en memoria
   }
   ```

   - `ClasspathSoundProvider` (por defecto): envuelve `SampleResolver` + `SampleLoader`; el
     comportamiento actual no cambia.
   - `SoundPackProvider`: lee el `.ltsp`, valida versión/integridad, descifra y devuelve el sample.
   - `FallbackSoundProvider`: encadena pack → classpath. Regla: el pack **sustituye** un material
     cuando tiene una entrada con el mismo ID; si la entrada falta, el pack no está o falla la
     integridad, se usa la del classpath y se registra `WARN`. Un pack ausente o roto **nunca deja
     al juego mudo**.
   - `AmbientPlayer` recibe el `SoundProvider` por constructor; el constructor actual sin provider
     delega en el classpath (retrocompatible).

5. **Ubicación de módulos**:
   - **Interfaz `SoundProvider` + `ClasspathSoundProvider` + `FallbackSoundProvider`**: en
     `soundscape.audio`, junto a `SampleResolver` / `SampleLoader` / `SoundSample`, que son los
     tipos que ya devuelve la carga.
     - Descartado poner la interfaz en `core` "junto a `AudioSample`": obligaría a que `soundscape`
       dependiera de `core` y rompería el aislamiento de ADR-023 (hoy `soundscape` no depende del
       juego); además ADR-026 ya prevé sacar el audio de `core`. Si en el futuro `train-audio` /
       `audio-core` necesitan packs, la interfaz se eleva a `audio-core` sin tocar el formato.
   - **Módulo nuevo `soundpack`**: lector del contenedor, descifrado y ensamblado de fragmentos +
     KDF (punto 3). Depende de `soundscape` (la interfaz) y de la JDK (JCA); **no** conoce el juego
     ni las UIs. Es el único punto con criptografía.
   - **`soundpack-tool`** (módulo aparte, no empaquetado con el juego): empaquetador y validador
     CLI; comparte las constantes del formato con `soundpack`.
   - **`game-audio`**: glue; decide si hay pack (configuración), construye
     `SoundPackProvider` + `FallbackSoundProvider` y los inyecta en el player. Las UIs siguen sin
     depender de `soundscape` (ADR-025).

6. **Herramienta CLI de empaquetado**:
   - `soundpack-tool package --input <carpeta WAV> --manifest <manifiesto> --out <pack.ltsp>`,
     con la clave por entorno o `--key-file` (nunca en git). El empaquetador **genera el
     `kdfSalt`** (32 B `SecureRandom`) y lo escribe en el manifiesto.
   - El manifiesto declara por entrada: ID lógico, fichero, procedencia/licencia y, opcionalmente,
     ejes de ADR-024 (`character`, `intensity`, `distance`, `tone`, `modulation`).
   - Validaciones: WAV canónico 44.1 kHz mono 16 bits (o conversión explícita), IDs duplicados,
     nonces únicos, orden determinista de entradas.
   - `soundpack-tool validate` verifica el pack con la misma KDF (secretos + `kdfSalt` del
     manifiesto) y, opcionalmente, lo cruza con un estilo `.sound`, listando los materiales del
     estilo que no están en el pack (caerán a classpath).

7. **Configuración y activación**: clave nueva en `letrain.cfg` (y, por tanto, en la sección
   `configuration` del `.ltr`): `soundpack.path` apuntando al `.ltsp`. Sin configurar, classpath.
   La resolución de rutas sigue el patrón de [[ADR-027-Clima-Y-Estaciones]] §7–8 (relativa al
   fichero de configuración o a una carpeta `soundpacks/`). El manifiesto permite avisar de
   versión de pack; la aceptación del EULA queda como pregunta abierta (fuera del alcance de este
   ADR).

8. **Guardas en git y CI**. Añadir a `.gitignore`:

   ```
   # Packs de sonido propietarios y claves (nunca en el repo)
   *.ltsp
   soundpacks/
   packs/
   soundpack-keys/
   *.key
   secrets/
   ```

   Los workflows públicos (CI y docs) no necesitan clave ni pack: los tests usarán WAVs
   sintéticos. El build de release la inyecta como secreto. Un PR no debe contener `.ltsp`, claves
   ni WAVs propietarios.

9. **Versionado, compatibilidad y rotación de clave**:
   - `formatVersion` distingue el contenedor; el lector rechaza versiones mayores con un error
     claro y mantiene la lectura de la versión anterior al menos una release.
   - `packVersion` / `packId` en el manifiesto permiten avisar de packs obsoletos. Los IDs lógicos
     son el contrato con los estilos: renombrar un material exige re-empaquetar (o dejar alias).
   - `keyId` en cabecera: el binario conoce un conjunto de claves (actual + anterior durante la
     transición). **Rotar la clave invalida los packs antiguos**; el procedimiento es:
     (1) generar clave nueva, (2) re-empaquetar y publicar pack nuevo, (3) build con ambas claves,
     (4) retirar la anterior en la siguiente release. Se documentará y anunciará a los usuarios.

10. **Alcance inicial y disciplina de assets**:
    - El contenedor y `SoundProvider` son **agnósticos al dominio** (ID + sample). La primera
      integración es `soundscape` (decorado), porque es donde ya existe el punto de carga.
    - Sonidos de tren: cuando ADR-026 materialice `train-audio` / `audio-core`, podrán reutilizar
      el mismo lector; **no se implementa ahora**.
    - Hasta que el acuerdo esté firmado y exista un pack real, **todo el trabajo se hace con WAVs
      sintéticos** generados en tests. Ningún asset real, ni siquiera temporal, en el repo.

## Consecuencias

- Positivas: contenido propietario utilizable sin contaminar la licencia del repo ni los assets
  abiertos; abstracción pequeña y testeable (el catálogo actual sigue sonando igual); el fallback
  garantiza que un pack ausente o roto no deja al juego mudo; GCM aporta integridad y detección de
  manipulación; el formato puede alojar futuros packs (DLC, comunidad con licencia) con el mismo
  mecanismo.
- Costes y riesgos: descarga extra y aceptación de un EULA para el usuario; complejidad de build
  (generador de fragmentos, ofuscación de release, secreto en CI, builds locales sin pack);
  rotación de clave que invalida packs antiguos;
  descifrado íntegro en RAM (aceptable para tomas de 10–30 s, a vigilar si el pack crece); la
  selección aleatoria de tomas puede cambiar según qué materiales sirva el pack (no afecta a la
  simulación ni al replay de ADR-020); riesgo de falsa sensación de seguridad si no se comunica
  bien que disuade, no impide.
- Dependencias: ADR-023 (aislamiento), ADR-024 (tomas y ejes), ADR-026 (dominios y futuro
  `audio-core`), ADR-027 (resolución de materiales de usuario). No afecta a la simulación.

## Alternativas consideradas

- **Carpeta externa sin cifrar**: lo más simple, pero los WAV se copian con un explorador de
  ficheros; no cumple el objetivo de disuasión ni aporta integridad.
- **Contenedor ofuscado sin cifrar** (ZIP con nombres opacos, XOR, cabeceras falsas): se revienta
  con `unzip` o un editor hexadecimal; no protege nada y complica el formato.
- **Cifrado en nativo / JNI** (nivel N4, C++/Rust): eleva el coste de extracción, pero rompe el
  "Java puro", multiplica plataformas y build para un beneficio marginal frente a la decompilación
  del resto.
- **Streaming desde servidor** (descifrado en servidor, samples por red): requiere infraestructura,
  caduca sin conexión y no encaja con un juego de escritorio.
- **Incluir el pack en los binarios** (JAR/instalador): contradice la decisión vinculante de que
  los assets nunca entran en el repo/recursos y multiplica artefactos; además el binario es
  decompilable.
- **DRM pesado, anti-debug o claves por máquina** (nivel N5): hostil al usuario, frágil y falsa
  seguridad; descartado.
- **Interfaz `SoundProvider` en `core`**: descartado por dependencia `soundscape → core` (ver
  punto 5).

## Plan por fases

1. **Este PR: solo ADR-028** (documentación; sin código ni assets).
2. `soundpack` + `soundpack-tool`: formato, lector/escritor, CLI, **generador de fragmentos de
   clave en el build de release** (`target/generated-sources`), **KDF HMAC-SHA256** e **higiene de
   memoria**, con tests con WAVs sintéticos y claves de prueba (ALEX/BICHO).
3. Seam en `soundscape`: `SoundProvider`, classpath, fallback e inyección en `AmbientPlayer`, con
   tests de regresión de que el catálogo actual suena igual.
4. Integración en `game-audio`: configuración, detección de pack, build de release (environment
   protegido y **ofuscación N2**) y flujo de aviso/EULA (a decidir en las preguntas abiertas).
5. Pack real (fuera del repo) solo tras firmar el acuerdo: empaquetado, validación con
   `soundpack-tool validate` y publicación.
6. Futuro: watermarking por usuario, sonidos de tren vía ADR-026 y hosting con control de acceso.
   Como documentación posterior quedan la plantilla de EULA y la guía de autoría/empaquetado.

## Preguntas abiertas

- **Hosting del pack**: GitHub Releases es público; ¿se usa itch.io (descarga con clave) u otro
  canal con control de acceso? El workflow de release público no debe incluir el pack.
- **EULA**: quién lo sirve, cuándo y cómo se acepta (checkbox al activar el pack, pantalla al
  primer uso, registro de la versión aceptada) y qué ocurre si se rechaza.
- **Watermarking por usuario**: ¿merece la pena una marca inaudible o una selección de tomas única
  por compra? Riesgo de degradar el audio y de falso positivo.
- **Alcance del pack**: ¿solo decorado o también sonidos de tren? Condiciona las fases 3–5 y el
  encaje con ADR-026.
- **Payload**: ¿float32 canónico o PCM16? ¿compresión (FLAC/OGG) si el pack crece?
- **Política ante fallo**: ¿`WARN` y fallback (propuesto) o modo estricto configurable que avise
  visiblemente cuando el pack configurado no carga?
- **Rotación**: frecuencia esperada, ¿una clave maestra o una por pack?, y cómo se ensaya sin
  invalidar packs vivos.
- **Verificación de "nunca a disco"**: técnica de test (espía de acceso a ficheros, revisión de
  código) para que BICHO pueda blindarlo.
- **Descifrado off-heap (N3+)**: ¿merece la pena sacar los buffers de descifrado del heap gestionado
  (`ByteBuffer` directo / FFM) para reducir la ventana de heap dump, a cambio de más complejidad?
  El zeroing deja de ser pregunta abierta: es decisión de mejor esfuerzo con los límites de JVM
  documentados (punto 3).
- **Contenido extra del pack**: ¿puede incluir overrides de estilo `.sound` o solo samples?
