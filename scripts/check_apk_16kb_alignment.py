"""Verify that every .so packaged in an APK has 16 KB-aligned PT_LOAD segments.

Mirrors the official Android `check_elf_alignment.sh` check (p_align >= 0x4000 for
PT_LOAD program headers in ELF64 binaries). Reports per-file status on stdout
and exits non-zero when any library would block install on a 16 KB device.
"""

from __future__ import annotations

import struct
import sys
import zipfile
from pathlib import Path

PT_LOAD = 1
REQUIRED_ALIGN = 0x4000


def check_elf64(data: bytes) -> tuple[bool, list[int]]:
    if len(data) < 64 or data[:4] != b"\x7fELF" or data[4] != 2:
        return True, []  # not ELF64 - ignore (e.g. armeabi-v7a 32-bit handled below)
    e_phoff = struct.unpack_from("<Q", data, 0x20)[0]
    e_phentsize = struct.unpack_from("<H", data, 0x36)[0]
    e_phnum = struct.unpack_from("<H", data, 0x38)[0]
    aligns: list[int] = []
    ok = True
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type = struct.unpack_from("<I", data, off)[0]
        if p_type != PT_LOAD:
            continue
        p_align = struct.unpack_from("<Q", data, off + 0x30)[0]
        aligns.append(p_align)
        if p_align < REQUIRED_ALIGN:
            ok = False
    return ok, aligns


def check_elf32(data: bytes) -> tuple[bool, list[int]]:
    if len(data) < 52 or data[:4] != b"\x7fELF" or data[4] != 1:
        return True, []
    e_phoff = struct.unpack_from("<I", data, 0x1C)[0]
    e_phentsize = struct.unpack_from("<H", data, 0x2A)[0]
    e_phnum = struct.unpack_from("<H", data, 0x2C)[0]
    aligns: list[int] = []
    ok = True
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type = struct.unpack_from("<I", data, off)[0]
        if p_type != PT_LOAD:
            continue
        p_align = struct.unpack_from("<I", data, off + 0x1C)[0]
        aligns.append(p_align)
        if p_align < REQUIRED_ALIGN:
            ok = False
    return ok, aligns


def main(apk_path: str) -> int:
    apk = Path(apk_path)
    if not apk.exists():
        print(f"APK not found: {apk}", file=sys.stderr)
        return 2

    failures: list[str] = []
    print(f"Scanning {apk.name} for native libraries...")
    print(f"Required PT_LOAD alignment: 0x{REQUIRED_ALIGN:x} ({REQUIRED_ALIGN // 1024} KB)")
    print()

    with zipfile.ZipFile(apk) as zf:
        sos = sorted(n for n in zf.namelist() if n.startswith("lib/") and n.endswith(".so"))
        if not sos:
            print("No .so entries found.")
            return 0
        for name in sos:
            data = zf.read(name)
            arch = name.split("/")[1] if "/" in name else "?"
            is_64 = arch in {"arm64-v8a", "x86_64", "riscv64"}
            ok, aligns = (check_elf64(data) if is_64 else check_elf32(data))
            tag = "OK " if ok else "FAIL"
            shown = ", ".join(f"0x{a:x}" for a in aligns) or "no PT_LOAD"
            print(f"  [{tag}] {name}  -> p_align: {shown}")
            if not ok:
                failures.append(name)

    print()
    if failures:
        print(f"FAIL: {len(failures)} library(ies) below {REQUIRED_ALIGN // 1024} KB alignment:")
        for n in failures:
            print(f"  - {n}")
        return 1
    print("OK: all native libraries are >= 16 KB aligned.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/debug/app-debug.apk"))
