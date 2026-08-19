package com.swear.autostorage.fixture.productivemetalworks;

import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.MachineEnergyTable;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(ProductivemetalworksFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class ProductivemetalworksPersistenceMigrationGameTests {
    private static final ResourceLocation LEGACY_CASTING_DESCRIPTOR =
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "productivemetalworks_casting_table");
    private static final ResourceLocation BASIN_DESCRIPTOR =
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "productivemetalworks_casting_basin");
    private static final ResourceLocation BASIN_ITEM =
            ResourceLocation.fromNamespaceAndPath(
                    "productivemetalworks", "casting_basin");

    private ProductivemetalworksPersistenceMigrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void legacy_basin_machine_and_work_are_migrated(
            GameTestHelper helper
    ) {
        try {
            HolderLookup.Provider registries = helper.getLevel().registryAccess();
            Class<?> recordClass = Class.forName(
                    "com.swear.autostorage.CoreStorageRecord");
            Object source = invokeStatic(recordClass, "fresh", UUID.randomUUID());
            CompoundTag encoded = (CompoundTag) invoke(
                    recordClass, source, "save", registries);

            ListTag machineEntries = encoded.getList("machineDescriptors", Tag.TAG_COMPOUND);
            CompoundTag machine = new CompoundTag();
            machine.putString("descriptorId", LEGACY_CASTING_DESCRIPTOR.toString());
            machine.put("item", basinStack().save(registries));
            machine.putLong("count", 2);
            machineEntries.add(machine);

            ListTag workEntries = encoded.getList("machineWork", Tag.TAG_COMPOUND);
            CompoundTag work = new CompoundTag();
            work.putString("descriptorId", LEGACY_CASTING_DESCRIPTOR.toString());
            work.putLong("amount", 17);
            workEntries.add(work);

            CompoundTag remainderWork = new CompoundTag();
            remainderWork.putString("descriptorId", LEGACY_CASTING_DESCRIPTOR.toString());
            remainderWork.putLong("amount", 0);
            remainderWork.putString("variantItemId", BASIN_ITEM.toString());
            remainderWork.putLong("rateNumerator", 1);
            remainderWork.putLong("rateDenominator", 1);
            remainderWork.putLong("remainder", 0);
            workEntries.add(remainderWork);

            Object result = invokeStatic(recordClass, "load", encoded, registries);
            if (!(Boolean) invoke(result.getClass(), result, "success")) {
                helper.fail("Legacy Productive Metalworks record did not load: "
                        + invoke(result.getClass(), result, "error"));
                return;
            }
            Object restored = invoke(result.getClass(), result, "record");
            SimpleContainer machines = (SimpleContainer) invoke(
                    recordClass, restored, "machines");
            Object unresolvedMachines = invoke(
                    recordClass, restored, "unresolvedMachineEntries");
            Object ledger = invoke(recordClass, restored, "resourceLedger");
            Class<?> bridgeClass = Class.forName(
                    "com.swear.autostorage.StorageResourceBridge");
            Class<?> ledgerClass = Class.forName(
                    "com.swear.autostorage.StorageResourceLedger");
            Method stationWorkKey = bridgeClass.getDeclaredMethod(
                    "stationWorkKey", ResourceLocation.class);
            stationWorkKey.setAccessible(true);
            Method amount = ledgerClass.getDeclaredMethod(
                    "amount", Class.forName("com.swear.autostorage.StorageResourceKey"));
            amount.setAccessible(true);
            long basinWork = (Long) amount.invoke(
                    ledger, stationWorkKey.invoke(null, BASIN_DESCRIPTOR));
            long legacyWork = (Long) amount.invoke(
                    ledger, stationWorkKey.invoke(null, LEGACY_CASTING_DESCRIPTOR));
            Map<?, ?> remainders = (Map<?, ?>) invoke(
                    recordClass, restored, "machineWorkRemainders");
            int basinSlot = MachineEnergyTable.findSlot(BASIN_DESCRIPTOR);
            if (basinSlot < 0
                    || machines.getItem(basinSlot).getCount() != 2
                    || !((java.util.List<?>) unresolvedMachines).isEmpty()
                    || basinWork != 17
                    || legacyWork != 0
                    || !remainders.containsKey(BASIN_DESCRIPTOR)) {
                helper.fail("Legacy basin state was not remapped: basinSlot=" + basinSlot
                        + " machines=" + ((java.util.List<?>) unresolvedMachines).size()
                        + " basinWork=" + basinWork + " legacyWork=" + legacyWork
                        + " remainders=" + remainders.keySet());
                return;
            }

            CompoundTag migrated = (CompoundTag) invoke(
                    recordClass, restored, "save", registries);
            ListTag savedMachines = migrated.getList("machineDescriptors", Tag.TAG_COMPOUND);
            ListTag savedWork = migrated.getList("machineWork", Tag.TAG_COMPOUND);
            if (savedMachines.size() != 1
                    || !BASIN_DESCRIPTOR.toString().equals(
                            savedMachines.getCompound(0).getString("descriptorId"))
                    || savedWork.size() != 1
                    || !BASIN_DESCRIPTOR.toString().equals(
                            savedWork.getCompound(0).getString("descriptorId"))) {
                helper.fail("Migrated basin state did not save under the new descriptor");
                return;
            }
            helper.succeed();
        } catch (ReflectiveOperationException exception) {
            helper.fail("Could not inspect persisted Productive Metalworks state: "
                    + exception);
        }
    }

    private static ItemStack basinStack() {
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(BASIN_ITEM);
        if (item == net.minecraft.world.item.Items.AIR) {
            throw new IllegalStateException("Missing Productive Metalworks casting basin");
        }
        return new ItemStack(item);
    }

    private static Object invokeStatic(
            Class<?> owner,
            String name,
            Object... arguments
    ) throws ReflectiveOperationException {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == arguments.length) {
                method.setAccessible(true);
                return method.invoke(null, arguments);
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name);
    }

    private static Object invoke(
            Class<?> owner,
            Object target,
            String name,
            Object... arguments
    ) throws ReflectiveOperationException {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == arguments.length) {
                method.setAccessible(true);
                return method.invoke(target, arguments);
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name);
    }
}
