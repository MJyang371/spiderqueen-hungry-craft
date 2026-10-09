$ErrorActionPreference = 'Stop'
$path = 'D:\MinecraftModding\SpiderQueen-HungryCraft-Forge-1.20.1\src\main\java\spiderqueenhungrycraft\brood\BroodSelection.java'
$text = [System.IO.File]::ReadAllText($path)

$pairs = @(
    @(
        'import spiderqueenhungrycraft.player.QueenDataCapability;',
        "import spiderqueenhungrycraft.player.QueenDataCapability;`r`nimport spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;`r`nimport spiderqueenhungrycraft.soulfire.SoulfireOrders;"
    ),
    @(
        '        List<Entity> dragons = NecromancyOrders.ownedLoaded(queen);',
        "        List<Entity> dragons = new ArrayList<>(NecromancyOrders.ownedLoaded(queen));`r`n        dragons.addAll(SoulfireOrders.ownedLoaded(queen));"
    ),
    @(
        '        for (Entity dragon : NecromancyOrders.commanded(queen))
        {
            dragons |= current.contains(dragon.getUUID());
        }',
        "        for (Entity dragon : NecromancyOrders.commanded(queen))
        {
            dragons |= current.contains(dragon.getUUID());
        }

        for (SoulfireDragonEntity dragon : SoulfireOrders.commanded(queen))
        {
            dragons |= current.contains(dragon.getUUID());
        }"
    ),
    @(
        '        if (!NecromancyOrders.isContractDragonOf(queen, dragon))',
        '        if (!NecromancyOrders.isContractDragonOf(queen, dragon) && !SoulfireOrders.isSoulfireDragonOf(queen, dragon))'
    ),
    @(
        '        int held = SpiderCommand.holdHere(queen) + NecromancyOrders.holdHere(queen);',
        '        int held = SpiderCommand.holdHere(queen) + NecromancyOrders.holdHere(queen) + SoulfireOrders.holdHere(queen);'
    ),
    @(
        '        int units = SpiderCommand.commanded(queen).size() + NecromancyOrders.commanded(queen).size();',
        "        int units = SpiderCommand.commanded(queen).size() + NecromancyOrders.commanded(queen).size()`r`n                + SoulfireOrders.commanded(queen).size();"
    ),
    @(
        '        SpiderOrderMode dragonOrder = NecromancyOrders.cycleStance(queen);',
        "        SpiderOrderMode dragonOrder = SoulfireOrders.cycleOrder(queen);`r`n`r`n        if (dragonOrder == null)`r`n        {`r`n            dragonOrder = NecromancyOrders.cycleStance(queen);`r`n        }"
    ),
    @(
        '        int sent = SpiderCommand.freeAttack(queen) + NecromancyOrders.freeAttack(queen);',
        '        int sent = SpiderCommand.freeAttack(queen) + NecromancyOrders.freeAttack(queen) + SoulfireOrders.freeAttack(queen);'
    ),
    @(
        '        int called = SpiderCommand.followHere(queen) + NecromancyOrders.followHere(queen);',
        '        int called = SpiderCommand.followHere(queen) + NecromancyOrders.followHere(queen) + SoulfireOrders.followHere(queen);'
    ),
    @(
        '            ids.addAll(NecromancyOrders.selectedEntityIds(queen));',
        "            ids.addAll(NecromancyOrders.selectedEntityIds(queen));`r`n            ids.addAll(SoulfireOrders.selectedEntityIds(queen));"
    )
)

foreach ($pair in $pairs) {
    $before = $text.Length
    $text = $text.Replace($pair[0], $pair[1])

    if ($text.Length -eq $before) {
        Write-Output ('MISS: ' + $pair[0].Split("`n")[0].Trim())
    }
}

[System.IO.File]::WriteAllText($path, $text)
Write-Output 'BroodSelection patched'
