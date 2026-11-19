package l1j.server.server.model.item.Card;

import java.util.ArrayList;
import java.util.List;

import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

/**
 * 수호성(조우, 크리스터, 이실로테, 아툰) 시스템 핸들러
 */
public class L1SkillTree {

	/** 조우 탭 정보 (neitreese01) */
	public static void Info(L1PcInstance pc) {
		if (invalid(pc)) return;
		Account acc = pc.getAccount();
		List<String> args = prepareBaseArgs(acc);

		// 조우 스킬들 활성화 상태 아이콘 (210~219)
		args.add(acc.getTree_iceq() != 0 ? "211" : "210");
		args.add(acc.getTree_fireq() != 0 ? "213" : "212");
		args.add(acc.getTree_final() != 0 ? "215" : "214");
		args.add(acc.getTree_ices() != 0 ? "217" : "216");
		args.add(acc.getTree_rain() != 0 ? "219" : "218");

		// 조우 스킬들 선택 여부 체크 아이콘 (208~209)
		int sel = acc.getTree_select();
		args.add(sel == 569 ? "209" : "208");
		args.add(sel == 580 ? "209" : "208");
		args.add(sel == 600 ? "209" : "208");
		args.add(sel == 589 ? "209" : "208");
		args.add(sel == 562 ? "209" : "208");

		// 아툰 헤더 이미지 추가
		args.add(getAtunHeaderImg(acc.getTree_select4()));

		SC_NPCTalkReturn.sendList(pc, pc.getId(), "neitreese01", args);
	}

	/** 크리스터 탭 정보 (neitreese02) */
	public static void Info2(L1PcInstance pc) {
		if (invalid(pc)) return;
		Account acc = pc.getAccount();
		List<String> args = prepareBaseArgs(acc);

		// 크리스터 스킬 활성화 상태 (241~244, 220~221)
		args.add(acc.getTree_crim1() != 0 ? "242" : "241");
		args.add(acc.getTree_crim2() != 0 ? "244" : "243");
		args.add(acc.getTree_cri() != 0 ? "221" : "220");

		// 크리스터 스킬 선택 여부
		int sel = acc.getTree_select2();
		args.add(sel == 379 ? "209" : "208");
		args.add(sel == 394 ? "209" : "208");
		args.add(sel == 605 ? "209" : "208");

		args.add(getAtunHeaderImg(acc.getTree_select4()));

		SC_NPCTalkReturn.sendList(pc, pc.getId(), "neitreese02", args);
	}

	/** 이실로테 탭 정보 (neitreese03) */
	public static void Info3(L1PcInstance pc) {
		if (invalid(pc)) return;
		Account acc = pc.getAccount();
		List<String> args = prepareBaseArgs(acc);

		// 이실로테 스킬 활성화 상태 (235~240)
		args.add(acc.getTree_ecil1() != 0 ? "236" : "235");
		args.add(acc.getTree_ecil2() != 0 ? "238" : "237");
		args.add(acc.getTree_ecil3() != 0 ? "240" : "239");

		// 이실로테 스킬 선택 여부
		int sel = acc.getTree_select3();
		args.add(sel == 420 ? "209" : "208");
		args.add(sel == 422 ? "209" : "208");
		args.add(sel == 426 ? "209" : "208");

		args.add(getAtunHeaderImg(acc.getTree_select4()));

		SC_NPCTalkReturn.sendList(pc, pc.getId(), "neitreese03", args);
	}

	/** 아툰 탭 정보 (inshop01) */
	public static void Info5(L1PcInstance pc) {
		if (invalid(pc)) return;
		Account acc = pc.getAccount();
		List<String> args = prepareBaseArgs(acc);

		// 아툰 스킬 활성화 상태 (248~253)
		args.add(acc.getTree_atun1() != 0 ? "249" : "248");
		args.add(acc.getTree_atun2() != 0 ? "251" : "250");
		args.add(acc.getTree_atun3() != 0 ? "253" : "252");

		// 아툰 스킬 선택 여부
		int sel = acc.getTree_select4();
		args.add(sel == 20796 ? "209" : "208");
		args.add(sel == 19052 ? "209" : "208");
		args.add(sel == 19055 ? "209" : "208");

		args.add(getAtunHeaderImg(sel));

		SC_NPCTalkReturn.sendList(pc, pc.getId(), "inshop01", args);
	}

