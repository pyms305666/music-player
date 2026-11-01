"""全量附录回归：使用独立临时目录，不扫描或改动个人项目数据。"""

import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location(
    "project_inventory", Path(__file__).with_name("generate-project-inventory.py")
)
inventory = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = inventory
spec.loader.exec_module(inventory)


class InventoryTest(unittest.TestCase):
    """核对真实路径、索引失败分支和 HTML 数据边界，避免仅重复实现的测试。"""

    def setUp(self):
        """创建中文路径、隐藏文件和空目录，模拟真实工作区边界。"""
        self.temporary = tempfile.TemporaryDirectory(prefix="项目清单-")
        self.root = Path(self.temporary.name)
        (self.root / ".隐藏").mkdir()
        (self.root / ".隐藏/.配置").write_text("仅测试", encoding="utf-8")
        (self.root / "空目录").mkdir()
        (self.root / "中文 & 文件.txt").write_text("测试", encoding="utf-8")

    def tearDown(self):
        """清理本用例临时目录，链接目标由各自的临时目录管理。"""
        self.temporary.cleanup()

    def test_hidden_empty_unicode_and_independent_coverage(self):
        """隐藏文件与空目录不得遗漏，两个扫描器必须得到相同完整集合。"""
        expected = {".", ".隐藏", ".隐藏/.配置", "空目录", "中文 & 文件.txt"}
        paths = inventory.scan_paths(self.root)
        self.assertEqual(expected, set(paths))
        self.assertEqual(expected, inventory.independently_scan(self.root))
        self.assertEqual("目录", paths["空目录"][0])

    def test_external_directory_link_records_target_without_recursing(self):
        """外部链接必须列出自身和指向，不能把项目外部文件加入清单。"""
        with tempfile.TemporaryDirectory(prefix="清单外部-") as external:
            target = Path(external)
            (target / "不应扫描.txt").write_text("外部", encoding="utf-8")
            link = self.root / "目录链接"
            try:
                os.symlink(target, link, target_is_directory=True)
            except OSError:
                if os.name != "nt":
                    raise
                # Windows junction 不需要管理员创建符号链接的权限。
                import subprocess

                result = subprocess.run(
                    ["cmd", "/c", "mklink", "/J", str(link), str(target)], capture_output=True
                )
                if result.returncode:
                    self.skipTest("本机既不能创建符号链接，也不能创建 junction")
            try:
                paths = inventory.scan_paths(self.root)
                self.assertEqual("目录链接", paths["目录链接"][0])
                self.assertTrue(paths["目录链接"][1])
                self.assertNotIn("目录链接/不应扫描.txt", paths)
                self.assertEqual(set(paths), inventory.independently_scan(self.root))
            finally:
                if link.is_symlink():
                    link.unlink()
                else:
                    link.rmdir()  # 只删除已知测试 junction 本身，不递归删除目标。

    def test_html_payload_cannot_end_script_or_inject_markup(self):
        """恶意用途文本仍只作为 JSON 和文字显示，中文及特殊字符可准确往返。"""
        text = "</script><img src=x onerror=alert(1)> & 中文\u2028"
        role = inventory.Role("文件", "测试", text, "生成来源", "人工测试")
        entries = inventory.entries_for(
            {"中文 & 文件.txt": ("文件", "")}, {"中文 & 文件.txt": role}
        )
        html = inventory.html_document(entries, self.root, "测试时间")
        data = re.search(
            r'<script id="inventory-data" type="application/json">(.*?)</script>', html, re.S
        ).group(1)
        self.assertNotIn("<", data)
        self.assertNotIn("&", data)
        self.assertEqual(text, json.loads(data)["entries"][0]["purpose"])
        self.assertIn("textContent=value", html)

    def test_missing_stale_and_duplicate_roles_are_rejected(self):
        """遗漏与不存在路径要明确失败，不能靠工具推断掩盖自写文件缺少说明。"""
        paths = inventory.scan_paths(self.root)
        with self.assertRaisesRegex(ValueError, "遗漏"):
            inventory.check_roles(paths, {}, {"中文 & 文件.txt"})
        role = inventory.Role("文件", "测试", "用途", "关联", "依据")
        with self.assertRaisesRegex(ValueError, "已不存在"):
            inventory.check_roles(paths, {"消失.txt": role}, set())
        guide = self.root / "索引.md"
        line = "| `a` | 文件 | 测试 | 用途 | 关联 | 依据 |\n"
        guide.write_text(inventory.START + "\n" + line + line + inventory.END, encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "重复"):
            inventory.read_roles(guide)

    def test_self_entry_and_post_generation_omission_detection(self):
        """首次生成包含输出自身，之后新增文件必须使 --check 覆盖核对失败。"""
        guide = self.root / "索引.md"
        guide.write_text(
            inventory.START + "\n| `.` | 目录 | 测试 | 根目录 | 子项 | 人工 |\n" + inventory.END,
            encoding="utf-8",
        )
        output = self.root / "附录.html"
        count = inventory.generate(self.root, guide, output)
        self.assertEqual(7, count)
        self.assertEqual(count, inventory.verify_inventory(output, self.root))
        (self.root / "新增.txt").touch()
        with self.assertRaisesRegex(ValueError, "遗漏"):
            inventory.verify_inventory(output, self.root)

    def test_new_authored_source_requires_its_parent_directory_roles(self):
        """新增自写源码及各级目录自动进入必填集合，第三方工具源码不误报。"""
        source = self.root / "src/main/java/新组件.java"
        source.parent.mkdir(parents=True)
        source.write_text("class 新组件 {}", encoding="utf-8")
        vendor = self.root / ".tools/vendor/runtime.py"
        vendor.parent.mkdir(parents=True)
        vendor.write_text("# third party", encoding="utf-8")
        paths = inventory.scan_paths(self.root)
        required = inventory.authored_paths(self.root, paths)
        self.assertTrue(
            {".", "src", "src/main", "src/main/java", "src/main/java/新组件.java"} <= required
        )
        self.assertNotIn(".tools/vendor/runtime.py", required)
        with self.assertRaisesRegex(ValueError, "新组件"):
            inventory.check_roles(paths, {}, required)


if __name__ == "__main__":
    unittest.main(verbosity=2)
