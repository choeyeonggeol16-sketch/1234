/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;

public class S_ServerVersion extends ServerBasePacket {
	/*
	* main.Server -> main.Client [ opc.116 len.32 ] 0.050ms
	* ##########################################################################
	* 0000 # 74 00 14 ad 3b 01 00 91 3a 01 00 01 ee 00 00 dd t...;...:....... 0010 #
	* 3a 01 00 4d ff c1 48 00 00 03 92 c5 3d 37 1e 9e :..M..H.....=7..
	* ##########################################################################
	*/
	public S_ServerVersion() {
        writeC(Opcodes.S_OPCODE_SERVERVERSION);
        writeC(0x00); // must be
        
        // 310 버전
        writeC(0x25); // must be limit version
        writeD(0x009a01c9); // server verion
        writeD(0x000189c5); // cache verion
        writeD(0x77cef9f0); // auth verion
        writeD(0x009a21d1); // npc verion
        writeD(0x4cb4e37c); // start time
        writeC(0x00); // unk 1
        writeC(0x00); // unk 2
        writeC(0x00); //
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
