package com.swear.autostorage;

import java.util.List;

public interface StorageResourceHandler {
    List<StorageResourceKey> getStoredResources();

    long getAmount(StorageResourceKey key);

    long insert(StorageResourceKey key, long amount, boolean simulate);

    long extract(StorageResourceKey key, long amount, boolean simulate);
}
