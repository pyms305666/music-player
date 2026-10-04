"""Load the CLI module without importing its __main__ entry point."""
import importlib.util
from pathlib import Path
import sys
spec = importlib.util.spec_from_file_location("za_android_signing", Path(__file__).with_name("android-signing.py"))
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)
