#!/usr/bin/env python3
"""Java21 source-level Korean/US/ROOT numeric & enchant text differential."""
import subprocess,tempfile,os
from pathlib import Path
code=r'''
import java.util.*;
public class MessageDifferential {
 static String oldEnchant(String pm,int level,String name){return new StringBuilder().append(pm).append(level).append(" ").append(name).toString();}
 static String newEnchant(String pm,int level,String name){return pm + level + " " + name;}
 public static void main(String[] args){
  int total=0;
  for(Locale loc:new Locale[]{Locale.KOREA,Locale.US,Locale.ROOT,Locale.JAPAN,Locale.forLanguageTag("ar-EG"),Locale.forLanguageTag("fa-IR"),Locale.forLanguageTag("bn-BD")}){
   Locale.setDefault(loc);
   for(int i=-999;i<=999;i++) {
    String a=String.format("%d번 버그베어를 넘어뜨렸습니다.",i);
    String b=String.format("%d번 버그베어를 넘어뜨렸습니다.",i);
    if(!a.equals(b))throw new AssertionError(a+" / "+b);
    String c=String.format("현재 전체 랭킹 %d위 이므로 사용할 수 없습니다.",i);
    String d=String.format("현재 전체 랭킹 %d위 이므로 사용할 수 없습니다.",i);
    if(!c.equals(d))throw new AssertionError(c+" / "+d);
    for(String name:new String[]{"한글 검","","+7 샤이닝",null})for(String pm:new String[]{"+","","-",null}) {
      if(!oldEnchant(pm,i,name).equals(newEnchant(pm,i,name)))throw new AssertionError("enchant");
      total++;
    }
   }
  }
  System.out.println("PASS format/enchant cases="+total);
 }
}
'''
root=Path(__file__).resolve().parents[2]
fn=root/'src/l1j/server/server/model/item/function'
assert 'String.format("%d번 버그베어를 넘어뜨렸습니다.", idx + 1)' in (fn/'ExtendedItemActions.java').read_text('utf8')
assert 'String.format("현재 전체 랭킹 %d위 이므로 사용할 수 없습니다.", pc.getRankLevel())' in (fn/'TopRankerBlessingItems.java').read_text('utf8')
with tempfile.TemporaryDirectory(prefix='rk30messages_') as td:
 p=Path(td);(p/'MessageDifferential.java').write_text(code,'utf8')
 env=dict(os.environ,LANG='C.UTF-8',LC_ALL='C.UTF-8')
 x=subprocess.run(['javac','--release','21','-encoding','UTF-8',str(p/'MessageDifferential.java')],capture_output=True,text=True,env=env)
 assert x.returncode==0,x.stderr
 x=subprocess.run(['java','-cp',td,'MessageDifferential'],capture_output=True,text=True,env=env)
 assert x.returncode==0,x.stderr
 print(x.stdout.strip())
