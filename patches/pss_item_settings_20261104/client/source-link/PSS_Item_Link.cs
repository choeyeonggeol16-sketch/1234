using System;
using System.IO;
using System.Text;
using System.Net;
using System.Net.Sockets;
using System.Diagnostics;
using System.ComponentModel;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Runtime.InteropServices;

// Shared by the Windows companion and the cross-platform wire contract test.
public static class PssItemProtocol {
    public static void Run(Socket socket, byte[] hello, Func<bool> alive, Action ready, Action open) {
        socket.Blocking = false;
        int sent = 0;
        while (sent < hello.Length) {
            if (!socket.Poll(1000000, SelectMode.SelectWrite)) throw new IOException("HELLO send timeout.");
            int n = socket.Send(hello, sent, hello.Length - sent, SocketFlags.None);
            if (n <= 0) throw new IOException("HELLO send failed.");
            sent += n;
        }
        Stopwatch timer = Stopwatch.StartNew(); long lastPing = 0; bool bound = false;
        byte[] data = new byte[1], ping = new byte[]{2};
        while (alive()) {
            if (socket.Poll(250000, SelectMode.SelectRead)) {
                int n = socket.Receive(data);
                if (n != 1) throw new IOException("Companion disconnected.");
                if (data[0] == 16 && !bound) { bound = true; ready(); }
                else if (data[0] == 1 && bound) open();
                else throw new InvalidDataException("Unknown companion command.");
            }
            if (!bound && timer.ElapsedMilliseconds >= 5000) throw new IOException("Pairing pending.");
            if (bound && timer.ElapsedMilliseconds - lastPing >= 10000) {
                if (!socket.Poll(1000000, SelectMode.SelectWrite) || socket.Send(ping) != 1) throw new IOException("PING failed.");
                lastPing = timer.ElapsedMilliseconds;
            }
        }
    }
}

