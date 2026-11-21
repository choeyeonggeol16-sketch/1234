package l1j.server.server.model.item.treasure;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.shared.L1Type;
import l1j.server.server.utils.BytesOutputStream;

public class L1TreasureChest extends L1Object {
	private static final long 	serialVersionUID 	= 1L;
	public static final int 	CHEST_OPEN 			= 28;
	public static final int 	CHEST_CLOSE 		= 29;

	public static final int		CHEST_GRADE_LOWEST	= 0;
	public static final int		CHEST_GRADE_LOW		= 1;
	public static final int		CHEST_GRADE_MIDDLE	= 2;
	public static final int		CHEST_GRADE_HIGH	= 3;
	public static final int		CHEST_GRADE_HIGHEST	= 4;


	private String						_name;
	private int 						_gfx;
	private int 						_act;
	private int 						_heading;
	private byte[]						_worldPutBody;
	private TreasureChestOpenFilter	_ofilter;
	private TreasureChestAttackFilter	_afilter;
	private TreasureChestCompensator	_compensator;

	public L1TreasureChest(int id, String name, int gfx, int x, int y, int h, short mapId,
			TreasureChestOpenFilter ofilter, TreasureChestAttackFilter afilter, TreasureChestCompensator compensator){
		this(id, name, gfx, x, y, h, mapId, CHEST_CLOSE, ofilter, afilter, compensator);
	}

	public L1TreasureChest(int id, String name, int gfx, int x, int y, int h, short mapId, int act,
			TreasureChestOpenFilter ofilter, TreasureChestAttackFilter afilter, TreasureChestCompensator compensator){
		super();
		setId(id);
		setX(x);
		setY(y);
		setMap(mapId);
		_name		= name;
		_heading	= h;
		_gfx 		= gfx;
		_act		= act;
		_ofilter	= ofilter;
		_afilter	= afilter;
		_compensator= compensator;
		_worldPutBody = serialize();
	}

	public int getHeading(){
		return _heading;
	}

	public int getGfx(){
		return _gfx;
	}

	public int getAction(){
		return _act;
	}

	public boolean isClose(){
		return _act == CHEST_CLOSE;
	}

	public boolean isOpen(){
		return _act == CHEST_OPEN;
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if(perceivedFrom == null)
			return;

		perceivedFrom.addKnownObject(this);

		final byte[] body = _worldPutBody;
		if(body != null)
			SC_WorldPutObject.get(perceivedFrom.getNetConnection(), body);
	}

	public final void open(L1PcInstance pc){
		boolean isDestroy;
		synchronized(this){
			if(isOpen())
				return;

			if(_ofilter == null || !_ofilter.isFilter(pc)){
				_act = CHEST_OPEN;
				isDestroy = false;
			}else{
				if(_afilter == null || _afilter.isFilter(pc))
					return;
				_act = CHEST_OPEN;
				isDestroy = true;
			}
		}
		dispose(isDestroy);
	}

	public final void attack(L1PcInstance pc){
		synchronized(this){
			if(isOpen())
				return;

			if(_afilter == null || _afilter.isFilter(pc))
				return;
			_act = CHEST_OPEN;
		}
		dispose(true);
	}

	public void dispose(){
		L1World.getInstance().removeVisibleObject(this);
		L1World.getInstance().removeObject(this);
		_worldPutBody = null;
		remove();
		_ofilter 		= null;
		_afilter 		= null;
		_compensator 	= null;
	}

	public void dispose(boolean isDestroy){
		L1World.getInstance().removeVisibleObject(this);
		L1World.getInstance().removeObject(this);
		_worldPutBody = null;
		ThreadPool.getInstance().execute(new Runnable(){
			@Override
			public void run() {
				try{
					showOpen();
					if(isDestroy)
						showBlackCloud();
					else{
						showCloud();
						Thread.sleep(300);
						if(_compensator != null)
							_compensator.compensate(L1TreasureChest.this);
					}
					Thread.sleep(300);
					dispose();
				}catch(Exception e){
					e.printStackTrace();
				}finally{

				}
			}
		});
	}

