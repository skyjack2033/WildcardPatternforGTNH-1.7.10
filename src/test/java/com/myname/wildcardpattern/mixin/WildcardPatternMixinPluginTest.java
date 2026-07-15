package com.myname.wildcardpattern.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.junit.jupiter.api.Test;

import com.myname.wildcardpattern.compat.gtnl.GTNLPatternCompat;

class WildcardPatternMixinPluginTest {

    private static final String HATCH_MIXIN =
        "com.myname.wildcardpattern.mixin.GTNLSuperCraftingInputHatchMEMixin";

    @Test
    void ordinaryMixinsAlwaysApply() {
        WildcardPatternMixinPlugin plugin = new WildcardPatternMixinPlugin(name -> false);

        assertTrue(plugin.shouldApplyMixin("appeng.helpers.DualityInterface", "ordinary.Mixin"));
    }

    @Test
    void gtnlMixinsAreSkippedWhenGTNLIsAbsent() {
        WildcardPatternMixinPlugin plugin = new WildcardPatternMixinPlugin(name -> false);

        assertFalse(plugin.shouldApplyMixin(GTNLPatternCompat.HATCH_TARGET, HATCH_MIXIN));
    }

    @Test
    void bothTargetClassesMustPassValidation() {
        Set<String> compatible = new HashSet<>();
        compatible.add(GTNLPatternCompat.HATCH_TARGET);
        WildcardPatternMixinPlugin plugin = new WildcardPatternMixinPlugin(compatible::contains);

        assertFalse(plugin.shouldApplyMixin(GTNLPatternCompat.HATCH_TARGET, HATCH_MIXIN));
    }

    @Test
    void gtnlMixinsApplyWhenBothTargetClassesAreCompatible() {
        Set<String> compatible = new HashSet<>();
        compatible.add(GTNLPatternCompat.HATCH_TARGET);
        compatible.add(GTNLPatternCompat.SLOT_TARGET);
        WildcardPatternMixinPlugin plugin = new WildcardPatternMixinPlugin(compatible::contains);

        assertTrue(plugin.shouldApplyMixin(GTNLPatternCompat.HATCH_TARGET, HATCH_MIXIN));
    }

    @Test
    void hatchBytecodeMustContainEveryRequiredMember() {
        assertTrue(
            WildcardPatternMixinPlugin.hasCompatibleStructure(
                GTNLPatternCompat.HATCH_TARGET,
                hatchClassBytes(true)));
        assertFalse(
            WildcardPatternMixinPlugin.hasCompatibleStructure(
                GTNLPatternCompat.HATCH_TARGET,
                hatchClassBytes(false)));
    }

    private static byte[] hatchClassBytes(boolean includePushPattern) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            GTNLPatternCompat.HATCH_TARGET.replace('.', '/'),
            null,
            "java/lang/Object",
            null);
        writer.visitField(
            Opcodes.ACC_PUBLIC,
            "internalInventory",
            "[Lcom/science/gtnl/common/machine/hatch/SuperCraftingInputHatchME$PatternSlot;",
            null,
            null);
        writer.visitField(Opcodes.ACC_PUBLIC, "patternDetailsPatternSlotMap", "Ljava/util/Map;", null, null);
        writer.visitField(Opcodes.ACC_PUBLIC, "processingLogics", "Ljava/util/List;", null, null);
        writer.visitField(Opcodes.ACC_PUBLIC, "justHadNewItems", "Z", null, null);
        writer.visitField(Opcodes.ACC_PUBLIC, "supportFluids", "Z", null, null);
        abstractMethod(writer, "isActive", "()Z");
        abstractMethod(
            writer,
            "provideCrafting",
            "(Lappeng/api/networking/crafting/ICraftingProviderHelper;)V");
        if (includePushPattern) {
            abstractMethod(
                writer,
                "pushPattern",
                "(Lappeng/api/networking/crafting/ICraftingPatternDetails;"
                    + "Lnet/minecraft/inventory/InventoryCrafting;)Z");
        }
        abstractMethod(writer, "onPatternChange", "(ILnet/minecraft/item/ItemStack;)V");
        abstractMethod(writer, "loadNBTData", "(Lnet/minecraft/nbt/NBTTagCompound;)V");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void abstractMethod(ClassWriter writer, String name, String descriptor) {
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, descriptor, null, null)
            .visitEnd();
    }
}
