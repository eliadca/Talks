#!/usr/bin/env python3
"""Rebuild the offline book-name classifier. Training uses synthetic spelling errors,
not recordings or user notes. Requires numpy/scipy/scikit-learn; inference needs none.
"""
import pathlib, random, re, struct, unicodedata
import numpy as np
from scipy.sparse import csr_matrix
from sklearn.linear_model import LogisticRegression

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'core/src/main/kotlin/com/eliadca/talks/core/bible/BibleReference.kt'
book_block = SOURCE.read_text().split('val books = listOf(', 1)[1].split('\n    )', 1)[0]
books = re.findall(r'"([^"]+)"', book_block)
DIM = 1024
SEED = 20261012
rng = random.Random(SEED)

def fold(text):
    return ''.join(c for c in unicodedata.normalize('NFD', text.lower()) if not unicodedata.combining(c))

def phonetic(text):
    text = fold(text).replace(' ', '')
    for a, b in [('ph', 'f'), ('qu', 'k'), ('ll', 'y'), ('v', 'b'), ('z', 's')]: text = text.replace(a, b)
    text = re.sub(r'c(?=[ei])', 's', text)
    text = re.sub(r'g(?=[ei])', 'j', text)
    return text.replace('c', 'k').replace('h', '').replace('y', 'i')

def features(text):
    out = {}
    for prefix, variant in [('o', fold(text).replace(' ', '')), ('p', phonetic(text))]:
        padded = '^' + variant + '$'
        for n in [2, 3, 4]:
            for i in range(len(padded) - n + 1):
                value = prefix + padded[i:i+n]
                h = 2166136261
                for c in value: h = ((h ^ ord(c)) * 16777619) & 0xffffffff
                index = h % DIM
                out[index] = out.get(index, 0) + 1
    norm = np.sqrt(sum(v*v for v in out.values())) or 1
    return {k: v/norm for k, v in out.items()}

families = {}
for i, title in enumerate(books, 1):
    stem = re.sub(r'^\d ', '', title)
    families.setdefault(stem, i)
samples = []
labels = []
extra = {'Salmos': ['salmo'], 'Cantar de los Cantares': ['cantares'],
         'Hechos': ['hechos de los apostoles'], 'Apocalipsis': ['revelacion']}
replacements = [('s', 'c'), ('c', 's'), ('z', 's'), ('s', 'z'), ('b', 'v'), ('v', 'b'),
                ('qu', 'k'), ('g', 'j'), ('j', 'g'), ('i', 'y'), ('ll', 'y')]
for stem, book in families.items():
    aliases = [fold(stem)] + extra.get(stem, [])
    for _ in range(220):
        value = rng.choice(aliases)
        if len(value) > 4:
            for _ in range(rng.choice([0, 0, 1, 1, 1, 2])):
                action = rng.randrange(5); i = rng.randrange(len(value))
                if action == 0: value = value[:i] + value[i+1:]
                elif action == 1 and i+1 < len(value): value = value[:i] + value[i+1] + value[i] + value[i+2:]
                elif action == 2: value = value[:i] + value[i] + value[i:]
                elif action == 3: value = value[:i] + ' ' + value[i:]
                else:
                    a, b = rng.choice(replacements); value = value.replace(a, b, 1)
        samples.append(value); labels.append(book)

ordinary = ['casa', 'persona', 'hoy', 'manana', 'amor', 'hermano', 'familia', 'hablando', 'palabra',
            'ejemplo', 'telefono', 'trabajo', 'ropa', 'libro', 'pasaje', 'capitulo', 'versiculo',
            'nota', 'escribir', 'discurso', 'cinco', 'veinte', 'escuchar', 'mision', 'reunion',
            'numero', 'mesa', 'silla', 'luego', 'para', 'como', 'dice', 'recuerdo', 'primera']
for _ in range(1200):
    samples.append(rng.choice(ordinary)); labels.append(0)

rows, cols, vals = [], [], []
for i, sample in enumerate(samples):
    for key, value in features(sample).items(): rows.append(i); cols.append(key); vals.append(value)
x = csr_matrix((np.array(vals, dtype=np.float32), (rows, cols)), shape=(len(samples), DIM))
model = LogisticRegression(C=8, max_iter=250, random_state=SEED, solver='lbfgs').fit(x, labels)
scale = float(np.max(np.abs(model.coef_)) / 127)
weights = np.rint(model.coef_ / scale).clip(-127, 127).astype(np.int8)
target = ROOT / 'core/src/main/resources/bible/book-name-tiny.bin'
target.parent.mkdir(parents=True, exist_ok=True)
with target.open('wb') as f:
    f.write(b'TBN1'); f.write(struct.pack('>HHf', DIM, len(model.classes_), scale))
    f.write(bytes(int(x) for x in model.classes_))
    f.write(struct.pack('>' + 'f' * len(model.classes_), *model.intercept_))
    f.write(weights.tobytes())
print(f'{len(model.classes_)} families, {len(samples)} synthetic samples, {target.stat().st_size} bytes')
