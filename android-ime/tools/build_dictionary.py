#!/usr/bin/env python3
"""
Build the word-suggestion dictionary bundled with the Android keyboard.

Outputs (into app/src/main/assets/), for Bangla and for English (`_en` suffix):
  dict_words[_en].tsv    word <TAB> frequency          — completion candidates, most frequent first
  dict_bigrams[_en].tsv  previous <TAB> next <TAB> count — next-word predictions ("<s>" = sentence start)

Sources (downloaded on each run):
  • Word frequencies — hermitdave/FrequencyWords, bn_50k / en_50k (OpenSubtitles 2018), CC BY-SA 4.0
  • Sentences for word pairs — Tatoeba Bengali / English sentences, CC BY 2.0 FR

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

WORDS_URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/{lang}/{lang}_50k.txt"
SENTENCES_URL = "https://downloads.tatoeba.org/exports/per_language/{tatoeba}/{tatoeba}_sentences.tsv.bz2"

MAX_WORDS = 30000        # completion dictionary size
MIN_WORD_FREQ = 3        # drop one-off typos from the subtitle corpus
MAX_NEXT_PER_WORD = 6    # predictions kept per previous word
SENTENCE_START = "<s>"


OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")

SENTENCE_END = re.compile(r"[।?!.]+")


class Lang:
    def __init__(self, code, tatoeba, suffix, word_re, letter_re, blocklist, min_pair, lower, is_ok=lambda w: True):
        self.code, self.tatoeba, self.suffix = code, tatoeba, suffix
        self.word_re, self.letter_re = re.compile(word_re), re.compile(letter_re)
        self.blocklist, self.min_pair, self.lower, self.is_ok = blocklist, min_pair, lower, is_ok


LANGS = [
    # A Bangla word is a run of Bengali letters/signs (U+0980–U+09FF minus digits and currency) plus ZWJ/ZWNJ.
    Lang("bn", "ben", "",
         r"[\u0980-\u09E5\u09F0\u09F1\u09FC-\u09FE\u200C\u200D]+", r"[\u0985-\u09B9\u09CE\u09DC-\u09E1\u09F0\u09F1]",
         # Tatoeba's stock example names (Tom, Mary) would otherwise dominate next-word predictions.
         {"টম", "টমের", "টমকে", "টমও", "মেরি", "মেরির", "মেরিকে", "মেরিও", "মেরী", "মেরীর", "মেরীকে"},
         min_pair=1, lower=False),
    Lang("en", "eng", "_en", r"[a-z']+", r"[a-z]",
         {"tom", "tom's", "mary", "mary's"},
         min_pair=3, lower=True,
         is_ok=lambda w: (len(w) > 1 or w in ("a", "i")) and not w.startswith("'") and not w.endswith("'")),
]


CACHE_DIR = os.path.join(os.path.dirname(__file__), ".cache")


def fetch(url: str) -> bytes:
    """Download once into tools/.cache (resuming interrupted downloads), then read from there."""
    # curl rather than urllib: python.org builds on macOS ship without CA certificates.
    os.makedirs(CACHE_DIR, exist_ok=True)
    path = os.path.join(CACHE_DIR, url.rsplit("/", 1)[-1])
    if not os.path.exists(path):
        part = path + ".part"
        for _ in range(8):
            if subprocess.run(["curl", "-fsSL", "--retry", "3", "-C", "-", "-o", part, url]).returncode == 0:
                break
        else:
            raise RuntimeError(f"download failed: {url}")
        os.rename(part, path)
    with open(path, "rb") as fh:
        return fh.read()


def nfc(s: str) -> str:
    return unicodedata.normalize("NFC", s)


def is_word(lang: Lang, w: str) -> bool:
    return (1 <= len(w) <= 24 and lang.word_re.fullmatch(w) is not None
            and lang.letter_re.search(w) is not None and lang.is_ok(w))


def build_words(lang: Lang) -> list[tuple[str, int]]:
    counts: collections.Counter[str] = collections.Counter()
    for line in fetch(WORDS_URL.format(lang=lang.code)).decode("utf-8").splitlines():
        parts = line.split()
        if len(parts) != 2 or not parts[1].isdigit():
            continue
        w = nfc(parts[0].lower() if lang.lower else parts[0])
        f = int(parts[1])
        if f >= MIN_WORD_FREQ and is_word(lang, w):
            counts[w] += f
    return counts.most_common(MAX_WORDS)


def build_bigrams(lang: Lang, known: set[str]) -> list[tuple[str, str, int]]:
    text = bz2.decompress(fetch(SENTENCES_URL.format(tatoeba=lang.tatoeba))).decode("utf-8")
    pairs: dict[str, collections.Counter[str]] = collections.defaultdict(collections.Counter)
    for line in text.splitlines():
        cols = line.split("\t")
        if len(cols) < 3:
            continue
        sentence_text = nfc(cols[2].lower() if lang.lower else cols[2]).replace("\u2019", "'")
        for sentence in SENTENCE_END.split(sentence_text):
            words = [w for w in lang.word_re.findall(sentence) if is_word(lang, w)]
            if not words or any(w in lang.blocklist for w in words):
                continue
            prev = SENTENCE_START
            for w in words:
                pairs[prev][w] += 1
                prev = w
    out = []
    for prev, nexts in pairs.items():
        for w, c in nexts.most_common(MAX_NEXT_PER_WORD):
            # Keep a pair if it repeats enough, or (small corpora) if both words are in the main dictionary.
            both_known = w in known and (prev == SENTENCE_START or prev in known)
            if c >= max(2, lang.min_pair) or (lang.min_pair <= 1 and both_known):
                if lang.min_pair > 1 and not both_known:
                    continue
                out.append((prev, w, c))
    out.sort(key=lambda t: (t[0], -t[2], t[1]))
    return out


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    for lang in LANGS:
        words = build_words(lang)
        with open(os.path.join(OUT_DIR, f"dict_words{lang.suffix}.tsv"), "w", encoding="utf-8") as f:
            for w, c in words:
                f.write(f"{w}\t{c}\n")
        bigrams = build_bigrams(lang, {w for w, _ in words})
        with open(os.path.join(OUT_DIR, f"dict_bigrams{lang.suffix}.tsv"), "w", encoding="utf-8") as f:
            for a, b, c in bigrams:
                f.write(f"{a}\t{b}\t{c}\n")
        print(f"{lang.code}: {len(words)} words, {len(bigrams)} word pairs")


if __name__ == "__main__":
    main()
