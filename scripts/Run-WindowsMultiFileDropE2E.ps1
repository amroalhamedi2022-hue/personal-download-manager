[CmdletBinding()]
param(
 [string]$GuiExecutable,
 [string]$OutputDirectory,
 [string]$SourceSha,
 [switch]$AsSource,
 [string]$One,
 [string]$Two,
 [string]$EffectPath
)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Windows.Forms

if ($AsSource) {
 # Hide ONLY the child PowerShell console. -WindowStyle Hidden also hides
 # its WinForms OLE source on some Windows runner sessions.
 Add-Type -Namespace HyperGetNativeSource -Name ConsoleWindow -MemberDefinition @'
 [System.Runtime.InteropServices.DllImport("kernel32.dll")]
 public static extern System.IntPtr GetConsoleWindow();
 [System.Runtime.InteropServices.DllImport("user32.dll")]
 public static extern bool ShowWindow(System.IntPtr h,int cmd);
'@
 $consoleHandle=[HyperGetNativeSource.ConsoleWindow]::GetConsoleWindow()
 if($consoleHandle -ne [IntPtr]::Zero){
   [HyperGetNativeSource.ConsoleWindow]::ShowWindow($consoleHandle,0)|Out-Null
 }
 $form=[System.Windows.Forms.Form]::new()
 $form.Text='HyperGetFileDragFixture'
 $form.StartPosition=[System.Windows.Forms.FormStartPosition]::Manual
 $form.Location=[System.Drawing.Point]::new(30,180)
 $form.Size=[System.Drawing.Size]::new(200,130)
 $form.TopMost=$true
 $pane=[System.Windows.Forms.Panel]::new()
 $pane.Dock=[System.Windows.Forms.DockStyle]::Fill
 $pane.BackColor=[System.Drawing.Color]::LightBlue
 $form.Controls.Add($pane)
 $pane.Add_MouseDown({
   [IO.File]::WriteAllText(($EffectPath+'.mousedown'),'received '+[DateTime]::UtcNow.ToString('o'))
   if ($_.Button -ne [System.Windows.Forms.MouseButtons]::Left) { return }
   $paths=[System.Collections.Specialized.StringCollection]::new()
   [void]$paths.Add($One)
   [void]$paths.Add($Two)
   $data=[System.Windows.Forms.DataObject]::new()
   $data.SetFileDropList($paths)
   try {
     $effect=$pane.DoDragDrop($data,[System.Windows.Forms.DragDropEffects]::Copy)
     [IO.File]::WriteAllText($EffectPath,$effect.ToString())
   } catch {
     [IO.File]::WriteAllText(($EffectPath+'.error'),$_.Exception.ToString())
   } finally { $form.Close() }
 }.GetNewClosure())
 $form.Add_Shown({ [IO.File]::WriteAllText(($EffectPath+'.ready'),[DateTime]::UtcNow.ToString('o')) }.GetNewClosure())
 $timer=[System.Windows.Forms.Timer]::new()
 $timer.Interval=20000
 $timer.Add_Tick({$timer.Stop();$form.Close()})
 $timer.Start()
 try {[void]$form.ShowDialog()} finally {$timer.Dispose();$form.Dispose()}
 exit 0
}

