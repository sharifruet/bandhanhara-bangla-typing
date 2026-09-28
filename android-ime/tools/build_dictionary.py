#!/usr/bin/env python3
"""
Build the word-suggestion dictionary bundled with the Android keyboard.

Outputs (into app/src/main/assets/):
  dict_words.tsv    word <TAB> frequency          — completion candidates, most frequent first
  dict_bigrams.tsv  previous <TAB> next <TAB> count — next-word predictions ("<s>" = sentence start)

Sources (downloaded on each run):
  • Word frequencies — hermitdave/FrequencyWords, bn_50k (OpenSubtitles 2018), CC BY-SA 4.0
  • Sentences for word pairs — Tatoeba Bengali sentences, CC BY 2.0 FR

All text is NFC-normalised, which is also what the keyboard does at runtime, so nukta letters
(ড় ঢ় য়) and split vowel signs (ো ৌ) compare equal however they were encoded.

Usage:  python3 tools/build_dictionary.py
"""
import bz2
import collections
import os
import re
import subprocess
import unicodedata

WORDS_URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/bn/bn_50k.txt"
SENTENCES_URL = "https://downloads.tatoeba.org/exports/per_language/ben/ben_sentences.tsv.bz2"

MAX_WORDS = 30000        # completion dictionary size
MIN_WORD_FREQ = 3        # drop one-off typos from the subtitle corpus
MAX_NEXT_PER_WORD = 6    # predictions kept per previous word
SENTENCE_START = "<s>"
# Tatoeba's stock example names (Tom, Mary) would otherwise dominate next-word predictions.
PAIR_BLOCKLIST = {"টম", "টমের", "টমকে", "টমও", "মেরি", "মেরির", "মেরিকে", "মেরিও", "মেরী", "মেরীর", "মেরীকে"}

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")

# A word is a run of Bengali letters/signs (U+0980–U+09FF minus digits and currency marks) plus ZWJ/ZWNJ.
WORD_RE = re.compile(r"[ঀ-৥ৰৱৼ-৾‌‍]+")
LETTER_RE = re.compile(r"[অ-হৎড়-ৡৰৱ]")
SENTENCE_END = re.compile(r"[।?!.]+")


def fetch(url: str) -> bytes:
    # curl rather than urllib: python.org builds on macOS ship without CA certificates.
    return subprocess.run(["curl", "-fsSL", "--max-time", "120", url], check=True, capture_output=True).stdout


def nfc(s: str) -> str:
    return unicodedata.normalize("NFC", s)


def is_word(w: str) -> bool:
    return 1 <= len(w) <= 24 and WORD_RE.fullmatch(w) is not None and LETTER_RE.search(w) is not None


def build_words() -> list[tuple[str, int]]:
    counts: collections.Counter[str] = collections.Counter()
    for line in fetch(WORDS_URL).decode("utf-8").splitlines():
        parts = line.split()
        if len(parts) != 2 or not parts[1].isdigit():
            continue
        w = nfc(parts[0])
        f = int(parts[1])
        if f >= MIN_WORD_FREQ and is_word(w):
            counts[w] += f
    return counts.most_common(MAX_WORDS)


def build_bigrams(known: set[str]) -> list[tuple[str, str, int]]:
    text = bz2.decompress(fetch(SENTENCES_URL)).decode("utf-8")
    pairs: dict[str, collections.Counter[str]] = collections.defaultdict(collections.Counter)
    for line in text.splitlines():
        cols = line.split("\t")
        if len(cols) < 3:
            continue
        for sentence in SENTENCE_END.split(nfc(cols[2])):
            words = [w for w in WORD_RE.findall(sentence) if is_word(w)]
            if any(w in PAIR_BLOCKLIST for w in words):
                continue
            if not words:
                continue
            prev = SENTENCE_START
            for w in words:
                pairs[prev][w] += 1
                prev = w
    out = []
    for prev, nexts in pairs.items():
        for w, c in nexts.most_common(MAX_NEXT_PER_WORD):
            # Keep a pair if it repeats, or if both words are in the main dictionary.
            if c >= 2 or (w in known and (prev == SENTENCE_START or prev in known)):
                out.append((prev, w, c))
    out.sort(key=lambda t: (t[0], -t[2], t[1]))
    return out


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    words = build_words()
    with open(os.path.join(OUT_DIR, "dict_words.tsv"), "w", encoding="utf-8") as f:
        for w, c in words:
            f.write(f"{w}\t{c}\n")

    bigrams = build_bigrams({w for w, _ in words})
    with open(os.path.join(OUT_DIR, "dict_bigrams.tsv"), "w", encoding="utf-8") as f:
        for a, b, c in bigrams:
            f.write(f"{a}\t{b}\t{c}\n")

    print(f"{len(words)} words, {len(bigrams)} word pairs → {os.path.abspath(OUT_DIR)}")


if __name__ == "__main__":
    main()
