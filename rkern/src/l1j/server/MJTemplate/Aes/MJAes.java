package l1j.server.MJTemplate.Aes;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class MJAes {
	private byte[] m_keyBytes;
	public MJAes(String key){
		m_keyBytes = new byte[16];
		try{
			byte[] keyBytes = key.getBytes("UTF-8");
			System.arraycopy(keyBytes, 0, m_keyBytes, 0, 16);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public String encrypt(String source) throws Exception{
		byte[] buff = source.getBytes("UTF-8");
		buff = encrypt(buff);
		return fromHex(buff);
	}
	
	public byte[] encrypt(final byte[] buff) throws Exception{
		SecretKeySpec skeySpec = new SecretKeySpec(m_keyBytes, "AES");
		Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
		return cipher.doFinal(addPadding(buff));
	}
	
	public String decrypt(final String source) throws Exception{
		SecretKeySpec skeySpec = new SecretKeySpec(m_keyBytes, "AES");
		Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, skeySpec);
		byte[] dArray = removePadding(cipher.doFinal(toBytes(source)));
		return new String(dArray, "UTF-8");
	}
	
	public byte[] decrypt(final byte[] buff) throws Exception{
		SecretKeySpec skeySpec = new SecretKeySpec(m_keyBytes, "AES");
		Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, skeySpec);
		return removePadding(cipher.doFinal(buff));
	}
	
	private byte[] removePadding(final byte[] pBytes){
		int pCount = pBytes.length;
		int idx = 0;
		boolean loop = true;
		while(loop){
			if(idx == pCount || pBytes[idx] == 0x00){
				loop = false;
				--idx;
			}
			++idx;
		}
		
		byte[] tBytes = new byte[idx];
		System.arraycopy(pBytes,  0,  tBytes,  0, idx);
		return tBytes;
	}
	
	private byte[] addPadding(final byte[] pBytes){
		int pCount = pBytes.length;
		int tCount = pCount + (16 - (pCount % 16));
		byte[] tBytes = new byte[tCount];
		System.arraycopy(pBytes, 0, tBytes, 0, pCount);
		return tBytes;
	}
	
	public static byte[] toBytes(final String pString){
		StringBuffer buff = new StringBuffer(pString);
		int bCount = buff.length() / 2;
		byte[] bArray = new byte[bCount];
		for(int bIndex = 0; bIndex < bCount; ++bIndex)
			bArray[bIndex] = (byte)Long.parseLong(buff.substring(2 * bIndex, (2 * bIndex) + 2), 16);
		return bArray;
	}
	
	public static String fromHex(byte[] pBytes){
		int pCount = pBytes.length;
		StringBuilder sb = new StringBuilder((pCount * 2) + 8);
		for(int pIndex = 0; pIndex < pCount; ++pIndex){
			if(((int)pBytes[pIndex] & 0xff) < 0x10)
				sb.append(0);
			sb.append(Long.toString((int)pBytes[pIndex] & 0xff, 16));
		}
		return sb.toString();
	}
}
