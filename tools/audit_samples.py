#!/usr/bin/env python3
"""Audits a folder of audio downloads for the soundscape catalog.

For every audio file it:

1. Converts it to the project format (16-bit PCM WAV, mono, 44.1 kHz) so it can be
   auditioned exactly as the game will hear it.
2. Measures level, loudness range, spectral balance, crest factor, clipping and DC offset.
3. Writes a spectrogram PNG (the reviewer can read structure without listening).
4. Fingerprints the audio (Chromaprint, when ``fpcalc`` is available) to spot duplicates.
5. Appends a row to ``catalog.csv`` with suggested tags and empty columns for the final
   human curation (character / intensity / distance / notes).

Usage:
    tools/audit_samples.py <input-dir> [output-dir]

Requires ffmpeg/ffprobe; fpcalc is optional. Output defaults to ``./audit-output``.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

AUDIO_SUFFIXES = {".mp3", ".wav", ".ogg", ".oga", ".flac", ".m4a"}
TARGET_RATE = 44100

# Keyboard hints from file names; the curator confirms or replaces them.
NAME_TAGS = {
    "wind": "wind",
    "snow": "snow",
    "storm": "storm",
    "coast": "water",
    "sea": "water",
    "lake": "water",
    "forest": "leaves",
    "tree": "leaves",
    "leaves": "leaves",
    "rain": "rain",
    "underground": "enclosed",
    "cemetery": "eerie",
    "scary": "eerie",
    "howl": "howl",
    "soft": "soft",
    "gentle": "soft",
    "rough": "rough",
    "strong": "rough",
    "far": "far",
    "terrace": "urban",
    "house": "urban",
}

CSV_FIELDS = [
    "file",
    "duration_s",
    "rate_hz",
    "channels",
    "bitrate_kbps",
    "peak_db",
    "rms_db",
    "lufs",
    "lra_lu",
    "low_db",
    "mid_db",
    "high_db",
    "crest",
    "peak_count",
    "dc_offset",
    "fingerprint",
    "suggested_tags",
    "character",
    "intensity",
    "distance",
    "notes",
]


def run(command: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, capture_output=True, text=True, check=False)


def probe(path: Path) -> dict:
    result = run(["ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json",
                  str(path)])
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout)


def convert(path: Path, target: Path) -> None:
    result = run(["ffmpeg", "-v", "error", "-y", "-i", str(path), "-ac", "1", "-ar",
                  str(TARGET_RATE), "-c:a", "pcm_s16le", str(target)])
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip())


def astats(path: Path, filters: str = "") -> dict[str, float]:
    chain = "astats=metadata=1:reset=0,ametadata=print:file=-"
    if filters:
        chain = f"{filters},{chain}"
    result = run(["ffmpeg", "-hide_banner", "-i", str(path), "-af", chain, "-f", "null", "-"])
    metrics: dict[str, float] = {}
    for key, value in re.findall(r"([A-Za-z][A-Za-z ]*): (-?[\d.]+)", result.stderr):
        if key.strip() not in metrics:  # first channel block; mono input
            metrics[key.strip()] = float(value)
    # Raw metadata (stdout): keeps the last value per key, e.g. Peak_count for clipping.
    for key, value in re.findall(r"lavfi\.astats\.Overall\.([A-Za-z_]+)=(-?[\d.eE+-]+)",
                                 result.stdout):
        try:
            metrics[key] = float(value)
        except ValueError:
            pass  # non-numeric entries such as -inf noise floor
    return metrics


def loudness(path: Path) -> tuple[str, str]:
    result = run(["ffmpeg", "-hide_banner", "-i", str(path), "-af", "ebur128", "-f", "null",
                  "-"])
    integrated = re.findall(r"I:\s*(-?[\d.]+)\s*LUFS", result.stderr)
    range_lu = re.findall(r"LRA:\s*(-?[\d.]+)\s*LU", result.stderr)
    return (integrated[-1] if integrated else "", range_lu[-1] if range_lu else "")


def spectrogram(path: Path, target: Path) -> None:
    run(["ffmpeg", "-v", "error", "-y", "-i", str(path), "-lavfi",
         "showspectrumpic=s=1200x400:legend=disabled", str(target)])


def fingerprint(path: Path) -> str:
    if not shutil.which("fpcalc"):
        return ""
    result = run(["fpcalc", "-json", "-length", "120", str(path)])
    if result.returncode != 0:
        return ""
    data = json.loads(result.stdout)
    code = data.get("fingerprint", "")
    return hashlib.sha1(code.encode()).hexdigest()[:12] if code else ""


def suggested_tags(name: str, metrics: dict[str, float]) -> str:
    words = set(re.split(r"[^a-z]+", name.lower()))
    tags = {NAME_TAGS[word] for word in words if word in NAME_TAGS}
    rms = metrics.get("RMS level dB")
    if rms is not None:
        tags.add("loud" if rms > -20 else "medium" if rms > -28 else "soft")
    mid = metrics.get("mid_db")
    high = metrics.get("high_db")
    if mid is not None and high is not None:
        tags.add("bright" if high > mid - 6 else "dark")
    return ";".join(sorted(tags))


def audit(path: Path, output: Path) -> dict:
    stem = path.stem
    wav = output / "wav" / f"{stem}.wav"
    converted = output / "spectrograms" / f"{stem}.png"
    convert(path, wav)
    spectrogram(wav, converted)

    info = probe(path)
    stream = info["streams"][0]
    overall = astats(wav)
    low = astats(wav, "lowpass=f=200")
    mid = astats(wav, "highpass=f=200,lowpass=f=2000")
    high = astats(wav, "highpass=f=2000")
    lufs, lra = loudness(wav)

    row = {
        "file": path.name,
        "duration_s": f"{float(info['format']['duration']):.1f}",
        "rate_hz": stream.get("sample_rate", ""),
        "channels": stream.get("channels", ""),
        "bitrate_kbps": f"{int(info['format'].get('bit_rate', 0)) // 1000}",
        "peak_db": overall.get("Peak level dB", ""),
        "rms_db": overall.get("RMS level dB", ""),
        "lufs": lufs,
        "lra_lu": lra,
        "low_db": low.get("RMS level dB", ""),
        "mid_db": mid.get("RMS level dB", ""),
        "high_db": high.get("RMS level dB", ""),
        "crest": overall.get("Crest factor", ""),
        "peak_count": overall.get("Peak_count", ""),
        "dc_offset": overall.get("DC offset", ""),
        "fingerprint": fingerprint(path),
        "character": "",
        "intensity": "",
        "distance": "",
        "notes": "",
    }
    row["suggested_tags"] = suggested_tags(path.name, row)
    return row


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="folder with the downloaded audio")
    parser.add_argument("output", type=Path, nargs="?", default=Path("audit-output"))
    args = parser.parse_args()

    files = sorted(path for path in args.input.iterdir()
                   if path.suffix.lower() in AUDIO_SUFFIXES)
    if not files:
        print(f"no audio files in {args.input}", file=sys.stderr)
        return 1

    for folder in ("wav", "spectrograms"):
        (args.output / folder).mkdir(parents=True, exist_ok=True)

    rows = []
    for index, path in enumerate(files, start=1):
        print(f"[{index}/{len(files)}] {path.name}")
        rows.append(audit(path, args.output))

    with (args.output / "catalog.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        writer.writeheader()
        writer.writerows(rows)

    print(f"\ncatalog: {args.output / 'catalog.csv'}")
    print(f"spectrograms: {args.output / 'spectrograms'}")

    duplicates = {}
    for row in rows:
        if row["fingerprint"]:
            duplicates.setdefault(row["fingerprint"], []).append(row["file"])
    for files_with_same_code in duplicates.values():
        if len(files_with_same_code) > 1:
            print(f"duplicates: {', '.join(files_with_same_code)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
