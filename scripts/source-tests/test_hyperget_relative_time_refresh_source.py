"""Source guard for relative-date recomposition cadence in the desktop download table."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
TIME_UTIL = (ROOT / "shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/util/TimeUtil.kt").read_text(encoding="utf-8")
TABLE = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/home/sections/TableDownloadItem.kt").read_text(encoding="utf-8")
KOTLIN_TEST = (ROOT / "shared/app/src/commonTest/kotlin/com/abdownloadmanager/shared/util/TimeUtilRelativeRefreshTest.kt").read_text(encoding="utf-8")
KOTLIN_WORKFLOW = (ROOT / ".github/workflows/hyperget-relative-time-refresh-tests.yml").read_text(encoding="utf-8")


class RelativeTimeRefreshSourceContract(unittest.TestCase):
    def test_date_cell_uses_unit_boundary_refresh_and_skips_unchanged_state(self):
        date_cell = TABLE.split("fun DateCell(", 1)[1].split("\nfun SpeedCell(", 1)[0]
        self.assertIn("relativeTimeRefreshDelayMillis(ageMillis)", date_cell)
        self.assertIn("if (dateString != relativeTime)", date_cell)
        self.assertNotIn("delay(1.seconds)", date_cell)

    def test_refresh_schedule_has_second_minute_hour_and_day_boundaries(self):
        self.assertIn("age < 60_000L", TIME_UTIL)
        self.assertIn("age < 3_600_000L", TIME_UTIL)
        self.assertIn("age < 86_400_000L", TIME_UTIL)
        self.assertIn("coerceAtLeast(1L)", TIME_UTIL)

    def test_expensive_unit_compile_is_scoped_to_relevant_latest_commit(self):
        self.assertIn("fetch-depth: 2", KOTLIN_WORKFLOW)
        self.assertIn("git diff --name-only HEAD^ HEAD", KOTLIN_WORKFLOW)
        self.assertIn("needs: change-scope", KOTLIN_WORKFLOW)
        self.assertIn("if: needs.change-scope.outputs.run == 'true'", KOTLIN_WORKFLOW)
        self.assertIn('case "$file" in', KOTLIN_WORKFLOW)
        self.assertIn("esac", KOTLIN_WORKFLOW)

    def test_kotlin_unit_tests_cover_all_display_units(self):
        for marker in ("secondLabelsWake", "minuteLabelsWake", "hourLabelsWake", "dayAndOlderLabels"):
            self.assertIn(marker, KOTLIN_TEST)


if __name__ == "__main__":
    unittest.main()
