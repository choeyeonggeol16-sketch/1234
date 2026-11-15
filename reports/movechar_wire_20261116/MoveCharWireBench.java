import com.sun.management.ThreadMXBean;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Standalone benchmark of ONLY 17-byte MoveChar packet build+defensive copy. */
public final class MoveCharWireBench {
    private static final VarHandle LE_I = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LE_S = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    private static final byte OPCODE = 0x15; // identical across variants; benchmark does not depend on live Opcodes constant
    private static final byte[] TEMPLATE = {17, 0, OPCODE, 0,0,0,0, 0,0, 0,0, 0, (byte)129, 0,0,0,0};
    private static volatile byte[] escape;
    private static final ThreadMXBean ALLOC = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    static byte[] original(int id,int x,int y,int h) {
        return ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
            .putShort((short)17).put(OPCODE).putInt(id)
            .putShort((short)x).putShort((short)y).put((byte)h)
            .put((byte)129).putInt(0).array();
    }
    static byte[] manual(int id,int x,int y,int h) {
        byte[] b = new byte[17]; b[0]=17; b[2]=OPCODE;
        b[3]=(byte)id; b[4]=(byte)(id>>>8); b[5]=(byte)(id>>>16); b[6]=(byte)(id>>>24);
        b[7]=(byte)x; b[8]=(byte)(x>>>8); b[9]=(byte)y; b[10]=(byte)(y>>>8);
        b[11]=(byte)h; b[12]=(byte)129; return b;
    }
    static byte[] varHandle(int id,int x,int y,int h) {
        byte[] b = new byte[17]; b[0]=17; b[2]=OPCODE;
        LE_I.set(b,3,id); LE_S.set(b,7,(short)x); LE_S.set(b,9,(short)y);
        b[11]=(byte)h; b[12]=(byte)129; return b;
    }
    static byte[] template(int id,int x,int y,int h) {
        byte[] b = TEMPLATE.clone();
        LE_I.set(b,3,id); LE_S.set(b,7,(short)x); LE_S.set(b,9,(short)y);
        b[11]=(byte)h; return b;
    }
    static byte[] make(int which,int id,int x,int y,int h) {
        return switch (which) {
            case 0 -> original(id,x,y,h);
            case 1 -> manual(id,x,y,h);
            case 2 -> varHandle(id,x,y,h);
            case 3 -> template(id,x,y,h);
            case 4 -> manual(id,x,y,h); // trusted-ownership model, copy omitted
            default -> throw new IllegalArgumentException();
        };
    }
    static void validate() {
        int[] edges={0,1,-1,255,256,65535,Integer.MIN_VALUE,Integer.MAX_VALUE,0x12345678};
        int n=0;
        for(int a:edges)for(int b:edges)for(int h=0;h<8;h++){
            byte[] expected=original(a,b,~b,h);
            for(int w=1;w<=4;w++)if(!Arrays.equals(expected,make(w,a,b,~b,h)))
                throw new AssertionError("edge parity variant="+w);
            n++;
        }
        int s=0x1af45237;
        for(int i=0;i<100000;i++) {
            s^=s<<13; s^=s>>>17; s^=s<<5;
            int id=s, x=s>>>9, y=~s, h=i&7;
            byte[] expected=original(id,x,y,h);
            for(int w=1;w<=4;w++)if(!Arrays.equals(expected,make(w,id,x,y,h)))
                throw new AssertionError("random parity variant="+w+" i="+i);
            n++;
        }
        System.out.println("PARITY_CASES="+n+" variants=4 (17-byte identical)");
    }
    static void work(int which,int iterations) {
        for(int i=0;i<iterations;i++) {
            int id=i*1664525 + 1013904223;
            byte[] b=make(which,id,id>>>3,~id,i&7);
            // Simulate CAsyncSendConstBuffer defensive clone for existing paths.
            // Only hypothetical trusted ownership model omits the clone.
            escape=(which==4)?b:b.clone();
        }
    }
    public static void main(String[] args) {
        validate();
        if(!ALLOC.isThreadAllocatedMemorySupported())throw new AssertionError("ThreadMXBean unsupported");
        ALLOC.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().threadId();
        final int warmup=400000, runs=5, n=1000000;
        String[] names={"ByteBuffer+clone","manual+clone","VarHandle+clone","template+clone","trusted(no clone)"};
        for(int k=0;k<6;k++)for(int which=0;which<5;which++)work(which,warmup);
        for(int pass=0;pass<runs;pass++){
            for(int jj=0;jj<5;jj++){
                int which=(jj+pass*2)%5;
                long a=ALLOC.getThreadAllocatedBytes(thread);
                long start=System.nanoTime();
                work(which,n);
                long nanos=System.nanoTime()-start;
                long bytes=ALLOC.getThreadAllocatedBytes(thread)-a;
                System.out.printf("RUN name=%-17s pass=%d bytes_per_event=%.1f ns_per_event=%.2f%n",
                    names[which],pass,(double)bytes/n,(double)nanos/n);
            }
        }
    }
}