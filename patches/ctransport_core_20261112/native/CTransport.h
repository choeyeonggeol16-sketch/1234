#pragma once
#include <winsock2.h>
#include <cstdint>
extern "C" {
__declspec(dllimport) unsigned CTransport_Version();
__declspec(dllimport) void* CIO_Create_TcpServerListener();
__declspec(dllimport) void CIO_Destroy_TcpServerListener(void*);
__declspec(dllimport) void* CIO_Create_TcpServer(void*);
__declspec(dllimport) void CIO_Destroy_TcpServer(void*);
__declspec(dllimport) void CIO_Set_FN_Server_OnAccept(void*,int (*)(void*,std::uint64_t,std::uint64_t));
__declspec(dllimport) void CIO_Set_FN_Server_OnReceive(void*,int (*)(void*,std::uint64_t,const void*,int));
__declspec(dllimport) void CIO_Set_FN_Server_OnSend(void*,int (*)(void*,std::uint64_t,const void*,int));
__declspec(dllimport) void CIO_Set_FN_Server_OnClose(void*,int (*)(void*,std::uint64_t,int,int));
__declspec(dllimport) int CIO_Server_Start(void*,const char*,unsigned short);
__declspec(dllimport) int CIO_StopAccepting(void*);
__declspec(dllimport) int CIO_Server_Stop(void*);
__declspec(dllimport) int CIO_Server_Wait(void*,unsigned);
__declspec(dllimport) int CIO_Server_Disconnect(void*,std::uint64_t,int);
__declspec(dllimport) int CIO_Server_PauseReceive(void*,std::uint64_t,int);
__declspec(dllimport) int CIO_Server_Send(void*,std::uint64_t,const void*,int);
__declspec(dllimport) int CIO_Server_SendPackets(void*,std::uint64_t,const WSABUF*,int);
__declspec(dllimport) int CIO_Server_GetListenAddress(void*,char*,int*,unsigned short*);
__declspec(dllimport) int CIO_Server_GetLocalAddress(void*,std::uint64_t,char*,int*,unsigned short*);
__declspec(dllimport) int CIO_Server_GetRemoteAddress(void*,std::uint64_t,char*,int*,unsigned short*);
__declspec(dllimport) void CIO_Server_SetMaxConnectionCount(void*,int);
__declspec(dllimport) void CIO_Server_SetWorkerThreadCount(void*,int);
__declspec(dllimport) void CIO_TcpServer_SetSocketListenQueue(void*,int);
__declspec(dllimport) void CIO_TcpServer_SetSocketBufferSize(void*,int);
__declspec(dllimport) void CIO_TcpServer_SetNoDelay(void*,int);
__declspec(dllimport) void CIO_Server_SetSendPolicy(void*,int);
__declspec(dllimport) void CIO_Server_SetOnSendSyncPolicy(void*,int);
__declspec(dllimport) void CTransport_SetTaskCallback(void*,void (*)(void*,std::uint64_t,int));
__declspec(dllimport) int CTransport_Server_PostTask(void*,std::uint64_t);
__declspec(dllimport) int CTransport_Server_Schedule(void*,std::uint64_t,unsigned);
__declspec(dllimport) int CTransport_Server_Cancel(void*,std::uint64_t);
__declspec(dllimport) void* CTransport_Server_RetainReceiveBuffer(void*,std::uint64_t);
__declspec(dllimport) const void* CTransport_Buffer_Data(void*);
__declspec(dllimport) unsigned CTransport_Buffer_Size(void*);
__declspec(dllimport) void CTransport_Buffer_Release(void*);
__declspec(dllimport) void CTransport_SetClosingCallback(void*,int (*)(void*,std::uint64_t,int,int));
__declspec(dllimport) int CTransport_Server_GetDiagnostics(void*,std::uint64_t*,unsigned);
// L1 connection profile: native frame/cipher ownership and leased plaintext.
__declspec(dllimport) int CTransport_Server_SetCodecApi(void*,const std::uintptr_t*,int);
__declspec(dllimport) void CTransport_SetUserPacketCallback(void*,int (*)(void*,std::uint64_t,std::uint64_t,const void*,int));
__declspec(dllimport) void CTransport_SetNetworkDeadlineCallback(void*,int (*)(void*,std::uint64_t));
__declspec(dllimport) int CTransport_UserSocket_Initialize(void*,std::uint64_t,unsigned);
__declspec(dllimport) int CTransport_UserSocket_ReleasePacket(void*,std::uint64_t,std::uint64_t);
__declspec(dllimport) int CTransport_UserSocket_Encrypt(void*,std::uint64_t,void*,int);
__declspec(dllimport) int CTransport_UserSocket_SetSessionState(void*,std::uint64_t,int);
__declspec(dllimport) int CTransport_UserSocket_ConfigureDeadlines(void*,std::uint64_t,unsigned);
__declspec(dllimport) int CTransport_UserSocket_RequestDeadline(void*,std::uint64_t,unsigned);
__declspec(dllimport) int CTransport_UserSocket_GetState(void*,std::uint64_t,std::uint64_t*,int);
__declspec(dllimport) int CTransport_Server_GetRegistryDiagnostics(void*,std::uint64_t*,int);
__declspec(dllimport) int CTransport_Server_ThreadOwner(void*);
__declspec(dllimport) int CTransport_Server_ConfigureLimits(void*,unsigned,unsigned);
__declspec(dllimport) int CTransport_Server_GetLimiterDiagnostics(void*,std::uint64_t*,int);
__declspec(dllimport) int CTransport_Server_GetListenerDiagnostics(void*,std::uint64_t*,int);
__declspec(dllimport) int CTransport_Server_GetThreadDiagnostics(void*,std::uint64_t*,int);
__declspec(dllimport) std::uint64_t CTransport_UserSocket_FindByNumber(void*,unsigned);
__declspec(dllimport) int CTransport_UserSocket_SetMacIdentity(void*,std::uint64_t,std::uint64_t);
__declspec(dllimport) int CTransport_Server_ReportSockets(void*,std::uint64_t*,int,int);
__declspec(dllimport) int CTransport_UserSocket_SetAccountIdentity(void*,std::uint64_t,unsigned);
__declspec(dllimport) int CTransport_UserSocket_SetCharacterEntered(void*,std::uint64_t,int);
__declspec(dllimport) std::uint64_t CTransport_UserSocket_FindEnteringAccount(void*,unsigned);
__declspec(dllimport) int CTransport_Server_GetAccountRegistryDiagnostics(void*,std::uint64_t*,int);
__declspec(dllimport) int CTransport_Server_ConfigurePerf(void*,unsigned);
__declspec(dllimport) int CTransport_Server_GetPerfDiagnostics(void*,std::uint64_t*,int);
}
