#!/usr/bin/env python3
"""Audit the real Windows PE icon groups for HyperGet's GUI and installer.

Uses only the Python standard library and Win32 resource APIs. It deliberately
checks RT_GROUP_ICON entries in the built PE files, not just the source .ico.
"""
from __future__ import annotations

import argparse
import ctypes
import hashlib
import os
import struct
import sys
from pathlib import Path
from ctypes import wintypes

EXPECTED_SIZES = (16, 32, 48, 64, 128, 256)
RT_GROUP_ICON = 14
LOAD_LIBRARY_AS_DATAFILE = 0x00000002
LOAD_LIBRARY_AS_IMAGE_RESOURCE = 0x00000020
ERROR_RESOURCE_TYPE_NOT_FOUND = 1813


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def ico_entries(path: Path) -> dict[int, set[int]]:
    data = path.read_bytes()
    if len(data) < 6:
        raise ValueError(f"ICO is truncated: {path}")
    reserved, kind, count = struct.unpack_from("<HHH", data, 0)
    if reserved != 0 or kind != 1 or count < 1:
        raise ValueError(f"Invalid ICO header: {path}")
    if len(data) < 6 + count * 16:
        raise ValueError(f"ICO directory is truncated: {path}")
    result: dict[int, set[int]] = {}
    for index in range(count):
        offset = 6 + index * 16
        width_byte, height_byte, _colors, _reserved, planes, bpp, size, image_offset = (
            struct.unpack_from("<BBBBHHII", data, offset)
        )
        width = 256 if width_byte == 0 else width_byte
        height = 256 if height_byte == 0 else height_byte
        if width != height or planes != 1 or bpp != 32:
            raise ValueError(
                f"ICO entry {index} must be square, one-plane, 32-bit: "
                f"{width}x{height}, planes={planes}, bpp={bpp}"
            )
        if image_offset < 6 + count * 16 or image_offset + size > len(data):
            raise ValueError(f"ICO entry {index} points outside the file")
        result.setdefault(width, set()).add(bpp)
    return result


