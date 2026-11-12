#include "CTransport.h"
#include <ws2tcpip.h>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <stdexcept>
#include <thread>
#include <vector>
#include <cstring>
static std::atomic<int> closed{0};
static void check(bool ok,const char* text){if(!ok)throw std::runtime_error(text);}
static int acceptEvent(void* server,std::uint64_t id,std::uint64_t){return CIO_Server_PauseReceive(server,id,0)?0:2;}
static int receiveEvent(void* server,std::uint64_t id,const void* bytes,int count){return CIO_Server_Send(server,id,bytes,count)?0:2;}
static int sendEvent(void*,std::uint64_t,const void*,int){return 0;}
static int closeEvent(void*,std::uint64_t,int,int){++closed;return 0;}
int main(){
 try{
  check(CTransport_Version()==0x60000,"native version");
  WSADATA data{};check(WSAStartup(MAKEWORD(2,2),&data)==0,"WSAStartup");
  void* listener=CIO_Create_TcpServerListener();check(listener,"listener allocation");
  CIO_Set_FN_Server_OnAccept(listener,acceptEvent);CIO_Set_FN_Server_OnReceive(listener,receiveEvent);
  CIO_Set_FN_Server_OnSend(listener,sendEvent);CIO_Set_FN_Server_OnClose(listener,closeEvent);
  void* server=CIO_Create_TcpServer(listener);check(server,"server allocation");
  CIO_Server_SetWorkerThreadCount(server,4);CIO_TcpServer_SetSocketBufferSize(server,4096);
  check(CIO_Server_Start(server,"127.0.0.1",0),"native start");
  char text[64];int capacity=64;unsigned short port=0;
  check(CIO_Server_GetListenAddress(server,text,&capacity,&port)&&port!=0,"bound port");
  std::vector<char> original(200000),received(original.size());
  for(std::size_t i=0;i<original.size();++i)original[i]=static_cast<char>(i*37);
  for(int round=0;round<20;++round){
   SOCKET client=socket(AF_INET,SOCK_STREAM,IPPROTO_TCP);check(client!=INVALID_SOCKET,"client socket");
   DWORD timeout=5000;setsockopt(client,SOL_SOCKET,SO_RCVTIMEO,reinterpret_cast<char*>(&timeout),sizeof(timeout));
   setsockopt(client,SOL_SOCKET,SO_SNDTIMEO,reinterpret_cast<char*>(&timeout),sizeof(timeout));
   sockaddr_in address{};address.sin_family=AF_INET;address.sin_port=htons(port);inet_pton(AF_INET,"127.0.0.1",&address.sin_addr);
   check(connect(client,reinterpret_cast<sockaddr*>(&address),sizeof(address))==0,"connect");
   std::size_t written=0;
   while(written<original.size()){
    const int n=send(client,original.data()+written,static_cast<int>(std::min<std::size_t>(137,original.size()-written)),0);
    check(n>0,"client send");written+=n;
   }
   std::size_t at=0;while(at<received.size()){int n=recv(client,received.data()+at,static_cast<int>(received.size()-at),0);check(n>0,"client receive");at+=n;}
   check(original==received,"echo bytes/order changed");closesocket(client);
  }
  const auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(5);
  while(closed.load()!=20&&std::chrono::steady_clock::now()<deadline)std::this_thread::yield();
  check(closed==20,"close callback count");check(CIO_StopAccepting(server),"accept stop");
  check(CIO_Server_Stop(server),"stop");check(CIO_Server_Wait(server,0),"wait");
  CIO_Destroy_TcpServer(server);CIO_Destroy_TcpServerListener(listener);WSACleanup();
  std::puts("PASS actual Windows DLL: 20 loopback connections, 4MB fragmented echo, order and close counts.");
  return 0;
 }catch(const std::exception& e){std::fprintf(stderr,"FAIL: %s (WSA %d)\n",e.what(),WSAGetLastError());return 1;}
}
