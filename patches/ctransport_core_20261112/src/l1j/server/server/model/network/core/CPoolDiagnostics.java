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
        if(javaPool.length!=4||ownership.length!=6)throw new IllegalArgumentException("pool diagnostic shape");
        boolean available=nativePool.length==18&&nativePool[8]==0x4354504c00010000L;
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
        }else b.append("Native 풀 상세: DLL 확장 측정 미지원/종료 중 — 0으로 판정하지 않음\n");
        long holders=0;for(long v:ownership)holders+=v;
        b.append("[Java 보유소켓,Java cleanup대기,native active,native retiring,공유입력생산자,송신요청대기]\n")
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
        history[cursor++%history.length]=new Sample(now,javaUsed,available?nativeUsed:-1,holders);count=Math.min(count+1,history.length);
        b.append("확보된 풀 메모리는 재사용을 위해 유지됩니다. 사용중/보유량과 구분하세요.\n")
            .append("활성 작업 중 대여·반납 카운터는 순간 차이가 날 수 있습니다. 최고값은 조회 시 관측값입니다.\n")
            .append("최근 관측(최대60, 오래된 순): ageSec / Java사용 / Native사용(-1=미지원) / 보유지표합\n");
        for(int i=0;i<count;i++){Sample s=history[(cursor-count+i)%history.length];b.append(Math.max(0,(now-s.at)/1_000_000_000L))
            .append(" / ").append(s.javaUsed).append(" / ").append(s.nativeUsed).append(" / ").append(s.holders).append('\n');}
        return b.toString();
    }
}
