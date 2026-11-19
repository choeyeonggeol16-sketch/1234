package l1j.server.server.model.item.function;

public final class Regression {
    static int passed;
    static void ok(boolean cond,String label){if(!cond)throw new AssertionError(label);passed++;}
    static L1PcInstance pc(){return new L1PcInstance();}
    static ItemActionContext use(L1PcInstance pc,int id,int targetId){
        L1ItemInstance item=new L1ItemInstance(100,id),target=new L1ItemInstance(200,targetId);
        pc.inv.items.put(item.id,item);pc.inv.items.put(target.id,target);
        return new ItemActionContext(pc,item,target,id);
    }
    static void reset(){ItemReceiptOperations.callback=null;ItemReceiptOperations.submits=0;ItemReceiptOperations.succeed=true;
        ItemActionCompletion.completions=0; SC_ServerMessage.fail=false;SC_SystemMessage.fail=false;}
    static void state(String s){ok(ItemReceiptOperations.submits==1,s+" submissions");
        ok(ItemActionCompletion.completions==0,s+" no early completion");}
    static void done(String s){ok(ItemReceiptOperations.callback!=null,s+" callback");
        ItemReceiptOperations.callback.run();
        ok(ItemActionCompletion.completions==1,s+" committed once");}
    static void failure(String s){reset();ItemReceiptOperations.succeed=false;}
    public static void main(String[]args){
        // Success/rollback and caller-return timing across the actual 8 production handler bodies.
        for(int branch=0;branch<2;branch++){
          reset();Config.CREATE_CHANCE_PROCESSING=branch==0?200:-1;
          var p=pc();var u=use(p,40931,41161);
          ok(QuestEarringCollection.handleBagCollection(u)==ItemActionResult.STOP,"earring stop");state("earring");done("earring");
          reset();p=pc();u=use(p,40931,41161);ItemReceiptOperations.succeed=false;
          ok(QuestEarringCollection.handleBagCollection(u)==ItemActionResult.STOP,"earring rollback stop");
          ok(ItemActionCompletion.completions==0,"earring rollback no finish");
        }
        reset();var p=pc();var u=use(p,41048,49082);
        ok(QuestVoucherExchange.handleVoucher(u)==ItemActionResult.STOP,"voucher stop");state("voucher");done("voucher");
        reset();p=pc();u=use(p,8000,1000004);
        ok(GemConversionItems.handleConvertAinGems(u)==ItemActionResult.STOP,"gem stop");state("gem");done("gem");
        reset();p=pc();u=use(p,210073,210074);
        ok(QuestTreasureFragmentCombine.handleCombineTreasureFragments(u)==ItemActionResult.STOP,"treasure stop");state("treasure");done("treasure");
        for(int branch=0;branch<2;branch++){
          reset();Config.CREATE_CHANCE_DANTES=branch==0?200:-1;
          p=pc();u=use(p,41029,41030);
          ok(QuestSummoningFragmentCombine.handleSummonFragments(u)==ItemActionResult.STOP,"summon combine stop");
          state("summon combine");done("summon combine");
        }
        for(int branch=0;branch<2;branch++){
          reset();Config.방어구비법서확률=branch==0?200:-1;
          p=pc();u=use(p,110002,22208);
          ok(DollPotentialEnhancementScrolls.handleDollPotential(u)==ItemActionResult.STOP,"doll stop");state("doll");done("doll");
        }
        reset();p=pc();u=use(p,3000210,0);
        ok(ExperienceRecoveryConsumables.handleOpenExperienceBox(u)==ItemActionResult.STOP,"box stop");
        state("experience box");ok(p.mp==10,"no precommit mp");done("experience box");
        ok(p.mp==310,"mp committed");
        reset();p=pc();u=use(p,3000210,0);SC_SystemMessage.fail=true;
        ExperienceRecoveryConsumables.handleOpenExperienceBox(u);done("box notice exception");
        ok(p.mp==310,"mp despite notification failure");
        reset();p=pc();u=use(p,40317,999);u.target.durability=3;
        ok(QuestRepairScrollUse.handleCombineMapFragments(u)==ItemActionResult.STOP,"repair stop");state("repair");done("repair");
        reset();p=pc();u=use(p,40317,999);u.target.durability=3;u.target.equipped=true;
        QuestRepairScrollUse.handleCombineMapFragments(u);done("equipped repair");ok(p.ac.sum==-1,"equipped AC adjusted");
        reset();p=pc();u=use(p,40317,999);u.target.durability=0;
        ok(QuestRepairScrollUse.handleCombineMapFragments(u)==ItemActionResult.STOP,"invalid repair stop");
        ok(ItemReceiptOperations.submits==0,"invalid repair no transaction");
        // User-facing packet failure must not turn committed grants into receipt errors.
        reset();p=pc();u=use(p,8000,1000004);SC_SystemMessage.fail=true;
        GemConversionItems.handleConvertAinGems(u);done("gem notice fault");
        reset();p=pc();u=use(p,40931,41161);Config.CREATE_CHANCE_PROCESSING=-1;
        SC_ServerMessage.fail=true;QuestEarringCollection.handleBagCollection(u);
        done("earring fail notice fault");
        reset();p=pc();u=use(p,41029,41030);Config.CREATE_CHANCE_DANTES=-1;
        SC_ServerMessage.fail=true;QuestSummoningFragmentCombine.handleSummonFragments(u);
        done("summoning notice fault");
        reset();p=pc();u=use(p,110002,22208);Config.방어구비법서확률=-1;p.failPacket=true;
        DollPotentialEnhancementScrolls.handleDollPotential(u);done("doll fail notice fault");
        reset();p=pc();u=use(p,40317,999);u.target.durability=2;SC_ServerMessage.fail=true;
        QuestRepairScrollUse.handleCombineMapFragments(u);done("whetstone notice fault");
        System.out.println("PRODUCTION_JAVA_TYPED_STUB_CONDITIONS="+passed+" PASS");
    }
}
