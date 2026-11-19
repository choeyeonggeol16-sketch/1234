package l1j.server.server;
public final class IdFactory { private static final IdFactory ONE=new IdFactory(); private int next=10000;
 public static IdFactory getInstance(){return ONE;} public int nextId(){return ++next;} }
