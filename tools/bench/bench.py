"""Unattended client-lag benchmarks for the pack (see docs/GLOSSARY.md: client lag, low tier, benchmark scene).

    python tools/bench/bench.py setup                  # server + Carpet, bench JDK copy, "Cakedition Bench" instance
    python tools/bench/bench.py gpu igpu|default       # Windows GPU preference for the bench JDK only
    python tools/bench/bench.py batch <sets.json> [--repeats N] [--name NAME]
    python tools/bench/bench.py report <batch name>    # docs/benchmarks/<batch name>.md
    python tools/bench/bench.py serve                  # the bench server alone, to look around by hand

A batch starts the local server, then for each mod set and repeat: resets the bench instance to the pack's mods and
configs, applies the set (mods added/removed, config files), launches it through Prism (which joins the server), and
waits for the CakeBench mod to write summary.json and quit. Everything downloaded or generated stays in
tools/bench/work/ (git-ignored); nothing here ships in the pack.

Synthetic scenes approximate Monumenta: they pick candidates, and the final set is confirmed on Monumenta itself.
"""
import argparse
import datetime
import hashlib
import json
import os
import pathlib
import re
import shutil
import statistics
import subprocess
import sys
import threading
import time
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
BENCH = ROOT / "tools" / "bench"
WORK = BENCH / "work"
CACHE = WORK / "cache"
SERVER = WORK / "server"
RESULTS = WORK / "results"
JDK_SRC = pathlib.Path("C:/Program Files/Java/jdk-17")
JDK = WORK / "jdk17"
SERVER_JAVA = pathlib.Path("C:/Program Files/Java/jdk-21/bin/java.exe")
PRISM = ROOT.parents[1] / "prismlauncher.exe"
INSTANCE_NAME = "Cakedition Bench"
INSTANCE = ROOT.parent / INSTANCE_NAME
MC = INSTANCE / ".minecraft"
STATE = WORK / "instance-state"  # the instance's mods/ + config/ + options.txt after priming
PORT = 25599
UA = {"User-Agent": "Monumenta-Cakedition bench (github.com/HarrytheCake/Monumenta-Cakedition)"}

MC_VERSION, LOADER, INSTALLER = "1.20.4", "0.18.6", "1.1.2"
SERVER_MODS = {  # Modrinth CDN, pinned; fake players come from CakeBench's /cbbot (Carpet's crash the 1.20.4 server)
    "fabric-api-0.97.3+1.20.4.jar": "https://cdn.modrinth.com/data/P7dR8mSH/versions/BPX6fK06/fabric-api-0.97.3%2B1.20.4.jar",
}
# Low tier: the client on 4 performance cores (one thread each), the server on the efficiency cores (i7-14700HX).
CLIENT_AFFINITY = 0x55
SERVER_AFFINITY = 0xFFF0000

# Scene spots: 2000 blocks apart, so no scene is in view of another.
SCENES = [
    {"name": "normal", "x": 0.5, "y": 123, "z": 0.5, "yaw": 0, "pitch": 15},
    {"name": "mobs", "x": 2000.5, "y": 127, "z": 0.5, "yaw": 0, "pitch": 25},
    {"name": "market", "x": 3994.5, "y": 125, "z": -11.5, "yaw": 90, "pitch": 15},  # east of the shelves
    {"name": "players", "x": 6000.5, "y": 125, "z": 0.5, "yaw": 0, "pitch": 20},
    {"name": "mixed", "x": 8000.5, "y": 127, "z": 0.5, "yaw": 0, "pitch": 25},
]


# --- helpers ---------------------------------------------------------------------------------------------------

def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA)) as r:
        return r.read()


def cached(url, name=None):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / (name or hashlib.sha256(url.encode()).hexdigest()[:24])
    if not path.exists():
        path.write_bytes(fetch(url))
    return path


def modrinth_version(version_id):
    v = json.loads(fetch(f"https://api.modrinth.com/v2/version/{version_id}"))
    f = next(f for f in v["files"] if f["primary"]) if any(f["primary"] for f in v["files"]) else v["files"][0]
    data = cached(f["url"], f["filename"]).read_bytes()
    if hashlib.sha512(data).hexdigest() != f["hashes"]["sha512"]:
        sys.exit(f"{f['filename']}: sha512 mismatch")
    return CACHE / f["filename"]


