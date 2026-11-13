from pathlib import Path
import argparse,hashlib,json,re
import pefile,capstone
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--root',type=Path,default=root);args=p.parse_args();root=args.root
dll=root/'build/CTransport.dll';pe=pefile.PE(str(dll))
assert pe.FILE_HEADER.Machine==0x8664 and pe.OPTIONAL_HEADER.Magic==0x20b
exports={v.name.decode():v for v in pe.DIRECTORY_ENTRY_EXPORT.symbols if v.name}
java=(root/'src/l1j/server/server/model/network/core/CIOServer.java').read_text()
symbols=set(re.findall(r'fn\("([^"]+)"',java))
assert symbols<=set(exports),(symbols-set(exports))
assert len(exports)==66 and not any(v.startswith(('HP_','Create_HP_')) for v in exports)
sha=hashlib.sha256(dll.read_bytes()).hexdigest()
assert sha in (root/'src/l1j/server/server/model/network/core/CIOConfig.java').read_text()
version=exports['CTransport_Version']
md=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64)
instructions=[]
for ins in md.disasm(pe.get_data(version.address,48),pe.OPTIONAL_HEADER.ImageBase+version.address):
 instructions.append((ins.mnemonic,ins.op_str))
 if ins.mnemonic=='ret':break
assert ('mov','eax, 0x80000') in instructions and instructions[-1][0]=='ret',instructions
imports={v.name.decode() for m in pe.DIRECTORY_ENTRY_IMPORT for v in m.imports if v.name}
assert {'GetQueuedCompletionStatusEx','PostQueuedCompletionStatus','CreateIoCompletionPort','WSARecv','WSASend','shutdown','GetModuleHandleW','GetProcAddress'}<=imports
assert 'GetQueuedCompletionStatus' not in imports
assert {'WSAEventSelect','WSACreateEvent','WSAResetEvent','WSACloseEvent','WaitForMultipleObjects','accept','SetThreadAffinityMask'} <= imports
assert 'AcceptEx' not in (root/'native/CTransport.cpp').read_text()
assert 'CIOConfig.DLL' in java and 'CIOWinTransport.dll' not in java
assert b'RtlNtStatusToDosError' in dll.read_bytes()
deps=[m.dll.decode() for m in pe.DIRECTORY_ENTRY_IMPORT]
assert not any('HPSocket' in n or 'CIOWinTransport' in n for n in deps)
selftest=pefile.PE(str(root/'build/CTransport_SelfTest.exe'))
assert selftest.FILE_HEADER.Machine==0x8664
assert any(m.dll.decode().lower()=='ctransport.dll' for m in selftest.DIRECTORY_ENTRY_IMPORT)
print('PASS PE64 DLL, all 66 required exports, native ABI 0x80000, exact Java SHA256 pin')
print('PASS batch IOCP/Winsock imports, per-entry NTSTATUS resolver, independent of HP-Socket DLL; self-test EXE imports this DLL')
print(json.dumps({'dll_sha256':sha,'imports':deps,'exports':sorted(exports),'pe_timestamp':pe.FILE_HEADER.TimeDateStamp},indent=2))
