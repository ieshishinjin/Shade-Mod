package io.github.shade.story.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * 玩家自定义事件 — 补充 Fabric API 未提供的玩家行为事件
 */
public final class PlayerEvents {

    /**
     * 玩家合成物品后调用
     */
    public static final Event<Crafted> CRAFTED = EventFactory.createArrayBacked(
            Crafted.class,
            callbacks -> (player, recipe, craftedItems) -> {
                for (Crafted callback : callbacks) {
                    callback.onCraft(player, recipe, craftedItems);
                }
            });

    @FunctionalInterface
    public interface Crafted {
        /**
         * 玩家合成物品后调用
         *
         * @param player       合成玩家
         * @param recipe       使用的配方
         * @param craftedItems 合成产物列表
         */
        void onCraft(ServerPlayer player, RecipeHolder<?> recipe, java.util.List<net.minecraft.world.item.ItemStack> craftedItems);
    }

    private PlayerEvents() {}
}