$guiPath=[IO.Path]::GetFullPath($GuiExecutable)
if ([IO.Path]::GetFileName($guiPath) -ne 'HyperGet.exe' -or -not [IO.File]::Exists($guiPath)) {
 throw 'A real installed HyperGet.exe is required.'
}
$root=[IO.Path]::GetFullPath($OutputDirectory)
[void](New-Item -ItemType Directory -Path $root -Force)
$one=Join-Path $root 'old.url'
$two=Join-Path $root 'second.txt'
$effectPath=Join-Path $root 'native-effect.txt'
$shortcut='[InternetShortcut]'+[Environment]::NewLine+'URL=http://127.0.0.1:9999/one.bin'+[Environment]::NewLine
[IO.File]::WriteAllBytes($one,([byte[]]@(255,254)+[Text.Encoding]::Unicode.GetBytes($shortcut)))
[IO.File]::WriteAllText($two,'http://127.0.0.1:9999/two.bin',[Text.Encoding]::UTF8)
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class HyperGetNativeDropTest {
 public delegate bool Callback(IntPtr h, IntPtr p);
 [StructLayout(LayoutKind.Sequential)] public struct RECT {public int Left,Top,Right,Bottom;}
 [DllImport("user32.dll")] public static extern bool EnumWindows(Callback cb,IntPtr p);
 [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h,out uint pid);
 [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
 [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h,out RECT r);
 [DllImport("user32.dll")] public static extern bool SetCursorPos(int x,int y);
 [DllImport("user32.dll")] public static extern void mouse_event(uint f,uint x,uint y,uint d,UIntPtr e);
 [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h,int cmd);
 [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
 public static int Count(uint processId) {
   int count=0;
   EnumWindows(delegate(IntPtr h, IntPtr p) {
     uint owner;
     GetWindowThreadProcessId(h,out owner);
     if(owner==processId && IsWindowVisible(h))count++;
     return true;
   },IntPtr.Zero);
   return count;
 }
 public static IntPtr FirstVisible(uint processId) {
   IntPtr found=IntPtr.Zero;
   EnumWindows(delegate(IntPtr h, IntPtr p) {
     uint owner;
     GetWindowThreadProcessId(h,out owner);
     if(owner==processId && IsWindowVisible(h)){
       found=h;
       return false;
     }
     return true;
   },IntPtr.Zero);
   return found;
 }
}
'@
$gui=$null
$source=$null
$before=0
$after=0
$effect='missing'
$failure=''
try {
 $gui=Start-Process -FilePath $guiPath -PassThru
 $main=[IntPtr]::Zero
 for($n=0;$n -lt 60;$n++){
  Start-Sleep -Milliseconds 350
  $proc=@(Get-Process -Name HyperGet -ErrorAction SilentlyContinue | Where-Object {$_.MainWindowHandle -ne [IntPtr]::Zero})
  if($proc.Count -eq 1){$gui=$proc[0];$main=$gui.MainWindowHandle;break}
 }
 if($main -eq [IntPtr]::Zero){throw 'Installed main GUI not visible.'}
 [HyperGetNativeDropTest]::ShowWindow($main,3)|Out-Null
 $before=[HyperGetNativeDropTest]::Count([uint32]$gui.Id)
 $rect=[HyperGetNativeDropTest+RECT]::new()
 if(-not [HyperGetNativeDropTest]::GetWindowRect($main,[ref]$rect)){throw 'No main bounds.'}
 $targetX=[int]($rect.Left+($rect.Right-$rect.Left)*0.65)
 $targetY=[int]($rect.Top+($rect.Bottom-$rect.Top)*0.52)
 $ps=Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
 $argsText='-NoProfile -NonInteractive -Sta -File "'+$PSCommandPath+'" -AsSource -One "'+$one+'" -Two "'+$two+'" -EffectPath "'+$effectPath+'"'
 $sourceOut=Join-Path $root 'source-stdout.log'
 $sourceErr=Join-Path $root 'source-stderr.log'
 $source=Start-Process -FilePath $ps -ArgumentList $argsText -PassThru -RedirectStandardOutput $sourceOut -RedirectStandardError $sourceErr
 $sourceH=[IntPtr]::Zero
 for($n=0;$n -lt 150;$n++) {
  Start-Sleep -Milliseconds 100
  $source.Refresh()
  if($source.HasExited){throw 'Native OLE file source exited before drag.'}
  # A WinForms FixedToolWindow is intentionally absent from the Windows
  # taskbar: Process.MainWindowHandle may stay zero even when it is visible.
  # Find the actual visible native window by owner PID instead.
  $sourceH=[HyperGetNativeDropTest]::FirstVisible([uint32]$source.Id)
  if($sourceH -ne [IntPtr]::Zero){break}
 }
 if($sourceH -eq [IntPtr]::Zero){throw 'Native file source missing.'}
 $sr=[HyperGetNativeDropTest+RECT]::new()
 if(-not [HyperGetNativeDropTest]::GetWindowRect($sourceH,[ref]$sr)){throw 'Native file source bounds missing.'}
 $sx=$sr.Left+70; $sy=$sr.Top+70
 [HyperGetNativeDropTest]::SetForegroundWindow($sourceH)|Out-Null
 Start-Sleep -Milliseconds 350
 [HyperGetNativeDropTest]::SetCursorPos($sx,$sy)|Out-Null
 [HyperGetNativeDropTest]::mouse_event([uint32]2,0,0,0,[UIntPtr]::Zero)
 Start-Sleep -Milliseconds 250
 for($n=1;$n -le 24;$n++){
  $x=[int]($sx+($targetX-$sx)*$n/24)
  $y=[int]($sy+($targetY-$sy)*$n/24)
  [HyperGetNativeDropTest]::SetCursorPos($x,$y)|Out-Null
  Start-Sleep -Milliseconds 50
 }
 Start-Sleep -Milliseconds 220
 [HyperGetNativeDropTest]::mouse_event([uint32]4,0,0,0,[UIntPtr]::Zero)
 for($n=0;$n -lt 35;$n++){
  Start-Sleep -Milliseconds 240
  if(Test-Path -LiteralPath $effectPath){break}
 }
 if(Test-Path -LiteralPath $effectPath){$effect=(Get-Content -LiteralPath $effectPath -Raw).Trim()}
 for($n=0;$n -lt 25;$n++){
  Start-Sleep -Milliseconds 240
  $after=[HyperGetNativeDropTest]::Count([uint32]$gui.Id)
  if($after -gt $before){break}
 }
 Add-Type -AssemblyName System.Drawing
 $screen=[System.Windows.Forms.Screen]::PrimaryScreen.Bounds
 $bmp=[System.Drawing.Bitmap]::new($screen.Width,$screen.Height)
 $draw=[System.Drawing.Graphics]::FromImage($bmp)
 try{
  $draw.CopyFromScreen($screen.Location,[System.Drawing.Point]::Empty,$screen.Size)
  $bmp.Save((Join-Path $root 'native-drop.png'),[System.Drawing.Imaging.ImageFormat]::Png)
 }finally{$draw.Dispose();$bmp.Dispose()}
 if($effect -ne 'Copy' -or $after -le $before){
  throw "Native drop inconclusive: OLE effect=$effect, before=$before, after=$after."
 }
 Write-Host "PASS: native two-file OLE Copy drop opened new HyperGet GUI window."
}catch{
 $failure=$_.Exception.Message
}finally{
 [pscustomobject]@{
  SourceSHA=$SourceSha
  OfferedFiles=2
  NativeOLEEffect=$effect
  VisibleWindowsBefore=$before
  VisibleWindowsAfter=$after
  OpenedNewDialog=($effect -eq 'Copy' -and $after -gt $before)
  SourceShown=(Test-Path -LiteralPath ($effectPath+'.ready'))
  NativeMouseDownReceived=(Test-Path -LiteralPath ($effectPath+'.mousedown'))
  NativeMouseDownError=$(if(Test-Path -LiteralPath ($effectPath+'.error')){Get-Content -LiteralPath ($effectPath+'.error') -Raw}else{''})
  Limitation='No automated assertion of both URL field values; needs visual/user content acceptance'
  Error=$failure
 }|ConvertTo-Json -Depth 4|Set-Content -LiteralPath (Join-Path $root 'native-drop-report.json') -Encoding utf8
 if($source -and -not $source.HasExited){Stop-Process -Id $source.Id -Force -ErrorAction SilentlyContinue}
 if($gui){Stop-Process -Id $gui.Id -Force -ErrorAction SilentlyContinue}
}
if($failure){throw $failure}
