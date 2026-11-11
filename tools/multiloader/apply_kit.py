"""Copies the Cake multiloader kit (tools/multiloader/kit) into a mod folder, so each mod folder
builds on its own (it is also its own GitHub repo through git subtree).

usage: python tools/multiloader/apply_kit.py <mod folder> [<mc version> ...]

Copies settings.gradle, build.gradle, buildSrc/, the Gradle wrapper, and for each Minecraft
version folder of the mod (or the versions given) kit/versions/<mc>.properties → <mod>/<mc>/version.properties.
Several mods in one build (gradle.properties cake_mods=a,b,c, e.g. caketext): the build files go into the
folder given, the version files into the version folders of a/, b/, c/.
Never touches the mod's sources, gradle.properties or metadata. Run it again after changing the kit.
"""
import shutil
import sys
from pathlib import Path

KIT = Path(__file__).resolve().parent / 'kit'


def cake_mods(root):
    props = root / 'gradle.properties'
    if not props.exists():
        return []
    for line in props.read_text(encoding='utf-8').splitlines():
        key, sep, value = line.partition('=')
        if sep and key.strip() == 'cake_mods':
            return [m.strip() for m in value.split(',') if m.strip()]
    return []


def apply_versions(mod, versions):
    if not versions:
        versions = [d.name for d in mod.iterdir() if d.is_dir() and (KIT / 'versions' / (d.name + '.properties')).exists()]
    for v in versions:
        (mod / v).mkdir(exist_ok=True)
        shutil.copy2(KIT / 'versions' / (v + '.properties'), mod / v / 'version.properties')
    # each part needs a build.gradle that applies its convention plugin; extra lines (dependencies) are the mod's
    parts = [mod / 'core'] if (mod / 'core').is_dir() else []
    for d in mod.iterdir():
        if d.is_dir() and (d / 'version.properties').exists():
            parts += [d / p for p in ('common', 'fabric', 'neoforge') if (d / p).is_dir()]
    for part in parts:
        bg = part / 'build.gradle'
        if not bg.exists():
            bg.write_text("plugins {\n\tid 'cake-%s'\n}\n" % part.name, encoding='utf-8', newline='')
    return sorted(versions)


def main():
    root = Path(sys.argv[1]).resolve()
    versions = sys.argv[2:]
    for name in ['settings.gradle', 'build.gradle', 'gradlew', 'gradlew.bat']:
        shutil.copy2(KIT / name, root / name)
    (root / 'gradle' / 'wrapper').mkdir(parents=True, exist_ok=True)
    for name in ['gradle-wrapper.jar', 'gradle-wrapper.properties']:
        shutil.copy2(KIT / 'gradle' / 'wrapper' / name, root / 'gradle' / 'wrapper' / name)
    if (root / 'buildSrc').exists():
        shutil.rmtree(root / 'buildSrc' / 'src', ignore_errors=True)
    shutil.copytree(KIT / 'buildSrc', root / 'buildSrc', dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns('build', '.gradle'))
    mods = cake_mods(root)
    for mod in ([root / m for m in mods] if mods else [root]):
        print('kit applied to', mod.name, 'versions', apply_versions(mod, versions))


if __name__ == '__main__':
    main()
