import copy
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('publisher', Path(__file__).with_name('publish-forge-fork.py'))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class ForgePublicationGuards(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads((publisher.ROOT / 'release/forge-fork.json').read_text())
        self.targets = json.loads((publisher.ROOT / 'release/targets.json').read_text())
        self.release = {'tag_name': 'v1.3.1', 'assets': [{'name': self.manifest['fileName'],
            'size': self.manifest['size'], 'digest': 'sha256:' + self.manifest['sha256'], 'state': 'uploaded'}]}

    def test_immutable_published_baseline(self):
        publisher.check_immutable_baseline(self.manifest)

    def test_immutable_baseline_rejects_retargeted_tag(self):
        tagged = copy.deepcopy(self.manifest)
        self.manifest['tag'] = 'HEAD'
        self.manifest['sha256'] = '0' * 64
        with self.assertRaisesRegex(AssertionError, 'retargeted'):
            publisher.validate_immutable_baseline(self.manifest, tagged)

    def test_cli_baseline_never_reads_manifest_selected_ref(self):
        tampered = copy.deepcopy(self.manifest)
        tampered['tag'] = 'HEAD'
        tampered['sha256'] = '0' * 64
        calls = []

        def fake_git(*args):
            calls.append(args)
            if args == ('git', 'rev-parse', 'refs/tags/v1.3.1^{commit}'):
                return publisher.PUBLISHED_FORGE_TAG_COMMIT
            if args == ('git', 'show', 'v1.3.1:release/forge-fork.json'):
                return json.dumps(self.manifest)
            if args == ('git', 'show', 'HEAD:release/forge-fork.json'):
                return json.dumps(tampered)  # old implementation accepted this self-selected baseline
            raise AssertionError(f'Unexpected git request: {args}')

        with patch.object(publisher, 'output', side_effect=fake_git):
            with self.assertRaises(AssertionError):
                publisher.check_immutable_baseline(tampered)
        self.assertNotIn(('git', 'show', 'HEAD:release/forge-fork.json'), calls)
        self.assertIn(('git', 'show', 'v1.3.1:release/forge-fork.json'), calls)

    def test_immutable_baseline_rejects_relocated_tag_commit(self):
        with patch.object(publisher, 'output', return_value='0' * 40):
            with self.assertRaisesRegex(AssertionError, 'tag was moved'):
                publisher.check_immutable_baseline(self.manifest)

    def test_immutable_baseline_rejects_changed_digest(self):
        tagged = copy.deepcopy(self.manifest)
        self.manifest['sha256'] = '0' * 64
        with self.assertRaises(AssertionError):
            publisher.validate_immutable_baseline(self.manifest, tagged)

    def test_immutable_baseline_rejects_relabelled_version(self):
        tagged = copy.deepcopy(self.manifest)
        self.manifest['version'] = '1.3.2'
        with self.assertRaises(AssertionError):
            publisher.validate_immutable_baseline(self.manifest, tagged)

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
