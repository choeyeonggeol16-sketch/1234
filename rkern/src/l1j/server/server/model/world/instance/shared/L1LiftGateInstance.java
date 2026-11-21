package l1j.server.server.model.world.instance.shared;


import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SupportInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeAttr;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.shared.L1Type;
import l1j.server.server.utils.BytesOutputStream;

public class L1LiftGateInstance extends L1Object{
	private static final long serialVersionUID = 1L;
	public static final int LIFT_OPEN 	= 28;
	public static final int LIFT_CLOSE 	= 29;

	private final int[] _attrCoordinates;
	private int 					_gfx;
	private int					_act;
	private int 					_heading;
	private boolean				_isDisposing;
	private byte[]				_worldPutBody;
	private boolean				_isWidth;
	private int					_weight;

	public L1LiftGateInstance(int id, int gfx, int x, int y, int h, short mapid, boolean isWidth, int weight){
		this(id, gfx, x, y, h, mapid, LIFT_CLOSE, isWidth, weight);
	}

	public L1LiftGateInstance(int id, int gfx, int x, int y, int h, short mapid, int act, boolean isWidth, int weight){
		super();
		setId(id);
		setX(x);
		setY(y);
		setMap(mapid);
		_heading 	= h;
		_gfx 		= gfx;
		_act 		= act;
		_isDisposing= false;

		_isWidth	= isWidth;
		_weight = weight;
		_attrCoordinates = new int[_weight * 4];
		createAttrs();
		_worldPutBody = serialize();
		onPass(false);
	}

	private void createAttrs() {
        final int x = getX(), y = getY();
        final int diff = (_weight - 1) / 2;
        int j = 0;
        for (int i = 0; i < _weight; ++i) {
            if (_isWidth) {
                _attrCoordinates[j++] = x - 1;
                _attrCoordinates[j++] = y - diff + i;
                _attrCoordinates[j++] = x;
                _attrCoordinates[j++] = y - diff + i;
            } else {
                _attrCoordinates[j++] = x - diff + i;
                _attrCoordinates[j++] = y - 1;
                _attrCoordinates[j++] = x - diff + i;
                _attrCoordinates[j++] = y;
            }
        }
    }

    private void sendAttrs(L1PcInstance pc) {
        final boolean close = isClose();
        for (int i = 0; i < _attrCoordinates.length; i += 2) {
            SC_ChangeAttr.send(pc, _attrCoordinates[i], _attrCoordinates[i + 1], _isWidth, close);
        }
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
		return _act == LIFT_CLOSE;
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if(this == null || perceivedFrom == null)
			return;

		perceivedFrom.addKnownObject(this);

		final byte[] body = _worldPutBody;
		if(body != null)
			SC_WorldPutObject.get(perceivedFrom.getNetConnection(), body);

        sendAttrs(perceivedFrom);
	}

	@Override
	public void onPerceive(L1SupportInstance perceivedFrom) {
	}

	public void up(){
		if(isClose())
			return;

		_act		= LIFT_CLOSE;
		swapWorldPut();
		notifyChanged();
		onPass(false);
	}

	public void down(){
		if(!isClose())
			return;

		_act		= LIFT_OPEN;
		swapWorldPut();
		if(_isDisposing)
			notifyDispose();
		else
			notifyChanged();
		onPass(true);
	}

	private void swapWorldPut(){
		if(!_isDisposing)
			_worldPutBody = serialize();
	}


	private void onPass(boolean isPass){
		L1Map m = L1WorldMap.getInstance().getMap(getMapId());
		int x = getX();
		int y = getY();
		if(_isWidth){
			int diff = (_weight - 1) / 2;
			for(int i=0; i<_weight; ++i){
				m.setPassable(x - 1, y-diff + i, isPass);
				m.setPassable(x, 		y-diff + i, isPass);
			}
		}else{
			int diff = (_weight - 1) / 2;
			for(int i=0; i<_weight; ++i){
				m.setPassable(x - diff + i, y-1, isPass);
				m.setPassable(x - diff + i, y, isPass);
			}
		}
	}

