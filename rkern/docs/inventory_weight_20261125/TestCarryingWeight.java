package l1j.server.server.model;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import l1j.server.Config;
import l1j.server.server.model.Instance.CItemList;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.templates.L1EtcItem;

public final class TestCarryingWeight {
    private static int checks;
    private static void check(long expected, long actual) {
        checks++;
        if (expected != actual) throw new AssertionError("expected=" + expected + " actual=" + actual);
    }
    private static L1EtcItem template(int id, int weight) {
        L1EtcItem t = new L1EtcItem();
        t.setItemId(id); t.setWeight(weight); t.set_stackable(true);
        return t;
    }
    private static long reference(CItemList list) {
        long total = 0;
        for (L1ItemInstance i : list.snapshot()) {
            int w = i.getItem().getWeight();
            total += w == 0 ? 0L : Math.clamp((long)i.getCount()*w/1000L,1L,Integer.MAX_VALUE);
        }
        return total;
    }
    public static void main(String[] args) throws Exception {
        Random rng = new Random(20261125L);
        L1EtcItem[] templates = {template(1,0),template(2,1),template(3,999),
            template(4,1000),template(5,1001),template(6,Integer.MAX_VALUE)};
        CInventory a = new CInventory(), b = new CInventory();
        CItemList x = a._items, y = b._items;
        Config.RATE_WEIGHT_LIMIT=1.0;
        check(0,x.GetCarryingWeight());
        ArrayList<L1ItemInstance> detached=new ArrayList<>();
        for (int i=0;i<180;i++) {
            L1ItemInstance it=new L1ItemInstance(templates[i%templates.length],1+i);
            it.setId(i+1); detached.add(it);
        }
        for(int n=0;n<60000;n++) {
            CItemList src=rng.nextBoolean()?x:y, dst=src==x?y:x;
            switch(rng.nextInt(10)) {
            case 0: if(!detached.isEmpty()) src.Push(detached.remove(detached.size()-1)); break;
            case 1: if(!src.isEmpty()){L1ItemInstance it=src.get(rng.nextInt(src.size()));src.Pop(it);detached.add(it);} break;
            case 2: if(!src.isEmpty()){L1ItemInstance it=src.get(rng.nextInt(src.size()));src.Pop(it);dst.Push(it);} break;
            case 3: if(!src.isEmpty())src.get(rng.nextInt(src.size())).setCount(rng.nextInt(2_000_000_001));break;
            case 4: if(!src.isEmpty())src.get(rng.nextInt(src.size())).setItem(templates[rng.nextInt(templates.length)]);break;
            case 5: if(!src.isEmpty()){
                L1ItemInstance it=src.get(rng.nextInt(src.size()));int old=it.getCount();
                int change=rng.nextInt(2001)-1000;
                boolean ok=(long)old+change>=0&&(long)old+change<=CInventory.MAX_AMOUNT;
                check(ok?1:0,it.AddAmount(change)?1:0);check(ok?(long)old+change:old,it.getCount());
                it.setCount(old); // rollback/replay path
            }break;
            case 6: templates[rng.nextInt(templates.length)].setWeight(rng.nextInt(Integer.MAX_VALUE));break;
            case 7: src.shuffle();break;
            case 8: if(n%32==0){detached.addAll(src.snapshot());src.clear();}break;
            case 9: if(n%16==0 && !detached.isEmpty()){
                ArrayList<L1ItemInstance> rows=new ArrayList<>();
                for(int j=0, count=Math.min(4,detached.size());j<count;j++) rows.add(detached.remove(detached.size()-1));
                detached.addAll(src.replaceDetached(rows));
            }break;
            }
            long rx=reference(x),ry=reference(y);
            check(rx,x.GetCarryingWeight());check(ry,y.GetCarryingWeight());
            Config.RATE_WEIGHT_LIMIT=(n%3==0?0.5:n%3==1?1.0:2.75);
            check((int)(rx/Config.RATE_WEIGHT_LIMIT),a.GetCarryingWeight());
            check((int)(ry/Config.RATE_WEIGHT_LIMIT),b.GetCarryingWeight());
            if(n%64==0){check(rx,x.ValidateCarringWeight());check(ry,y.ValidateCarringWeight());}
        }
        // Batch claim rejection must leave both cached totals untouched.
        L1ItemInstance owned=new L1ItemInstance(templates[0],1);
        x.Push(owned); long beforeX=reference(x),beforeY=reference(y);
        try{y.addAll(java.util.List.of(new L1ItemInstance(templates[0],2),owned));throw new AssertionError("double claim");}
        catch(IllegalStateException expected){}
        check(beforeX,x.GetCarryingWeight());check(beforeY,y.GetCarryingWeight());
        x.clear();y.clear();
        // Stress a detached setter racing membership publication. Final
        // quiescent totals must remain exact; no cross-list lock acquisition.
        L1ItemInstance racing=new L1ItemInstance(templates[1],1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread setter=new Thread(()->{try{for(int n=0;n<100000;n++)racing.setCount(n);}catch(Throwable t){failure.set(t);}});
        setter.start();
        for(int n=0;n<20000;n++){
            x.Push(racing); x.GetCarryingWeight(); x.Pop(racing);
            y.Push(racing); y.GetCarryingWeight(); y.Pop(racing);
        }
        x.Push(racing); setter.join(10000);
        if(setter.isAlive())throw new AssertionError("setter deadlock");
        if(failure.get()!=null)throw new AssertionError(failure.get());
        check(reference(x),x.GetCarryingWeight());check(0,y.GetCarryingWeight());
        x.Pop(racing);check(0,x.GetCarryingWeight());
        System.out.println("PASS actual production carrying-weight regression checks="+checks);
    }
}
