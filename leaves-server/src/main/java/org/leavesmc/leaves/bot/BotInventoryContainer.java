package org.leavesmc.leaves.bot;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.Material;
import org.leavesmc.leaves.bot.agent.Actions;

import javax.annotation.Nonnull;

public class BotInventoryContainer extends Inventory {

    private static final ItemStack PLACEHOLDER;
    private static final ItemStack STOP_BUTTON;
    private static final ItemStack SWAP_HANDS_BUTTON;
    private static final ItemStack DROP_BUTTON;
    private static final ItemStack TOGGLE_MODE_BUTTON_ACTION;
    private static final ItemStack TOGGLE_MODE_BUTTON_HOTBAR;
    private static final ItemStack ATTACK_BUTTON;
    private static final ItemStack INTERVAL_ATTACK_BUTTON;
    private static final ItemStack USE_BUTTON;
    private static final ItemStack JUMP_BUTTON;
    private static final ItemStack SNEAK_BUTTON;
    private static final ItemStack LOOK_BUTTON;
    private static final ItemStack DISMOUNT_BUTTON;
    private static final ItemStack MOVE_BUTTON;
    private static final ItemStack ENDER_CHEST_BUTTON;
    private static final ItemStack[] HOTBAR_SLOT_BUTTONS;
    private static final ItemStack BARRIER;

    static {
        CompoundTag customData = new CompoundTag();
        customData.putBoolean("Leaves.Gui.Placeholder", true);
        DataComponentPatch patch = DataComponentPatch.builder()
            .set(DataComponents.CUSTOM_NAME, Component.empty())
            .set(DataComponents.CUSTOM_DATA, CustomData.of(customData))
            .build();
        PLACEHOLDER = new ItemStack(Items.STRUCTURE_VOID);
        PLACEHOLDER.applyComponents(patch);

        STOP_BUTTON = createButton(Material.RED_WOOL, "\u00a7c\u505c\u6b62\u6240\u6709\u64cd\u4f5c");
        SWAP_HANDS_BUTTON = createButton(Material.SWEET_BERRIES, "\u00a7e\u4ea4\u6362\u4e3b\u526f\u624b");
        DROP_BUTTON = createButton(Material.BUCKET, "\u00a7e\u4e22\u5f03\u7269\u54c1");
        TOGGLE_MODE_BUTTON_ACTION = createButton(Material.REDSTONE_BLOCK, "\u00a7b\u5207\u6362\u5230\u5feb\u6377\u680f\u6a21\u5f0f");
        TOGGLE_MODE_BUTTON_HOTBAR = createButton(Material.EMERALD_BLOCK, "\u00a7b\u5207\u6362\u5230\u52a8\u4f5c\u6a21\u5f0f");
        ATTACK_BUTTON = createButton(Material.IRON_SWORD, "\u00a7a\u653b\u51fb");
        INTERVAL_ATTACK_BUTTON = createButton(Material.GOLDEN_SWORD, "\u00a7a\u6301\u7eed\u653b\u51fb");
        USE_BUTTON = createButton(Material.BOW, "\u00a7a\u4f7f\u7528\u7269\u54c1");
        JUMP_BUTTON = createButton(Material.FEATHER, "\u00a7a\u8df3\u8dc3");
        SNEAK_BUTTON = createButton(Material.LEATHER_BOOTS, "\u00a7a\u6f5c\u884c");
        LOOK_BUTTON = createButton(Material.ENDER_EYE, "\u00a7a\u770b\u5411\u73a9\u5bb6");
        DISMOUNT_BUTTON = createButton(Material.SADDLE, "\u00a7a\u9a91\u4e58");
        MOVE_BUTTON = createButton(Material.DIAMOND_BOOTS, "\u00a7a\u79fb\u52a8");
        ENDER_CHEST_BUTTON = createButton(Material.ENDER_CHEST, "\u00a7a\u672b\u5f71\u7bb1");

        HOTBAR_SLOT_BUTTONS = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            HOTBAR_SLOT_BUTTONS[i] = createButton(Material.CRAFTING_TABLE, "\u00a7f\u5feb\u6377\u680f " + (i + 1));
        }

