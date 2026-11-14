#!/usr/bin/env python3
"""Full 1711 Java source broadcast call-site audit using tree-sitter-java."""
import hashlib,json,pathlib,re,zipfile,collections
from tree_sitter import Language,Parser
import tree_sitter_java
ROOT=pathlib.Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/CTransport_SharedBroadcast_NoLegacy_FULL_SRC_20261115.zip"
EXPECTED="006f9c53143461f0bff5b5d0e6edd588644ef09efe4ca7b791a4724426d18efb"
OUT=ROOT/"reports/broadcast_full_audit_20261115"
PARSER=Parser(Language(tree_sitter_java.language()))
SC="src/l1j/server/server/model/network/core/sc/packet/"
NET=re.compile(r'\b(?:sendPackets\w*|sendPacket\w*|broadcast\w*|sendToReceivers\w*|sendToAll|sendAll)\s*\(',re.I)
TARGET=re.compile(r'getVisiblePlayer|borrowVisiblePlayerFast|getAllPlayers|L1PcInstance|players|Players|members|Members|pcList|pcArray|recipients|nearby|clan|party|world',re.I)
SEND=re.compile(r'\b(?:sendPackets\w*|sendPacket\w*|sendToReceivers\w*|broadcast\w*|sendAll|\.send\s*\()',re.I)
EFFECT=re.compile(r'\b(?:addKnownObject|removeKnownObject|addObject|removeObject|set[A-Z]\w*|delete[A-Z]\w*|update[A-Z]\w*|commit\w*)\s*\(')
DISPATCH=re.compile(r'getVisiblePlayer|borrowVisiblePlayerFast|getAllPlayers|sendPacketsToAll|broadcastPacket|broadcastAll',re.I)
def text(s,n):return s[n.start_byte:n.end_byte].decode('utf8','replace')
def scope(n):
    while n is not None:
        if n.type in ("method_declaration","constructor_declaration"):return n
        n=n.parent
    return None
def method(s,n):
    n=scope(n)
    if n is None:return "(initializer)"
    x=n.child_by_field_name('name')
    return text(s,x) if x else "(anonymous)"
def lineno(s,n):return s.count(b'\n',0,n.start_byte)+1
def collect(root):
    stack=[root];loops=[];calls=[]
    while stack:
        node=stack.pop()
        if node.type in ("enhanced_for_statement","for_statement","while_statement","do_statement"):loops.append(node)
        elif node.type=="method_invocation":calls.append(node)
        stack.extend(node.children)
    return loops,calls
