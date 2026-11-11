"""Smoke test: start one dev client of a Cake mod, wait for the title screen (or for the mod's own
self-test to finish), check the log, close the game.

usage: python tools/multiloader/smoke.py <mod folder> <mc version> <fabric|neoforge>
           [--jvm=-Dfoo=bar ...] [--done=REGEX] [--timeout=SECONDS] [--linger=SECONDS] [--keep-open]

  --done      log line that means "finished" (default: the title screen is up: "Sound engine started")
  --linger    seconds to keep running after --done matched (default 8), for late errors
  --jvm       extra JVM arguments for the game (e.g. a mod's self-test switch); may repeat
  --keep-open leave the game running (for a human look); no verdict
  --world=DIR copy this world (or DIR-<mc> if that exists, e.g. pre-upgraded with the dedicated server's
              --forceUpgrade) into run/saves/cake-smoke and open it at start (Minecraft's quick play), so
              mixins on in-game classes load too; --done then defaults to the player joining
  --args=...  extra program arguments for the game (space-separated)

Exit code 0 = reached --done with no error lines that mention the mod or mixins; 1 otherwise.
Writes the game's log to <mod>/<mc>/<loader>/run/logs/latest.log as usual and prints the
interesting lines. Only up to two dev clients run at once on this machine: the script waits for a
free slot (lock folders in the system temp dir), so parallel agents don't overload it.
"""
import os
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

SLOTS = 2
JDK25 = r'C:\Program Files\Java\jdk-25.0.4'


def take_slot():
    base = Path(tempfile.gettempdir()) / 'cake-smoke-slots'
    base.mkdir(exist_ok=True)
    while True:
        for i in range(SLOTS):
            d = base / f'slot{i}'
            try:
                d.mkdir()
                (d / 'pid').write_text(str(os.getpid()))
                return d
            except FileExistsError:
                # stale slot (its owner died)?
                try:
                    pid = int((d / 'pid').read_text())
                    out = subprocess.run(['tasklist', '/FI', f'PID eq {pid}'], capture_output=True, text=True).stdout
                    if str(pid) not in out:
                        for f in d.iterdir():
                            f.unlink()
                        d.rmdir()
                except (OSError, ValueError):
                    pass
        time.sleep(5)


def kill_tree(pid):
    subprocess.run(['taskkill', '/T', '/F', '/PID', str(pid)], capture_output=True)


