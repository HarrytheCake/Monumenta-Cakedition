"""Rewrites the Yarn names left in string literals after Loom's `migrateMappings` (mixin
`method = "..."`, `target = "L...;name(...)V"`, `@Accessor("...")`, `@Invoker("...")`, descriptors)
to Mojang's names, for Minecraft 1.20.4.

Loom's migrateMappings remaps Java code (imports, calls, overrides) but not strings, so mixin
targets keep their Yarn names. This joins Yarn and Mojang's mappings on Fabric's intermediary names.

usage: python tools/multiloader/yarn2moj.py <java source dir> [--dry-run]
       (needs the Loom cache of a 1.20.4 build that has run with both mappings; see find_mappings)

Prints every string it changed, and every Yarn-looking string it could not resolve (fix those by hand).
"""
import io
import os
import re
import sys
import zipfile
from pathlib import Path

LOOM = Path.home() / '.gradle' / 'caches' / 'fabric-loom' / '1.20.4'


def find_mappings():
    yarn = next(LOOM.glob('net.fabricmc.yarn.*/mappings.tiny'))
    moj = next(LOOM.glob('layered/loom.mappings-layered*.jar'))
    return yarn, moj


def read_tiny(text):
    """tiny v2 → (namespaces, classes {ns: name per class}, members [(kind, owner_idx, desc, names)])."""
    lines = text.splitlines()
    ns = lines[0].split('\t')[3:]
    classes, members = [], []
    for line in lines[1:]:
        parts = line.split('\t')
        if parts[0] == 'c':
            classes.append(dict(zip(ns, parts[1:])))
        elif len(parts) > 1 and parts[0] == '' and parts[1] in ('m', 'f'):
            members.append((parts[1], len(classes) - 1, parts[2], dict(zip(ns, parts[3:]))))
    return ns, classes, members


def remap_desc(desc, cmap):
    return re.sub(r'L([^;]+);', lambda m: 'L' + cmap.get(m.group(1), m.group(1)) + ';', desc)


class Mappings:
    def __init__(self):
        yarn_path, moj_path = find_mappings()
        _, ycls, ymem = read_tiny(yarn_path.read_text(encoding='utf-8'))
        with zipfile.ZipFile(moj_path) as z:
            _, mcls, mmem = read_tiny(z.read('mappings/mappings.tiny').decode('utf-8'))
        off2int = {c['official']: c['intermediary'] for c in ycls}
        int2yarn = {c['intermediary']: c['named'] for c in ycls}
        int2moj = {c['intermediary']: c['named'] for c in mcls}
        self.cls = {}  # yarn class → moj class
        for i, y in int2yarn.items():
            if i in int2moj:
                self.cls[y] = int2moj[i]
        self.cls_back = {v: k for k, v in self.cls.items()}
        # members by intermediary name (+ intermediary desc)
        moj_m = {}
        for kind, ci, desc, names in mmem:
            moj_m[(kind, names['intermediary'], desc)] = names['named']
        self.members = {}  # (kind, yarn owner, yarn name, yarn desc) → moj name
        self.by_name = {}  # (kind, yarn name) → set of moj names
        self.by_owner = {}  # (kind, yarn owner, yarn name) → set of (yarn desc, moj name)
        for kind, ci, desc, names in ymem:
            owner_int = ycls[ci]['intermediary']
            owner_yarn = int2yarn.get(owner_int, owner_int)
            idesc = remap_desc(desc, off2int)
            ydesc = remap_desc(idesc, int2yarn)
            moj = moj_m.get((kind, names['intermediary'], idesc))
            if moj is None:
                continue
            self.members[(kind, owner_yarn, names['named'], ydesc)] = moj
            self.by_name.setdefault((kind, names['named']), set()).add(moj)
            self.by_owner.setdefault((kind, owner_yarn, names['named']), set()).add((ydesc, moj))

    def desc(self, d):
        return re.sub(r'L(net/minecraft/[^;]+);', lambda m: 'L' + self.cls.get(m.group(1), m.group(1)) + ';', d)

    def member(self, kind, owner, name, desc):
        """owner and desc in Yarn names (desc may be None). Returns the Mojang name or None."""
        if desc is not None and (kind, owner, name, desc) in self.members:
            return self.members[(kind, owner, name, desc)]
        cands = {m for d, m in self.by_owner.get((kind, owner, name), set()) if desc is None or d == desc}
        if len(cands) == 1:
            return cands.pop()
        cands = self.by_name.get((kind, name), set())
        if len(cands) == 1:
            return next(iter(cands))
        return None


MEMBER_REF = re.compile(r'^L([\w/$]+);([\w<>$]+)(\(.*|:.*)?$')
SIMPLE_METHOD = re.compile(r'^([\w$<>]+)(\(.*\).*)?$')


