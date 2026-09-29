#!/usr/bin/env python3
"""
Build the emoji list for the keyboard's emoji panel.

Output: app/src/main/assets/emoji.tsv — one line per emoji: <category index> <TAB> <emoji>
Categories (index order): smileys, people, animals, food, travel, activities, objects, symbols, flags.

Source: Unicode emoji-test.txt (fully-qualified sequences only; skin-tone variants left out so the
grid stays short). Emoji the phone's font can't draw are filtered out at runtime.

Usage:  python3 tools/build_emoji.py
"""
import os
import subprocess

URL = "https://www.unicode.org/Public/emoji/15.1/emoji-test.txt"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "emoji.tsv")

GROUPS = {
    "Smileys & Emotion": 0,
    "People & Body": 1,
    "Animals & Nature": 2,
    "Food & Drink": 3,
    "Travel & Places": 4,
    "Activities": 5,
    "Objects": 6,
    "Symbols": 7,
    "Flags": 8,
}
SKIN_TONES = {chr(c) for c in range(0x1F3FB, 0x1F400)}


def main() -> None:
    text = subprocess.run(["curl", "-fsSL", URL], check=True, capture_output=True).stdout.decode("utf-8")
    group = None
    out = []
    for line in text.splitlines():
        if line.startswith("# group:"):
            group = GROUPS.get(line.split(":", 1)[1].strip())
            continue
        if group is None or not line or line.startswith("#") or "; fully-qualified" not in line:
            continue
        codes = line.split(";", 1)[0].split()
        emoji = "".join(chr(int(c, 16)) for c in codes)
        if any(ch in SKIN_TONES for ch in emoji):
            continue
        out.append(f"{group}\t{emoji}")
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print(f"{len(out)} emoji → {os.path.abspath(OUT)}")


if __name__ == "__main__":
    main()
