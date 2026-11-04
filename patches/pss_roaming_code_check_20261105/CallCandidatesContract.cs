using System;
class CallCandidatesContract {
 static int count;
 static void Equal(uint[] got,params uint[] expected){count++;if(got.Length!=expected.Length)throw new Exception("length");for(int i=0;i<got.Length;i++)if(got[i]!=expected[i])throw new Exception("address");}
 static byte[] Call(uint at,uint target){byte[] code=new byte[5];code[0]=0xe8;Array.Copy(BitConverter.GetBytes(unchecked((int)(target-at-5))),0,code,1,4);return code;}
 static void Main(){
  Equal(PssRoamingCodeCheck.RelativeCalls(new byte[0],0x669840));
  Equal(PssRoamingCodeCheck.RelativeCalls(Call(0x669840,0x669b20),0x669840),0x669b20);
  Equal(PssRoamingCodeCheck.RelativeCalls(Call(0x669840,0x620750),0x669840),0x620750);
  Equal(PssRoamingCodeCheck.RelativeCalls(Call(0x669840,0x400fff),0x669840));
  Equal(PssRoamingCodeCheck.RelativeCalls(Call(0x669840,0x1100000),0x669840));
  Equal(PssRoamingCodeCheck.RelativeCalls(new byte[]{0xe8,1,2,3},0x669840));
  byte[] a=Call(0x669840,0x669b20),b=Call(0x669845,0x669b20),both=new byte[10];Array.Copy(a,0,both,0,5);Array.Copy(b,0,both,5,5);Equal(PssRoamingCodeCheck.RelativeCalls(both,0x669840),0x669b20);
  Console.WriteLine("PASS read-only call candidate bounds contract checks="+count);
 }
}
