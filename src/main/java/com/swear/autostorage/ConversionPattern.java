package com.swear.autostorage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Runtime item→stored-resource conversion pattern.
 *
 * <p>A pattern reads its values live from the server's recipe manager,
 * datamaps, or config at resolve time (or from a revision-tracked cache
 * rebuilt on datapack reload). Auto Storage never hardcodes conversion
 * values; bundled modules register patterns through
 * {@link ConversionScanner} and the user-owned pattern list.</p>
 */
public interface ConversionPattern {

    /**
     * @return stable pattern id, namespaced by the owner
     */
    ResourceLocation patternId();

    /**
     * Side-effect-free exact-input resolution. Returning {@code null}
     * means this pattern does not accept the supplied item.
     */
    TransformProviderApi.Result resolve(ItemStack input);

    /**
     * Digest of everything the pattern reads (recipe holder ids and
     * payload hashes, config values, datamap digests). When it changes,
     * shared Transform results that depend on this pattern are
     * invalidated.
     */
    String revisionKey();
}
