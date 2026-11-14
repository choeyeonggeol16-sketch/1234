package l1j.server.server.model.network.core.session;

import l1j.server.server.model.network.core.connection.CUserSocket;public class CIOClient {public final CUserSocket owner=new CUserSocket();public boolean connected=true;public boolean isConnected(){return connected;}public CUserSocket getConnection(){return owner;}}
