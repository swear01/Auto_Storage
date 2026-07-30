package com.swear.autostorage.mixin;

import ca.weblite.objc.Client;
import ca.weblite.objc.NSObject;
import ca.weblite.objc.Proxy;
import com.mojang.blaze3d.platform.Window;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeCocoa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Window.class)
abstract class MacOsWindowMixin {
    @Redirect(
            method = "setMode",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwGetWindowMonitor(J)J",
                    remap = false),
            require = 1)
    private long autoStorage$getWindowMonitor(long window) {
        if (!Minecraft.ON_OSX) return GLFW.glfwGetWindowMonitor(window);
        return CocoaWindow.isBorderlessFullscreen(window) ? 1L : GLFW.glfwGetWindowMonitor(window);
    }

    @Redirect(
            method = "setMode",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowMonitor(JJIIIII)V",
                    remap = false),
            require = 2)
    private void autoStorage$setWindowMonitor(
            long window,
            long monitor,
            int x,
            int y,
            int width,
            int height,
            int refreshRate
    ) {
        if (!Minecraft.ON_OSX) {
            GLFW.glfwSetWindowMonitor(window, monitor, x, y, width, height, refreshRate);
            return;
        }
        if (monitor != 0L) {
            CocoaWindow.enterBorderlessFullscreen(window);
            GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowMonitor(window, 0L, x, y, width, height, GLFW.GLFW_DONT_CARE);
            return;
        }
        if (!CocoaWindow.isBorderlessFullscreen(window)) {
            GLFW.glfwSetWindowMonitor(window, monitor, x, y, width, height, refreshRate);
            return;
        }
        CocoaWindow.leaveBorderlessFullscreen(window);
        GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
        GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwSetWindowMonitor(window, 0L, x, y, width, height, GLFW.GLFW_DONT_CARE);
        CocoaWindow.restorePresentationOptions();
    }

    static final class CocoaWindow {
        private static final int NORMAL_WINDOW_LEVEL_KEY = 4;
        private static final int MAIN_MENU_WINDOW_LEVEL_KEY = 8;
        private static final long AUTO_HIDE_DOCK_AND_MENU_BAR = (1L << 0) | (1L << 2);
        private static Long previousPresentationOptions;
        private static long borderlessWindow;

        private CocoaWindow() {
        }

        static void enterBorderlessFullscreen(long glfwWindow) {
            Proxy application = application();
            if (previousPresentationOptions == null) {
                previousPresentationOptions = (Long) application.sendRaw("presentationOptions");
            }
            application.send(
                    "setPresentationOptions:",
                    previousPresentationOptions | AUTO_HIDE_DOCK_AND_MENU_BAR);
            NSObject window = window(glfwWindow);
            window.send("setLevel:", (long) windowLevel(MAIN_MENU_WINDOW_LEVEL_KEY) + 1L);
            window.send("setHasShadow:", false);
            borderlessWindow = glfwWindow;
        }

        static boolean isBorderlessFullscreen(long glfwWindow) {
            return borderlessWindow == glfwWindow;
        }

        static void leaveBorderlessFullscreen(long glfwWindow) {
            NSObject window = window(glfwWindow);
            window.send("setLevel:", (long) windowLevel(NORMAL_WINDOW_LEVEL_KEY));
            window.send("setHasShadow:", true);
            borderlessWindow = 0L;
        }

        static void restorePresentationOptions() {
            if (previousPresentationOptions == null) return;
            application().send("setPresentationOptions:", previousPresentationOptions);
            previousPresentationOptions = null;
        }

        private static Proxy application() {
            return Client.getInstance().sendProxy("NSApplication", "sharedApplication");
        }

        private static NSObject window(long glfwWindow) {
            long cocoaWindow = GLFWNativeCocoa.glfwGetCocoaWindow(glfwWindow);
            if (cocoaWindow == 0L) {
                throw new IllegalStateException("GLFW returned no Cocoa window");
            }
            return new NSObject(new Pointer(cocoaWindow));
        }

        private static int windowLevel(int key) {
            Function function = NativeLibrary.getInstance("CoreGraphics")
                    .getFunction("CGWindowLevelForKey");
            return function.invokeInt(new Object[]{key});
        }
    }
}
