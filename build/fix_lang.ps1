$root = 'D:\MinecraftModding\SpiderQueen-HungryCraft-Forge-1.20.1\src\main\resources\assets\spiderqueen_hungrycraft\lang\'

$en = $root + 'en_us.json'
$e = [System.IO.File]::ReadAllText($en)
$e = $e.Replace(
    '"item.spiderqueen_hungrycraft.awakened_bone_dragon.desc": "Spawns a bone dragon that is awake, that belongs to you, and that answers your sceptre for ten minutes.",',
    '"item.spiderqueen_hungrycraft.awakened_bone_dragon.desc": "Spawns bone dragon remains in front of you and raises them as a Soulfire Dragon of your own, which answers your sceptre for ten minutes.",')
[System.IO.File]::WriteAllText($en, $e)

$zh = $root + 'zh_cn.json'
$z = [System.IO.File]::ReadAllText($zh)
$z = $z.Replace(
    '"item.spiderqueen_hungrycraft.awakened_bone_dragon.desc": "在正前方生成一具已唤醒、并归属于你的骨龙，它会听从你的权杖十分钟。",',
    '"item.spiderqueen_hungrycraft.awakened_bone_dragon.desc": "在正前方生成一具骨龙遗骸，并把它复活成归属于你的灵火龙，它会听从你的权杖十分钟。",')
[System.IO.File]::WriteAllText($zh, $z)

foreach ($file in @($en, $zh)) {
    try {
        $null = Get-Content $file -Raw | ConvertFrom-Json
        Write-Output ('JSON ok: ' + (Split-Path $file -Leaf))
    }
    catch {
        Write-Output ('JSON BROKEN: ' + (Split-Path $file -Leaf) + ' ' + $_.Exception.Message)
    }
}
