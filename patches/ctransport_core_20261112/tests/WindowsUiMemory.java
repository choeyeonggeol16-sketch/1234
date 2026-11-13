package l1j.server.swing;
import java.lang.foreign.*;
public final class WindowsUiMemory {
 static long[] read()throws Throwable {
  if(!System.getProperty("os.name").startsWith("Windows"))return new long[0];
  try(Arena arena=Arena.ofConfined()){
   Linker linker=Linker.nativeLinker();SymbolLookup kernel=SymbolLookup.libraryLookup("kernel32.dll",arena),psapi=SymbolLookup.libraryLookup("psapi.dll",arena),user=SymbolLookup.libraryLookup("user32.dll",arena);
   var current=linker.downcallHandle(kernel.find("GetCurrentProcess").orElseThrow(),FunctionDescriptor.of(ValueLayout.ADDRESS));
   var memory=linker.downcallHandle(psapi.find("GetProcessMemoryInfo").orElseThrow(),FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
   var gui=linker.downcallHandle(user.find("GetGuiResources").orElseThrow(),FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
   MemorySegment process=(MemorySegment)current.invokeExact(),counters=arena.allocate(80,8);counters.set(ValueLayout.JAVA_INT,0,80);
   if((int)memory.invokeExact(process,counters,80)==0)throw new AssertionError("Windows process memory query failed");
   return new long[]{counters.get(ValueLayout.JAVA_LONG,16),counters.get(ValueLayout.JAVA_LONG,72),(int)gui.invokeExact(process,0),(int)gui.invokeExact(process,1)};
  }
 }
}
