package l1j.server.server.model.Instance;
public final class L1ItemInstance {
    private final int id; private final int template;
    private int count=1, enchant, bless, attr; private boolean identified=true;
    public L1ItemInstance(int id,int template) { this.id=id;this.template=template; }
    public int getId() {return id;} public int getItemId() {return template;}
    public int getCount() {return count;} public void setCount(int v){count=v;}
    public void setEnchantLevel(int v){enchant=v;} public int getEnchantLevel(){return enchant;}
    public void setAttrEnchantLevel(int v){attr=v;} public int getAttrEnchantLevel(){return attr;}
    public void setIdentified(boolean v){identified=v;} public boolean isIdentified(){return identified;}
    public void setBless(int v){bless=v;} public int getBless(){return bless;}
    public String getLogName(){return "#"+template+" x"+count+" e"+enchant+" b"+bless;}
}