package l1j.server.server.model.network.core.broadcast;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;

/** Exactly reproduce phase67 action frame and demonstrate immutable ownership. */
public final class Phase68ActionParity {
    private static final Field WIRE;
    static {
        try {
            WIRE=CAsyncSendConstBuffer.class.getDeclaredField("wire");
            WIRE.setAccessible(true);
        } catch (ReflectiveOperationException ex) { throw new ExceptionInInitializerError(ex); }
    }
    private static byte[] wire(CAsyncSendConstBuffer source) throws Exception {
        return (byte[])WIRE.get(source);
    }
    private static byte[] old(int id,int action) {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short)8).put((byte)Opcodes.S_ACTION)
                .putInt(id).put((byte)action).array();
    }
    private static void check(int id,int action) throws Exception {
        byte[] expected=old(id,action);
        byte[] exported=SC_DoActionGFX.broadcastWire(id,action);
        if (!Arrays.equals(expected,exported))
            throw new AssertionError("phase67 broadcastWire changed unexpectedly");
        try(var shared=CAsyncSendConstBufferExceptSelf.forAction(
                id,Opcodes.S_ACTION,id,action)) {
            byte[] actual=wire(shared);
            if (!Arrays.equals(expected,actual))throw new AssertionError(
                    "S_ACTION parity mismatch id="+id+" action="+action);
            if (!shared.exclude(id)||shared.exclude(id+1))
                throw new AssertionError("except self mismatch");
        }
    }
    public static void main(String[] ignored) throws Exception {
        int[] edges={0,1,-1,255,256,65535,Integer.MIN_VALUE,Integer.MAX_VALUE,
            0x12345678,0x87654321};
        int n=0;
        for(int id:edges)for(int action:edges){ check(id,action);n++; }
        Random rng=new Random(0x68004141L);
        for(int i=0;i<200000;i++){
            check(rng.nextInt(),rng.nextInt());
            n++;
        }
        byte[] data=old(0x12345678,7);
        try(var ordinary=new CAsyncSendConstBuffer(data,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            byte[] stored=wire(ordinary);
            if(stored==data)throw new AssertionError("ordinary constructor aliased mutable caller");
            byte val=stored[3];
            data[3]^=0x7f;
            if(stored[3]!=val)throw new AssertionError("ordinary defensive clone removed");
        }
        var closed=CAsyncSendConstBufferExceptSelf.forAction(99,Opcodes.S_ACTION,99,1);
        closed.close();closed.close();
        try{
            closed.send(null,1);
            throw new AssertionError("closed send accepted");
        }catch(IllegalStateException expected){}
        System.out.println("PHASE68_SACTION_200100_WIRE_PARITY_PASS "+n);
        System.out.println("PHASE68_SACTION_CLONE_AND_CLOSED_PASS");
    }
}
