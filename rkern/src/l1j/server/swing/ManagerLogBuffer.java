package l1j.server.swing;

import java.awt.EventQueue;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.swing.JTextArea;
import javax.swing.text.*;

/** Bounded, best-effort screen log. Durable audit files are independent. */
public final class ManagerLogBuffer {
    public static final int MAX_PENDING=1024, MAX_PENDING_CHARS=256*1024;
    public static final int MAX_MESSAGE_CHARS=2048, MAX_DOCUMENT_CHARS=64*1024, MAX_LINES=300;
    private record Entry(JTextArea area,String text) {}
    private static final ArrayDeque<Entry> QUEUE=new ArrayDeque<>();
    private static int pendingChars;
    private static long dropped;
    private static boolean scheduled;
    private ManagerLogBuffer() {}
    public static void install(JTextArea area) {
        if(area.getDocument() instanceof AbstractDocument doc && !(doc.getDocumentFilter() instanceof Limit))
            doc.setDocumentFilter(new Limit());
        if(area.getCaret() instanceof DefaultCaret caret)caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
    }
    public static void append(JTextArea area,String message) {
        if(area==null||message==null||message.isEmpty())return;
        String text=message.length()>MAX_MESSAGE_CHARS?message.substring(message.length()-MAX_MESSAGE_CHARS):message;
        if(!text.endsWith("\n"))text+='\n';
        boolean post=false;
        synchronized(QUEUE){
            while(!QUEUE.isEmpty()&&(QUEUE.size()>=MAX_PENDING||pendingChars+text.length()>MAX_PENDING_CHARS)){
                pendingChars-=QUEUE.removeFirst().text.length();++dropped;
            }
            QUEUE.addLast(new Entry(area,text));pendingChars+=text.length();
            if(!scheduled){scheduled=true;post=true;}
        }
        if(post)EventQueue.invokeLater(ManagerLogBuffer::drain);
    }
    private static void drain(){
        Map<JTextArea,StringBuilder> batch=new IdentityHashMap<>();
        synchronized(QUEUE){
            for(int i=0;i<128&&!QUEUE.isEmpty();i++){
                Entry e=QUEUE.removeFirst();pendingChars-=e.text.length();
                batch.computeIfAbsent(e.area,k->new StringBuilder()).append(e.text);
            }
        }
        try{
            batch.forEach((area,text)->{try{
                install(area);area.append(text.toString());area.setCaretPosition(area.getDocument().getLength());
            }catch(RuntimeException ignored){/* An optional display must not fail the audit/game path. */}});
        }finally{
            boolean again;
            synchronized(QUEUE){again=!QUEUE.isEmpty();if(!again)scheduled=false;}
            if(again)EventQueue.invokeLater(ManagerLogBuffer::drain);
        }
    }
    public static long[] diagnostics(){synchronized(QUEUE){return new long[]{QUEUE.size(),pendingChars,dropped,scheduled?1:0};}}
    private static final class Limit extends DocumentFilter {
        @Override public void insertString(FilterBypass fb,int offset,String s,AttributeSet a)throws BadLocationException{replace(fb,offset,0,s,a);}
        @Override public void replace(FilterBypass fb,int offset,int length,String s,AttributeSet a)throws BadLocationException{
            if(s!=null&&s.length()>MAX_DOCUMENT_CHARS)s=s.substring(s.length()-MAX_DOCUMENT_CHARS);
            fb.replace(offset,length,s,a);
            Document doc=fb.getDocument();int extra=doc.getLength()-MAX_DOCUMENT_CHARS;
            if(extra>0)fb.remove(0,extra);
            Element lines=doc.getDefaultRootElement();int excess=lines.getElementCount()-MAX_LINES;
            if(excess>0)fb.remove(0,Math.min(doc.getLength(),lines.getElement(excess-1).getEndOffset()));
        }
    }
}
