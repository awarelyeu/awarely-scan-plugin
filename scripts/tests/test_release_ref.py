import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('release_ref', Path(__file__).parents[1] / 'verify-release-ref.py')
release_ref = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release_ref)


class ReleaseRefTest(unittest.TestCase):
    def setUp(self):
        self.commit = 'a' * 40
        self.target = {'type': 'commit', 'sha': self.commit}
        self.objects = {
            ('commits', self.commit): {'verification': {'verified': True}},
            ('tags', 'b' * 40): {'verification': {'verified': True}, 'object': self.target},
        }

    def check(self, ref):
        release_ref.verify(ref, self.commit, lambda kind, sha: self.objects[kind, sha])

    def test_lightweight_ref_on_verified_commit(self):
        self.check({'object': self.target})

    def test_signed_annotated_ref(self):
        self.check({'object': {'type': 'tag', 'sha': 'b' * 40}})

    def test_unsigned_annotated_tag_rejected_even_on_verified_commit(self):
        self.objects['tags', 'b' * 40]['verification']['verified'] = False
        with self.assertRaisesRegex(ValueError, 'Annotated release tags'):
            self.check({'object': {'type': 'tag', 'sha': 'b' * 40}})

    def test_unverified_commit_rejected(self):
        self.objects['commits', self.commit]['verification']['verified'] = False
        with self.assertRaisesRegex(ValueError, 'Release commit'):
            self.check({'object': self.target})

    def test_changed_target_rejected(self):
        with self.assertRaisesRegex(ValueError, 'checked-out commit'):
            self.check({'object': {'type': 'commit', 'sha': 'c' * 40}})

    def test_nested_tags_rejected(self):
        self.objects['tags', 'b' * 40]['object'] = {'type': 'tag', 'sha': 'c' * 40}
        with self.assertRaisesRegex(ValueError, 'checked-out commit'):
            self.check({'object': {'type': 'tag', 'sha': 'b' * 40}})

    def test_missing_signature_rejected(self):
        self.objects['commits', self.commit] = {}
        with self.assertRaisesRegex(ValueError, 'Release commit'):
            self.check({'object': self.target})


if __name__ == '__main__':
    unittest.main()
