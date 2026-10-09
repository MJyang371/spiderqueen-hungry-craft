$ErrorActionPreference = 'Stop'

$staging = 'D:\MinecraftModding\NecromancyIV_Astra_Handoff\soulfire_review_round7D'
$project = 'D:\MinecraftModding\SpiderQueen-HungryCraft-Forge-1.20.1'
$zip = 'D:\MinecraftModding\NecromancyIV_Astra_Handoff\soulfire_dragon_review_round7D.zip'

# Layout inside the package
$src = Join-Path $staging 'src'
$integration = Join-Path $staging 'integration'
$docs = Join-Path $staging 'docs'

foreach ($dir in @($src, $integration, $docs)) {
    if (!(Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
}

# 1. The unit and its behaviour
Copy-Item (Join-Path $project 'src\main\java\spiderqueenhungrycraft\soulfire\*') $src -Recurse -Force

# 2. The picture: model, renderer, probe, texture
$client = Join-Path $src 'client'
New-Item -ItemType Directory -Force -Path (Join-Path $client 'model'), (Join-Path $client 'renderer') | Out-Null
Copy-Item (Join-Path $project 'src\main\java\spiderqueenhungrycraft\client\model\SoulfireDragonModel.java') (Join-Path $client 'model') -Force
Copy-Item (Join-Path $project 'src\main\java\spiderqueenhungrycraft\client\renderer\SoulfireDragonRenderer.java') (Join-Path $client 'renderer') -Force
Copy-Item (Join-Path $project 'src\main\java\spiderqueenhungrycraft\client\SoulfireRenderProbe.java') $client -Force
Copy-Item (Join-Path $project 'src\main\resources\assets\spiderqueen_hungrycraft\textures\entity\soulfire_dragon.png') $src -Force

# 3. The wiring: the sceptre, the selection, the skill key, the registry and the language files
Copy-Item (Join-Path $project 'src\main\java\spiderqueenhungrycraft\brood\BroodSelection.java') $integration -Force
Copy-Item (Join-Path $project 'src\main\java\spiderqueenhungrycraft\brood\QueenScepterCommands.java') $integration -Force
Copy-Item (Join-Path $project 'src\main\java\spiderqueenhungrycraft\brood\SpiderAbilityCast.java') $integration -Force
Copy-Item (Join-Path $project 'src\main\java\spiderqueenhungrycraft\compat\iceandfire\NecromancySkill.java') $integration -Force
Copy-Item (Join-Path $project 'src\main\java\spiderqueenhungrycraft\compat\iceandfire\IceAndFireSupport.java') $integration -Force
Copy-Item (Join-Path $project 'src\main\java\spiderqueenhungrycraft\client\HungryCraftClientSetup.java') $integration -Force
Copy-Item (Join-Path $project 'src\main\java\spiderqueenhungrycraft\registry\HungryCraftEntities.java') $integration -Force
Copy-Item (Join-Path $project 'src\main\resources\assets\spiderqueen_hungrycraft\lang\en_us.json') $integration -Force
Copy-Item (Join-Path $project 'src\main\resources\assets\spiderqueen_hungrycraft\lang\zh_cn.json') $integration -Force

# 4. The handover documents of the round
Copy-Item 'D:\MinecraftModding\NecromancyIV_Astra_Handoff\ROUND7C_SOULFIRE_DRAGON_DESIGN.md' $docs -Force
Copy-Item 'D:\MinecraftModding\NecromancyIV_Astra_Handoff\ROUND7C_SOULFIRE_IMPLEMENTATION.md' $docs -Force
Copy-Item 'D:\MinecraftModding\NecromancyIV_Astra_Handoff\ROUND7D_SOULFIRE_RENDER_FIX.md' $docs -Force

# 5. The evidence of the round: the pictures the client probe took, and the tools that read them
$evidence = Join-Path $staging 'evidence'
New-Item -ItemType Directory -Force -Path (Join-Path $evidence 'tools') | Out-Null
Copy-Item (Join-Path $project 'build\probe_shots\baseline.png') (Join-Path $evidence 'soulfire_probe_baseline.png') -Force
Copy-Item (Join-Path $project 'build\probe_shots\front.png') (Join-Path $evidence 'soulfire_probe_front.png') -Force
Copy-Item (Join-Path $project 'build\probe_shots\side.png') (Join-Path $evidence 'soulfire_probe_side.png') -Force
Copy-Item (Join-Path $project 'build\diff_shots.ps1') (Join-Path $evidence 'tools') -Force
Copy-Item (Join-Path $project 'build\ascii_view.ps1') (Join-Path $evidence 'tools') -Force
Copy-Item (Join-Path $project 'build\make_soulfire_texture.ps1') (Join-Path $evidence 'tools') -Force

# 6. The package
Remove-Item $zip -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $zip -CompressionLevel Optimal

Write-Output ('zip: ' + $zip + '  ' + [math]::Round((Get-Item $zip).Length / 1KB, 1) + ' KB')
Get-ChildItem $staging -Recurse -File | ForEach-Object { $_.FullName.Replace($staging, '') }
