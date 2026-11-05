#!/usr/bin/env python3
"""Fail-closed, ID-based task selection and change validation for HyperGet CI.

All tasks and path permissions are fixed in reviewed source code. The LLM
cannot add to its own allowlist or decide what task to implement.
"""
from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]

TASKS = (
    {
        "id": "f01-08-content-range",
        "prompt": "docs/autodev/tasks/F01-08-content-range.md",
        "marker": "fun contentRangeRejectsReversedBounds()",
        "marker_path": "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/connection/response/headers/RangeHeaderExtractorTest.kt",
        "paths": (
            "downloader/core/src/commonMain/kotlin/ir/amirab/downloader/connection/response/headers/RangeHeaderExtractor.kt",
            "downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/connection/response/headers/RangeHeaderExtractorTest.kt",
        ),
    },
)

# Windows .bat line endings can be normalized by Linux checkouts; never stage
# these files and reject actual edits to their contents.
EOL_ONLY_WRAPPERS = {
    "gradlew.bat",
    "shared/updater/src/desktopMain/resources/com/abdownloadmanager/updater/updater_windows.bat",
}


def run(*cmd: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        cmd, cwd=ROOT, check=check, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )


def verify() -> None:
    assert (ROOT / ".github/workflows/private-windows-validation.yml").exists()
    ids = [task["id"] for task in TASKS]
    assert len(ids) == len(set(ids)), "Duplicate task ID"
    for task in TASKS:
        assert (ROOT / task["prompt"]).is_file(), task["prompt"]
        assert len(task["paths"]) == 2, "Exactly production + test paths required"
        assert (ROOT / task["paths"][0]).is_file(), "Production source missing"
        assert task["marker_path"] == task["paths"][1]
        prompt = (ROOT / task["prompt"]).read_text(encoding="utf-8")
        assert task["marker"] in prompt, "Prompt must name acceptance test"
        assert "GEMINI_API_KEY" not in prompt, "Secret names must not be in model prompt"
    print("PASS: verified finite approved backlog, exact paths and Windows validation")


def completed(task: dict) -> bool:
    marker_file = ROOT / task["marker_path"]
    return marker_file.is_file() and task["marker"] in marker_file.read_text(encoding="utf-8")


def set_output(task: dict | None) -> None:
    output = Path(os.environ["GITHUB_OUTPUT"])
    with output.open("a", encoding="utf-8") as handle:
        if task is None:
            handle.write("task=\n")
        else:
            handle.write(f"task={task['id']}\n")
            handle.write(f"prompt={task['prompt']}\n")
            handle.write(f"paths={' '.join(task['paths'])}\n")


def select() -> None:
    verify()
    for task in TASKS:
        if completed(task):
            print(f"SKIP: {task['id']} already included on this checkout")
            continue
        branch = "autodev/real-" + task["id"]
        # A branch is the durable lease even when GitHub Actions cannot create
        # a PR. Require human reconciliation, never pile on competing changes.
        probe = run("gh", "api",
                    f"repos/{os.environ['GITHUB_REPOSITORY']}/git/ref/heads/{branch}",
                    check=False)
        if probe.returncode == 0:
            print(f"BLOCKED: {branch} exists, must be reviewed/merged/removed")
            set_output(None)
            return
        if "404" not in probe.stderr and "Not Found" not in probe.stderr:
            raise RuntimeError("Cannot prove candidate branch absent; refusing AI call: " + probe.stderr[-500:])
        set_output(task)
        print(f"SELECTED: {task['id']} with approved paths {task['paths']}")
        return
    print("DONE: no unimplemented approved tasks; no AI call needed")
    set_output(None)


def validate(task_id: str) -> None:
    task = next((t for t in TASKS if t["id"] == task_id), None)
    if task is None:
        raise RuntimeError(f"Unapproved task ID {task_id}")
    changed = set(run("git", "ls-files", "--modified", "--others", "--exclude-standard").stdout.splitlines())
    allowed = set(task["paths"])
    extraneous = changed - allowed
    if not extraneous.issubset(EOL_ONLY_WRAPPERS):
        raise RuntimeError(f"Non-allowlisted changes: {sorted(extraneous)}")
    if extraneous:
        diff = run("git", "diff", "--ignore-space-at-eol", "--exit-code", "--", *sorted(extraneous), check=False)
        if diff.returncode != 0:
            raise RuntimeError(f"Non-EOL edits to Windows wrappers: {sorted(extraneous)}")
    if not allowed.issubset(changed):
        raise RuntimeError(f"Missing code or tests: {sorted(allowed-changed)}")
    if not completed(task):
        raise RuntimeError(f"Mandatory regression test marker missing: {task['marker']}")
    diff = run("git", "diff", "--check", "--", *task["paths"], check=False)
    if diff.returncode:
        raise RuntimeError("Whitespace problems in allowed production diff: " + diff.stdout + diff.stderr)
    test_lines = (ROOT / task["marker_path"]).read_text(encoding="utf-8").splitlines()
    if any(line.rstrip(" \t") != line for line in test_lines):
        raise RuntimeError("Trailing spaces in generated test")
    print(f"PASS: {task_id}, one production file + one test only; Windows wrappers EOL-only")


if __name__ == "__main__":
    try:
        command = sys.argv[1]
        if command == "verify":
            verify()
        elif command == "select":
            select()
        elif command == "validate" and len(sys.argv) == 3:
            validate(sys.argv[2])
        else:
            raise ValueError("Usage: task_runner.py verify | select | validate TASK_ID")
    except (AssertionError, RuntimeError, ValueError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
