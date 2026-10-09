$root = 'D:\MinecraftModding\SpiderQueen-HungryCraft-Forge-1.20.1\src\main\java\spiderqueenhungrycraft\'

# --- A) the entity: two missing imports and one missing field ------------------------------------------------
$entity = $root + 'soulfire\SoulfireDragonEntity.java'
$t = [System.IO.File]::ReadAllText($entity)

$t = $t.Replace(
    "import net.minecraft.network.syncher.SynchedEntityData;",
    "import net.minecraft.network.syncher.SynchedEntityData;`r`nimport net.minecraft.server.level.ServerLevel;")
$t = $t.Replace(
    "import net.minecraft.world.entity.player.Player;",
    "import net.minecraft.world.InteractionResult;`r`nimport net.minecraft.world.entity.player.Player;")
$t = $t.Replace(
    "    /** Where the unit was told to go, in world coordinates. */
    @Nullable
    private Vec3 moveTarget;",
    "    /** Where the unit was told to go, in world coordinates. */
    @Nullable
    private Vec3 moveTarget;

    /** The id of the scout move this errand belongs to, so the oldest errand can never win. */
    private int moveCommandId;")

[System.IO.File]::WriteAllText($entity, $t)

# --- B) the bridge: the CompoundTag import it never had -----------------------------------------------------
$bridge = $root + 'compat\iceandfire\IceAndFireBridge.java'
$b = [System.IO.File]::ReadAllText($bridge)
$b = $b.Replace(
    "import net.minecraft.network.chat.Component;",
    "import net.minecraft.nbt.CompoundTag;`r`nimport net.minecraft.network.chat.Component;")
[System.IO.File]::WriteAllText($bridge, $b)

# --- C) the registry: the 1.20.1 missing mappings API -------------------------------------------------------
$registry = $root + 'registry\HungryCraftEntities.java'
$r = [System.IO.File]::ReadAllText($registry)
$r = $r.Replace(
    '    @SubscribeEvent
    public static void remapOldIds(net.minecraftforge.event.RegistryEvent.MissingMappings<EntityType<?>> event)
    {
        for (net.minecraftforge.event.RegistryEvent.MissingMappings.Mapping<EntityType<?>> mapping : event
                .getAllMappings())
        {
            if (mapping.key.equals(new net.minecraft.resources.ResourceLocation(SpiderQueenHungryCraft.MODID,
                    "contracted_bone_dragon")))
            {
                mapping.remap(SOULFIRE_DRAGON.get());
            }
        }
    }',
    '    @SubscribeEvent
    public static void remapOldIds(net.minecraftforge.registries.MissingMappingsEvent event)
    {
        net.minecraft.resources.ResourceLocation oldId = new net.minecraft.resources.ResourceLocation(
                SpiderQueenHungryCraft.MODID, "contracted_bone_dragon");

        for (net.minecraftforge.registries.MissingMappingsEvent.Mapping<EntityType<?>> mapping : event
                .getAllMappings(Registries.ENTITY_TYPE))
        {
            if (mapping.getKey().equals(oldId))
            {
                mapping.remap(SOULFIRE_DRAGON.get());
            }
        }
    }')
[System.IO.File]::WriteAllText($registry, $r)

# --- D) the probe command: the duplicated method the two edits left behind -----------------------------------
$probe = $root + 'command\IceAndFireProbeCommands.java'
$lines = [System.IO.File]::ReadAllLines($probe)
$kept = @()
$kept += $lines[0..134]
$kept += $lines[154..($lines.Length - 1)]
[System.IO.File]::WriteAllLines($probe, $kept)

Write-Output ('probe lines now: ' + $kept.Length)
Write-Output ($kept[134])
Write-Output ($kept[135])
