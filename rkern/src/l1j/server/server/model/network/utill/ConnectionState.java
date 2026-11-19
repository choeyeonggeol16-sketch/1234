package l1j.server.server.model.network.utill;

/**
 * 클라이언트의 연결 및 진행 상태를 나타내는 Enum
 */
public enum ConnectionState {
	CONNECTED,     // 소켓 연결만 성공한 최초 상태 (로그인 및 아이디 입력 전)
	AUTHENTICATED, // 계정 인증(로그인)은 완료되었으나, 서버 선택 또는 캐릭터 선택창에 머물러 있는 상태
	IN_GAME,       // 로딩이 완전히 끝나고 실제 필드에서 플레이 중인 정상 상태
	OUT_GAME,      // 로그아웃 상태고 캐릭터 선택창으로 나간 상태
	DISCONNECTED;  // 네트워크 연결이 완전히 끊어진 상태
}