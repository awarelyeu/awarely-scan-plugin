"""Package a versioned GitHub preview without changing Jenkins CD versioning."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PINS = 'io/jenkins/plugins/awarely/tools.properties'


def preview_version(tag):
    if not re.fullmatch(r'v\d+\.\d+\.\d+-alpha\.[1-9]\d*', tag):
        raise ValueError('Expected an exact preview tag such as v0.1.0-alpha.2')
    return tag[1:]


def package(root, tag):
    version = preview_version(tag)
    pins_bytes = (root / 'src/main/resources' / PINS).read_bytes()
    pins = dict(line.split('=', 1) for line in pins_bytes.decode().splitlines()
                if line and not line.startswith('#'))
    if not re.fullmatch(r'v\d+\.\d+\.\d+(?:-alpha\.\d+)?', pins.get('version', '')):
        raise ValueError('Missing exact CLI version')
    for arch in ('amd64', 'arm64'):
        for kind in ('archive', 'binary'):
            if not re.fullmatch(r'[0-9a-f]{64}', pins.get(arch + '.' + kind, '')):
                raise ValueError('Unfinished tool pins cannot be released')
    hpi = root / 'target/awarely-scan.hpi'
    with zipfile.ZipFile(hpi) as archive:
        text = re.sub(r'\r?\n ', '', archive.read('META-INF/MANIFEST.MF').decode())
        manifest = dict(line.split(': ', 1) for line in text.splitlines() if ': ' in line)
        if manifest.get('Plugin-Version') != version or manifest.get('Short-Name') != 'awarely-scan':
            raise ValueError('HPI identity/version does not match the preview tag')
        if manifest.get('Plugin-ScmTag') != tag:
            raise ValueError('HPI source tag does not match the preview tag')
        with zipfile.ZipFile(io.BytesIO(archive.read('WEB-INF/lib/awarely-scan.jar'))) as jar:
            if jar.read(PINS) != pins_bytes:
                raise ValueError('HPI tool pins differ from the reviewed manifest')
    bom = root / 'target/bom.json'
    inventory = json.loads(bom.read_text())
    component = inventory.get('metadata', {}).get('component', {})
    if (inventory.get('bomFormat') != 'CycloneDX'
            or not isinstance(inventory.get('components'), list)
            or component.get('name') != 'awarely-scan'
            or component.get('version') != version):
        raise ValueError('Dependency inventory identity/version does not match the HPI')
    dest = root / 'dist/preview'
    dest.mkdir(parents=True, exist_ok=False)
    shutil.copyfile(hpi, dest / f'awarely-scan-jenkins-{version}.hpi')
    shutil.copyfile(bom, dest / f'awarely-scan-jenkins-{version}.cdx.json')
    (dest / 'SHA256SUMS').write_text(''.join(
        hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n'
        for p in sorted(dest.iterdir())))
    return dest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('tag')
    parser.add_argument('--version-only', action='store_true')
    args = parser.parse_args()
    try:
        if args.version_only:
            print(preview_version(args.tag))
        else:
            print('Packaged preview: ' + str(package(ROOT, args.tag)))
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        raise SystemExit(str(error))
