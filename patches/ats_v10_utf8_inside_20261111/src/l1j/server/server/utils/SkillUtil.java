package l1j.server.server.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.model.skill.SkillUse;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_SkillSound;


public class SkillUtil {

	/**
	 * 앱솔루트 배리어 효과 상태일경우 지워준다 
	 * @param pc
	 */
	public static void cancelBarrier(L1PcInstance pc) {
		if (pc.hasSkillEffect(SkillId.앱솔루트_배리어))
			pc.removeSkillEffect(SkillId.앱솔루트_배리어);
	}

	/**
	 * 스킬 효과상태일 경우 스킬을 지워준다.
	 * @param cha
	 */
	public static void cancelEraseMagic(L1Character cha) {
		if (cha.hasSkillEffect(SkillId.이레이즈_매직)){
			cha.removeSkillEffect(SkillId.이레이즈_매직);
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.sendPackets(new S_PacketBox(S_PacketBox.ICON_AURA), true);
			}

		}
	}
	/**
	 * 스킬 효과 상태일경우 스킬을 지워준다 .
	 * @param c
	 * @return
	 */
	public static boolean isCounterMagic(L1Character c) {
		if(c.hasSkillEffect(SkillId.카운터_매직)) {
			c.removeSkillEffect(SkillId.카운터_매직);
			S_SkillSound ss = S_SkillSound.get(c.getId(), EffectUtil.카운터_매직);
			c.broadcastPacket(ss, true);
			if (c instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) c;
				pc.sendPackets(ss); 
			}
			return true;
		}
		return false;
	}

	/**
	 * 캔슬레이션으로 해제할 수 없는 스킬인지를 돌려준다.
	 */
	public static boolean isNotCancelable(int skillNum) {
		return skillNum == SkillId.인챈트_웨폰 || skillNum == SkillId.블레스드_아머 || skillNum == SkillId.앱솔루트_배리어 || skillNum == SkillId.어드밴스_스피릿
				|| skillNum == SkillId.쇼크_스턴 || skillNum == SkillId.쉐도우_팽 || skillNum == SkillId.바운스_어택 || skillNum == SkillId.트루_타겟
				|| skillNum == SkillId.리덕션_아머 || skillNum == SkillId.솔리드_캐리지 || skillNum == SkillId.코마_축복_코인_8  || skillNum == SkillId.아머_브레이크
				|| skillNum == SkillId.RANKING_BUFF_1 || skillNum == SkillId.RANKING_BUFF_2 || skillNum == SkillId.카운터_배리어 || skillNum == SkillId.언케니_닷지
				|| skillNum == SkillId.쉐도우_아머 || skillNum == SkillId.드레스_이베이젼;
	}

	/**
	 * 인비지 스킬 중에 사용 가능한 스킬 
	 */
	public static boolean isInvisUsableSkill(int skillNum) {
		return skillNum == SkillId.힐 || skillNum == SkillId.라이트 || skillNum == SkillId.실드 || skillNum == SkillId.홀리_웨폰 
				|| skillNum == SkillId.큐어_포이즌 || skillNum == SkillId.인챈트_웨폰 || skillNum == SkillId.디텍션
				|| skillNum == SkillId.디크리즈_웨이트 || skillNum == SkillId.익스트라_힐 || skillNum == SkillId.블레스드_아머
				|| skillNum == SkillId.인챈트_덱스터리티 || skillNum == SkillId.카운터_매직 || skillNum == SkillId.메디테이션
				|| skillNum == SkillId.그레이터_힐  || skillNum == SkillId.인챈트_마이티 || skillNum == SkillId.리무브_커스
				|| skillNum == SkillId.헤이스트  || skillNum == SkillId.블레스_웨폰 || skillNum == SkillId.힐올
				|| skillNum == SkillId.홀리_워크  || skillNum == SkillId.그레이터_헤이스트 || skillNum == SkillId.버서커스
				|| skillNum == SkillId.풀_힐  || skillNum == SkillId.라이프_스트림 || skillNum == SkillId.카운터_디텍션
				|| skillNum == SkillId.앱솔루트_배리어 || skillNum == SkillId.어드밴스_스피릿 || skillNum == SkillId.캔슬레이션;
	}
	/**
	 * 침묵 상태일 경우에도 사용가능한 스킬
	 */
	public static boolean isSilenceUsableSkill(int skillNum) {
		return skillNum == SkillId.쇼크_스턴 || skillNum == SkillId.리덕션_아머 || skillNum == SkillId.바운스_어택 || skillNum == SkillId.솔리드_캐리지
				|| skillNum == SkillId.카운터_배리어 || skillNum == SkillId.트루_타겟;
	}

	/**
	 * 카운터 매직이 발동되지 않는 스킬
	 */
	public static boolean isNotConterMagicSkill(int skillNum) {
		return skillNum == SkillId.힐 || skillNum == SkillId.라이트 || skillNum == SkillId.실드 || skillNum == SkillId.홀리_웨폰 
				|| skillNum == SkillId.큐어_포이즌 || skillNum == SkillId.인챈트_웨폰 || skillNum == SkillId.디텍션
				|| skillNum == SkillId.디크리즈_웨이트 || skillNum == SkillId.익스트라_힐 || skillNum == SkillId.블레스드_아머
				|| skillNum == SkillId.인챈트_덱스터리티 || skillNum == SkillId.카운터_매직 || skillNum == SkillId.메디테이션
				|| skillNum == SkillId.그레이터_힐 || skillNum == SkillId.인챈트_마이티 || skillNum == SkillId.리무브_커스
				|| skillNum == SkillId.헤이스트 || skillNum == SkillId.캔슬레이션 || skillNum == SkillId.블레스_웨폰
				|| skillNum == SkillId.힐올 || skillNum == SkillId.홀리_워크 || skillNum == SkillId.그레이터_헤이스트
				|| skillNum == SkillId.버서커스 || skillNum == SkillId.풀_힐 || skillNum == SkillId.인비지블리티
				|| skillNum == SkillId.리절렉션 || skillNum == SkillId.라이프_스트림 || skillNum == SkillId.셰이프_체인지
				|| skillNum == SkillId.이뮨_투함 || skillNum == SkillId.매스_텔레포트 || skillNum == SkillId.카운터_디텍션
				|| skillNum == SkillId.크리에이트_매지컬_웨폰 || skillNum == SkillId.그레이터_리절렉션 || skillNum == SkillId.앱솔루트_배리어
				|| skillNum == SkillId.어드밴스_스피릿 || skillNum == SkillId.쇼크_스턴 || skillNum == SkillId.리덕션_아머
				|| skillNum == SkillId.바운스_어택 || skillNum == SkillId.솔리드_캐리지 || skillNum == SkillId.카운터_배리어
				|| skillNum == SkillId.블라인드_하이딩 || skillNum == SkillId.인챈트_베놈 || skillNum == SkillId.쉐도우_아머
				|| skillNum == SkillId.브링_스톤 || skillNum == SkillId.무빙_악셀레이션 || skillNum == SkillId.버닝_스피릿츠	
				|| skillNum == SkillId.베놈_레지스트 || skillNum == SkillId.더블_브레이크 || skillNum == SkillId.언케니_닷지
				|| skillNum == SkillId.쉐도우_팽 || skillNum == SkillId.드레스_마이티 || skillNum == SkillId.드레스_덱스터리티
				|| skillNum == SkillId.드레스_이베이젼 || skillNum == SkillId.트루_타겟 || skillNum == SkillId.글루잉_오라
				|| skillNum == SkillId.샤이닝_오라 || skillNum == SkillId.콜_클랜 || skillNum == SkillId.브레이브_오라
				|| skillNum == SkillId.런_클랜 || skillNum == SkillId.레지스트_매직 || skillNum == SkillId.바디_투_마인드
				|| skillNum == SkillId.텔레포트_투_마더 || skillNum == SkillId.트리플_애로우 || skillNum == SkillId.카운터_미러
				|| skillNum == SkillId.클리어_마인드 || skillNum == SkillId.레지스트_엘리멘트 || skillNum == SkillId.블러드_투_소울
				|| skillNum == SkillId.프로텍션_프롬_엘리멘탈 || skillNum == SkillId.파이어_웨폰 || skillNum == SkillId.윈드_샷
				|| skillNum == SkillId.윈드_워크 || skillNum == SkillId.어스_스킨 || skillNum == SkillId.블레스_오브_파이어
				|| skillNum == SkillId.아이_오브_스톰 || skillNum == SkillId.네이쳐스_터치 || skillNum == SkillId.블레스_오브_어스
				|| skillNum == SkillId.에이리어_오브_사일런스 || skillNum == SkillId.버닝_웨폰 || skillNum == SkillId.네이쳐스_블레싱
				|| skillNum == SkillId.콜_오브_네이쳐 || skillNum == SkillId.스톰_샷 || skillNum == SkillId.아이언_스킨
				|| skillNum == SkillId.엑조틱_바이탈라이즈 || skillNum == SkillId.워터_라이프 || skillNum == SkillId.엘리멘탈_파이어
				|| skillNum == SkillId.소울_오브_프레임 || skillNum == SkillId.어디셔널_파이어 || skillNum == SkillId.린드비오르_안식
				|| skillNum == SkillId.안타라스_안식 || skillNum == SkillId.발라카스_안식 || skillNum == SkillId.파푸리온_안식
				|| skillNum == SkillId.상태_괴물눈;	
	}

	/**
	 * 타겟이 앱솔루트 배리어 상태일경우 사용가능한 스킬 (확률계 스킬만)
	 * @param skillNum
	 * @return
	 */
	public static boolean isSkilldAbol(int skillNum) {
		return isAbsoluteBarrier(skillNum);
	}
	
	private static boolean isAbsoluteBarrier(int skillNum) {
		return skillNum == SkillId.커스_블라인드 || skillNum == SkillId.웨폰_브레이크 || skillNum == SkillId.다크니스 || skillNum == SkillId.위크니스 
				|| skillNum == SkillId.디지즈 || skillNum == SkillId.포그_오브_슬리핑 || skillNum == SkillId.매스_슬로우 || skillNum == SkillId.슬로우
				|| skillNum == SkillId.캔슬레이션 || skillNum == SkillId.사일런스 || skillNum == SkillId.디케이_포션 || skillNum == SkillId.인탱글;
	}
	/**
	 * 어스 바인드, 아이스 랜스 스킬 효과중 불가한 스킬 
	 * @param skillNum
	 * @return
	 */
	public static boolean isNotSkills(int skillNum) {
		return isNotSkill(skillNum);
	}
	private static boolean isNotSkill(int skillNum) {
		return skillNum == SkillId.아이스_랜스 || skillNum == SkillId.쇼크_스턴 || skillNum == SkillId.웨폰_브레이크 || skillNum == SkillId.슬로우 
				|| skillNum == SkillId.마나_드레인 || skillNum == SkillId.다크니스 || skillNum == SkillId.포그_오브_슬리핑 || skillNum == SkillId.아머_브레이크 
				|| skillNum == SkillId.어스_바인드 || skillNum == SkillId.플루트_워터 || skillNum == SkillId.스트라이커_게일 || skillNum == SkillId.커스_패럴라이즈
				|| skillNum == SkillId.커스_블라인드  || skillNum == SkillId.커스_포이즌 || skillNum == SkillId.이레이즈_매직; 
	}
	/**
	 * 운영자용 버프 명령어
	 */
	public static void startAllBuffThread(L1PcInstance user, String range, int buffType) {
		new AllBuffThread(user, range, buffType).begin();
	}

	public static class AllBuffThread implements Runnable {
		L1PcInstance _user;
		String _range;
		int _buffType;
		int _time;

		AllBuffThread(L1PcInstance user, String range, int buffType) {
			_user = user;
			_range = range;
			_buffType = buffType;
		}

		public void run() {
			try {
				ArrayList<Integer> buffList = null;
				if (_buffType == 1) {
					buffList = SkillUtil.getBuffList();
				} else if (_buffType == 2) {
					buffList = new ArrayList<Integer>();
					buffList.add(Integer.valueOf(SkillId.코마_축복_코인_8));
				}
				Iterator<Integer> iter = buffList.iterator();
				while (iter.hasNext()) {
					int skillId = ((Integer) iter.next()).intValue();
					Collection<L1PcInstance> pcList = new ArrayList<L1PcInstance>();
					L1PcInstance pc;
					if (_range == null) {
						pcList = L1World.getInstance().getAllPlayers();
					} else if (_range.equals("!")) {
						pcList = L1World.getInstance().getRecognizePlayer(_user);
					} else {
						pc = L1World.getInstance().getPlayer(_range);
						if (pc == null) {
							return;
						}
						pcList.add(pc);
					}
					for (L1PcInstance target : pcList) {
						if(target == null || target.isFishing()) {
							continue;
						}
						if (_buffType == 2) {
							if (target.hasSkillEffect(SkillId.코마_축복_코인_8)) {
								target.removeSkillEffect(SkillId.코마_축복_코인_8);
							}
						}
						if ((target != null) && (!target.isDead())) {
							int eachSkillId = skillId;	
							target.setBuffnoch(1);
							if (eachSkillId <= SkillId.스킬_끝) {
								new SkillUse().handleCommands(target, eachSkillId, target.getId(), target.getX(), target.getY(), null, 0, SkillUse.TYPE_GMBUFF);
							} else {
								SkillUtil.useBuff(target, eachSkillId);
							}
							target.setBuffnoch(0);
						}
					}
					Thread.sleep(2000);
				}
			} catch (Exception localException) {
			}
		}

		private void begin() {
			GeneralThreadPool.getInstance().execute(this);
		}

	}
	private static ArrayList<Integer> getBuffList() {
		ArrayList<Integer> buffList = new ArrayList<Integer>();
		buffList.add(Integer.valueOf(SkillId.인챈트_웨폰));
		buffList.add(Integer.valueOf(SkillId.블레스드_아머));
		buffList.add(Integer.valueOf(SkillId.블레스_웨폰));		
		buffList.add(Integer.valueOf(SkillId.그레이터_헤이스트));
		buffList.add(Integer.valueOf(SkillId.아이언_스킨));
		buffList.add(Integer.valueOf(SkillId.디크리즈_웨이트));
		buffList.add(Integer.valueOf(SkillId.인챈트_마이티));
		buffList.add(Integer.valueOf(SkillId.인챈트_덱스터리티));
		buffList.add(Integer.valueOf(SkillId.어드밴스_스피릿));		
		buffList.add(Integer.valueOf(SkillId.레지스트_매직));
		buffList.add(Integer.valueOf(SkillId.클리어_마인드));
		buffList.add(Integer.valueOf(SkillId.레지스트_엘리멘트));
		buffList.add(Integer.valueOf(SkillId.파이어_웨폰));
		buffList.add(Integer.valueOf(SkillId.아쿠아_프로텍트));
		buffList.add(Integer.valueOf(SkillId.네이쳐스_터치));
		buffList.add(Integer.valueOf(SkillId.엑조틱_바이탈라이즈));
		buffList.add(Integer.valueOf(SkillId.어디셔널_파이어));
		buffList.add(Integer.valueOf(SkillId.워터_라이프));
		buffList.add(Integer.valueOf(SkillId.버닝_웨폰));
		buffList.add(Integer.valueOf(SkillId.소울_오브_프레임));
		buffList.add(Integer.valueOf(SkillId.샤이닝_오라));
		buffList.add(Integer.valueOf(SkillId.글루잉_오라));
		buffList.add(Integer.valueOf(SkillId.브레이브_오라));
		buffList.add(Integer.valueOf(SkillId.버닝_스피릿츠));
		buffList.add(Integer.valueOf(SkillId.더블_브레이크));
		buffList.add(Integer.valueOf(SkillId.언케니_닷지));
		buffList.add(Integer.valueOf(SkillId.쉐도우_팽));
		buffList.add(Integer.valueOf(SkillId.베놈_레지스트));
		buffList.add(Integer.valueOf(SkillId.드레스_이베이젼));
		buffList.add(Integer.valueOf(SkillId.리덕션_아머));
		buffList.add(Integer.valueOf(SkillId.바운스_어택));
		buffList.add(Integer.valueOf(SkillId.솔리드_캐리지));
		buffList.add(Integer.valueOf(SkillId.카운터_배리어));
		buffList.add(Integer.valueOf(SkillId.이뮨_투함));
		buffList.add(Integer.valueOf(SkillId.코마_축복_코인_8));
		return buffList;
	}
	public static void useBuff(L1PcInstance pc, int skillId) {
		new SkillUse().handleCommands(pc, skillId, pc.getId(), pc.getX(), pc.getY(), null, 0, SkillUse.TYPE_GMBUFF);
	}
}
