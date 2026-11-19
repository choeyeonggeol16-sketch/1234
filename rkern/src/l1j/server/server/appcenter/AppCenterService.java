package l1j.server.server.appcenter;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.MJDShopSystem.MJDShopItem;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Embedded read-only app center: same RKern JVM, isolated fixed HTTP executor.
 * No HTTP request calls JDBC or CIO, and no endpoint mutates game state.
 * Client HTML is untrusted; it cannot authorize any operation.
 */
public final class AppCenterService {
    private static final int MAX_SHOP_ROWS = 30_000;
    private static final int MAX_CHARACTER_ROWS = 20_000;
    private static final int MAX_RESPONSE = 64 * 1024;
    private static final int MAX_QUERY = 48;
    private static final int CACHE_INTERVAL_SEC = 60;
    private static final int CACHE_EXPIRE_SEC = 180;
    private static final long NANO = 1_000_000_000L;
    private static final int IP_STRIPES = 4096;

    private static final class Market {
        final String name, folded, seller;
        final int enchant, type, iconIndex, x, y;
        final long low, high, quantity;
        final int stores = 1;
        Market(String name, String seller, int enchant, int type, int inventoryGfx,
               long price, long quantity, int x, int y) {
            this.name = name;
            this.folded = name.toLowerCase(Locale.ROOT);
            this.seller = seller;
            this.enchant = enchant;
            this.type = type;
            this.iconIndex = AppCenterAssets.iconIndex(inventoryGfx);
            this.low = price;
            this.high = price;
            this.quantity = quantity;
            this.x = x;
            this.y = y;
        }
    }
    private record Character(String name,String folded,int level,int type,int sex,String clan) {}
    private record Notice(int id,int board,String title,String date,String author,String content) {}
    private record Castle(int id,String castle,String owner,String time) {}
    private record Clan(String name,String leader,int clanId,int castleId,int house,int joinSetting,int joinType,int members) {}
    private record ClanRecruitment(int clanId,int joinSetting,int joinType) {}
    private static final ConcurrentHashMap<String,ClanRecruitment> liveClanSettings=new ConcurrentHashMap<>();
    /** Game packet -> CacheD SQL COMMIT -> publish latest settings to open AppCenter pages. */
    public static void publishClanSettings(int clanId,String clanName,int joinSetting,int joinType) {
        if (clanId<=0 || clanName==null || clanName.isEmpty() || listener==null) return;
        if (liveClanSettings.size()>5000) liveClanSettings.clear();
        liveClanSettings.put(clanName.toLowerCase(Locale.ROOT),new ClanRecruitment(clanId,joinSetting,joinType));
    }
    private record Profile(String name,int level,int classId,int sex,String clan) {}
    private static volatile Map<String,Profile> liveProfiles=Map.of();
    private record Snapshot(long capturedNanos,List<Market> markets,List<Character> characters,
                            List<Character> ranks,List<Notice> notices,List<Castle> castles,List<Clan> clans) {}

    private static volatile Snapshot snapshot;
    // A five-second snapshot of visible personal shops; character_shop is cleared
    // on startup, so JDBC snapshots cannot reliably represent currently open shops.
    private static volatile List<Market> liveMarkets = List.of();
    private record Launch(String term, int playerId, long deadlineNanos) {}
    private static final ConcurrentHashMap<String,Launch> pendingMarketSearch = new ConcurrentHashMap<>();
    private static final class Bucket {
        private double credit;
        private long tick;
        private Bucket(double initial){credit=initial;tick=System.nanoTime();}
        synchronized boolean admit(double rate,double ceiling,long now) {
            long elapsed=now-tick;
            if(elapsed>0){credit=Math.min(ceiling,credit+Math.min((double)elapsed/NANO,10.0)*rate);tick=now;}
            if(credit<1.0)return false;
            credit-=1.0;
            return true;
        }
    }
    // Static in-memory assets and read-only API calls have different budgets.
    // A single page needs several parallel assets, and users may share a NAT IP.
    // Keep bounded limits so a flood cannot consume game-side resources.
    private static final Bucket staticGlobal=new Bucket(1800);
    private static final Bucket apiGlobal=new Bucket(360);
    private static final Bucket[] staticIpBuckets=new Bucket[IP_STRIPES];
    private static final Bucket[] apiIpBuckets=new Bucket[IP_STRIPES];
    static {for(int i=0;i<IP_STRIPES;i++){
        staticIpBuckets[i]=new Bucket(360);
        apiIpBuckets[i]=new Bucket(72);
    }}
    private static final Semaphore requests=new Semaphore(8);
    private static final AtomicInteger threadNumber=new AtomicInteger();
    private static final class NamedThread implements ThreadFactory {
        private final String prefix;
        NamedThread(String prefix){this.prefix=prefix;}
        @Override public Thread newThread(Runnable r){
            Thread t=new Thread(r,prefix+threadNumber.incrementAndGet());t.setDaemon(true);return t;
        }
    }
    private static final byte[] HTML = AppCenterAssets.get("/").bytes;
    private static final byte[] CSS = AppCenterAssets.get("/app.css").bytes;
    private static final byte[] JS = AppCenterAssets.get("/app.js").bytes;
    private static final int MAX_STATIC_RESPONSE = 1024 * 1024;

