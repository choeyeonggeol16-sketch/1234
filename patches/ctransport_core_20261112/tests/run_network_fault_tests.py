"""Fault injection uses live pool slabs/objects, never dangling pointers."""
from pathlib import Path
import argparse,os,subprocess
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--tsan',action='store_true');a=p.parse_args()
kind='tsan' if a.tsan else 'asan'
binary=root/'build'/('network_fault_'+kind)
with (root/'logs'/('network-fault-'+kind+'-build.log')).open('w')as log:
 subprocess.run(['g++','-std=c++17','-O1','-pthread','-fsanitize='+('thread'if a.tsan else'address,undefined'),'-fno-omit-frame-pointer','-I'+str(root/'tests'),str(root/'tests/NetworkFaultTest.cpp'),'-o',str(binary)],stdout=log,stderr=subprocess.STDOUT,check=True,timeout=90)
env=os.environ.copy();env['ASAN_OPTIONS']='detect_leaks=1:halt_on_error=1';env['TSAN_OPTIONS']='halt_on_error=1'
with (root/'logs'/('network-fault-'+kind+'.log')).open('w')as log:
 subprocess.run([str(binary)],env=env,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=45)
print((root/'logs'/('network-fault-'+kind+'.log')).read_text())
