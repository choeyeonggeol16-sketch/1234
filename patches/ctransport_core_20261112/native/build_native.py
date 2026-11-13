"""Shipping Windows builds require the MSVC x64 object ABI.
Linux sanitizer fixtures are built by tests/run_native_tests.py.
"""
from pathlib import Path
import runpy,sys
if sys.platform!='win32':raise SystemExit('Build the shipping DLL in an MSVC x64 Developer shell on Windows: python native/build_native.py. Linux: python tests/run_native_tests.py.')
if '--write-java-hash' in sys.argv:sys.argv.remove('--write-java-hash')
runpy.run_path(str(Path(__file__).with_name('build_native_msvc.py')),run_name='__main__')
