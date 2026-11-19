package l1j.server.server.model.item.Exp;

public final class ExpBonusState {

    // 인형(+%) 합산 : 1.0 + dollAdd
    private double dollAdd = 0.0;

    // 아르카(+%) 합산 : 1.0 + arkaAdd
    private double arkaAdd = 0.0;

    // 문장류(곱셈) : 기본 1.0
    private double ins1 = 1.0; // 900020 (성장)
    private double ins2 = 1.0; // 900093 (완력)
    private double ins3 = 1.0; // 900094 (민첩)
    private double ins4 = 1.0; // 900095 (지식)
    private double ins5 = 1.0; // 900099 (회복)
    
    // ---- Doll ----
    public double getDollMul() { return 1.0 + dollAdd; }
    public void addDollDelta(double delta) { dollAdd += delta; }

    // ---- Arka / Equip ----
    public double getArkaMul() { return 1.0 + arkaAdd; }
    public void addArkaDelta(double delta) { arkaAdd += delta; }

    // ---- Insignia ----
    public double getInsigniaMul() { return ins1 * ins2 * ins3 * ins4 * ins5; }

    public void setIns1(double v) { ins1 = v; }
    public void setIns2(double v) { ins2 = v; }
    public void setIns3(double v) { ins3 = v; }
    public void setIns4(double v) { ins4 = v; }
    public void setIns5(double v) { ins5 = v; }
    
}
