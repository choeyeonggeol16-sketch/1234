package l1j.server.server.clientpackets;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import l1j.server.configure.Config;
import l1j.server.server.GameClient;
import l1j.server.server.datatables.LetterTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.serverpackets.S_LetterList;
import l1j.server.server.serverpackets.S_ReadLetter;
import l1j.server.server.serverpackets.S_RenewLetter;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_SystemMessage;

public class C_MailBox  extends ClientBasePacket {
	private static final int TYPE_PRIVATE_MAIL 			= 0;  // 개인 편지   
	private static final int TYPE_BLOODPLEDGE_MAIL 		= 1;  // 혈맹 편지
	private static final int TYPE_KEPT_MAIL				= 2;  // 보관 편지

	private static final int READ_PRIVATE_MAIL 			= 16; // 개인 편지읽기
	private static final int READ_BLOODPLEDGE_MAIL 		= 17; // 혈맹 편지읽기
	private static final int READ_KEPT_MAIL_			= 18; // 보관함 편지읽기

	private static final int WRITE_PRIVATE_MAIL 		= 32; // 개인 편지쓰기
	private static final int WRITE_BLOODPLEDGE_MAIL 	= 33; // 혈맹 편지쓰기

	private static final int DEL_PRIVATE_MAIL 			= 48; // 개인 편지삭제
	private static final int DEL_BLOODPLEDGE_MAIL 		= 49; // 혈맹 편지삭제
	private static final int DEL_KEPT_MAIL 				= 50; // 보관함 편지삭제

	private static final int TO_KEEP_MAIL				= 64; // 편지 보관하기 

	private static final int PRICE_PRIVATEMAIL			= 50; 	// 개인 편지 가격
	private static final int PRICE_BLOODPLEDGEMAIL		= 1000; // 혈맹 편지 가격

	private static final int SIZE_PRIVATE_MAILBOX		= 20; 	// 개인 편지함 크기
	private static final int SIZE_BLOODPLEDGE_MAILBOX	= 50; 	// 혈맹 편지함 크기
	private static final int SIZE_KEPTMAIL_MAILBOX		= 10; 	// 편지보관함 크기

	private static final String C_MailBox = "[C] C_MailBox";

	public C_MailBox(byte abyte0[], GameClient client) {
		super(abyte0);
		int type = readC(); 
		L1PcInstance pc = client.getActiveChar();	
		if (pc == null) {
			return;
		}
		switch(type){
		case TYPE_PRIVATE_MAIL: 	LetterList(pc,TYPE_PRIVATE_MAIL,SIZE_PRIVATE_MAILBOX);			break;
		case TYPE_BLOODPLEDGE_MAIL: LetterList(pc,TYPE_BLOODPLEDGE_MAIL,SIZE_BLOODPLEDGE_MAILBOX); 	break;
		case TYPE_KEPT_MAIL: 		LetterList(pc,TYPE_KEPT_MAIL,SIZE_KEPTMAIL_MAILBOX); 			break;
		case READ_PRIVATE_MAIL: 	ReadLetter(pc,READ_PRIVATE_MAIL, TYPE_PRIVATE_MAIL); 			break;
		case READ_BLOODPLEDGE_MAIL: ReadLetter(pc,READ_BLOODPLEDGE_MAIL,TYPE_BLOODPLEDGE_MAIL); 	break;
		case READ_KEPT_MAIL_: 		ReadLetter(pc,READ_KEPT_MAIL_,TYPE_KEPT_MAIL); 					break;
		case WRITE_PRIVATE_MAIL: 	WritePrivateMail(pc); 											break;
		case WRITE_BLOODPLEDGE_MAIL:WriteBloodPledgeMail(pc);										break;
		case DEL_PRIVATE_MAIL: 		DeleteLetter(pc,DEL_PRIVATE_MAIL,TYPE_PRIVATE_MAIL); 			break;
		case DEL_BLOODPLEDGE_MAIL: 	DeleteLetter(pc,DEL_BLOODPLEDGE_MAIL,TYPE_BLOODPLEDGE_MAIL); 	break;
		case DEL_KEPT_MAIL: 		DeleteLetter(pc,DEL_KEPT_MAIL,TYPE_KEPT_MAIL); 					break;
		case TO_KEEP_MAIL: 			SaveLetter(pc,TO_KEEP_MAIL,TYPE_KEPT_MAIL);						break; 
		default:	
		}
	}

	private boolean payMailCost(final L1PcInstance RECEIVER, final int PRICE) {
		int AdenaCnt = RECEIVER.getInventory().countItems(ItemId.아데나);
		if(AdenaCnt < PRICE) { 
			RECEIVER.sendPackets(new S_ServerMessage(189,""));
			return false;
		}

		RECEIVER.getInventory().consumeItem(ItemId.아데나, PRICE);
		return true;
	}

