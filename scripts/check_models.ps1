$files = @(
  'app/src/main/assets/models/blazeface_short_range.tflite',
  'app/src/main/assets/models/mobilefacenet.tflite'
)
foreach ($f in $files) {
  $bytes = [System.IO.File]::ReadAllBytes($f)
  $head = $bytes[0..7]
  $hex = ($head | ForEach-Object { '{0:X2}' -f $_ }) -join ' '
  $ascii = -join ($head | ForEach-Object { if ($_ -ge 32 -and $_ -le 126) { [char]$_ } else { '.' } })
  Write-Host ("{0,-50} size={1,-9} hex={2}  ascii={3}" -f $f, $bytes.Length, $hex, $ascii)
}