POWERSHELL = pathlib.Path(os.environ.get("SystemRoot", "C:/Windows")) / "System32/WindowsPowerShell/v1.0/powershell.exe"


def ps(command):
    return subprocess.run([str(POWERSHELL), "-NoProfile", "-Command", command], capture_output=True, text=True)


def set_affinity(pid, mask):
    ps(f"(Get-Process -Id {pid}).ProcessorAffinity = {mask}")


def game_pids():
    """javaw processes started from the bench JDK (so the dev and test instances are never touched)."""
    out = ps(f"Get-Process javaw -ErrorAction SilentlyContinue | Where-Object {{ $_.Path -like '{JDK}*' }} "
             f"| ForEach-Object {{ $_.Id }}").stdout
    return [int(x) for x in out.split()]


# --- setup -----------------------------------------------------------------------------------------------------

def setup_server():
    SERVER.mkdir(parents=True, exist_ok=True)
    jar = SERVER / "fabric-server-launch.jar"
    if not jar.exists():
        jar.write_bytes(fetch(f"https://meta.fabricmc.net/v2/versions/loader/{MC_VERSION}/{LOADER}/{INSTALLER}/server/jar"))
    if (SERVER / "mods").exists():
        shutil.rmtree(SERVER / "mods")
    (SERVER / "mods").mkdir()
    for name, url in SERVER_MODS.items():
        shutil.copy(cached(url, name), SERVER / "mods" / name)
    shutil.copy(bench_jar(), SERVER / "mods" / bench_jar().name)
    (SERVER / "eula.txt").write_text("eula=true\n")
    (SERVER / "server.properties").write_text("\n".join([
        f"server-port={PORT}", "server-ip=127.0.0.1", "online-mode=false", "enforce-secure-profile=false",
        "spawn-protection=0", "view-distance=12", "simulation-distance=12", "max-players=100",
        # spawn-animals/spawn-npcs=false would delete the scene's animals and villagers; doMobSpawning stops spawning
        "level-seed=cakedition-bench", "difficulty=easy", "spawn-monsters=true", "spawn-animals=true",
        "spawn-npcs=true", "allow-flight=true", "motd=Cakedition bench", "sync-chunk-writes=false",
        # Carpet looks up each fake player's profile from Mojang on the server thread; a slow lookup isn't a crash
        "max-tick-time=-1", ""]))
    write_datapack(SERVER / "world" / "datapacks" / "cakebench")


