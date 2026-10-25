param(
    [string]$Jdk=$env:JAVA_HOME,
    [string]$Sdk=$env:ANDROID_HOME,
    [string]$DeviceSerial='',
    [string]$BuildTools='37.0.0',
    [int]$Api=35
)
$ErrorActionPreference='Stop'
if(!$Jdk){$Jdk=Split-Path (Split-Path (Get-Command javac -ErrorAction Stop).Source)}
$suffix=if($IsWindows){'.exe'}else{''}
$build=Join-Path $PSScriptRoot ('build/tests-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path "$build/classes","$build/dex" | Out-Null
function Check {if($LASTEXITCODE -ne 0){throw "Test command failed with exit $LASTEXITCODE"}}
$hostSources=@('WallpaperDeck','RequestGate','DisplayFormat','RotationTimer','LatestRequest','ThemeStyle','VideoPolicy','PowerRequest')
foreach($name in $hostSources){
    & "$Jdk/bin/javac$suffix" -encoding UTF-8 -d "$build/classes" "$PSScriptRoot/src/local/quiethome/$name.java" "$PSScriptRoot/tests/${name}Test.java"; Check
    & "$Jdk/bin/java$suffix" -cp "$build/classes" "local.quiethome.${name}Test"; Check
}
if(!$DeviceSerial){return}
if(!$Sdk){throw 'Device tests require -Sdk or ANDROID_HOME.'}
# Explicit opt-in: pushes a test DEX to the selected authorised device and fetches public feeds.
$android=Join-Path $Sdk "platforms/android-$Api/android.jar"
$androidSources=@(Get-ChildItem "$PSScriptRoot/src" -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
& "$Jdk/bin/javac$suffix" -encoding UTF-8 -source 8 -target 8 -classpath "$android$([IO.Path]::PathSeparator)$build/classes" -d "$build/classes" @androidSources "$PSScriptRoot/tests/SettingsBackupTest.java" "$PSScriptRoot/tests/WidgetDataTest.java"; Check
$classes=@(Get-ChildItem "$build/classes" -Filter '*.class' -Recurse | Select-Object -ExpandProperty FullName)
& "$Jdk/bin/java$suffix" -cp "$Sdk/build-tools/$BuildTools/lib/d8.jar" com.android.tools.r8.D8 --lib $android --min-api 23 --output "$build/dex" @classes; Check
& adb -s $DeviceSerial push "$build/dex/classes.dex" /data/local/tmp/quiethome-tests.dex; Check
try{
    & adb -s $DeviceSerial shell 'CLASSPATH=/data/local/tmp/quiethome-tests.dex app_process /system/bin local.quiethome.SettingsBackupTest'; Check
    & adb -s $DeviceSerial shell 'CLASSPATH=/data/local/tmp/quiethome-tests.dex app_process /system/bin local.quiethome.WidgetDataTest'; Check
}finally{
    & adb -s $DeviceSerial shell rm /data/local/tmp/quiethome-tests.dex
}
