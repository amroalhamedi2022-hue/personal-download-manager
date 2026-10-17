# Approved file download window

Status: approved by the user on 2026-10-17.

Visual reference: `file-download-window-approved-2026-10-17.webp`

SHA-256: `f4e8fb922db21564fa1051d9bea9efa0b753abca44c4e3b49543ee9debd171a7`

This reference locks the intended Windows file-download window for the first
release. Implement it as closely as the framework and accessibility constraints
allow, while keeping the controls functional at different window sizes and DPI
scales.

Required details:

- Keep the familiar information hierarchy shown in the reference: tabs, file
  details, transfer-rate chart, overall progress, actions, connections/parts,
  and part table.
- Use the theme palette for both liquid progress tracks. The wave and bubbles
  must remain readable in every approved light and dark theme.
- Show the percentage in the window title. Do not show a second percentage at
  the right end of the main progress track.
- Keep transferred size, total size, remaining time, and current transfer rate
  below the main track.
- Keep Pause and Cancel centered beneath the main progress information.
- Preserve semantic colors for paused, retrying, failed, and completed states.
- The reference is an original project mockup. Do not add IDM branding or
  copyrighted IDM artwork.

The image is the visual target, not proof that the current code has been built
or tested on Windows. Record those checks separately in the execution plan.