def main():
    # log lines may hold characters the Windows console code page can't show (→, CJK)
    sys.stdout.reconfigure(errors='replace')
    args =[a for a in sys.argv[1:] if not a.startswith('--')]
    opts = [a for a in sys.argv[1:] if a.startswith('--')]
    mod, mc, loader = Path(args[0]).resolve(), args[1], args[2]
    jvm = [o.split('=', 1)[1] for o in opts if o.startswith('--jvm=')]
    done = next((o.split('=', 1)[1] for o in opts if o.startswith('--done=')), r'Sound engine started')
    timeout = int(next((o.split('=', 1)[1] for o in opts if o.startswith('--timeout=')), '900'))
    linger = int(next((o.split('=', 1)[1] for o in opts if o.startswith('--linger=')), '8'))
    keep = '--keep-open' in opts
    world = next((o.split('=', 1)[1] for o in opts if o.startswith('--world=')), None)
    prog = [a for o in opts if o.startswith('--args=') for a in o.split('=', 1)[1].split(' ') if a]
    if world and not any(o.startswith('--done=') for o in opts):
        done = r'joined the game'
    mod_id = re.search(r'^mod_id=(\S+)', (mod / 'gradle.properties').read_text(encoding='utf-8'), re.M).group(1)

    run_dir = mod / mc / loader / 'run'
    log = run_dir / 'logs' / 'latest.log'
    if log.exists():
        log.unlink()
    # skip the first-launch accessibility screen (it blocks quick play) and keep the window small
    run_dir.mkdir(parents=True, exist_ok=True)
    opts_file = run_dir / 'options.txt'
    lines = opts_file.read_text(encoding='utf-8').splitlines() if opts_file.exists() else []
    lines = [l for l in lines if not l.startswith(('onboardAccessibility:', 'tutorialStep:', 'joinedFirstServer:'))]
    lines += ['onboardAccessibility:false', 'tutorialStep:none', 'joinedFirstServer:true']
    opts_file.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    if world:
        import shutil
        # a world upgraded to this version beforehand (<world>-<mc>) skips 26.1's "back up before upgrading?" screen
        if Path(world + '-' + mc).is_dir():
            world = world + '-' + mc
        dst = run_dir / 'saves' / 'cake-smoke'
        shutil.rmtree(dst, ignore_errors=True)
        shutil.copytree(world, dst, ignore=shutil.ignore_patterns('session.lock'))
        prog += ['--quickPlaySingleplayer', 'cake-smoke']
    slot = take_slot()
    env = dict(os.environ)
    env['JAVA_HOME'] = env.get('CAKE_GRADLE_JAVA', JDK25)
    # several mods in one build (cake_mods, e.g. caketext/translator): Gradle runs from the parent folder
    root, prefix = (mod, '') if (mod / 'gradlew.bat').exists() else (mod.parent, f':{mod.name}')
    cmd = [str(root / 'gradlew.bat'), f'{prefix}:{mc}:{loader}:runClient', f'-Pcake.versions={mc}', '--console=plain']
    if jvm:
        # read by the kit's run configs (cake-fabric / cake-neoforge): space-separated JVM args
        cmd.append('-Pcake.runJvmArgs=' + ' '.join(jvm))
    if prog:
        cmd.append('-Pcake.runArgs=' + ' '.join(prog))
    print('>>', ' '.join(cmd), flush=True)
    gradle_log = open(run_dir / 'smoke-gradle.log', 'w', encoding='utf-8', errors='replace')
    p = subprocess.Popen(cmd, cwd=root, env=env, stdout=gradle_log, stderr=subprocess.STDOUT,
                         creationflags=subprocess.CREATE_NEW_PROCESS_GROUP)
    start = time.time()
    reached = None
    verdict = 1
    try:
        while time.time() - start < timeout:
            exited = p.poll() is not None
            # read the log even when the game just quit by itself (a self-test that ends the game)
            if log.exists():
                text = log.read_text(encoding='utf-8', errors='replace')
                if re.search(done, text):
                    reached = time.time()
                    break
            if exited:
                break
            time.sleep(3)
        if reached and not keep:
            time.sleep(linger)
        if keep and reached:
            print('game is up; left running (--keep-open)')
            return 0
    finally:
        if not keep:
            kill_tree(p.pid)
            time.sleep(2)
        for f in slot.iterdir():
            f.unlink()
        slot.rmdir()

    text = log.read_text(encoding='utf-8', errors='replace') if log.exists() else ''
    bad = []
    for line in text.splitlines():
        low = line.lower()
        severe = re.search(r'/(error|fatal)\]', low) or re.search(
            r'invalidinjectionexception|invalidmixinexception|mixinapplyerror|mixin apply.*failed|critical injection'
            r'|injection failure|could not be applied|mixintransformererror', low)
        if severe and (mod_id in low or 'mixin' in low or 'cake' in low):
            bad.append(line)
    loaded = [l for l in text.splitlines() if mod_id in l.lower()][:15]
    print(f'== {mod.name} {mc} {loader}: ' + ('reached "' + done + '"' if reached else 'did NOT reach "' + done + '"')
          + f' after {int((reached or time.time()) - start)} s')
    print('-- lines mentioning', mod_id)
    for l in loaded:
        print('  ', l[:300])
    print('-- error lines', len(bad))
    for l in bad[:40]:
        print('  ', l[:300])
    if not reached and not text:
        print('-- no game log; see', run_dir / 'smoke-gradle.log')
    verdict = 0 if reached and not bad else 1
    print('SMOKE', 'PASS' if verdict == 0 else 'FAIL')
    return verdict


if __name__ == '__main__':
    sys.exit(main())
