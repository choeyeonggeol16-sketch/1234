package l1j.server.Payment;

import java.text.DecimalFormat;
import l1j.server.MJSurveySystem.MJInterfaceSurvey;
import l1j.server.MJSurveySystem.MJSurveySystemLoader;
import l1j.server.MJTemplate.Command.MJCommand;
import l1j.server.MJTemplate.Command.MJCommandArgs;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.MJCommons;

public class MJPaymentUserHandler implements MJCommand{
	public static void do_execute(MJCommandArgs args){
		new MJPaymentUserHandler().execute(args);
	}
	
	private MJPaymentInfo m_pInfo;
	private MJPaymentUserHandler(){}
	@Override
	public void execute(MJCommandArgs args) {
		try{
			String code = args.nextString();
			String name = args.nextString();
			if(MJCommons.isNullOrEmpty(code) || code.length() < 4){
				args.notify("충전코드는 스페이스가 포함될 수 없으며 최소 4자리 이상 입력되어야 합니다.");
				throw new Exception();
			}
			if(MJCommons.isNullOrEmpty(name)){
				args.notify("입금자 명은 공백일 수 없습니다.");
				throw new Exception();
			}
			
			m_pInfo = MJPaymentInfo.newInstance(code.toUpperCase(), name);
			if(m_pInfo == null){
				args.notify(String.format("코드 : %s, 입금자명 : %s로 발급된 정보가 없습니다.", code, name));
				return;
			}
			if(m_pInfo.get_is_use()){
				args.notify(String.format("해당 코드는 이미 %s 캐릭터로 수령되었습니다.", m_pInfo.get_character_name()));
				return;
			}
			MJSurveySystemLoader.getInstance().registerSurvey(args.getOwner(),"해당 코드로 충전을 진행하시겠습니까?)", args.getOwner().getId(), new MJInterfaceSurvey(){
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
