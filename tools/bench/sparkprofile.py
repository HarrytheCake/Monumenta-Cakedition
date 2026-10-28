"""Where a thread's CPU time went, from a spark profile saved with --save-to-file (read locally, never uploaded).

    python tools/bench/sparkprofile.py <file.sparkprofile> [--thread "Render thread"] [--top 15] [--json]

Self time (time in a method itself, not in what it calls) is credited to the mod of the deepest frame on its stack
that spark attributes to a mod (its class or method sources); time with no mod frame above it is "minecraft/java".
The format is spark's SamplerData protobuf; the field numbers come from spark 1.10's generated classes.
"""
import argparse
import json
import struct
import sys


def fields(buf):
    """(field number, wire type, value) for each field of one protobuf message."""
    i, n = 0, len(buf)
    while i < n:
        key, i = varint(buf, i)
        num, wire = key >> 3, key & 7
        if wire == 0:
            value, i = varint(buf, i)
        elif wire == 1:
            value, i = buf[i:i + 8], i + 8
        elif wire == 2:
            size, i = varint(buf, i)
            value, i = buf[i:i + size], i + size
        elif wire == 5:
            value, i = buf[i:i + 4], i + 4
        else:
            raise ValueError(f"unsupported wire type {wire}")
        yield num, wire, value


def varint(buf, i):
    shift = result = 0
    while True:
        b = buf[i]
        i += 1
        result |= (b & 0x7F) << shift
        if b < 0x80:
            return result, i
        shift += 7


def doubles(wire, value):
    if wire == 1:
        return [struct.unpack("<d", value)[0]]
    return list(struct.unpack(f"<{len(value) // 8}d", value))


def ints(wire, value):
    if wire == 0:
        return [value]
    out, i = [], 0
    while i < len(value):
        v, i = varint(value, i)
        out.append(v)
    return out


def string_map(value):
    key = val = ""
    for num, _, v in fields(value):
        if num == 1:
            key = v.decode("utf-8", "replace")
        elif num == 2:
            val = v.decode("utf-8", "replace")
    return key, val


def node(buf):
    n = {"class": "", "method": "", "desc": "", "times": [], "refs": []}
    for num, wire, v in fields(buf):
        if num == 3:
            n["class"] = v.decode("utf-8", "replace")
        elif num == 4:
            n["method"] = v.decode("utf-8", "replace")
        elif num == 7:
            n["desc"] = v.decode("utf-8", "replace")
        elif num == 8:
            n["times"] += doubles(wire, v)
        elif num == 9:
            n["refs"] += ints(wire, v)
    n["time"] = sum(n["times"])
    return n


def read(path):
    data = open(path, "rb").read()
    threads, class_sources, method_sources = [], {}, {}
    for num, _, v in fields(data):
        if num == 2:
            t = {"name": "", "nodes": [], "roots": [], "times": []}
            for tn, tw, tv in fields(v):
                if tn == 1:
                    t["name"] = tv.decode("utf-8", "replace")
                elif tn == 3:
                    t["nodes"].append(node(tv))
                elif tn == 4:
                    t["times"] += doubles(tw, tv)
                elif tn == 5:
                    t["roots"] += ints(tw, tv)
            threads.append(t)
        elif num == 3:
            k, val = string_map(v)
            class_sources[k] = val
        elif num == 4:
            k, val = string_map(v)
            method_sources[k] = val
    return threads, class_sources, method_sources


def breakdown(thread, class_sources, method_sources):
    nodes, total = thread["nodes"], sum(thread["times"]) or sum(thread["nodes"][r]["time"] for r in thread["roots"])
    by_mod, by_method = {}, {}

    def source(n):
        return (method_sources.get(f"{n['class']};{n['method']};{n['desc']}")
                or method_sources.get(f"{n['class']};{n['method']}")
                or class_sources.get(n["class"]))

    stack = [(r, None) for r in thread["roots"]]
    while stack:
        idx, inherited = stack.pop()
        n = nodes[idx]
        mod = source(n) or inherited
        child_time = sum(nodes[c]["time"] for c in n["refs"])
        self_time = max(0.0, n["time"] - child_time)
        if self_time:
            key = mod or "minecraft/java"
            by_mod[key] = by_mod.get(key, 0) + self_time
            m = f"{n['class']}.{n['method']}"
            by_method[m] = by_method.get(m, 0) + self_time
        stack.extend((c, mod) for c in n["refs"])
    return total, by_mod, by_method


def main():
    p = argparse.ArgumentParser()
    p.add_argument("file")
    p.add_argument("--thread", default="Render thread")
    p.add_argument("--top", type=int, default=15)
    p.add_argument("--json", action="store_true")
    a = p.parse_args()
    threads, class_sources, method_sources = read(a.file)
    match = [t for t in threads if t["name"] == a.thread]
    if not match:
        sys.exit(f"no thread '{a.thread}'; threads: {', '.join(t['name'] for t in threads)}")
    total, by_mod, by_method = breakdown(match[0], class_sources, method_sources)
    mods = sorted(by_mod.items(), key=lambda kv: -kv[1])
    methods = sorted(by_method.items(), key=lambda kv: -kv[1])[:a.top]
    if a.json:
        print(json.dumps({"thread": a.thread, "totalMs": total, "mods": dict(mods), "methods": dict(methods)}))
        return
    print(f"{a.thread}: {total / 1000:.1f} s sampled")
    print("\nBy mod (self time):")
    for mod, ms in mods[:a.top]:
        print(f"  {ms / total * 100:5.1f}%  {mod}")
    print("\nTop methods (self time):")
    for m, ms in methods:
        print(f"  {ms / total * 100:5.1f}%  {m}")


if __name__ == "__main__":
    main()
