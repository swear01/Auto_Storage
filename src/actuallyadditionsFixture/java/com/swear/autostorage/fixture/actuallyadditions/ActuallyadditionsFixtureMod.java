package com.swear.autostorage.fixture.actuallyadditions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ActuallyadditionsFixtureMod.MODID)
public final class ActuallyadditionsFixtureMod {
    public static final String MODID = "auto_storage_actuallyadditions_fixture";

    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(BuiltInRegistries.FLUID, MODID);
    static final DeferredHolder<Fluid, Fluid> BUCKETLESS_FLUID =
            FLUIDS.register("bucketless_fluid", BucketlessFluid::new);

    public ActuallyadditionsFixtureMod(IEventBus modEventBus) {
        FLUIDS.register(modEventBus);
    }

    private static final class BucketlessFluid extends Fluid {
        @Override
        public Item getBucket() {
            return Items.AIR;
        }

        @Override
        protected boolean canBeReplacedWith(
                FluidState state,
                BlockGetter level,
                BlockPos pos,
                Fluid fluid,
                Direction direction
        ) {
            return false;
        }

        @Override
        protected Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) {
            return Vec3.ZERO;
        }

        @Override
        public int getTickDelay(LevelReader level) {
            return 0;
        }

        @Override
        protected float getExplosionResistance() {
            return 0;
        }

        @Override
        public float getHeight(FluidState state, BlockGetter level, BlockPos pos) {
            return 0;
        }

        @Override
        public float getOwnHeight(FluidState state) {
            return 0;
        }

        @Override
        protected BlockState createLegacyBlock(FluidState state) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
            return Shapes.empty();
        }
    }
}
