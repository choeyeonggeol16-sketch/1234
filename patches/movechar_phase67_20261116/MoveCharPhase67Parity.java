package l1j.server.server.model.network.core.broadcast;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_MoveChar;

public final class MoveCharPhase67Parity {
    private static final Field WIRE;
    static {
        try {
            WIRE=CAsyncSendConstBuffer.class.getDeclaredField("wire");
            WIRE.setAccessible(true);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
    private static byte[] bytes(CAsyncSendConstBuffer shared) throws Exception {
        return (byte[])WIRE.get(shared);
    }
    private static void check(int id,int x,int y,int heading) throws Exception {
        byte[] old = SC_MoveChar.broadcastWire(id,x,y,heading);
        try (var shared=CAsyncSendConstBufferExceptSelf.forMove(
                id, Opcodes.S_MOVE_OBJECT,id,x,y,heading)) {
            byte[] actual=bytes(shared);
            if (!Arrays.equals(old,actual))
                throw new AssertionError("move bytes mismatch id="+id+" x="+x+
                        " y="+y+" h="+heading);
            if (actual.length!=17 || actual[0]!=17 || actual[1]!=0)
                throw new AssertionError("bad length header");
            if (!shared.exclude(id) || shared.exclude(id+1))
                throw new AssertionError("self exclusion changed");
        }
    }
    public static void main(String[] args) throws Exception {
        final int[] edges={0,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,
                1, 255, 256, 65535,0x12345678,0x87654321};
        int n=0;
        for (int h=0;h<8;h++)
            for (int id:edges)for (int x:edges)for (int y:edges) {
                check(id,x,y,h);
                n++;
            }
        Random random=new Random(0x41672026L);
        for (int i=0;i<100000;i++) {
            check(random.nextInt(),random.nextInt(),random.nextInt(),i&7);
            n++;
        }
        byte[] original=SC_MoveChar.broadcastWire(77,345,678,3);
        try(var ordinary=new CAsyncSendConstBuffer(original,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            byte[] internal=bytes(ordinary);
            if (internal == original) throw new AssertionError("ordinary constructor must copy");
            byte before=internal[3];
            original[3]=(byte)(original[3]+1);
            if (internal[3]!=before) throw new AssertionError("ordinary frame alias");
        }
        var closed=CAsyncSendConstBufferExceptSelf.forMove(99,Opcodes.S_MOVE_OBJECT,99,0,0,0);
        closed.close();
        closed.close();
        try {
            closed.send(null, 4);
            throw new AssertionError("closed send accepted");
        } catch (IllegalStateException expected) {}
        System.out.println("MOVECHAR_PHASE67_WIRE_PARITY_OK "+n);
        System.out.println("MOVECHAR_PHASE67_CLONE_AND_CLOSE_OK");
    }
}
