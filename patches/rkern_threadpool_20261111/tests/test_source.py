from pathlib import Path
import argparse,subprocess,tempfile,zipfile
R=Path(__file__).resolve().parents[1]
a=argparse.ArgumentParser();a.add_argument('--src',type=Path);args=a.parse_args()
with tempfile.TemporaryDirectory(prefix='aden-source-parse-') as tmp:
 p=Path(tmp);src=args.src
 if src is None:
  with zipfile.ZipFile(R.parents[1]/'downloads/RKern_Aden_IOCP_THREADPOOL_FIXED_FULL_SRC_20261111.zip') as z:z.extractall(p/'source')
  src=p/'source/src'
 with (R/'logs/source-parse.log').open('w') as out:
  subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(p),str(R/'tests/ParseSources.java')],check=True,stdout=out,stderr=subprocess.STDOUT,timeout=30)
  subprocess.run(['java','-cp',str(p),'ParseSources',str(src)],check=True,stdout=out,stderr=subprocess.STDOUT,timeout=60)
print((R/'logs/source-parse.log').read_text())
