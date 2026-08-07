package com.swear.autostorage.compat;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.runtime.IJeiKeyMapping;
import net.minecraft.client.KeyMapping;

import java.util.Objects;

final class JeiUserInput implements IJeiUserInput {
    private final InputConstants.Key key;
    private final int modifiers;
    private final boolean simulate;

    private JeiUserInput(InputConstants.Key key, int modifiers, boolean simulate) {
        this.key = Objects.requireNonNull(key, "key");
        this.modifiers = modifiers;
        this.simulate = simulate;
    }

    static JeiUserInput mouse(int button, boolean simulate) {
        return new JeiUserInput(InputConstants.Type.MOUSE.getOrCreate(button), 0, simulate);
    }

    static JeiUserInput key(int keyCode, int scanCode, int modifiers) {
        return new JeiUserInput(InputConstants.getKey(keyCode, scanCode), modifiers, false);
    }

    @Override
    public InputConstants.Key getKey() {
        return key;
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    @Override
    public boolean isSimulate() {
        return simulate;
    }

    @Override
    public boolean is(KeyMapping keyMapping) {
        return keyMapping.isActiveAndMatches(key);
    }

    @Override
    public boolean is(IJeiKeyMapping keyMapping) {
        return keyMapping.isActiveAndMatches(key);
    }
}
