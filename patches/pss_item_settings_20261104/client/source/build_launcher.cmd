@echo off
cd /d "%~dp0"
set "pssCsc=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe"
if not exist "%pssCsc%" (
  echo .NET Framework 4.x C# compiler is required.
  exit /b 1
)
"%pssCsc%" /nologo /target:winexe /platform:x86 /optimize+ /r:System.Windows.Forms.dll /r:System.Drawing.dll /out:"..\PssButton.exe" PssButton.cs
exit /b %errorlevel%
