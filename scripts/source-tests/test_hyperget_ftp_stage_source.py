"""Source-only invariants for HyperGet FTP staged-file safety.

This checks wiring and regression coverage; it does NOT compile the app or
substitute for Windows FTP integration tests.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
FTP = ROOT / "downloader/core/src/desktopMain/kotlin/ir/amirab/downloader/ftp"


class StagedFileSourceContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.output = (FTP / "FtpStagedOutput.kt").read_text(encoding="utf-8")
        cls.publish = (FTP / "FtpStagedFileCommit.kt").read_text(encoding="utf-8")
        cls.transfer = (FTP / "FtpTransferClient.kt").read_text(encoding="utf-8")
        cls.resume_identity = (FTP / "FtpResumeIdentity.kt").read_text(encoding="utf-8")
        cls.regression = (
            ROOT / "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/ftp/FtpStagedIdentityTest.kt"
        ).read_text(encoding="utf-8")

    def test_opened_stage_never_follows_symlinks(self):
        self.assertIn("LinkOption.NOFOLLOW_LINKS", self.output)
        self.assertIn("Files.newByteChannel(part, options)", self.output)
        self.assertIn("!after.isRegularFile", self.output)

    def test_opened_stage_validates_length_and_file_identity(self):
        self.assertIn("channel.size() != expectedSize || after.size() != expectedSize", self.output)
        self.assertIn("before.fileKey() != after.fileKey()", self.output)
        self.assertIn("after.fileKey()", self.output)
        self.assertIn("before.creationTime() != after.creationTime()", self.output)
        self.assertIn("after.creationTime()", self.output)

    def test_publish_checks_file_type_size_and_key(self):
        for fragment in (
            "if (!attributes.isRegularFile)",
            "attributes.size() != expectedSize",
            "attributes.fileKey() != expectedFileKey",
            "attributes.creationTime() != expectedCreationTime",
        ):
            self.assertIn(fragment, self.publish)

    def test_publish_does_not_request_replacement(self):
        self.assertIn("Files.createLink(destination, part)", self.publish)
        self.assertIn("Files.move(part, destination)", self.publish)
        self.assertNotIn("StandardCopyOption.REPLACE_EXISTING", self.publish)

    def test_transport_uses_opened_identity_and_byte_count(self):
        self.assertIn("stagedFileKey = openedStage.fileKey", self.transfer)
        self.assertIn("stagedCreationTime = openedStage.creationTime", self.transfer)
        self.assertIn("publishFtpStagedFile(part, absolute, downloaded, stagedFileKey, stagedCreationTime)", self.transfer)

    def test_regression_covers_same_size_replacement(self):
        self.assertIn("sameSizeReplacementCannotBePublishedWithPortableIdentity", self.regression)
        self.assertIn("creationTimeFallbackRejectsSameSizeSwapWithoutFileKey", self.regression)
        self.assertIn("publishFtpStagedFile(stage, destination, 3, null, opened.creationTime)", self.regression)
        self.assertNotIn("assumeTrue(", self.regression)

    def test_resume_identity_is_bounded_and_never_follows_symlinks(self):
        self.assertIn("Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)", self.resume_identity)
        self.assertIn("Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)", self.resume_identity)
        self.assertIn("it.readNBytes(maxBytes + 1)", self.resume_identity)
        self.assertIn("if (bytes.size > maxBytes)", self.resume_identity)
        self.assertIn("before.fileKey() != after.fileKey()", self.resume_identity)

    def test_resume_transport_uses_guarded_reader(self):
        self.assertIn("val old = loadFtpResumeIdentity(meta)", self.transfer)
        self.assertNotIn("Files.newInputStream(path).use(properties::load)", self.transfer)



if __name__ == "__main__":
    unittest.main()
