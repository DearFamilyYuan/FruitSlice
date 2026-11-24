#!/usr/bin/env python3
"""Generate short synth sound effects for the game (stdlib only)."""
import math, os, struct, wave

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")

def write_wav(name, samples):
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", max(-32767, min(32767, int(s * 32767)))) for s in samples))

def pop(freq=700, dur=0.16, decay=18):
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        f = freq * (1 + 2.2 * math.exp(-t * 40))  # quick upward chirp = juicy pop
        env = math.exp(-t * decay)
        out.append(0.85 * env * math.sin(2 * math.pi * f * t))
    return out

def slice_snd(dur=0.22, decay=13):
    n = int(SR * dur)
    out = []
    seed = 12345
    for i in range(n):
        t = i / SR
        env = math.exp(-t * decay)
        seed = (1103515245 * seed + 12345) & 0x7FFFFFFF
        noise = (seed / 0x3FFFFFFF) - 1.0
        tone = math.sin(2 * math.pi * (2400 - 1600 * t / dur) * t)
        out.append(0.55 * env * (0.55 * noise + 0.45 * tone))
    return out

def cheer(dur=0.5, decay=6):
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        env = math.exp(-t * decay)
        v = (math.sin(2 * math.pi * 523 * t) +
             0.7 * math.sin(2 * math.pi * 659 * t) +
             0.6 * math.sin(2 * math.pi * 784 * t))
        out.append(0.4 * env * v)
    return out

os.makedirs(OUT, exist_ok=True)
write_wav("pop.wav", pop())
write_wav("slice.wav", slice_snd())
write_wav("cheer.wav", cheer())
print("wrote:", sorted(os.listdir(OUT)))
