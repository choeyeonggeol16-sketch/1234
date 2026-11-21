package l1j.server.server.payment;

import java.text.DecimalFormat;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.survey.InterfaceSurvey;
import l1j.server.server.model.character.survey.SurveySystemLoader;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.shared.command.Command;
import l1j.server.server.shared.command.CommandArgs;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.GameUtils;

public class PaymentUserHandler implements Command{
	public static void do_execute(CommandArgs args){
		new PaymentUserHandler().execute(args);
	}
	
	private PaymentInfo m_pInfo;
	private PaymentUserHandler(){}
	@Override
	public void execute(CommandArgs args) {
		try{
			String code = args.nextString();
			String name = args.nextString();
			if(GameUtils.isNullOrEmpty(code) || code.length() < 4){
				args.notify("충전코드는 스페이스가 포함될 수 없으며 최소 4자리 이상 입력되어야 합니다.");
				throw new Exception();
			}
			if(GameUtils.isNullOrEmpty(name)){
				args.notify("입금자 명은 공백일 수 없습니다.");
				throw new Exception();
			}
			
			m_pInfo = PaymentInfo.newInstance(code.toUpperCase(), name);
			if(m_pInfo == null){
				args.notify(String.format("코드 : %s, 입금자명 : %s로 발급된 정보가 없습니다.", code, name));
				return;
			}
			if(m_pInfo.get_is_use()){
				args.notify(String.format("해당 코드는 이미 %s 캐릭터로 수령되었습니다.", m_pInfo.get_character_name()));
				return;
			}
			SurveySystemLoader.getInstance().registerSurvey(args.getOwner(),"해당 코드로 충전을 진행하시겠습니까?)", args.getOwner().getId(), new InterfaceSurvey(){
				@Override
				public void survey(L1PcInstance pc, int num, boolean isYes) {
					if(!isYes){
						pc.sendPackets("코드 발급을 취소하셨습니다.");
						return;
					}
					m_pInfo
					.set_account_name(pc.getAccountName())
					.set_character_name(pc.getName())
					.set_expire_date(CommonUtil.getLocalTime())
					.set_is_use(true)
					.do_update();
					pc.getInventory().storeItem(L1ItemId.ADENA, m_pInfo.get_adena());
					pc.sendPackets(String.format("%s 아데나가 지급되었습니다.", new DecimalFormat("#,##0").format(m_pInfo.get_adena())));
				}
			}, 15000L);
		}catch(Exception e){
			args.notify(".충전 코드 입금자명 (비밀문자는 스페이스가 포함될 수 없습니다.)");
		}
	}
}
