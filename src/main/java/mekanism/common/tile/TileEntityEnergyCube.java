package mekanism.common.tile;

import javax.annotation.Nonnull;
import mekanism.api.IConfigCardAccess;
import mekanism.api.NBTConstants;
import mekanism.api.RelativeSide;
import mekanism.api.providers.IBlockProvider;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.ITileComponent;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.energy.EnergyCubeEnergyContainer;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.capabilities.resolver.basic.BasicCapabilityResolver;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.tier.EnergyCubeTier;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.EnergySlotInfo;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.upgrade.EnergyCubeUpgradeData;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.util.CableUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.NBTUtils;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;

public class TileEntityEnergyCube extends TileEntityMekanism implements ISideConfiguration, IConfigCardAccess {

    /**
     * This Energy Cube's tier.
     */
    private EnergyCubeTier tier;
    private float prevScale;
    public TileComponentEjector ejectorComponent;
    public TileComponentConfig configComponent;

    private EnergyCubeEnergyContainer energyContainer;
    private EnergyInventorySlot chargeSlot;
    private EnergyInventorySlot dischargeSlot;

    /**
     * A block used to store and transfer electricity.
     */
    public TileEntityEnergyCube(IBlockProvider blockProvider) {
        super(blockProvider);

        configComponent = new TileComponentConfig(this, TransmissionType.ENERGY, TransmissionType.ITEM);

        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null) {
            //Note: we allow inputting and outputting in both directions as the slot is a processing slot
            itemConfig.addSlotInfo(DataType.INPUT, new InventorySlotInfo(true, true, chargeSlot));
            itemConfig.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(true, true, dischargeSlot));
            //Set default config directions
            itemConfig.setDataType(RelativeSide.LEFT, DataType.INPUT);
            itemConfig.setDataType(RelativeSide.RIGHT, DataType.OUTPUT);

            itemConfig.setCanEject(false);
        }

        ConfigInfo energyConfig = configComponent.getConfig(TransmissionType.ENERGY);
        if (energyConfig != null) {
            energyConfig.addSlotInfo(DataType.INPUT, new EnergySlotInfo(true, false, energyContainer));
            energyConfig.addSlotInfo(DataType.OUTPUT, new EnergySlotInfo(false, true, energyContainer));
            //Set default config directions
            energyConfig.fill(DataType.INPUT);
            energyConfig.setDataType(RelativeSide.FRONT, DataType.OUTPUT);
            energyConfig.setEjecting(true);
        }

        ejectorComponent = new TileComponentEjector(this);
        addCapabilityResolver(BasicCapabilityResolver.constant(Capabilities.CONFIG_CARD_CAPABILITY, this));
    }

    @Override
    protected void presetVariables() {
        tier = Attribute.getTier(getBlockType(), EnergyCubeTier.class);
    }

    @Nonnull
    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers() {
        EnergyContainerHelper builder = EnergyContainerHelper.forSideWithConfig(this::getDirection, this::getConfig);
        builder.addContainer(energyContainer = EnergyCubeEnergyContainer.create(tier, this));
        return builder.build();
    }

    @Nonnull
    @Override
    protected IInventorySlotHolder getInitialInventory() {
        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this::getDirection, this::getConfig);
        builder.addSlot(dischargeSlot = EnergyInventorySlot.fillOrConvert(energyContainer, this::getWorld, this, 17, 35));
        builder.addSlot(chargeSlot = EnergyInventorySlot.drain(energyContainer, this, 143, 35));
        dischargeSlot.setSlotOverlay(SlotOverlay.MINUS);
        chargeSlot.setSlotOverlay(SlotOverlay.PLUS);
        return builder.build();
    }

    public EnergyCubeTier getTier() {
        return tier;
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        chargeSlot.drainContainer();
        dischargeSlot.fillContainerOrConvert();
        if (!energyContainer.isEmpty() && MekanismUtils.canFunction(this)) {
            ConfigInfo info = configComponent.getConfig(TransmissionType.ENERGY);
            if (info != null && info.isEjecting()) {
                CableUtils.emit(info.getSidesForData(DataType.OUTPUT), energyContainer, this, tier.getOutput());
            }
        }
        float newScale = MekanismUtils.getScale(prevScale, energyContainer);
        if (newScale != prevScale) {
            prevScale = newScale;
            sendUpdatePacket();
        }
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(energyContainer.getEnergy(), energyContainer.getMaxEnergy());
    }

    @Override
    public TileComponentEjector getEjector() {
        return ejectorComponent;
    }

    @Override
    public TileComponentConfig getConfig() {
        return configComponent;
    }

    @Override
    public Direction getOrientation() {
        return getDirection();
    }

    @Override
    public void parseUpgradeData(@Nonnull IUpgradeData upgradeData) {
        if (upgradeData instanceof EnergyCubeUpgradeData) {
            EnergyCubeUpgradeData data = (EnergyCubeUpgradeData) upgradeData;
            redstone = data.redstone;
            setControlType(data.controlType);
            getEnergyContainer().setEnergy(data.energyContainer.getEnergy());
            chargeSlot.setStack(data.chargeSlot.getStack());
            dischargeSlot.setStack(data.dischargeSlot.getStack());
            for (ITileComponent component : getComponents()) {
                component.read(data.components);
            }
        } else {
            super.parseUpgradeData(upgradeData);
        }
    }

    public EnergyCubeEnergyContainer getEnergyContainer() {
        return energyContainer;
    }

    @Nonnull
    @Override
    public EnergyCubeUpgradeData getUpgradeData() {
        return new EnergyCubeUpgradeData(redstone, getControlType(), getEnergyContainer(), chargeSlot, dischargeSlot, getComponents());
    }

    public float getEnergyScale() {
        return prevScale;
    }

    @Nonnull
    @Override
    public CompoundNBT getReducedUpdateTag() {
        CompoundNBT updateTag = super.getReducedUpdateTag();
        updateTag.putFloat(NBTConstants.SCALE, prevScale);
        return updateTag;
    }

    @Override
    public void handleUpdateTag(@Nonnull CompoundNBT tag) {
        super.handleUpdateTag(tag);
        NBTUtils.setFloatIfPresent(tag, NBTConstants.SCALE, scale -> prevScale = scale);
    }
}