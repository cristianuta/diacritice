"""
Generează dicționarul complet FastDiacritics.

Surse:
  * Hunspell ro_RO (LibreOffice/dictionaries, licență GPL/LGPL/MPL) – toate formele flexionare valide;
  * corpusul Leipzig „ron_news_2022_1M” (CC BY) – frecvențe, folosite DOAR pentru a alege între
    formele valide care au aceeași scriere fără diacritice (ex. „casa” / „casă”).

Folosire:
  python tools/build_dictionary.py ro_RO.dic ro_RO.aff ron_news_2022_1M-words.txt \
      src/main/resources/dictionar/ro.txt.gz

Fișierul rezultat (gzip, UTF-8) are o linie per cheie: forma câștigătoare. Cheia se obține
eliminând diacriticele din formă, deci o linie fără diacritice înseamnă „cuvântul rămâne așa”.
"""
import gzip
import re
import sys
from collections import defaultdict

STRIP = str.maketrans("ăâîșțşţĂÂÎȘȚŞŢ", "aaiststAAISTST")
CEDILLA = str.maketrans("şţŞŢ", "șțȘȚ")

# Cât din frecvența unei forme fără diacritice provine din texte scrise neglijent.
# Estimat din corpus: count("si") / count("și") ≈ 0.13; folosim o valoare puțin mai mare.
SLOPPY_RATIO = 0.15
# Formele cu diacritice din corpus care lipsesc din Hunspell (nume proprii, neologisme)
# sunt acceptate doar peste acest prag.
CORPUS_MIN_COUNT = 20


def strip(s):
    return s.translate(STRIP)


def parse_aff(path):
    rules = {}  # flag -> (kind, cross, [(strip, add, regex)])
    with open(path, encoding="utf-8") as f:
        lines = [l.split("#")[0].split() for l in f]
    i = 0
    while i < len(lines):
        p = lines[i]
        if len(p) >= 4 and p[0] in ("SFX", "PFX") and p[2] in ("Y", "N"):
            kind, flag, cross, n = p[0], p[1], p[2] == "Y", int(p[3])
            entries = []
            for p2 in lines[i + 1:i + 1 + n]:
                strip_, add, cond = p2[2], p2[3], p2[4] if len(p2) > 4 else "."
                strip_ = "" if strip_ == "0" else strip_
                add = "" if add == "0" else add.split("/")[0]
                regex = re.compile(cond + "$" if kind == "SFX" else "^" + cond)
                entries.append((strip_, add, regex))
            rules[flag] = (kind, cross, entries)
            i += n + 1
        else:
            i += 1
    return rules


def apply_sfx(word, entries):
    for strip_, add, regex in entries:
        if regex.search(word) and word.endswith(strip_):
            yield word[: len(word) - len(strip_)] + add


def apply_pfx(word, entries):
    for strip_, add, regex in entries:
        if regex.search(word) and word.startswith(strip_):
            yield add + word[len(strip_):]


def expand(dic_path, rules):
    words = set()
    with open(dic_path, encoding="utf-8") as f:
        next(f)  # numărul de intrări
        for line in f:
            line = line.strip()
            if not line:
                continue
            stem, _, flags = line.partition("/")
            stem = stem.translate(CEDILLA)
            forms = {stem}
            sfx = [rules[c] for c in flags if c in rules and rules[c][0] == "SFX"]
            pfx = [rules[c] for c in flags if c in rules and rules[c][0] == "PFX"]
            suffixed = set()
            for _, cross, entries in sfx:
                for w in apply_sfx(stem, entries):
                    suffixed.add((w, cross))
            forms |= {w for w, _ in suffixed}
            for _, pcross, entries in pfx:
                forms |= set(apply_pfx(stem, entries))
                if pcross:
                    for w, scross in suffixed:
                        if scross:
                            forms |= set(apply_pfx(w, entries))
            words |= forms
    return words


def load_corpus(path):
    freq = defaultdict(int)
    with open(path, encoding="utf-8") as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) >= 3 and p[1].isalpha():
                freq[p[1].translate(CEDILLA).lower()] += int(p[2])
    return freq


def main(dic_path, aff_path, corpus_path, out_path):
    rules = parse_aff(aff_path)
    words = {w.lower() for w in expand(dic_path, rules) if re.fullmatch(r"[^\W\d_]+(-[^\W\d_]+)*", w)}
    freq = load_corpus(corpus_path)
    print(f"Forme Hunspell: {len(words):,}; cuvinte în corpus: {len(freq):,}")

    candidates = defaultdict(set)
    for w in words:
        for part in w.split("-"):  # „într-o”, „ADN-ul” → tokenurile sunt fără cratimă
            candidates[strip(part)].add(part)
    for w, c in freq.items():
        key = strip(w)
        if c >= CORPUS_MIN_COUNT and key != w and c > freq.get(key, 0) and key not in candidates:
            candidates[key].add(w)

    def score(form, forms):
        c = freq.get(form, 0)
        if form == strip(form):  # forma fără diacritice: scădem partea „neglijentă”
            c -= SLOPPY_RATIO * sum(freq.get(f, 0) for f in forms if f != form)
        return c

    lines, changed = [], 0
    for key, forms in candidates.items():
        best = max(forms, key=lambda f: (score(f, forms), f == key, f))
        lines.append(best)
        changed += best != key
    lines.sort()
    with gzip.open(out_path, "wt", encoding="utf-8", compresslevel=9) as out:
        out.write("\n".join(lines) + "\n")
    print(f"Chei: {len(lines):,}, dintre care {changed:,} primesc diacritice -> {out_path}")


if __name__ == "__main__":
    if len(sys.argv) != 5:
        sys.exit(__doc__)
    main(*sys.argv[1:])
