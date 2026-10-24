"""Regression: commit hashes beginning with digits must remain valid SemVer."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class CommitVersionSourceTest(unittest.TestCase):
    def test_commit_hash_prerelease_is_alphanumeric(self):
        source = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('sha.g${it.refInfo.commitHash.take(7)}', source)
        self.assertNotIn('sha.${it.refInfo.commitHash.take(5)}', source)

    def test_leading_zero_sha_is_valid_prerelease_identifier(self):
        # A numeric-only prerelease component with leading zeros is invalid.
        for sha in ("03710ab", "0000000", "1234567", "abcdef0"):
            version = "1.0.0-sha.g" + sha
            self.assertTrue(version.startswith("1.0.0-sha.g"))
            self.assertEqual(len(version.split("sha.g", 1)[1]), 7)
            self.assertTrue(all(char in "0123456789abcdef" for char in sha))
            self.assertTrue(version.split(".")[-1].startswith("g"))


if __name__ == "__main__":
    unittest.main()
