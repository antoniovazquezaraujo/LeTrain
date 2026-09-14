#!/bin/sh
#
# Generates docs/developer/architecture/ClassIndex.md from the source tree.
#
# The index is meant to be consumed by humans (it lives in the Obsidian vault, so
# entries are [[wikilinks]]) and by agents (GEMINI.md tells them to consult it to
# locate components), which is why every line carries the module and uses a plain
# `package.Class` wikilink target.
#
# Regenerate after adding/removing/renaming classes and commit the result. CI runs
# this script and fails if the file is out of date.
#
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/developer/architecture/ClassIndex.md"
MODULES="core ui-terminal ui-graphic launcher-terminal launcher-graphic launcher-check"

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

for module in $MODULES; do
    base="$ROOT/$module/src/main/java"
    [ -d "$base" ] || continue
    find "$base" -name '*.java' ! -name 'package-info.java' -print | while read -r file; do
        rel="${file#"$base"/}"
        fqcn="$(printf '%s' "$rel" | sed 's|/|.|g; s|\.java$||')"
        dir="${rel%/*}"
        case "$dir" in
            letrain)    group="letrain (raíz)" ;;
            letrain/*)  first="${dir#letrain/}"; group="letrain.${first%%/*}" ;;
            *)          group="(otros)" ;;
        esac
        short="${fqcn##*.}"
        printf '%s\t%s\t%s\t%s\n' "$group" "$fqcn" "$short" "$module"
    done
done > "$tmp"

{
    printf '# Índice de Clases de LeTrain\n\n'
    printf '> **Generado automáticamente** con `tools/gen-class-index.sh`. No editar a mano.\n'
    printf '> Formato: `[[paquete.Clase|Clase]] (módulo)`. Pensado para Obsidian y para localizar código.\n'
    sort -k1,1 -k2,2 "$tmp" | awk -F '\t' '
        $1 != last {
            if (last != "") printf "\n"
            printf "## %s\n", $1
            last = $1
        }
        { printf "- [[%s|%s]] (%s)\n", $2, $3, $4 }
    '
} > "$OUT"

echo "Wrote $OUT"
