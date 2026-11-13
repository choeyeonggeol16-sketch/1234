from pathlib import Path
import argparse,os,subprocess
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--tsan',action='store_true');args=p.parse_args()
(root/'build').mkdir(exist_ok=True);(root/'logs').mkdir(exist_ok=True)
kind='tsan' if args.tsan else 'native';san='thread' if args.tsan else 'address,undefined'
binary=root/'build'/('native_engine_'+kind)
with (root/'logs'/(kind+'-test-build.log')).open('w') as log:
 subprocess.run(['g++','-std=c++17','-O1','-g1','-pthread','-fsanitize='+san,'-fno-omit-frame-pointer','-I'+str(root/'tests'),str(root/'tests/native_engine_test.cpp'),'-o',str(binary)],check=True,stdout=log,stderr=subprocess.STDOUT,timeout=90)
env=os.environ.copy();env['ASAN_OPTIONS']='detect_leaks=1:halt_on_error=1';env['TSAN_OPTIONS']='halt_on_error=1'
with (root/'logs'/(kind+'-test.log')).open('w') as log:
 subprocess.run([str(binary)],check=True,stdout=log,stderr=subprocess.STDOUT,env=env,timeout=45)
print((root/'logs'/(kind+'-test.log')).read_text())
