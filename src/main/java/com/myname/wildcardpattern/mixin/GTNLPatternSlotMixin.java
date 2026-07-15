package com.myname.wildcardpattern.mixin;

import java.util.Collections;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import com.myname.wildcardpattern.compat.gtnl.GTNLPatternCompat;
import com.myname.wildcardpattern.compat.gtnl.GTNLPatternSlotState;
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.objects.GTDualInputPattern;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

@Pseudo
@Mixin(targets = GTNLPatternCompat.SLOT_TARGET, remap = false)
public abstract class GTNLPatternSlotMixin implements GTNLPatternSlotBridge {

    @Shadow
    @Final
    public ItemStack pattern;

    @Shadow
    @Final
    @Mutable
    public ICraftingPatternDetails patternDetails;

    @Shadow
    @Final
    public IMetaTileEntity parentMTE;

    @Shadow
    public abstract boolean isEmpty();

    @Shadow
    public abstract ItemStack[] getItemInputs();

    @Shadow
    public abstract FluidStack[] getFluidInputs();

    @Shadow
    public abstract boolean insertItemsAndFluids(InventoryCrafting table);

    @Unique
    private ICraftingPatternDetails wildcardpattern$representative;

    @Unique
    private GTNLPatternSlotState<ICraftingPatternDetails> wildcardpattern$state;

    @Inject(
        method = "<init>(Lnet/minecraft/item/ItemStack;Lnet/minecraft/nbt/NBTTagCompound;"
            + "Lgregtech/api/interfaces/metatileentity/IMetaTileEntity;I)V",
        at = @At("RETURN"),
        require = 0)
    private void wildcardpattern$initialize(
        ItemStack pattern,
        NBTTagCompound saved,
        IMetaTileEntity parent,
        int index,
        CallbackInfo ci) {
        this.wildcardpattern$representative = this.patternDetails;
        if (!wildcardpattern$isWildcard()) {
            return;
        }
        this.wildcardpattern$state =
            new GTNLPatternSlotState<>(this.wildcardpattern$representative, GTNLPatternCompat::generatedId);
        wildcardpattern$refreshDetails(parent.getBaseMetaTileEntity().getWorld());
        String savedId = saved == null ? "" : saved.getString(GTNLPatternCompat.ACTIVE_PATTERN_ID_KEY);
        this.wildcardpattern$state.recover(
            savedId,
            !isEmpty(),
            details -> GTNLPatternCompat.inputsMatch(details, getItemInputs(), getFluidInputs()));
        wildcardpattern$syncActiveDetail();
    }

    @Inject(method = "getPatternDetails", at = @At("HEAD"), require = 0)
    private void wildcardpattern$resetDetailWhenEmpty(CallbackInfoReturnable<ICraftingPatternDetails> cir) {
        wildcardpattern$syncActiveDetail();
    }

    @Inject(method = "getPatternInputs", at = @At("HEAD"), cancellable = true, require = 0)
    private void wildcardpattern$guardAmbiguousInputs(CallbackInfoReturnable<GTDualInputPattern> cir) {
        wildcardpattern$syncActiveDetail();
        if (this.wildcardpattern$state == null || !this.wildcardpattern$state.isUnresolved() || isEmpty()) {
            return;
        }
        GTDualInputPattern empty = new GTDualInputPattern();
        empty.inputItems = GTValues.emptyItemStackArray;
        empty.inputFluid = GTValues.emptyFluidStackArray;
        cir.setReturnValue(empty);
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), cancellable = true, require = 0)
    private void wildcardpattern$saveActiveId(
        NBTTagCompound target,
        CallbackInfoReturnable<NBTTagCompound> cir) {
        if (this.wildcardpattern$state == null) {
            return;
        }
        NBTTagCompound written = cir.getReturnValue();
        String activeId = this.wildcardpattern$state.getPersistentId(!isEmpty());
        if (activeId.isEmpty()) {
            written.removeTag(GTNLPatternCompat.ACTIVE_PATTERN_ID_KEY);
        } else {
            written.setString(GTNLPatternCompat.ACTIVE_PATTERN_ID_KEY, activeId);
        }
        cir.setReturnValue(written);
    }

    @Override
    public boolean wildcardpattern$isOperational() {
        return !wildcardpattern$isWildcard() || this.wildcardpattern$state != null;
    }

    @Override
    public boolean wildcardpattern$isWildcard() {
        return WildcardPatternGenerator.isWildcardPattern(this.pattern);
    }

    @Override
    public List<ICraftingPatternDetails> wildcardpattern$getRegistrationDetails(World world) {
        if (!wildcardpattern$isWildcard()) {
            return this.patternDetails == null
                ? Collections.emptyList()
                : Collections.singletonList(this.patternDetails);
        }
        wildcardpattern$refreshDetails(world);
        return this.wildcardpattern$state == null
            ? Collections.emptyList()
            : this.wildcardpattern$state.getExpandedDetails();
    }

    @Override
    public boolean wildcardpattern$owns(ICraftingPatternDetails details, World world) {
        String requestedId = GTNLPatternCompat.generatedId(details);
        if (requestedId.isEmpty()) {
            return false;
        }
        for (ICraftingPatternDetails candidate : wildcardpattern$getRegistrationDetails(world)) {
            if (requestedId.equals(GTNLPatternCompat.generatedId(candidate))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public GTNLPatternSlotState.Snapshot<ICraftingPatternDetails> wildcardpattern$beginActivation(
        ICraftingPatternDetails details) {
        if (this.wildcardpattern$state == null) {
            return null;
        }
        if (!isEmpty() && this.wildcardpattern$state.getActiveId().isEmpty()) {
            this.wildcardpattern$state.recover(
                "",
                true,
                candidate -> GTNLPatternCompat.inputsMatch(candidate, getItemInputs(), getFluidInputs()));
        }
        GTNLPatternSlotState.Snapshot<ICraftingPatternDetails> before = this.wildcardpattern$state.snapshot();
        if (!this.wildcardpattern$state.activate(details, !isEmpty())) {
            return null;
        }
        // The slot is still empty until pushPattern inserts the table.
        this.patternDetails = this.wildcardpattern$state.current(true);
        return before;
    }

    @Override
    public void wildcardpattern$rollbackActivation(
        GTNLPatternSlotState.Snapshot<ICraftingPatternDetails> snapshot) {
        if (this.wildcardpattern$state != null && snapshot != null) {
            this.wildcardpattern$state.restore(snapshot);
            wildcardpattern$syncActiveDetail();
        }
    }

    @Override
    public String wildcardpattern$getActiveId() {
        return this.wildcardpattern$state == null ? "" : this.wildcardpattern$state.getActiveId();
    }

    @Override
    public boolean wildcardpattern$insert(InventoryCrafting table) {
        return table != null && insertItemsAndFluids(table);
    }

    @Unique
    private void wildcardpattern$refreshDetails(World world) {
        if (this.wildcardpattern$state == null || world == null) {
            return;
        }
        this.wildcardpattern$state.replaceExpandedDetails(GTNLPatternCompat.expand(this.pattern, world));
        wildcardpattern$syncActiveDetail();
    }

    @Unique
    private void wildcardpattern$syncActiveDetail() {
        if (this.wildcardpattern$state == null) {
            return;
        }
        ICraftingPatternDetails current = this.wildcardpattern$state.current(!isEmpty());
        this.patternDetails = current == null ? this.wildcardpattern$representative : current;
    }
}
