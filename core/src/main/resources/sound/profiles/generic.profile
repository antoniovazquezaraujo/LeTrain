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
# All measured values come from BICHO's audit (2026-10-02, fixtures v2).
# Legacy aliases ('wagons' -> rolling, 'train-brakes' -> brakes) are
# resolved by the code, never written in descriptors (ADR-029 §5).
# ============================================================

profile     = generic
packVersion = 0

# --- Engine notches (loops) ---------------------------------
# gain normalizes each notch to the measured RMS median (-10.22 dBFS):
# gain = -10.22 - measured RMS (fixtures v2, notches section).
notch.1  = sound/train/generic/notch-1.wav  loop=4.477937,4.252766  gain=-0.57
notch.2  = sound/train/generic/notch-2.wav  loop=6.301451,2.429252  gain=-0.36
notch.3  = sound/train/generic/notch-3.wav  loop=6.684286,2.046417  gain=0.44
notch.4  = sound/train/generic/notch-4.wav  loop=6.685102,2.045601  gain=0.18
notch.5  = sound/train/generic/notch-5.wav  loop=4.734195,3.996508  gain=0.05
notch.6  = sound/train/generic/notch-6.wav  loop=3.747370,4.983333  gain=-0.88
notch.7  = sound/train/generic/notch-7.wav  loop=4.629683,4.101020  gain=-0.54
notch.8  = sound/train/generic/notch-8.wav  loop=7.763832,0.966871  gain=0.35
notch.9  = sound/train/generic/notch-9.wav  loop=3.643175,5.087528  gain=-0.05
notch.10 = sound/train/generic/notch-10.wav loop=7.508707,1.221995  gain=0.37

# --- Start transitions 0<->1 (one-shots, ordered pairs) ------
# 0->1 / 1->0 start transitions: loop gains baked in (idle unity, notch-1 part -2.55 dB);
# with gain=1.98 (idle's) each half matches its loop exactly (sample-exact handover, verified).
trans.0-1 = sound/train/generic/trans-0-1.wav gain=1.98
trans.1-0 = sound/train/generic/trans-1-0.wav gain=1.98

# --- Adjacent transitions (one-shots, ordered pairs) ---------
# No gain today (default 0.0): level balance is done by the notch gains
# and the runtime mixing stage (PR C/D). effort/torque are reserved for
# hand-labelled or licensor-provided metadata; not invented here.
trans.1-2  = sound/train/generic/trans-1-2.wav
trans.2-3  = sound/train/generic/trans-2-3.wav
trans.3-4  = sound/train/generic/trans-3-4.wav
trans.4-5  = sound/train/generic/trans-4-5.wav
trans.5-6  = sound/train/generic/trans-5-6.wav
trans.6-7  = sound/train/generic/trans-6-7.wav
trans.7-8  = sound/train/generic/trans-7-8.wav
trans.8-9  = sound/train/generic/trans-8-9.wav
trans.9-10 = sound/train/generic/trans-9-10.wav

trans.2-1  = sound/train/generic/trans-2-1.wav
trans.3-2  = sound/train/generic/trans-3-2.wav
trans.4-3  = sound/train/generic/trans-4-3.wav
trans.5-4  = sound/train/generic/trans-5-4.wav
trans.6-5  = sound/train/generic/trans-6-5.wav
trans.7-6  = sound/train/generic/trans-7-6.wav
trans.8-7  = sound/train/generic/trans-8-7.wav
trans.9-8  = sound/train/generic/trans-9-8.wav
trans.10-9 = sound/train/generic/trans-10-9.wav

# --- Idle, start, stop, rolling (loops/one-shots) ------------
# idle: +1.98 dB towards the notch median; it peaks at -0.01 dBFS, so any
# positive gain needs the float mixing headroom stage (PR C/D).
idle    = sound/train/generic/idle.wav    loop=14.579841,3.624603  gain=1.98
start   = sound/train/generic/start.wav   gain=0.0
stop    = sound/train/generic/stop.wav    gain=0.0

# rolling: loop A recommended by the audit (level-matched, no clipped
# samples). +5.15 dB to legacy 'wagons' bed (-19.94 dBFS), volume by
# speed in runtime.
rolling = sound/train/generic/rolling.wav loop=22.059252,3.039093  gain=5.15
# Alternative loop B (audit cross-check, not active):
# rolling = sound/train/generic/rolling.wav loop=40.402698,5.895193  gain=1.60

# --- Reserved: no measured material yet (fallback §3) --------
# brakes: legacy alias file sound/train-brakes.wav; loop points pending
#   measurement, so it stays out of the descriptor for now.
# horn:   never existed as material (one-shot, reserved).
# brakes = sound/train-brakes.wav  loop=<pending>  gain=<pending>
# horn   = sound/train/generic/horn.wav            gain=<pending>
