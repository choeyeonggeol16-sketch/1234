package l1j.server.server.model.item.assembly;

import l1j.server.Config;
import l1j.server.server.ThreadPool; //CrockController
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.item.cooking.CookingRegistry;
import l1j.server.server.utils.ItemPresentOutStream;

/** CItemConstInfo-equivalent base item attribute assembly (L1 template source). */
public final class CItemConstInfo {
    private CItemConstInfo() {}

    static void AssembleAttribute(L1ItemInstance item, ItemPresentOutStream os) {
        switch (item.getItem().getType()) {
				case 2: // light
					os.writeLightRange(item.getItem().getLightRange());
					break;
				case 7: // food
					os.writeFoodVolume(item.getItem().getFoodVolume());
					break;
				case 0: // arrow
				case 15: // sting
					os.writeDMG(item.getItem().getDmgSmall(), item.getItem().getDmgLarge());
					break;
				default:
					os.writeC(23);
					break;
				}
				os.writeC(item.getItem().getMaterial());
				os.writeD(item.getWeight());
    }

    /** Non-equipment item definitions and custom ETC display entries. */
    static void AssembleEtcItem(L1ItemInstance item, ItemPresentOutStream os,
                                int itemType2, int itemId, int itemgrade) throws Exception { // etcitem
				CItemConstInfo.AssembleAttribute(item, os);


				switch (item.getItem().getItemId()) {
				case 145001:
				case 145002:
				case 145003:
				case 145004:
				case 145005:
				case 145006:
				case 145007:
				case 145008:
					os.writeC(39);
					os.writeS("\\fH지속시간: \\aA"+Config.혈맹버프지속시간+"분");
				
					break;
				}
				
				switch (item.getItem().getItemId()) {
				case 145010:
				os.writeC(39);
				os.writeS("\\fH랜덤획득: "+Config.혈맹포인트최소+"~"+Config.혈맹포인트최대);
				break;
				}
				switch (item.getItem().getItemId()) {
				case 3000209:
				case 3000213:
				case 3000214:
					os.writeC(39);
					os.writeS("\\fH포션: \\aA신속강력체력회복제");
					os.writeC(39);
					os.writeS("\\f2시작: \\aA더블클릭");
					os.writeC(39);
					os.writeS("\\f3종료: \\aA더블클릭");
					os.writeC(39);
					os.writeS("\\f9소멸 시 자동종료");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 4100076:
					os.writeAddDMG(3);
					os.writeLongDMG(3);
					os.writeAddSP(2);
					os.writeShortHIT(3);
					os.writeLongHIT(3);
					os.writeAddEXP(20);
					os.writeAddAc(3);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 4100039:
					os.writeAddSP(3);
					os.writeMagicHIT(5);
					os.writePVPAddDMGdown(3);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 4100041:
					os.writeLongDMG(3);
					os.writeLongHIT(5);
					os.writePVPAddDMGdown(3);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 4100042:
					os.writeShortDMG(3);
					os.writeShortHIT(5);
					os.writePVPAddDMGdown(3);
					break;
				}
				// TODO 각클래스 스킬 표기
				switch (item.getItem().getItemId()) {
				// 1단계 힐
				case 45000:
					os.writeClass(1 + 2 + 4 + 8 + 16 + 128);
					os.writeLawful(1);
					os.writeStep(0);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 1단계
				case 45001:
				case 45002:
				case 45003:
				case 45004:
				case 45005:
				case 45006:
				case 45007:
					os.writeClass(1 + 2 + 4 + 8 + 16 + 128);
					os.writeLawful(0);
					os.writeStep(0);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 2단계 큐어
				case 45008:
					os.writeClass(1 + 4 + 8 + 16);
					os.writeLawful(1);
					os.writeStep(2);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 2단계 칠터치,커스
				case 45009:
				case 45010:
					os.writeClass(1 + 4 + 8 + 16);
					os.writeLawful(2);
					os.writeStep(2);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 2단계
				case 45011:
				case 45012:
				case 45013:
				case 45014:
				case 45015:
					os.writeClass(1 + 4 + 8 + 16);
					os.writeLawful(0);
					os.writeStep(2);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 3단계
				case 45019:
					os.writeClass(4 + 8);
					os.writeLawful(2);
					os.writeStep(3);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 3단계
				case 45018:
				case 45021:
					os.writeClass(4 + 8);
					os.writeLawful(1);
					os.writeStep(3);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 3단계
				case 45016:
				case 45017:
				case 45020:
				case 45022:
					os.writeClass(4 + 8);
					os.writeLawful(0);
					os.writeStep(3);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 4단계
				case 40171:
					os.writeClass(4 + 8);
					os.writeLawful(1);
					os.writeStep(4);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 4단계
				case 40173:
					os.writeClass(4 + 8);
					os.writeLawful(2);
					os.writeStep(4);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 4단계
				case 40170:
				case 40172:
				case 40174:
				case 40175:
				case 40176:
				case 40177:
					os.writeClass(4 + 8);
					os.writeLawful(0);
					os.writeStep(4);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 5단계
				case 40178:
				case 40185:
					os.writeClass(4 + 8);
					os.writeLawful(2);
					os.writeStep(5);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 5단계
				case 40180:
				case 40182:
					os.writeClass(4 + 8);
					os.writeLawful(1);
					os.writeStep(5);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 5단계
				case 40184:
				case 40183:
				case 40179:
				case 40181:
					os.writeClass(4 + 8);
					os.writeLawful(0);
					os.writeStep(5);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 6단계
				case 40186:
				case 40192:
					os.writeClass(4 + 8);
					os.writeLawful(2);
					os.writeStep(6);
					break;
				}
				switch (item.getItem().getItemId()) {
				// 6단계
				case 40187:
				case 40188:
				case 40189:
				case 40190:
				case 40191:
				case 40193:
					os.writeClass(4 + 8);
					os.writeLawful(0);
					os.writeStep(6);
					break;
				}
				// 7단계
				switch (item.getItem().getItemId()) {
				case 40199:
				case 40200:
				case 40195:
				case 40198:
					os.writeClass(8);
					os.writeLawful(0);
					os.writeStep(7);
					break;
				}
				// 7단계
				switch (item.getItem().getItemId()) {
				case 40201:
				case 40196:
					os.writeClass(8);
					os.writeLawful(2);
					os.writeStep(7);
					break;
				}
				// 7단계
				switch (item.getItem().getItemId()) {
				case 40197:
				case 40194:
					os.writeClass(8);
					os.writeLawful(1);
					os.writeStep(7);
					break;
				}
				// 8단계
				switch (item.getItem().getItemId()) {
				case 40208:
				case 40209:
				case 40207:
				case 40205:
				case 40203:
					os.writeClass(8);
					os.writeLawful(0);
					os.writeStep(8);
					break;
				}
				// 8단계
				switch (item.getItem().getItemId()) {
				case 40206:
				case 40202:
					os.writeClass(8);
					os.writeLawful(1);
					os.writeStep(8);
					break;
				}
				// 8단계
				switch (item.getItem().getItemId()) {
				case 40204:
					os.writeClass(8);
					os.writeLawful(2);
					os.writeStep(8);
					break;
				}
				// 9단계
				switch (item.getItem().getItemId()) {
				case 40216:
				case 40210:
				case 40214:
				case 40212:
				case 40217:
				case 40215:
					os.writeClass(8);
					os.writeLawful(0);
					os.writeStep(9);
					break;
				}
				// 9단계
				switch (item.getItem().getItemId()) {
				case 40213:
					os.writeClass(8);
					os.writeLawful(1);
					os.writeStep(9);
					break;
				}
				// 9단계
				switch (item.getItem().getItemId()) {
				case 40211:
					os.writeClass(8);
					os.writeLawful(2);
					os.writeStep(9);
					break;
				}
				// 10단계
				switch (item.getItem().getItemId()) {
				case 40220:
				case 40222:
					os.writeClass(8);
					os.writeLawful(1);
					os.writeStep(10);
					break;
				}
				// 10단계
				switch (item.getItem().getItemId()) {
				case 40219:
				case 40221:
				case 40223:
				case 40224:
				case 40225:
					os.writeClass(8);
					os.writeLawful(0);
					os.writeStep(10);
					break;
				}
				// 10단계
				switch (item.getItem().getItemId()) {
				case 3000095:
					os.writeClass(8);
					os.writeLawful(2);
					os.writeStep(10);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 40066:
					os.writeC(39);
					os.writeS("\\fIMP회복: \\aA5~10");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 40067:
					os.writeC(39);
					os.writeS("\\fIMP회복: \\aA15~30");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 65648: // 흑사의 코인
					os.writeAddAc(2);
					os.writeaspirit_resis(10);
					os.writeAddMaxHP(20);
					os.writeMaxMP(13);
					os.writeDMGdown(3);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 667799:
				case 779988:
					os.writeC(39);
					os.writeS("\\fH오픈 시 확률적 획득");
					break;
				}

				switch (item.getItem().getItemId()) {
				case 1000007:
					os.writeC(39);
					os.writeS("\\fI축복 수치: \\aA+500%");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000231:
					os.writeC(39);
					os.writeS("\\fI축복 수치: \\aA+1300%");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000225: // 잊혀진 상자
					os.writeC(39);
					os.writeS("\\fI내용: \\aA최상급 무기 상자.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 310064:
				case 310065:
				case 310066:
				case 310067:
					os.writeC(39);
					os.writeS("\\f9속성 [5단계] 100% 성공");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 500220:
					os.writeC(39);
					os.writeS("\\fI효과: \\aAMP 1000 회복.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 600223:
				case 600225:
				case 3000237:// PC방 버프 코인
					os.writeC(39);
					os.writeS("\\fI기간: \\aA3일");
					os.writeAddEXP(40);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 600198:
				case 600199:
				case 600200:
				case 600201:
				case 600202:
				case 600203:
				case 600204:
				case 600205:
				case 600206:
				case 600207:
				case 600208:
				case 600209:
				case 600210:
				case 600211:
				case 600212:
				case 600213:
				case 600214:
				case 600215:
				case 600216:
				case 600217:
					os.writeC(39);
					os.writeS("\\fI기간: \\aA3일");
					//os.writeAddEXP(40);
					break;
				}

				// TODO 마법인형 리스트
				DollOptionPresentation.write(os, itemId);

				switch (item.getItem().getItemId()) {
				case 300001:
					os.writeC(39);
					os.writeS("\\aR잠재력 일반: "+Config.일반갯수+"개");
					os.writeC(39);
					os.writeS("\\aH잠재력 고급: "+Config.고급갯수+"개");
					os.writeC(39);
					os.writeS("\\aL잠재력 희귀: "+Config.희귀갯수+"개");
					os.writeC(39);
					os.writeS("\\aG잠재력 영웅: "+Config.영웅갯수+"개");
					os.writeC(39);
					os.writeS("\\aF잠재력 전설: "+Config.전설갯수+"개");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 45453:
				case 45451:
				case 45452:
					os.writeC(39);
					os.writeS("\\fH소멸 시 자동해제");
					break;
				}
				
				switch (item.getItem().getItemId()) {
				case 410032:
				case 410033:
				case 410034:
				case 410035:
				case 410036:
				case 410037:
				case 410038:
					os.writeC(39);
					os.writeS("\\fI재사용 대기: \\aA3600초.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 51093:
				case 51094:
				case 51095:
				case 51096:
				case 51097:
				case 51098:
				case 51099:
				case 51100:
					os.writeC(39);
					os.writeS("\\fI효과: \\aA클래스 변경.");
					os.writeC(39);
					os.writeS("\\aA기존 스킬 삭제.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 60208:// 완력의빙수
					os.writeShortDMG(3);
					os.writeShortHIT(5);
					os.writeaSTR_Bu(1);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 60209:// 민첩의빙수
					os.writeLongDMG(3);
					os.writeLongHIT(5);
					os.writeaDEX_Bu(1);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 60210:// 지식의빙수
					os.writeaINT_Bu(1);
					os.writeAddSP(2);
					os.writeMaxMP(50);
					os.writeAddMPPrecovery(5);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000456:
				case 30105:// 성장의물약
					os.writeAddEXP(30);
					break;
				}
		
				switch (item.getItem().getItemId()) {
				case 701:
					os.writeC(39);
					os.writeS("\\fI수량: \\aA7개.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 4100037:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA영웅의 가넷");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 44114:
					os.writeC(39);
					os.writeS("\\fH조건: 파티상태");
					os.writeC(39);
					os.writeS("타겟을 지정하면 파티원들에게");
					os.writeC(39);
					os.writeS("타겟의 이펙트&멘트 출력 ");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 1000002:
					os.writeC(39);
					os.writeS("\\fI축복 수치: \\aA+30%");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 1000003:
					os.writeC(39);
					os.writeS("\\fI축복 수치: \\aA+50%");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 410064:
				case 1000004:
					os.writeC(39);
					os.writeS("\\fI축복 수치: \\aA+100%");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 4100160:
				case 4100161:
				case 4100162:
				case 4100163:
					os.writeC(39);
					os.writeS("\\fI불가능: \\f3PK시 어택/마법");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 410012:
					os.writeShortHIT(3);
					os.writeShortDMG(3);
					os.writeLongHIT(3);
					os.writeLongDMG(3);
					os.writeAddSP(3);
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA30분.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 410010:
					os.writeAddMaxHP(50);
					os.writeAddHPPrecovery(4);
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA30분.");
					break;
				}
				switch (item.getItem().getItemId()) {// 다이노스
				case 500212:
					os.writeC(39);
					os.writeS("\\fIMP 회복 + 15");
					os.writeC(39);
					os.writeS("\\fI근거리 대미지 +1");
					os.writeC(39);
					os.writeS("\\fI원거리 대미지 +1");
					os.writeC(39);
					os.writeS("\\fI추가 대미지 +1");
					os.writeC(39);
					os.writeS("\\fISP +1");
					os.writeAddEXP(5);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 410011:
					os.writeAddMaxHP(40);
					os.writeAddMPPrecovery(4);
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA30분.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 201548:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA아르카의 유물.");
					os.writeC(39);
					os.writeS("\\aA행운 아이템.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 30072:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA단테스의 유물.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 700085:
					os.writeC(39);
					os.writeS("\\fI효과: \\aA메세지 출력(20초).");
					os.writeUseLevel(82);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 44001:
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.버닝샷대기+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP(30)정령옥(2)");
					os.writeC(39);
					os.writeS("\\fH효과: \\aA사용 시 채창출력");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 90088:
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.비전대기시간+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP("+Config.비전엠피+")");
					os.writeC(39);
					os.writeS("\\aA6칸 내 순간이동");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 70706:
					
					os.writeC(39);
					os.writeS("\\fH지속시간: \\aA"+Config.버서크시간+"초");
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.버서크대기시간+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAHP(100)/결정체(100)");
					os.writeC(39);
					os.writeS("\\aA모든 내성+20/근거리대미지+20");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 70710:
					
					os.writeC(39);
					os.writeS("\\fH지속시간: \\aA128초");
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA20초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP(18)각인뼈조각(1)");
					os.writeC(39);
					os.writeS("\\fH효과: \\aA물리회피 및 반격");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 70711:
					os.writeC(39);
					os.writeS("\\fH획득조건: \\aA용기사&85레벨↑");
					os.writeC(39);
					os.writeS("\\fI클릭 시 할파스 스킬 획득");
					os.writeC(39);
					os.writeS("\\fI획득 후 교환/창고 불가");
				
					break;
				}switch (item.getItem().getItemId()) {
				case 70722:
					os.writeC(39);
					os.writeS("\\fI사용레벨: \\aA"+Config.어드밴스레벨);
					
					break;
				}switch (item.getItem().getItemId()) {
				case 70741:
					os.writeC(39);
					os.writeS("\\fI클래스: \\aA전체");
					os.writeC(39);
					os.writeS("\\fI사용레벨: \\aA"+Config.이뮨투함블레이드레벨);
					
					break;
				}switch (item.getItem().getItemId()) {
				case 70721:
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.어드밴스대기+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP(20)/결정체(200)");
					os.writeC(39);
					os.writeS("본인 8셀 이내에 ");
					os.writeC(39);
					os.writeS("파티맺은 혈맹원 1인 소환 ");
				
					break;
				}
				switch (item.getItem().getItemId()) {
				case 70708:
					
					os.writeC(39);
					os.writeS("\\fH지속시간: \\aA10분");
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.프라임대기시간+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP(50)/결정체(1000)");
					os.writeC(39);
					os.writeS("\\fH효과: \\aA버프창에 효과 표기");
					break;
				}switch (item.getItem().getItemId()) {
				case 707777:
					
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.쉐도우대기+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP(15)");
					os.writeC(39);
					os.writeS("대상에게 빠르게 접근하여");
					os.writeC(39);
					os.writeS("데미지+확률 이동불가 효과");
					break;
				}switch (item.getItem().getItemId()) {
				case 707781:
					
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.데몰리션대기+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP("+Config.데몰리션엠피+")결정체(100)");
					os.writeC(39);
					os.writeS("최대 4초간 이동 불가");
					os.writeC(39);
					os.writeS("지속 시간 동안 대미지");
					break;
					
				}
				switch (item.getItem().getItemId()) {
				case 70740:
					os.writeC(39);
					os.writeS("\\fH클래스: \\aA전체");
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.이뮨투함블레이드대기+"초");
					
					if (item._cha.getType() == 0 ) {
					os.writeC(39);
					os.writeS("\\fH소모: \\aAHP ("+Config.이뮨투함블레이드군주피+")");
					}
					if (item._cha.getType() == 1 ) {
						os.writeC(39);
						os.writeS("\\fH소모: \\aAHP("+Config.이뮨투함블레이드기사피+")");
						}
					if (item._cha.getType() == 2 ) {
						os.writeC(39);
						os.writeS("\\fH소모: \\aAHP("+Config.이뮨투함블레이드요정피+")");
						}
					if (item._cha.getType() == 3 ) {
						os.writeC(39);
						os.writeS("\\fH소모: \\aAHP("+Config.이뮨투함블레이드법사피+")");
						}
					if (item._cha.getType() == 4 ) {
						os.writeC(39);
						os.writeS("\\fH소모: \\aAHP("+Config.이뮨투함블레이드다엘피+")");
						}
					if (item._cha.getType() == 5 ) {
						os.writeC(39);
						os.writeS("\\fH소모: \\aAHP("+Config.이뮨투함블레이드용기사피+")");
						}
					if (item._cha.getType() == 6 ) {
						os.writeC(39);
						os.writeS("\\fH소모: \\aAHP("+Config.이뮨투함블레이드환술피+")");
						}
					if (item._cha.getType() == 7 ) {
						os.writeC(39);
						os.writeS("\\fH소모: \\aAHP("+Config.이뮨투함블레이드전사피+")");
						}
					os.writeC(39);
					os.writeS("확률적 이뮨 투 함 제거");
					break;
				
			}
				switch (item.getItem().getItemId()) {
				case 592247:
					os.writeLongDMG(3);
					os.writeShortDMG(3);
					os.writeAddAc(3);
					os.writePVPAddDMG(3);
					os.writePVPAddDMGdown(3);
					os.writeAddSP(2);
					break;
				
			}	
			switch (item.getItem().getItemId()) { 
			case 94268:
				os.writeShortHIT(5);
				os.writeLongHIT(5);
				os.writeLongDMG(5);
				os.writeShortDMG(5);
				os.writeAddSP(2);
				os.writeAddAc(5);
				break;
			
		}
				switch (item.getItem().getItemId()) {
			case 707779:
				
				os.writeC(39);
				os.writeS("\\fH대기시간: \\aA"+Config.어벤저대기+"초");
				os.writeC(39);
				os.writeS("\\fH소모: \\aAMP(25)흑요석(2)");
				os.writeC(39);
				os.writeS("대상에게 물리대미지");
				os.writeC(39);
				os.writeS("체력 30% 이하 확률적 즉사");
				break;
			}switch (item.getItem().getItemId()) {
			case 80888:
				
				os.writeC(39);
				os.writeS("\\fH대기시간: \\aA"+Config.이터니티대기+"초");
				os.writeC(39);
				os.writeS("\\fH소모: \\aAMP(80)");
				os.writeC(39);
				os.writeS("확률적 이동,귀환 불가");
				os.writeC(39);
				os.writeS("추가 마법 대미지");
				break;
			}switch (item.getItem().getItemId()) {
			case 220012:
				
				os.writeC(39);
				os.writeS("\\fH대기시간: \\aA"+Config.매직실드대기+"초");
				os.writeC(39);
				os.writeS("\\fH소모: \\aAMP(20)정령옥(5)");
				os.writeC(39);
				os.writeS("첫번째 디버프 마법 면역");
				os.writeC(39);
				os.writeS("두번째 확률적 효과 해제");
				break;
			}switch (item.getItem().getItemId()) {
			case 875640444:
				os.writeC(39);
				os.writeS("\\fG실패 쿨타임: \\aA"+Config.엘븐스트라이크대기+"초");
				os.writeC(39);
				os.writeS("\\fH성공 쿨타임: \\aA15초");
				os.writeC(39);
				os.writeS("\\fH소모: \\aAMP(20)");
				os.writeC(39);
				os.writeS("6칸 이내의 적을 기절시킨다.");
				break;
			}switch (item.getItem().getItemId()) {
			case 70888:
				
				os.writeC(39);
				os.writeS("\\fH추가명중: \\aA"+Config.엘븐스트라이크대기+"초");
				os.writeC(39);
				os.writeS("\\fH소모: \\aAMP(20)정령옥(2)");
				os.writeC(39);
				os.writeS("주변 대상에게 물리대미지");
				os.writeC(39);
				os.writeS("확률적 홀드 효과");
				break;
			}switch (item.getItem().getItemId()) {
			case 70735:
				
				os.writeC(39);
				os.writeS("\\fH대기시간: \\aA"+Config.리버레이션대기+"초");
				os.writeC(39);
				os.writeS("\\fH소모: \\aAMP(20)정령옥(4)");
				os.writeC(39);
				os.writeS("상태 이상 스킬의 시간이");
				os.writeC(39);
				os.writeS("일정량 감소되어 적용");
				break;
			
			}switch (item.getItem().getItemId()) {
			case 70733:
				
				os.writeC(39);
				os.writeS("\\fH대기시간: \\aA"+Config.매스이뮨대기시간+"초");
				os.writeC(39);
				os.writeS("소모: MP("+Config.매스이뮨엠피+")마력의돌("+Config.매스이뮨마돌+")");
				os.writeC(39);
				os.writeS("8칸 내 파티 중인 혈맹원들");
				os.writeC(39);
				os.writeS("이뮨 투 함 효과 적용");
				break;
			}
				switch (item.getItem().getItemId()) {
				case 70714:
					
					os.writeC(39);
					os.writeS("\\fH지속시간: \\aA128초");
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.포텐셜대기시간+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP(30)/속성석(2)");
					
					break;
				}
				switch (item.getItem().getItemId()) {
				case 210068:
				case 810012:
				case 810013:
					os.writeC(39);
					os.writeS("\\f9최대인챈가능: \\aA+"+Config.악세사리);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 70707:
					os.writeC(39);
					os.writeS("\\fH획득조건: \\aA전사 & 85레벨↑");
					os.writeC(39);
					os.writeS("\\fI클릭 시 버서크 스킬 획득");
					os.writeC(39);
					os.writeS("\\fI획득 후 교환/창고 불가");
				
					break;
				}
				switch (item.getItem().getItemId()) {
				case 70709:
					os.writeC(39);
					os.writeS("\\fH획득조건: \\aA군주 & 85레벨↑");
					os.writeC(39);
					os.writeS("\\fI클릭 시 프라임 스킬 획득");
					os.writeC(39);
					os.writeS("\\fI획득 후 교환/창고 불가");
				
					break;
				}
				switch (item.getItem().getItemId()) {
				case 700086:
					os.writeC(39);
					os.writeC(39);
					os.writeS("\\fI효과: \\aA메세지 출력(40초).");
					os.writeUseLevel(82);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 777000:
				case 777001:
				case 777002:
				case 777003:
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA" + Config.영웅유물 + "분");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000455:
				case 30104:// 코마의축복
					os.writeaSTR_Bu(5);
					os.writeaDEX_Bu(5);
					os.writeaCON_Bu(1);
					os.writeShortHIT(5);
					os.writeAddSP(1);
					os.writeAddAc(8);
					os.writeAddEXP(20);
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA2시간.");
					break;
				}
				
				switch (item.getItem().getItemId()) {
				case 70703:
					os.writeC(39);
					os.writeS("8장 클릭 시 완제품 제작");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 879977:
					os.writeC(39);
					os.writeS("\\fI확률지급");
					os.writeC(39);
					os.writeS("전설급 방어구,악세");
					os.writeC(39);
					os.writeS("\\aA좀더 높은 확률로 지급");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 889977:
					os.writeC(39);
					os.writeS("\\fI확률지급");
					os.writeC(39);
					os.writeS("전설급 방어구,악세");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 779977:
					os.writeC(39);
					os.writeS("\\fI확률지급");
					os.writeC(39);
					os.writeS("전설급 방어구,악세");

					break;
				}
				switch (item.getItem().getItemId()) {
				
				case 3000130:
					os.writeC(39);
					os.writeS("공격력,방어력,회피력");
					os.writeC(39);
					os.writeS("MR,모든 저항 상승");
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA30분.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000129:
				
					os.writeDMGdown(5);
					os.writeAddEXP(5);
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA30분.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000128:// 메티스의축복
					os.writeC(39);
					os.writeS("\\fI효과: \\aA공격력,방어력");
					os.writeC(39);
					os.writeS("\\aA회피력 상승");
					os.writeC(39);
					os.writeS("\\aA최대 MP/HP 상승");
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA30분.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000049:// 구호 증서
					os.writeC(39);
					os.writeS("\\fI효과: \\aA경험치 100% 복구");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000155:// 속죄 성서
					os.writeC(39);
					os.writeS("\\fI효과: \\aA라우풀 복구");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 490028:// 라우풀물약
					os.writeC(39);
					os.writeS("\\fI효과: \\aA성향 증가");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 490029:// 카오틱물약
					os.writeC(39);
					os.writeS("\\fI효과: \\aA성향 감소");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 4100209:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA특정 기간에 사용");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 707:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA4대 용 갑옷.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA동일 인챈 랜덤.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 706:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA휘장.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA동일 인챈 랜덤.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 705:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA문장.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA동일 인챈 랜덤.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 704:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA룸티스의 귀걸이.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA동일 인챈 랜덤.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 703:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA스냅퍼의 반지.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA동일 인챈 랜덤.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000148:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA전설급 무기.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA동일 인챈 랜덤.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 4100148: // 사신의 무기 마법 주문서
					os.writeC(39);
					os.writeS("\\aA100% 무기인챈트 +1 성공");
					break;
				}
				/*switch (getItem().getItemId()) {
				case 32001:
				case 32002:
				case 32003:
				case 32004:
				case 32005:
					os.writeC(39);
					os.writeS("\\fH[획득 방법]");
					os.writeC(39);
					os.writeS("\\aA8장 소지시 클릭");
					break;
				}*/
//				switch (getItem().getItemId()) {
//				case 32015:
//				case 32087:
//				case 32001:
//				case 32002:
//				case 32003:
//				case 32004:
//				case 32005:
//					os.writeC(39);
//					os.writeS("\\fH8장 클릭 시 제작");
//					break;
//				}
				switch (item.getItem().getItemId()) {
				case 5922910:
					os.writeC(39);
					os.writeS("\\fI랜덤 Hp증가: \\aH+10 ~ +30");
					break;
				case 5922911:
					os.writeC(39);
					os.writeS("\\fI랜덤 Hp증가: \\aH+30 ~ +50");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 5922101:
					os.writeC(39);
					os.writeS("\\fI획득: \\aH귀신망토/마에티셔츠");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 5922810:
					os.writeC(39);
					os.writeS("\\fI획득:\\aH에픽코인50~500개");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 4100150:
					os.writeC(39);
					os.writeS("\\fI획득: \\aH3대 전설인형");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 719:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA4대 마법.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA동일 인챈 랜덤.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000169:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA접설급 인챈 무기.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA+0~3 인챈 랜덤.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 718:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA영웅급 인챈 무기.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA+0~3 인챈 랜덤.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 100002:
				case 100003:// 캐릭터교환권
					os.writeC(39);
					os.writeS("\\fI내용: \\aA캐릭터 교환.");
					os.writeC(39);
					os.writeS("\\fI사용: \\aA교환 창 거래.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000353:
				case 410062:
				case 1000011:
				case 410135:
				case 4100185:
					os.writeC(39);
					os.writeS("\\fI효과: \\aA던전 시간 초기화.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000159:// 생마 일회용
					os.writeC(39);
					os.writeS("\\fI효과: \\aA1회용 생명의 마안.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 779911:
					os.writeC(39);
					os.writeS("\\fI지역: \\aA기란감옥1층");
					os.writeC(39);
					os.writeS("\\fI사용법: \\aA특정지역에서 사용");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 779922:
					os.writeC(39);
					os.writeS("\\fI지역: \\aA지배결계1층");
					os.writeC(39);
					os.writeS("\\fI사용법: \\aA특정지역에서 사용");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 779933:
					os.writeC(39);
					os.writeS("\\fI지역: \\aA지배정상층");
					os.writeC(39);
					os.writeS("\\fI사용법: \\aA특정지역에서 사용");
					break;
				}

				

				switch (item.getItem().getItemId()) {
				case 7010:
				case 7011:
					os.writeC(39);
					os.writeS("\\fH단계마다 추타/SP 증가");
					os.writeC(39);
					os.writeS("\\f9숨결 인챈시 옵션 삭제");

					break;
				}
				/*switch (getItem().getItemId()) {
				case 210064:
				case 210065:
				case 210066:
				case 210067:
					os.writeC(39);
					os.writeS("\\f9숨결 인챈실패시 옵션 삭제");

					break;
				}*/
				switch (item.getItem().getItemId()) {
				case 3000171:// 마안 상자
					os.writeC(39);
					os.writeS("\\fI내용: \\aA마안 랜덤 획득.");
					break;
				}
				/*switch (getItem().getItemId()) {
				case 40308:
					os.writeC(39);
					os.writeS("\\fI설명: \\aA화폐.");
					break;
				}*/
				switch (item.getItem().getItemId()) {
				case 145009:// 고대의 서: 무기
					os.writeC(39);
					os.writeS("\\fI물약회복률증가: \\aA+"+Config.혈맹버프회복+"%");
					break;
				}
				
				switch (item.getItem().getItemId()) {
				case 68076:// 고대의 서: 무기
					os.writeC(39);
					os.writeS("\\fI설명: \\aA확률 인챈 +1");
					os.writeC(39);
					os.writeS("\\fI설명: \\aA증발하지 않음.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 98077:// 고대의 서: 방어구
					os.writeC(39);
					os.writeS("\\fI[할파스 전용 강화]");
					os.writeC(39);
					os.writeS("\\fI설명: \\aA확률 인챈 +1");
					os.writeC(39);
					os.writeS("\\fI설명: \\aA증발하지 않음.");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 68077:// 고대의 서: 방어구
					os.writeC(39);
					os.writeS("\\fI설명: \\aA확률 인챈 +1");
					os.writeC(39);
					os.writeS("\\fI설명: \\aA증발하지 않음.");
					os.writeC(39);
					os.writeS("\\fI[제외품목]");
					os.writeC(39);
					os.writeS("\\fI악세/벨트/휘장/문장");
					
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000380:// 고대의 서:악세
					os.writeC(39);
					os.writeS("\\fI설명: \\aA확률 인챈 +1");
					os.writeC(39);
					os.writeS("\\fI설명: \\aA증발하지 않음.");
					os.writeC(39);
					os.writeS("\\fI[제외품목]");
					os.writeC(39);
					os.writeS("\\fI스냅퍼/룸티스/문장/휘장");

					break;
				}
				switch (item.getItem().getItemId()) {
				case 300000:
					os.writeC(39);
					os.writeS("\\fI설명: \\aA전설급 무기류");
					os.writeC(39);
					os.writeS("\\fI인챈: \\aA+5/+7/+8/+9 사용");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 4100139:
				case 4100179:
				case 4100180:
					os.writeC(39);
					os.writeS("\\fI설명: \\aA자신과 동일인 분신");
					os.writeC(39);
					os.writeS("\\fI업그레이드: \\aA총 3단계 가능");
					os.writeC(39);
					os.writeS("\\fI인챈소비재료: \\aA영생의 빛");
					break;
				}
				/** 특수 마법인형 인챈트 시스템 **/
				DollOptionPresentation.writePotential(item, os);
				
				switch (item.getItem().getItemId()) {
				case 410063:
				case 410137:
					os.writeC(39);
					os.writeS("\\fI설명: \\aA3단 가속");
					break;
				}

				switch (item.getItem().getItemId()) {
				case 5100135:
					os.writeC(39);
					os.writeS("\\fH[재료]");
					os.writeC(39);
					os.writeS("환상의 오만의 탑 지배부적: 1개");
					os.writeC(39);
					os.writeS("오만의 탑 정상주문서: 200장");
					os.writeC(39);
					os.writeS("아데나: 5억");
					break;
				}

				switch (item.getItem().getItemId()) {
				case 4100186:
					os.writeC(39);
					os.writeS("\\fI설명: \\aA아덴용사 방어구류");
					os.writeC(39);
					os.writeS("\\fI인챈: \\aA3~5 랜덤");
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000518:
					os.writeC(39);
					os.writeS("인챈시 옵션 증가");
					
					break;
				}
				switch (item.getItem().getItemId()) {
				case 3000517:
				case 3000518:
				case 4000517:
				case 4000518:
					os.writeC(39);
					os.writeS("인챈시 옵션 증가");
					break;
				}

				switch (item.getItem().getItemId()) {
				case 40014:
				case 140014:
				case 41415:
					os.writeClass(2 + 128);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 40068:
				case 140068:
				case 210110:
					os.writeClass(4);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 713:
				case 210036:
					os.writeClass(64);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 712:
				case 40031:
					os.writeClass(1);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 40016:
				case 140016:
				case 210113:
					os.writeClass(8 + 64);
					break;
				}
				switch (item.getItem().getItemId()) {
				case 40184:
				case 40183:
				case 40179:
				case 40181:
					os.writeClass(4 + 8);
					os.writeLawful(0);
					os.writeStep(5);
					break;
				}
				
				// 요리 표기
				CookingRegistry.writeItemOptions(os, itemId);
				
				
				// TODO ETC 표기 설정 시작
				int dmg = item.getItem().getDmgModifier();
				int hit = item.getItem().getHitModifier();

				if (item.getItem().getMinLevel() != 0) { // 최소 사용 레벨
					os.writeUseLevel(item.getItem().getMinLevel());
				}
				if (item.getItem().getMaxLevel() != 0) { // 최소~최대 사용 레벨
					os.writeLimitLevel(item.getItem().getMinLevel(), item.getItem().getMaxLevel());
				}

				if (dmg > 0)
					os.writeLongDMG(dmg);
				if (hit > 0)
					os.writeLongHIT(hit);
				if (itemId == 3000516) {
					os.writeAttrDmg(3);
				}

				if (itemType2 == 0 || itemType2 == 15) {
					if (item.isUndeadDmg()) {
						os.writeC(114);
						os.writeD(1);
					}
				}

				os.writeC(130);
				if (!item.getItem().isTradable()) {
					os.writeD(6);
				} else {
					os.writeD(7);
				}

			
    }
}