def convert(s, target_yarn, m, kind_hint):
    """One string literal → (new string, resolved?)."""
    mm = MEMBER_REF.match(s)
    if mm:
        owner, name, rest = mm.group(1), mm.group(2), mm.group(3) or ''
        is_field = rest.startswith(':')
        desc = rest[1:] if is_field else rest or None
        new_owner = m.cls.get(owner, owner)
        new_name = name
        if owner.startswith('net/minecraft/') and name not in ('<init>', '<clinit>'):
            new_name = m.member('f' if is_field else 'm', owner, name, desc)
            if new_name is None:
                return s, False
        new_rest = (':' + m.desc(desc)) if is_field else (m.desc(rest) if rest else '')
        return 'L' + new_owner + ';' + new_name + new_rest, True
    if s.startswith('L') and s.endswith(';') or s.startswith('('):
        return m.desc(s), True
    sm = SIMPLE_METHOD.match(s)
    if sm and target_yarn:
        name, desc = sm.group(1), sm.group(2)
        if name in ('<init>', '<clinit>') or name.startswith('lambda$'):
            return (name + m.desc(desc)) if desc else name, True
        new = m.member(kind_hint, target_yarn, name, desc)
        if new is None:
            return s, False
        return new + (m.desc(desc) if desc else ''), True
    return s, False


ANNOT_STRING = re.compile(r'\b(method|target|value|desc)\s*=\s*(\{[^}]*\}|"[^"]*")')
ACCESSOR = re.compile(r'@(Accessor|Invoker)\(\s*(?:value\s*=\s*)?"([^"]+)"')
MIXIN_TARGET = re.compile(r'@Mixin\((?:value\s*=\s*)?\{?\s*([\w.]+)\.class')


def process(path, m, dry):
    src = path.read_text(encoding='utf-8')
    imports = dict(re.findall(r'^import\s+([\w.]+\.(\w+));', src, re.M) and
                   [(simple, full) for full, simple in re.findall(r'^import\s+([\w.]+\.(\w+));', src, re.M)])
    pkg = re.search(r'^package\s+([\w.]+);', src, re.M)
    target_yarn = None
    t = MIXIN_TARGET.search(src)
    if t:
        simple = t.group(1)
        full = imports.get(simple, simple).replace('.', '/')
        target_yarn = m.cls_back.get(full)
    changes, unresolved = [], []

    def fix_literal(lit, kind_hint):
        s = lit[1:-1]
        new, ok = convert(s, target_yarn, m, kind_hint)
        if not ok:
            # a bare name only matters when the mixin targets a Minecraft class (not another mod's)
            if 'net/minecraft' in s or target_yarn and re.search(r'^\w+$|\(', s):
                unresolved.append(s)
            return lit
        if new != s:
            changes.append((s, new))
        return '"' + new + '"'

    def on_annot(mt):
        key, val = mt.group(1), mt.group(2)
        if key == 'value' and not re.match(r'"L|"\(', val):
            return mt.group(0)  # value = "INVOKE" etc.
        kind = 'm'
        out = re.sub(r'"[^"]*"', lambda l: fix_literal(l.group(0), kind) if l.group(0) not in KEYWORDS else l.group(0), val)
        return key + ' = ' + out

    def on_accessor(mt):
        kind = 'f' if mt.group(1) == 'Accessor' else 'm'
        return '@' + mt.group(1) + '(' + fix_literal('"' + mt.group(2) + '"', kind)

    new_src = ANNOT_STRING.sub(on_annot, src)
    new_src = ACCESSOR.sub(on_accessor, new_src)
    if changes or unresolved:
        print(f'== {path}')
        for a, b in changes:
            print(f'   {a}\n-> {b}')
        for u in unresolved:
            print(f'   UNRESOLVED: {u}')
    if new_src != src and not dry:
        path.write_text(new_src, encoding='utf-8', newline='')
    return len(changes), len(unresolved)


KEYWORDS = {'"INVOKE"', '"HEAD"', '"TAIL"', '"RETURN"', '"FIELD"', '"NEW"', '"INVOKE_ASSIGN"', '"CONSTANT"',
            '"STORE"', '"LOAD"', '"JUMP"', '"INVOKE_STRING"', '"MIXINEXTRAS:EXPRESSION"'}


def main():
    root = Path(sys.argv[1])
    dry = '--dry-run' in sys.argv
    m = Mappings()
    total = [0, 0]
    for p in sorted(root.rglob('*.java')):
        txt = p.read_text(encoding='utf-8')
        if '@Mixin' not in txt and 'net/minecraft/' not in txt:
            continue
        c, u = process(p, m, dry)
        total[0] += c
        total[1] += u
    print(f'changed {total[0]}, unresolved {total[1]}')


if __name__ == '__main__':
    main()
