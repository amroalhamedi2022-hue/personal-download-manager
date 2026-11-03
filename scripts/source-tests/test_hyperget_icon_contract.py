from pathlib import Path
import struct
import unittest

ROOT = Path(__file__).resolve().parents[2]
ICO = ROOT / "desktop/app/icons/icon.ico"
SVG = ROOT / "desktop/app/icons/hyperget-icon.svg"


class HyperGetIconContractTests(unittest.TestCase):
    def test_vector_source_is_a_local_glass_download_symbol(self):
        svg = SVG.read_text(encoding="utf-8")
        self.assertIn("<title>HyperGet", svg)
        self.assertIn('linearGradient id="glass"', svg)
        self.assertIn('linearGradient id="ring"', svg)
        self.assertIn('linearGradient id="arrow"', svg)
        self.assertIn('linearGradient id="tray"', svg)
        self.assertIn('M105 48H151V126H183L128 185 73 126H105Z', svg)
        self.assertNotIn('href="http', svg)
        self.assertNotIn('xlink:href="http', svg)

    def test_windows_ico_has_alpha_and_native_taskbar_sizes(self):
        data = ICO.read_bytes()
        reserved, kind, count = struct.unpack_from("<HHH", data, 0)
        self.assertEqual(0, reserved)
        self.assertEqual(1, kind)
        self.assertEqual(6, count)
        expected_sizes = [256, 128, 64, 48, 32, 16]
        observed_sizes = []
        ranges = []
        for i in range(count):
            pos = 6 + i * 16
            w0, h0, _colors, _reserved, planes, bpp, byte_count, offset = struct.unpack_from("<BBBBHHII", data, pos)
            width = 256 if w0 == 0 else w0
            height = 256 if h0 == 0 else h0
            observed_sizes.append(width)
            self.assertEqual(width, height)
            self.assertEqual(1, planes)
            self.assertEqual(32, bpp)
            self.assertGreaterEqual(offset, 6 + count * 16)
            self.assertLessEqual(offset + byte_count, len(data))
            header_size, bmp_width, bmp_height, bmp_planes, bmp_bpp, compression, image_size = struct.unpack_from("<IiiHHII", data, offset)
            self.assertEqual(40, header_size)
            self.assertEqual(width, bmp_width)
            self.assertEqual(height * 2, bmp_height)
            self.assertEqual(1, bmp_planes)
            self.assertEqual(32, bmp_bpp)
            self.assertEqual(0, compression)
            xor_size = width * height * 4
            mask_row = ((width + 31) // 32) * 4
            self.assertEqual(xor_size, image_size)
            self.assertEqual(40 + xor_size + mask_row * height, byte_count)
            pixels = offset + 40
            def alpha_at(x, y):
                return data[pixels + ((height - 1 - y) * width + x) * 4 + 3]
            for x, y in ((0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1)):
                self.assertEqual(0, alpha_at(x, y), f"Expected transparent corner at {width}px: {(x, y)}")
            self.assertGreater(alpha_at(width // 2, height // 2), 200)
            ranges.append((offset, offset + byte_count))
        self.assertEqual(expected_sizes, observed_sizes)
        self.assertEqual(sorted(ranges), ranges)
        self.assertEqual(6 + count * 16, ranges[0][0])
        self.assertEqual(len(data), ranges[-1][1])
        size, offset = 256, ranges[0][0]
        pixel_start = offset + 40
        sampled = set()
        for y in range(0, size, 8):
            for x in range(0, size, 8):
                idx = pixel_start + ((size - 1 - y) * size + x) * 4
                if data[idx + 3] > 200:
                    sampled.add(data[idx:idx + 3])
        self.assertGreaterEqual(len(sampled), 12)


if __name__ == "__main__":
    unittest.main(verbosity=2)
