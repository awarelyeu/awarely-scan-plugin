import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('preview', Path(__file__).parents[1] / 'package-preview.py')
preview = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preview)


class PreviewPackagingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.tag = 'v0.1.0-alpha.2'
        self.version = '0.1.0-alpha.2'
        self.pins = ('version=v0.10.0\n' + ''.join(
            f'{arch}.{kind}=' + 'a' * 64 + '\n'
            for arch in ('amd64', 'arm64') for kind in ('archive', 'binary'))).encode()
        manifest = self.root / 'src/main/resources' / preview.PINS
        manifest.parent.mkdir(parents=True)
        manifest.write_bytes(self.pins)
        (self.root / 'target').mkdir()
        self.write_hpi()
        self.bom = {'bomFormat': 'CycloneDX', 'metadata': {'component': {
            'name': 'awarely-scan', 'version': self.version}}, 'components': []}
        self.write_bom()

    def write_hpi(self, version=None, tag=None, pins=None):
        jar = io.BytesIO()
        with zipfile.ZipFile(jar, 'w') as z:
            z.writestr(preview.PINS, pins if pins is not None else self.pins)
        with zipfile.ZipFile(self.root / 'target/awarely-scan.hpi', 'w') as z:
            z.writestr('META-INF/MANIFEST.MF', 'Short-Name: awarely-scan\r\n'
                       f'Plugin-Version: {version or self.version}\r\n'
                       f'Plugin-ScmTag: {tag or self.tag}\r\n')
            z.writestr('WEB-INF/lib/awarely-scan.jar', jar.getvalue())

    def write_bom(self):
        (self.root / 'target/bom.json').write_text(json.dumps(self.bom))

    def test_artifacts_and_checksums_match(self):
        dest = preview.package(self.root, self.tag)
        for line in (dest / 'SHA256SUMS').read_text().splitlines():
            digest, name = line.split('  ')
            self.assertEqual(digest, hashlib.sha256((dest / name).read_bytes()).hexdigest())
        self.assertEqual(len(list(dest.iterdir())), 3)

    def test_no_overwrite(self):
        dest = preview.package(self.root, self.tag)
        before = {p.name: p.read_bytes() for p in dest.iterdir()}
        with self.assertRaises(FileExistsError):
            preview.package(self.root, self.tag)
        self.assertEqual(before, {p.name: p.read_bytes() for p in dest.iterdir()})

    def test_only_exact_alpha_tags(self):
        for tag in ['v0.1.0', 'v0.1.0-alpha.0', 'v0.1.0-alpha.2/../x', 'v0.1.0-alpha.2\n', '-DskipTests']:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                preview.preview_version(tag)

    def test_snapshot_hpi_rejected(self):
        self.write_hpi(version='999999-SNAPSHOT')
        with self.assertRaisesRegex(ValueError, 'HPI identity/version'):
            preview.package(self.root, self.tag)

    def test_wrong_source_tag_rejected(self):
        self.write_hpi(tag='v0.1.0-alpha.1')
        with self.assertRaisesRegex(ValueError, 'source tag'):
            preview.package(self.root, self.tag)

    def test_bundled_tool_pin_drift_rejected(self):
        self.write_hpi(pins=self.pins.replace(b'v0.10.0', b'v0.10.1'))
        with self.assertRaisesRegex(ValueError, 'HPI tool pins differ'):
            preview.package(self.root, self.tag)

    def test_missing_pins_rejected(self):
        (self.root / 'src/main/resources' / preview.PINS).write_text('version=v0.10.0\n')
        with self.assertRaisesRegex(ValueError, 'Unfinished tool pins'):
            preview.package(self.root, self.tag)

    def test_bom_version_must_match(self):
        self.bom['metadata']['component']['version'] = '999999-SNAPSHOT'
        self.write_bom()
        with self.assertRaisesRegex(ValueError, 'Dependency inventory identity/version'):
            preview.package(self.root, self.tag)


if __name__ == '__main__':
    unittest.main()
