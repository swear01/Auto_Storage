package com.swear.autostorage;

public interface IStorageNetworkBlock {
    default boolean isStorageCore() {
        return false;
    }
}
