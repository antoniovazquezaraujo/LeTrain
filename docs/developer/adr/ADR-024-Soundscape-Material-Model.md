# ADR-024: Modelo de materiales del decorado sonoro (familias, tomas y ejes)

## Estado: PROPUESTO (borrador para revisión)

## Contexto

- [[ADR-023-Soundscape-Isolation]] dejó el decorado aislado y propuso un mecanismo único de
  **bucle con saltos** que todavía no está implementado: hoy `AmbientVoice` reproduce una toma en
  bucle fijo y `AmbientPlayer` elige **un** material al azar por sonido y sesión.
- El catálogo empieza a crecer con material real. La primera auditoría (20 descargas de Pixabay)
  con `tools/audit_samples.py` muestra que un mismo fenómeno —"viento"— tiene caracteres muy
  distintos: con hojas de bosque (banda alta), costero (grave constante), tormenta (ráfagas
  marcadas), gélido, lejano (solo graves), encerrado (`underground`), inquietante (`cemetery`,
  `scary`)…
- Con un único fichero por sonido: 9 de cada 10 tomas no suenan nunca, el bucle fijo se nota, y no
  hay forma de describir ni mezclar carácter, intensidad o distancia.
- La propia auditoría destapa problemas prácticos recurrentes: tomas de 128–401 s (inservibles
  como bucle sin recorte), niveles dispares (−49 a −13 LUFS), orígenes a 48 kHz estéreo y MP3 con
  retardo de encoder.

## Decisión (propuesta)

1. **Familia = sonido del estilo** (`wind`, `rain`, `birds`). Una familia agrupa **N tomas**.
2. **Bucle con saltos por familia** (retoma ADR-023 §3): la voz reproduce una toma y salta a otra
   del grupo seleccionado con un **crossfade corto** (~0.5–1 s) en el empalme. La semilla se
   mantiene: mismas entradas, mismo resultado.
3. **Ejes de material** (etiquetas discretas de autoría, no curvas):
   - `character`: `leaves`, `water`, `rain`, `howl`, `rumble`, `enclosed`, `eerie`, `urban`…
   - `intensity`: `soft`, `medium`, `strong`
   - `distance`: `near`, `far`
   - `tone`: `bright`, `dark`
   El estado (clima, altura, zona, hora) selecciona 1–2 tomas por familia y las crossfadea. Las
   curvas (`presence`, sensibilidades) siguen viviendo en la familia, no en la toma.
4. **Capas**: una capa es un sonido del estilo (ya funciona: `wind` + `rain` + `birds` suman). Un
   mismo fenómeno puede declarar capas de detalle independientes (`wind` base y `wind.leaves`) con
   su propia presencia; no se añade jerarquía dura al motor.
5. **Formato de estilo** (retrocompatible; borrador):
   ```
   [sounds]
   wind = mountain/wind-*.wav             # patrón por defecto de la familia
   [takes]
   wind = wind/soft-pine-01.wav   character leaves intensity soft  distance near
   wind = wind/coast-rough-01.wav character water  intensity strong distance near
   ```
   La sección `[takes]` es opcional: sin ella todo sigue funcionando como hoy.
6. **Carga y peso**: carga diferida por familia (hoy se cargan todos los samples al construir
   `AmbientPlayer`); presupuesto de repo a fijar (orientativo: 10–30 s por toma, ≤ 2 MB), WAV
   16/44.1 como formato canónico y compresión (FLAC/OGG + SPI) en un ADR futuro solo si el
   catálogo lo exige.

## Consecuencias

- Positivas: variedad real dentro de una misma sesión; estilos declarativos (añadir tomas es añadir
  datos); carácter/intensidad/distancia seleccionables sin multiplicar curvas; auditoría
  reproducible de calidad y licencias con `tools/audit_samples.py`.
- Costes y riesgos: implementar bien el crossfade del bucle (riesgo de clics si el empalme es
  malo); más disciplina de catálogo (peso, licencias, normalización de nivel); decidir umbrales de
  selección por ejes sin complicar el motor.
- Dependencias: retoma el "bucle con saltos" de ADR-023; no afecta a la simulación ni al juego.

## Alternativas consideradas

- **Mantener un fichero por sonido**: descartado; la variedad desaparece y la repetición se nota.
- **Meter docenas de "sonidos" distintos en `[zones]`**: descartado; explota la combinatoria y
  duplica curvas por cada variante.
- **Codificar los ejes en el nombre del fichero** (`wind_leaves_soft_near.wav`): frágil ante
  renombrados y descargas; se prefiere la tabla del estilo.
- **Metadatos en el WAV (LIST/INFO)**: descartado; la mayoría de las descargas no los traen y el
  formato canónico no los necesita.
- **Remuestrear en reproducción** para admitir cualquier tasa: descartado; la auditoría normaliza a
  44.1 kHz.
- **Jerarquía dura de capas en el motor**: descartado por ahora; los sonidos ya suman.

## Plan por fases

1. Bucle con saltos + crossfade por familia (es lo que desbloquea la variedad).
2. Sección `[takes]`, ejes y selección por estado.
3. Carga diferida y presupuesto de repo.
4. (Futuro) compresión/streaming si el catálogo lo pide.

## Preguntas abiertas

- ¿Bastan 2 tomas simultáneas por familia o hacen falta más?
- ¿Duración del crossfade? Se fija oyendo en el reproductor.
- Umbrales de `intensity` y `distance`: ¿los fija el autor a mano o se derivan de medidas (LUFS,
  balance espectral) como ya hace la auditoría?
- ¿Normalizamos todas las tomas a un objetivo común (p. ej. −23 LUFS) para que los saltos de nivel
  no se noten al crossfadear?
- Presupuesto definitivo de peso del catálogo en el repo.
