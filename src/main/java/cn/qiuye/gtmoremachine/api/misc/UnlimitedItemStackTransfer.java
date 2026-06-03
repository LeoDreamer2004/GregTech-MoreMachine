package cn.qiuye.gtmoremachine.api.misc;

import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import com.lowdragmc.lowdraglib.side.item.ItemTransferHelper;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

import javax.annotation.Nonnull;

public class UnlimitedItemStackTransfer extends CustomItemStackHandler implements IItemTransfer {

    public UnlimitedItemStackTransfer(int size) {
        super(size);
    }

    public UnlimitedItemStackTransfer(NonNullList<ItemStack> stacks) {
        super(stacks);
    }

    public UnlimitedItemStackTransfer(ItemStack stack) {
        super(stack);
    }

    @Setter
    private Function<ItemStack, Boolean> filter;

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return filter == null || filter.apply(stack);
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        validateSlotIndex(slot);
        this.stacks.set(slot, stack);
        onContentsChanged(slot);
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate, boolean notifyChanges) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!isItemValid(slot, stack)) return stack;

        validateSlotIndex(slot);

        ItemStack existing = this.stacks.get(slot);
        int limit = getStackLimit(slot, stack);

        if (!existing.isEmpty()) {
            if (!ItemTransferHelper.canItemStacksStack(stack, existing)) {
                return stack;
            }
            limit -= existing.getCount();
        }

        if (limit <= 0) {
            return stack;
        }

        boolean reachedLimit = stack.getCount() > limit;

        if (!simulate) {
            if (existing.isEmpty()) {
                this.stacks.set(slot, reachedLimit ? ItemTransferHelper.copyStackWithSize(stack, limit) : stack.copy());
            } else {
                existing.grow(reachedLimit ? limit : stack.getCount());
            }

            if (notifyChanges) {
                onContentsChanged(slot);
            }
        }

        return reachedLimit ? ItemTransferHelper.copyStackWithSize(stack, stack.getCount() - limit) : ItemStack.EMPTY;
    }

    @Override
    @NotNull
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return insertItem(slot, stack, simulate, !simulate);
    }

    @Override
    @NotNull
    public ItemStack extractItem(int slot, int amount, boolean simulate, boolean notifyChanges) {
        if (amount == 0) return ItemStack.EMPTY;

        validateSlotIndex(slot);

        ItemStack existing = this.stacks.get(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;

        int toExtract = Math.min(amount, getSlotLimit(slot));

        if (existing.getCount() <= toExtract) {
            if (!simulate) {
                this.stacks.set(slot, ItemStack.EMPTY);
                if (notifyChanges) {
                    onContentsChanged(slot);
                }
                return existing;
            } else {
                return existing.copy();
            }
        } else {
            if (!simulate) {
                this.stacks.set(slot, ItemTransferHelper.copyStackWithSize(existing, existing.getCount() - toExtract));
                if (notifyChanges) {
                    onContentsChanged(slot);
                }
            }
            return ItemTransferHelper.copyStackWithSize(existing, toExtract);
        }
    }

    @Override
    @NotNull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return extractItem(slot, amount, simulate, !simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    protected int getStackLimit(int slot, @NotNull ItemStack stack) {
        return Integer.MAX_VALUE;
    }

    @Override
    public CompoundTag serializeNBT() {
        ListTag nbtTagList = new ListTag();
        for (int i = 0; i < stacks.size(); i++) {
            if (!stacks.get(i).isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                var is = stacks.get(i).copy();
                itemTag.putInt("realCount", is.getCount());
                is.setCount(1);
                is.save(itemTag);
                nbtTagList.add(itemTag);
            }
        }
        CompoundTag nbt = new CompoundTag();
        nbt.put("Items", nbtTagList);
        nbt.putInt("Size", stacks.size());
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        setSize(nbt.contains("Size", Tag.TAG_INT) ? nbt.getInt("Size") : stacks.size());
        ListTag tagList = nbt.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < tagList.size(); i++) {
            CompoundTag itemTags = tagList.getCompound(i);
            int slot = itemTags.getInt("Slot");

            if (slot >= 0 && slot < stacks.size()) {
                var is = ItemStack.of(itemTags).copy();
                is.setCount(itemTags.getInt("realCount"));
                stacks.set(slot, is);
            }
        }
        onLoad();
    }

    @Override
    @NotNull
    public Object createSnapshot() {
        ItemStack[] copied = new ItemStack[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) {
            copied[i] = stacks.get(i).copy();
        }
        return copied;
    }

    @Override
    public void restoreFromSnapshot(Object snapshot) {
        if (snapshot instanceof ItemStack[] copied && copied.length == stacks.size()) {
            for (int i = 0; i < stacks.size(); i++) {
                stacks.set(i, copied[i].copy());
            }
        }
    }
}
