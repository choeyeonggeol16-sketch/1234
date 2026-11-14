package LIN;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import l1j.server.Config;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Stream.ProtoInputStream;
import l1j.server.server.utils.MJHexHelper;



/**
 * SC_ALCHEMY_DESIGN_ACK (0x007B) — 인형합성 설계 데이터
 *  1 result_code enum [required]
 *  2 inputLists  repeated InputList
 *  3 outputLists repeated OutputList
 *  4 alchemies   repeated Alchemy
 *
 * alchemyInfo.dat(MJ 바이너리) 로드 → 싱글턴 → byte[] 4단 캐시.
 * 게임로직 게터(getAlchemy 등)도 여기서 제공.
 */
public class SC_ALCHEMY_DESIGN_ACK extends ProtoPacket {

	private static ArrayList<byte[]> _alchemyCaches;
	private static byte[] _alchemySameCache;
	private static LIN.SC_ALCHEMY_DESIGN_ACK _instance;

	private static ArrayList<byte[]> createAlchemyInfoCache(){
		ArrayList<byte[]> caches = new ArrayList<byte[]>(4);

		LIN.SC_ALCHEMY_DESIGN_ACK ack = new LIN.SC_ALCHEMY_DESIGN_ACK();
		ack.set_result_code(LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode.RC_ALCHEMY_LOAD_START);
		ack.set_inputLists(_instance.inputLists());
		caches.add(ack.encodeToBytes());

		ack = new LIN.SC_ALCHEMY_DESIGN_ACK();
		ack.set_result_code(LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode.RC_ALCHEMY_LOADING);
		ack.set_outputLists(_instance.outputLists());
		caches.add(ack.encodeToBytes());

		ack = new LIN.SC_ALCHEMY_DESIGN_ACK();
		ack.set_result_code(LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode.RC_ALCHEMY_LOADING);
		ack.set_alchemies(_instance.alchemies());
		caches.add(ack.encodeToBytes());

		ack = new LIN.SC_ALCHEMY_DESIGN_ACK();
		ack.set_result_code(LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode.RC_ALCHEMY_LOAD_FINISH);
		caches.add(ack.encodeToBytes());

		return caches;
	}

	private byte[] encodeToBytes(){
		ProtoWriter w = ProtoWriter.tl();
		body(w);
		return w.finishWithTrailer(msgId());
	}

	public static void reloadedAlchemyCaches(){
		_alchemyCaches = createAlchemyInfoCache();
	}

	public static void reloadedAlchemySameCaches(){
		LIN.SC_ALCHEMY_DESIGN_ACK ack = new LIN.SC_ALCHEMY_DESIGN_ACK();
		ack.set_result_code(LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode.RC_ALCHEMY_SAME_HASH_VAL);
		_alchemySameCache = ack.encodeToBytes();
	}

