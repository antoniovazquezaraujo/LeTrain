#!/usr/bin/env python3
"""Renders layered ambience mixes from simple recipe files.

A recipe is a plain-text file; ``layer:`` starts a layer and the indented keys
configure it:

    name: distant-storm
    duration: 30
    target_lufs: -23

    layer:
      source: rain.wav          # file name, searched in the --samples folders
      gain_db: -4               # fader
      lowpass: 8000             # air absorption (distance)
      loop: true                # repeat until the mix ends
    layer:
      source: thunder.wav
      start: 2                  # take offset in seconds
      duration: 12              # take length
      gain_db: -10
      lowpass: 3000
      echo: tunnel              # optional reflection preset

Usage:
    tools/mix_layers.py recipes/*.mix --samples samples --out out

Requires ffmpeg/ffprobe; every mix is rendered as 16-bit PCM mono 44.1 kHz WAV,
with a short fade at both ends and an optional EBU R128 normalization.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

ECHO_PRESETS = {
    "tunnel": "aecho=0.8:0.9:60|120|240:0.5|0.35|0.2",
    "room": "aecho=0.8:0.7:25|55:0.35|0.2",
}


def parse_recipe(path: Path) -> dict:
    recipe: dict = {"layers": []}
    current: dict | None = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        if line == "layer:":
            current = {}
            recipe["layers"].append(current)
            continue
        if ":" not in line:
            raise ValueError(f"{path.name}: expected 'key: value', got {line!r}")
        key, value = (part.strip() for part in line.split(":", 1))
        target = current if current is not None else recipe
        target[key] = value
    if not recipe["layers"]:
        raise ValueError(f"{path.name}: no layers")
    return recipe


def resolve_source(name: str, roots: list[Path]) -> Path:
    for root in roots:
        candidate = root / name
        if candidate.exists():
            return candidate
    raise FileNotFoundError(f"source not found in {[str(r) for r in roots]}: {name}")


def layer_input(layer: dict, roots: list[Path]) -> list[str]:
    source = resolve_source(layer["source"], roots)
    command = []
    if float(layer.get("start", 0)) > 0:
        command += ["-ss", layer["start"]]
    if layer.get("loop", "false").lower() == "true":
        command += ["-stream_loop", "-1"]
    return command + ["-i", str(source)]


def layer_filters(layer: dict, duration: float) -> str:
    filters = [f"volume={layer.get('gain_db', 0)}dB"]
    if layer.get("highpass"):
        filters.append(f"highpass=f={layer['highpass']}")
    if layer.get("lowpass"):
        filters.append(f"lowpass=f={layer['lowpass']}")
    if layer.get("echo"):
        preset = ECHO_PRESETS.get(layer["echo"])
        if preset is None:
            raise ValueError(f"unknown echo preset: {layer['echo']}")
        filters.append(preset)
    take = float(layer.get("duration", duration))
    fade = min(0.8, take / 4)
    filters.append(f"afade=t=in:st=0:d={fade:.2f}")
    filters.append(f"afade=t=out:st={take - fade:.2f}:d={fade:.2f}")
    return ",".join(filters)


def render(recipe: dict, path: Path, roots: list[Path], out: Path) -> Path:
    duration = float(recipe.get("duration", 30))
    layers = recipe["layers"]
    command = ["ffmpeg", "-v", "error", "-y"]
    for layer in layers:
        command += layer_input(layer, roots)
        if layer.get("loop", "false").lower() == "true":
            command += ["-t", str(duration)]
    chains = []
    for index, layer in enumerate(layers):
        chains.append(f"[{index}:a]{layer_filters(layer, duration)}[l{index}]")
    mixed = "".join(f"[l{index}]" for index in range(len(layers)))
    chain = f"{mixed}amix=inputs={len(layers)}:duration=longest:normalize=0[mixed]"
    chains.append(chain)
    tail = "alimiter=limit=0.95,afade=t=in:st=0:d=0.4"
    tail += f",afade=t=out:st={duration - 1:.2f}:d=1"
    if recipe.get("target_lufs"):
        tail += f",loudnorm=I={recipe['target_lufs']}:TP=-1.5:LRA=11"
    chains.append(f"[mixed]{tail}[out]")
    command += ["-filter_complex", ";".join(chains), "-map", "[out]", "-t", str(duration),
                "-ar", "44100", "-ac", "1", "-c:a", "pcm_s16le"]
    output = out / (recipe.get("name", path.stem) + ".wav")
    command.append(str(output))
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise RuntimeError(f"{path.name}: {result.stderr.strip()}")
    return output


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("recipes", nargs="+", type=Path)
    parser.add_argument("--samples", action="append", default=[], type=Path,
                        help="folder searched for layer sources (repeatable)")
    parser.add_argument("--out", default=Path("mix-output"), type=Path)
    args = parser.parse_args()

    roots = args.samples or [Path(".")]
    args.out.mkdir(parents=True, exist_ok=True)
    for path in args.recipes:
        try:
            output = render(parse_recipe(path), path, roots, args.out)
            print(f"{path.name:<28} -> {output}")
        except (ValueError, FileNotFoundError, RuntimeError) as error:
            print(f"{path.name:<28} !! {error}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
