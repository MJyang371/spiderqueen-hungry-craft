    $ErrorActionPreference = 'Stop'
$root = 'D:\MinecraftModding\SpiderQueen-HungryCraft-Forge-1.20.1\src\main\java\spiderqueenhungrycraft\'

$scepter = $root + 'brood\QueenScepterCommands.java'
$t = [System.IO.File]::ReadAllText($scepter)

$pairs = @(
    @(
        'import spiderqueenhungrycraft.registry.HungryCraftItems;',
        "import spiderqueenhungrycraft.registry.HungryCraftItems;`r`nimport spiderqueenhungrycraft.soulfire.SoulfireOrders;"
    ),
    @(
        '        boolean pickingOneSpider = hit.hasEntity() && (SpiderCommand.isOwnSpider(queen, hit.entity())
                || NecromancyOrders.isContractDragonOf(queen, hit.entity()));',
        "        boolean pickingOneSpider = hit.hasEntity() && (SpiderCommand.isOwnSpider(queen, hit.entity())`r`n                || NecromancyOrders.isContractDragonOf(queen, hit.entity())`r`n                || SoulfireOrders.isSoulfireDragonOf(queen, hit.entity()));"
    ),
    @(
        '        if (NecromancyOrders.isContractDragonOf(queen, target))',
        '        if (NecromancyOrders.isContractDragonOf(queen, target) || SoulfireOrders.isSoulfireDragonOf(queen, target))'
    ),
    @(
        '        int dragons = NecromancyOrders.attack(queen, target);',
        '        int dragons = NecromancyOrders.attack(queen, target) + SoulfireOrders.attack(queen, target);'
    ),
    @(
        '        int dragons = NecromancyOrders.moveTo(queen, dragonTarget);',
        '        int dragons = NecromancyOrders.moveTo(queen, dragonTarget) + SoulfireOrders.moveTo(queen, dragonTarget, commandId);'
    ),
    @(
        '        int dragons = NecromancyOrders.bombard(queen, target);',
        '        int dragons = NecromancyOrders.bombard(queen, target) + SoulfireOrders.breathAll(queen, target);'
    ),
    @(
        '        int called = SpiderCommand.rally(queen) + NecromancyOrders.rally(queen);',
        '        int called = SpiderCommand.rally(queen) + NecromancyOrders.rally(queen) + SoulfireOrders.rally(queen);'
    )
)

foreach ($pair in $pairs) {
    $before = $t.Length
    $t = $t.Replace($pair[0], $pair[1])

    if ($t.Length -eq $before) { Write-Output ('MISS scepter: ' + $pair[0].Split("`n")[0].Trim()) }
}

[System.IO.File]::WriteAllText($scepter, $t)

$cast = $root + 'brood\SpiderAbilityCast.java'
$c = [System.IO.File]::ReadAllText($cast)

$pairs2 = @(
    @(
        'import spiderqueenhungrycraft.registry.HungryCraftItems;',
        "import spiderqueenhungrycraft.registry.HungryCraftItems;`r`nimport spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;`r`nimport spiderqueenhungrycraft.soulfire.SoulfireOrders;"
    ),
    @(
        '        List<Entity> dragons = NecromancyOrders.commanded(queen);

        if (brood.isEmpty() && dragons.isEmpty())',
        "        List<Entity> dragons = NecromancyOrders.commanded(queen);`r`n        List<SoulfireDragonEntity> soulfires = SoulfireOrders.commanded(queen);`r`n`r`n        if (brood.isEmpty() && dragons.isEmpty() && soulfires.isEmpty())"
    ),
    @(
        '        if (cast > 0)
        {
            queen.level().playSound(null, queen.getX(), queen.getY(), queen.getZ(), SoundEvents.SPIDER_AMBIENT,
                    SoundSource.PLAYERS, 1.0F, 0.6F);
            tell(queen, Component.translatable("message.spiderqueen_hungrycraft.brood_ability_cast", cast));
        }',
        "        for (SoulfireDragonEntity dragon : soulfires)
        {
            // The soul fire dragon of round 7C answers the very same key, and its ability is the breath: the place the
            // sceptre of the server pointed at becomes the place it sprays, until another order arrives.
            if (context.hasPosition() && context.targetPosition() != null
                    && SoulfireOrders.breathAt(dragon, BlockPos.containing(context.targetPosition())))
            {
                bombarding++;
            }
        }

        if (cast > 0)
        {
            queen.level().playSound(null, queen.getX(), queen.getY(), queen.getZ(), SoundEvents.SPIDER_AMBIENT,
                    SoundSource.PLAYERS, 1.0F, 0.6F);
            tell(queen, Component.translatable(`"message.spiderqueen_hungrycraft.brood_ability_cast`", cast));
        }"
    )
)

foreach ($pair in $pairs2) {
    $before = $c.Length
    $c = $c.Replace($pair[0], $pair[1])

    if ($c.Length -eq $before) { Write-Output ('MISS cast: ' + $pair[0].Split("`n")[0].Trim()) }
}

[System.IO.File]::WriteAllText($cast, $c)
Write-Output 'scepter + ability patched'
