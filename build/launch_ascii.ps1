param(
    [Parameter(Mandatory = $true)][string]$BatPath,
    [Parameter(Mandatory = $true)][string]$WorkingDir,
    [Parameter(Mandatory = $true)][string]$LogPath
)

$ErrorActionPreference = 'Stop'
$line = [string]@(Get-Content -LiteralPath $BatPath -Encoding UTF8 | Where-Object { $_ -match 'java\.exe' })[0]
$line = $line.Replace('${clientid}', '00000FFFFFFFFFFFFFFFFFFFFFF0E09B').Replace('${auth_xuid}', '0')

$tokens = [System.Collections.Generic.List[string]]::new()
foreach ($m in [regex]::Matches($line, '"([^"]*)"|(\S+)')) { if ($m.Groups[1].Success) { $tokens.Add($m.Groups[1].Value) } else { $tokens.Add($m.Groups[2].Value) } }

$java = $tokens[0]
$rest = $tokens[1..($tokens.Count - 1)]
Write-Output ('java: ' + $java)
Write-Output ('arguments: ' + $rest.Count)
Remove-Item -LiteralPath $LogPath -ErrorAction SilentlyContinue
Start-Process -FilePath $java -ArgumentList $rest -WorkingDirectory $WorkingDir -RedirectStandardOutput $LogPath -WindowStyle Minimized
Write-Output 'client launched'
