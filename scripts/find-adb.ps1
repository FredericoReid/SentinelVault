$paths = @(
  (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
  (Join-Path $env:USERPROFILE  'AppData\Local\Android\Sdk\platform-tools\adb.exe'),
  'C:\Android\Sdk\platform-tools\adb.exe',
  'C:\Program Files\Android\Android Studio\plugins\android\resources\platform-tools\adb.exe'
)
if ($env:ANDROID_HOME)     { $paths += (Join-Path $env:ANDROID_HOME     'platform-tools\adb.exe') }
if ($env:ANDROID_SDK_ROOT) { $paths += (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe') }
$paths = $paths | Where-Object { $_ }
foreach ($p in $paths) {
  if (Test-Path -LiteralPath $p) { Write-Output ('FOUND: ' + $p) }
  else                            { Write-Output ('miss : ' + $p) }
}
