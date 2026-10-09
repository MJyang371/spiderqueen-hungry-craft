Add-Type -AssemblyName System.Drawing

$path = 'D:\MinecraftModding\SpiderQueen-HungryCraft-Forge-1.20.1\src\main\resources\assets\spiderqueen_hungrycraft\textures\entity\soulfire_dragon.png'
$dir = Split-Path -Parent $path
if (!(Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

$size = 128
$bmp = New-Object System.Drawing.Bitmap($size, $size)
$rnd = New-Object System.Random(20261009)

# Bone field: the whole bone area, with the mottling and the pores of old bone.
for ($y = 0; $y -lt $size; $y++) {
    for ($x = 0; $x -lt $size; $x++) {
        $inBone = ($x -lt 96 -and $y -lt 96)
        $inGlow = ($x -ge 96 -and $y -lt 32)
        $inDim = ($y -ge 96)

        if ($inGlow) {
            $base = 150 + $rnd.Next(60)
            $r = [Math]::Min(255, 120 + $rnd.Next(80))
            $g = [Math]::Min(255, $base + 70)
            $b = 255
            if ($rnd.Next(100) -lt 25) { $r = 255; $g = 255; $b = 255 }
        } elseif ($inDim) {
            $r = 40 + $rnd.Next(50)
            $g = 110 + $rnd.Next(70)
            $b = 170 + $rnd.Next(70)
            if ($rnd.Next(100) -lt 20) { $r = 150; $g = 220; $b = 255 }
        } elseif ($inBone) {
            $r = 226; $g = 222; $b = 204
            $roll = $rnd.Next(100)
            if ($roll -lt 12) { $r = 190; $g = 184; $b = 164 }
            elseif ($roll -lt 24) { $r = 246; $g = 244; $b = 232 }
            elseif ($roll -lt 28) { $r = 150; $g = 144; $b = 128 }
            $shade = $rnd.Next(14) - 7
            $r = [Math]::Max(0, [Math]::Min(255, $r + $shade))
            $g = [Math]::Max(0, [Math]::Min(255, $g + $shade))
            $b = [Math]::Max(0, [Math]::Min(255, $b + $shade))
        } else {
            $r = 38; $g = 36; $b = 44
        }

        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
    }
}

$bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Output ('written: ' + $path + '  ' + (Get-Item $path).Length + ' bytes')
