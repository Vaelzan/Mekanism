package mekanism.common.content.boiler;

import java.util.Set;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.content.blocktype.BlockTypeTile;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.multiblock.UpdateProtocol;
import mekanism.common.registries.MekanismBlockTypes;
import mekanism.common.tile.TileEntityBoilerCasing;
import mekanism.common.tile.TileEntityPressureDisperser;
import mekanism.common.tile.TileEntitySuperheatingElement;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

public class BoilerUpdateProtocol extends UpdateProtocol<BoilerMultiblockData> {

    public BoilerUpdateProtocol(TileEntityBoilerCasing tile) {
        super(tile);
    }

    @Override
    protected CasingType getCasingType(BlockPos pos) {
        Block block = pointer.getWorld().getBlockState(pos).getBlock();
        if (BlockTypeTile.is(block, MekanismBlockTypes.BOILER_CASING)) {
            return CasingType.FRAME;
        } else if (BlockTypeTile.is(block, MekanismBlockTypes.BOILER_VALVE)) {
            return CasingType.VALVE;
        }
        return CasingType.INVALID;
    }

    @Override
    protected boolean isValidInnerNode(BlockPos pos) {
        if (super.isValidInnerNode(pos)) {
            return true;
        }
        TileEntity tile = MekanismUtils.getTileEntity(pointer.getWorld(), pos);
        return tile instanceof TileEntityPressureDisperser || tile instanceof TileEntitySuperheatingElement;
    }

    @Override
    protected FormationResult validate(BoilerMultiblockData structure, Set<BlockPos> innerNodes) {
        Set<BlockPos> dispersers = new ObjectOpenHashSet<>();
        Set<BlockPos> elements = new ObjectOpenHashSet<>();
        for (BlockPos pos : innerNodes) {
            TileEntity tile = MekanismUtils.getTileEntity(pointer.getWorld(), pos);
            if (tile instanceof TileEntityPressureDisperser) {
                dispersers.add(pos);
            } else if (tile instanceof TileEntitySuperheatingElement) {
                structure.internalLocations.add(pos);
                elements.add(pos);
            }
        }
        //Ensure at least one disperser exists
        if (dispersers.isEmpty()) {
            return FormationResult.fail(MekanismLang.BOILER_INVALID_NO_DISPERSER);
        }

        //Find a single disperser contained within this multiblock
        final BlockPos initDisperser = dispersers.iterator().next();

        //Ensure that a full horizontal plane of dispersers exist, surrounding the found disperser
        BlockPos pos = new BlockPos(structure.renderLocation.getX(), initDisperser.getY(), structure.renderLocation.getZ());
        for (int x = 1; x < structure.length - 1; x++) {
            for (int z = 1; z < structure.height - 1; z++) {
                BlockPos shifted = pos.add(x, 0, z);
                TileEntityPressureDisperser tile = MekanismUtils.getTileEntity(TileEntityPressureDisperser.class, pointer.getWorld(), shifted);
                if (tile == null) {
                    return FormationResult.fail(MekanismLang.BOILER_INVALID_MISSING_DISPERSER, shifted);
                }
                dispersers.remove(shifted);
            }
        }

        //If there are more dispersers than those on the plane found, the structure is invalid
        if (!dispersers.isEmpty()) {
            return FormationResult.fail(MekanismLang.BOILER_INVALID_EXTRA_DISPERSER);
        }

        if (!elements.isEmpty()) {
            structure.superheatingElements = new NodeCounter(coord -> {
                return coord.getY() < initDisperser.getY() && MekanismUtils.getTileEntity(TileEntitySuperheatingElement.class, pointer.getWorld(), coord) != null;
            }).calculate(elements.iterator().next());
        }

        if (elements.size() > structure.superheatingElements) {
            return FormationResult.fail(MekanismLang.BOILER_INVALID_SUPERHEATING);
        }

        BlockPos initAir = null;
        int totalAir = 0;

        //Find the first available block in the structure for water storage (including casings)
        for (int x = structure.renderLocation.getX(); x < structure.renderLocation.getX() + structure.length; x++) {
            for (int y = structure.renderLocation.getY(); y < initDisperser.getY(); y++) {
                for (int z = structure.renderLocation.getZ(); z < structure.renderLocation.getZ() + structure.height; z++) {
                    BlockPos airPos = new BlockPos(x, y, z);
                    if (pointer.getWorld().isAirBlock(airPos) || checkNode(airPos)) {
                        initAir = airPos;
                        totalAir++;
                    }
                }
            }
        }

        //Gradle build requires these fields to be final
        final BlockPos renderLocation = structure.renderLocation;
        final int volLength = structure.length;
        final int volWidth = structure.height;
        structure.setWaterVolume(new NodeCounter(coord -> {
            int x = coord.getX();
            int y = coord.getY();
            int z = coord.getZ();
            return y >= renderLocation.getY() - 1 && y < initDisperser.getY() &&
                   x >= renderLocation.getX() && x < renderLocation.getX() + volLength &&
                   z >= renderLocation.getZ() && z < renderLocation.getZ() + volWidth &&
                   (pointer.getWorld().isAirBlock(coord) || checkNode(coord));
        }).calculate(initAir));

        //Make sure all air blocks are connected
        if (totalAir > structure.getWaterVolume()) {
            return FormationResult.fail(MekanismLang.BOILER_INVALID_AIR_POCKETS);
        }

        int steamHeight = (structure.renderLocation.getY() + structure.width - 2) - initDisperser.getY();
        structure.setSteamVolume(structure.height * structure.length * steamHeight);
        structure.upperRenderLocation = new Coord4D(structure.renderLocation.getX(), initDisperser.getY() + 1, structure.renderLocation.getZ(), pointer.getWorld().getDimension().getType());
        return FormationResult.SUCCESS;
    }

    @Override
    protected MultiblockManager<BoilerMultiblockData> getManager() {
        return Mekanism.boilerManager;
    }
}