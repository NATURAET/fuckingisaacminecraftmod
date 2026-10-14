package com.isaacmod;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ModItems {
    public static final List<PassiveItem> PASSIVES = new ArrayList<>();
    public static final List<Item> ACTIVES = new ArrayList<>();
    public static final List<Item> ALL = new ArrayList<>();

    public static Item TEARS, BOMB, PENNY, RED_HEART, SOUL_HEART, D6, PILL, TAROT;

    // ------------------------------------------------------------------
    public static class PassiveItem extends Item {
        public final String id;
        public final float tears, damage, speed, range, luck;
        public final int shots, hp, flags;

        public PassiveItem(String id, float tears, float damage, int shots, float speed, int hp,
                           float range, float luck, int flags, Settings s) {
            super(s);
            this.id = id; this.tears = tears; this.damage = damage; this.shots = shots;
            this.speed = speed; this.hp = hp; this.range = range; this.luck = luck; this.flags = flags;
        }

        @Override
        public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext ctx) {
            tooltip.add(Text.translatable("item.isaacmod." + id + ".desc").formatted(Formatting.GRAY));
            tooltip.add(Text.translatable("tooltip.isaacmod.passive").formatted(Formatting.DARK_PURPLE));
        }

        @Override
        public boolean hasGlint(ItemStack stack) { return true; }
    }

    // ------------------------------------------------------------------
    @FunctionalInterface
    public interface Use { boolean run(ServerWorld w, ServerPlayerEntity p); }

    /** Активный предмет (с перезарядкой) или расходник (consume = true). */
    public static class ActiveItem extends Item {
        private final String id;
        private final int cooldown;
        private final boolean consume;
        private final Use use;

        public ActiveItem(String id, int cooldown, boolean consume, Use use, Settings s) {
            super(s);
            this.id = id; this.cooldown = cooldown; this.consume = consume; this.use = use;
        }

        @Override
        public TypedActionResult<ItemStack> use(World w, PlayerEntity p, Hand h) {
            ItemStack st = p.getStackInHand(h);
            if (!w.isClient && w instanceof ServerWorld sw && p instanceof ServerPlayerEntity sp) {
                if (use.run(sw, sp)) {
                    if (cooldown > 0) p.getItemCooldownManager().set(this, cooldown);
                    if (consume && !p.getAbilities().creativeMode) st.decrement(1);
                }
            }
            return TypedActionResult.success(st, w.isClient());
        }

        @Override
        public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext ctx) {
            tooltip.add(Text.translatable("item.isaacmod." + id + ".desc").formatted(Formatting.GRAY));
            if (!consume) tooltip.add(Text.translatable("tooltip.isaacmod.active", cooldown / 20).formatted(Formatting.AQUA));
        }
    }

    // ------------------------------------------------------------------
    public static class TearsItem extends Item {
        public TearsItem(Settings s) { super(s); }

        @Override
        public TypedActionResult<ItemStack> use(World w, PlayerEntity p, Hand h) {
            ItemStack st = p.getStackInHand(h);
            if (!w.isClient) {
                IsaacStats s = IsaacStats.of(p);
                int n = 1 + s.shots;
                for (int i = 0; i < n; i++) {
                    TearEntity t = new TearEntity(w, p);
                    t.damage = 3.5f + s.damage;
                    t.flags = s.flags;
                    t.maxAge = Math.max(8, 24 + (int) s.range);
                    float off = (i - (n - 1) / 2f) * 9f;
                    t.setVelocity(p, p.getPitch(), p.getYaw() + off, 0f, 1.6f, n > 2 ? 1.2f : 0.6f);
                    w.spawnEntity(t);
                }
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_SLIME_SQUISH_SMALL,
                        SoundCategory.PLAYERS, 0.6f, 1.4f + w.random.nextFloat() * 0.3f);
                p.getItemCooldownManager().set(this, s.delayTicks());
            }
            return TypedActionResult.success(st, w.isClient());
        }
    }

    public static class BombItem extends Item {
        public BombItem(Settings s) { super(s); }

        @Override
        public TypedActionResult<ItemStack> use(World w, PlayerEntity p, Hand h) {
            ItemStack st = p.getStackInHand(h);
            if (!w.isClient) {
                TntEntity t = new TntEntity(w, p.getX(), p.getEyeY() - 0.3, p.getZ(), p);
                t.setFuse(50);
                t.setVelocity(p.getRotationVec(1f).multiply(0.6).add(0, 0.2, 0));
                w.spawnEntity(t);
                w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 1f, 1f);
                if (!p.getAbilities().creativeMode) st.decrement(1);
            }
            return TypedActionResult.success(st, w.isClient());
        }
    }

    /** Монетка = "игровой автомат". */
    public static class PennyItem extends Item {
        public PennyItem(Settings s) { super(s); }

        @Override
        public TypedActionResult<ItemStack> use(World w, PlayerEntity p, Hand h) {
            ItemStack st = p.getStackInHand(h);
            if (!w.isClient) {
                if (!p.getAbilities().creativeMode) st.decrement(1);
                float luck = IsaacStats.of(p).luck;
                float r = w.random.nextFloat();
                float jack = 0.10f + luck * 0.02f;
                if (r < jack) {
                    Item prize = w.random.nextInt(4) == 0 ? randomActive(w) : randomPassive(w);
                    p.getInventory().offerOrDrop(new ItemStack(prize));
                    p.sendMessage(Text.literal("§6Джекпот!"), true);
                } else if (r < jack + 0.25f) {
                    p.getInventory().offerOrDrop(new ItemStack(RED_HEART));
                } else if (r < jack + 0.45f) {
                    p.getInventory().offerOrDrop(new ItemStack(BOMB));
                } else if (r < jack + 0.55f) {
                    p.getInventory().offerOrDrop(new ItemStack(w.random.nextBoolean() ? PILL : TAROT));
                } else {
                    p.sendMessage(Text.literal("§7Автомат проглотил монетку..."), true);
                }
                w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 1.5f);
            }
            return TypedActionResult.success(st, w.isClient());
        }
    }

    public static class HeartItem extends Item {
        private final boolean soul;
        public HeartItem(boolean soul, Settings s) { super(s); this.soul = soul; }

        @Override
        public TypedActionResult<ItemStack> use(World w, PlayerEntity p, Hand h) {
            ItemStack st = p.getStackInHand(h);
            if (soul) {
                if (!w.isClient) p.setAbsorptionAmount(Math.min(p.getAbsorptionAmount() + 4f, 20f));
            } else {
                if (p.getHealth() >= p.getMaxHealth()) return TypedActionResult.fail(st);
                if (!w.isClient) p.heal(2f);
            }
            if (!w.isClient) {
                w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.8f);
                if (!p.getAbilities().creativeMode) st.decrement(1);
            }
            return TypedActionResult.success(st, w.isClient());
        }
    }

    /** D6: перебрасывает все пассивки в инвентаре. */
    public static class D6Item extends Item {
        public D6Item(Settings s) { super(s); }

        @Override
        public TypedActionResult<ItemStack> use(World w, PlayerEntity p, Hand h) {
            ItemStack st = p.getStackInHand(h);
            if (!w.isClient) {
                boolean any = false;
                for (int i = 0; i < p.getInventory().size(); i++) {
                    ItemStack s = p.getInventory().getStack(i);
                    if (s.getItem() instanceof PassiveItem old) {
                        Item pick = old;
                        for (int t = 0; t < 6 && pick == old; t++) pick = randomPassive(w);
                        p.getInventory().setStack(i, new ItemStack(pick, s.getCount()));
                        any = true;
                    }
                }
                if (any) {
                    w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1f, 0.8f);
                    p.getItemCooldownManager().set(this, 600);
                } else {
                    p.sendMessage(Text.literal("§7Нечего перебрасывать"), true);
                }
            }
            return TypedActionResult.success(st, w.isClient());
        }
    }

    // ------------------------------------------------------------------ helpers
    public static Item randomPassive(World w) { return PASSIVES.get(w.random.nextInt(PASSIVES.size())); }
    public static Item randomActive(World w) { return ACTIVES.get(w.random.nextInt(ACTIVES.size())); }

    public static void drop(World w, double x, double y, double z, ItemStack s) {
        w.spawnEntity(new ItemEntity(w, x, y, z, s));
    }

    /** Таблица дропа с монстров. Удача увеличивает шанс. */
    public static Item rollDrop(World w, float luck) {
        float r = w.random.nextFloat() / Math.max(0.5f, 1f + luck * 0.15f);
        if (r < 0.18f) return PENNY;
        if (r < 0.24f) return RED_HEART;
        if (r < 0.29f) return BOMB;
        if (r < 0.32f) return SOUL_HEART;
        if (r < 0.35f) return PILL;
        if (r < 0.37f) return TAROT;
        if (r < 0.385f) return randomPassive(w);
        if (r < 0.395f) return randomActive(w);
        return null;
    }

    private static void msg(ServerPlayerEntity p, String s) { p.sendMessage(Text.literal(s), true); }

    private static void fx(ServerWorld w, ServerPlayerEntity p, SoundEvent s, float pitch) {
        w.playSound(null, p.getBlockPos(), s, SoundCategory.PLAYERS, 1f, pitch);
    }

    private static void effect(LivingEntity e, StatusEffect eff, int ticks, int amp) {
        e.addStatusEffect(new StatusEffectInstance(eff, ticks, amp));
    }

    private static void hurtNearby(ServerWorld w, ServerPlayerEntity p, double r, float dmg) {
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(r),
                x -> x != p && x instanceof Monster && x.isAlive())) {
            e.timeUntilRegen = 0;
            e.damage(w.getDamageSources().playerAttack(p), dmg);
        }
    }

    private static void tpRandom(ServerWorld w, ServerPlayerEntity p, int radius) {
        int x = p.getBlockX() + w.random.nextInt(radius * 2 + 1) - radius;
        int z = p.getBlockZ() + w.random.nextInt(radius * 2 + 1) - radius;
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        p.requestTeleport(x + 0.5, y + 1, z + 0.5);
    }

    private static void spawnTnt(ServerWorld w, ServerPlayerEntity p, int n, int fuse) {
        for (int i = 0; i < n; i++) {
            TntEntity t = new TntEntity(w, p.getX() + w.random.nextInt(9) - 4, p.getY() + 1,
                    p.getZ() + w.random.nextInt(9) - 4, p);
            t.setFuse(fuse + w.random.nextInt(20));
            w.spawnEntity(t);
        }
    }

    private static boolean pill(ServerWorld w, ServerPlayerEntity p) {
        switch (w.random.nextInt(10)) {
            case 0 -> { p.heal(6f); msg(p, "§aHealth up!"); }
            case 1 -> { p.damage(w.getDamageSources().magic(), 2f); msg(p, "§cHealth down"); }
            case 2 -> { effect(p, StatusEffects.SPEED, 600, 1); msg(p, "§aSpeed up!"); }
            case 3 -> { effect(p, StatusEffects.SLOWNESS, 400, 1); msg(p, "§cSpeed down"); }
            case 4 -> { effect(p, StatusEffects.NIGHT_VISION, 1200, 0); msg(p, "§eI can see forever!"); }
            case 5 -> { effect(p, StatusEffects.LEVITATION, 80, 1); msg(p, "§dTelepills? Нет, левитация!"); }
            case 6 -> { effect(p, StatusEffects.NAUSEA, 300, 0); effect(p, StatusEffects.BLINDNESS, 100, 0); msg(p, "§5Hallucinogenics"); }
            case 7 -> { p.getHungerManager().add(10, 1f); msg(p, "§6Full tummy"); }
            case 8 -> { effect(p, StatusEffects.ABSORPTION, 600, 2); msg(p, "§bBalls of Steel"); }
            default -> { effect(p, StatusEffects.GLOWING, 600, 0); msg(p, "§fX-Ray... почти"); }
        }
        fx(w, p, SoundEvents.ENTITY_GENERIC_DRINK, 1f);
        return true;
    }

    private static boolean tarot(ServerWorld w, ServerPlayerEntity p) {
        switch (w.random.nextInt(6)) {
            case 0 -> {
                BlockPos sp = w.getSpawnPos();
                p.requestTeleport(sp.getX() + 0.5, w.getTopY(Heightmap.Type.MOTION_BLOCKING, sp.getX(), sp.getZ()), sp.getZ() + 0.5);
                msg(p, "§dThe Fool: домой!");
            }
            case 1 -> { effect(p, StatusEffects.STRENGTH, 1200, 2); msg(p, "§cStrength!"); }
            case 2 -> {
                for (int i = 0; i < 3; i++) drop(w, p.getX(), p.getY() + 1, p.getZ(), new ItemStack(PENNY));
                drop(w, p.getX(), p.getY() + 1, p.getZ(), new ItemStack(RED_HEART));
                drop(w, p.getX(), p.getY() + 1, p.getZ(), new ItemStack(BOMB));
                msg(p, "§6Justice");
            }
            case 3 -> { hurtNearby(w, p, 12, 30f); msg(p, "§8Death"); }
            case 4 -> { effect(p, StatusEffects.ABSORPTION, 600, 3); effect(p, StatusEffects.STRENGTH, 600, 1); msg(p, "§aThe Empress"); }
            default -> { p.setHealth(p.getMaxHealth()); p.getHungerManager().add(20, 1f); msg(p, "§bTemperance"); }
        }
        fx(w, p, SoundEvents.ITEM_BOOK_PAGE_TURN, 1f);
        return true;
    }

    private static boolean megaBlast(ServerWorld w, ServerPlayerEntity p) {
        Vec3d start = p.getEyePos();
        Vec3d dir = p.getRotationVec(1f);
        BlockHitResult hr = w.raycast(new RaycastContext(start, start.add(dir.multiply(30)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        double max = hr.getType() == HitResult.Type.MISS ? 30 : hr.getPos().distanceTo(start);
        Set<Integer> hit = new HashSet<>();
        for (double d = 1; d <= max; d += 0.5) {
            Vec3d pos = start.add(dir.multiply(d));
            w.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 2, 0.05, 0.05, 0.05, 0.0);
            Box b = new Box(pos.x - 0.7, pos.y - 0.7, pos.z - 0.7, pos.x + 0.7, pos.y + 0.7, pos.z + 0.7);
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, b, x -> x != p && x.isAlive())) {
                if (hit.add(e.getId())) {
                    e.timeUntilRegen = 0;
                    e.damage(w.getDamageSources().playerAttack(p), 25f);
                }
            }
        }
        fx(w, p, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------ registration
    private static <T extends Item> T reg(String id, T item) {
        Registry.register(Registries.ITEM, new Identifier(IsaacMod.MOD_ID, id), item);
        ALL.add(item);
        return item;
    }

    private static void passive(String id, float tears, float dmg, int shots, float speed, int hp,
                                float range, float luck, int flags) {
        PassiveItem i = new PassiveItem(id, tears, dmg, shots, speed, hp, range, luck, flags,
                new FabricItemSettings().rarity(Rarity.RARE));
        reg(id, i);
        PASSIVES.add(i);
    }

    private static Item active(String id, int cooldown, boolean consume, Use use) {
        FabricItemSettings s = new FabricItemSettings();
        if (!consume) s.maxCount(1).rarity(Rarity.EPIC);
        Item i = reg(id, new ActiveItem(id, cooldown, consume, use, s));
        if (!consume) ACTIVES.add(i);
        return i;
    }

    public static void init() {
        TEARS = reg("isaac_tears", new TearsItem(new FabricItemSettings().maxCount(1).rarity(Rarity.UNCOMMON)));
        BOMB = reg("bomb", new BombItem(new FabricItemSettings()));
        PENNY = reg("penny", new PennyItem(new FabricItemSettings()));
        RED_HEART = reg("red_heart", new HeartItem(false, new FabricItemSettings()));
        SOUL_HEART = reg("soul_heart", new HeartItem(true, new FabricItemSettings()));
        D6 = reg("d6", new D6Item(new FabricItemSettings().maxCount(1).rarity(Rarity.EPIC)));
        ACTIVES.add(D6);

        PILL = active("pill", 0, true, ModItems::pill);
        TAROT = active("tarot_card", 0, true, ModItems::tarot);

        active("yum_heart", 600, false, (w, p) -> { p.heal(4f); fx(w, p, SoundEvents.ENTITY_PLAYER_BURP, 1f); return true; });
        active("necronomicon", 900, false, (w, p) -> {
            hurtNearby(w, p, 8, 8f);
            w.spawnParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 40, 3, 0.5, 3, 0.05);
            fx(w, p, SoundEvents.ENTITY_WITHER_SHOOT, 0.8f);
            return true;
        });
        active("kamikaze", 300, false, (w, p) -> {
            w.createExplosion(null, p.getX(), p.getY(), p.getZ(), 3.5f, World.ExplosionSourceType.NONE);
            return true;
        });
        active("teleport", 400, false, (w, p) -> { tpRandom(w, p, 60); fx(w, p, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f); return true; });
        active("anarchist_cookbook", 900, false, (w, p) -> { spawnTnt(w, p, 6, 40); return true; });
        active("how_to_jump", 100, false, (w, p) -> {
            Vec3d look = p.getRotationVec(1f);
            p.setVelocity(look.x * 0.9, 1.1, look.z * 0.9);
            p.velocityModified = true;
            effect(p, StatusEffects.SLOW_FALLING, 60, 0);
            return true;
        });
        active("book_of_shadows", 1200, false, (w, p) -> {
            effect(p, StatusEffects.RESISTANCE, 200, 4);
            effect(p, StatusEffects.ABSORPTION, 200, 1);
            fx(w, p, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.4f);
            return true;
        });
        active("hourglass", 900, false, (w, p) -> {
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(16),
                    x -> x != p && x instanceof Monster)) {
                effect(e, StatusEffects.SLOWNESS, 200, 4);
                effect(e, StatusEffects.WEAKNESS, 200, 1);
            }
            fx(w, p, SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.6f);
            return true;
        });
        active("mega_blast", 1500, false, ModItems::megaBlast);
        active("moms_pills", 200, false, ModItems::pill);

        passive("sad_onion", 1.0f, 0f, 0, 0f, 0, 0f, 0f, 0);
        passive("cricket_head", 0f, 1.0f, 0, 0f, 0, 0f, 0f, 0);
        passive("magic_mushroom", 0.5f, 0.5f, 0, 0.2f, 2, 0f, 0f, 0);
        passive("inner_eye", -0.6f, 0f, 2, 0f, 0, 0f, 0f, 0);
        passive("wire_coat_hanger", 0.7f, 0f, 0, 0f, 0, 0f, 0f, 0);
        passive("pentagram", 0f, 1.0f, 0, 0f, 0, 0f, 0f, 0);
        passive("speed_ball", 0f, 0f, 0, 0.3f, 0, 0f, 0f, 0);
        passive("heart_container", 0f, 0f, 0, 0f, 2, 0f, 0f, 0);
        passive("brimstone", -0.5f, 2.0f, 0, 0f, 0, 0f, 0f, IsaacStats.WITHER);
        passive("polyphemus", -0.8f, 2.5f, 0, 0f, 0, 0f, 0f, 0);
        passive("spoon_bender", 0f, 0f, 0, 0f, 0, 0f, 0f, IsaacStats.HOMING);
        passive("twenty_twenty", -0.3f, 0f, 1, 0f, 0, 0f, 0f, 0);
        passive("mutant_spider", -0.8f, 0f, 3, 0f, 0, 0f, 0f, 0);
        passive("ipecac", -0.8f, 1.5f, 0, 0f, 0, 0f, 0f, IsaacStats.EXPLODE | IsaacStats.POISON);
        passive("fire_mind", 0f, 0.5f, 0, 0f, 0, 0f, 0f, IsaacStats.FIRE);
        passive("jesus_juice", 0f, 0.8f, 0, 0f, 0, 6f, 0f, 0);
        passive("number_one", 1.5f, 0f, 0, 0f, 0, -8f, 0f, 0);
        passive("sacred_heart", -0.4f, 2.3f, 0, 0f, 2, 0f, 0f, IsaacStats.HOMING);
        passive("godhead", 0f, 1.0f, 0, 0f, 0, 4f, 0f, IsaacStats.HOMING);
        passive("lump_of_coal", 0f, 1.0f, 0, 0f, 0, 6f, 0f, 0);
        passive("dr_fetus", -1.0f, 1.0f, 0, 0f, 0, 0f, 0f, IsaacStats.EXPLODE);
        passive("cupids_arrow", 0f, 0f, 0, 0f, 0, 0f, 0f, IsaacStats.PIERCE);
        passive("sinus_infection", 0.3f, 0f, 0, 0f, 0, 0f, 0f, IsaacStats.SLOW);
        passive("toxic_shock", 0f, 0.3f, 0, 0f, 0, 0f, 0f, IsaacStats.POISON);
        passive("ouija_board", 0.2f, 0f, 0, 0f, 0, 0f, 0f, IsaacStats.PIERCE);
        passive("vampire_fang", 0f, 0.3f, 0, 0f, 0, 0f, 0f, IsaacStats.LIFESTEAL);
        passive("cat_o_nine_tails", 0.5f, 0.5f, 0, 0f, 0, 0f, 0f, 0);
        passive("transcendence", 0f, 0f, 0, 0.4f, 0, 0f, 1.0f, 0);
        passive("lucky_foot", 0f, 0f, 0, 0f, 0, 0f, 2.0f, 0);
        passive("clover", 0f, 0f, 0, 0f, 0, 0f, 1.5f, 0);
        passive("stigmata", 0f, 0.3f, 0, 0f, 2, 0f, 0f, 0);
        passive("cube_of_meat", 0.3f, 0.5f, 0, 0f, 0, 0f, 0f, 0);
        passive("growth_hormones", 0f, 1.0f, 0, 0.2f, 0, 0f, 0f, 0);
        passive("blood_of_the_martyr", 0f, 1.0f, 0, 0f, 0, 0f, 0f, 0);
    }
}
