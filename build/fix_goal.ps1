$path = 'D:\MinecraftModding\SpiderQueen-HungryCraft-Forge-1.20.1\src\main\java\spiderqueenhungrycraft\soulfire\goal\SoulfireCommandGoal.java'
$lines = [System.IO.File]::ReadAllLines($path)
$text = ($lines[0..($lines.Length - 2)] -join "`r`n")
[System.IO.File]::WriteAllText($path, $text)
Write-Output ('lines now: ' + ([System.IO.File]::ReadAllLines($path)).Length)
Write-Output (([System.IO.File]::ReadAllLines($path))[-1])