	/** 메인 요약 정보 (neitree) */
	public static void Info4(L1PcInstance pc) {
		if (invalid(pc)) return;
		Account acc = pc.getAccount();

		HeaderInfo h1 = getJowHeader(acc.getTree_select());
		HeaderInfo h2 = getKristerHeader(acc.getTree_select2());
		HeaderInfo h3 = getIsilotteHeader(acc.getTree_select3());
		String h4 = getAtunHeaderImg(acc.getTree_select4());

		String[] args = {
				h1.img, h2.img, h3.img, h4, h1.name, h2.name, h3.name
		};

		SC_NPCTalkReturn.send(pc, pc.getId(), "neitree", args);
	}

	// --- 비즈니스 로직 도우미  ---

	/** 공통 헤더 인자 6개를 생성하는 메서드 */
	private static List<String> prepareBaseArgs(Account acc) {
		List<String> args = new ArrayList<>();
		HeaderInfo h1 = getJowHeader(acc.getTree_select());
		HeaderInfo h2 = getKristerHeader(acc.getTree_select2());
		HeaderInfo h3 = getIsilotteHeader(acc.getTree_select3());

		args.add(h1.img);
		args.add(h2.img);
		args.add(h3.img);
		args.add(h1.name);
		args.add(h2.name);
		args.add(h3.name);
		return args;
	}

	private static HeaderInfo getJowHeader(int sel) {
		switch (sel) {
		case 569: return new HeaderInfo("224", "[조우 활성화 : 아이스 퀘이크]");
		case 580: return new HeaderInfo("225", "[조우 활성화 : 파이어 퀘이크]");
		case 600: return new HeaderInfo("226", "[조우 활성화 : 파이널 붐]");
		case 589: return new HeaderInfo("227", "[조우 활성화 : 아이스 스트라이크]");
		case 562: return new HeaderInfo("228", "[조우 활성화 : 크리티컬 레인보우]");
		default:  return new HeaderInfo("222", "[조우 활성화 : 없음]");
		}
	}

	private static HeaderInfo getKristerHeader(int sel) {
		switch (sel) {
		case 379: return new HeaderInfo("233", "[크리스터 활성화 : 크리티컬 샷]");
		case 394: return new HeaderInfo("234", "[크리스터 활성화 : 크리티컬 차지]");
		case 605: return new HeaderInfo("229", "[크리스터 활성화 : 크리티컬 쇼크]");
		default:  return new HeaderInfo("222", "[크리스터 활성화 : 없음]");
		}
	}

	private static HeaderInfo getIsilotteHeader(int sel) {
		switch (sel) {
		case 420: return new HeaderInfo("230", "[이실로테 활성화 : 스완 붐]");
		case 422: return new HeaderInfo("231", "[이실로테 활성화 : 스완 웨이브]");
		case 426: return new HeaderInfo("232", "[이실로테 활성화 : 스완 라이징]");
		default:  return new HeaderInfo("222", "[이실로테 활성화 : 없음]");
		}
	}

	private static String getAtunHeaderImg(int sel) {
		switch (sel) {
		case 20796: return "245";
		case 19052: return "246";
		case 19055: return "247";
		default:    return "222";
		}
	}

	private static boolean invalid(L1PcInstance pc) {
		return pc == null || pc.isDead() || pc.getAccount() == null;
	}

	private static class HeaderInfo {
		String img, name;
		HeaderInfo(String img, String name) { this.img = img; this.name = name; }
	}
}