	private void WritePrivateMail(L1PcInstance sender) {
		if(!payMailCost(sender, PRICE_PRIVATEMAIL))	
			return;
		
		/** 폭탄 편지 방지 소스 **/
		long postdelaytime = System.currentTimeMillis() / 1000;
		if (!sender.isGm() && sender.getPostDelay() + 1 > postdelaytime) {
			long time = (sender.getPostDelay() + 1) - postdelaytime;
			sender.sendPackets(time + "초 후에 보낼수 있습니다."); 
			return;
		}
		/** 폭탄 편지 방지 소스 **/
		
		
		if (sender.getLevel() < Config.ALT_MAILBOXLEVEL) {
			sender.sendPackets(new S_SystemMessage("레벨" + Config.ALT_MAILBOXLEVEL + " 이하는 편지를 보낼 수 없습니다."));
			return;
		}

		int paper = readH(); //편지지
		SimpleDateFormat formatter = new SimpleDateFormat ( "yy/MM/dd", Locale.KOREA );
		String dTime = formatter.format(new Date());
		String receiverName = readS();
		String subject = readSS();
		String content = readSS();
		
		if (subject.length() > 17) {
			sender.sendPackets("제목의 길이가 너무 길어 편지를 보낼 수 없습니다.");
			return;
		}

		if(!checkCountMail(sender, receiverName, TYPE_PRIVATE_MAIL, SIZE_PRIVATE_MAILBOX)) return;

		L1PcInstance target = L1World.getInstance().getPlayer(receiverName);
		LetterTable.getInstance().writeLetter(paper, dTime, sender.getName(), receiverName, TYPE_PRIVATE_MAIL, subject, content);

		sendMessageToReceiver(target, sender, TYPE_PRIVATE_MAIL, SIZE_PRIVATE_MAILBOX);
		
		sender.setPostDelay(postdelaytime + 5);
	}
	
	private void WriteBloodPledgeMail(L1PcInstance sender) {
		if (!payMailCost(sender, PRICE_BLOODPLEDGEMAIL))
			return;

		if(sender == null)
			return;
		
		/** 폭탄 편지 방지 소스 **/
		long postdelaytime = System.currentTimeMillis() / 1000;
		if (!sender.isGm() && sender.getPostDelay() + 1 > postdelaytime) {
			long time = (sender.getPostDelay() + 1) - postdelaytime;
			sender.sendPackets(time + "초 후에 보낼수 있습니다."); 
			return;
		}
		/** 폭탄 편지 방지 소스 **/
		
		int paper = readH(); // 편지지

		Timestamp dTime = new Timestamp(System.currentTimeMillis());
		String receiverName = readS();
		String subject = readSS();
		String content = readSS();

		L1Clan targetClan = null;
		for (L1Clan clan : L1World.getInstance().getAllClans()) {
			if (clan.getClanName().toLowerCase().equals(receiverName.toLowerCase())) {
				targetClan = clan;
				break;
			}
		}
		
		if(targetClan ==null){
			return;
		}
		
		String name;
		L1PcInstance target = null;
		String membername[] = targetClan.getAllMembers();
		try {
			for (int i = 0, a = membername.length; i < a; i++) {
				name = membername[i];
				target = L1World.getInstance().getPlayer(name);
				if (!checkCountMail(sender, name, TYPE_BLOODPLEDGE_MAIL, SIZE_BLOODPLEDGE_MAILBOX))
					continue;				
				if (name.equalsIgnoreCase(sender.getName()))
					continue;
				int id = LetterTable.getInstance().writeLetter(paper, dTime, sender.getName(), name, TYPE_BLOODPLEDGE_MAIL, subject, content);
			
				if (target != null && target.getOnlineStatus() != 0) {
					target.sendPackets(new S_LetterList(S_LetterList.WRITE_TYPE_BLOODPLEDGE_MAIL, id, S_LetterList.TYPE_RECEIVE, sender.getName(), subject)); // 받는사람
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		sender.setPostDelay(postdelaytime + 5);
	}

	//편지를 삭제하기위한 메소드
	private void DeleteLetter(L1PcInstance pc , int type, int letterType){
		int id = readD(); 
		LetterTable.getInstance().deleteLetter(id);
		pc.sendPackets(new S_RenewLetter(pc,type,id));   
	}
	//편지를 읽기위한 메소드
	private void ReadLetter(L1PcInstance pc, int type, int letterType){
		int id = readD();
		LetterTable.getInstance().CheckLetter(id);
		pc.sendPackets(new S_ReadLetter(pc,type,letterType,id));
	}
	//편지리스트 출력을위한 메소드
	private void LetterList(L1PcInstance pc, int type, int count){
		pc.sendPackets(new S_LetterList(pc,type,count));
	}
	//편지를 보관하기 위함 메소드
	private void SaveLetter(L1PcInstance pc, int type, int letterType){
		int id = readD(); 
		LetterTable.getInstance().SaveLetter(id,letterType);
		pc.sendPackets(new S_RenewLetter(pc,type,id));  
	}

	private boolean checkCountMail(L1PcInstance from, String to, int type, int max) {
		int cntMailInMailBox = LetterTable.getInstance().getLetterCount(to, type);		
		if(cntMailInMailBox >= max) { // 편지함 만땅
			from.sendPackets(new S_ServerMessage(1261));
			return false;
		}		
		return true;
	}

	private void sendMessageToReceiver(L1PcInstance receiver, L1PcInstance sender, final int type, final int MAILBOX_SIZE) {
		if(receiver != null && receiver.getOnlineStatus() != 0){
			LetterList(receiver,type,MAILBOX_SIZE);
			receiver.sendPackets(S_SkillSound.get(receiver.getId(), 1091));
			receiver.sendPackets(new S_ServerMessage(428)); // 편지가 도착했습니다.
			sender.sendPackets(new S_ServerMessage(1239)); //수신자에게 편지를 보냈습니다.
			sender.sendPackets(new S_LetterList(sender,type,MAILBOX_SIZE));
		}
	}

	@Override
	public String getType() {
		return C_MailBox;
	}
}