def main():
    if hashlib.sha256(SRC.read_bytes()).hexdigest()!=EXPECTED:raise ValueError('wrong latest SRC SHA')
    src_total=non_sc=errors=0;loops=[];dispatcher=[];api_count=0;code_lines=0
    with zipfile.ZipFile(SRC) as z:
        if z.testzip() is not None:raise ValueError('ZIP failed CRC')
        for p in sorted(n for n in z.namelist() if n.startswith('src/') and n.endswith('.java')):
            src_total+=1
            if p.startswith(SC):continue
            non_sc+=1;s=z.read(p)
            code_lines+=s.count(b'\n')
            if not any(x in s for x in (b'VisiblePlayer',b'Players',b'sendPackets',b'broadcast',b'Broadcast',b'forEach')):continue
            parsed=PARSER.parse(s)
            if parsed.root_node.has_error:errors+=1
            all_loops,calls=collect(parsed.root_node)
            for node in all_loops:
                body=text(s,node)
                if len(body)>250000:continue
                h=body[:min(body.find('{') if '{' in body else 300,400)]
                if not SEND.search(body):continue
                if not (TARGET.search(h) or 'getVisiblePlayer' in body[:400] or 'L1PcInstance' in body[:400]):continue
                hits=re.findall(r'(?:[.\w]+\.)?(?:sendPackets\w*|sendPacket\w*|broadcast\w*|sendAll|sendToReceivers\w*)\s*\([^;]{0,190}',body)
                effects=sorted(set(EFFECT.findall(body)))
                score=(4 if DISPATCH.search(h+" "+body[:500]) else 0)+(2 if 'L1PcInstance' in h or 'L1PcInstance' in body[:450] else 0)+(2 if re.search(r'new\s+(?:S_|SC_|R)\w+',body) else 0)+(1 if hits else 0)
                loops.append(dict(file=p,line=lineno(s,node),end=lineno(s,node)+body.count('\n'),method=method(s,node),score=score,
                                  header=' '.join(h.split())[:250],
                                  sends=[' '.join(v.split())[:190] for v in hits[:8]],
                                  effects=effects[:12],excerpt=' '.join(body.split())[:450]))
            for node in calls:
                t=text(s,node)
                if not DISPATCH.search(t):continue
                dispatcher.append(dict(file=p,line=lineno(s,node),method=method(s,node),call=' '.join(t.split())[:280]))
                api_count+=1
    loops.sort(key=lambda x:(-x['score'],x['file'],x['line']))
    byfile=collections.Counter(x['file'] for x in loops)
    md=["# RKern 전체 SRC 기존 방송 경로 감사","",
        "검사 소스 SHA256: "+EXPECTED,
        "전체 Java: "+str(src_total)+", SC 패킷 제외 Java: "+str(non_sc),
        "파서 오류 포함 파일: "+str(errors),
        "SC 외 반복 송신 후보: "+str(len(loops))+"곳, "+str(len(byfile))+"개 소스",
        "AOI·전체 방송 관련 API 호출: "+str(api_count)+"곳",
        "",
        "이 목록은 후보이며 실제 수신자별 직렬화와 게임 상태 변경을 구분하여 수정해야 합니다.",
        "",
        "## 상위 200개 후보","",
        "| 소스:줄 | 함수 | 점수 | 수신자 루프 | 송신호출 | 상태변경 검사 |",
        "|---|---|---:|---|---|---|"]
    for x in loops[:200]:
        clean=lambda v:v.replace("|","/").replace(chr(96),"'")
        row="| "+clean(x['file']+":"+str(x['line']))+" | "+clean(x['method'])+" | "+str(x['score'])+" | "+clean(x['header'][:120])+" | "+clean("; ".join(x['sends'])[:140])+" | "+clean(",".join(x['effects'])[:80])+" |"
        md.append(row)
    md+=["","## 파일별 후보",""]
    for p,v in byfile.most_common():md.append("- "+p+": "+str(v))
    md+=["","## 주의","",
         "정적 AST 감사는 호출된 메서드의 내부 구현과 런타임 데이터를 증명하지 않습니다.",
         "수신자별 다른 내용의 방송, 게임 객체 등록·삭제 작업은 공통 공유 버퍼로 직접 전환하면 안 됩니다."]
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/"AUDIT_KO.md").write_text("\n".join(md)+"\n",encoding="utf8")
    (OUT/"audit.json").write_text(json.dumps(dict(baseline_sha256=EXPECTED,java=src_total,non_sc_java=non_sc,parse_errors=errors,
                                                 candidate_loops=loops,dispatch_calls=dispatcher,by_file=byfile.most_common()),
                                             ensure_ascii=False,indent=2)+"\n",encoding="utf8")
    print("JAVA",src_total,"NON_SC",non_sc,"PARSE_ERRORS",errors,"CANDIDATE_LOOPS",len(loops),"SOURCE_FILES",len(byfile),"DISPATCH_CALLS",api_count)
    print("HOT FILES")
    for p,c in byfile.most_common(65):print("FILE",c,p)
    print("TOP CANDIDATES")
    for x in loops[:90]:print("LOOP",x['score'],x['file'],x['line'],x['method'],x['header'][:110],str(x['sends'])[:150],str(x['effects'])[:100])
if __name__=="__main__":main()
