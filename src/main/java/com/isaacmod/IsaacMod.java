package com.isaacmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.UUID;

public class IsaacMod implements ModInitializer {
    public static final String MOD_ID = "isaacmod";
    public static final String TAG = "isaac";
    public static EntityType<TearEntity> TEAR;

    private static final UUID HP_BASE = UUID.fromString("5a1d0c10-0000-4000-8000-000000000001");
    private static final UUID HP_ITEMS = UUID.fromString("5a1d0c10-0000-4000-8000-000000000002");
    private static final UUID SPD_ITEMS = UUID.fromString("5a1d0c10-0000-4000-8000-000000000003");

    @Override
    public void onInitialize() {
        TEAR = Registry.register(Registries.ENTITY_TYPE, new Identifier(MOD_ID, "tear"),
                EntityType.Builder.<TearEntity>create(TearEntity::new, SpawnGroup.MISC)
                        .setDimensions(0.25f, 0.25f).maxTrackingRange(4).trackingTickInterval(2).build("tear"));

        ModItems.init();

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> {
            for (Item i : ModItems.ALL) e.add(i);
        });

        // Пересчёт статов от предметов в инвентаре
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 10 != 0) return;
            for (ServerPlayerEntity p : new ArrayList<>(server.getPlayerManager().getPlayerList())) applyStats(p);
        });

        // Дроп пикапов с монстров, убитых "Айзеком"
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getAttacker() instanceof ServerPlayerEntity p) || !p.getCommandTags().contains(TAG)) return;
            if (!(entity instanceof Monster)) return;
            World w = entity.getWorld();
            boolean boss = entity.getType() == EntityType.WITHER || entity.getType() == EntityType.ENDER_DRAGON
                    || entity.getType() == EntityType.WARDEN || entity.getType() == EntityType.ELDER_GUARDIAN;
            double x = entity.getX(), y = entity.getY() + 0.5, z = entity.getZ();
            if (boss) ModItems.drop(w, x, y, z, new ItemStack(ModItems.randomPassive(w)));
            Item drop = ModItems.rollDrop(w, IsaacStats.of(p).luck);
            if (drop != null) ModItems.drop(w, x, y, z, new ItemStack(drop));
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                dispatcher.register(CommandManager.literal("isaac")
                        .then(CommandManager.literal("start").executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            p.addCommandTag(TAG);
                            p.giveItemStack(new ItemStack(ModItems.TEARS));
                            p.giveItemStack(new ItemStack(ModItems.BOMB, 1));
                            applyStats(p);
                            p.setHealth(p.getMaxHealth());
                            p.sendMessage(Text.literal("§cТы — Айзек. 3 сердца, слёзы в руке (ПКМ)."), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("stop").executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            p.removeScoreboardTag(TAG);
                            applyStats(p);
                            return 1;
                        }))
                        .then(CommandManager.literal("passive")
                                .requires(s -> s.hasPermissionLevel(2))
                                .executes(c -> {
                                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                                    p.giveItemStack(new ItemStack(ModItems.randomPassive(p.getWorld())));
                                    return 1;
                                }))));
    }

    private static void applyStats(PlayerEntity p) {
        IsaacStats s = IsaacStats.of(p);
        boolean isaac = p.getCommandTags().contains(TAG);
        EntityAttributeInstance hp = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        setMod(hp, HP_BASE, "isaac_base", isaac ? -14 : 0, EntityAttributeModifier.Operation.ADDITION);
        setMod(hp, HP_ITEMS, "isaac_items", s.health, EntityAttributeModifier.Operation.ADDITION);
        setMod(p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED), SPD_ITEMS, "isaac_speed",
                s.speed * 0.1, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    private static void setMod(EntityAttributeInstance inst, UUID id, String name, double v,
                               EntityAttributeModifier.Operation op) {
        if (inst == null) return;
        EntityAttributeModifier old = inst.getModifier(id);
        if (old != null) {
            if (old.getValue() == v) return;
            inst.removeModifier(id);
        } else if (v == 0) {
            return;
        }
        if (v != 0) inst.addTemporaryModifier(new EntityAttributeModifier(id, name, v, op));
    }
}
