# ============================================================
# ADR-029 §5 — Generic train sound profile
#
# Plain text DSL (same style as soundscape 'styles/*.sound'):
#   key = value, '#' starts a comment, no external dependencies.
#
# Material lines declare the classpath path followed by metadata:
#   loop=<start_s>,<length_s>  loop window in seconds (mandatory on loops)
#   gain=<dB>                  per-material gain (default 0.0)
#   effort=<token>             optional, parsed and informational today
#   torque=<token>             optional, parsed and informational today
#
# v3 regenerated set: loop windows and gains come from BICHO's audit
# (2026-10-02) measured against the engine's real loop crossfade.
# Recipe: 'cruise' from the legacy master pitch-shifted with rubberband to
# BASE+2N semitones (BASE=-6.86, anchored to 'ralenti'). Transitions are
# rubberband pitch maps over 'cruise' (0.8 s pre-roll, 0.5 s extra tail):
# ups start at cruise[0], downs at cruise[5.4].
# Legacy aliases ('wagons' -> rolling, 'train-brakes' -> brakes) are
# resolved by the code, never written in descriptors (ADR-029 §5).
# ============================================================

profile     = generic
packVersion = 0

# --- Engine notches (loops) ---------------------------------
# gain is BICHO's single recommended value per notch (v3 audit).
notch.1  = sound/train/generic/notch-1.wav  loop=1.291791,3.314717  gain=+1.09
notch.2  = sound/train/generic/notch-2.wav  loop=6.197007,1.459025  gain=-0.70
notch.3  = sound/train/generic/notch-3.wav  loop=4.467120,2.061020  gain=+0.23
notch.4  = sound/train/generic/notch-4.wav  loop=1.338231,3.274671  gain=+1.28
notch.5  = sound/train/generic/notch-5.wav  loop=6.197007,1.445442  gain=-0.45
notch.6  = sound/train/generic/notch-6.wav  loop=6.191202,1.457982  gain=-0.74
notch.7  = sound/train/generic/notch-7.wav  loop=4.490340,3.878345  gain=-0.03
notch.8  = sound/train/generic/notch-8.wav  loop=1.303401,0.723492  gain=-0.32
notch.9  = sound/train/generic/notch-9.wav  loop=1.309206,0.585079  gain=+0.03
notch.10 = sound/train/generic/notch-10.wav loop=2.290249,1.578662  gain=+0.56

# --- Adjacent transitions (one-shots, ordered pairs) ---------
# One-shots: no loop windows. gain is BICHO's single v3 value per
# transition. effort/torque stay reserved for hand-labelled or
# licensor-provided metadata; not invented here.
trans.0-1  = sound/train/generic/trans-0-1.wav  gain=-2.19
trans.1-2  = sound/train/generic/trans-1-2.wav  gain=-2.15
trans.2-3  = sound/train/generic/trans-2-3.wav  gain=-2.29
trans.3-4  = sound/train/generic/trans-3-4.wav  gain=-2.06
trans.4-5  = sound/train/generic/trans-4-5.wav  gain=-1.71
trans.5-6  = sound/train/generic/trans-5-6.wav  gain=-2.15
trans.6-7  = sound/train/generic/trans-6-7.wav  gain=-1.88
trans.7-8  = sound/train/generic/trans-7-8.wav  gain=-2.05
trans.8-9  = sound/train/generic/trans-8-9.wav  gain=-1.92
trans.9-10 = sound/train/generic/trans-9-10.wav gain=-1.97

trans.1-0  = sound/train/generic/trans-1-0.wav  gain=-0.33
trans.2-1  = sound/train/generic/trans-2-1.wav  gain=-0.15
trans.3-2  = sound/train/generic/trans-3-2.wav  gain=+0.78
trans.4-3  = sound/train/generic/trans-4-3.wav  gain=-0.05
trans.5-4  = sound/train/generic/trans-5-4.wav  gain=+0.66
trans.6-5  = sound/train/generic/trans-6-5.wav  gain=+0.38
trans.7-6  = sound/train/generic/trans-7-6.wav  gain=+0.33
trans.8-7  = sound/train/generic/trans-8-7.wav  gain=+0.59
trans.9-8  = sound/train/generic/trans-9-8.wav  gain=+0.33
trans.10-9 = sound/train/generic/trans-10-9.wav gain=+0.17

# --- Idle, start, stop, rolling (loops/one-shots) ------------
# Loop windows and gains audited against the engine's real crossfade.
idle    = sound/train/generic/idle.wav    loop=13.673832,1.209773  gain=+1.15
start   = sound/train/generic/start.wav   gain=+0.81
stop    = sound/train/generic/stop.wav    gain=+0.66
rolling = sound/train/generic/rolling.wav loop=22.059252,3.039093  gain=+5.15

# --- Reserved: no measured material yet (fallback §3) --------
# brakes: legacy alias file sound/train-brakes.wav; loop points pending
#   measurement, so it stays out of the descriptor for now.
# horn:   never existed as material (one-shot, reserved).
# brakes = sound/train-brakes.wav  loop=<pending>  gain=<pending>
# horn   = sound/train/generic/horn.wav            gain=<pending>