    private static volatile HttpServer listener;
    private static volatile ThreadPoolExecutor httpPool;
    private static volatile Thread snapshotWorker;
    private static volatile boolean running;

    /** Start after the existing DB pool, world and CIO have initialized.
     * Never fail game startup if HTTP port is unavailable. */
    public static synchronized void start() {
        if (listener != null) return;
        try {
            // JDK21 HttpServer request-header/count and slow-request deadlines.
            // These protect the HTTP parser before a request reaches our handler.
            System.setProperty("sun.net.httpserver.maxConnections", "384");
            System.setProperty("sun.net.httpserver.maxIdleConnections", "64");
            System.setProperty("sun.net.httpserver.maxReqHeaders", "32");
            System.setProperty("sun.net.httpserver.maxReqHeaderSize", "4096");
            System.setProperty("sun.net.httpserver.maxReqTime", "3");
            System.setProperty("sun.net.httpserver.maxRspTime", "5");
            System.setProperty("sun.net.httpserver.drainAmount", "0");
            // Public read-only endpoints, one additional TCP port on SAME server.
            // No reverse proxy or second JVM is required. No secrets enter HTTP.
            HttpServer service=HttpServer.create(new InetSocketAddress("0.0.0.0",18085),64);
            ThreadPoolExecutor executor=new ThreadPoolExecutor(8,8,0,TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256),new NamedThread("app-http-"),new ThreadPoolExecutor.AbortPolicy());
            service.createContext("/",new Handler(HTML,CSS,JS));
            service.setExecutor(executor);
            service.start();
            listener=service;
            httpPool=executor;
            running=true;
            Thread worker=new Thread(()->{
                long nextDatabase=0L;
                while(running&&!Thread.currentThread().isInterrupted()){
                    try {liveProfiles=refreshProfiles();}
                    catch(RuntimeException ignored){liveProfiles=Map.of();}
                    try {liveMarkets=refreshMarket();}
                    catch(RuntimeException ignored){/* retain last complete snapshot */}
                    if (!pendingMarketSearch.isEmpty()) {
                        long time=System.nanoTime();
                        pendingMarketSearch.entrySet().removeIf(e -> time-e.getValue().deadlineNanos() >= 0);
                    }
                    long now=System.nanoTime();
                    if(now>=nextDatabase){
                        try {
                            Snapshot s=refresh();
                            if(running)snapshot=s;
                        }catch(Exception e){
                            if(running)System.err.println("[AppCenter] Snapshot unavailable: " +
                                e.getClass().getSimpleName());
                        }
                        nextDatabase=System.nanoTime()+CACHE_INTERVAL_SEC*NANO;
                    }
                    try{Thread.sleep(5_000L);}
                    catch(InterruptedException e){Thread.currentThread().interrupt();break;}
                }
            },"app-db-snapshot");
            worker.setDaemon(true);
            snapshotWorker=worker;
            worker.start();
            System.out.println("앱센터 서버가 연결되었습니다.");
        }catch(IOException|RuntimeException e){
            System.err.println("[AppCenter] Disabled (game server continues): "+e.getClass().getSimpleName());
            running=false;
        }
    }
    /** No new requests or snapshots after shutdown begins. */
    public static synchronized void stop(){
        running=false;
        Thread t=snapshotWorker;snapshotWorker=null;
        if(t!=null)t.interrupt();
        HttpServer s=listener;listener=null;
        if(s!=null)s.stop(0);
        ThreadPoolExecutor e=httpPool;httpPool=null;
        if(e!=null)e.shutdownNow();
        snapshot=null;
        liveMarkets=List.of();
        pendingMarketSearch.clear();
        liveProfiles=Map.of();
        liveClanSettings.clear();
    }
    private static Connection connection() throws SQLException {
        // Reuse the game server's already installed JDBC driver and Hikari pool.
        // One background refresh worker; HTTP threads NEVER borrow connections.
        Connection c=DatabaseFactory.getInstance().getConnection();
        try { c.setReadOnly(true);return c; }
        catch(SQLException ex){c.close();throw ex;}
    }
    private static PreparedStatement query(Connection c,String sql) throws SQLException {
        PreparedStatement p=c.prepareStatement(sql,ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
        p.setQueryTimeout(3);
        return p;
    }
    private static Snapshot refresh() throws SQLException {
        // No HTTP request can open a JDBC connection. Only this one daemon does.
        try(Connection c=connection()) {
            // Market data comes from live shop owners, never a cold item SQL scan.
            List<Market> markets=List.of();
            List<Character> characters=new ArrayList<>();
            Map<String,Integer> memberCounts=new HashMap<>();
            try(PreparedStatement ps=query(c,"SELECT char_name,level,Type,Sex,Clanname FROM characters "+
                    "WHERE Banned=0 AND AccessLevel=0 AND Type BETWEEN 0 AND 7 LIMIT 20001");ResultSet rs=ps.executeQuery()){
                int rows=0;
                while(rs.next()){
                    if(++rows>MAX_CHARACTER_ROWS)throw new SQLException("Character snapshot cap exceeded");
                    String name=validText(rs.getString(1),32);
                    if(name.isEmpty())continue;
                    String clan=validText(rs.getString(5),50);
                    characters.add(new Character(name,name.toLowerCase(Locale.ROOT),rs.getInt(2),
                        rs.getInt(3),rs.getInt(4),clan));
                    if(!clan.isEmpty())memberCounts.merge(clan,1,Integer::sum);
                }
            }
            characters.sort(Comparator.comparingInt((Character m)->m.level).reversed().thenComparing(m->m.name));
            List<Character> ranks=List.copyOf(characters.subList(0,Math.min(characters.size(),100)));
            List<Notice> notices=new ArrayList<>();
            try(PreparedStatement ps=query(c,"SELECT id,board_id,title,date,name,content FROM board_posts "+
                    "WHERE board_id IN (2,8) ORDER BY id DESC LIMIT 50");ResultSet rs=ps.executeQuery()){
                while(rs.next())notices.add(new Notice(rs.getInt(1),rs.getInt(2),
                    validText(rs.getString(3),100),validText(rs.getString(4),25),
                    validText(rs.getString(5),32),validText(rs.getString(6),4000)));
            }catch(SQLException unavailable){
                System.err.println("[AppCenter] Notice list unavailable");
            }
            // Public castle status and clan roster only.  No inventory/weapon/item schema scans.
            Map<Integer,String> castleOwners=new HashMap<>();
            try(PreparedStatement ps=query(c,"SELECT clan_name,hascastle FROM clan_data " +
                    "WHERE hascastle BETWEEN 1 AND 8");ResultSet rs=ps.executeQuery()){
                while(rs.next())castleOwners.putIfAbsent(rs.getInt(2),validText(rs.getString(1),50));
            }catch(SQLException unavailable){
                System.err.println("[AppCenter] Castle owner list unavailable");
            }
            List<Clan> clans=new ArrayList<>();
            try(PreparedStatement ps=query(c,"SELECT clan_name,leader_name,clan_id,hascastle,hashouse,join_setting,join_type " +
                    "FROM clan_data ORDER BY clan_id DESC LIMIT 200");ResultSet rs=ps.executeQuery()){
                while(rs.next()){
                    String name=validText(rs.getString(1),50);
                    if(name.isEmpty())continue;
                    int cid=rs.getInt(4);
                    if(cid>=1&&cid<=8)castleOwners.putIfAbsent(cid,name);
                    clans.add(new Clan(name,validText(rs.getString(2),32),rs.getInt(3),cid,rs.getInt(5),
                        rs.getInt(6),rs.getInt(7),memberCounts.getOrDefault(name,0)));
                }
            }catch(SQLException unavailable){
                System.err.println("[AppCenter] Clan list unavailable");
            }
            List<Castle> castles=new ArrayList<>();
            try(PreparedStatement ps=query(c,"SELECT castle_id,name,war_time FROM castle " +
                    "WHERE castle_id BETWEEN 1 AND 8 ORDER BY castle_id");ResultSet rs=ps.executeQuery()){
                while(rs.next()){
                    int id=rs.getInt(1);
                    castles.add(new Castle(id,validText(rs.getString(2),50),
                        castleOwners.getOrDefault(id,""),validText(rs.getString(3),26)));
                }
            }catch(SQLException unavailable){
                System.err.println("[AppCenter] Siege list unavailable");
            }
            return new Snapshot(System.nanoTime(),List.copyOf(markets),List.copyOf(characters),
                ranks,List.copyOf(notices),List.copyOf(castles),List.copyOf(clans));
        }
    }
    private static String validText(String s,int max){
        if(s==null)return "";
        if(s.length()>max)s=s.substring(0,max);
        StringBuilder b=new StringBuilder(s.length());
        for(int i=0;i<s.length();i++){char c=s.charAt(i);if(!java.lang.Character.isISOControl(c))b.append(c);}
        return b.toString();
    }
    private static final class Handler implements HttpHandler {
        private final byte[] html,css,js;
        Handler(byte[] html,byte[] css,byte[] js){this.html=html;this.css=css;this.js=js;}
        @Override public void handle(HttpExchange ex) throws IOException {
            try {
                if(!admit(ex)){send(ex,429,"text/plain","Too many requests".getBytes(StandardCharsets.US_ASCII));return;}
                if(!requests.tryAcquire()){
                    send(ex,503,"text/plain","Busy".getBytes(StandardCharsets.US_ASCII));return;
                }
                try{process(ex);}finally{requests.release();}
            }catch(RuntimeException e){
                // No stack traces, paths, SQL, or secrets returned to an HTTP client.
                try{send(ex,500,"text/plain","Internal error".getBytes(StandardCharsets.US_ASCII));}
                catch(IOException ignored){}
            }finally{ex.close();}
        }
        private boolean admit(HttpExchange ex) {
            InetAddress client=ex.getRemoteAddress().getAddress();
            if(client==null)return false;
            String ip=client.getHostAddress();
            int slot=(ip.hashCode()&0x7FFFFFFF)%IP_STRIPES;
            long now=System.nanoTime();
            String path=ex.getRequestURI().getRawPath();
            boolean staticPage=path!=null && path.length()<=80 &&
                (path.equals("/") || path.equals("/ingame/index") ||
                 path.equals("/app.css") || path.equals("/app.js") ||
                 AppCenterAssets.isStaticPath(path));
            if(staticPage){
                return staticIpBuckets[slot].admit(180,360,now) &&
                    staticGlobal.admit(900,1800,now);
            }
            return apiIpBuckets[slot].admit(24,72,now) &&
                apiGlobal.admit(180,360,now);
        }
        private void process(HttpExchange ex) throws IOException {
            // Unknown / cross-site origins are not trusted, including file:// pages.
            // This is defense-in-depth; no user identity is inferred from headers.
            String fetchSite=ex.getRequestHeaders().getFirst("Sec-Fetch-Site");
            if("cross-site".equalsIgnoreCase(fetchSite)){
                send(ex,403,"text/plain",new byte[0]);return;
            }
            if(!"GET".equals(ex.getRequestMethod())){
                ex.getResponseHeaders().set("Allow","GET");
                send(ex,405,"text/plain","GET only".getBytes(StandardCharsets.US_ASCII));return;
            }
            String length=ex.getRequestHeaders().getFirst("Content-Length");
            if((length!=null && !length.equals("0")) ||
               ex.getRequestHeaders().getFirst("Transfer-Encoding")!=null){
                send(ex,400,"text/plain",new byte[0]);return;
            }
            String path=ex.getRequestURI().getRawPath();
            if(path==null||path.length()>80||path.indexOf('%')>=0||path.indexOf('\\')>=0){
                send(ex,404,"text/plain",new byte[0]);return;
            }
            if(path.equals("/")||path.equals("/ingame/index")){
                send(ex,200,"text/html; charset=utf-8",html);return;
            }
            if(path.equals("/app.css")){send(ex,200,"text/css; charset=utf-8",css);return;}
            if(path.equals("/app.js")){send(ex,200,"application/javascript; charset=utf-8",js);return;}
            AppCenterAssets.Asset resource = AppCenterAssets.get(path);
            if (resource != null) {
                send(ex, 200, resource.contentType, resource.bytes, MAX_STATIC_RESPONSE);
                return;
            }
            if(!path.equals("/api/status")&&!path.equals("/api/me")&&
               !path.equals("/api/launch")&&!path.equals("/api/market")&&
               !path.equals("/api/rank")&&!path.equals("/api/characters")&&
               !path.equals("/api/notice")&&!path.equals("/api/siege")&&
               !path.equals("/api/clan")){
                send(ex,404,"text/plain",new byte[0]);return;
            }
            if(path.equals("/api/launch")){
                byte[] data=consumeMarketSearch(ex.getRemoteAddress().getAddress())
                    .getBytes(StandardCharsets.UTF_8);
                send(ex,200,"application/json; charset=utf-8",data);return;
            }
            if(path.equals("/api/market")){
                Map<String,String> params=parameters(ex.getRequestURI());
                if(params==null){send(ex,400,"text/plain",new byte[0]);return;}
                send(ex,200,"application/json; charset=utf-8",
                    markets(liveMarkets,params).getBytes(StandardCharsets.UTF_8));return;
            }
            if(path.equals("/api/me")){
                byte[] data=personalProfile(ex.getRemoteAddress().getAddress())
                    .getBytes(StandardCharsets.UTF_8);
                send(ex,200,"application/json; charset=utf-8",data);return;
            }
            Snapshot s=snapshot;
            if(s==null || System.nanoTime()-s.capturedNanos> CACHE_EXPIRE_SEC*NANO){
                send(ex,503,"application/json; charset=utf-8",
                    "{\"error\":\"data_unavailable\"}".getBytes(StandardCharsets.UTF_8));return;
            }
            Map<String,String> params=parameters(ex.getRequestURI());
            if(params==null){send(ex,400,"text/plain",new byte[0]);return;}
            String result;
            switch(path){
                case "/api/status" -> result="{\"ok\":true,\"ageSeconds\":"+((System.nanoTime()-s.capturedNanos)/NANO)+"}";
                case "/api/rank" -> result=characters(
                    params.containsKey("type") && !params.get("type").equals("all")?s.characters:s.ranks,params,true);
                case "/api/characters" -> result=characters(s.characters,params,false);
                case "/api/notice" -> result=notices(s,params);
                case "/api/siege" -> result=castles(s);
                case "/api/clan" -> result=clans(s,params);
                default -> {send(ex,404,"text/plain",new byte[0]);return;}
            }
            byte[] bytes=result.getBytes(StandardCharsets.UTF_8);
            if(bytes.length>MAX_RESPONSE){send(ex,503,"text/plain",new byte[0]);return;}
            send(ex,200,"application/json; charset=utf-8",bytes);
        }
    }
    private static Map<String,String> parameters(URI uri){
        String raw=uri.getRawQuery();if(raw==null||raw.isEmpty())return Map.of();
        if(raw.length()>240)return null;
        Map<String,String> p=new HashMap<>(4);
        for(String pair:raw.split("&",-1)){
            if(pair.length()>150||p.size()>=4)return null;
            int pos=pair.indexOf('=');
            if(pos<1)return null;
            try{
                String k=URLDecoder.decode(pair.substring(0,pos),StandardCharsets.UTF_8);
                String v=URLDecoder.decode(pair.substring(pos+1),StandardCharsets.UTF_8);
                if(k.length()>20||v.length()>(k.equals("name")?50:MAX_QUERY)||p.putIfAbsent(k,v)!=null)return null;
                if(!k.equals("q")&&!k.equals("page")&&!k.equals("type")&&!k.equals("id")&&!k.equals("board")&&!k.equals("name"))return null;
                for(int i=0;i<v.length();i++)if(java.lang.Character.isISOControl(v.charAt(i)))return null;
            }catch(IllegalArgumentException e){return null;}
        }
        return p;
    }
    private static int page(Map<String,String> p){
        try{int n=Integer.parseInt(p.getOrDefault("page","1"));return n>=1&&n<=10?n:1;}
        catch(NumberFormatException e){return 1;}
    }
    private static String q(Map<String,String> p){
        String s=p.getOrDefault("q","").strip().toLowerCase(Locale.ROOT);
        return s.length()>=2 && s.length()<=MAX_QUERY?s:"";
    }
    private static String markets(List<Market> rows,Map<String,String> p){
        String term=q(p);if(term.isEmpty())return "{\"items\":[],\"more\":false}";
        int page=page(p),begin=(page-1)*20,count=0;boolean more=false;
        StringBuilder b=new StringBuilder(4096);b.append("{\"items\":[");
        String collapsed=term.replace(" ","");
        for(Market m:rows){
            if(!m.folded.contains(term) && !m.folded.replace(" ","").contains(collapsed))continue;
            String filter=p.get("type");
            if(filter!=null && !filter.isEmpty() && !filter.equals(Integer.toString(m.type)))continue;
            if(count>=begin&&count<begin+20){
                if(count>begin)b.append(',');
                b.append("{\"name\":");json(b,m.name);
                b.append(",\"enchant\":").append(m.enchant);
                b.append(",\"type\":").append(m.type);
                b.append(",\"iconIndex\":").append(m.iconIndex);
                b.append(",\"low\":").append(m.low);
                b.append(",\"high\":").append(m.high);
                b.append(",\"stores\":").append(m.stores);
                b.append(",\"quantity\":").append(m.quantity);
                b.append(",\"seller\":");json(b,m.seller);
                b.append(",\"x\":").append(m.x);
                b.append(",\"y\":").append(m.y).append('}');
            }
            if(++count>begin+20){more=true;break;}
        }
        return b.append("],\"more\":").append(more).append('}').toString();
    }
    private static String characters(List<Character> chars,Map<String,String> p,boolean ranking){
        String term=ranking?"":q(p);
        if(!ranking && term.isEmpty())return "{\"items\":[],\"more\":false}";
        int selected=-1;
        if(ranking && p.containsKey("type")){
            String type=p.get("type");
            if(!"all".equals(type)){
                try{selected=Integer.parseInt(type);}catch(NumberFormatException ignored){selected=-2;}
                if(selected<0||selected>7)return "{\"items\":[],\"more\":false}";
            }
        }
        int page=page(p),begin=(page-1)*20,count=0;boolean more=false;
        StringBuilder b=new StringBuilder(3072).append("{\"items\":[");
        for(Character c:chars){
            if(!c.folded.contains(term) || (selected>=0&&c.type!=selected))continue;
            if(count>=begin&&count<begin+20){
                if(count>begin)b.append(',');
                b.append("{\"name\":");json(b,c.name);b.append(",\"level\":").append(c.level);
                b.append(",\"classId\":").append(c.type).append(",\"sex\":").append(c.sex);
                b.append(",\"clan\":");json(b,c.clan);
                if(ranking)b.append(",\"rank\":").append(count+1);
                b.append('}');
            }
            if(++count>begin+20){more=true;break;}
        }
        return b.append("],\"more\":").append(more).append('}').toString();
    }
    private static String notices(Snapshot s,Map<String,String> p){
        String ident=p.get("id");
        StringBuilder b=new StringBuilder(4096);
        if(ident!=null){
            int id;
            try{id=Integer.parseInt(ident);}catch(NumberFormatException e){return "{\"items\":[]}";}
            int board=-1;
            try{if(p.containsKey("board"))board=Integer.parseInt(p.get("board"));}
            catch(NumberFormatException e){return "{\"items\":[]}";}
            for(Notice n:s.notices)if(n.id==id&&(board<0||n.board==board)){
                b.append("{\"items\":[");appendNotice(b,n,true);return b.append("]}").toString();
            }
            return "{\"items\":[]}";
        }
        b.append("{\"items\":[");int max=Math.min(30,s.notices.size());
        for(int i=0;i<max;i++){if(i>0)b.append(',');appendNotice(b,s.notices.get(i),false);}
        return b.append("],\"more\":false}").toString();
    }
    private static void appendNotice(StringBuilder b,Notice n,boolean detail){
        b.append("{\"id\":").append(n.id).append(",\"board\":").append(n.board);
        b.append(",\"title\":");json(b,n.title);
        b.append(",\"author\":");json(b,n.author);
        b.append(",\"date\":");json(b,n.date);
        if(detail){b.append(",\"content\":");json(b,n.content);}
        b.append('}');
    }
    private static String castles(Snapshot s){
        StringBuilder b=new StringBuilder(2048).append("{\"items\":[");
        int i=0;
        for(Castle c:s.castles){
            if(i++>0)b.append(',');
            b.append("{\"castle\":");json(b,c.castle);
            b.append(",\"owner\":");json(b,c.owner);
            b.append(",\"time\":");json(b,c.time);
            b.append('}');
        }
        return b.append("],\"more\":false}").toString();
    }
    private static void appendClan(StringBuilder b,Clan c,Snapshot s,boolean detail){
        ClanRecruitment live=liveClanSettings.get(c.name.toLowerCase(Locale.ROOT));
        int setting=c.joinSetting,type=c.joinType;
        if(live!=null&&live.clanId==c.clanId){setting=live.joinSetting;type=live.joinType;}
        b.append("{\"name\":");json(b,c.name);
        b.append(",\"leader\":");json(b,c.leader);
        b.append(",\"castle\":");json(b,castleName(c.castleId));
        b.append(",\"house\":").append(c.house>0);
        b.append(",\"members\":").append(c.members);
        b.append(",\"joinSetting\":").append(setting);
        b.append(",\"joinType\":").append(type);
        if(detail){
            b.append(",\"roster\":[");int n=0;
            for(Character member:s.characters){
                if(!member.clan.equalsIgnoreCase(c.name))continue;
                if(n++>=40)break;
                if(n>1)b.append(',');
                b.append("{\"name\":");json(b,member.name);
                b.append(",\"level\":").append(member.level);
                b.append(",\"classId\":").append(member.type).append('}');
            }
            b.append(']');
        }
        b.append('}');
    }
    private static String clans(Snapshot s,Map<String,String> p){
        String exact=p.get("name");
        if(exact!=null){
            StringBuilder detail=new StringBuilder(4096).append("{\"items\":[");
            for(Clan c:s.clans)if(c.name.equalsIgnoreCase(exact)){
                appendClan(detail,c,s,true);break;
            }
            return detail.append("],\"more\":false}").toString();
        }
        String term=p.getOrDefault("q","").strip().toLowerCase(Locale.ROOT);
        int begin=(page(p)-1)*20,count=0;
        StringBuilder b=new StringBuilder(4096).append("{\"items\":[");
        for(Clan c:s.clans){
            if(!term.isEmpty() && !c.name.toLowerCase(Locale.ROOT).contains(term) &&
               !c.leader.toLowerCase(Locale.ROOT).contains(term))continue;
            if(count>=begin&&count<begin+20){
                if(count>begin)b.append(',');
                appendClan(b,c,s,false);
            }
            count++;if(count>begin+20)break;
        }
        return b.append("],\"more\":").append(count>begin+20).append('}').toString();
    }
    private static String castleName(int id){
        return switch(id){case 1->"켄트";case 2->"오크 요새";case 3->"윈다우드";
            case 4->"기란";case 5->"하이네";case 6->"도와";
            case 7->"아덴";case 8->"디아드";default->"";};
    }
    private static List<Market> refreshMarket() {
        ArrayList<Market> result = new ArrayList<>(1024);
        // Read only the market map and shops already in the world registry.
        // Avoid a JDBC connection and never hold world locks across HTTP requests.
        for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
            if (pc == null || pc.getMapId() != 800 || !pc.isPrivateShop()) continue;
            String seller = validText(pc.getName(),32);
            if (seller.isEmpty()) continue;
            final int x=pc.getX(),y=pc.getY();
            try {
                appendListings(result,pc.getSellings(),seller,x,y,0);
                appendListings(result,pc.getPurchasings(),seller,x,y,1);
            } catch (IndexOutOfBoundsException | java.util.ConcurrentModificationException transientChange) {
                // A shop edited its mutable listing while this worker read it.
                // A complete list will be picked up on the next five-second tick.
            }
            if (result.size() >= MAX_SHOP_ROWS) break;
        }
        // Offline/stationary shops are L1NpcShopInstance, not L1PcInstance.
        // They are registered separately in L1World and otherwise never appear
        // in market search despite having active stock.
        for (L1NpcShopInstance npc : L1World.getInstance().getAllShopNpc()) {
            if (npc == null || npc.getState() != 1 || npc.getMapId() != 800) continue;
            String seller=validText(npc.getName(),32);
            if(seller.isEmpty()) continue;
            int x=npc.getX(),y=npc.getY();
            try {
                appendListings(result,npc.getSellings(),seller,x,y,0);
                appendListings(result,npc.getPurchasings(),seller,x,y,1);
            } catch (IndexOutOfBoundsException | java.util.ConcurrentModificationException changed) {
                // A racing shop refill will be captured on a later snapshot.
            }
            if(result.size()>=MAX_SHOP_ROWS)break;
        }
        result.sort(Comparator.comparing((Market m)->m.name).thenComparingLong(m->m.low)
            .thenComparing(m->m.seller));
        return List.copyOf(result);
    }
    private static void appendListings(List<Market> out,List<MJDShopItem> listings,
                                       String seller,int x,int y,int kind) {
        if (listings == null) return;
        int count=Math.min(listings.size(),7);
        for (int i=0;i<count&&out.size()<MAX_SHOP_ROWS;i++) {
            MJDShopItem item=listings.get(i);
            if (item==null || item.price<=0 || item.count<=0) continue;
            String name=validText(item.name,80);
            if (name.isEmpty()) continue;
            out.add(new Market(name,seller,item.enchant,kind,item.invId,
                               item.price,item.count,x,y));
        }
    }
    // Invoked only by the authenticated game-socket opcode that receives
    // a native /시세 item-name search; never authorizes a game action over HTTP.
    public static void registerMarketSearch(L1PcInstance pc,String raw) {
        if (pc==null || raw==null || listener==null) return;
        CIOClient client=pc.getNetConnection();
        if (client==null || !client.isConnected()) return;
        String term=validText(raw.strip(),MAX_QUERY);
        if (term.length()<2 || term.length()>MAX_QUERY || !term.equals(raw.strip())) return;
        String ip=client.getIp();
        if (ip==null || ip.isEmpty()) return;
        if (pendingMarketSearch.size()>2048) pendingMarketSearch.clear();
        // If two game sessions behind the same NAT issue commands concurrently,
        // do not deliver one player's search to the other player's browser.
        // Repeated commands from the same character replace the older term.
        final long expiry=System.nanoTime()+15L*NANO;
        final int playerId=pc.getId();
        pendingMarketSearch.compute(ip,(key,old)->{
            if(old!=null && System.nanoTime()-old.deadlineNanos()<0 &&
                    old.playerId()!=playerId)return new Launch("",-1,expiry);
            if(old!=null && old.playerId()==-1 && System.nanoTime()-old.deadlineNanos()<0)
                return new Launch("",-1,expiry);
            return new Launch(term,playerId,expiry);
        });
    }
    private static String consumeMarketSearch(InetAddress client) {
        if (client==null) return "{\"found\":false}";
        Launch launch=pendingMarketSearch.remove(client.getHostAddress());
        if (launch==null || launch.term().isEmpty() || System.nanoTime()-launch.deadlineNanos()>=0)
            return "{\"found\":false}";
        StringBuilder b=new StringBuilder(96).append("{\"found\":true,\"q\":");
        json(b,launch.term());
        return b.append('}').toString();
    }

    private static Map<String,Profile> refreshProfiles(){
        HashMap<String,Profile> found=new HashMap<>();
        java.util.HashSet<String> ambiguous=new java.util.HashSet<>();
        for(L1PcInstance pc:L1World.getInstance().getAllPlayers()){
            if(pc==null)continue;
            CIOClient client=pc.getNetConnection();
            if(client==null||!client.isConnected())continue;
            String ip=client.getIp();
            if(ip==null||ip.isEmpty()||ambiguous.contains(ip))continue;
            String name=pc.getName();
            if(name==null||name.isEmpty())continue;
            Profile profile=new Profile(validText(name,32),pc.getLevel(),pc.getType(),
                pc.get_sex(),validText(pc.getClanname(),50));
            if(found.putIfAbsent(ip,profile)!=null){found.remove(ip);ambiguous.add(ip);}
        }
        return Map.copyOf(found);
    }
    private static String personalProfile(InetAddress remote){
        if(remote==null)return "{\"found\":false}";
        // IP is an availability hint, not proof of identity.  Only the public
        // character name/level may be displayed, and shared IPs are suppressed.
        Profile p=liveProfiles.get(remote.getHostAddress());
        if(p==null)return "{\"found\":false}";
        StringBuilder b=new StringBuilder(128).append("{\"found\":true,\"name\":");
        json(b,p.name);b.append(",\"level\":").append(p.level);
        b.append(",\"classId\":").append(p.classId);
        b.append(",\"sex\":").append(p.sex);
        b.append(",\"clan\":");json(b,p.clan);
        return b.append('}').toString();
    }
    private static void json(StringBuilder b,String s){
        b.append('"');
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            switch(c){
                case '"'->b.append("\\\""); case '\\'->b.append("\\\\");
                case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");
                case '<'->b.append("\\u003c");case '>'->b.append("\\u003e");
                case '&'->b.append("\\u0026");
                default->{if(c<32||c==0x2028||c==0x2029){
                    String hex=Integer.toHexString(c);b.append("\\u");for(int n=hex.length();n<4;n++)b.append('0');b.append(hex);
                }else b.append(c);}
            }
        }
        b.append('"');
    }
    private static void send(HttpExchange ex,int status,String contentType,byte[] bytes) throws IOException {
        send(ex, status, contentType, bytes, MAX_RESPONSE);
    }
    private static void send(HttpExchange ex,int status,String contentType,byte[] bytes,int limit) throws IOException {
        var h=ex.getResponseHeaders();
        h.set("Content-Type",contentType);
        String path=ex.getRequestURI().getRawPath();
        if(status==200 && path!=null && path.startsWith("/img/") &&
           AppCenterAssets.isStaticPath(path)){
            // Asset names are stable for a build; image caching prevents
            // navigation from repeatedly downloading expensive atlases.
            h.set("Cache-Control","private, max-age=3600");
        }else if(status==200 && ("/app.css".equals(path)||"/app.js".equals(path))){
            h.set("Cache-Control","no-cache, must-revalidate");
        }else{
            h.set("Cache-Control","no-store");
        }
        if(status==429||status==503)h.set("Retry-After","1");
        h.set("X-Content-Type-Options","nosniff");
        h.set("X-AppCenter-Build","RKern-AppCenter-PreRemaster-ClassPledgeMarket-20261106-v8");
        h.set("Referrer-Policy","no-referrer");h.set("X-Frame-Options","DENY");
        h.set("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; form-action 'none'; object-src 'none'; base-uri 'none'");
        // HttpServer manages keep-alive for bounded, completed response bodies.
        // Forcing close here created a new TCP connection for every small asset.
        if(bytes.length>limit){status=503;bytes=new byte[0];}
        if(bytes.length==0){ex.sendResponseHeaders(status,-1);return;}
        ex.sendResponseHeaders(status,bytes.length);
        try(OutputStream out=ex.getResponseBody()){out.write(bytes);}
    }
}
