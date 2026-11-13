from pathlib import Path
import argparse,os,subprocess,tempfile,time
p=argparse.ArgumentParser();p.add_argument('--classes',type=Path,required=True);p.add_argument('--libs',type=Path,required=True);p.add_argument('--xvfb',type=Path);a=p.parse_args()
r=Path(__file__).resolve().parents[1]
cp=os.pathsep.join([str(a.classes.resolve()),*[str(p.resolve()) for p in sorted(a.libs.glob('*.jar'))]])
with tempfile.TemporaryDirectory(prefix='manager-window-') as tmp:
 subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--enable-preview','-source','21','-target','21','-encoding','UTF-8','-cp',cp,'-d',tmp,*[str(r/'tests'/n) for n in ['ManagerWindowTest.java','ManagerLogBufferTest.java','WindowsUiMemory.java']]],check=True)
 display=None;env=os.environ.copy()
 try:
  if a.xvfb:
   env['DISPLAY']=':97';xenv=env.copy();xenv['LD_LIBRARY_PATH']=str(a.xvfb.resolve().parents[1]/'lib/x86_64-linux-gnu')+':'+env.get('LD_LIBRARY_PATH','')
   log=(r/'logs/pool-ui-xvfb.log').open('w');display=subprocess.Popen([str(a.xvfb.resolve()),':97','-screen','0','1600x1000x24','-nolisten','tcp','-ac'],env=xenv,stdout=log,stderr=subprocess.STDOUT)
   for i in range(100):
    if display.poll() is not None:raise RuntimeError('Xvfb startup failed;see pool-ui-xvfb.log')
    if Path('/tmp/.X11-unix/X97').exists():break
    time.sleep(.05)
  subprocess.run(['java','--enable-preview','--enable-native-access=ALL-UNNAMED','-Xmx192m','-Djava.awt.headless=false','-Djava.util.prefs.userRoot='+tmp,'-cp',tmp+os.pathsep+cp,'l1j.server.swing.ManagerWindowTest'],env=env,check=True,timeout=90)
 finally:
  if display is not None:display.terminate();display.wait(timeout=5);log.close()
