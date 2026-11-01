"签名脚本导入适配。加载文件名含连字符的 android-signing.py，供其他 Python 工具复用其中定义。\n\nLoad the CLI module without importing its __main__ entry point."

import importlib.util
from pathlib import Path
import sys

spec = importlib.util.spec_from_file_location(
    "za_android_signing", Path(__file__).with_name("android-signing.py")
)
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)
