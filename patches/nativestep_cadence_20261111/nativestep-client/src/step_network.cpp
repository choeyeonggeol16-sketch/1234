// First-test DLL-to-server control channel. TCP read happens off the game UI thread.
// Uses the existing game TCP connection's IPv4 destination and ephemeral source port
// for association, without touching client packets or the game's binary.
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h>
#include <iphlpapi.h>
#include <tcpmib.h>
#include <atomic>
#include <cstdio>
#include <cstring>
#include <cwchar>
#include "step_network.h"

namespace {
std::atomic<bool> running{false};
std::atomic<bool> started{false};
std::atomic<SOCKET> currentSocket{INVALID_SOCKET};
std::atomic<bool> manualStopRequested{false};
HMODULE dllModule = nullptr;

void event_signal(const wchar_t* suffix, bool state) {
    wchar_t name[120]{};
    wsprintfW(name, L"Local\\NativeStep.%s.%lu", suffix, GetCurrentProcessId());
    HANDLE e = OpenEventW(EVENT_MODIFY_STATE, FALSE, name);
    if (e) { if (state) SetEvent(e); else ResetEvent(e); CloseHandle(e); }
}

void log_status(const char* what, int code = 0) {
    char path[MAX_PATH]{};
    if (!GetModuleFileNameA(dllModule, path, MAX_PATH)) return;
    char* slash = strrchr(path, '\\');
    if (!slash) return;
    strcpy_s(slash + 1, MAX_PATH - (slash + 1 - path), "client_io.log");
    FILE* f = nullptr;
    if (fopen_s(&f, path, "a") == 0 && f) {
        fprintf(f, "pid=%lu %s code=%d\n", GetCurrentProcessId(), what, code);
        fclose(f);
    }
}

// The client is already connected to the game server.  Reuse that peer's IP;
// no public server address is embedded in this DLL or a client-side config.
constexpr int kGamePort = 5000;
constexpr int kControlPort = 18841;

struct GameSocket {
    DWORD remoteIPv4;
    unsigned short sourcePort;
    bool valid;
};

GameSocket locate_game_socket(int gamePort) {
    ULONG size = 0;
    DWORD ret = GetExtendedTcpTable(nullptr, &size, FALSE, AF_INET, TCP_TABLE_OWNER_PID_CONNECTIONS, 0);
    if (ret != ERROR_INSUFFICIENT_BUFFER || size == 0 || size > 16u * 1024u * 1024u) return {};
    BYTE* storage = static_cast<BYTE*>(HeapAlloc(GetProcessHeap(), 0, size));
    if (!storage) return {};
    GameSocket found{};
    ret = GetExtendedTcpTable(storage, &size, FALSE, AF_INET, TCP_TABLE_OWNER_PID_CONNECTIONS, 0);
    if (ret == NO_ERROR) {
        auto* table = reinterpret_cast<MIB_TCPTABLE_OWNER_PID*>(storage);
        for (DWORD n = 0; n < table->dwNumEntries; ++n) {
            const auto& r = table->table[n];
            if (r.dwOwningPid != GetCurrentProcessId() || r.dwState != MIB_TCP_STATE_ESTAB ||
                ntohs(static_cast<u_short>(r.dwRemotePort)) != gamePort) continue;
            found = {r.dwRemoteAddr, ntohs(static_cast<u_short>(r.dwLocalPort)), true};
            break;
        }
    }
    HeapFree(GetProcessHeap(), 0, storage);
    return found;
}

// The packet is tiny, so enforce a line maximum and never forward arbitrary text to UI.
bool accept_command(const char* line, int length) {
    if (length == 1 && line[0] == 'S') {
        log_status("item START received");
        NativeStep_PostNetworkCommand(9);
        return true;
    }
    if (length == 1 && line[0] == 'C') { NativeStep_PostNetworkCommand(8); return true; }
    if (length == 1 && line[0] == 'T') {
        log_status("item STOP received");
        NativeStep_PostNetworkCommand(10);
        return true;
    }
    if (length == 2 && line[0] == 'M' && line[1] >= '0' && line[1] <= '7') {
        static bool first_move_logged = false;
        if (!first_move_logged) {
            log_status("first direction received");
            first_move_logged = true;
        }
        NativeStep_PostNetworkCommand(line[1] - '0'); return true;
    }
    return false;
}

DWORD WINAPI network_loop(void* unused) {
    (void) unused;
    WSADATA data{};
    if (WSAStartup(MAKEWORD(2,2), &data) != 0) {
        log_status("WSAStartup failed");
        running.store(false);
        started.store(false);
        FreeLibraryAndExitThread(dllModule, 0);
    }
    log_status("network thread started");
    bool missing_game_logged = false;
    while (running.load(std::memory_order_acquire)) {
        const GameSocket game = locate_game_socket(kGamePort);
        if (!game.valid) {
            if (!missing_game_logged) log_status("waiting for game TCP connection");
            missing_game_logged = true;
            for (int n = 0; n < 10 && running.load(); ++n) Sleep(100);
            continue;
        }
        missing_game_logged = false;
        SOCKET sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        if (sock == INVALID_SOCKET) { Sleep(1000); continue; }
        currentSocket.store(sock);
        sockaddr_in dst{};
        dst.sin_family = AF_INET;
        dst.sin_addr.s_addr = game.remoteIPv4;
        dst.sin_port = htons(static_cast<u_short>(kControlPort));
        DWORD timeout = 250;
        setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, reinterpret_cast<const char*>(&timeout), sizeof(timeout));
        setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, reinterpret_cast<const char*>(&timeout), sizeof(timeout));
        bool paired = false;
        if (connect(sock, reinterpret_cast<sockaddr*>(&dst), sizeof(dst)) == SOCKET_ERROR) {
            log_status("connect control port failed", WSAGetLastError());
        } else {
            char hello[32]{};
            int len = sprintf_s(hello, "HELLO %hu\n", game.sourcePort);
            int sent = 0;
            while (running.load() && sent < len) {
                int written = send(sock, hello + sent, len - sent, 0);
                if (written <= 0) break;
                sent += written;
            }
            if (sent == len) {
                char buf[32]{};
                int got = 0;
                // TCP is a stream: OK\n may arrive split across multiple recv calls.
                while (running.load() && got < 3) {
                    int n = recv(sock, buf + got, 3 - got, 0);
                    if (n <= 0) break;
                    got += n;
                }
                if (got == 3 && buf[0] == 'O' && buf[1] == 'K' && buf[2] == '\n') {
                    paired = true;
                    event_signal(L"Linked", true);
                    log_status("paired to server");
                    // the next game action (item 49027) starts the automation.
                    int count = 0;
                    while (running.load() && paired) {
                        if (manualStopRequested.exchange(false)) {
                            const char stop[] = "U\n";
                            if (send(sock, stop, 2, 0) != 2) { paired = false; break; }
                        }
                        char input[32]{};
                        int received = recv(sock, input, sizeof(input), 0);
                        if (received == 0) break;
                        if (received == SOCKET_ERROR) {
                            if (WSAGetLastError() == WSAETIMEDOUT) continue;
                            break;
                        }
                        for (int i = 0; i < received; ++i) {
                            char c = input[i];
                            if (c == '\n') {
                                if (!accept_command(buf, count)) { paired = false; break; }
                                count = 0;
                            } else if (c >= 32 && c <= 126 && count < 5) {
                                buf[count++] = c;
                            } else { paired = false; break; }
                        }
                    }
                } else {
                    log_status("handshake rejected or timed out", WSAGetLastError());
                }
            } else { log_status("handshake send failed", WSAGetLastError()); }
        }
        event_signal(L"Linked", false);
        // On lost control connection never leave the synthetic button held.
        if (running.load()) NativeStep_PostNetworkCommand(10);
        currentSocket.store(INVALID_SOCKET);
        shutdown(sock, SD_BOTH);
        closesocket(sock);
        for (int n = 0; n < 10 && running.load(); ++n) Sleep(100);
    }
    event_signal(L"Linked", false);
    currentSocket.store(INVALID_SOCKET);
    WSACleanup();
    log_status("network thread stopped");
    running.store(false);
    started.store(false);
    FreeLibraryAndExitThread(dllModule, 0);
}
} // namespace

void NativeStep_NetworkStart(HMODULE module) {
    (void)module;
    if (started.exchange(true)) return;
    running.store(true);
    // Keep an independent reference so the DLL cannot unload while recv() is active.
    HMODULE hold = nullptr;
    if (!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,
           reinterpret_cast<LPCWSTR>(&NativeStep_NetworkStart), &hold)) {
        running.store(false);
        started.store(false);
        return;
    }
    dllModule = hold;
    HANDLE thread = CreateThread(nullptr, 0, network_loop, nullptr, 0, nullptr);
    if (!thread) { running.store(false); started.store(false); FreeLibrary(hold); }
    else CloseHandle(thread);
}

void NativeStep_NetworkStop() {
    running.store(false);
    SOCKET sock = currentSocket.load();
    if (sock != INVALID_SOCKET) shutdown(sock, SD_BOTH);
}

void NativeStep_LogInput(const char* event, int code) { log_status(event, code); }

void NativeStep_RequestManualStop() { manualStopRequested.store(true); }
