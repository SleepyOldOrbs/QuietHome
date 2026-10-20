# Requires PowerShell 7, JDK 21 and an Android SDK installation.
param(
    [string]$Sdk = $(if($env:ANDROID_HOME){$env:ANDROID_HOME}elseif($env:ANDROID_SDK_ROOT){$env:ANDROID_SDK_ROOT}elseif($IsWindows){Join-Path $env:LOCALAPPDATA 'Android/Sdk'}else{''}),
    [string]$Jdk = $env:JAVA_HOME,
    [string]$BuildTools = '37.0.0',
    [int]$Api = 35
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if(!$Sdk -or !(Test-Path -LiteralPath $Sdk)){throw 'Set ANDROID_HOME or pass -Sdk with your Android SDK directory.'}
if(!$Jdk){
    $compiler=Get-Command javac -ErrorAction Stop
    $Jdk=Split-Path (Split-Path $compiler.Source)
}
$suffix=if($IsWindows){'.exe'}else{''}
$bt=Join-Path $Sdk "build-tools/$BuildTools"
$android=Join-Path $Sdk "platforms/android-$Api/android.jar"
foreach($file in @("$bt/aapt2$suffix","$bt/zipalign$suffix","$bt/lib/d8.jar","$bt/lib/apksigner.jar",$android,"$Jdk/bin/javac$suffix")){
    if(!(Test-Path -LiteralPath $file)){throw "Required build tool missing: $file"}
}
$out=Join-Path $PSScriptRoot 'build'
$build=Join-Path $out ('intermediate-'+[guid]::NewGuid().ToString('N'))
$private=Join-Path $PSScriptRoot '.local'
$keyStore=Join-Path $private 'debug.p12'
New-Item -ItemType Directory -Force -Path "$build/classes","$build/dex",$private | Out-Null
function Check {if($LASTEXITCODE -ne 0){throw "Build command failed with exit $LASTEXITCODE"}}
& "$bt/aapt2$suffix" compile --dir "$PSScriptRoot/res" -o "$build/resources.zip"; Check
& "$bt/aapt2$suffix" link -o "$build/resources.apk" -I $android --manifest "$PSScriptRoot/AndroidManifest.xml" "$build/resources.zip"; Check
$sources=@(Get-ChildItem "$PSScriptRoot/src" -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
& "$Jdk/bin/javac$suffix" -encoding UTF-8 -source 8 -target 8 -classpath $android -d "$build/classes" @sources; Check
$classes=@(Get-ChildItem "$build/classes" -Filter '*.class' -Recurse | Select-Object -ExpandProperty FullName)
& "$Jdk/bin/java$suffix" -cp "$bt/lib/d8.jar" com.android.tools.r8.D8 --lib $android --min-api 23 --output "$build/dex" @classes; Check
Copy-Item "$build/resources.apk" "$build/unsigned.apk"
$zip=[IO.Compression.ZipFile]::Open("$build/unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try{
    foreach($file in Get-ChildItem "$PSScriptRoot/assets" -File -Recurse){
        $relative=[IO.Path]::GetRelativePath("$PSScriptRoot/assets",$file.FullName).Replace('\','/')
        if($file.Name.StartsWith('.')){continue}
        if($relative.StartsWith('wallpapers/') -and $file.Extension -notmatch '^\.(webp|jpg|jpeg|png)$'){
            throw "Unsupported wallpaper asset: $relative"
        }
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$file.FullName,"assets/$relative",[IO.Compression.CompressionLevel]::NoCompression) | Out-Null
    }
}finally{$zip.Dispose()}
& "$Jdk/bin/jar$suffix" uf "$build/unsigned.apk" -C "$build/dex" classes.dex; Check
& "$bt/zipalign$suffix" -f 4 "$build/unsigned.apk" "$build/aligned.apk"; Check
# A per-checkout development key, never committed. Not a production release key.
if(!(Test-Path -LiteralPath $keyStore)){
    & "$Jdk/bin/keytool$suffix" -genkeypair -keystore $keyStore -storepass android -keypass android -alias quiethome -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=QuietHome Local Development'; Check
}
$apk=Join-Path $out 'QuietHome-debug.apk'
& "$Jdk/bin/java$suffix" -jar "$bt/lib/apksigner.jar" sign --ks $keyStore --ks-pass pass:android --key-pass pass:android --out $apk "$build/aligned.apk"; Check
& "$Jdk/bin/java$suffix" -jar "$bt/lib/apksigner.jar" verify --verbose $apk; Check
Get-FileHash -LiteralPath $apk -Algorithm SHA256