        BARRIER = new ItemStack(Items.BARRIER);
        CompoundTag barrierData = new CompoundTag();
        barrierData.putBoolean("Leaves.Gui.Barrier", true);
        DataComponentPatch barrierPatch = DataComponentPatch.builder()
            .set(DataComponents.CUSTOM_DATA, CustomData.of(barrierData))
            .build();
        BARRIER.applyComponents(barrierPatch);
    }

    private static ItemStack createButton(Material bukkitMaterial, String name) {
        org.bukkit.inventory.ItemStack bukkitStack = new org.bukkit.inventory.ItemStack(bukkitMaterial);
        net.minecraft.world.item.ItemStack stack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(bukkitStack);
        CompoundTag customData = new CompoundTag();
        customData.putBoolean("Leaves.Gui.Button", true);
        customData.putString("Leaves.Gui.ButtonType", name);
        DataComponentPatch buttonPatch = DataComponentPatch.builder()
            .set(DataComponents.CUSTOM_NAME, Component.literal(name))
            .set(DataComponents.CUSTOM_DATA, CustomData.of(customData))
            .build();
        stack.applyComponents(buttonPatch);
        return stack;
    }

    private final Inventory original;
    private boolean hotbarMode = false;

    public BotInventoryContainer(Inventory original) {
        super(original.player, original.equipment);
        this.original = original;
    }

    public void toggleMode() {
        this.hotbarMode = !this.hotbarMode;
    }

    public boolean isHotbarMode() {
        return hotbarMode;
    }

    @Override
    public int getContainerSize() {
        return 54;
    }

    @Override
    @Nonnull
    public ItemStack getItem(int slot) {
        int realSlot = convertSlot(slot);
        if (realSlot == -999) {
            return getButtonItem(slot);
        }
        if (realSlot == -1000) {
            return BARRIER;
        }
        return original.getItem(realSlot);
    }

    private ItemStack getButtonItem(int slot) {
        return switch (slot) {
            case 0 -> STOP_BUTTON;
            case 5 -> SWAP_HANDS_BUTTON;
            case 6 -> DROP_BUTTON;
            case 8 -> hotbarMode ? TOGGLE_MODE_BUTTON_ACTION : TOGGLE_MODE_BUTTON_HOTBAR;
            case 9 -> hotbarMode ? HOTBAR_SLOT_BUTTONS[0] : ATTACK_BUTTON;
            case 10 -> hotbarMode ? HOTBAR_SLOT_BUTTONS[1] : INTERVAL_ATTACK_BUTTON;
            case 11 -> hotbarMode ? HOTBAR_SLOT_BUTTONS[2] : USE_BUTTON;
            case 12 -> hotbarMode ? HOTBAR_SLOT_BUTTONS[3] : JUMP_BUTTON;
            case 13 -> hotbarMode ? HOTBAR_SLOT_BUTTONS[4] : SNEAK_BUTTON;
            case 14 -> hotbarMode ? HOTBAR_SLOT_BUTTONS[5] : LOOK_BUTTON;
            case 15 -> hotbarMode ? HOTBAR_SLOT_BUTTONS[6] : DISMOUNT_BUTTON;
            case 16 -> hotbarMode ? HOTBAR_SLOT_BUTTONS[7] : MOVE_BUTTON;
            case 17 -> hotbarMode ? HOTBAR_SLOT_BUTTONS[8] : ENDER_CHEST_BUTTON;
            default -> PLACEHOLDER;
        };
    }

    public int convertSlot(int slot) {
        return switch (slot) {
            case 0, 5, 6, 8 -> -999;
            case 1 -> 39;
            case 2 -> 38;
            case 3 -> 37;
            case 4 -> 36;
            case 7 -> 40;
            case 9, 10, 11, 12, 13, 14, 15, 16, 17 -> hotbarMode ? (slot - 9) : -999;
            case 18, 19, 20, 21, 22, 23, 24, 25, 26,
                 27, 28, 29, 30, 31, 32, 33, 34, 35,
                 36, 37, 38, 39, 40, 41, 42, 43, 44 -> slot - 9;
            case 45, 46, 47, 48, 49, 50, 51, 52, 53 -> slot - 45;
            default -> -999;
        };
    }

    @Override
    @Nonnull
    public ItemStack removeItem(int slot, int amount) {
        int realSlot = convertSlot(slot);
        if (realSlot == -999 || realSlot == -1000) {
            handleButtonClick(slot);
            return ItemStack.EMPTY;
        }
        ItemStack removed = original.removeItem(realSlot, amount);
        player.detectEquipmentUpdates();
        return removed;
    }

    @Override
    @Nonnull
    public ItemStack removeItemNoUpdate(int slot) {
        int realSlot = convertSlot(slot);
        if (realSlot == -999 || realSlot == -1000) {
            return ItemStack.EMPTY;
        }
        return original.removeItemNoUpdate(realSlot);
    }

    @Override
    public void setItem(int slot, @Nonnull ItemStack stack) {
        int realSlot = convertSlot(slot);
        if (realSlot == -999 || realSlot == -1000) {
            return;
        }
        original.setItem(realSlot, stack);
        player.detectEquipmentUpdates();
    }

    private void handleButtonClick(int slot) {
        if (!(player instanceof ServerBot bot)) {
            return;
        }
        switch (slot) {
            case 0 -> bot.getBukkitEntity().stopAllActions();
            case 5 -> {
                ItemStack mainHand = bot.getMainHandItem();
                ItemStack offHand = bot.getOffhandItem();
                bot.setItemInHand(InteractionHand.MAIN_HAND, offHand);
                bot.setItemInHand(InteractionHand.OFF_HAND, mainHand);
                bot.detectEquipmentUpdates();
            }
            case 6 -> bot.dropAll(false);
            case 8 -> toggleMode();
            case 9, 10, 11, 12, 13, 14, 15, 16, 17 -> {
                if (hotbarMode) {
                    original.setSelectedSlot(slot - 9);
                } else {
                    handleActionButtonClick(bot, slot);
                }
            }
        }
    }

    private void handleActionButtonClick(ServerBot bot, int slot) {
        String actionName = switch (slot) {
            case 9 -> "attack";
            case 10 -> "attack";
            case 11 -> "use";
            case 12 -> "jump";
            case 13 -> "sneak";
            case 14 -> "look";
            case 15 -> "mount";
            case 16 -> "move";
            case 17 -> null;
            default -> null;
        };
        if (actionName == null) {
            return;
        }
        toggleAction(bot, actionName, slot == 10);
    }

    private void toggleAction(ServerBot bot, String actionName, boolean continuous) {
        boolean isRunning = bot.getBotActions().stream()
            .anyMatch(a -> a.getName().equals(actionName) && !a.isCancelled());
        if (isRunning) {
            bot.getBotActions().stream()
                .filter(a -> a.getName().equals(actionName) && !a.isCancelled())
                .forEach(a -> a.stop(bot, org.leavesmc.leaves.event.bot.BotActionStopEvent.Reason.PLUGIN));
        } else {
            Actions<?> actionHolder = Actions.getByName(actionName);
            if (actionHolder != null) {
                var action = actionHolder.create();
                if (continuous && "attack".equals(actionName)) {
                    action.setDoNumber(-1);
                    action.setDoIntervalTick(20);
                }
                bot.addBotAction(action, null);
            }
        }
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(@Nonnull Player player) {
        if (this.player.isRemoved()) {
            return false;
        }
        return !(player.distanceToSqr(this.player) > 64.0);
    }
}
