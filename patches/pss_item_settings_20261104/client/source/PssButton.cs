using System;
using System.IO;
using System.Diagnostics;
using System.Security.Cryptography;
using System.Text;
using System.Runtime.InteropServices;
using System.Windows.Forms;
using System.Drawing;
using System.Collections.Generic;

class PssButton : Form {
 const string TargetHash="9C82129EFED1D93981782069E82DE79869920769AA0F67BC1183CA75CF621823";
 const uint Control=0x8000+0x5A1;
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)]static extern IntPtr LoadLibrary(string path);
 [DllImport("kernel32.dll",CharSet=CharSet.Ansi,SetLastError=true)]static extern IntPtr GetProcAddress(IntPtr h,string name);
 [DllImport("kernel32.dll")]static extern bool FreeLibrary(IntPtr h);
 [DllImport("kernel32.dll",SetLastError=true)]static extern IntPtr OpenProcess(uint rights,bool inherit,int pid);
 [DllImport("kernel32.dll",SetLastError=true)]static extern IntPtr VirtualAllocEx(IntPtr process,IntPtr address,UIntPtr size,uint type,uint protection);
 [DllImport("kernel32.dll")]static extern bool VirtualFreeEx(IntPtr process,IntPtr address,UIntPtr size,uint type);
 [DllImport("kernel32.dll",SetLastError=true)]static extern bool WriteProcessMemory(IntPtr process,IntPtr address,byte[] data,UIntPtr size,out UIntPtr written);
 [DllImport("kernel32.dll",SetLastError=true)]static extern IntPtr CreateRemoteThread(IntPtr process,IntPtr attributes,UIntPtr stack,IntPtr start,IntPtr param,uint flags,IntPtr threadId);
 [DllImport("kernel32.dll")]static extern uint WaitForSingleObject(IntPtr h,uint milliseconds);
 [DllImport("kernel32.dll")]static extern bool GetExitCodeThread(IntPtr h,out uint code);
 [DllImport("kernel32.dll")]static extern bool CloseHandle(IntPtr h);
 [DllImport("user32.dll")]static extern uint GetWindowThreadProcessId(IntPtr hwnd,out uint pid);
 [DllImport("user32.dll")]static extern bool PostMessage(IntPtr hwnd,uint msg,IntPtr wp,IntPtr lp);
 [DllImport("user32.dll")]static extern IntPtr SendMessageTimeout(IntPtr hwnd,uint msg,IntPtr wp,IntPtr lp,uint flags,uint timeout,out IntPtr result);
 [DllImport("user32.dll")]static extern bool EnumWindows(EnumProc cb,IntPtr lp);
 [DllImport("user32.dll")]static extern bool IsWindowVisible(IntPtr hwnd);
 [DllImport("user32.dll")]static extern IntPtr GetWindow(IntPtr hwnd,uint cmd);
 delegate bool EnumProc(IntPtr hwnd,IntPtr lp);
 class Item {
  public int Id; public string Path;public IntPtr Window;
  public override string ToString(){return System.IO.Path.GetFileName(Path)+" (PID "+Id+")";}
 }
 ComboBox clients=new ComboBox(); TextBox log=new TextBox();Label status=new Label();Button attach=new Button();
 bool connected;IntPtr module,window; string root=AppDomain.CurrentDomain.BaseDirectory;Timer timer=new Timer();
 PssButton(){
  Text="2019 PSS 버튼 · 시험본";ClientSize=new Size(570,350);StartPosition=FormStartPosition.CenterScreen;
  Font=new Font("맑은 고딕",9);FormBorderStyle=FormBorderStyle.FixedDialog;MaximizeBox=false;
  Controls.Add(new Label{Text="19,02,15,1002 클라이언트를 실행하고 캐릭터에 접속한 뒤 연결하세요.",Left=14,Top=14,Width=545,Height=38});
  clients.SetBounds(14,56,340,28);clients.DropDownStyle=ComboBoxStyle.DropDownList;Controls.Add(clients);
  Button refresh=new Button{Text="새로고침",Left=363,Top=54,Width=88,Height=30};refresh.Click+=(s,e)=>RefreshClients();Controls.Add(refresh);
  attach.Text="연결";attach.SetBounds(460,54,94,30);attach.Click+=(s,e)=>Attach();Controls.Add(attach);
  Button show=new Button{Text="PSS 버튼 다시 표시",Left=14,Top=95,Width=180,Height=30};show.Click+=(s,e)=>{if(window!=IntPtr.Zero)PostMessage(window,Control,(IntPtr)1,IntPtr.Zero);};Controls.Add(show);
  Button detach=new Button{Text="연결 해제",Left=204,Top=95,Width=100,Height=30};detach.Click+=(s,e)=>Detach();Controls.Add(detach);
  status.Text="실행 중인 대상 확인 중";status.SetBounds(14,137,540,24);Controls.Add(status);
  log.SetBounds(14,166,540,166);log.Multiline=true;log.ReadOnly=true;log.ScrollBars=ScrollBars.Vertical;Controls.Add(log);
  timer.Interval=1000;timer.Tick+=(s,e)=>ReadLog();timer.Start();Shown+=(s,e)=>RefreshClients();
  FormClosing+=(s,e)=>Detach();
 }
 void RefreshClients(){
  clients.Items.Clear();
  foreach(Process p in Process.GetProcesses())try{
   if(p.HasExited||!p.ProcessName.EndsWith(".bin",StringComparison.OrdinalIgnoreCase)&&p.ProcessName.IndexOf("lin",StringComparison.OrdinalIgnoreCase)<0&&p.ProcessName.IndexOf("amazon",StringComparison.OrdinalIgnoreCase)<0)continue;
   string path=p.MainModule.FileName;
   FileVersionInfo version=FileVersionInfo.GetVersionInfo(path);
   if(version.FileMajorPart!=19||version.FileMinorPart!=2||version.FileBuildPart!=15||version.FilePrivatePart!=1002)continue;
   IntPtr hwnd=IntPtr.Zero;EnumWindows((h,l)=>{uint id;GetWindowThreadProcessId(h,out id);if(id==p.Id&&IsWindowVisible(h)&&GetWindow(h,4)==IntPtr.Zero){hwnd=h;return false;}return true;},IntPtr.Zero);
   if(hwnd!=IntPtr.Zero)clients.Items.Add(new Item{Id=p.Id,Path=path,Window=hwnd});
  }catch{}
  if(clients.Items.Count>0)clients.SelectedIndex=0;
  status.Text=clients.Items.Count==0?"대상 없음 — 2019년 클라이언트를 먼저 실행하세요.":"연결할 클라이언트를 선택하세요.";
 }
 void Attach(){
  if(connected){status.Text="이미 연결되어 있습니다.";return;}
  Item item=clients.SelectedItem as Item;if(item==null){status.Text="실행 중인 대상이 없습니다.";return;}
  try{
   string hash;using(SHA256 sha=SHA256.Create())using(FileStream f=File.OpenRead(item.Path))hash=BitConverter.ToString(sha.ComputeHash(f)).Replace("-","");
   if(hash!=TargetHash){status.Text="원본 해시 불일치 — 적용하지 않았습니다.";return;}
   string dll=System.IO.Path.Combine(root,"Pss2019.dll");
   module=LoadLibrary(dll);if(module==IntPtr.Zero)throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
   IntPtr callback=GetProcAddress(module,"PssAttach@4");
   if(callback==IntPtr.Zero)callback=GetProcAddress(module,"PssAttach");
   if(callback==IntPtr.Zero)throw new Exception("PssAttach 내보내기를 찾지 못했습니다.");
   uint id;GetWindowThreadProcessId(item.Window,out id);
   if(id!=item.Id)throw new Exception("대상 창이 바뀌었습니다. 새로고침하세요.");
   using(Process process=Process.GetProcessById(item.Id)){
    IntPtr handle=OpenProcess(0x43A,false,item.Id);
    if(handle==IntPtr.Zero)throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
    IntPtr pathMemory=IntPtr.Zero;bool pathInUse=false;
    try{
     byte[] pathBytes=Encoding.Unicode.GetBytes(dll+"\0");
     pathMemory=VirtualAllocEx(handle,IntPtr.Zero,(UIntPtr)pathBytes.Length,0x3000,4);
     if(pathMemory==IntPtr.Zero)throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
     UIntPtr written;
     if(!WriteProcessMemory(handle,pathMemory,pathBytes,(UIntPtr)pathBytes.Length,out written)||written.ToUInt32()!=pathBytes.Length)throw new Exception("모듈 경로 전달 실패");
     IntPtr kernel=LoadLibrary("kernel32.dll");IntPtr loader=GetProcAddress(kernel,"LoadLibraryW");FreeLibrary(kernel);
     IntPtr remoteLoader=ResolveRemote(process,loader);
     pathInUse=true;uint remoteModule=CallRemote(handle,remoteLoader,pathMemory);pathInUse=false;
     if(remoteModule==0)throw new Exception("대상에서 PSS 모듈을 불러오지 못했습니다.");
     IntPtr remoteAttach=new IntPtr(unchecked((int)(remoteModule+(uint)(callback.ToInt64()-module.ToInt64()))));
     if(CallRemote(handle,remoteAttach,item.Window)!=1)throw new Exception("대상 코드 검증 또는 창 연결 실패");
    }finally{if(pathMemory!=IntPtr.Zero&&!pathInUse)VirtualFreeEx(handle,pathMemory,UIntPtr.Zero,0x8000);CloseHandle(handle);}
   }
   connected=true;
   window=item.Window;IntPtr sent;SendMessageTimeout(window,0,IntPtr.Zero,IntPtr.Zero,2,2000,out sent);
   status.Text="연결 요청됨 — 실제 적용 결과는 아래 기록을 확인하세요.";
  }catch(Exception ex){Detach();status.Text="연결 실패: "+ex.Message;}
 }
 void Detach(){
  if(window!=IntPtr.Zero){IntPtr result;SendMessageTimeout(window,Control,(IntPtr)2,IntPtr.Zero,2,1500,out result);}
  connected=false;
  if(module!=IntPtr.Zero){FreeLibrary(module);module=IntPtr.Zero;}
  window=IntPtr.Zero;status.Text="연결 해제됨";
 }
 static uint CallRemote(IntPtr process,IntPtr fn,IntPtr param){
  IntPtr thread=CreateRemoteThread(process,IntPtr.Zero,UIntPtr.Zero,fn,param,0,IntPtr.Zero);
  if(thread==IntPtr.Zero)throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
  try{if(WaitForSingleObject(thread,10000)!=0)throw new Exception("모듈 연결 응답 시간 초과");uint result;if(!GetExitCodeThread(thread,out result))throw new Exception("연결 결과 확인 실패");return result;}
  finally{CloseHandle(thread);}
 }
 static IntPtr ResolveRemote(Process target,IntPtr function){
  long addr=unchecked((uint)function.ToInt32());string name=null;long offset=0;
  using(Process local=Process.GetCurrentProcess())foreach(ProcessModule m in local.Modules){
   long start=unchecked((uint)m.BaseAddress.ToInt32());
   if(addr>=start&&addr<start+m.ModuleMemorySize){name=m.ModuleName;offset=addr-start;break;}
  }
  if(name==null)throw new Exception("시스템 함수 위치 확인 실패");
  target.Refresh();foreach(ProcessModule m in target.Modules)if(String.Equals(m.ModuleName,name,StringComparison.OrdinalIgnoreCase))return new IntPtr(unchecked((int)(unchecked((uint)m.BaseAddress.ToInt32())+offset)));
  throw new Exception("대상 시스템 모듈을 찾지 못했습니다.");
 }
 void ReadLog(){try{
  string p=System.IO.Path.Combine(root,"PSS2019_trace.txt");
  if(File.Exists(p)){string text=File.ReadAllText(p,Encoding.Unicode);if(log.Text!=text){log.Text=text;log.SelectionStart=log.TextLength;log.ScrollToCaret();}}
 }catch{}}
 [STAThread]static void Main(){Application.EnableVisualStyles();Application.Run(new PssButton());}
}