// Source-only fallback for the ORIGINAL, already working Pss2019 helper.
// A single in-game pairing command authorizes a window-only connection.
public static class PssItemLink {
    const string ClientHash = "9C82129EFED1D93981782069E82DE79869920769AA0F67BC1183CA75CF621823";
    const uint Show = 0x7918D0, Branch = Show + 8;
    static readonly byte[] Original = {0x55,0x8B,0xEC,0x80,0x7D,0x08,0,0x75,0x0D,0xC7,0x45,0x08,0,0,0,0,0x5D,0xE9,0xBA,0xC1,0x4C,0,0x5D,0xC2,0x04,0};
    delegate bool EnumProc(IntPtr window, IntPtr parameter);
    [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr OpenProcess(uint rights, bool inherit, int pid);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool ReadProcessMemory(IntPtr process, IntPtr address, [Out] byte[] data, UIntPtr size, out UIntPtr read);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool WriteProcessMemory(IntPtr process, IntPtr address, byte[] data, UIntPtr size, out UIntPtr written);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool VirtualProtectEx(IntPtr process, IntPtr address, UIntPtr size, uint protection, out uint old);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool FlushInstructionCache(IntPtr process, IntPtr address, UIntPtr size);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc callback, IntPtr parameter);
    [DllImport("user32.dll")] static extern bool IsWindow(IntPtr window);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr window, out uint pid);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern int GetClassName(IntPtr window, StringBuilder text, int length);
    [DllImport("user32.dll", SetLastError=true)] static extern bool PostMessage(IntPtr window, uint message, IntPtr wparam, IntPtr lparam);
    [DllImport("iphlpapi.dll")] static extern uint GetExtendedTcpTable(IntPtr table, ref int size, bool sorted, int family, int kind, int reserved);

    static IntPtr Address(uint value) { return IntPtr.Size == 4 ? new IntPtr(unchecked((int)value)) : new IntPtr((long)value); }
    static byte[] Read(IntPtr process, uint address, int count) {
        byte[] data = new byte[count]; UIntPtr read;
        if (!ReadProcessMemory(process, Address(address), data, new UIntPtr((uint)count), out read) || read.ToUInt64() != (ulong)count)
            throw new Win32Exception(Marshal.GetLastWin32Error());
        return data;
    }
    static uint Read32(IntPtr process, uint address) { return BitConverter.ToUInt32(Read(process, address, 4), 0); }
    static byte CurrentBranch(IntPtr process) {
        byte[] current = Read(process, Show, Original.Length);
        for (int i = 0; i < current.Length; i++)
            if (i == 8 ? current[i] != 0x0D && current[i] != 0x07 : current[i] != Original[i])
                throw new InvalidOperationException("PSS Show code differs. Nothing written.");
        return current[8];
    }
    static void SetBranch(IntPtr process, byte target) {
        if (CurrentBranch(process) == target) return;
        uint old;
        if (!VirtualProtectEx(process, Address(Branch), new UIntPtr(1), 0x40, out old))
            throw new Win32Exception(Marshal.GetLastWin32Error());
        try {
            UIntPtr written;
            if (!WriteProcessMemory(process, Address(Branch), new byte[]{target}, new UIntPtr(1), out written) || written.ToUInt64() != 1
                    || !FlushInstructionCache(process, Address(Branch), new UIntPtr(1)))
                throw new Win32Exception(Marshal.GetLastWin32Error());
        } finally {
            uint ignored;
            if (!VirtualProtectEx(process, Address(Branch), new UIntPtr(1), old, out ignored))
                throw new Win32Exception(Marshal.GetLastWin32Error());
        }
        if (CurrentBranch(process) != target) throw new InvalidOperationException("PSS branch verification failed.");
    }
    static void ValidateUi(IntPtr process) {
        if (Read32(process, 0x15D8E44) != 3) throw new InvalidOperationException("Enter the game world first.");
        uint ui = Read32(process, 0x15B4BCC);
        if (ui == 0 || Read32(process, ui) != 0x11B09C8 || Read32(process, 0x11B09C8 + 0x44) != Show
                || Encoding.ASCII.GetString(Read(process, ui + 4, 14)) != "PlaySupportUI\0")
            throw new InvalidOperationException("PSS settings object differs. Nothing written.");
    }
    static Dictionary<int, IntPtr> Panels() {
        Dictionary<int, IntPtr> found = new Dictionary<int, IntPtr>();
        EnumWindows(delegate(IntPtr window, IntPtr ignored) {
            StringBuilder name = new StringBuilder(128); GetClassName(window, name, name.Capacity);
            if (name.ToString() == "Pss2019Panel") {
                uint owner; GetWindowThreadProcessId(window, out owner); found[(int)owner] = window;
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }
    static bool ExactClient(Process client) {
        if (client.MainModule.BaseAddress.ToInt64() != 0x400000 || new FileInfo(client.MainModule.FileName).Length != 8354488) return false;
        using (SHA256 sha = SHA256.Create()) using (FileStream input = File.OpenRead(client.MainModule.FileName))
            return BitConverter.ToString(sha.ComputeHash(input)).Replace("-", "") == ClientHash;
    }
    static IPEndPoint GameEndpoint(int pid, int gamePort) {
        int size = 0;
        uint error = GetExtendedTcpTable(IntPtr.Zero, ref size, false, 2, 5, 0);
        if (error != 122 || size < 4 || size > 1048576) throw new InvalidOperationException("Cannot query the game TCP endpoint.");
        IntPtr memory = Marshal.AllocHGlobal(size);
        try {
            if (GetExtendedTcpTable(memory, ref size, false, 2, 5, 0) != 0) throw new InvalidOperationException("TCP table changed; run again.");
            int count = Marshal.ReadInt32(memory), matches = 0; IPEndPoint endpoint = null;
            if (count < 0 || count > (size - 4) / 24) throw new InvalidOperationException("Invalid TCP table.");
            for (int i = 0; i < count; i++) {
                IntPtr row = new IntPtr(memory.ToInt64() + 4 + i * 24);
                int port = Marshal.ReadByte(row, 16) * 256 + Marshal.ReadByte(row, 17);
                if (Marshal.ReadInt32(row) != 5 || Marshal.ReadInt32(row, 20) != pid || port != gamePort) continue;
                byte[] address = new byte[4]; Marshal.Copy(new IntPtr(row.ToInt64() + 12), address, 0, 4);
                endpoint = new IPEndPoint(new IPAddress(address), port); matches++;
            }
            if (matches != 1) throw new InvalidOperationException("Need one established game connection on port " + gamePort + "; found " + matches + ".");
            return endpoint;
        } finally { Marshal.FreeHGlobal(memory); }
    }
    static TcpClient Connect(IPEndPoint endpoint) {
        TcpClient socket = new TcpClient(AddressFamily.InterNetwork);
        try {
            IAsyncResult pending = socket.BeginConnect(endpoint.Address, endpoint.Port, null, null);
            using (pending.AsyncWaitHandle) {
                if (!pending.AsyncWaitHandle.WaitOne(1500)) throw new IOException("Companion connection timeout.");
                socket.EndConnect(pending);
            }
            socket.NoDelay = true;
            socket.Client.Blocking = false;
            return socket;
        } catch { socket.Close(); throw; }
    }
    static bool Alive(Process client, IntPtr panel) { return !client.HasExited && IsWindow(panel); }

    // No PID dialog. The original helper panel identifies the intended process.
    public static void Run(int targetPid, int gamePort, int windowPort) {
        if (gamePort < 1 || gamePort > 65535 || windowPort < 1 || windowPort > 65535) throw new ArgumentOutOfRangeException("port");
        Dictionary<int, IntPtr> panels = Panels();
        List<int> candidates = new List<int>();
        foreach (int pid in panels.Keys) {
            if (targetPid != 0 && targetPid != pid) continue;
            try { using (Process candidate = Process.GetProcessById(pid)) { if (ExactClient(candidate)) candidates.Add(pid); } }
            catch (Win32Exception) { }
            catch (ArgumentException) { }
        }
        if (candidates.Count != 1) throw new InvalidOperationException("Need one exact client with the ORIGINAL PSS helper attached. Enter the game and attach that helper first. Run at the same privilege level.");
        int selected = candidates[0]; IntPtr panel = panels[selected];
        IntPtr process = OpenProcess(0x438, false, selected);
        if (process == IntPtr.Zero) throw new Win32Exception(Marshal.GetLastWin32Error());
        bool changed = false;
        System.Threading.CancellationTokenSource cancellation = new System.Threading.CancellationTokenSource();
        ConsoleCancelEventHandler cancel = delegate(object sender, ConsoleCancelEventArgs args) { args.Cancel = true; cancellation.Cancel(); };
        Console.CancelKeyPress += cancel;
        using (Process client = Process.GetProcessById(selected)) try {
            ValidateUi(process);
            if (CurrentBranch(process) == 0x0D) { changed = true; SetBranch(process, 0x07); }
            IPEndPoint game = GameEndpoint(selected, gamePort);
            IPEndPoint endpoint = new IPEndPoint(game.Address, windowPort);
            Console.WriteLine("Client found automatically: PID=" + selected + "; companion=" + endpoint);
            while (!cancellation.IsCancellationRequested && Alive(client, panel)) {
                byte[] hello = new byte[24]; Encoding.ASCII.GetBytes("PSSUI").CopyTo(hello, 0); hello[5] = 1;
                using (RandomNumberGenerator rng = RandomNumberGenerator.Create()) {
                    byte[] token = new byte[16]; rng.GetBytes(token); token.CopyTo(hello, 8); Array.Clear(token, 0, token.Length);
                }
                string command = ".psslink " + BitConverter.ToString(hello, 8, 16).Replace("-", "").ToLowerInvariant();
                Console.WriteLine("\nPaste this ONCE into the game chat for this session:");
                Console.WriteLine(command);
                Console.WriteLine("Keep this console open. When READY appears, double-click PSS settings item 900000901.");
                bool bound = false;
                while (!cancellation.IsCancellationRequested && Alive(client, panel)) {
                    try {
                        using (TcpClient connection = Connect(endpoint)) {
                            PssItemProtocol.Run(connection.Client, hello,
                                delegate { return !cancellation.IsCancellationRequested && Alive(client, panel); },
                                delegate {
                                    bound = true; Console.WriteLine("READY - inventory item now opens native PSS settings.");
                                    PostMessage(panel, 0x10, IntPtr.Zero, IntPtr.Zero); // Original helper hides, never destroys.
                                },
                                delegate {
                                    ValidateUi(process); SetBranch(process, 0x07);
                                    if (!PostMessage(panel, 0x111, new IntPtr(101), IntPtr.Zero)) throw new Win32Exception(Marshal.GetLastWin32Error());
                                    Console.WriteLine("OPEN queued on the existing game UI thread.");
                                });
                        }
                    } catch (SocketException) { }
                    catch (IOException) { }
                    if (bound || cancellation.IsCancellationRequested) break;
                    System.Threading.Thread.Sleep(1000);
                }
                Array.Clear(hello, 0, hello.Length);
                if (bound && !cancellation.IsCancellationRequested && Alive(client, panel)) Console.WriteLine("Disconnected. A new pairing command follows.");
            }
        } finally {
            Console.CancelKeyPress -= cancel;
            cancellation.Dispose();
            if (changed) try { SetBranch(process, 0x0D); } catch (Exception) { /* Game may already have exited. */ }
            CloseHandle(process);
        }
    }
}
