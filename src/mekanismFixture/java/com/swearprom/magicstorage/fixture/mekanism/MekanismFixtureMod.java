package com.swearprom.magicstorage.fixture.mekanism;

import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@Mod(MekanismFixtureMod.MODID)
public final class MekanismFixtureMod {
    public static final String MODID = "magic_storage_mekanism_fixture";
    private static final BlockCapability<IChemicalHandler, Direction> CHEMICAL_CAPABILITY =
            BlockCapability.createSided(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"),
                    IChemicalHandler.class);

    public MekanismFixtureMod(IEventBus modEventBus) {
        modEventBus.addListener(MekanismFixtureMod::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                CHEMICAL_CAPABILITY,
                (level, pos, state, blockEntity, side) -> side != null
                        && side == state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                        ? FixtureChemicalBlockStorage.handler(level, pos)
                        : null,
                Blocks.WHITE_GLAZED_TERRACOTTA);
    }
}
