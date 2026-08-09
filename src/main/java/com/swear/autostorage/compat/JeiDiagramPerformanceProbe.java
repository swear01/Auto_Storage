package com.swear.autostorage.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

final class JeiDiagramPerformanceProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger(JeiDiagramPerformanceProbe.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SAMPLE_LIMIT = 32;
    private static final int MAX_LEVEL_WAIT_TICKS = 20 * 30;
    private static final AtomicBoolean SCHEDULED = new AtomicBoolean(false);

    private JeiDiagramPerformanceProbe() {
    }

    static void scheduleOnce(IJeiRuntime runtime) {
        if (!Boolean.getBoolean("auto_storage.jeiDiagramBench")) {
            return;
        }
        if (!SCHEDULED.compareAndSet(false, true)) {
            return;
        }
        AtomicInteger waitedTicks = new AtomicInteger();
        Consumer<ClientTickEvent.Post> waiter = new Consumer<>() {
            @Override
            public void accept(ClientTickEvent.Post event) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level != null) {
                    NeoForge.EVENT_BUS.unregister(this);
                    run(runtime);
                    return;
                }
                if (waitedTicks.incrementAndGet() >= MAX_LEVEL_WAIT_TICKS) {
                    NeoForge.EVENT_BUS.unregister(this);
                    SCHEDULED.set(false);
                    LOGGER.warn("JEI diagram probe skipped: client level never became ready");
                    Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
                }
            }
        };
        NeoForge.EVENT_BUS.addListener(waiter);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void run(IJeiRuntime runtime) {
        IRecipeManager manager = runtime.getRecipeManager();
        IFocusGroup focuses = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        IRecipeCategory<RecipeHolder<CraftingRecipe>> category =
                manager.getRecipeCategory(RecipeTypes.CRAFTING);
        List<RecipeHolder<CraftingRecipe>> allSamples = manager.createRecipeLookup(RecipeTypes.CRAFTING)
                .get()
                .toList();
        int sampleCount = Math.min(SAMPLE_LIMIT, allSamples.size());
        List<RecipeHolder<CraftingRecipe>> samples = IntStream.range(0, sampleCount)
                .mapToObj(index -> allSamples.get((int) ((long) index * (allSamples.size() - 1)
                        / Math.max(1, sampleCount - 1))))
                .toList();
        if (samples.isEmpty()) {
            LOGGER.warn("JEI diagram probe skipped: no crafting recipes");
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
            return;
        }

        for (RecipeHolder<CraftingRecipe> holder : samples) {
            manager.createRecipeLayoutDrawable(category, holder, focuses);
            createByLookup(manager, category, focuses, holder);
        }

        List<Long> directNanos = new ArrayList<>(samples.size());
        List<Long> lookupNanos = new ArrayList<>(samples.size());
        List<Long> warmNanos = new ArrayList<>(samples.size());
        int directHits = 0;
        int lookupHits = 0;

        for (RecipeHolder<CraftingRecipe> holder : samples) {
            long directStart = System.nanoTime();
            Optional direct = manager.createRecipeLayoutDrawable(category, holder, focuses);
            directNanos.add(System.nanoTime() - directStart);
            if (direct.isPresent()) {
                directHits++;
            }
        }

        for (RecipeHolder<CraftingRecipe> holder : samples) {
            long lookupStart = System.nanoTime();
            Optional lookup = createByLookup(manager, category, focuses, holder);
            lookupNanos.add(System.nanoTime() - lookupStart);
            if (lookup.isPresent()) {
                lookupHits++;
            }
        }

        for (RecipeHolder<CraftingRecipe> holder : samples) {
            Optional direct = manager.createRecipeLayoutDrawable(category, holder, focuses);
            if (direct.isEmpty()) {
                warmNanos.add(0L);
                continue;
            }
            IRecipeLayoutDrawable warmSource = (IRecipeLayoutDrawable) direct.get();
            long warmStart = System.nanoTime();
            warmSource.tick();
            warmSource.getRect();
            warmNanos.add(System.nanoTime() - warmStart);
        }

        double directP50 = percentileMs(directNanos, 0.50);
        double directP95 = percentileMs(directNanos, 0.95);
        double lookupP50 = percentileMs(lookupNanos, 0.50);
        double lookupP95 = percentileMs(lookupNanos, 0.95);
        JsonObject report = new JsonObject();
        report.addProperty("sample_count", samples.size());
        report.addProperty("direct_holder_hits", directHits);
        report.addProperty("lookup_scan_hits", lookupHits);
        report.addProperty("direct_holder_p50_ms", directP50);
        report.addProperty("direct_holder_p95_ms", directP95);
        report.addProperty("lookup_scan_p50_ms", lookupP50);
        report.addProperty("lookup_scan_p95_ms", lookupP95);
        report.addProperty("warm_tick_p50_us", percentileUs(warmNanos, 0.50));
        report.addProperty("warm_tick_p95_us", percentileUs(warmNanos, 0.95));
        report.addProperty(
                "lookup_over_direct_p95",
                directP95 <= 0.0 ? 0.0 : lookupP95 / Math.max(0.001, directP95));

        Path output = Path.of("build/reports/jei-diagram-bench.json");
        Path projectOutput = Path.of("..", "build", "reports", "jei-diagram-bench.json");
        try {
            Files.createDirectories(output.getParent());
            String json = GSON.toJson(report);
            Files.writeString(output, json);
            Files.createDirectories(projectOutput.getParent());
            Files.writeString(projectOutput, json);
            LOGGER.info("JEI diagram probe wrote {}", projectOutput.toAbsolutePath().normalize());
            LOGGER.info(
                    "JEI diagram probe samples={} direct_p95={}ms lookup_p95={}ms warm_p95={}us lookup/direct_p95={}",
                    samples.size(),
                    report.get("direct_holder_p95_ms"),
                    report.get("lookup_scan_p95_ms"),
                    report.get("warm_tick_p95_us"),
                    report.get("lookup_over_direct_p95"));
            if (Boolean.getBoolean("auto_storage.jeiDiagramBench")) {
                LOGGER.info("JEI diagram probe requesting client exit");
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
            }
        } catch (IOException exception) {
            LOGGER.error("Failed to write JEI diagram probe report", exception);
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Optional createByLookup(
            IRecipeManager manager,
            IRecipeCategory<RecipeHolder<CraftingRecipe>> category,
            IFocusGroup focuses,
            RecipeHolder<CraftingRecipe> holder
    ) {
        return manager.createRecipeLookup(RecipeTypes.CRAFTING)
                .get()
                .filter(candidate -> holder.id().equals(category.getRegistryName(candidate)))
                .findFirst()
                .flatMap(candidate -> manager.createRecipeLayoutDrawable(category, candidate, focuses));
    }

    private static double percentileMs(List<Long> nanos, double percentile) {
        return percentileNanos(nanos, percentile) / 1_000_000.0;
    }

    private static double percentileUs(List<Long> nanos, double percentile) {
        return percentileNanos(nanos, percentile) / 1_000.0;
    }

    private static double percentileNanos(List<Long> nanos, double percentile) {
        if (nanos.isEmpty()) {
            return 0.0;
        }
        List<Long> sorted = nanos.stream().sorted().toList();
        int index = Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1));
        return sorted.get(index);
    }
}
