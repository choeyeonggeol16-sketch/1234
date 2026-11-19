package l1j.server.server.model;
import java.util.*;import l1j.server.server.model.Instance.L1PcInstance;
public class L1Clan {
 public final List<Member> members=new ArrayList<>();
 public static final class Member {public L1PcInstance player;public Member(L1PcInstance p){player=p;}}
 public static final class OnlineMembers implements AutoCloseable,Iterable<Member> {
  private final List<Member> list;public OnlineMembers(List<Member> l){list=l;} public Iterator<Member> iterator(){return list.iterator();} public void close(){}
 }
 public OnlineMembers createOnlineMembers(){return new OnlineMembers(members);}
}