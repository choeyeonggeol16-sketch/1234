from pathlib import Path
import argparse,os,subprocess
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--tsan',action='store_true');a=p.parse_args();kind='tsan' if a.tsan else 'asan'
log=root/'logs'/('reference-restore-'+kind+'.log');binary=root/'build'/('reference_trace_'+kind)
with (root/'logs'/('reference-restore-'+kind+'-build.log')).open('w') as out:
 subprocess.run(['g++','-std=c++17','-O1','-g0','-pthread','-fsanitize='+('thread' if a.tsan else 'address,undefined'),'-fno-omit-frame-pointer','-I'+str(root/'tests'),str(root/'tests/ReferenceTraceTest.cpp'),'-o',str(binary)],stdout=out,stderr=subprocess.STDOUT,check=True,timeout=120)
env=os.environ.copy();env['ASAN_OPTIONS']='detect_leaks=1:halt_on_error=1';env['TSAN_OPTIONS']='halt_on_error=1'
with log.open('w') as out:subprocess.run([str(binary)],env=env,stdout=out,stderr=subprocess.STDOUT,check=True,timeout=30)
print(log.read_text())
