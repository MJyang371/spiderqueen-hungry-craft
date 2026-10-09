param(
    [Parameter(Mandatory = $true)][string]$Baseline,
    [Parameter(Mandatory = $true)][string]$WithUnit
)

Add-Type -AssemblyName System.Drawing

$before = [System.Drawing.Bitmap]::FromFile($Baseline)
$after = [System.Drawing.Bitmap]::FromFile($WithUnit)
$w = $before.Width
$h = $before.Height

$cellsX = 76
$cellsY = 30
$changed = New-Object 'int[,]' $cellsY, $cellsX
$total = 0
$minX = $w; $maxX = 0; $minY = $h; $maxY = 0
$columns = New-Object 'bool[]' $cellsX

for ($y = $h * 0.18; $y -lt $h * 0.80; $y += 2) {
    for ($x = 0; $x -lt $w; $x += 2) {
        $p = $before.GetPixel([int]$x, [int]$y)
        $q = $after.GetPixel([int]$x, [int]$y)
        $d = [Math]::Abs($p.R - $q.R) + [Math]::Abs($p.G - $q.G) + [Math]::Abs($p.B - $q.B)

        # A soul fire dragon is bone or soul fire, and the colours of its texture are specific: a warm bright off
        # white, or a cyan that no sky reaches. Loose thresholds counted dirt, sand and stone as bone, which is what
        # turned half the screen into a "difference" - these ones are tight around the texture itself.
        $isBone = ($q.R -ge 185 -and $q.G -ge 180 -and $q.B -ge 170 -and ($q.R - $q.B) -ge 8 -and ($q.R - $q.B) -le 45 -and [Math]::Abs($q.R - $q.G) -le 25 -and [Math]::Abs($q.G - $q.B) -le 40)
        $isGlow = ($q.B -ge 200 -and $q.G -ge 180 -and ($q.B - $q.R) -ge 60)

        if ($d -gt 45 -and ($isBone -or $isGlow)) {
            $total++
            $cx = [int]([Math]::Floor($x * $cellsX / $w))
            $cy = [int]([Math]::Floor($y * $cellsY / $h))
            $changed[$cy, $cx]++
            $columns[$cx] = $true
            if ($x -lt $minX) { $minX = $x }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}

Write-Output ('changed pixels (sampled every 2px): ' + $total)
if ($maxX -gt 0) {
    Write-Output ('bounding box: x ' + $minX + '..' + $maxX + '   y ' + $minY + '..' + $maxY)
}

# Groups of occupied columns, which is how many separate things appeared on the screen.
$groups = 0
$inside = $false
for ($cx = 0; $cx -lt $cellsX; $cx++) {
    if ($columns[$cx] -and !$inside) { $groups++; $inside = $true }
    elseif (!$columns[$cx]) { $inside = $false }
}
Write-Output ('separate objects seen on the screen: ' + $groups)

# Connected clusters of changed cells, which is the count of separate things that appeared on the screen.
$seen = New-Object 'bool[,]' $cellsY, $cellsX
$clusters = 0
for ($cy = 0; $cy -lt $cellsY; $cy++) {
    for ($cx = 0; $cx -lt $cellsX; $cx++) {
        if ($changed[$cy, $cx] -lt 2 -or $seen[$cy, $cx]) { continue }

        $clusters++
        $queue = New-Object System.Collections.Queue
        $queue.Enqueue("$cy,$cx")
        $seen[$cy, $cx] = $true
        $area = 0
        $cMinX = $cellsX; $cMaxX = 0; $cMinY = $cellsY; $cMaxY = 0

        while ($queue.Count -gt 0) {
            $parts = ([string]$queue.Dequeue()).Split(',')
            $y2 = [int]$parts[0]
            $x2 = [int]$parts[1]
            $area++
            if ($x2 -lt $cMinX) { $cMinX = $x2 }
            if ($x2 -gt $cMaxX) { $cMaxX = $x2 }
            if ($y2 -lt $cMinY) { $cMinY = $y2 }
            if ($y2 -gt $cMaxY) { $cMaxY = $y2 }

            foreach ($d in @(@(-1, 0), @(1, 0), @(0, -1), @(0, 1), @(-1, -1), @(1, 1), @(-1, 1), @(1, -1))) {
                $ny = $y2 + $d[0]
                $nx = $x2 + $d[1]
                if ($ny -lt 0 -or $ny -ge $cellsY -or $nx -lt 0 -or $nx -ge $cellsX) { continue }
                if ($seen[$ny, $nx] -or $changed[$ny, $nx] -lt 2) { continue }
                $seen[$ny, $nx] = $true
                $queue.Enqueue("$ny,$nx")
            }
        }

        if ($area -ge 4) {
            $pxMinX = [int]($cMinX * $w / $cellsX)
            $pxMaxX = [int](($cMaxX + 1) * $w / $cellsX)
            $pxMinY = [int]($cMinY * $h / $cellsY)
            $pxMaxY = [int](($cMaxY + 1) * $h / $cellsY)
            Write-Output ('  object ' + $clusters + ': area ' + $area + ' cells, screen box x ' + $pxMinX + '..' + $pxMaxX + '  y ' + $pxMinY + '..' + $pxMaxY + '  (' + ($pxMaxX - $pxMinX) + 'x' + ($pxMaxY - $pxMinY) + ' px)')
        }
    }
}
Write-Output '--- "#" changed pixels (the units), "." unchanged background ---'
for ($cy = 0; $cy -lt $cellsY; $cy++) {
    $line = ''
    for ($cx = 0; $cx -lt $cellsX; $cx++) {
        if ($changed[$cy, $cx] -ge 6) { $line += '#' }
        elseif ($changed[$cy, $cx] -ge 2) { $line += '+' }
        elseif ($changed[$cy, $cx] -ge 1) { $line += '.' }
        else { $line += ' ' }
    }
    Write-Output $line
}

$before.Dispose()
$after.Dispose()
