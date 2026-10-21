# Copy one validated video without changing the original or enabling playback.
param(
    [Parameter(Mandatory)][string]$Path,
    [Parameter(Mandatory)][string]$DeviceSerial,
    [string]$Ffprobe='ffprobe'
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$source=Get-Item -LiteralPath $Path
if($source.PSIsContainer -or $source.Extension -ine '.mp4' -or $source.Length -le 0 -or $source.Length -gt 80MB){throw 'Choose an MP4 between 1 byte and 80 MiB.'}
$probe=& $Ffprobe -v error -select_streams v -show_entries stream=codec_name,width,height,avg_frame_rate -of json $source.FullName
if($LASTEXITCODE){throw 'ffprobe could not inspect this video.'}
$streams=@(($probe -join "`n" | ConvertFrom-Json).streams)
if($streams.Count -ne 1){throw 'Choose an MP4 containing exactly one video track.'}
$stream=$streams[0]
$rate=$stream.avg_frame_rate -split '/'
if($rate.Count -ne 2 -or [double]$rate[1] -le 0){throw 'Cannot verify frame rate.'}
$fps=[double]$rate[0]/[double]$rate[1]
if($stream.codec_name -ne 'h264' -or $stream.width -lt 1 -or $stream.height -lt 1 -or $stream.width -gt 1920 -or $stream.height -gt 1080 -or $fps -le 0 -or $fps -gt 30){throw 'Use H.264 MP4, up to 1920x1080 and 30 fps. See docs/VIDEO-BACKGROUNDS.md.'}
$adb=(Get-Command adb -CommandType Application -ErrorAction Stop).Source
function Invoke-Adb([string[]]$Arguments){
    $output=& $adb -s $DeviceSerial @Arguments 2>&1
    if($LASTEXITCODE){throw ($output -join "`n")}
    return ($output -join "`n")
}
if((Invoke-Adb @('shell','pm','path','local.quiethome')) -notmatch '^package:'){throw 'QuietHome is not installed on this device.'}
$hash=(Get-FileHash -LiteralPath $source.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
$stem=$source.BaseName -replace '[^A-Za-z0-9_-]','-'
$stem=$stem.Trim('-');if(!$stem){$stem='video'}
if($stem.Length -gt 70){$stem=$stem.Substring(0,70)}
$name="$stem-$($hash.Substring(0,12)).mp4"
$directory='/sdcard/Android/data/local.quiethome/files/videos'
Invoke-Adb @('shell','mkdir','-p',$directory) | Out-Null
$existing=(Invoke-Adb @('shell','ls','-1',$directory)) -split '\r?\n'
if($name -in $existing){
    $remoteHash=(Invoke-Adb @('shell','sha256sum',"$directory/$name")) -split '\s+'
    if($remoteHash[0] -ne $hash){throw 'Existing destination has different content; it was not overwritten.'}
    "Already present and verified: $name";return
}
$space=Invoke-Adb @('shell','df','-k','/data')
$line=($space -split '\r?\n' | Where-Object {$_ -match '^/'} | Select-Object -Last 1).Trim() -split '\s+'
if($line.Count -lt 4 -or $line[3] -notmatch '^\d+$'){throw 'Cannot verify available TV storage.'}
if([long]$line[3]*1024 -lt $source.Length+100MB){throw 'Not enough free storage to retain a 100 MiB reserve. Nothing copied.'}
$temporary="$directory/copy-$([guid]::NewGuid().ToString('N')).partial"
Invoke-Adb @('push',$source.FullName,$temporary) | Out-Null
$copied=(Invoke-Adb @('shell','sha256sum',$temporary)) -split '\s+'
if($copied[0] -ne $hash){throw "Copy verification failed. Incomplete copy retained at $temporary; playback was not changed."}
Invoke-Adb @('shell','mv','-n',$temporary,"$directory/$name") | Out-Null
$finalHash=(Invoke-Adb @('shell','sha256sum',"$directory/$name")) -split '\s+'
if($finalHash[0] -ne $hash){throw 'Final verification failed. Playback was not changed.'}
[pscustomobject]@{File=$name;Bytes=$source.Length;SHA256=$hash;Next='Home options > Wallpaper settings > Video backgrounds > Choose video, then Playback mode'}
