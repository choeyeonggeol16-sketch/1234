package l1j.server.server.model.network.buffer;
import java.util.Arrays;
public class WriteBuffer {
 private byte[] array;private int position;
 public WriteBuffer(int capacity){array=new byte[capacity];}
 private void ensure(int size){if(size>array.length)array=Arrays.copyOf(array,Math.max(size,array.length*2));}
 public void writeC(int value){writeByte((byte)value);}
 public void writeH(int value){writeByte((byte)value);writeByte((byte)(value>>>8));}
 public void writeBytes(byte[] b){ensure(position+b.length);System.arraycopy(b,0,array,position,b.length);position+=b.length;}
 public void writeByte(byte b){ensure(position+1);array[position++]=b;}
 public void writeByte(int index,byte b){ensure(index+1);array[index]=b;}
 public byte readByte(int index){return array[index];}
 public int position(){return position;}
 public void position(int p){position=p;}
 public byte[] toByteArray(){return Arrays.copyOf(array,position);}
}