	public void dispose(){
		L1World.getInstance().removeVisibleObject(this);
		L1World.getInstance().removeObject(this);
		_isDisposing = true;
		_worldPutBody = null;
		down();
	}
	public void notifyDispose() {
        final java.util.List<L1PcInstance> viewers=L1World.getInstance().getVisiblePlayer(this);
        if(viewers.isEmpty())return;
        final int objectId=getId(),action=_act;
        final var attrFrames=createSharedAttrs(isClose());
        Throwable primaryFailure = null;
        try {
            try (var actionFrame=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                         SC_DoActionGFX.nativeWriter(objectId,action),Integer.MIN_VALUE,Integer.MAX_VALUE);
                 var removeFrame=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                         SC_RemoveObject.nativeWriter(objectId),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
                for(L1PcInstance pc:viewers) {
                    if(pc==null)continue;
                    pc.removeKnownObject(this);
                    final var client=pc.getNetConnection();
                    // Preserve original order: known-object removal, tile attributes,
                    // action and finally object removal for each specific player.
                    sendSharedAttrs(attrFrames,client,pc.getId());
                    if(client!=null && client.isConnected()) {
                        final var socket=client;
                        if(socket!=null) {
                            actionFrame.send(socket,pc.getId());
                            removeFrame.send(socket,pc.getId());
                        }
                    }
                }
            }
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally { closeSharedAttrs(attrFrames, primaryFailure); }
    }

	public void notifyChanged() {
        final java.util.List<L1PcInstance> viewers=L1World.getInstance().getVisiblePlayer(this);
        if(viewers.isEmpty())return;
        final int objectId=getId(),action=_act;
        final var attrFrames=createSharedAttrs(isClose());
        Throwable primaryFailure = null;
        try {
            try (var actionFrame=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                         SC_DoActionGFX.nativeWriter(objectId,action),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
                for(L1PcInstance pc:viewers) {
                    if(pc==null)continue;
                    final var client=pc.getNetConnection();
                    // Preserve original order: action first, then tile attributes.
                    if(client!=null && client.isConnected()) {
                        final var socket=client;
                        if(socket!=null)actionFrame.send(socket,pc.getId());
                    }
                    sendSharedAttrs(attrFrames,client,pc.getId());
                }
            }
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally { closeSharedAttrs(attrFrames, primaryFailure); }
    }

	

	

	public void takeClose(long l){
		down();
		ThreadPool.getInstance().scheduleRequired(new Runnable(){
			@Override
			public void run(){
				up();
			}
		}, l);
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
			s.writeBit(getGfx());
			s.write(0x20); 				// action
			s.writeBit(getAction());
			s.write(0x28); 				// direction
			s.writeBit(getHeading());
			s.write(0x30); 				// lightRadius
			s.writeBit(15);
			s.write(0x38); 				// objectcount
			s.write(1);
			s.write(0x40); 				// alignment(lawful)
			s.writeBit(0);
			s.write(0x4A);				// desc
			s.write(0x00);
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
		return _instanceType == -1 ? _instanceType = super.getL1Type() | L1Type.L1TYPE_LIFT : _instanceType;
	}


    /** One const buffer per unchanged tile attribute packet per gate event. */
    private l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer[]
            createSharedAttrs(boolean closed) {
        final int count=_attrCoordinates.length>>>1;
        final var frames=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer[count];
        int made=0;
        try {
            for(int i=0;i<count;i++) {
                final int idx=i<<1;
                final l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter wire=SC_ChangeAttr.nativeWriter(_attrCoordinates[idx],
                        _attrCoordinates[idx+1],_isWidth,closed);
                frames[made++]=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                        wire,Integer.MIN_VALUE,Integer.MAX_VALUE);
            }
            return frames;
        } catch (RuntimeException | Error failure) {
            closeSharedAttrs(frames, failure);
            throw failure;
        }
    }

    /** Return every frame even when one Native source release fails. */
    private static void closeSharedAttrs(
            l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer[] frames,
            Throwable pending) {
        if (frames == null) return;
        Throwable failure = pending;
        for (var frame : frames) {
            if (frame == null) continue;
            try { frame.close(); }
            catch (RuntimeException | Error closeFailure) {
                if (failure == null) failure = closeFailure;
                else if (failure != closeFailure) failure.addSuppressed(closeFailure);
            }
        }
        if (pending == null && failure != null) {
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw (Error) failure;
        }
    }

    private static void sendSharedAttrs(
            l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer[] attrs,
            l1j.server.server.model.network.core.connection.CUserSocket client,int playerId) {
        if(client==null || !client.isConnected())return;
        final var socket=client;
        if(socket==null)return;
        for(var frame:attrs)frame.send(socket,playerId);
    }

}
