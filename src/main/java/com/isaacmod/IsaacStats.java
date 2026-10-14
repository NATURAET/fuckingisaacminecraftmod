package com.isaacmod;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Статы считаются по предметам-пассивкам в инвентаре игрока. */
public final class IsaacStats {
    public static final int HOMING = 1, PIERCE = 2, FIRE = 4, POISON = 8,
            SLOW = 16, LIFESTEAL = 32, EXPLODE = 64, WITHER = 128;

    public float tears, damage, speed, range, luck;
    public int shots, health, flags;

    public static IsaacStats of(PlayerEntity p) {
        IsaacStats s = new IsaacStats();
        List<ItemStack> all = new ArrayList<>(p.getInventory().main);
        all.addAll(p.getInventory().offHand);
        for (ItemStack st : all) {
            if (st.getItem() instanceof ModItems.PassiveItem pi) {
                int n = Math.min(st.getCount(), 5);
                s.tears += pi.tears * n;
                s.damage += pi.damage * n;
                s.speed += pi.speed * n;
                s.range += pi.range * n;
                s.luck += pi.luck * n;
                s.shots += pi.shots * n;
                s.health += pi.hp * n;
                s.flags |= pi.flags;
            }
        }
        return s;
    }

    /** Задержка между слезами в тиках. */
    public int delayTicks() {
        float f = Math.max(0.4f, 1f + tears * 0.35f);
        return Math.max(2, Math.round(10f / f));
    }
}