def pe_group_icon_entries(path: Path) -> dict[int, set[int]]:
    if os.name != "nt":
        raise RuntimeError("PE icon-resource audit requires Windows")
    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    enum_callback_type = ctypes.WINFUNCTYPE(
        wintypes.BOOL, wintypes.HMODULE, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p
    )
    kernel32.LoadLibraryExW.argtypes = [wintypes.LPCWSTR, wintypes.HANDLE, wintypes.DWORD]
    kernel32.LoadLibraryExW.restype = wintypes.HMODULE
    kernel32.FreeLibrary.argtypes = [wintypes.HMODULE]
    kernel32.FreeLibrary.restype = wintypes.BOOL
    kernel32.EnumResourceNamesW.argtypes = [
        wintypes.HMODULE, ctypes.c_void_p, enum_callback_type, ctypes.c_void_p
    ]
    kernel32.EnumResourceNamesW.restype = wintypes.BOOL
    kernel32.FindResourceW.argtypes = [wintypes.HMODULE, ctypes.c_void_p, ctypes.c_void_p]
    kernel32.FindResourceW.restype = ctypes.c_void_p
    kernel32.LoadResource.argtypes = [wintypes.HMODULE, ctypes.c_void_p]
    kernel32.LoadResource.restype = ctypes.c_void_p
    kernel32.SizeofResource.argtypes = [wintypes.HMODULE, ctypes.c_void_p]
    kernel32.SizeofResource.restype = wintypes.DWORD
    kernel32.LockResource.argtypes = [ctypes.c_void_p]
    kernel32.LockResource.restype = ctypes.c_void_p

    module = kernel32.LoadLibraryExW(
        str(path), None, LOAD_LIBRARY_AS_DATAFILE | LOAD_LIBRARY_AS_IMAGE_RESOURCE
    )
    if not module:
        raise ctypes.WinError(ctypes.get_last_error(), f"Cannot map PE resources: {path}")

    names: list[int] = []

    @enum_callback_type
    def collect_name(_module, _resource_type, resource_name, _parameter):
        value = resource_name if isinstance(resource_name, int) else ctypes.cast(
            resource_name, ctypes.c_void_p
        ).value
        if value is not None:
            names.append(int(value))
        return True

    result: dict[int, set[int]] = {}
    try:
        ok = kernel32.EnumResourceNamesW(
            module, ctypes.c_void_p(RT_GROUP_ICON), collect_name, None
        )
        if not ok:
            error = ctypes.get_last_error()
            if error == ERROR_RESOURCE_TYPE_NOT_FOUND:
                return {}
            raise ctypes.WinError(error, f"Cannot enumerate RT_GROUP_ICON in {path}")

        for name_value in names:
            resource = kernel32.FindResourceW(
                module, ctypes.c_void_p(name_value), ctypes.c_void_p(RT_GROUP_ICON)
            )
            if not resource:
                raise ctypes.WinError(ctypes.get_last_error(), f"Cannot find icon group in {path}")
            size = kernel32.SizeofResource(module, resource)
            loaded = kernel32.LoadResource(module, resource)
            pointer = kernel32.LockResource(loaded) if loaded else None
            if not pointer or size < 6:
                raise ValueError(f"Empty or truncated RT_GROUP_ICON resource in {path}")
            data = ctypes.string_at(pointer, size)
            reserved, kind, count = struct.unpack_from("<HHH", data, 0)
            if reserved != 0 or kind != 1 or count < 1 or len(data) < 6 + count * 14:
                raise ValueError(f"Malformed RT_GROUP_ICON directory in {path}")
            for index in range(count):
                entry_offset = 6 + index * 14
                width_byte, height_byte, _colors, _reserved, planes, bpp, _bytes, _icon_id = (
                    struct.unpack_from("<BBBBHHIH", data, entry_offset)
                )
                width = 256 if width_byte == 0 else width_byte
                height = 256 if height_byte == 0 else height_byte
                if width != height or planes != 1:
                    raise ValueError(
                        f"Invalid PE icon entry in {path}: {width}x{height}, planes={planes}"
                    )
                result.setdefault(width, set()).add(bpp)
    finally:
        kernel32.FreeLibrary(module)
    return result


def verify_sizes(label: str, entries: dict[int, set[int]]) -> None:
    missing = sorted(set(EXPECTED_SIZES) - set(entries))
    bad_bpp = {
        size: sorted(entries.get(size, set()))
        for size in EXPECTED_SIZES
        if 32 not in entries.get(size, set())
    }
    summary = ", ".join(f"{size}:{sorted(bpp)}" for size, bpp in sorted(entries.items()))
    print(f"{label}: sizes={sorted(entries)} bpp={{{summary}}}")
    if missing or bad_bpp:
        raise AssertionError(
            f"{label} failed native-size icon acceptance: missing={missing}, non-32-bit={bad_bpp}"
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-icon", required=True, type=Path)
    parser.add_argument("--application-exe", required=True, type=Path)
    parser.add_argument("--installer-exe", required=True, type=Path)
    args = parser.parse_args()
    if os.name != "nt":
        raise SystemExit("FAIL: Windows PE resource audit must run on Windows.")
    for label, path in (
        ("source ICO", args.source_icon),
        ("application GUI", args.application_exe),
        ("installer", args.installer_exe),
    ):
        if not path.is_file():
            raise SystemExit(f"FAIL: required icon audit input is missing: {path}")
        print(f"{label} SHA256={sha256_file(path)} path={path}")
    verify_sizes("source ICO", ico_entries(args.source_icon))
    verify_sizes("application GUI RT_GROUP_ICON", pe_group_icon_entries(args.application_exe))
    verify_sizes("installer RT_GROUP_ICON", pe_group_icon_entries(args.installer_exe))
    print("HYPERGET_NATIVE_ICON_RESOURCE_AUDIT=PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"HYPERGET_NATIVE_ICON_RESOURCE_AUDIT=FAIL: {exc}", file=sys.stderr)
        raise
