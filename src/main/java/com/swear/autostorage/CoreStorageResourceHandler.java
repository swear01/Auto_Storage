package com.swear.autostorage;

import java.util.List;

final class CoreStorageResourceHandler implements StorageResourceHandler {
    private final StorageCoreBlockEntity core;

    CoreStorageResourceHandler(StorageCoreBlockEntity core) {
        this.core = core;
    }

    @Override
    public List<StorageResourceKey> getStoredResources() {
        return core.getResourceKeys();
    }

    @Override
    public long getAmount(StorageResourceKey key) {
        return core.getResourceAmount(key);
    }

    @Override
    public long insert(StorageResourceKey key, long amount, boolean simulate) {
        return core.insertResource(key, amount, simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public long extract(StorageResourceKey key, long amount, boolean simulate) {
        return core.extractResource(key, amount, simulate ? Action.SIMULATE : Action.EXECUTE);
    }
}
