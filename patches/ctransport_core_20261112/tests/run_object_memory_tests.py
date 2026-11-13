from pathlib import Path
import argparse,os,subprocess
root=Path(__file__).resolve().parents[1];p=argparse.ArgumentParser();p.add_argument('--tsan',action='store_true');a=p.parse_args();kind='tsan'if a.tsan else'asan'
env=os.environ.copy();env['ASAN_OPTIONS']='detect_leaks=1:halt_on_error=1';env['TSAN_OPTIONS']='halt_on_error=1'
for name,source in [('allocator','ObjectAllocatorTest.cpp'),('abi','ObjectAbiTest.cpp'),('virtual-network','ObjectNetworkContract.cpp')]:
 binary=root/'build'/('object_'+name+'_'+kind)
 with(root/'logs'/('object-memory-'+name+'-'+kind+'-build.log')).open('w')as log:
  subprocess.run(['g++','-std=c++17','-O1','-g0','-pthread','-I'+str(root/'tests'),'-fsanitize='+('thread'if a.tsan else'address,undefined'),'-fno-omit-frame-pointer',str(root/'tests'/source),'-o',str(binary)],stdout=log,stderr=subprocess.STDOUT,check=True,timeout=120)
 with(root/'logs'/('object-memory-'+name+'-'+kind+'.log')).open('w')as log:subprocess.run([str(binary)],env=env,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=40)
 print((root/'logs'/('object-memory-'+name+'-'+kind+'.log')).read_text().strip())
 binary.unlink()
