package io.github.shade.mixin;

import io.github.shade.story.event.PlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin 拦截 ServerPlayer.triggerRecipeCrafted — 检测玩家合成物品
 */
@Mixin(ServerPlayer.class)
public class CraftingMixin {

    @Inject(method = "triggerRecipeCrafted", at = @At("HEAD"))
    private void onCraft(RecipeHolder<?> recipe, List<ItemStack> items, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        PlayerEvents.CRAFTED.invoker().onCraft(player, recipe, items);
    }
}
