package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.network.OpenRouteConfigPayload;
import com.benjamintaskis.createsignalextensions.network.SaveDestinationRulePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class DestinationRuleSmallTheatreMappingScreen extends Screen {
    private OpenRouteConfigPayload payload;
    private final List<OpenRouteConfigPayload.DestinationRule> rules = new ArrayList<>();
    private final EditBox[] cells = new EditBox[6];
    private int ruleIndex;
    private int panelLeft, panelTop;
    private int visibleRows = 1;

    public DestinationRuleSmallTheatreMappingScreen(OpenRouteConfigPayload payload, int ruleIndex) {
        super(Component.literal("Destination Rule Small Theatre"));
        this.payload = payload;
        rules.addAll(payload.destinationRules());
        this.ruleIndex = rules.isEmpty() ? 0 : Math.max(0, Math.min(ruleIndex, rules.size() - 1));
    }

    public boolean isFor(BlockPos pos) { return payload.pos().equals(pos); }

    public void applyUpdate(OpenRouteConfigPayload update) {
        if (!isFor(update.pos())) return;
        String pattern = rules.isEmpty() ? "" : rules.get(ruleIndex).pattern();
        payload = update;
        rules.clear();
        rules.addAll(update.destinationRules());
        if (!rules.isEmpty()) {
            int found = -1;
            for (int i = 0; i < rules.size(); i++)
                if (rules.get(i).pattern().equals(pattern)) { found = i; break; }
            ruleIndex = found >= 0 ? found : Math.min(ruleIndex, rules.size() - 1);
        } else ruleIndex = 0;
    }

    @Override
    protected void init() {
        panelLeft = (width - 300) / 2;
        panelTop = Math.max(8, (height - 260) / 2);
        for (int i = 0; i < 6; i++) {
            int col = i % 2, row = i / 2;
            EditBox box = new EditBox(font, panelLeft + 76 + col * 90, panelTop + 118 + row * 34, 58, 20,
                    Component.literal("Cell " + (i + 1)));
            box.setMaxLength(2);
            box.setValue(valueFor(i));
            box.setBordered(false);
            box.setTextColor(0xFFF0F0F0);
            box.setFilter(v -> v.length() <= 2 && v.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '/' || c == '\\'));
            addRenderableWidget(box);
            cells[i] = box;
        }
    }

    private String valueFor(int cell) {
        if (rules.isEmpty()) return "";
        List<String> values = rules.get(ruleIndex).smallTheatreIndications();
        return values != null && cell < values.size() ? values.get(cell) : "";
    }

    private void loadCells() {
        for (int i = 0; i < 6; i++) cells[i].setValue(valueFor(i));
    }

    private void changeRule(int amount) {
        if (rules.isEmpty()) return;
        ruleIndex = Math.floorMod(ruleIndex + amount, rules.size());
        loadCells();
    }

    private void save() {
        if (rules.isEmpty()) return;
        List<String> values = new ArrayList<>(6);
        for (EditBox box : cells) values.add(box.getValue().trim().toUpperCase());
        OpenRouteConfigPayload.DestinationRule old = rules.get(ruleIndex);
        OpenRouteConfigPayload.DestinationRule updated = new OpenRouteConfigPayload.DestinationRule(old.pattern(), old.theatreIndication(), List.copyOf(values));
        rules.set(ruleIndex, updated);
        PacketDistributor.sendToServer(new SaveDestinationRulePayload(payload.pos(), old.pattern(), old.pattern(), old.theatreIndication(), values, false));
        payload = new OpenRouteConfigPayload(payload.pos(), payload.signal(), payload.aspect(), payload.destination(), payload.currentRouteKey(),
                payload.routes(), List.copyOf(rules), payload.automaticRouteRange(), payload.allowNewRoutes(), payload.refreshResponse(), payload.sessionId());
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            for (int r = 1; r <= 3; r++) {
                int bx = panelLeft + 98 + (r - 1) * 38;
                if (inside(x, y, bx, panelTop + 84, 34, 18)) { visibleRows = r; return true; }
            }
            if (inside(x, y, panelLeft + 22, panelTop + 40, 32, 22)) { changeRule(-1); return true; }
            if (inside(x, y, panelLeft + 246, panelTop + 40, 32, 22)) { changeRule(1); return true; }
            if (inside(x, y, panelLeft + 66, panelTop + 224, 72, 22)) { save(); return true; }
            if (inside(x, y, panelLeft + 162, panelTop + 224, 72, 22)) {
                minecraft.setScreen(new RouteInterfaceScreen(payload));
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    private static boolean inside(double x, double y, int bx, int by, int w, int h) {
        return x >= bx && x < bx + w && y >= by && y < by + h;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x78000000);
        int r = panelLeft + 300, b = panelTop + 260;
        g.fill(panelLeft, panelTop, r, b, 0xF0181818);
        g.fill(panelLeft + 1, panelTop + 1, r - 1, panelTop + 28, 0xFF292925);
        g.drawCenteredString(font, title, width / 2, panelTop + 10, 0xFFF3F0E8);
        String rule = rules.isEmpty() ? "No destination rules" :
                ("Rule " + (ruleIndex + 1) + " of " + rules.size() + "  " + rules.get(ruleIndex).pattern());
        g.drawCenteredString(font, rule, width / 2, panelTop + 46, 0xFFE8E8E8);
        control(g, panelLeft + 22, panelTop + 40, 32, 22, "<", !rules.isEmpty(), inside(mx, my, panelLeft + 22, panelTop + 40, 32, 22));
        control(g, panelLeft + 246, panelTop + 40, 32, 22, ">", !rules.isEmpty(), inside(mx, my, panelLeft + 246, panelTop + 40, 32, 22));
        g.drawCenteredString(font, "DISPLAY LAYOUT", width / 2, panelTop + 70, 0xFFAAA58F);
        for (int rr = 1; rr <= 3; rr++) {
            int bx = panelLeft + 98 + (rr - 1) * 38;
            control(g, bx, panelTop + 84, 34, 18, "2x" + rr, true, visibleRows == rr);
        }
        for (int i = 0; i < 6; i++) {
            int col = i % 2, row = i / 2;
            boolean active = row < visibleRows;
            cells[i].setVisible(active);
            cells[i].setEditable(active);
            if (active) {
                int x = panelLeft + 76 + col * 90, y = panelTop + 118 + row * 34;
                g.drawString(font, "Cell " + (i + 1), x, y - 10, 0xFFAAA58F);
                g.fill(x - 2, y - 2, x + 60, y + 20, 0xFF050505);
                g.fill(x - 2, y - 2, x + 60, y - 1, 0xFF8A877F);
                g.fill(x - 2, y + 19, x + 60, y + 20, 0xFF55524D);
                g.fill(x - 2, y - 2, x - 1, y + 20, 0xFF77736B);
                g.fill(x + 59, y - 2, x + 60, y + 20, 0xFF55524D);
            }
        }
        control(g, panelLeft + 66, panelTop + 224, 72, 22, "SAVE", !rules.isEmpty(), inside(mx, my, panelLeft + 66, panelTop + 224, 72, 22));
        control(g, panelLeft + 162, panelTop + 224, 72, 22, "BACK", true, inside(mx, my, panelLeft + 162, panelTop + 224, 72, 22));
        for (EditBox box : cells) if (box.visible) box.render(g, mx, my, pt);
    }

    private void control(GuiGraphics g, int x, int y, int w, int h, String text, boolean enabled, boolean hover) {
        int fill = !enabled ? 0xFF252525 : hover ? 0xFF58544C : 0xFF3B3934;
        g.fill(x, y, x + w, y + h, fill);
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, enabled ? 0xFFF0EEE8 : 0xFF777777);
    }

    @Override public boolean isPauseScreen() { return false; }
}
