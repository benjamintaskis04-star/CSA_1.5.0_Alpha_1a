package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.network.OpenConvergingSignalConfigPayload;
import com.benjamintaskis.createsignalextensions.network.SetConvergingSignalDirectionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class ConvergingSignalConfigScreen extends Screen {
    private final OpenConvergingSignalConfigPayload payload;
    private String direction;
    private int left;
    private int top;

    public ConvergingSignalConfigScreen(OpenConvergingSignalConfigPayload payload) {
        super(Component.literal("Converging Signal"));
        this.payload = payload;
        this.direction = "RIGHT".equalsIgnoreCase(payload.direction()) ? "RIGHT" : "LEFT";
    }

    @Override
    protected void init() {
        left = (width - 280) / 2;
        top = Math.max(10, (height - 150) / 2);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            if (inside(x, y, left + 45, top + 68, 90, 26)) {
                setDirection("LEFT");
                return true;
            }
            if (inside(x, y, left + 145, top + 68, 90, 26)) {
                setDirection("RIGHT");
                return true;
            }
            if (inside(x, y, left + 195, top + 112, 60, 22)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    private void setDirection(String value) {
        direction = value;
        PacketDistributor.sendToServer(new SetConvergingSignalDirectionPayload(payload.pos(), value));
    }

    private static boolean inside(double x, double y, int bx, int by, int w, int h) {
        return x >= bx && x < bx + w && y >= by && y < by + h;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0x78000000);
        g.fill(left, top, left + 280, top + 145, 0xF0181818);
        g.fill(left + 1, top + 1, left + 279, top + 28, 0xFF292925);
        g.drawCenteredString(font, title, width / 2, top + 10, 0xFFF3F0E8);
        g.drawString(font, "Default convergence direction", left + 36, top + 46, 0xFFC8C8C8);
        drawChoice(g, mx, my, left + 45, top + 68, "LEFT", "LEFT".equals(direction));
        drawChoice(g, mx, my, left + 145, top + 68, "RIGHT", "RIGHT".equals(direction));
        g.drawCenteredString(font, "Used whenever this signal shows a convergence indication", width / 2, top + 99, 0xFFAAA58F);
        g.fill(left + 195, top + 112, left + 255, top + 134, 0xFF3B3934);
        g.drawCenteredString(font, "DONE", left + 225, top + 119, 0xFFF0EEE8);
    }

    private void drawChoice(GuiGraphics g, int mx, int my, int x, int y, String text, boolean selected) {
        boolean hovered = inside(mx, my, x, y, 90, 26);
        g.fill(x, y, x + 90, y + 26, selected ? 0xFF666158 : hovered ? 0xFF504D47 : 0xFF383632);
        g.drawCenteredString(font, text, x + 45, y + 9, 0xFFF0EEE8);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
