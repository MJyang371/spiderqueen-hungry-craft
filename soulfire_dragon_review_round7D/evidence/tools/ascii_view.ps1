Add-Type -AssemblyName System.Drawing

$path = $args[0]
$bmp = [System.Drawing.Bitmap]::FromFile($path)
$w = $bmp.Width
$h = $bmp.Height

$bone = 0
$glow = 0
$other = 0

$cellsX = 76
$cellsY = 30
$map = New-Object 'string[,]' $cellsY, $cellsX
for ($cy = 0; $cy -lt $cellsY; $cy++) { for ($cx = 0; $cx -lt $cellsX; $cx++) { $map[$cy, $cx] = '.' } }
$cellBone = New-Object 'int[,]' $cellsY, $cellsX
$cellGlow = New-Object 'int[,]' $cellsY, $cellsX

$minX = $w; $maxX = 0; $minY = $h; $maxY = 0

for ($y = 0; $y -lt $h; $y += 2) {
    for ($x = 0; $x -lt $w; $x += 2) {
        $c = $bmp.GetPixel($x, $y)
        $r = $c.R; $g = $c.G; $b = $c.B

        $isGlow = ($b -ge 205 -and $g -ge 185 -and $r -le 205 -and ($b - $r) -ge 45)
        $isBone = (!$isGlow -and $r -ge 185 -and $g -ge 180 -and $b -ge 150 -and $b -le 225 -and $r -ge $b -and ($r - $b) -le 55 -and ([Math]::Abs($r - $g) -le 22))

        $cx = [int]([Math]::Floor($x * $cellsX / $w))
        $cy = [int]([Math]::Floor($y * $cellsY / $h))

        if ($isGlow) {
            $glow++
            $cellGlow[$cy, $cx]++
            if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y }
        } elseif ($isBone) {
            $bone++
            $cellBone[$cy, $cx]++
            if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y }
        } else {
            $other++
        }
    }
}

for ($cy = 0; $cy -lt $cellsY; $cy++) {
    for ($cx = 0; $cx -lt $cellsX; $cx++) {
        if ($cellBone[$cy, $cx] -ge 4 -and $cellBone[$cy, $cx] -ge $cellGlow[$cy, $cx]) { $map[$cy, $cx] = '#' }
        elseif ($cellGlow[$cy, $cx] -ge 3) { $map[$cy, $cx] = '+' }
        elseif ($cellBone[$cy, $cx] -ge 1) { $map[$cy, $cx] = ':' }
    }
}

Write-Output ('image ' + $w + 'x' + $h + '   sampled pixels: bone=' + $bone + ' glow=' + $glow + ' other=' + $other)
if ($maxX -gt 0) { Write-Output ('dragon coloured pixels span x ' + $minX + '..' + $maxX + '  y ' + $minY + '..' + $maxY) }
Write-Output '--- "#" bone  "+" soul fire  ":" sparse bone  "." background ---'
for ($cy = 0; $cy -lt $cellsY; $cy++) {
    $line = ''
    for ($cx = 0; $cx -lt $cellsX; $cx++) { $line += $map[$cy, $cx] }
    Write-Output $line
}

$bmp.Dispose()
