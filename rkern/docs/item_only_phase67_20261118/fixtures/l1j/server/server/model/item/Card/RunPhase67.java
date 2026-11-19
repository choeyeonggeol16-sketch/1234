package l1j.server.server.model.item.Card;
import java.util.*;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
public class RunPhase67 {
 static final String[] GETTERS = {"getInfo_atun_poly","getInfo_bapo_poly","getInfo_class_poly","getInfo_da_poly","getInfo_dev_poly","getInfo_drag_poly","getInfo_dru_poly","getInfo_ds_poly","getInfo_dsn_poly","getInfo_el_poly","getInfo_ga_poly","getInfo_ger_poly","getInfo_il_poly","getInfo_ki_poly","getInfo_mar_poly","getInfo_moon_poly","getInfo_pen_poly","getInfo_wa_poly","getInfo_wi_poly","getlegend01","getlegend02","getlegend03","getlegend04","getlegend05","getlegend06","getlegend07","getlegend08","getlegend09","getlegend10","getlegend11","getlegend12","getlegend13","getlegend14","getmyth01"};
 static void ck(boolean ok, String msg){if(!ok)throw new AssertionError(msg);}
 static L1PcInstance player(Random r){
  L1PcInstance pc=new L1PcInstance();pc.acc=new Account();pc.sex=r.nextInt(3)-1;pc.job=r.nextInt(8);pc.dead=r.nextInt(25)==0;
  for(String g:GETTERS)pc.acc.m.put(g,r.nextInt(6));return pc;
 }
 static String infoMyth(L1PcInstance p, boolean old){if(old)OldMyth.cardinfo(p);else NewMyth.cardinfo(p);return Trace.take();}
 static String listMyth(L1PcInstance p, boolean old){if(old)OldMyth.cardlist(p);else NewMyth.cardlist(p);return Trace.take();}
 static String infoLegend(L1PcInstance p, boolean old){if(old)OldLegend.cardinfo(p);else NewLegend.cardinfo(p);return Trace.take();}
 static String legendList(L1PcInstance p,int cat,boolean old){
  if(old){switch(cat){case 0->OldLegend.cardlist(p);case 1->OldLegend.cardlistA(p);case 2->OldLegend.cardlistB(p);case 3->OldLegend.cardlistC(p);}}
  else{switch(cat){case 0->NewLegend.cardlist(p);case 1->NewLegend.cardlistA(p);case 2->NewLegend.cardlistB(p);case 3->NewLegend.cardlistC(p);}}
  return Trace.take();
 }
 static String past(L1PcInstance p,boolean old){
  Trace.seed=161803L;
  if(old)OldPast.L1PastSelct(p);else NewPast.L1PastSelct(p);
  return Arrays.toString(p.acc.sel)+":"+Trace.take();
 }
 static L1PcInstance cloneP(L1PcInstance p){L1PcInstance q=new L1PcInstance();q.acc=new Account();q.acc.m.putAll(p.acc.m);q.sex=p.sex;q.job=p.job;q.dead=p.dead;q.id=p.id;return q;}
 public static void main(String[] a){
  Random r=new Random(1234567L);int checked=0;
  for(int i=0;i<15000;i++){
   L1PcInstance p=player(r),q=cloneP(p);
   String v1=infoMyth(p,true),v2=infoMyth(q,false);ck(v1.equals(v2),"myth info #"+i+" "+v1+" "+v2);checked++;
   v1=listMyth(p,true);v2=listMyth(q,false);ck(v1.equals(v2),"myth list #"+i);checked++;
   v1=infoLegend(p,true);v2=infoLegend(q,false);ck(v1.equals(v2),"legend info #"+i);checked++;
   for(int k=0;k<4;k++){v1=legendList(p,k,true);v2=legendList(q,k,false);ck(v1.equals(v2),"legend #"+i+" k="+k);checked++;}
   v1=past(p,true);v2=past(q,false);ck(v1.equals(v2),"past #"+i+" " +v1+" "+v2);checked++;
  }
  System.out.println("Phase67 original vs optimized type-compiled execution comparisons: "+checked+" PASS");
  // Exercise threshold 0/1/2/3 eligible and all 11 eligible with deterministic random.
  for(int n=0;n<=11;n++){
    L1PcInstance p=player(r);for(String g:GETTERS)p.acc.m.put(g,0);
    String[] MYTH={"getInfo_class_poly","getInfo_ki_poly","getInfo_el_poly","getInfo_wi_poly","getInfo_da_poly","getInfo_wa_poly","getInfo_drag_poly","getInfo_il_poly","getInfo_dsn_poly","getInfo_bapo_poly","getmyth01"};
    for(int j=0;j<n;j++)p.acc.m.put(MYTH[j],2);
    L1PcInstance q=cloneP(p);
    String v1=past(p,true),v2=past(q,false);ck(v1.equals(v2),"eligible="+n+" "+v1+" vs "+v2);
  }
  System.out.println("Phase67 all eligibility boundary cases: 12 PASS");
 }
}