	/** alchemyInfo.dat 로드 + 캐시 구축 — 서버 시작 시 1회 */
	public static void load(){
		final LIN.SC_ALCHEMY_DESIGN_ACK message = new LIN.SC_ALCHEMY_DESIGN_ACK();
		_instance = message;
		ThreadPool.getInstance().scheduleRequired(new Runnable(){
			@Override
			public void run(){
				try {
					message.readFrom(ProtoInputStream.newInstance("./data/alchemyInfo.dat"));
					if(!message.isInitialized())
						throw new IllegalArgumentException(String.format("fail initialized alchemy data.(SC_AlchemyDesignAck) %d", message.getInitializeBit()));
					reloadedAlchemyCaches();
					reloadedAlchemySameCaches();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, 2000L);
	}

	public static void send(L1PcInstance pc, byte[] hash_val){
		try{
			if(MJHexHelper.compareArrays(Config.ALCHEMY_VERSION_HASH, hash_val, Config.ALCHEMY_VERSION_HASH.length)){
				l1j.server.server.packets.Protocol.Proto.sendRaw(pc, _alchemySameCache);
			}else if(Config.CRAFT_TRANSMIT_SAFELINE <= L1World.getInstance().getAllPlayersSize()){
				pc.sendPackets("현재 클라이언트의 인형 정보가 이전 버전임으로 모든 목록이 나오지 않을 수 있습니다.");
				pc.sendPackets("접속기에서 오류패치를 하신 후 다시 접속하면 모든 목록을 받아볼 수 있습니다.");
				l1j.server.server.packets.Protocol.Proto.sendRaw(pc, _alchemySameCache);
			}else{
                final l1j.server.server.model.network.core.session.CIOClient socket = pc.getNetConnection();
                if (socket == null || !socket.isConnected()) return;
                if (ThreadPool.getInstance().schedule(() -> {
                    if (socket.isConnected() && socket.getActiveChar() == pc)
                        socket.directSendPacketBytes(_alchemyCaches);
                }, 100L) == null) {
                    // Never silently leave the client's alchemy loading state
                    // waiting for a scheduled task that was not accepted.
                    LIN.SC_ALCHEMY_DESIGN_ACK.sendClosed(pc);
                }
			}
		}catch(Exception e){
			LIN.SC_ALCHEMY_DESIGN_ACK.sendClosed(pc);
			e.printStackTrace();
		}
	}

	private static LIN.SC_ALCHEMY_DESIGN_ACK closed(){
		LIN.SC_ALCHEMY_DESIGN_ACK ack = new LIN.SC_ALCHEMY_DESIGN_ACK();
		ack.set_result_code(LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode.RC_ALCHEMY_CURRENTLY_CLOSED);
		return ack;
	}

	// ────────────── 게임로직 접근 ──────────────

	public static LIN.SC_ALCHEMY_DESIGN_ACK getInstance(){
		return _instance;
	}

	public static LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy getAlchemy(int alchemyId){
		LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy> alchemies = _instance.alchemies();
		if(alchemies == null || alchemyId < 0 || alchemyId >= alchemies.size())
			return null;
		return alchemies.get(alchemyId);
	}

	public static LIN.SC_ALCHEMY_DESIGN_ACK.OutputList getOutputList(int alchemyId){
		LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.OutputList> outputs = _instance.outputLists();
		if(outputs == null || alchemyId < 0 || alchemyId >= outputs.size())
			return null;
		return outputs.get(alchemyId);
	}

	public static LIN.SC_ALCHEMY_DESIGN_ACK.Item selectOutputItem(int alchemyId){
		LIN.SC_ALCHEMY_DESIGN_ACK.OutputList outputs = getOutputList(alchemyId);
		if(outputs == null) return null;
		HashMap<Integer, LIN.SC_ALCHEMY_DESIGN_ACK.Item> output = outputs.item();
		if(output == null) return null;
		int select = l1j.server.MJTemplate.MJRnd.next(output.size());
		int idx = 0;
		LIN.SC_ALCHEMY_DESIGN_ACK.Item result = null;
		for(LIN.SC_ALCHEMY_DESIGN_ACK.Item item : output.values()){
			result = item;
			if(idx++ == select)
				break;
		}
		return result;
	}

	public static LIN.SC_ALCHEMY_DESIGN_ACK.InputList getInputList(int alchemyId){
		LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.InputList> inputs = _instance.inputLists();
		if(inputs == null || alchemyId < 0 || alchemyId >= inputs.size())
			return null;
		return inputs.get(alchemyId);
	}

	// ────────────── 데이터 ──────────────

	private LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode _result_code;
	private LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.InputList> _inputLists;
	private LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.OutputList> _outputLists;
	private LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy> _alchemies;

	private boolean _hasResultCode, _hasInputLists, _hasOutputLists, _hasAlchemies;

	private SC_ALCHEMY_DESIGN_ACK(){
	}

	// ────────────── setter/getter ──────────────

	public LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode result_code(){ return _result_code; }
	public void set_result_code(LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode val){ _hasResultCode = true; _result_code = val; }

	public LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.InputList> inputLists(){ return _inputLists; }
	public void set_inputLists(LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.InputList> val){ _hasInputLists = true; _inputLists = val; }
	public void add_inputLists(LIN.SC_ALCHEMY_DESIGN_ACK.InputList val){
		if(!_hasInputLists){
			_inputLists = new LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.InputList>();
			_hasInputLists = true;
		}
		_inputLists.add(val);
	}

	public LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.OutputList> outputLists(){ return _outputLists; }
	public void set_outputLists(LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.OutputList> val){ _hasOutputLists = true; _outputLists = val; }
	public void add_outputLists(LIN.SC_ALCHEMY_DESIGN_ACK.OutputList val){
		if(!_hasOutputLists){
			_outputLists = new LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.OutputList>();
			_hasOutputLists = true;
		}
		_outputLists.add(val);
	}

	public LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy> alchemies(){ return _alchemies; }
	public void set_alchemies(LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy> val){ _hasAlchemies = true; _alchemies = val; }
	public void add_alchemies(LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy val){
		if(!_hasAlchemies){
			_alchemies = new LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy>();
			_hasAlchemies = true;
		}
		_alchemies.add(val);
	}

	public void readFrom(ProtoInputStream input) throws IOException {
		while(!input.isAtEnd()){
			int tag = input.readTag();
			switch(tag){
			case 0x00000008:{   // result_code (varint)
				set_result_code(LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode.fromInt(input.readEnum()));
				break;
			}
			case 0x00000012:{   // inputLists (LEN)
				byte[] sub = input.readBytes();
				add_inputLists(LIN.SC_ALCHEMY_DESIGN_ACK.InputList.fromBinary(ProtoInputStream.newInstance(sub)));
				break;
			}
			case 0x0000001A:{   // outputLists (LEN)
				byte[] sub = input.readBytes();
				add_outputLists(LIN.SC_ALCHEMY_DESIGN_ACK.OutputList.fromBinary(ProtoInputStream.newInstance(sub)));
				break;
			}
			case 0x00000022:{   // alchemies (LEN)
				byte[] sub = input.readBytes();
				add_alchemies(LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.fromBinary(ProtoInputStream.newInstance(sub)));
				break;
			}
			default:{
				// unknown 필드 스킵 후 계속 — dat 확장 대비
				if(!input.skipField(tag))
					return;
				break;
			}
			}
		}
		if(_hasInputLists)
			_inputLists.sort((o1, o2) -> o1.id() - o2.id());
		if(_hasOutputLists)
			_outputLists.sort((o1, o2) -> o1.id() - o2.id());
		if(_hasAlchemies)
			_alchemies.sort((o1, o2) -> o1.id() - o2.id());
	}

	/** dat 로드 검증용 */
	public boolean isInitialized(){
		return _hasResultCode;
	}
	public long getInitializeBit(){
		return (_hasResultCode ? 0x1L : 0)
				| (_hasInputLists ? 0x2L : 0)
				| (_hasOutputLists ? 0x4L : 0)
				| (_hasAlchemies ? 0x8L : 0);
	}

	// ────────────── 직렬화 ──────────────

	@Override
	public int msgId() { return ProtoMsg.SC_ALCHEMY_DESIGN_ACK; }

	@Override
	protected void body(ProtoWriter w) {
		if (_hasResultCode) {
			w.int32(1, _result_code.toInt());
		}
		if (_hasInputLists) {
			for (LIN.SC_ALCHEMY_DESIGN_ACK.InputList val : _inputLists) {
				w.msg(2);
				val.writeTo(w);
				w.end();
			}
		}
		if (_hasOutputLists) {
			for (LIN.SC_ALCHEMY_DESIGN_ACK.OutputList val : _outputLists) {
				w.msg(3);
				val.writeTo(w);
				w.end();
			}
		}
		if (_hasAlchemies) {
			for (LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy val : _alchemies) {
				w.msg(4);
				val.writeTo(w);
				w.end();
			}
		}
	}

	// ────────────── ResultCode ──────────────

	public enum ResultCode{
		RC_ALCHEMY_LOAD_START(0),
		RC_ALCHEMY_LOADING(1),
		RC_ALCHEMY_LOAD_FINISH(2),
		RC_ALCHEMY_SAME_HASH_VAL(3),
		RC_ALCHEMY_INVALID_HASH_VAL(4),
		RC_ALCHEMY_CURRENTLY_CLOSED(5),
		RC_ERROR_UNKNOWN(9999);
		private int value;
		ResultCode(int val){
			value = val;
		}
		public int toInt(){
			return value;
		}
		public static LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode fromInt(int i){
			switch(i){
			case 0:  return RC_ALCHEMY_LOAD_START;
			case 1:  return RC_ALCHEMY_LOADING;
			case 2:  return RC_ALCHEMY_LOAD_FINISH;
			case 3:  return RC_ALCHEMY_SAME_HASH_VAL;
			case 4:  return RC_ALCHEMY_INVALID_HASH_VAL;
			case 5:  return RC_ALCHEMY_CURRENTLY_CLOSED;
			case 9999: return RC_ERROR_UNKNOWN;
			default: return null;
			}
		}
	}

	// ═══════════════════════════════════════════
	// 중첩 서브메시지
	// dat 역직렬화: 상위에서 readBytes() → 서브 스트림 주입
	// 직렬화: writeTo(ProtoWriter)
	// ═══════════════════════════════════════════

	public static class Item {
		private int _name_id;
		private int _icon;
		private boolean _hasNameId, _hasIcon;

		private Item(){}

		public int name_id(){ return _name_id; }
		public void set_name_id(int val){ _hasNameId = true; _name_id = val; }
		public boolean has_name_id(){ return _hasNameId; }

		public int icon(){ return _icon; }
		public void set_icon(int val){ _hasIcon = true; _icon = val; }
		public boolean has_icon(){ return _hasIcon; }

		static LIN.SC_ALCHEMY_DESIGN_ACK.Item fromBinary(ProtoInputStream input) throws IOException {
			LIN.SC_ALCHEMY_DESIGN_ACK.Item item = new LIN.SC_ALCHEMY_DESIGN_ACK.Item();
			while(!input.isAtEnd()){
				int tag = input.readTag();
				switch(tag){
				default: return item;
				case 0x00000008: item.set_name_id(input.readInt32()); break;
				case 0x00000010: item.set_icon(input.readInt32()); break;
				}
			}
			return item;
		}

		public void writeTo(ProtoWriter w){
			if (_hasNameId) w.int32(1, _name_id);
			if (_hasIcon)   w.int32(2, _icon);
		}
	}

	public static class InputList {
		private int _id;
		private HashMap<Integer, LIN.SC_ALCHEMY_DESIGN_ACK.Item> _item;
		private boolean _hasId, _hasItem;

		private InputList(){}

		public int id(){ return _id; }
		public void set_id(int val){ _hasId = true; _id = val; }
		public boolean has_id(){ return _hasId; }

		public HashMap<Integer, LIN.SC_ALCHEMY_DESIGN_ACK.Item> item(){ return _item; }
		public void add_item(LIN.SC_ALCHEMY_DESIGN_ACK.Item val){
			if(!_hasItem){
				_item = new HashMap<Integer, LIN.SC_ALCHEMY_DESIGN_ACK.Item>(8);
				_hasItem = true;
			}
			_item.put(val.name_id(), val);
		}
		public LIN.SC_ALCHEMY_DESIGN_ACK.Item item(int name_id){ return _item.get(name_id); }
		public boolean isInInput(int descId){ return _item.containsKey(descId); }

		static LIN.SC_ALCHEMY_DESIGN_ACK.InputList fromBinary(ProtoInputStream input) throws IOException {
			LIN.SC_ALCHEMY_DESIGN_ACK.InputList list = new LIN.SC_ALCHEMY_DESIGN_ACK.InputList();
			while(!input.isAtEnd()){
				int tag = input.readTag();
				switch(tag){
				default: return list;
				case 0x00000008: list.set_id(input.readInt32()); break;
				case 0x00000012:{
					byte[] sub = input.readBytes();
					list.add_item(LIN.SC_ALCHEMY_DESIGN_ACK.Item.fromBinary(ProtoInputStream.newInstance(sub)));
					break;
				}
				}
			}
			return list;
		}

		public void writeTo(ProtoWriter w){
			if (_hasId) w.int32(1, _id);
			if (_hasItem)
				for(LIN.SC_ALCHEMY_DESIGN_ACK.Item val : _item.values()){
					w.msg(2);
					val.writeTo(w);
					w.end();
				}
		}
	}

	public static class OutputList {
		private int _id;
		private HashMap<Integer, LIN.SC_ALCHEMY_DESIGN_ACK.Item> _item;
		private boolean _hasId, _hasItem;

		private OutputList(){}

		public int id(){ return _id; }
		public void set_id(int val){ _hasId = true; _id = val; }
		public boolean has_id(){ return _hasId; }

		public HashMap<Integer, LIN.SC_ALCHEMY_DESIGN_ACK.Item> item(){ return _item; }
		public void add_item(LIN.SC_ALCHEMY_DESIGN_ACK.Item val){
			if(!_hasItem){
				_item = new HashMap<Integer, LIN.SC_ALCHEMY_DESIGN_ACK.Item>(8);
				_hasItem = true;
			}
			_item.put(val.name_id(), val);
		}

		static LIN.SC_ALCHEMY_DESIGN_ACK.OutputList fromBinary(ProtoInputStream input) throws IOException {
			LIN.SC_ALCHEMY_DESIGN_ACK.OutputList list = new LIN.SC_ALCHEMY_DESIGN_ACK.OutputList();
			while(!input.isAtEnd()){
				int tag = input.readTag();
				switch(tag){
				default: return list;
				case 0x00000008: list.set_id(input.readInt32()); break;
				case 0x00000012:{
					byte[] sub = input.readBytes();
					list.add_item(LIN.SC_ALCHEMY_DESIGN_ACK.Item.fromBinary(ProtoInputStream.newInstance(sub)));
					break;
				}
				}
			}
			return list;
		}

		public void writeTo(ProtoWriter w){
			if (_hasId) w.int32(1, _id);
			if (_hasItem)
				for(LIN.SC_ALCHEMY_DESIGN_ACK.Item val : _item.values()){
					w.msg(2);
					val.writeTo(w);
					w.end();
				}
		}
	}

	public static class Alchemy {
		private int _id;
		private LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Input> _inputs;
		private LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output> _outputs;
		private boolean _hasId, _hasInputs, _hasOutputs;

		private Alchemy(){}

		public int id(){ return _id; }
		public void set_id(int val){ _hasId = true; _id = val; }
		public boolean has_id(){ return _hasId; }

		public LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Input> inputs(){ return _inputs; }
		public void add_inputs(LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Input val){
			if(!_hasInputs){
				_inputs = new LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Input>();
				_hasInputs = true;
			}
			_inputs.add(val);
		}

		public LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output> outputs(){ return _outputs; }
		public void add_outputs(LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output val){
			if(!_hasOutputs){
				_outputs = new LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output>();
				_hasOutputs = true;
			}
			_outputs.add(val);
		}

		public LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output findOutput(LinkedList<Integer> filled_slots){
			for(LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output output : _outputs){
				if(output.equalsFilled(filled_slots))
					return output;
			}
			return null;
		}

		static LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy fromBinary(ProtoInputStream input) throws IOException {
			LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy alchemy = new LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy();
			while(!input.isAtEnd()){
				int tag = input.readTag();
				switch(tag){
				default: return alchemy;
				case 0x00000008: alchemy.set_id(input.readInt32()); break;
				case 0x00000012:{
					byte[] sub = input.readBytes();
					alchemy.add_inputs(LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Input.fromBinary(ProtoInputStream.newInstance(sub)));
					break;
				}
				case 0x0000001A:{
					byte[] sub = input.readBytes();
					alchemy.add_outputs(LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output.fromBinary(ProtoInputStream.newInstance(sub)));
					break;
				}
				}
			}
			return alchemy;
		}

		public void writeTo(ProtoWriter w){
			if (_hasId) w.int32(1, _id);
			if (_hasInputs)
				for(LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Input val : _inputs){
					w.msg(2);
					val.writeTo(w);
					w.end();
				}
			if (_hasOutputs)
				for(LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output val : _outputs){
					w.msg(3);
					val.writeTo(w);
					w.end();
				}
		}

		public static class Input {
			private int _slot_number;
			private boolean _is_required;
			private int _input_list_id;
			private boolean _is_hyper;
			private boolean _hasSlot, _hasRequired, _hasListId, _hasHyper;

			private Input(){}

			public int slot_number(){ return _slot_number; }
			public void set_slot_number(int val){ _hasSlot = true; _slot_number = val; }
			public boolean has_slot_number(){ return _hasSlot; }

			public boolean is_required(){ return _is_required; }
			public void set_is_required(boolean val){ _hasRequired = true; _is_required = val; }

			public int input_list_id(){ return _input_list_id; }
			public void set_input_list_id(int val){ _hasListId = true; _input_list_id = val; }

			public boolean is_hyper(){ return _is_hyper; }
			public void set_is_hyper(boolean val){ _hasHyper = true; _is_hyper = val; }

			static LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Input fromBinary(ProtoInputStream input) throws IOException {
				LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Input in = new LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Input();
				while(!input.isAtEnd()){
					int tag = input.readTag();
					switch(tag){
					default: return in;
					case 0x00000008: in.set_slot_number(input.readInt32()); break;
					case 0x00000010: in.set_is_required(input.readBool()); break;
					case 0x00000018: in.set_input_list_id(input.readInt32()); break;
					case 0x00000020: in.set_is_hyper(input.readBool()); break;
					}
				}
				return in;
			}

			public void writeTo(ProtoWriter w){
				if (_hasSlot)     w.int32(1, _slot_number);
				if (_hasRequired) w.bool(2, _is_required);
				if (_hasListId)   w.int32(3, _input_list_id);
				if (_hasHyper)    w.bool(4, _is_hyper);
			}
		}

		public static class Output {
			private LinkedList<Integer> _filled_slots;
			private int _output_list_id;
			private LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output> _hyper_outputs;
			private boolean _hasFilled, _hasListId, _hasHyper;

			private Output(){}

			public LinkedList<Integer> filled_slots(){ return _filled_slots; }
			public void add_filled_slots(int val){
				if(!_hasFilled){
					_filled_slots = new LinkedList<Integer>();
					_hasFilled = true;
				}
				_filled_slots.add(val);
			}

			public boolean equalsFilled(LinkedList<Integer> filled_slots){
				int size = filled_slots.size();
				if(size != _filled_slots.size())
					return false;
				for(int i = size - 1; i >= 0; --i){
					if(filled_slots.get(i).intValue() != _filled_slots.get(i).intValue())
						return false;
				}
				return true;
			}

			public int output_list_id(){ return _output_list_id; }
			public void set_output_list_id(int val){ _hasListId = true; _output_list_id = val; }

			public LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output> hyper_outputs(){ return _hyper_outputs; }
			public void add_hyper_outputs(LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output val){
				if(!_hasHyper){
					_hyper_outputs = new LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output>();
					_hasHyper = true;
				}
				_hyper_outputs.add(val);
			}

			static LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output fromBinary(ProtoInputStream input) throws IOException {
				LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output out = new LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output();
				while(!input.isAtEnd()){
					int tag = input.readTag();
					switch(tag){
					default: return out;
					case 0x00000008: out.add_filled_slots(input.readInt32()); break;
					case 0x00000010: out.set_output_list_id(input.readInt32()); break;
					case 0x0000001A:{
						byte[] sub = input.readBytes();
						out.add_hyper_outputs(LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output.fromBinary(ProtoInputStream.newInstance(sub)));
						break;
					}
					}
				}
				return out;
			}

			public void writeTo(ProtoWriter w){
				if (_hasFilled)
					for(int val : _filled_slots)
						w.int32(1, val);
				if (_hasListId) w.int32(2, _output_list_id);
				if (_hasHyper)
					for(LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy.Output val : _hyper_outputs){
						w.msg(3);
						val.writeTo(w);
						w.end();
					}
			}
		}
	}


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode _result_code, LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.InputList> _inputLists, LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.OutputList> _outputLists, LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy> _alchemies, boolean _hasResultCode, boolean _hasInputLists, boolean _hasOutputLists, boolean _hasAlchemies) {
        if (_hasResultCode) {
                w.int32(1, _result_code.toInt());
            }
            if (_hasInputLists) {
                for (LIN.SC_ALCHEMY_DESIGN_ACK.InputList val : _inputLists) {
                    w.msg(2);
                    val.writeTo(w);
                    w.end();
                }
            }
            if (_hasOutputLists) {
                for (LIN.SC_ALCHEMY_DESIGN_ACK.OutputList val : _outputLists) {
                    w.msg(3);
                    val.writeTo(w);
                    w.end();
                }
            }
            if (_hasAlchemies) {
                for (LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy val : _alchemies) {
                    w.msg(4);
                    val.writeTo(w);
                    w.end();
                }
            }
    }

    public static boolean sendFields(L1PcInstance recipient, LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode _result_code, LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.InputList> _inputLists, LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.OutputList> _outputLists, LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy> _alchemies, boolean _hasResultCode, boolean _hasInputLists, boolean _hasOutputLists, boolean _hasAlchemies) {
        return sendFields(
                recipient == null ? null : recipient.getNetConnection(), _result_code, _inputLists, _outputLists,
                _alchemies, _hasResultCode, _hasInputLists, _hasOutputLists, _hasAlchemies);
    }

    public static boolean sendFields(CIOClient recipient, LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode _result_code, LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.InputList> _inputLists, LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.OutputList> _outputLists, LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy> _alchemies, boolean _hasResultCode, boolean _hasInputLists, boolean _hasOutputLists, boolean _hasAlchemies) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ALCHEMY_DESIGN_ACK, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, _result_code, _inputLists, _outputLists, _alchemies, _hasResultCode, _hasInputLists, _hasOutputLists, _hasAlchemies);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode _result_code, LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.InputList> _inputLists, LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.OutputList> _outputLists, LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy> _alchemies, boolean _hasResultCode, boolean _hasInputLists, boolean _hasOutputLists, boolean _hasAlchemies) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _result_code, _inputLists, _outputLists, _alchemies, _hasResultCode, _hasInputLists, _hasOutputLists, _hasAlchemies);
        return writer.finishWithTrailer(ProtoMsg.SC_ALCHEMY_DESIGN_ACK);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode proto7_result_code = null;
        LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.InputList> proto7_inputLists = null;
        LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.OutputList> proto7_outputLists = null;
        LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy> proto7_alchemies = null;
        boolean proto7_hasResultCode = false;
        boolean proto7_hasInputLists = false;
        boolean proto7_hasOutputLists = false;
        boolean proto7_hasAlchemies = false;


        return sendFields(
                protoRecipient, proto7_result_code, proto7_inputLists, proto7_outputLists, proto7_alchemies,
                proto7_hasResultCode, proto7_hasInputLists, proto7_hasOutputLists, proto7_hasAlchemies);
    }

    public static boolean sendClosed(CIOClient protoRecipient) {
        LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode ackWire_result_code = null;
                LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.InputList> ackWire_inputLists = null;
                LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.OutputList> ackWire_outputLists = null;
                LinkedList<LIN.SC_ALCHEMY_DESIGN_ACK.Alchemy> ackWire_alchemies = null;
                boolean ackWire_hasResultCode = false;
                boolean ackWire_hasInputLists = false;
                boolean ackWire_hasOutputLists = false;
                boolean ackWire_hasAlchemies = false;


                { ackWire_hasResultCode = true; ackWire_result_code = LIN.SC_ALCHEMY_DESIGN_ACK.ResultCode.RC_ALCHEMY_CURRENTLY_CLOSED; }
                return LIN.SC_ALCHEMY_DESIGN_ACK.sendFields(
                        protoRecipient, ackWire_result_code, ackWire_inputLists, ackWire_outputLists, ackWire_alchemies,
                        ackWire_hasResultCode, ackWire_hasInputLists, ackWire_hasOutputLists, ackWire_hasAlchemies);
    }

    public static boolean sendClosed(L1PcInstance protoRecipient) {
        return sendClosed(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }
    public static boolean sendConstructed(CIOClient protoRecipient) { return sendCtor(protoRecipient); }

    public static boolean sendConstructed(L1PcInstance protoRecipient) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection()); }

}