def write_datapack(dp):
    fn = dp / "data" / "cakebench" / "functions"
    fn.mkdir(parents=True, exist_ok=True)
    (dp / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 26, "description": "Cakedition bench scenes"}}))
    tags = dp / "data" / "minecraft" / "tags" / "functions"
    tags.mkdir(parents=True, exist_ok=True)
    (tags / "tick.json").write_text(json.dumps({"values": ["cakebench:tick"]}))

    mobs = ["zombie", "skeleton", "husk", "stray", "vindicator", "pillager", "piglin", "wither_skeleton"]
    armor = '[{id:"minecraft:iron_boots",Count:1b},{id:"minecraft:iron_leggings",Count:1b},' \
            '{id:"minecraft:iron_chestplate",Count:1b},{id:"minecraft:iron_helmet",Count:1b}]'

    def mob(kind, x, y, z, i):
        return (f'summon minecraft:{kind} {x} {y} {z} {{NoAI:1b,PersistenceRequired:1b,Silent:1b,Invulnerable:1b,Tags:["cb"],'
                f'CustomName:\'"Elite {kind.title()} {i}"\',CustomNameVisible:1b,ArmorItems:{armor},'
                f'HandItems:[{{id:"minecraft:iron_sword",Count:1b}},{{}}]}}')

    def sign(x, y, z, i, rotation=8):
        lines = [f"Shop #{i}", f"{(i * 7) % 64 + 1}x Hyper Item", f"{(i * 13) % 900 + 10} CXP", "Sold by Market"]
        msgs = ",".join("'" + json.dumps({"text": t}) + "'" for t in lines)
        return f"setblock {x} {y} {z} minecraft:oak_sign[rotation={rotation}]{{front_text:{{messages:[{msgs}]}}}}"

    # normal: a grass platform with a few animals, chests and signs, open to the terrain around it
    c = ["fill -15 120 -15 15 120 15 minecraft:grass_block", "fill -15 121 -15 15 130 15 minecraft:air"]
    animals = ["cow", "sheep", "pig", "chicken", "villager"]
    for i in range(20):
        x, z = -12 + (i % 5) * 6, -12 + (i // 5) * 6
        c.append(f"summon minecraft:{animals[i % 5]} {x} 121 {z} {{NoAI:1b,PersistenceRequired:1b,Silent:1b,Invulnerable:1b,Tags:[\"cb\"]}}")
    for i in range(15):
        c.append(f"setblock {-14 + i * 2} 121 14 minecraft:chest[facing=north]")
        c.append(sign(-14 + i * 2, 121, -14, i, 0))
    (fn / "build_normal.mcfunction").write_text("\n".join(c) + "\n")

    # mobs: 300 armored, named mobs in an arena with stone pillars (something for culling to hide behind)
    c = ["fill 1980 120 -20 2020 120 20 minecraft:stone_bricks", "fill 1980 121 -20 2020 135 20 minecraft:air"]
    for i in range(30):
        px, pz = 1983 + (i % 6) * 7, -17 + (i // 6) * 8
        c.append(f"fill {px} 121 {pz} {px + 1} 127 {pz + 1} minecraft:stone_bricks")
    n = 0
    for gx in range(20):
        for gz in range(15):
            x, z = 1981.5 + gx * 2, -17.5 + gz * 2.5
            c.append(mob(mobs[n % len(mobs)], x, 121, z, n))
            n += 1
    (fn / "build_mobs.mcfunction").write_text("\n".join(c) + "\n")

    # market: a closed, lit room with 600 barrels in shelves and 400 signs with text
    c = ["fill 3965 120 -35 4015 130 15 minecraft:air",
         "fill 3965 120 -35 4015 120 15 minecraft:polished_andesite",
         "fill 3965 121 -35 4015 128 -35 minecraft:stone_bricks", "fill 3965 121 15 4015 128 15 minecraft:stone_bricks",
         "fill 3965 121 -35 3965 128 15 minecraft:stone_bricks", "fill 4015 121 -35 4015 128 15 minecraft:stone_bricks",
         "fill 3965 129 -35 4015 129 15 minecraft:glowstone"]
    n = 0
    for row in range(10):
        z = -30 + row * 4
        c.append(f"fill 3970 121 {z} 3989 123 {z} minecraft:barrel[facing=up]")  # 20 x 3 = 60 barrels per row
        for i in range(20):
            c.append(sign(3970 + i, 124, z, n))  # one on top of each column
            c.append(sign(3970 + i, 121, z + 1, n + 1, 0))  # one in the aisle
            n += 2
    (fn / "build_market.mcfunction").write_text("\n".join(c) + "\n")

    # players: a platform for 50 fake players (spawned from the console by the driver; they don't persist)
    c = ["fill 5980 120 -20 6020 120 20 minecraft:smooth_stone", "fill 5980 121 -20 6020 135 20 minecraft:air"]
    (fn / "build_players.mcfunction").write_text("\n".join(c) + "\n")

    # mixed: smaller versions of mobs, market and players together, plus particles every tick
    c = ["fill 7980 120 -20 8020 120 20 minecraft:stone_bricks", "fill 7980 121 -20 8020 135 20 minecraft:air"]
    n = 0
    for gx in range(10):
        for gz in range(10):
            c.append(mob(mobs[n % len(mobs)], 7981.5 + gx * 2, 121, -17.5 + gz * 2, n))
            n += 1
    for row in range(5):
        z = 5 + row * 3
        c.append(f"fill 7982 121 {z} 8001 122 {z} minecraft:barrel[facing=up]")  # 40 barrels per row
        for i in range(15):
            c.append(sign(7982 + i, 123, z, 1000 + row * 15 + i))
            c.append(sign(7982 + i, 121, z + 1, 2000 + row * 15 + i, 0))
    (fn / "build_mixed.mcfunction").write_text("\n".join(c) + "\n")

    # entities in their own function, so a scene that lost them can be re-summoned without rebuilding it
    for build in list(fn.glob("build_*.mcfunction")):
        lines = build.read_text().splitlines()
        build.write_text("\n".join(l for l in lines if not l.startswith("summon ")) + "\n")
        summons = [l for l in lines if l.startswith("summon ")]
        if summons:
            (fn / build.name.replace("build_", "summon_")).write_text("\n".join(summons) + "\n")

    (fn / "tick.mcfunction").write_text(
        "execute if entity @a[x=7950,y=0,z=-50,dx=100,dy=300,dz=100] run particle minecraft:flame 8000 124 0 12 2 12 0.02 40 normal\n"
        "execute if entity @a[x=7950,y=0,z=-50,dx=100,dy=300,dz=100] run particle minecraft:crit 8000 124 0 12 2 12 0.2 30 normal\n"
        "execute if entity @a[x=7950,y=0,z=-50,dx=100,dy=300,dz=100] run particle minecraft:enchanted_hit 8000 124 0 12 2 12 0.2 30 normal\n")


def install_pack_file(mrpack, dest):
    """What a launcher does with a pack file: pack defaults (optional files off), then the overrides."""
    z = zipfile.ZipFile(mrpack)
    index = json.loads(z.read("modrinth.index.json"))
    for f in index["files"]:
        if f["env"]["client"] != "required":
            continue
        data = cached(f["downloads"][0], f["hashes"]["sha512"][:32]).read_bytes()
        if hashlib.sha512(data).hexdigest() != f["hashes"]["sha512"]:
            sys.exit(f"{f['path']}: sha512 mismatch")
        (dest / f["path"]).parent.mkdir(parents=True, exist_ok=True)
        (dest / f["path"]).write_bytes(data)
    for n in z.namelist():
        if n.startswith("overrides/") and not n.endswith("/"):
            p = dest / n[len("overrides/"):]
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(z.read(n))


def setup_instance(mrpack):
    if INSTANCE.exists():
        shutil.rmtree(INSTANCE)
    MC.mkdir(parents=True)
    install_pack_file(mrpack, MC)
    shutil.copy(ROOT / "mmc-pack.json", INSTANCE / "mmc-pack.json")
    javaw = (JDK / "bin" / "javaw.exe").as_posix()
    (INSTANCE / "instance.cfg").write_text("\n".join([
        "[General]", "ConfigVersion=1.2", "InstanceType=OneSix", f"name={INSTANCE_NAME}", "iconKey=default",
        "OverrideJavaLocation=true", f"JavaPath={javaw}", "OverrideMemory=true", "MinMemAlloc=2048", "MaxMemAlloc=4096",
        "OverrideConsole=true", "ShowConsole=false", "ShowConsoleOnError=false", "AutoCloseConsole=true",
        "OverrideGameTime=true", "ShowGameTime=false", "RecordGameTime=false", ""]))  # CakeBench joins by itself
    shutil.copy(bench_jar(), MC / "mods" / bench_jar().name)


def bench_jar():
    return sorted((BENCH / "mod" / "build" / "libs").glob("cakebench-*.jar"))[-1]


def cmd_setup(args):
    if not JDK.exists():
        shutil.copytree(JDK_SRC, JDK)  # its own copy, so the GPU preference only hits the bench
    setup_server()
    mrpack = pathlib.Path(args.mrpack) if args.mrpack else cached(
        "https://github.com/HarrytheCake/Monumenta-Cakedition/releases/download/v2.2.1/Monumenta-Cakedition-2.2.1.mrpack",
        "Monumenta-Cakedition-2.2.1.mrpack")
    setup_instance(mrpack)
    print(f"Instance '{INSTANCE_NAME}' ready; restart Prism if it is open so it lists the instance.")
    # priming run: yosbr copies the shipped defaults in, mods write their configs; that state is every run's start
    with Server() as server:
        run_client(server, {"run": "prime", "scenes": []}, timeout=600)
    if STATE.exists():
        shutil.rmtree(STATE)
    for part in ["mods", "config"]:
        shutil.copytree(MC / part, STATE / part)
    opts = (MC / "options.txt").read_text(encoding="utf-8")
    # the only bench-specific option: don't open the pause menu when another window takes focus
    opts = re.sub(r"^pauseOnLostFocus:.*$", "pauseOnLostFocus:false", opts, flags=re.M)
    (STATE / "options.txt").write_text(opts, encoding="utf-8")
    print("Primed; baseline state saved.")


def cmd_gpu(args):
    key = r"HKCU:\Software\Microsoft\DirectX\UserGpuPreferences"
    exe = str(JDK / "bin" / "javaw.exe").replace("/", "\\")
    if args.mode == "igpu":
        r = ps(f"New-Item -Path '{key}' -Force | Out-Null; "
               f"Set-ItemProperty -Path '{key}' -Name '{exe}' -Value 'GpuPreference=1;'")
    else:
        r = ps(f"Remove-ItemProperty -Path '{key}' -Name '{exe}' -ErrorAction SilentlyContinue")
    print(r.stdout or r.stderr or f"GPU preference for {exe}: {args.mode}")


# --- server ----------------------------------------------------------------------------------------------------

class Server:
    def __init__(self):
        self.proc = None
        self.lines = []
        self.joined = threading.Event()

    def __enter__(self):
        self.proc = subprocess.Popen([str(SERVER_JAVA), "-Xmx3G", "-jar", "fabric-server-launch.jar", "nogui"],
                                     cwd=SERVER, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
        try:
            return self._start()
        except BaseException:
            self.proc.kill()  # never leave the server running behind a failed start
            raise

    def _start(self):
        set_affinity(self.proc.pid, SERVER_AFFINITY)
        threading.Thread(target=self._read, daemon=True).start()
        self.wait_for(r"Done \(", 600)
        built = (SERVER / "world" / "cakebench-built").exists()
        for c in ["gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                  "gamerule sendCommandFeedback false", "gamerule doFireTick false", "gamerule randomTickSpeed 0",
                  "gamerule mobGriefing false", "gamerule doPatrolSpawning false", "gamerule doTraderSpawning false",
                  "gamerule doInsomnia false", "gamerule fallDamage false", "time set noon", "weather clear", "datapack enable \"file/cakebench\""]:
            self.send(c)
        for s in SCENES:
            x, z = int(s["x"]), int(s["z"])
            self.send(f"forceload add {x - 40} {z - 40} {x + 40} {z + 40}")
            # a build into chunks that aren't loaded yet fails silently, so wait until the whole area is
            for cx, cz in [(x - 40, z - 40), (x + 40, z + 40), (x - 40, z + 40), (x + 40, z - 40)]:
                self.wait_until(f"execute if loaded {cx} 120 {cz}", 300)
            if not built:
                self.send(f"function cakebench:build_{s['name']}")
                time.sleep(2)
        time.sleep(5)  # entity storage loads after the chunk itself
        self.summon_missing()
        self.verify()
        (SERVER / "world" / "cakebench-built").write_text("built\n")
        self.spawn_bots()
        return self

    def wait_until(self, test, timeout):
        """Repeat an `execute if ...` test on the console until it passes."""
        end = time.time() + timeout
        while time.time() < end:
            if self.test(test) is not None:
                return
            time.sleep(1)
        sys.exit(f"server: '{test}' never passed")

    def test(self, test):
        """Run an `execute if ...` test; returns the count when it passes, else None."""
        mark = len(self.lines)
        self.send(test)
        end = time.time() + 5
        while time.time() < end:
            for line in self.lines[mark:]:
                m = re.search(r"Test passed(?:, count: (\d+))?", line)
                if m:
                    return int(m.group(1) or 1)
                if "Test failed" in line:
                    return None
            time.sleep(0.1)
        return None

    # scene -> (box corner x, z) and how many entities it holds (60 x 40 x 60 box from y=110)
    ENTITIES = {"normal": ((-30, -30), 20), "mobs": ((1970, -30), 300), "mixed": ((7970, -30), 100)}

    def count(self, scene):
        (x, z), _ = self.ENTITIES[scene]
        return self.test(f"execute if entity @e[tag=cb,x={x},y=110,z={z},dx=60,dy=40,dz=60]") or 0

    def summon_missing(self):
        """Summoned entities can be lost while a chunk's entity storage is still loading; put back what's missing."""
        for scene, ((x, z), want) in self.ENTITIES.items():
            for _ in range(3):
                if self.count(scene) == want:
                    break
                self.send(f"kill @e[tag=cb,x={x},y=110,z={z},dx=60,dy=40,dz=60]")
                self.send(f"function cakebench:summon_{scene}")
                time.sleep(3)

    def verify(self):
        """Every scene must hold what it was built with; stop rather than measure a half-built scene."""
        problems = []
        for scene, (_, want) in self.ENTITIES.items():
            got = self.count(scene)
            print(f"  scene check: {scene} entities {got}/{want}")
            if got != want:
                problems.append(f"{scene}: {got} entities instead of {want}")
        for pos, block in [("3970 121 -30", "barrel"), ("3989 123 6", "barrel"), ("3989 124 6", "oak_sign"),
                           ("3970 121 7", "oak_sign"), ("1983 127 -17", "stone_bricks"), ("8001 122 17", "barrel"),
                           ("-14 121 14", "chest"), ("6020 120 20", "smooth_stone")]:
            if self.test(f"execute if block {pos} minecraft:{block}") is None:
                problems.append(f"no {block} at {pos}")
        if problems:
            sys.exit("bench world is incomplete (delete tools/bench/work/server/world and run setup):\n  "
                     + "\n  ".join(problems))

    def spawn_bots(self):
        armor = ["diamond_helmet", "diamond_chestplate", "netherite_leggings", "iron_boots"]
        slots = ["armor.head", "armor.chest", "armor.legs", "armor.feet"]
        # names no real account has, so Carpet keeps them as written and gives them default skins
        bots = [(f"zzCakeBench{i:02d}", 5981 + (i % 10) * 4, -17 + (i // 10) * 8) for i in range(50)]
        bots += [(f"zzCakeMixed{i:02d}", 8005 + (i % 5) * 3, -18 + (i // 5) * 3) for i in range(25)]
        for name, x, z in bots:
            self.send(f"cbbot {name} {x} 121 {z}")
        self.wait_for(rf"{bots[-1][0]}\[[^\]]*\] logged in", 60)
        for name, _, _ in bots:
            for slot, item in zip(slots, armor):
                self.send(f"item replace entity {name} {slot} with minecraft:{item}")
            self.send(f"item replace entity {name} weapon.mainhand with minecraft:diamond_sword")

    def _read(self):
        for line in self.proc.stdout:
            self.lines.append(line.rstrip())
            # fake players log in from "[embedded]"; the bench client from a socket address like "/127.0.0.1:50123"
            m = re.search(r"\]: (\w+)\[([^\]]*)\] logged in with entity id", line)
            if m and m.group(2).startswith("/"):
                self.send(f"op {m.group(1)}")
                self.send(f"gamemode spectator {m.group(1)}")
                self.joined.set()

    def wait_for(self, pattern, timeout, fatal=True):
        end = time.time() + timeout
        while time.time() < end:
            self.check()
            if any(re.search(pattern, l, re.I) for l in self.lines[-200:]):
                return True
            time.sleep(0.5)
        if fatal:
            sys.exit(f"server: no '{pattern}' after {timeout}s")
        print(f"  server: no '{pattern}' after {timeout}s, going on")
        return False

    def check(self):
        if self.proc.poll() is not None:
            sys.exit("the bench server stopped; see tools/bench/work/server/logs/latest.log")

    def send(self, command):
        self.proc.stdin.write(command + "\n")
        self.proc.stdin.flush()

    def __exit__(self, *exc):
        self.send("stop")
        try:
            self.proc.wait(120)
        except subprocess.TimeoutExpired:
            self.proc.kill()


# --- runs ------------------------------------------------------------------------------------------------------

def reset_instance(mod_set):
    """Pack mods and configs as primed, then the set's changes."""
    for part in ["mods", "config"]:
        if (MC / part).exists():
            shutil.rmtree(MC / part)
        shutil.copytree(STATE / part, MC / part)
    shutil.copy(STATE / "options.txt", MC / "options.txt")
    for prefix in mod_set.get("remove", []):
        hits = [p for p in (MC / "mods").glob("*.jar") if p.name.lower().startswith(prefix.lower())]
        if not hits:
            sys.exit(f"set {mod_set['name']}: no jar starts with '{prefix}'")
        for p in hits:
            p.unlink()
    for version_id in mod_set.get("add", []):
        jar = modrinth_version(version_id)
        shutil.copy(jar, MC / "mods" / jar.name)
    for rel, content in mod_set.get("files", {}).items():
        p = MC / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content if isinstance(content, str) else json.dumps(content, indent=2), encoding="utf-8")
    for line in mod_set.get("options", []):  # options.txt lines, e.g. "renderDistance:8"
        key = line.split(":", 1)[0]
        opts = (MC / "options.txt").read_text(encoding="utf-8")
        opts = re.sub(rf"^{re.escape(key)}:.*$", line, opts, flags=re.M)
        (MC / "options.txt").write_text(opts, encoding="utf-8")


def run_client(server, plan, timeout):
    out_dir = RESULTS / plan.get("batch", "setup")
    plan = {"out": str(out_dir), "server": f"127.0.0.1:{PORT}", "settleSeconds": 15, "warmupSeconds": 10, "measureSeconds": 30,
            "turnDegreesPerSecond": 6, **plan}
    (MC / "config" / "cakebench").mkdir(parents=True, exist_ok=True)
    (MC / "config" / "cakebench" / "plan.json").write_text(json.dumps(plan, indent=2))
    summary = out_dir / plan["run"] / "summary.json"
    if summary.exists():
        summary.unlink()
    server.joined.clear()
    subprocess.Popen([str(PRISM), "--launch", INSTANCE_NAME])
    end, pinned = time.time() + timeout, set()
    while time.time() < end:
        if server.proc.poll() is not None:
            for pid in game_pids():
                ps(f"Stop-Process -Id {pid} -Force")
            server.check()
        for pid in game_pids():
            if pid not in pinned:
                set_affinity(pid, CLIENT_AFFINITY)
                pinned.add(pid)
        if summary.exists() and not game_pids():
            return json.loads(summary.read_text())
        time.sleep(2)
    for pid in game_pids():
        ps(f"Stop-Process -Id {pid} -Force")
    print(f"  {plan['run']}: timed out after {timeout}s")
    return None


def cmd_batch(args):
    sets = json.loads(pathlib.Path(args.sets).read_text(encoding="utf-8"))
    batch = args.name or f"{datetime.date.today().isoformat()}-{pathlib.Path(args.sets).stem}"
    measure, warmup = sets.get("measureSeconds", 30), sets.get("warmupSeconds", 10)
    per_run = 90 + len(SCENES) * (warmup + measure)
    # a cool laptop boosts harder: the first run of a batch only warms it up and isn't reported
    all_sets = [{"name": "_warmup", "why": "warms the laptop up; not reported"}] + sets["sets"]
    repeats = {s["name"]: s.get("repeats", args.repeats) for s in all_sets}  # a set may ask for more
    total = sum(repeats.values()) * per_run
    print(f"Batch {batch}: {len(all_sets)} sets, {sum(repeats.values())} runs, about {total / 3600:.1f} h")
    with Server() as server:
        for s in all_sets:
            for r in range(1, repeats[s["name"]] + 1):
                run = f"{s['name']}#{r}"
                if (RESULTS / batch / run / "summary.json").exists():
                    continue  # resume a stopped batch
                reset_instance(s)
                power = power_state()
                print(f"  {run} ({power}) ...", flush=True)
                result = run_client(server, {"batch": batch, "run": run, "scenes": SCENES, "measureSeconds": measure,
                                             "warmupSeconds": warmup, "notes": {"set": s, "power": power}},
                                    timeout=per_run + 300)
                if result:
                    print("   ", {k: v["avgFps"] for k, v in result["scenes"].items()},
                          *(["FLAGGED: " + "; ".join(flags(result))] if flags(result) else []))
    print(f"Done. python tools/bench/bench.py report {batch}")


def cmd_serve(args):
    """The bench server with every scene and fake player, for looking around by hand; stops when work/stop exists."""
    stop = WORK / "stop"
    stop.unlink(missing_ok=True)
    with Server():
        print(f"Bench server up on 127.0.0.1:{PORT}. Scenes (use /tp in game):")
        for s in SCENES:
            print(f"  {s['name']}: /tp @s {s['x']} {s['y']} {s['z']} {s['yaw']} {s['pitch']}")
        print(f"Create {stop} to stop it.", flush=True)
        while not stop.exists():
            time.sleep(2)
    stop.unlink(missing_ok=True)


def power_state():
    """'AC 100%' or 'battery 63%': a laptop on battery throttles, so such runs aren't comparable."""
    out = ps("Add-Type -AssemblyName System.Windows.Forms; $p = [System.Windows.Forms.SystemInformation]::PowerStatus; "
             "\"$($p.PowerLineStatus) $([int]($p.BatteryLifePercent * 100))\"").stdout.split()
    if len(out) < 2:
        return "unknown"
    return f"{'AC' if out[0] == 'Online' else 'battery'} {out[1]}%"


def flags(result):
    """Reasons a run can't be compared with the others."""
    out = []
    power = result.get("notes", {}).get("power", "")
    if power and not power.startswith("AC"):
        out.append(f"power: {power}")
    for name, sc in result["scenes"].items():
        if sc.get("unfocusedFrames") or sc.get("minimizedFrames"):
            out.append(f"{name}: window unfocused {sc.get('unfocusedFrames', 0)} / minimized "
                       f"{sc.get('minimizedFrames', 0)} of {sc['frames']} frames")
    return out


# --- report ----------------------------------------------------------------------------------------------------

def cmd_report(args):
    base = RESULTS / args.batch
    runs, flagged = {}, []
    for summary in sorted(base.glob("*/summary.json")):
        s = json.loads(summary.read_text())
        if s["run"].startswith("_"):
            continue
        if flags(s):
            flagged.append((s["run"], flags(s)))
            continue  # left out of the medians
        runs.setdefault(s["run"].split("#")[0], []).append(s)
    if not runs:
        sys.exit(f"no results in {base}")
    first = next(iter(runs.values()))[0]
    scenes = [s["name"] for s in SCENES]
    baseline = runs.get("baseline")

    def med(rs, scene, key):
        vals = [r["scenes"][scene][key] for r in rs if scene in r["scenes"]]
        return statistics.median(vals) if vals else None

    lines = [f"# Benchmark {args.batch}", "",
             f"- Renderer: {first['renderer']} ({first['glVersion']})",
             f"- Java {first['java']}, heap {first['maxHeapMB']} MB, {first['cpus']} CPUs visible to the game",
             f"- Render distance {first['renderDistance']}, graphics {first['graphics']}, max FPS {first['maxFps']}",
             "- Scenes are synthetic (tools/bench/bench.py); medians over repeats; 1% low = average FPS of the slowest 1% of frames.",
             ""]
    for key, title in [("avgFps", "Average FPS"), ("onePercentLowFps", "1% low FPS"), ("maxFrameMs", "Worst frame (ms)")]:
        lines += [f"## {title}", "", "| Set | runs | " + " | ".join(scenes) + " |",
                  "|---|---|" + "---|" * len(scenes)]
        for name, rs in runs.items():
            cells = []
            for sc in scenes:
                v = med(rs, sc, key)
                cell = "–" if v is None else f"{v:g}"
                if baseline and name != "baseline" and v is not None and key != "maxFrameMs":
                    b = med(baseline, sc, key)
                    if b:
                        cell += f" ({(v - b) / b * 100:+.0f}%)"
                cells.append(cell)
            lines.append(f"| {name} | {len(rs)} | " + " | ".join(cells) + " |")
        lines.append("")
    if flagged:
        lines += ["## Left out", ""] + [f"- {run}: {'; '.join(why)}" for run, why in flagged] + [""]
    lines += ["## Sets", ""]
    for name, rs in runs.items():
        s = rs[0]["notes"].get("set", {})
        lines.append(f"- **{name}**: {s.get('why', '')} "
                     f"{'add ' + ', '.join(s['add']) if s.get('add') else ''} "
                     f"{'remove ' + ', '.join(s['remove']) if s.get('remove') else ''}".rstrip())
    out = ROOT / "docs" / "benchmarks" / f"{args.batch}.md"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(out.relative_to(ROOT).as_posix())


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("setup")
    s.add_argument("--mrpack")
    g = sub.add_parser("gpu")
    g.add_argument("mode", choices=["igpu", "default"])
    b = sub.add_parser("batch")
    b.add_argument("sets")
    b.add_argument("--repeats", type=int, default=1)
    b.add_argument("--name")
    sub.add_parser("serve")
    r = sub.add_parser("report")
    r.add_argument("batch")
    args = p.parse_args()
    {"setup": cmd_setup, "gpu": cmd_gpu, "batch": cmd_batch, "serve": cmd_serve, "report": cmd_report}[args.cmd](args)


if __name__ == "__main__":
    main()
