#!/usr/bin/env python3
"""NiuDash offline proof-of-capability analyzer (Python 3.10+, stdlib only).

No network, firmware writes, APK decompilation, payload output, or secrets.
Never publish raw APKs, bugreports or Bluetooth HCI snoop data.
"""
from __future__ import annotations
import argparse
import collections
from datetime import datetime, timezone
import io
import json
from pathlib import Path
import struct
import sys
import zipfile

BTSNOOP_MAGIC = b"btsnoop\x00"
MAX_PACKET = 2 * 1024 * 1024
RESOURCE_EXTS = {".png", ".webp", ".jpg", ".jpeg", ".gif", ".svg",
                 ".xml", ".json", ".bin", ".dat", ".pak", ".zip",
                 ".mp4", ".webm", ".ttf", ".otf", ".riv", ".svga",
                 ".skel", ".atlas", ".lottie"}


def resource_inventory(path: Path) -> dict:
    if not zipfile.is_zipfile(path):
        raise ValueError("Not an APK/ZIP file")
    groups = collections.Counter()
    extensions = collections.Counter()
    examples = []
    with zipfile.ZipFile(path) as z:
        for entry in z.infolist():
            if entry.is_dir():
                continue
            name = entry.filename.replace("\\", "/")
            root = name.split("/")[0]
            if root not in ("assets", "res", "lib", "META-INF"):
                root = "other"
            groups[root] += 1
            suffix = Path(name).suffix.lower() or "(none)"
            if root in ("assets", "res"):
                extensions[suffix] += 1
                if len(examples) < 40 and suffix in RESOURCE_EXTS:
                    # Names are only for local inspection; no extraction occurs.
                    examples.append({"name": name, "size": entry.file_size})
    return {
        "type": "android_app_package_inventory_not_instrument_firmware",
        "file": path.name, "entry_groups": dict(groups),
        "asset_extensions": dict(extensions.most_common(30)),
        "resource_name_samples": examples,
        "warning": "APK resources are PHONE APP resources; no claim that these are MTQ02V06 firmware assets."
    }


def snoop_summary(f, source: str) -> dict:
    header = f.read(16)
    if len(header) != 16 or not header.startswith(BTSNOOP_MAGIC):
        raise ValueError("Not standard binary btsnoop. Some bugreports contain btsnooz text; use AOSP btsnooz.py first.")
    version, datalink = struct.unpack(">II", header[8:16])
    buckets = collections.defaultdict(lambda: [0, 0])
    flags = collections.Counter()
    packet_kinds = collections.Counter()
    packets = 0
    wire_bytes = 0
    first_stamp = None
    last_stamp = None
    truncated = False
    while True:
        raw = f.read(24)
        if not raw:
            break
        if len(raw) < 24:
            truncated = True
            break
        original_len, included_len, flag, dropped, stamp = struct.unpack(">IIIIQ", raw)
        if included_len > MAX_PACKET or included_len > original_len:
            raise ValueError("Invalid or unexpectedly large btsnoop frame length")
        data = f.read(included_len)
        if len(data) < included_len:
            truncated = True
            break
        if first_stamp is None:
            first_stamp = stamp
        last_stamp = stamp
        packets += 1
        wire_bytes += original_len
        flags[str(flag & 1)] += 1  # Direction bit, do not claim remote identity.
        kind = data[0] if data else 0
        # Only classify H4 packet type if recognizable.
        kind_name = {1: "cmd", 2: "acl", 3: "sco", 4: "event", 5: "iso"}.get(kind, "unknown")
        packet_kinds[kind_name] += 1
        seconds = max(0, (stamp - first_stamp) // 1_000_000)
        bucket = int(seconds // 5)
        buckets[bucket][0] += 1
        buckets[bucket][1] += original_len
    # Android btsnoop uses microseconds since 0000-01-01. Convert to UTC
    # only when the derived timestamp is plausible, as vendor logs may vary.
    def utc_time(tick):
        if tick is None:
            return None
        unix_micros = tick - 0x00dcddb30f2f8000
        if not 1_262_304_000_000_000 < unix_micros < 4_102_444_800_000_000:
            return None
        return datetime.fromtimestamp(unix_micros / 1_000_000, tz=timezone.utc).isoformat()

    return {
        "type": "bluetooth_hci_traffic_metadata_only",
        "source": source, "version": version, "datalink": datalink,
        "start_utc": utc_time(first_stamp), "end_utc": utc_time(last_stamp),
        "packet_count": packets, "total_wire_bytes": wire_bytes,
        "direction_flag_counts": dict(flags),
        "h4_packet_type_guesses": dict(packet_kinds),
        "duration_seconds": round((last_stamp - first_stamp) / 1_000_000, 1) if first_stamp is not None else 0,
        "five_second_windows": [
            {"elapsed_s": k * 5, "packets": v[0], "bytes": v[1]}
            for k, v in sorted(buckets.items())
        ][:400],
        "truncated": truncated,
        "warning": "Counts are for ALL phone Bluetooth traffic, not proven NIU traffic. Do not treat byte bursts as proof of image/video transfer."
    }


def hci_file(path: Path) -> dict:
    if zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as z:
            matches = [
                i for i in z.infolist()
                if not i.is_dir() and
                (i.filename.lower().endswith("btsnoop_hci.log") or
                 i.filename.lower().endswith("btsnoop.log"))
            ]
            if not matches:
                raise ValueError("No binary HCI snoop log in ZIP; follow the btsnooz instructions in docs/niudash.")
            matches.sort(key=lambda x: x.file_size, reverse=True)
            with z.open(matches[0]) as f:
                result = snoop_summary(f, path.name + " / " + matches[0].filename)
            result["candidate_log_files"] = len(matches)
            return result
    with path.open("rb") as f:
        return snoop_summary(f, path.name)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="NiuDash local APK resource inventory / HCI metadata summary. No network."
    )
    parser.add_argument("mode", choices=["apk", "hci"])
    parser.add_argument("files", nargs="+", type=Path)
    parser.add_argument("--out", type=Path, help="Save local JSON report")
    args = parser.parse_args()
    output = {"project": "NiuDash", "schema": 1,
              "reports": [], "privacy": "Do not upload raw APKs, bugreports, snoop logs or account identifiers."}
    for path in args.files:
        if not path.is_file():
            print(f"Missing input: {path}", file=sys.stderr)
            return 2
        try:
            output["reports"].append(
                resource_inventory(path) if args.mode == "apk" else hci_file(path)
            )
        except (ValueError, OSError, zipfile.BadZipFile) as e:
            print(f"Cannot analyze {path}: {e}", file=sys.stderr)
            return 2
    report = json.dumps(output, ensure_ascii=False, indent=2)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(report, encoding="utf-8")
        print(f"Saved LOCAL report to: {args.out}")
    else:
        print(report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
