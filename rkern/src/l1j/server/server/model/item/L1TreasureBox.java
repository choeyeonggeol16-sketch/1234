package l1j.server.server.model.item;

import l1j.server.server.datatables.CObjectDB;
import java.io.File;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import l1j.server.Config;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.L1ItemDelay;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1EtcItem;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.TYUtil;
import l1j.server.server.utils.MessageUtil;

@XmlAccessorType(XmlAccessType.FIELD)
public class L1TreasureBox {

	private static Logger _log = Logger.getLogger(L1TreasureBox.class.getName());

	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "TreasureBoxList")
	private static class TreasureBoxList implements Iterable<L1TreasureBox> {
		@XmlElement(name = "TreasureBox")
		private List<L1TreasureBox> _list;

		public Iterator<L1TreasureBox> iterator() {
			return _list.iterator();
		}
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	private static class Item {
		@XmlAttribute(name = "ItemId")
		private int _itemId;

		@XmlAttribute(name = "Count")
		private int _count;

		@XmlAttribute(name = "Enchant")
		private int _enchant;

		@XmlAttribute(name = "Attr")
		private int _attr;

		@XmlAttribute(name = "Identi")
		private boolean _identified;
		
		@XmlAttribute(name = "Time")
		private int _time;
		
		@XmlAttribute(name = "Bless")
		private int _bless = 1;
		
		private int _chance;

		@XmlAttribute(name = "Chance")
		private void setChance(double chance) {
			_chance = (int) (chance * 10000);
		}

		public int getItemId() {
			return _itemId;
		}

		public int getCount() {
			return _count;
		}

		// 아이템 인첸트 레벨
		public int getEnchant() {
			return _enchant;
		}

		// 속성 인첸트 레벨
		public int getAttr() {
			return _attr;
		}

		// 확인 상태
		public boolean getIdentified() {
			return _identified;
		}

		public double getChance() {
			return _chance;
		}
		
		public int getTime(){
			return _time;
		}
		
		public int getBless(){
			return _bless;
		}
	}

	private static enum TYPE {
		RANDOM, SPECIFIC, RANDOM_SPECIFIC
	}

	private static final String PATH = "./data/xml/Item/TreasureBox.xml";

	// Build a complete new definition snapshot before publishing it. A reload
	// must not keep definitions deleted from the XML or expose a partial map.
	private static volatile HashMap<Integer, L1TreasureBox> _dataMap = new HashMap<>();

	public static L1TreasureBox get(int id) {
		return _dataMap.get(id);
	}

	@XmlAttribute(name = "ItemId")
	private int _boxId;

	@XmlAttribute(name = "Type")
	private TYPE _type;

	private int getBoxId() {
		return _boxId;
	}

	private TYPE getType() {
		return _type;
	}

	@XmlElement(name = "Item")
	private CopyOnWriteArrayList<Item> _items;

	private List<Item> getItems() {
		return _items;
	}

	private int _totalChance;

	private int getTotalChance() {
		return _totalChance;
	}

	private void init() {
		for (Item each : getItems()) {
			_totalChance += each.getChance();
			if (CObjectDB.GetItem(each.getItemId()) == null) {
				getItems().remove(each);
				_log.warning("아이템 ID " + each.getItemId() + " 의 템플릿이 발견되지 않았습니다.");
				System.out.println("아이템 ID " + each.getItemId() + " 의 템플릿이 발견되지 않았습니다.");
			}
		}
		if (getType() == TYPE.RANDOM && getTotalChance() != 1000000) {
			_log.warning("ID " + getBoxId() + "의 확률의 합계가 100%가 되지 않습니다.");
			System.out.println("ID " + getBoxId() + "의 확률의 합계가 100%가 되지 않습니다.");
		}
	}

	public static boolean load() {
		// PerformanceTimer timer = new PerformanceTimer();
		// System.out.print("■ 트래져박스 데이터 .......................... ");
		try {
			JAXBContext context = JAXBContext.newInstance(L1TreasureBox.TreasureBoxList.class);

			Unmarshaller um = context.createUnmarshaller();

			File file = new File(PATH);
			TreasureBoxList list = (TreasureBoxList) um.unmarshal(file);

			final HashMap<Integer, L1TreasureBox> next = new HashMap<>();
			for (L1TreasureBox each : list) {
				each.init();
				next.put(each.getBoxId(), each);
			}
			_dataMap = next;
			return true;
		} catch (Exception e) {
			_log.log(Level.SEVERE, PATH + "의 로드에 실패.", e);
			return false;
			//System.exit(0);
		}
		// System.out.println("■ 로딩 정상 완료 " + timer.get() + "ms");
	}


    // Invalid/unconfigured boxes fail closed rather than using the legacy reward-first path.
    public boolean supportsAtomicOpen() { return true; }

    private static final int[] ERZABE_REWARDS = {3000090,3000096,41148};
    private static final int[] SANDWORM_REWARDS = {3000092,3000091,3000095,3000094,210125,5559};
    private static final int[] ANCIENT_WEAPON_ENCHANT = {
        0,0,0,1,1,1,2,2,0,0,0,1,1,1,2,2,3,3,3,1,2,3,4,4,0,0,0,1,
        1,1,2,2,0,0,0,1,1,1,2,6,3,3,3,1,2,3,4,4,5,1,2,3,7
    };
    private static final int[] ANCIENT_ARMOR_ENCHANT = {
        0,0,0,1,1,1,2,2,0,0,0,1,1,1,2,2,3,3,3,1,2,3,4,4,0,0,0,1,
        1,1,2,2,0,0,0,1,1,1,2,2,3,3,3,1,2,3,4,4,5
    };

    private static final class TreasureNotice {
        final int objectId, rewardId;
        final String acquiredName, itemName;
        final boolean randomReward;
        TreasureNotice(int objectId, int rewardId, String acquiredName,
                       String itemName, boolean randomReward) {
            this.objectId=objectId;this.rewardId=rewardId;
            this.acquiredName=acquiredName;this.itemName=itemName;
            this.randomReward=randomReward;
        }
    }

    public void openAtomic(L1PcInstance pc, L1ItemInstance source) {
        openAtomic(pc,source,null);
    }

    /** All inventory and optional clan SQL changes commit in one CacheD receipt.
     * The callback (also used by starter support items) runs only after owner replay.
     */
    public void openAtomic(L1PcInstance pc, L1ItemInstance source, Runnable afterCommitted) {
        openAtomic(pc, source, null, afterCommitted);
    }

    /** Optional item-use settlement is staged before the same inventory COMMIT.
     * The callback must only stage SQL, never change live owner state.
     */
    public void openAtomic(L1PcInstance pc, L1ItemInstance source,
                           java.util.function.Consumer<L1InventoryTransaction> prepareSettlement,
                           Runnable afterCommitted) {
        if (pc == null || source == null || source.getItemId() != getBoxId()
                || pc.getInventory().GetByServerID(source.getId()) != source) return;
        final int boxId=getBoxId();
        final boolean erzabe=boxId==30102 && pc._ErzabeBox;
        final boolean sandworm=boxId==30103 && pc._SandwormBox;
        if (!erzabe && !sandworm && (_type==null || _items==null || _items.isEmpty())) {
            SC_SystemMessage.send(pc,"보물상자 보상 설정을 확인해 주세요.");return;
        }
        if (!erzabe && !sandworm && _type!=TYPE.SPECIFIC && _totalChance<=0) {
            SC_SystemMessage.send(pc,"보물상자 확률 설정을 확인해 주세요.");return;
        }
        final int roll=(erzabe || sandworm || _type==TYPE.SPECIFIC) ? -1
                : ThreadLocalRandom.current().nextInt(_totalChance);
        final int specialReward=erzabe ? ERZABE_REWARDS[ThreadLocalRandom.current().nextInt(ERZABE_REWARDS.length)]
                : sandworm ? SANDWORM_REWARDS[ThreadLocalRandom.current().nextInt(SANDWORM_REWARDS.length)] : 0;
        final int delay=((L1EtcItem)source.getItem()).get_delayEffect();
        final long now=System.currentTimeMillis();
        final l1j.server.server.model.L1Clan clan=(_type==TYPE.SPECIFIC
                && boxId==Config.보스한입상자번호 && Config.혈맹포인트여부2
                && pc.getClanid()!=0) ? pc.getClan() : null;
        final int pointDelta=clan==null ? 0 : Config.혈맹포인트카운트22;
        final int clanId=clan==null ? 0 : clan.getClanId();
        final String clanName=clan==null ? null : clan.getClanName();
        try (L1InventoryTransaction tx=L1InventoryTransaction.begin(
                pc,CacheD.accountOrderKey("item-use:"+pc.getId()))) {
            if (delay>0 && afterCommitted==null) tx.change(source,image->image.setLastUsed(new Timestamp(now)));
            else if (!tx.debit(source,1)) return;
            final ArrayList<TreasureNotice> outputs=new ArrayList<>();
            if (specialReward!=0) {
                prepareReward(tx,outputs,specialReward,1,0,0,1,0,now,true);
            } else {
                int chance=0;
                boolean picked=false;
                for (Item spec : getItems()) {
                    final boolean selected;
                    if (_type==TYPE.SPECIFIC) selected=true;
                    else if (_type==TYPE.RANDOM_SPECIFIC && spec.getChance()==0) selected=true;
                    else if (!picked) {
                        chance=Math.addExact(chance,(int)spec.getChance());
                        selected=roll<chance;
                        if(selected) picked=true;
                    } else selected=false;
                    if (!selected) continue;
                    int enchant=spec.getEnchant();
                    if (boxId==3000045)
                        enchant=ANCIENT_WEAPON_ENCHANT[ThreadLocalRandom.current().nextInt(ANCIENT_WEAPON_ENCHANT.length)];
                    else if (boxId>=3000038 && boxId<=3000044)
                        enchant=ANCIENT_ARMOR_ENCHANT[ThreadLocalRandom.current().nextInt(ANCIENT_ARMOR_ENCHANT.length)];
                    prepareReward(tx,outputs,spec.getItemId(),spec.getCount(),enchant,
                            _type==TYPE.SPECIFIC ? spec.getAttr():0,spec.getBless(),
                            spec.getTime(),now,spec.getChance()>0);
                    // Legacy RANDOM_SPECIFIC terminates after random success:
                    // fixed rewards later in XML were never awarded.
                    if (_type!=TYPE.SPECIFIC && spec.getChance()>0) break;
                }
            }
            if (outputs.isEmpty()) {
                SC_SystemMessage.send(pc,"보물상자 보상을 확인하지 못했습니다.");return;
            }
            if (pointDelta>0)
                l1j.server.server.datatables.ClanTable.stageWarPointDelta(tx,clan,pointDelta);
            if (prepareSettlement != null) prepareSettlement.accept(tx);
            tx.onFailure(()->SC_SystemMessage.send(pc,
                    "보물상자 사용을 저장하지 못했습니다. 보유 아이템을 확인해 주세요."));
            final Runnable onCommitted=()->{
                if(erzabe) pc._ErzabeBox=false;
                if(sandworm) pc._SandwormBox=false;
                if(pointDelta>0) clan.addWarPointCommitted(pointDelta);
                for(TreasureNotice notice:outputs) {
                    // Notification failures cannot unwind an already committed reward.
                    try {
                        L1ItemInstance live=pc.getInventory().GetByServerID(notice.objectId);
                        if(live!=null) live.setOpenEffect(live.getItem().isEndedTimeMessage()?0x01:0x20);
                        SC_ServerMessage.send(pc,403,notice.acquiredName);
                        announceCommitted(pc,boxId,notice,erzabe,sandworm);
                    } catch(RuntimeException notification) {
                        _log.log(Level.WARNING,"Treasure notice failed after COMMIT: "+boxId,notification);
                    }
                }
                try { SC_SkillSound.send(pc,pc.getId(),470); }
                catch(RuntimeException e){_log.log(Level.WARNING,"Treasure sound failed",e);}
                try { L1ItemDelay.onItemUse(pc,source); }
                catch(RuntimeException e){_log.log(Level.WARNING,"Treasure item delay failed",e);}
                if(afterCommitted!=null) {
                    try { afterCommitted.run(); }
                    catch(RuntimeException e){
                        _log.log(Level.SEVERE,"Committed treasure follow-up failed",e);
                        if(prepareSettlement!=null) throw e;
                    }
                }
            };
            if(prepareSettlement==null) tx.commit(onCommitted);
            else tx.commitAfterInventoryRelease(onCommitted);
        } catch(RuntimeException failure) {
            _log.log(Level.WARNING,"Atomic treasure preparation failed: "+boxId+" / "+pc.getId(),failure);
            SC_SystemMessage.send(pc,"보물상자 처리를 확정하지 못했습니다. 아이템 상태를 확인해 주세요.");
        }
    }

    private void prepareReward(L1InventoryTransaction tx,ArrayList<TreasureNotice> outputs,
                               int itemId,int count,int enchant,int attr,int bless,
                               int minutes,long now,boolean randomReward) {
        if(count<=0) throw new IllegalStateException("Nonpositive treasure reward: "+itemId);
        // Never register a speculative reward in L1World before its DB receipt.
        // ItemTable.createItem() publishes immediately and leaves a ghost on rollback.
        final l1j.server.server.templates.L1Item template=CObjectDB.GetItem(itemId);
        if(template==null) throw new IllegalStateException("Missing treasure reward: "+itemId);
        L1ItemInstance reward=new L1ItemInstance(template,count);
        reward.setId(l1j.server.server.IdFactory.getInstance().nextId());
        reward.setEnchantLevel(enchant);reward.setBless(bless);
        reward.setIdentified(true);
        if(_type==TYPE.SPECIFIC) reward.setAttrEnchantLevel(attr);
        if(minutes>0) reward.setEndTime(new Timestamp(Math.addExact(now,
                Math.multiplyExact(60000L,(long)minutes))));
        L1ItemInstance staged=tx.grant(reward);
        if(staged==null) throw new IllegalStateException("Cannot prepare treasure grant: "+itemId);
        outputs.add(new TreasureNotice(staged.getId(),itemId,reward.getLogName(),reward.getName(),randomReward));
    }

    private static boolean oneOf(int id,int... options) {
        for(int item:options) if(item==id) return true;
        return false;
    }

    /** Announcements are never emitted while an inventory transaction is pending. */
    private void announceCommitted(L1PcInstance pc,int boxId,TreasureNotice result,
                                          boolean erzabe,boolean sandworm) {
        final int id=result.rewardId;
        final String name=result.itemName;
        if(erzabe) {
            SC_PacketBox.broadcastGreen("누군가가 에르자베의 알에서 "+name+" 를(을) 획득하였습니다.");return;
        }
        if(sandworm) {
            SC_PacketBox.broadcastGreen("누군가가 샌드 웜의 모래 주머니에서 "+name+" 를(을) 획득하였습니다.");return;
        }
        String boxName=null;
        if(boxId==4100177 && oneOf(id,900155,900156,900157,900158,900159,900160,900161,900162,900163))
            boxName="상급 방어구 랜덤 상자";
        else if(boxId==736 && id==737) boxName="산타의 행운 양말";
        else if(boxId==4100138 && oneOf(id,900162,900163,4100136,900159,900160,900161,900157,900158,900168,900156))
            boxName="오만한 황금 상자";
        else if(boxId==4100020 && id>=4100023 && id<=4100032) boxName="변신 랜덤 상자";
        if(boxName!=null) {
            SC_PacketBox.broadcastGreen(pc.getName()+"님께서 "+boxName+"에서 \\f3("+name+")\\f2 를(을) 획득하였습니다.");
            SC_SystemMessage.broadcast(pc.getName()+"님께서 "+boxName+"에서 ("+name+") 를(을) 획득하였습니다.");
        } else if(boxId>=30152 && boxId<=30155 && oneOf(id,40222,41148,210125,5559,210130,210131,
                210132,3000094,40346,40354,40362,40370)) {
            SC_PacketBox.broadcastGreen("누군가가 빛나는 룬스톤에서 ("+name+") 를(을) 획득하였습니다.");
            SC_SystemMessage.broadcast("누군가가 빛나는 룬스톤에서 ("+name+") 를(을) 획득하였습니다.");
        } else if(boxId==30102 && oneOf(id,3000090,3000096,41148,20049,20050,222306,222304,20271)) {
            SC_PacketBox.broadcastGreen("누군가가 에르자베의 알에서 ("+name+") 를(을) 획득하였습니다.");
            SC_SystemMessage.broadcast("누군가가 에르자베의 알에서 ("+name+") 를(을) 획득하였습니다.");
        } else if(boxId==30103 && oneOf(id,3000092,3000091,3000095,3000094,210125,5559,
                20049,20050,222306,222304,20271)) {
            SC_PacketBox.broadcastGreen("누군가가 샌드 웜의 모래 주머니에서 ("+name+") 를(을) 획득하였습니다.");
            SC_SystemMessage.broadcast("누군가가 샌드 웜의 모래 주머니에서 ("+name+") 를(을) 획득하였습니다.");
        } else if(boxId==4100145 && result.randomReward && oneOf(id,505011,505015,505012,
                620,625,626,623,618,619,616,622,617)) {
            SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 \\f3("+name+")\\f2 를(을) 획득하였습니다.");
            SC_SystemMessage.broadcast("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 ("+name+") 를(을) 획득하였습니다.");
        }
        // These random-box branches broke out before reaching the dynamic boss
        // notification block in the original implementation.
        if(boxId==4100177 || boxId==736 || boxId==4100138 || boxId==4100020
                || boxId==30102 || (boxId>=30152 && boxId<=30155)
                || boxId==4100145 && result.randomReward) return;
        if(boxId==30103 && oneOf(id,3000092,3000091,3000095,3000094,210125,5559,
                20049,20050,222306,222304,20271)) return;
        // A RANDOM_SPECIFIC fixed grant never entered the original boss-notice branch.
        if(!result.randomReward && _type==TYPE.RANDOM_SPECIFIC) return;
        {
            final int[] boxIds=TYUtil.GetIntTokenizeIntArray(Config.BossAreaDropBossIds3);
            final int[] rewardIds=TYUtil.GetIntTokenizeIntArray(Config.BossAreaDropBossIds2);
            if(boxIds==null || rewardIds==null || !oneOf(boxId,boxIds) || !oneOf(id,rewardIds)) return;
            if(result.randomReward) {
                SC_SkillSound.send(pc,pc.getId(),3198);
                SC_SkillSound.send(pc,pc.getId(),2046);
            }
            String actor=Config.SysMsg ? pc.getName()+(_type==TYPE.SPECIFIC ? " 용사가 " : "님께서 ") : "어느 용사가 ";
            MessageUtil.toAllPlayers("아덴 월드의 "+actor+name+"\\fH를(을) 획득하였습니다.");
            MessageUtil.greenMessageToAllPlayers("아덴 월드의 "+actor+name+"\\f2를(을) 획득하였습니다.");
        }
    }
}
