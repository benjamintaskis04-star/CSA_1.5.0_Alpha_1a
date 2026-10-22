package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.content.signal.SignalPlateBlockEntity;
import com.benjamintaskis.createsignalextensions.network.SetSignalPlateTextPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Direct, no-screen Signal Plate text editor. Keyboard events are consumed by KeyboardHandlerMixin. */
public final class SignalPlateDirectEditor {
    private static BlockPos pos;
    private static String text = "";
    private static final int MAX_LENGTH = 16;

    private SignalPlateDirectEditor() {}

    public static void begin(BlockPos p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (pos != null && !pos.equals(p)) finish();
        if (mc.level.getBlockEntity(p) instanceof SignalPlateBlockEntity be) {
            pos = p;
            text = be.text() == null ? "" : be.text();
            // Prevent a movement/use key that was already held as editing began from remaining latched.
            KeyMapping.releaseAll();
        }
    }

    public static boolean active() { return pos != null; }

    /** @return true when the key event belongs to the plate editor and must not reach Minecraft keybinds. */
    public static boolean keyPressed(int key, int action, int modifiers) {
        if (!active()) return false;
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return true;

        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            finish();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            if (!text.isEmpty()) {
                int cut = text.offsetByCodePoints(text.length(), -1);
                text = text.substring(0, cut);
                preview();
            }
            return true;
        }
        return true; // consume all other keybinds; printable characters arrive through charTyped.
    }

    /** @return true when the character event was consumed by the editor. */
    public static boolean charTyped(int codePoint, int modifiers) {
        if (!active()) return false;
        if (!Character.isValidCodePoint(codePoint) || Character.isISOControl(codePoint)) return true;
        if (text.codePointCount(0, text.length()) >= MAX_LENGTH) return true;

        String chars = new String(Character.toChars(codePoint)).toUpperCase(java.util.Locale.ROOT);
        text += chars;
        if (text.codePointCount(0, text.length()) > MAX_LENGTH) {
            int end = text.offsetByCodePoints(0, MAX_LENGTH);
            text = text.substring(0, end);
        }
        preview();
        return true;
    }

    private static void preview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && pos != null && mc.level.getBlockEntity(pos) instanceof SignalPlateBlockEntity be)
            be.setText(text);
    }

    private static void finish() {
        if (pos == null) return;
        PacketDistributor.sendToServer(new SetSignalPlateTextPayload(pos, text));
        pos = null;
        KeyMapping.releaseAll();
    }
}