	private void remove() {
        try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                SC_RemoveObject.nativeWriter(getId()),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayer(this)) {
                if(pc==null)continue;
                pc.removeKnownObject(this);
                final var client=pc.getNetConnection();
                if(client!=null && client.isConnected()) {
                    final var socket=client;
                    if(socket!=null && socket.isClientConnected())
                        shared.send(socket,pc.getId());
                }
            }
        }
    }
	private void showOpen(){
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                .sendWrittenRecipientsBestEffort(L1World.getInstance().getVisiblePlayer(this),
                        SC_DoActionGFX.nativeWriter(getId(), CHEST_OPEN));
    }

	private void showCloud(){
		showCloud(12157);
	}

	private void showBlackCloud(){
		showCloud(12158);
	}

	private void showCloud(int i){
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenNestedVisibleBestEffort(
                this,SC_SkillSound.nativeWriter(getId(),i));
    }

	public byte[] serialize(){
		BytesOutputStream s = new BytesOutputStream(256);
		byte[] 				b = null;
		try{
			s.write(0x08);				// point
			s.writePoint(getX(), getY());
			s.write(0x10);				// objectnumber
			s.writeBit(getId());
			s.write(0x18); 				// objectsprite
			s.writeBit(_gfx);
			s.write(0x20); 				// action
			s.writeBit(_act);
			s.write(0x28); 				// direction
			s.writeBit(_heading);
			s.write(0x30); 				// lightRadius
			s.writeBit(15);
			s.write(0x38); 				// objectcount
			s.write(1);
			s.write(0x40); 				// alignment(lawful)
			s.writeBit(0);
			s.write(0x4A);				// desc
			s.writeS2(_name);
			s.write(0x52); 				// title
			s.write(0x00);
			s.write(0x58);	 			// speed data
			s.write(0x00);
			s.write(0x60); 				// emotion
			s.write(0x00);
			s.write(0x68);				// drunken
			s.writeBit(0x00);
			s.write(0x70); 				// isghost
			s.write(0x00);
			s.write(0x78); 				// isparalyzed
			s.write(0x00);
			s.writeBit(0x80);			// isuser
			s.write(0x00);
			s.writeBit(0x88);			// isinvisible
			s.write(0x00);
			s.writeBit(0x90); 			// ispoisoned
			s.write(0x00);
			s.writeBit(0x98);			// emblemid
			s.write(0x00);
			s.writeBit(0xA2); 			// pledgename
			s.write(0x00);
			s.writeBit(0xAA); 			// mastername
			s.write(0x00);
			s.writeBit(0xB0); 			// altitude
			s.write(0x00);
			s.writeBit(0xB8);			// hitratio
			s.writeBit(-1);
			s.writeBit(0xC0); 			// safelevel
			s.write(0x00);
			s.writeBit(0xD0);	 		// weapon sprite
			s.write(0x00);
			s.writeBit(0xD8);	 		// couplestate
			s.write(0x00);
			s.writeBit(0xE0); 			// boundarylevelindex
			s.writeBit(0x00);
			s.writeBit(0xE8); 			// weakelemental
			s.write(0x00);
			s.writeBit(0xF0);			// manaratio
			s.writeBit(-1);
			s.writeBit(0xF8);			// botindex
			s.write(0x00);
			s.writeBit(0x100); 			// home server no
			s.write(0x00);
			s.writeH(0x00);
			b = s.toArray();
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			if(s != null){
				s.close();
				s.dispose();
				s = null;
			}
		}
		return b;
	}

	private static int _instanceType = -1;
	@Override
	public int getL1Type(){
		return _instanceType == -1 ? _instanceType = super.getL1Type() | L1Type.L1TYPE_TREASURE_CHEST : _instanceType;
	}
}
