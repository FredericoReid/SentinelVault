Get-ChildItem -Recurse -Path app/src -Filter *.kt |
    Select-String -Pattern 'Capture|Ready|Frame your face|Save Face' |
    ForEach-Object { "{0}:{1}: {2}" -f $_.Path, $_.LineNumber, $_.Line.Trim() }
