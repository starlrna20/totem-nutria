package com.supertotem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.DeathProtection;

public class SuperTotemMod implements ModInitializer {
    public static final String MOD_ID = "supertotem";

    /** Probabilidad de drop al matar un evoker de una raid: 0,5 %. */
    public static final double DROP_CHANCE = 0.005;
    /** 4.0 de vida = 2 corazones completos. */
    public static final double EXTRA_HEALTH = 4.0;
    /** Velocidad II = amplificador 1. */
    public static final int SPEED_AMPLIFIER = 1;

    private static final ResourceLocation HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "extra_hearts");

    public static Item SUPREME_TOTEM;
    private int tick = 0;

    @Override
    public void onInitialize() {
        // --- Objeto: se comporta como el totem normal (death_protection) y brilla para distinguirlo ---
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "supreme_totem"));
        SUPREME_TOTEM = Registry.register(BuiltInRegistries.ITEM, key,
                new Item(new Item.Properties()
                        .setId(key)
                        .stacksTo(1)
                        .rarity(Rarity.EPIC)
                        .component(DataComponents.DEATH_PROTECTION, DeathProtection.TOTEM_OF_UNDYING)
                        .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT)
                .register(entries -> entries.accept(SUPREME_TOTEM));

        // --- Drop: 0,5 % al matar (un jugador) un evoker mientras hay una raid activa cerca ---
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof Evoker evoker)) return;
            if (!(evoker.level() instanceof ServerLevel level)) return;
            if (!(source.getEntity() instanceof Player)) return;
            if (level.getRaids().getNearbyRaid(evoker.blockPosition(), 9216) == null) return;
            if (level.random.nextDouble() >= DROP_CHANCE) return;
            level.addFreshEntity(new ItemEntity(level,
                    evoker.getX(), evoker.getY(), evoker.getZ(), new ItemStack(SUPREME_TOTEM)));
        });

        // --- Buffs: se revisan 1 vez por segundo (muy ligero) ---
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tick % 20 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) updateBuffs(p);
        });
    }

    /** Mientras el jugador lleve el totem en cualquier parte del inventario: +2 corazones y Velocidad II infinita. */
    private static void updateBuffs(ServerPlayer p) {
        boolean has = p.getInventory().contains(s -> s.is(SUPREME_TOTEM));

        AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            boolean applied = hp.hasModifier(HEALTH_MODIFIER_ID);
            if (has && !applied) {
                hp.addTransientModifier(new AttributeModifier(
                        HEALTH_MODIFIER_ID, EXTRA_HEALTH, AttributeModifier.Operation.ADD_VALUE));
            } else if (!has && applied) {
                hp.removeModifier(HEALTH_MODIFIER_ID);
                if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
            }
        }

        MobEffectInstance cur = p.getEffect(MobEffects.MOVEMENT_SPEED);
        if (has) {
            if (cur == null || cur.getAmplifier() < SPEED_AMPLIFIER) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                        MobEffectInstance.INFINITE_DURATION, SPEED_AMPLIFIER, true, false, true));
            }
        } else if (cur != null && cur.isInfiniteDuration() && cur.getAmplifier() == SPEED_AMPLIFIER) {
            p.removeEffect(MobEffects.MOVEMENT_SPEED);
        }
    }
}
