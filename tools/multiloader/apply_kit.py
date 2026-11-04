"""Copies the Cake multiloader kit (tools/multiloader/kit) into a mod folder, so each mod folder
builds on its own (it is also its own GitHub repo through git subtree).

usage: python tools/multiloader/apply_kit.py <mod folder> [<mc version> ...]

Copies settings.gradle, build.gradle, buildSrc/, the Gradle wrapper, and for each Minecraft
version folder of the mod (or the versions given) kit/versions/<mc>.properties → <mod>/<mc>/version.properties.
Never touches the mod's sources, gradle.properties or metadata. Run it again after changing the kit.
"""
import shutil
import sys
from pathlib import Path

KIT = Path(__file__).resolve().parent / 'kit'


def main():
    mod = Path(sys.argv[1]).resolve()
    versions = sys.argv[2:]
    for name in ['settings.gradle', 'build.gradle', 'gradlew', 'gradlew.bat']:
        shutil.copy2(KIT / name, mod / name)
    (mod / 'gradle' / 'wrapper').mkdir(parents=True, exist_ok=True)
    for name in ['gradle-wrapper.jar', 'gradle-wrapper.properties']:
        shutil.copy2(KIT / 'gradle' / 'wrapper' / name, mod / 'gradle' / 'wrapper' / name)
    if (mod / 'buildSrc').exists():
        shutil.rmtree(mod / 'buildSrc' / 'src', ignore_errors=True)
    shutil.copytree(KIT / 'buildSrc', mod / 'buildSrc', dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns('build', '.gradle'))
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
    print('kit applied to', mod.name, 'versions', sorted(versions))


if __name__ == '__main__':
    main()
