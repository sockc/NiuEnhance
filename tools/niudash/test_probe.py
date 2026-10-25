"""Synthetic-data checks: no vehicle or personal logs required."""
import io
import json
import struct
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile
from probe import resource_inventory, snoop_summary, hci_file


class NiuDashProbeTests(unittest.TestCase):
    def test_apk_inventory_does_not_extract(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "sample.apk"
            with ZipFile(p, "w") as z:
                z.writestr("assets/theme/cover.webp", b"not-real-image")
                z.writestr("res/layout/main.xml", b"<fake/>")
            summary = resource_inventory(p)
            self.assertEqual(summary["entry_groups"]["assets"], 1)
            self.assertEqual(summary["asset_extensions"][".webp"], 1)
            self.assertFalse((Path(d) / "assets").exists())

    def test_hci_metadata_counts_only(self):
        magic = b"btsnoop\x00" + struct.pack(">II", 1, 1002)
        stamp = 0x00dcddb30f2f8000 + 1_700_000_000_000_000
        payload1 = b"\x02\x08\x04\x00" + b"SECRET"
        payload2 = b"\x04\x0e\x01"
        raw = magic + struct.pack(">IIIIQ", len(payload1), len(payload1), 0, 0, stamp) + payload1
        raw += struct.pack(">IIIIQ", len(payload2), len(payload2), 1, 0, stamp + 6_000_000) + payload2
        result = snoop_summary(io.BytesIO(raw), "synthetic")
        encoded = json.dumps(result)
        self.assertEqual(result["packet_count"], 2)
        self.assertEqual(len(result["five_second_windows"]), 2)
        self.assertNotIn("SECRET", encoded)
        self.assertEqual(result["h4_packet_type_guesses"]["acl"], 1)

    def test_zip_hci(self):
        header = b"btsnoop\x00" + struct.pack(">II", 1, 1002)
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "bugreport.zip"
            with ZipFile(path, "w") as z:
                z.writestr("FS/data/misc/bluetooth/logs/btsnoop_hci.log", header)
            summary = hci_file(path)
            self.assertEqual(summary["packet_count"], 0)


if __name__ == "__main__":
    unittest.main()
