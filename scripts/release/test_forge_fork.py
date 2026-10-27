import copy
import importlib.util
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('publisher', Path(__file__).with_name('publish-forge-fork.py'))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class ForgePublicationGuards(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads((publisher.ROOT / 'release/forge-fork.json').read_text())
        self.targets = json.loads((publisher.ROOT / 'release/targets.json').read_text())
        self.release = {'tag_name': 'v1.3.1', 'assets': [{'name': self.manifest['fileName'],
            'size': self.manifest['size'], 'digest': 'sha256:' + self.manifest['sha256'], 'state': 'uploaded'}]}

    def rejected(self):
        with self.assertRaises(AssertionError):
            publisher.validate(self.manifest, self.release, self.targets)

    def test_exact_forge_scope(self):
        publisher.validate(self.manifest, self.release, self.targets)

    def test_other_repository(self):
        self.manifest['repository'] = 'YuWan-030/TrueUUID'; self.rejected()

    def test_other_loader(self):
        self.manifest['target'] = 'fabric-1.20.1'; self.rejected()

    def test_no_all_loader_waiver(self):
        self.targets['release_ready'] = True; self.rejected()

    def test_no_individual_loader_waiver(self):
        self.targets['targets'][0]['release'] = True; self.rejected()

    def test_wrong_tag(self):
        self.release['tag_name'] = 'v1.3.2'; self.rejected()

    def test_extra_loader_asset(self):
        self.release['assets'].append(copy.deepcopy(self.release['assets'][0])); self.rejected()

    def test_changed_hash(self):
        self.release['assets'][0]['digest'] = 'sha256:' + '0' * 64; self.rejected()

    def test_changed_size(self):
        self.release['assets'][0]['size'] += 1; self.rejected()

    def test_incomplete_upload(self):
        self.release['assets'][0]['state'] = 'new'; self.rejected()


if __name__ == '__main__':
    unittest.main()
