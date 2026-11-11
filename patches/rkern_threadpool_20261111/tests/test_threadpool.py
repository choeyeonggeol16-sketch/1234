from pathlib import Path
import argparse, tempfile, subprocess, zipfile
R=Path(__file__).resolve().parents[1]
a=argparse.ArgumentParser();a.add_argument('--src',type=Path);args=a.parse_args()
with tempfile.TemporaryDirectory(prefix='aden-pool-regression-') as tmp:
 p=Path(tmp);src=args.src
 if src is None:
  bundle=R.parents[1]/'downloads/RKern_Aden_IOCP_THREADPOOL_FIXED_FULL_SRC_20261111.zip'
  with zipfile.ZipFile(bundle) as z:z.extractall(p/'source')
  src=p/'source/src'
 files=[src/f for f in ['l1j/server/server/ThreadPool.java','l1j/server/server/utils/ThreadConfig.java','l1j/server/server/utils/ConfigReader.java','l1j/server/server/utils/TimeUtil.java','l1j/server/server/model/network/utill/PacketThreadFactory.java']]
 files.extend([R/'tests/ThreadPoolRegression.java',R/'tests/JvmExitRegression.java']);(p/'build').mkdir();(p/'config').mkdir()
 (p/'config/Threads.ini').write_text('ScheduledThreadPoolSize=1\nInstantThreadPoolSize=1\n')
 (R/'logs').mkdir(exist_ok=True)
 with (R/'logs/threadpool-test.log').open('w') as out:
  subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-source','21','-target','21','-encoding','UTF-8','-d',str(p/'build'),*[str(f) for f in files]],stdout=out,stderr=subprocess.STDOUT,check=True,timeout=30)
  subprocess.run(['java','-cp',str(p/'build'),'ThreadPoolRegression'],cwd=p,stdout=out,stderr=subprocess.STDOUT,check=True,timeout=30)
  subprocess.run(['java','-cp',str(p/'build'),'JvmExitRegression'],cwd=p,stdout=out,stderr=subprocess.STDOUT,check=True,timeout=10)
print((R/'logs/threadpool-test.log').read_text())
