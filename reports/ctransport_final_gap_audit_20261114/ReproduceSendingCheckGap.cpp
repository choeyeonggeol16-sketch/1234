// Evidence-only Linux fixture. Calls the current declared methods safely;
// does not call a function through an incompatible function pointer.
#define CTRANSPORT_TEST
#include "CTransport.cpp"
#include <cassert>
#include <iostream>
#include <type_traits>

int main() {
    static_assert(std::is_same_v<decltype(&CUserSocket::Slot15SendAllowed),
                  bool (CUserSocket::*)() const noexcept>);
    auto* callbacks = L1Interop_CreateCallbacks();
    auto* server = static_cast<Server*>(L1Interop_CreateServer(callbacks));
    assert(server);
    auto value = WSASocketW(AF_INET, SOCK_STREAM, 0, nullptr, 0, 0);
    auto* user = static_cast<CUserSocket*>(CUserSocket::Allocate(server, value));
    assert(user && value != INVALID_SOCKET);

    user->reservedSendBytes = 0x180001;
    const int margin = 0x80000;
    const bool original15 = static_cast<int>(user->reservedSendBytes) + margin <= 0x200000;
    const bool current15 = user->Slot15SendAllowed();
    assert(!original15 && current15);
    std::cout << "REPRODUCED slot15: field+0x20=1572865, margin=524288; original condition=false, current method=true (no margin parameter)\n";

    user->reservedSendBytes = 0x200001;
    const bool original14 = static_cast<int>(user->reservedSendBytes) <= 0x200000;
    const bool current14 = user->Slot14SendAllowed();
    assert(!original14 && current14);
    std::cout << "REPRODUCED slot14: field+0x20=2097153; original condition=false, current method=true\n";

    // Production SEND has its own admission guard; confirm this discrepancy
    // does not mean it admits more than the existing 2MiB queue bound.
    user->reservedSendBytes = 0x200000;
    const unsigned char byte = 0x12;
    Span span{&byte, 1};
    assert(!user->send(&span, 1));
    std::cout << "PASS independent production send(): 1 additional byte rejected when reservations equal 2MiB\n";

    user->reservedSendBytes = 0;
    user->release(ObjectReferenceType::ORT_CREATE_DELETE);
    server->release();
    L1Interop_ReleaseCallbacks(callbacks);
    std::array<std::uint64_t, ObjectAllocator::ReportWords> heap{};
    ObjectAllocator::Instance().Snapshot(heap.data());
    assert(heap[1] == 0 && heap[2] == 0);
    std::cout << "PASS fixture cleanup: zero live allocator objects and bytes\n";
}
