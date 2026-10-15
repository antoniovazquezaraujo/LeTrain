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
# v3 regenerated set: long loop windows (>= 4 s; 6.9-8.4 s chosen) played
# with simple WRAP and audited against the engine's real loop crossfade;
# gains target the measured body at -10.62 dBFS (BICHO audit, 2026-10-02).
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
# Long windows, simple WRAP: no audible repetition; gain targets -10.62 dBFS.
notch.1  = sound/train/generic/notch-1.wav  loop=0.435556,7.789773  gain=0.46
notch.2  = sound/train/generic/notch-2.wav  loop=1.265420,7.413515  gain=-0.42
notch.3  = sound/train/generic/notch-3.wav  loop=1.201633,7.052063  gain=0.43
notch.4  = sound/train/generic/notch-4.wav  loop=1.422245,6.951519  gain=0.65
notch.5  = sound/train/generic/notch-5.wav  loop=1.410635,6.949252  gain=-0.04
notch.6  = sound/train/generic/notch-6.wav  loop=0.719819,7.681202  gain=-0.46
notch.7  = sound/train/generic/notch-7.wav  loop=0.725601,7.668299  gain=0.06
notch.8  = sound/train/generic/notch-8.wav  loop=1.271293,7.419274  gain=-0.54
notch.9  = sound/train/generic/notch-9.wav  loop=1.213243,7.046145  gain=0.04
notch.10 = sound/train/generic/notch-10.wav loop=1.387392,6.931361  gain=-0.40

# --- Adjacent transitions (one-shots, ordered pairs) ---------
# One-shots: no loop windows. gain is BICHO's single v3 value per
# transition. 0<->1 are single-source ramps (trans-0-1 over 'ralenti',
# trans-1-0 over 'cruise') so the entry has no internal timbre jump; the
# engine crossfade covers the handover into the destination loop.
# effort/torque stay reserved for hand-labelled or licensor-provided
# metadata; not invented here.
trans.0-1  = sound/train/generic/trans-0-1.wav  gain=1.54
trans.1-2  = sound/train/generic/trans-1-2.wav  gain=-2.15
trans.2-3  = sound/train/generic/trans-2-3.wav  gain=-2.29
trans.3-4  = sound/train/generic/trans-3-4.wav  gain=-2.06
trans.4-5  = sound/train/generic/trans-4-5.wav  gain=-1.71
trans.5-6  = sound/train/generic/trans-5-6.wav  gain=-2.15
trans.6-7  = sound/train/generic/trans-6-7.wav  gain=-1.88
trans.7-8  = sound/train/generic/trans-7-8.wav  gain=-2.05
trans.8-9  = sound/train/generic/trans-8-9.wav  gain=-1.92
trans.9-10 = sound/train/generic/trans-9-10.wav gain=-1.97

trans.1-0  = sound/train/generic/trans-1-0.wav  gain=-0.67
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
# idle/rolling use the same long-window WRAP; rolling adopts audit
# alternative B (residual -33.4 dB vs -25.5 dB, 2.8x longer). start/stop
# are one-shots.
idle    = sound/train/generic/idle.wav    loop=6.965986,7.054263  gain=1.13
start   = sound/train/generic/start.wav   gain=0.67
stop    = sound/train/generic/stop.wav    gain=0.52
rolling = sound/train/generic/rolling.wav loop=23.521769,8.432585  gain=6.71

# --- Reserved: no measured material yet (fallback §3) --------
# brakes: legacy alias file sound/train-brakes.wav; loop points pending
#   measurement, so it stays out of the descriptor for now.
# horn:   never existed as material (one-shot, reserved).
# brakes = sound/train-brakes.wav  loop=<pending>  gain=<pending>
# horn   = sound/train/generic/horn.wav            gain=<pending>
