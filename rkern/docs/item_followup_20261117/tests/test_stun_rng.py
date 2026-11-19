#!/usr/bin/env python3
"""Verify production AoE-stun random expressions retain outcome ranges and omit hot-loop allocation."""
from pathlib import Path
import re, subprocess, tempfile
root=Path(__file__).resolve().parents[3]
p=root/'src/l1j/server/server/model/item/function/CrownAreaStunItemEffect.java'
s=p.read_text('utf8')
anchor='final ThreadLocalRandom random = ThreadLocalRandom.current();'
assert s.count(anchor)==1
snippet=s.split(anchor,1)[1].split('if (probability < 50)',1)[0]
assert 'new Random(' not in snippet and 'new int[' not in snippet
pattern=r'final int stunDuration = ([^;]+);\s*final int probability = ([^;]+);'
m=re.search(pattern,snippet)
assert m, 'expected production assignments'
expr1,expr2=m.groups()
# Compile the actual source expressions in a bounded Java 21 harness.
source='''import java.util.concurrent.ThreadLocalRandom;
public class StunRngTest {
 public static void main(String[] args) {
  int[] counts=new int[5];int hits=0;final ThreadLocalRandom random=ThreadLocalRandom.current();
  for(int i=0;i<500000;i++){
   final int stunDuration = EXPR_DURATION;
   final int probability = EXPR_PROB;
   if(stunDuration<2000||stunDuration>4000||(stunDuration%500)!=0)throw new AssertionError("duration");
   if(probability<1||probability>100)throw new AssertionError("probability");
   counts[(stunDuration-2000)/500]++;
   if(probability<50)hits++;
  }
  if(hits<237500||hits>252500)throw new AssertionError("hit odds "+hits);
  for(int c:counts)if(c<95000||c>105000)throw new AssertionError("duration odds "+c);
  System.out.println("PASS stun per-target no-alloc: 500k draws, five durations, 49% hit condition");
 }
}'''.replace('EXPR_DURATION',expr1).replace('EXPR_PROB',expr2)
with tempfile.TemporaryDirectory() as t:
 p=Path(t)/'StunRngTest.java';p.write_text(source,'utf8')
 subprocess.run(['javac','-encoding','UTF-8',str(p)],check=True,capture_output=True)
 r=subprocess.run(['java','-cp',t,'StunRngTest'],check=True,capture_output=True,text=True)
 print(r.stdout.strip())
