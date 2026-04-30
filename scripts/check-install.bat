@echo off
setlocal
set ADB="C:\Users\Frederico\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set DEV=192.168.15.6:46133
%ADB% -s %DEV% shell "dumpsys package com.sentinelvault | grep -E 'versionName|versionCode|firstInstallTime|lastUpdateTime|targetSdk|minSdk|primaryCpuAbi'"
echo ---
%ADB% -s %DEV% shell "pm list packages -f com.sentinelvault"
