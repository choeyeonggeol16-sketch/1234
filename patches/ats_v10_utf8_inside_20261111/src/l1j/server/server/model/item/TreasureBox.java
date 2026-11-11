package l1j.server.server.model.item;

import java.io.File;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
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

import l1j.server.Template.ArrangeHelper.MJArrangeParseeFactory;
import l1j.server.Template.ArrangeHelper.MJArrangeParser;
import l1j.server.Templates.L1Item;
import l1j.server.Templates.L1ItemMessageBox;
import l1j.server.server.datatables.ItemMessageBoxTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.ServerBasePacket;

@XmlAccessorType(XmlAccessType.FIELD)
public class TreasureBox {

	private static Logger _log = Logger
			.getLogger(TreasureBox.class.getName());

	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "TreasureBoxList")
	private static class TreasureBoxList implements Iterable<TreasureBox> {
		@XmlElement(name = "TreasureBox")
		private List<TreasureBox> _list;

		public Iterator<TreasureBox> iterator() {
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
		// 포비: 인챈트수치
		private int _enchant;

		private int _chance;

		@XmlAttribute(name = "Attr")
		private int _attr;
		@XmlAttribute(name = "Bless")
		private int _bless;

		@XmlAttribute(name = "Identi")
		private boolean _identified;

		@XmlAttribute(name = "Chance")
		private void setChance(double chance) {
			_chance = (int) (chance * 10000);
		}

		@XmlAttribute(name = "Time")
		private int _time;

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

		public int getBless() {
			return _bless;
		}

		public double getChance() {
			return _chance;
		}

		public int getTime() {
			return _time;
		}

	}

	private static enum TYPE {
		RANDOM, SPECIFIC, RANDOM_SPECIFIC
	}

	private static final String PATH = "./data/xml/Item/TreasureBox.xml";

	private static final HashMap<Integer, TreasureBox> _dataMap = new HashMap<Integer, TreasureBox>();

	public static TreasureBox get(int id) {
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
		try {
			for (Item each : getItems()) {
				_totalChance += each.getChance();
				if (ItemTable.getInstance().getTemplate(each.getItemId()) == null) {
					getItems().remove(each);
					_log.warning("아이템 ID " + each.getItemId()
							+ " 의 템플릿이 발견되지 않았습니다.2");
				}
			}
			if (getType() == TYPE.RANDOM && getTotalChance() != 1000000) {
				_log.warning("ID " + getBoxId() + "의 확률의 합계가 100%가 되지 않습니다.");
				_log.warning("ID " + getBoxId() + "의 확률 : " + getTotalChance()
						+ "%");

			}
		} catch (Exception e) {
			System.out.println("박스아이템오류 : " + getBoxId());
		}
	}

	public static boolean load() {
		try {
			JAXBContext context = JAXBContext
					.newInstance(TreasureBox.TreasureBoxList.class);

			Unmarshaller um = context.createUnmarshaller();

			File file = new File(PATH);
			TreasureBoxList list = (TreasureBoxList) um.unmarshal(file);

			for (TreasureBox each : list) {
				each.init();
				_dataMap.put(each.getBoxId(), each);
			}
			return true;
		} catch (Exception e) {
			_log.log(Level.SEVERE, PATH + "의 로드에 실패.", e);
			return false;
		}
	}

	/**
	 * TreasureBox를 여는 PC에 아이템을 입수시킨다. PC가 아이템을 가지지 못했던 경우는 아이템은 지면에 떨어진다.
	 * 
	 * @param pc
	 *            - TreasureBox를 여는 PC
	 * @return 개봉한 결과 어떠한 아이템이 나왔을 경우는 true를 돌려준다. 가지지 않고 지면에 떨어졌을 경우도 true가 된다.
	 */
	public boolean open(L1PcInstance pc) {
		ItemInstance item = null;
		Random random = null;
		if (getType().equals(TYPE.SPECIFIC)) {
			for (Item each : getItems()) {
				int itemid = each.getItemId();
				int itemcount = each.getCount();
				int enchantlvl = each.getEnchant();
				int AttrEnchantLevel = each.getAttr(); // 트레져박스 무기 속성 추가 by 개념탑재
														// 2012.03.22
				int bless = each.getBless(); // 트레져박스 아이템 봉인여부 추가 by 개념탑재
												// 2012.03.25

				L1Item temp = ItemTable.getInstance().getTemplate(itemid);
				if (temp == null) {
					continue;
				}
				if (temp.isStackable()) {
					item = ItemTable.getInstance().createItem(itemid);
					item.setCount(itemcount);
					if (each.getTime() > 0) {
						Timestamp deleteTime = null;
						deleteTime = new Timestamp(System.currentTimeMillis()
								+ (60000 * each.getTime()));
						item.setEndTime(deleteTime);
						pc.getInventory().updateItem(item,
								L1PcInventory.COL_REMAINING_TIME);
						pc.getInventory().saveItem(item,
								L1PcInventory.COL_REMAINING_TIME);
					}
					storeItem(pc, item);
					if (bless == 1) {
						if (temp.getBless() >= 0 && temp.getBless() <= 3) {
							int Bless = 0;

							switch (temp.getBless()) {
							case 0:
								Bless = 128;
								break; // 축

							case 1:
								Bless = 129;
								break; // 보통

							case 2:
								Bless = 130;
								break; // 저주

							case 3:
								Bless = 131;
								break; // 미확인
							}
							item.setBless(Bless);
							pc.getInventory().updateItem(item,
									L1PcInventory.COL_BLESS);
							pc.getInventory().saveItem(item,
									L1PcInventory.COL_BLESS);
						}
					}
				} else {
					for (int i = 0; i < itemcount; i++) {
						item = ItemTable.getInstance().createItem(itemid);
						if (enchantlvl != 0) {
							item.setIdentified(true);
							item.setEnchantLevel(enchantlvl);
							item.setAttrEnchantLevel(AttrEnchantLevel);
						}
						item.setCount(1);

						/** 마족무기함 **/

						if ((getBoxId() >= 3000036 && item.getItemId() <= 3000037)
								|| (getBoxId() == 410174)
								|| (getBoxId() == 410170)
								|| (getBoxId() >= 410127 && item.getItemId() <= 410132)
								|| (getBoxId() >= 3000126 && item.getItemId() <= 3000129)
								|| (getBoxId() >= 2000021 && item.getItemId() <= 2000028)) {
							int[] at = { 0 };
							random = new Random(System.nanoTime());
							item.setAttrEnchantLevel(at[random.nextInt(1)]);
						}
						if (each.getTime() > 0) {
							Timestamp deleteTime = null;
							deleteTime = new Timestamp(
									System.currentTimeMillis()
											+ (60000 * each.getTime()));
							item.setEndTime(deleteTime);
							pc.getInventory().updateItem(item,
									L1PcInventory.COL_REMAINING_TIME);
							pc.getInventory().saveItem(item,
									L1PcInventory.COL_REMAINING_TIME);
						}

						storeItem(pc, item);
						if (bless == 1) {
							if (temp.getBless() >= 0 && temp.getBless() <= 3) {
								int Bless = 0;

								switch (temp.getBless()) {
								case 0:
									Bless = 128;
									break; // 축

								case 1:
									Bless = 129;
									break; // 보통

								case 2:
									Bless = 130;
									break; // 저주

								case 3:
									Bless = 131;
									break; // 미확인
								}
								item.setBless(Bless);
								pc.getInventory().updateItem(item,
										L1PcInventory.COL_BLESS);
								pc.getInventory().saveItem(item,
										L1PcInventory.COL_BLESS);

							}
						}
					}

					if (ItemMessageBoxTable.getInstance().isBoxMessage(
							getBoxId())) {
						L1ItemMessageBox m_box = ItemMessageBoxTable
								.getInstance().getBoxMessage(getBoxId());
						String men = "";
						if (m_box != null) {
							String[] bonusitem = (String[]) MJArrangeParser
									.parsing(
											m_box.getBonusItem(),
											",",
											MJArrangeParseeFactory
													.createStringArrange())
									.result();
							String[] enchant = (String[]) MJArrangeParser
									.parsing(
											m_box.getEnchant(),
											",",
											MJArrangeParseeFactory
													.createStringArrange())
									.result();
							for (int i = 0; i < bonusitem.length; i++) {
								int bitem = Integer.parseInt(bonusitem[i]);
								int benchant = Integer.parseInt(enchant[i]);

								if (item.getItemId() != bitem)
									continue;

								if (item.getEnchantLevel() != benchant)
									continue;

								if (m_box.getMentoption().equalsIgnoreCase(
										"누군가가"))
									men = "누군가가 ";
								else
									men = "" + pc.getName() + " 님께서 ";

								if (m_box.getMent() != null) {
									// String BoxName =
									// ItemTable.getInstance().findItemIdByName(getBoxId());
									String BoxName = m_box.getItemName();
									String itemName = item.getViewName();
									String message = String.format(
											men + m_box.getMent(), BoxName,
											itemName);
									L1World.getInstance().broadcastPacketToAll(
											new S_SystemMessage(message));
									L1World.getInstance().broadcastPacketToAll(
											new S_PacketBox(
													S_PacketBox.GREEN_MESSAGE,
													message));
								} else {
									// String BoxName =
									// ItemTable.getInstance().findItemIdByName(getBoxId());
									String BoxName = m_box.getItemName();
									String itemName = item.getViewName();
									if (itemName == null)
										itemName = item.getName();
									String message = String.format("" + men
											+ "%s 를(을) " + BoxName
											+ "에서 획득하였습니다.", itemName);
									String message2 = String.format("" + men
											+ "%s 를(을) " + BoxName
											+ "에서 획득하였습니다.", itemName);
									L1World.getInstance()
											.broadcastPacketToAll(
													new ServerBasePacket[] {
															new S_SystemMessage(
																	message),
															new S_PacketBox(
																	S_PacketBox.GREEN_MESSAGE,
																	message2) });
								}
							}
						}
					}
				}
			}
		} else if (getType().equals(TYPE.RANDOM)) {
			try {
				random = new Random();
				int chance = 0;

				int r = random.nextInt(getTotalChance());

				for (Item each : getItems()) {
					chance += each.getChance();
					if (r < chance) {
						int itemid = each.getItemId();
						int itemcount = each.getCount();
						int enchantlvl = each.getEnchant();
						int AttrEnchantLevel = each.getAttr();
						int bless = each.getBless();

						L1Item temp = ItemTable.getInstance().getTemplate(
								itemid);

						if (temp == null) {
							continue;
						}

						if (temp.isStackable()) {
							item = ItemTable.getInstance().createItem(itemid);
							item.setCount(itemcount);
							if (each.getTime() > 0) {
								Timestamp deleteTime = null;
								deleteTime = new Timestamp(
										System.currentTimeMillis()
												+ (60000 * each.getTime()));
								item.setEndTime(deleteTime);
								pc.getInventory().updateItem(item,
										L1PcInventory.COL_REMAINING_TIME);
								pc.getInventory().saveItem(item,
										L1PcInventory.COL_REMAINING_TIME);
							}
							storeItem(pc, item);

							if (bless == 1) {
								if (temp.getBless() >= 0
										&& temp.getBless() <= 3) {
									int Bless = 0;

									switch (temp.getBless()) {
									case 0:
										Bless = 128;
										break; // 축

									case 1:
										Bless = 129;
										break; // 보통

									case 2:
										Bless = 130;
										break; // 저주

									case 3:
										Bless = 131;
										break; // 미확인
									}
									item.setBless(Bless);
									pc.getInventory().updateItem(item,
											L1PcInventory.COL_BLESS);
									pc.getInventory().saveItem(item,
											L1PcInventory.COL_BLESS);
								}
							}
						} else {
							for (int i = 0; i < itemcount; i++) {
								item = ItemTable.getInstance().createItem(
										itemid);
								if (enchantlvl != 0) {
									item.setIdentified(true);
									item.setEnchantLevel(enchantlvl);
									item.setAttrEnchantLevel(AttrEnchantLevel); // 트레져박스
																				// 무기
																				// 속성
																				// 추가
																				// by
																				// 개념탑재
																				// 2012.03.22
								}
								item.setCount(1);
								if (each.getTime() > 0) {
									Timestamp deleteTime = null;
									deleteTime = new Timestamp(
											System.currentTimeMillis()
													+ (60000 * each.getTime()));
									item.setEndTime(deleteTime);
									pc.getInventory().updateItem(item,
											L1PcInventory.COL_REMAINING_TIME);
									pc.getInventory().saveItem(item,
											L1PcInventory.COL_REMAINING_TIME);
								}
								storeItem(pc, item);

								if (bless == 1) {
									if (temp.getBless() >= 0
											&& temp.getBless() <= 3) {
										int Bless = 0;

										switch (temp.getBless()) {
										case 0:
											Bless = 128;
											break; // 축

										case 1:
											Bless = 129;
											break; // 보통

										case 2:
											Bless = 130;
											break; // 저주

										case 3:
											Bless = 131;
											break; // 미확인
										}
										item.setBless(Bless);
										pc.getInventory().updateItem(item,
												L1PcInventory.COL_BLESS);
										pc.getInventory().saveItem(item,
												L1PcInventory.COL_BLESS);
									}
								}
							}
						}

						if (ItemMessageBoxTable.getInstance().isBoxMessage(
								getBoxId())) {
							L1ItemMessageBox m_box = ItemMessageBoxTable
									.getInstance().getBoxMessage(getBoxId());
							String men = "";
							if (m_box != null) {
								String[] bonusitem = (String[]) MJArrangeParser
										.parsing(
												m_box.getBonusItem(),
												",",
												MJArrangeParseeFactory
														.createStringArrange())
										.result();
								String[] enchant = (String[]) MJArrangeParser
										.parsing(
												m_box.getEnchant(),
												",",
												MJArrangeParseeFactory
														.createStringArrange())
										.result();
								for (int i = 0; i < bonusitem.length; i++) {
									int bitem = Integer.parseInt(bonusitem[i]);
									int benchant = Integer.parseInt(enchant[i]);

									if (item.getItemId() != bitem)
										continue;

									if (item.getEnchantLevel() != benchant)
										continue;

									if (m_box.getMentoption().equalsIgnoreCase(
											"누군가가"))
										men = "누군가가 ";
									else
										men = "" + pc.getName() + " 님께서 ";

									if (m_box.getMent() != null) {
										String BoxName = m_box.getItemName();
										String itemName = item.getViewName();
										String message = String.format(men
												+ m_box.getMent(), BoxName,
												itemName);
										L1World.getInstance()
												.broadcastPacketToAll(
														new S_SystemMessage(
																message));
										L1World.getInstance()
												.broadcastPacketToAll(
														new S_PacketBox(
																S_PacketBox.GREEN_MESSAGE,
																message));
									} else {
										String BoxName = m_box.getItemName();
										String itemName = item.getViewName();
										if (itemName == null)
											itemName = item.getName();
										String message = String.format("" + men
												+ "%s 를(을) " + BoxName
												+ "에서 획득하였습니다.", itemName);
										String message2 = String.format(""
												+ men + "%s 를(을) " + BoxName
												+ "에서 획득하였습니다.", itemName);
										L1World.getInstance()
												.broadcastPacketToAll(
														new ServerBasePacket[] {
																new S_SystemMessage(
																		message),
																new S_PacketBox(
																		S_PacketBox.GREEN_MESSAGE,
																		message2) });
									}
								}
							}
						}
						break;
					}
				}
			} catch (Exception e) {
				System.out.println("박스아이템확률오류 : " + getBoxId());
			}
		} else if (getType().equals(TYPE.RANDOM_SPECIFIC)) {
			random = new Random(System.nanoTime());
			int chance = 0;

			int r = random.nextInt(getTotalChance());

			for (Item each : getItems()) {
				if (each.getChance() == 0) {
					item = ItemTable.getInstance().createItem(each.getItemId());
					if (item != null) {
						item.setCount(each.getCount());
						if (each.getTime() > 0) {
							Timestamp deleteTime = null;
							deleteTime = new Timestamp(
									System.currentTimeMillis()
											+ (60000 * each.getTime()));
							item.setEndTime(deleteTime);
							pc.getInventory().updateItem(item,
									L1PcInventory.COL_REMAINING_TIME);
							pc.getInventory().saveItem(item,
									L1PcInventory.COL_REMAINING_TIME);
						}
						storeItem(pc, item);

					}
					continue;
				}
				chance += each.getChance();

				if (r < chance) {
					item = ItemTable.getInstance().createItem(each.getItemId());
					if (item != null) {
						item.setCount(each.getCount());
						if (each.getTime() > 0) {
							Timestamp deleteTime = null;
							deleteTime = new Timestamp(
									System.currentTimeMillis()
											+ (60000 * each.getTime()));
							item.setEndTime(deleteTime);
							pc.getInventory().updateItem(item,
									L1PcInventory.COL_REMAINING_TIME);
							pc.getInventory().saveItem(item,
									L1PcInventory.COL_REMAINING_TIME);
						}
						storeItem(pc, item);
					}
					break;
				}
			}

			if (ItemMessageBoxTable.getInstance().isBoxMessage(getBoxId())) {
				L1ItemMessageBox m_box = ItemMessageBoxTable.getInstance()
						.getBoxMessage(getBoxId());
				String men = "";
				if (m_box != null) {
					String[] bonusitem = (String[]) MJArrangeParser.parsing(
							m_box.getBonusItem(), ",",
							MJArrangeParseeFactory.createStringArrange())
							.result();
					String[] enchant = (String[]) MJArrangeParser.parsing(
							m_box.getEnchant(), ",",
							MJArrangeParseeFactory.createStringArrange())
							.result();
					for (int i = 0; i < bonusitem.length; i++) {
						int bitem = Integer.parseInt(bonusitem[i]);
						int benchant = Integer.parseInt(enchant[i]);

						if (item.getItemId() != bitem)
							continue;

						if (item.getEnchantLevel() != benchant)
							continue;

						if (m_box.getMentoption().equalsIgnoreCase("누군가가"))
							men = "누군가가 ";
						else
							men = "" + pc.getName() + " 님께서 ";

						if (m_box.getMent() != null) {
							// String BoxName =
							// ItemTable.getInstance().findItemIdByName(getBoxId());
							String BoxName = m_box.getItemName();
							String itemName = item.getViewName();
							String message = String.format(
									men + m_box.getMent(), BoxName, itemName);
							L1World.getInstance().broadcastPacketToAll(
									new S_SystemMessage(message));
							L1World.getInstance().broadcastPacketToAll(
									new S_PacketBox(S_PacketBox.GREEN_MESSAGE,
											message));
						} else {
							// String BoxName =
							// ItemTable.getInstance().findItemIdByName(getBoxId());
							String BoxName = m_box.getItemName();
							String itemName = item.getViewName();
							if (itemName == null)
								itemName = item.getName();
							String message = String.format("" + men
									+ "%s 를(을) " + BoxName + "에서 획득하였습니다.",
									itemName);
							String message2 = String.format("" + men
									+ "%s 를(을) " + BoxName + "에서 획득하였습니다.",
									itemName);
							L1World.getInstance().broadcastPacketToAll(
									new ServerBasePacket[] {
											new S_SystemMessage(message),
											new S_PacketBox(
													S_PacketBox.GREEN_MESSAGE,
													message2) });

						}
					}
				}
			}
		}

		if (item == null) {
			return false;
		} else {
			int itemId = getBoxId();

			// 영혼의 결정의 파편, 마족의 스크롤, 브락크엔트의 열매
			if (itemId == 40576 || itemId == 40577 || itemId == 40578
					|| itemId == 40411 || itemId == 49013) {
				// pc.death(null);
			}
			return true;
		}
	}

	private static void storeItem(L1PcInstance pc, ItemInstance item) {
		if (pc.getInventory().checkAddItem(item, item.getCount()) != L1Inventory.OK) {
			pc.sendPackets(new S_SystemMessage("소지하고 있는 아이템이 너무 많습니다."));
			return;
		}
		L1Inventory inventory = pc.getInventory();
		item.setIdentified(true);//
		inventory.storeItem(item);
		pc.sendPackets(new S_ServerMessage(403, item.getLogName()));
		pc.getInventory().updateItem(item, L1PcInventory.COL_REMAINING_TIME);
		pc.getInventory().saveItem(item, L1PcInventory.COL_REMAINING_TIME);

	}
}
