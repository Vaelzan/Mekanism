package mekanism.common.item.block;

import java.util.List;
import javax.annotation.Nonnull;
import mekanism.api.EnumColor;
import mekanism.client.MekKeyHandler;
import mekanism.client.MekanismKeyHandler;
import mekanism.common.util.TextComponentUtil;
import mekanism.common.util.TextComponentUtil.Translation;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

//TODO: Should this just be merged with ItemBlockTooltip somehow, or maybe not extend it at all
public abstract class ItemBlockAdvancedTooltip<BLOCK extends Block> extends ItemBlockTooltip<BLOCK> {

    public ItemBlockAdvancedTooltip(BLOCK block) {
        this(block, new Item.Properties());
    }

    public ItemBlockAdvancedTooltip(BLOCK block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addInformation(@Nonnull ItemStack itemstack, World world, @Nonnull List<ITextComponent> tooltip, @Nonnull ITooltipFlag flag) {
        if (!MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.sneakKey)) {
            addStats(itemstack, world, tooltip, flag);
            tooltip.add(TextComponentUtil.build(Translation.of("mekanism.tooltip.hold"), " ", EnumColor.INDIGO, MekanismKeyHandler.sneakKey.getKey(),
                  EnumColor.GREY, " ", Translation.of("mekanism.tooltip.for_details"), "."));
            tooltip.add(TextComponentUtil.build(Translation.of("mekanism.tooltip.hold"), " ", EnumColor.AQUA, MekanismKeyHandler.sneakKey.getKey(),
                  EnumColor.GREY, " ", Translation.of("mekanism.tooltip.and"), " ", EnumColor.AQUA, MekanismKeyHandler.modeSwitchKey.getKey(), EnumColor.GREY, " ",
                  Translation.of("tooltip.forDesc"), "."));
        } else if (!MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.modeSwitchKey)) {
            addDetails(itemstack, world, tooltip, flag);
        } else {
            addDescription(itemstack, world, tooltip, flag);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public abstract void addDetails(@Nonnull ItemStack itemstack, World world, @Nonnull List<ITextComponent> tooltip, @Nonnull ITooltipFlag flag);
}