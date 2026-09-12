# LeTrain - Escenarios (`.ltr`)

Un **escenario** es una receta en texto plano que reconstruye un mundo a partir de una **semilla**
de terreno: la infraestructura, las condiciones iniciales y el programa del operador. *No* es una
partida guardada.

| | Partida guardada (savegame) | Escenario (`.ltr`) |
|---|---|---|
| Qué es | El **estado** actual (posiciones, dinero, trenes en marcha) | La **receta** que construye un mundo |
| Continuar donde lo dejaste | Sí | No |
| Reproducible en un mundo nuevo | No | Sí (misma semilla → mismo mundo) |
| Construcción libre (sin costes) | No | Sí |

Ambos artefactos son autocontenidos y nunca se mezclan.

## Formato del fichero

```
# LeTrain scenario v1
seed 123
configuration {
  threshold.WATER=130
}
on build {
  go 0,0; face e; write 5;
  new st;
}
on start {
  semaphore 1 close;
}
program {
  sensor 1 on train enter { semaphore 1 open; }
}
```

- `seed <n>` — **obligatorio**. La semilla del terreno; la misma semilla reproduce el mismo terreno.
- `configuration { ... }` — ajustes como `clave=valor` (las mismas claves que `letrain.cfg`). El
  escenario lleva sus propias reglas y **tienen preferencia sobre** el fichero `letrain.cfg` local.
- `on build { ... }` — los comandos de construcción (vías, elementos, trenes). Se reproducen una vez,
  primero.
- `on start { ... }` — condiciones iniciales opcionales aplicadas justo después de construir (por
  ejemplo, el estado de los semáforos).
- `program { ... }` — el script de automatización (itinerarios, triggers). Se instala después de
  construir, así puede referenciar los elementos recién creados.

Todas las secciones son opcionales y pueden aparecer en cualquier orden; `#` inicia un comentario. Un
fichero plano con solo una semilla y comandos (sin llaves) es válido y se trata como `on build`, así
que los escenarios antiguos siguen funcionando.

## Crear y jugar

1. Construye y edita (teclado o consola). La grabación se conmuta con **`R`**; el diario es lo que
   reproduce una exportación.
2. Abre el editor (`p`) y **Exporta** (o usa el comando `export` de la consola). Obtienes un `.ltr`.
3. **Importa** un `.ltr` (editor) o juégalo: el mundo se reconstruye en un modelo nuevo con esa
   semilla, en **modo construcción libre** (sin costes) y con la grabación activada.

Durante la grabación, las ediciones se pueden deshacer/rehacer con `u` / **Ctrl+R** (o `undo;` / `redo;`).

## Validar un escenario

`letrain-check` valida un `.ltr` **sin lanzar el juego** (solo sintaxis):

```bash
letrain-check mi-escenario.ltr
```

- salida `0` si es válido, `1` si hay diagnósticos, `2` para errores de uso/IO.
- cada diagnóstico se imprime como `ruta:línea:col: error: mensaje`, para que editores como vim o
  VS Code salten directos a la línea.

## El editor

Pulsa `p` para abrir el **LeTrain Editor**. Sus tres pestañas forman el escenario:

- **Scenario** — `seed` + `on build` + `on start`.
- **Program** — el bloque `program { ... }`.
- **Config** — los ajustes de `configuration { ... }`.

La referencia rápida de la derecha muestra solo los comandos que tienen sentido en la pestaña
activa; al elegir uno se inserta en una línea nueva. El pie tiene `Save`/`Load` (partida guardada),
`Export`/`Import` (fichero de escenario), `Refresh` (recargar desde el mundo), `Reprogram` (aplicar
el Program) y `Rebuild` (validar y jugar el escenario). `Esc` cierra el editor. Si hay errores,
aparece una lista con scroll que te lleva a la línea del fallo.

---

Ver también: **[grammar_es.md](grammar_es.md)** para el lenguaje de comandos y la consola.
