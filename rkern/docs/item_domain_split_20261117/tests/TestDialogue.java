package l1j.server.server.model.item.function;
import java.nio.file.*;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
public final class TestDialogue {
 public static void main(String[] args)throws Exception{
   L1PcInstance pc=new L1PcInstance(0,41146);
   int count=0;
   for(String line:Files.readAllLines(Path.of(args[0]))){
     String[] cells=line.split("\t",2);
     if(cells.length!=2)throw new AssertionError("bad golden row: "+line);
     int id=Integer.parseInt(cells[0]);QuestDialogueItems.send(pc,id);
     if(!cells[1].equals(SC_NPCTalkReturn.last))throw new AssertionError("id="+id);
     count++;
   }
   if(count!=63)throw new AssertionError("dialogue count="+count);
   try {QuestDialogueItems.send(pc,-123456);throw new AssertionError("unknown allowed");}
   catch(IllegalArgumentException good){}
   System.out.println("PASS dialogue mappings=63 and unknown=throws");
 }
}
