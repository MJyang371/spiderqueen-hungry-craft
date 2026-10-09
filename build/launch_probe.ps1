param(
    [Parameter(Mandatory = $true)][string]$BatPath,
    [Parameter(Mandatory = $true)][string]$WorkingDir,
    [Parameter(Mandatory = $true)][string]$LogPath,
    [string]$Jvm = '',
    [string]$World = ''
)

$ErrorActionPreference = 'Stop'
$line = [string]@(Get-Content -LiteralPath $BatPath -Encoding UTF8 | Where-Object { $_ -match 'java\.exe' })[0]
$line = $line.Replace('${clientid}', '00000FFFFFFFFFFFFFFFFFFFFFF0E09B').Replace('${auth_xuid}', '0')
if ($World -ne '') { $line = $line + ' --quickPlaySingleplayer "' + $World + '"' }

$tokens = [System.Collections.Generic.List[string]]::new()
foreach ($m in [regex]::Matches($line, '"([^"]*)"|(\S+)')) { if ($m.Groups[1].Success) { $tokens.Add($m.Groups[1].Value) } else { $tokens.Add($m.Groups[2].Value) } }

$java = $tokens[0]
$rest = [System.Collections.Generic.List[string]]::new()
if ($Jvm -ne '') { $rest.Add($Jvm) }
for ($i = 1; $i -lt $tokens.Count; $i++) { $rest.Add($tokens[$i]) }
Write-Output ('launching with ' + $rest.Count + ' arguments')
Remove-Item -LiteralPath $LogPath -ErrorAction SilentlyContinue
Start-Process -FilePath $java -ArgumentList $rest -WorkingDirectory $WorkingDir -RedirectStandardOutput $LogPath -WindowStyle Minimized
Write-Output 'client launched'
