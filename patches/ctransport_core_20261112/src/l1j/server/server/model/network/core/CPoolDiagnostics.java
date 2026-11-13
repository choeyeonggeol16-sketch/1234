package l1j.server.server.model.network.core;

/** Request-driven, bounded observations. Never reclaims or closes anything. */
public final class CPoolDiagnostics {
    private record Sample(long at,long javaUsed,long nativeUsed,long holders) {}
    private final Sample[] history=new Sample[60];
    private final long[] javaPeak=new long[4],nativePeak=new long[2];
    private int cursor,count;
    private long quietSince,lastJava=-1,lastNative=-1;
    private boolean quietTracking;
    public synchronized String sample(long now,long[][] javaPool,long[] nativePool,long[] ownership) {
        if(javaPool.length!=4||(ownership.length!=6&&ownership.length!=7))throw new IllegalArgumentException("pool diagnostic shape");
        boolean available=nativePool.length>=18&&nativePool[8]==0x4354504c00010000L;
        boolean faults=available&&nativePool.length>=24&&nativePool[18]==0x4354464100010000L;
        StringBuilder b=new StringBuilder("풀·회수 진단 (강제 회수 없음)\n");
        b.append("Java: 슬롯크기 / 사용중 / 용량 / 관측최고 / 누적대여 / 누적반납 / 확보direct bytes / 실패\n");
        String[] names={"RX4K","RX64K","TX16K","TX64K"};long javaUsed=0,nativeUsed=0;
        for(int i=0;i<4;i++){
            long[] r=javaPool[i];if(r.length!=7)throw new IllegalArgumentException("pool row");
            javaUsed+=r[2];javaPeak[i]=Math.max(javaPeak[i],r[2]);
            b.append(names[i]).append(": ").append(r[0]).append(" / ").append(r[2]).append(" / ").append(r[1])
                .append(" / ").append(javaPeak[i]).append(" / ").append(r[3]).append(" / ").append(r[4])
                .append(" / ").append(r[5]).append(" / ").append(r[6]).append('\n');
        }
        if(available){
            b.append("Native: 사용중 / 용량 / 관측최고 / 누적대여 / 최종반납\n");
            for(int i=0;i<2;i++){
                int at=9+i*4;long used=nativePool[at+1];nativeUsed+=used;nativePeak[i]=Math.max(nativePeak[i],used);
                b.append(i==0?"8K: ":"16K: ").append(used).append(" / ").append(nativePool[at]).append(" / ")
                    .append(nativePeak[i]).append(" / ").append(nativePool[at+2]).append(" / ").append(nativePool[at+3]).append('\n');
            }
            b.append("Native slab 확보 bytes=").append(nativePool[17]).append(" 획득실패=").append(nativePool[1])
                .append(" 초기화실패=").append(nativePool[2]).append(" 풀무결성오류=").append(nativePool[3]).append('\n');
            b.append("Native close 시작/terminal=").append(nativePool[6]).append('/').append(nativePool[7]).append('\n');
            if(faults)b.append("Native 격리 슬롯 8K/16K=").append(nativePool[19]).append('/').append(nativePool[20])
                .append(" (재사용 제외, 사용중 수에서 제외)\n프로세스 MemoryObject 참조오류=").append(nativePool[21])
                .append(" 마지막 객체ID=").append(nativePool[22]).append(" 감소 전 참조수=").append(nativePool[23]).append('\n');
        }else b.append("Native 풀 상세: DLL 확장 측정 미지원/종료 중 — 0으로 판정하지 않음\n");
        long holders=0;for(long v:ownership)holders+=v;
        b.append(ownership.length==7?"[Java 보유소켓,Java cleanup대기,native active,native retiring,공유입력생산자,송신요청대기,참조검사기]\n":"[Java 보유소켓,Java cleanup대기,native active,native retiring,공유입력생산자,송신요청대기]\n")
            .append(java.util.Arrays.toString(ownership)).append('\n');
        boolean quiet=available&&holders==0;
        if(!quiet)quietTracking=false;
        else if(!quietTracking||lastJava!=javaUsed||lastNative!=nativeUsed){quietSince=now;quietTracking=true;}
        lastJava=javaUsed;lastNative=nativeUsed;
        if(!available)b.append("판정: 측정 불완전\n");
        else if(javaUsed+nativeUsed==0)b.append("판정: 현재 미반납 버퍼 없음\n");
        else if(!quiet)b.append("판정: 연결·작업·생산자가 보유 중 (누수 판정 안 함)\n");
        else if(now-quietSince>=60_000_000_000L)b.append("판정: 미회수 의심 — 관측된 보유자 없이 같은 잔여량 60초 이상; 확정 누수 아님\n");
        else b.append("판정: 회수 확인 대기\n");
        if(faults&&(nativePool[19]+nativePool[20]>0||nativePool[21]>0))
            b.append("무결성 오류 기록 있음: 격리 슬롯·객체 참조오류를 정상 대여/회수 판정과 별도로 확인하세요.\n");
        history[cursor++%history.length]=new Sample(now,javaUsed,available?nativeUsed:-1,holders);count=Math.min(count+1,history.length);
        b.append("확보된 풀 메모리는 재사용을 위해 유지됩니다. 사용중/보유량과 구분하세요.\n")
            .append("활성 작업 중 대여·반납 카운터는 순간 차이가 날 수 있습니다. 최고값은 조회 시 관측값입니다.\n")
            .append("최근 관측(최대60, 오래된 순): ageSec / Java사용 / Native사용(-1=미지원) / 보유지표합\n");
        for(int i=0;i<count;i++){Sample s=history[(cursor-count+i)%history.length];b.append(Math.max(0,(now-s.at)/1_000_000_000L))
            .append(" / ").append(s.javaUsed).append(" / ").append(s.nativeUsed).append(" / ").append(s.holders).append('\n');}
        return b.toString();
    }
    private static final long REFERENCE_MARKER=0x4354524600010000L;
    private static final int REASONS=31,STRIDE=12+REASONS*3;
    private static final String[] REFERENCE_NAMES={
      "ORT_ANONYMOUS","ORT_CREATE_DELETE","ORT_TIMER","ORT_IO_RETAIN","ORT_IOCP_MESSAGE_RETAIN",
      "ORT_OBJECT_MAP","ORT_OBJECT_NAME_MAP","ORT_OBJECT_INDEX","ORT_OBJECT_STORAGE","ORT_FIND_OBJECT",
      "ORT_TL_MAP","ORT_SHARED_CONTAINER","ORT_SMART_ID","ORT_SERIALIZED_WORLD_THREAD","ORT_TEMP_USE",
      "ORT_CACHED","ORT_FUNCTION_LOCAL","ORT_FUNCTION_CHAIN","ORT_EVENT_RETAIN","ORT_INVENTORY_CHANGE_MAP",
      "ORT_IO_SOCKET_WRITE","ORT_NPC_RELATED","ORT_VERIFIER_RETAIN","ORT_CLASS_MEMBER","ORT_SOCKET_MAP",
      "ORT_ACCOUNT_MAP","ORT_USER_SOCKET_MAP","ORT_USER_SOCKET_PLAY_MAP","ORT_USER_SOCKET_CHAR_MAP",
      "ORT_SERIALIZED_MESSAGE","ORT_TDD_USE"};
    private static final String[] REFERENCE_FILES={"unknown","CTransport.cpp","cio_core.hpp","execution_serializer.hpp","user_registry.hpp","object_map.hpp","timer_wheel.hpp","native-test"};
    private static boolean validReferences(long[] r){return r.length>=16&&r[0]==REFERENCE_MARKER&&r[9]==REASONS&&r[10]==STRIDE&&r[11]==16&&r[8]>=0&&r[8]<=16&&r.length>=16+r[8]*STRIDE;}
    public static long referenceActive(long[] r){return validReferences(r)?r[2]:0;}
    private static String referenceSite(long packed){int file=(int)(packed>>>32);long line=packed&0xffffffffL;return(file>=0&&file<REFERENCE_FILES.length?REFERENCE_FILES[file]:"unknown")+":"+line;}
    public static String referenceReport(long[] r){
        if(!validReferences(r))return "\n종료 참조 진단: DLL 확장 미지원/종료 중\n";
        StringBuilder b=new StringBuilder("\n종료 참조 진단 (미회수 의심 관측, 강제 해제 없음)\n");
        b.append("검사기 시작/진행/검사횟수/잔여참조의심/생략/취소=").append(r[1]).append('/').append(r[2]).append('/').append(r[3]).append('/').append(r[4]).append('/').append(r[5]).append('/').append(r[6])
         .append(" 진행 상한=").append(r[15]).append("\n프로세스 timer 상태오류=").append(r[12]).append(" 종류별 과반납=").append(r[13]).append('\n');
        int count=(int)r[8];for(int n=Math.max(0,count-8);n<count;n++){
            int at=16+n*STRIDE;long state=r[at+8],pending=r[at+9]>>>32;
            String status=state==0?"추가 참조 없음":state==1?"잔여 참조 의심":state==2?"검사 취소":"I/O·패킷·등록 정리 대기";
            b.append('#').append(r[at]).append(' ').append(status).append(" objectId=").append(r[at+2]).append(" socketNo=").append(r[at+3]).append(" connectionId=").append(r[at+4]).append(" account=").append(r[at+5])
             .append(" ref=").append(r[at+6]).append(" 검사기 제외=").append(r[at+7]).append(" timer=").append(r[at+9]&0xffffffffL).append(" pending=0x").append(Long.toHexString(pending)).append('\n');
            for(int kind=0;kind<REASONS;kind++){
                int slot=at+12+kind*3;long refs=r[slot],add=r[slot+1],release=r[slot+2];if(refs==0&&add==0&&release==0)continue;
                b.append("  ").append(REFERENCE_NAMES[kind]).append('=').append(refs).append(" add ").append(referenceSite(add)).append(" release ").append(referenceSite(release)).append('\n');
            }
        }
        return b.append("정상 I/O·패킷 보유와 원인 미확정 잔여 참조를 구분합니다. 검사기 자신의1개 참조는 잔여량에서 제외합니다.\n")
          .append("최근 기록 최대16개 중8개 표시. 위치는 L1 소스이며 RAII 반납은 소유 scope 위치입니다. 동시에 읽은 카운터는 순간 차이가 날 수 있습니다.\n").toString();
    }

}
