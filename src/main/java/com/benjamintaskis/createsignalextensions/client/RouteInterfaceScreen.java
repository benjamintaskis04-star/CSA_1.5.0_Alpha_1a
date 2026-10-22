package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.network.OpenRouteConfigPayload;
import com.benjamintaskis.createsignalextensions.network.RequestRouteConfigPayload;
import com.benjamintaskis.createsignalextensions.network.SaveRouteLabelPayload;
import com.benjamintaskis.createsignalextensions.network.SaveDestinationRulePayload;
import com.benjamintaskis.createsignalextensions.network.ClearLoadedRoutesPayload;
import com.benjamintaskis.createsignalextensions.network.DeleteLoadedRoutePayload;
import com.benjamintaskis.createsignalextensions.network.SetAutomaticRouteRangePayload;
import com.benjamintaskis.createsignalextensions.network.SetRouteLearningPayload;
import com.benjamintaskis.createsignalextensions.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class RouteInterfaceScreen extends Screen {
    private OpenRouteConfigPayload payload;
    private final List<OpenRouteConfigPayload.RouteEntry> routes = new ArrayList<>();
    private final List<OpenRouteConfigPayload.DestinationRule> destinationRules = new ArrayList<>();
    private int routeIndex;
    private int refreshTicks;
    private EditBox labelBox;
    private EditBox theatreBox;
    private EditBox destinationPatternBox;
    private EditBox destinationTheatreBox;
    private int destinationRuleIndex;
    private boolean routeControlsActive;
    private int panelLeft;
    private int panelTop;
    private double uiScale = 1.0;
    private int virtualWidth;
    private int virtualHeight;
    private double scrollOffset = 0.0;
    private double maxScroll = 0.0;
    private boolean scrollbarDragging = false;
    private boolean signalInfoExpanded = false;
    private boolean routesExpanded = false;
    private boolean destinationRulesExpanded = false;
    private boolean routeTypeDropdownOpen = false;

    public RouteInterfaceScreen(OpenRouteConfigPayload payload) {
        this(payload, null);
    }

    public RouteInterfaceScreen(OpenRouteConfigPayload payload, String preferredRouteKey) {
        super(Component.translatable("screen.create_signal_extensions.route_interface.title"));
        this.payload = payload;
        this.routes.addAll(payload.routes());
        this.destinationRules.addAll(payload.destinationRules());
        selectCurrentRoute();
        if (preferredRouteKey != null && !preferredRouteKey.isBlank()) {
            int preferred = findRouteIndex(preferredRouteKey);
            if (preferred >= 0)
                routeIndex = preferred;
        }
    }

    public boolean isFor(BlockPos pos) {
        return payload.pos().equals(pos);
    }

    public void applyUpdate(OpenRouteConfigPayload update) {
        if (!isFor(update.pos()))
            return;

        String selectedKey = routes.isEmpty() ? "" : routes.get(routeIndex).key();
        boolean editing = labelBox != null && (labelBox.isFocused() || (theatreBox != null && theatreBox.isFocused()));
        boolean editingRule = destinationPatternBox != null && (destinationPatternBox.isFocused() ||
                (destinationTheatreBox != null && destinationTheatreBox.isFocused()));
        String selectedPattern = destinationRules.isEmpty() ? "" : destinationRules.get(destinationRuleIndex).pattern();

        payload = update;
        routes.clear();
        routes.addAll(update.routes());

        // Do not let the 5-tick server refresh overwrite an in-progress destination-rule draft.
        // In particular, NEW creates a local blank rule before it exists on the server; replacing
        // the list here could turn that draft into an edit of the previous saved rule.
        if (!editingRule) {
            destinationRules.clear();
            destinationRules.addAll(update.destinationRules());
            if (!destinationRules.isEmpty()) {
                int found = -1;
                for (int i = 0; i < destinationRules.size(); i++)
                    if (destinationRules.get(i).pattern().equals(selectedPattern)) { found = i; break; }
                destinationRuleIndex = found >= 0 ? found : Math.min(destinationRuleIndex, destinationRules.size() - 1);
            } else {
                destinationRuleIndex = 0;
            }
        }

        if (!routes.isEmpty()) {
            int desired = findRouteIndex(selectedKey);
            if (desired < 0 && !update.currentRouteKey().isBlank())
                desired = findRouteIndex(update.currentRouteKey());
            routeIndex = desired >= 0 ? desired : Math.min(routeIndex, routes.size() - 1);
        } else {
            routeIndex = 0;
        }

        if (labelBox != null && !editing)
            refreshRouteControls();
        else if (labelBox != null) {
            routeControlsActive = !routes.isEmpty();
            labelBox.setEditable(routeControlsActive);
            if (theatreBox != null) theatreBox.setEditable(routeControlsActive);
        }
        if (destinationPatternBox != null && !editingRule)
            refreshDestinationRuleControls();
    }

    private int findRouteIndex(String key) {
        if (key == null || key.isBlank())
            return -1;
        for (int i = 0; i < routes.size(); i++)
            if (routes.get(i).key().equals(key))
                return i;
        return -1;
    }

    private void selectCurrentRoute() {
        int index = findRouteIndex(payload.currentRouteKey());
        if (index >= 0)
            routeIndex = index;
    }

    private void updateUiScale() {
        int designHeight = panelHeight();
        double minimum = ClientConfig.minimumGuiScale();
        double byWidth = (width * 0.96) / 320.0;
        double byHeight = (height * 0.96) / Math.max(1.0, designHeight);
        double fit = Math.min(1.0, Math.min(byWidth, byHeight));
        if (fit >= minimum)
            uiScale = fit;
        else
            uiScale = Math.max(0.10, Math.min(byWidth, minimum));

        virtualWidth = Math.max(1, (int)Math.floor(width / uiScale));
        virtualHeight = Math.max(1, (int)Math.floor(height / uiScale));
        panelLeft = (virtualWidth - 320) / 2;
        panelTop = 4;

        double visiblePanelHeight = Math.max(1.0, virtualHeight - 8.0);
        maxScroll = Math.max(0.0, designHeight - visiblePanelHeight);
        scrollOffset = Math.max(0.0, Math.min(scrollOffset, maxScroll));
    }

    private double virtualMouseX(double mouseX) { return mouseX / uiScale; }
    private double virtualMouseY(double mouseY) { return mouseY / uiScale + scrollOffset; }

    private void refreshScaleAndPositions() {
        updateUiScale();
        repositionRouteFields();
    }

    @Override
    protected void init() {
        updateUiScale();

        int routeBase = routeContentBase();

        labelBox = new EditBox(font, panelLeft + 36, panelTop + routeBase + 101, 168, 18,
                Component.translatable("screen.create_signal_extensions.route_interface.label"));
        labelBox.setMaxLength(32);
        labelBox.setBordered(false);
        labelBox.setTextColor(0xF0F0F0);
        labelBox.setTextColorUneditable(0x888888);
        addRenderableWidget(labelBox);

        theatreBox = new EditBox(font, panelLeft + 232, panelTop + routeBase + 101, 60, 18,
                Component.literal("Theatre indication"));
        theatreBox.setMaxLength(1);
        theatreBox.setBordered(false);
        theatreBox.setTextColor(0xF0F0F0);
        theatreBox.setTextColorUneditable(0x888888);
        theatreBox.setFilter(value -> value.isEmpty() || (value.length() == 1 && "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789/\\".contains(value)));
        addRenderableWidget(theatreBox);

        int db = destinationContentBase();
        destinationPatternBox = new EditBox(font, panelLeft + 32, panelTop + db + 52, 176, 18, Component.literal("Station pattern"));
        destinationPatternBox.setMaxLength(128);
        destinationPatternBox.setBordered(false);
        addRenderableWidget(destinationPatternBox);
        destinationTheatreBox = new EditBox(font, panelLeft + 232, panelTop + db + 52, 60, 18, Component.literal("Indication"));
        destinationTheatreBox.setMaxLength(1);
        destinationTheatreBox.setBordered(false);
        destinationTheatreBox.setFilter(value -> value.isEmpty() || (value.length() == 1 && "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789/\\".contains(value)));
        addRenderableWidget(destinationTheatreBox);

        refreshRouteControls();
        refreshDestinationRuleControls();
        updateFieldVisibility();
    }

    @Override
    public void tick() {
        super.tick();
        // Match the Route Interface's fast monitoring cadence closely without spamming every tick.
        if (++refreshTicks >= 5) {
            refreshTicks = 0;
            PacketDistributor.sendToServer(new RequestRouteConfigPayload(payload.pos(), payload.sessionId()));
        }
    }

    private void changeRoute(int amount) {
        if (routes.isEmpty())
            return;
        routeIndex = Math.floorMod(routeIndex + amount, routes.size());
        routeTypeDropdownOpen = false;
        refreshRouteControls();
    }

    private void refreshRouteControls() {
        routeControlsActive = !routes.isEmpty();
        labelBox.setEditable(routeControlsActive);
        theatreBox.setEditable(routeControlsActive);
        labelBox.setValue(routeControlsActive ? routes.get(routeIndex).label() : "");
        theatreBox.setValue(routeControlsActive ? routes.get(routeIndex).theatreIndication() : "");
    }

    private void saveCurrent() {
        if (routes.isEmpty())
            return;
        String label = labelBox.getValue().trim();
        String theatre = theatreBox.getValue().trim().toUpperCase();
        OpenRouteConfigPayload.RouteEntry old = routes.get(routeIndex);
        routes.set(routeIndex, new OpenRouteConfigPayload.RouteEntry(old.key(), label, theatre, old.smallTheatreIndications(), old.routeType(), old.routeIndication(), old.visualPath()));
        PacketDistributor.sendToServer(new SaveRouteLabelPayload(payload.pos(), old.key(), label, theatre, old.routeType(), old.routeIndication()));
    }

    private void setCurrentRouteType(String routeType) {
        if (routes.isEmpty())
            return;
        OpenRouteConfigPayload.RouteEntry old = routes.get(routeIndex);
        routes.set(routeIndex, new OpenRouteConfigPayload.RouteEntry(old.key(), old.label(), old.theatreIndication(), old.smallTheatreIndications(), routeType, old.routeIndication(), old.visualPath()));
        PacketDistributor.sendToServer(new SaveRouteLabelPayload(payload.pos(), old.key(), old.label(), old.theatreIndication(), routeType, old.routeIndication()));
        routeTypeDropdownOpen = false;
        updateFieldVisibility();
    }


    private void cycleCurrentRouteIndication() {
        if (routes.isEmpty()) return;
        OpenRouteConfigPayload.RouteEntry old = routes.get(routeIndex);
        String next = switch (old.routeIndication()) { case "SHUNT" -> "DEAD_END"; case "DEAD_END" -> "NORMAL"; default -> "SHUNT"; };
        routes.set(routeIndex, new OpenRouteConfigPayload.RouteEntry(old.key(), old.label(), old.theatreIndication(), old.smallTheatreIndications(), old.routeType(), next, old.visualPath()));
        PacketDistributor.sendToServer(new SaveRouteLabelPayload(payload.pos(), old.key(), old.label(), old.theatreIndication(), old.routeType(), next));
    }

    private void refreshDestinationRuleControls() {
        if (destinationPatternBox == null || destinationTheatreBox == null) return;
        if (destinationRules.isEmpty()) {
            destinationPatternBox.setValue("");
            destinationTheatreBox.setValue("");
        } else {
            OpenRouteConfigPayload.DestinationRule rule = destinationRules.get(destinationRuleIndex);
            destinationPatternBox.setValue(rule.pattern());
            destinationTheatreBox.setValue(rule.theatreIndication());
        }
    }

    private void changeDestinationRule(int amount) {
        if (destinationRules.isEmpty()) return;
        destinationRuleIndex = Math.floorMod(destinationRuleIndex + amount, destinationRules.size());
        refreshDestinationRuleControls();
    }

    private void newDestinationRule() {
        destinationRules.add(new OpenRouteConfigPayload.DestinationRule("", "", List.of("", "", "", "", "", "")));
        destinationRuleIndex = destinationRules.size() - 1;
        refreshDestinationRuleControls();
        destinationPatternBox.setFocused(true);
    }

    private void saveDestinationRule() {
        String pattern = destinationPatternBox.getValue().trim();
        String indication = destinationTheatreBox.getValue().trim().toUpperCase();
        if (pattern.isBlank()) return;
        String oldPattern = destinationRules.isEmpty() ? "" : destinationRules.get(destinationRuleIndex).pattern();
        List<String> small = destinationRules.isEmpty() ? List.of("", "", "", "", "", "") : destinationRules.get(destinationRuleIndex).smallTheatreIndications();
        OpenRouteConfigPayload.DestinationRule updated = new OpenRouteConfigPayload.DestinationRule(pattern, indication, small);
        if (destinationRules.isEmpty()) {
            destinationRules.add(updated);
            destinationRuleIndex = 0;
        } else destinationRules.set(destinationRuleIndex, updated);
        PacketDistributor.sendToServer(new SaveDestinationRulePayload(payload.pos(), oldPattern, pattern, indication, small, false));
    }

    private void openDestinationSmallTheatreMapping() {
        if (destinationRules.isEmpty()) return;
        saveDestinationRule();
        OpenRouteConfigPayload updatedPayload = new OpenRouteConfigPayload(
                payload.pos(), payload.signal(), payload.aspect(), payload.destination(), payload.currentRouteKey(),
                List.copyOf(routes), List.copyOf(destinationRules), payload.automaticRouteRange(), payload.allowNewRoutes(), payload.refreshResponse(), payload.sessionId());
        minecraft.setScreen(new DestinationRuleSmallTheatreMappingScreen(updatedPayload, destinationRuleIndex));
    }

    private void deleteDestinationRule() {
        if (destinationRules.isEmpty()) return;
        String oldPattern = destinationRules.get(destinationRuleIndex).pattern();
        if (!oldPattern.isBlank())
            PacketDistributor.sendToServer(new SaveDestinationRulePayload(payload.pos(), oldPattern, "", "", List.of("", "", "", "", "", ""), true));
        destinationRules.remove(destinationRuleIndex);
        if (destinationRuleIndex >= destinationRules.size()) destinationRuleIndex = Math.max(0, destinationRules.size() - 1);
        refreshDestinationRuleControls();
    }

    private void clearCurrent() {
        if (routes.isEmpty())
            return;
        labelBox.setValue("");
        theatreBox.setValue("");
        saveCurrent();
    }

    private boolean handleScrollbarPress(double rawX, double rawY) {
        if (maxScroll <= 0.5) return false;
        int right = panelLeft + 320;
        int trackX = right - 7;
        int trackTop = panelTop + 32;
        int trackBottom = virtualHeight - 5;
        if (trackBottom <= trackTop + 12 || rawX < trackX - 4 || rawX > trackX + 7 || rawY < trackTop || rawY > trackBottom)
            return false;
        double visible = Math.max(1.0, virtualHeight - 8.0);
        int thumbH = Math.max(14, (int)Math.round((visible / panelHeight()) * (trackBottom - trackTop)));
        thumbH = Math.min(thumbH, trackBottom - trackTop);
        int travel = Math.max(1, trackBottom - trackTop - thumbH);
        double ratio = (rawY - trackTop - thumbH / 2.0) / travel;
        scrollOffset = Math.max(0.0, Math.min(maxScroll, ratio * maxScroll));
        scrollbarDragging = true;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double rawMx = mouseX / uiScale;
        double rawMy = mouseY / uiScale;
        double mx = rawMx;
        double my = rawMy + scrollOffset;
        if (button == 0) {
            if (handleScrollbarPress(rawMx, rawMy)) return true;
            if (maxScroll > 0.5 && rawMy >= panelTop && rawMy < panelTop + 29 && rawMx >= panelLeft && rawMx < panelLeft + 320)
                return true;
            if (inside(mx, my, panelLeft + 20, panelTop + 34, 280, 18)) {
                signalInfoExpanded = !signalInfoExpanded;
                refreshScaleAndPositions();
                return true;
            }

            if (signalInfoExpanded) {
                if (inside(mx, my, panelLeft + 188, panelTop + 122, 34, 18)) {
                    int range = Math.max(16, payload.automaticRouteRange() - 16);
                    PacketDistributor.sendToServer(new SetAutomaticRouteRangePayload(payload.pos(), payload.sessionId(), range));
                    return true;
                }
                if (inside(mx, my, panelLeft + 258, panelTop + 122, 34, 18)) {
                    int range = Math.min(256, payload.automaticRouteRange() + 16);
                    PacketDistributor.sendToServer(new SetAutomaticRouteRangePayload(payload.pos(), payload.sessionId(), range));
                    return true;
                }
            }

            int rh = routeHeaderBase();
            if (inside(mx, my, panelLeft + 20, panelTop + rh, 280, 18)) {
                routesExpanded = !routesExpanded;
                if (!routesExpanded)
                    routeTypeDropdownOpen = false;
                refreshScaleAndPositions();
                return true;
            }

            int dh = destinationHeaderBase();
            if (inside(mx, my, panelLeft + 20, panelTop + dh, 280, 18)) {
                destinationRulesExpanded = !destinationRulesExpanded;
                refreshScaleAndPositions();
                return true;
            }

            if (routesExpanded) {
                int rb = routeContentBase();
                if (inside(mx, my, panelLeft + 84, panelTop + rb + 197, 152, 18)) {
                    PacketDistributor.sendToServer(new SetRouteLearningPayload(payload.pos(), payload.sessionId(), !payload.allowNewRoutes()));
                    return true;
                }
                if (routeControlsActive && routeTypeDropdownOpen) {
                    String[] types = {"STRAIGHT", "DIVERGING LEFT", "DIVERGING RIGHT"};
                    for (int i = 0; i < types.length; i++) {
                        if (inside(mx, my, panelLeft + 174, panelTop + rb + 82 + i * 18, 122, 18)) {
                            setCurrentRouteType(types[i]);
                            return true;
                        }
                    }
                    routeTypeDropdownOpen = false;
                    updateFieldVisibility();
                    return true;
                }
                if (routeControlsActive && inside(mx, my, panelLeft + 32, panelTop + rb + 62, 130, 18)) {
                    cycleCurrentRouteIndication();
                    return true;
                }
                if (routeControlsActive && inside(mx, my, panelLeft + 174, panelTop + rb + 62, 122, 18)) {
                    routeTypeDropdownOpen = !routeTypeDropdownOpen;
                    updateFieldVisibility();
                    return true;
                }
                if (routeControlsActive && inside(mx, my, panelLeft + 24, panelTop + rb + 17, 34, 24)) {
                    changeRoute(-1);
                    return true;
                }
                if (routeControlsActive && inside(mx, my, panelLeft + 262, panelTop + rb + 17, 34, 24)) {
                    changeRoute(1);
                    return true;
                }
                if (routeControlsActive && inside(mx, my, panelLeft + 20, panelTop + rb + 149, 112, 18)) {
                    minecraft.setScreen(new SmallTheatreMappingScreen(payload, routeIndex));
                    return true;
                }
                if (routeControlsActive && inside(mx, my, panelLeft + 20, panelTop + rb + 125, 62, 20)) {
                    saveCurrent();
                    return true;
                }
                if (routeControlsActive && inside(mx, my, panelLeft + 86, panelTop + rb + 125, 62, 20)) {
                    clearCurrent();
                    return true;
                }
                if (routeControlsActive && inside(mx, my, panelLeft + 152, panelTop + rb + 125, 148, 20)) {
                    String routeKey = routes.get(routeIndex).key();
                    PacketDistributor.sendToServer(new DeleteLoadedRoutePayload(payload.pos(), payload.sessionId(), routeKey));
                    routes.remove(routeIndex);
                    if (routeIndex >= routes.size())
                        routeIndex = Math.max(0, routes.size() - 1);
                    refreshRouteControls();
                    return true;
                }
                if (inside(mx, my, panelLeft + 148, panelTop + rb + 149, 132, 18)) {
                    PacketDistributor.sendToServer(new ClearLoadedRoutesPayload(payload.pos(), payload.sessionId()));
                    routes.clear();
                    routeIndex = 0;
                    refreshRouteControls();
                    return true;
                }
                if (inside(mx, my, panelLeft + 94, panelTop + rb + 173, 132, 18)) {
                    String selectedKey = routes.isEmpty() ? "" : routes.get(routeIndex).key();
                    minecraft.setScreen(new RoutePathMapScreen(payload, selectedKey));
                    return true;
                }
            }

            if (destinationRulesExpanded) {
                int db = destinationContentBase();
                if (inside(mx, my, panelLeft + 24, panelTop + db + 18, 34, 22)) { changeDestinationRule(-1); return true; }
                if (inside(mx, my, panelLeft + 262, panelTop + db + 18, 34, 22)) { changeDestinationRule(1); return true; }
                if (inside(mx, my, panelLeft + 20, panelTop + db + 80, 58, 20)) { newDestinationRule(); return true; }
                if (inside(mx, my, panelLeft + 82, panelTop + db + 80, 76, 20)) { saveDestinationRule(); return true; }
                if (inside(mx, my, panelLeft + 162, panelTop + db + 80, 76, 20)) { deleteDestinationRule(); return true; }
                if (!destinationRules.isEmpty() && inside(mx, my, panelLeft + 20, panelTop + db + 104, 132, 20)) { openDestinationSmallTheatreMapping(); return true; }
            }

            int done = doneBase();
            if (inside(mx, my, panelLeft + 122, panelTop + done, 76, 20)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private int routeHeaderBase() {
        return signalInfoExpanded ? 156 : 54;
    }

    private int routeContentBase() {
        return routeHeaderBase() + 22;
    }

    private int destinationHeaderBase() {
        return routeHeaderBase() + 22 + (routesExpanded ? 246 : 0);
    }

    private int destinationContentBase() {
        return destinationHeaderBase() + 22;
    }

    private int doneBase() {
        return destinationHeaderBase() + 22 + (destinationRulesExpanded ? 130 : 0);
    }

    private int panelHeight() {
        return doneBase() + 28;
    }

    private void repositionRouteFields() {
        if (labelBox == null || theatreBox == null)
            return;
        labelBox.setX(panelLeft + 36);
        theatreBox.setX(panelLeft + 232);
        int y = panelTop + routeContentBase() + 101;
        labelBox.setY(y);
        theatreBox.setY(y);
        if (destinationPatternBox != null) {
            destinationPatternBox.setX(panelLeft + 32);
            destinationTheatreBox.setX(panelLeft + 232);
            int db = panelTop + destinationContentBase() + 52;
            destinationPatternBox.setY(db);
            destinationTheatreBox.setY(db);
        }
        updateFieldVisibility();
    }

    private void updateFieldVisibility() {
        if (labelBox != null) {
            boolean showRouteFields = routesExpanded && !routeTypeDropdownOpen;
            labelBox.visible = showRouteFields;
            theatreBox.visible = showRouteFields;
            labelBox.active = showRouteFields;
            theatreBox.active = showRouteFields;
        }
        if (destinationPatternBox != null) {
            destinationPatternBox.visible = destinationRulesExpanded;
            destinationTheatreBox.visible = destinationRulesExpanded;
            destinationPatternBox.active = destinationRulesExpanded;
            destinationTheatreBox.active = destinationRulesExpanded;
        }
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void drawControl(GuiGraphics graphics, int x, int y, int w, int h,
                             Component text, boolean active, boolean hovered) {
        int fill = !active ? 0xFF242424 : hovered ? 0xFF4A4A42 : 0xFF363632;
        int border = !active ? 0xFF454545 : hovered ? 0xFFB8A96A : 0xFF777064;
        int textColor = active ? 0xFFF2F2F2 : 0xFF777777;

        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
        graphics.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, textColor);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateUiScale();
        repositionRouteFields();
        graphics.fill(0, 0, width, height, 0x78000000);

        int panelWidth = 320;
        int panelHeight = panelHeight();
        int left = panelLeft;
        int top = panelTop;
        int right = left + panelWidth;
        int bottom = top + panelHeight;
        int center = virtualWidth / 2;
        double vmx = virtualMouseX(mouseX);
        double vmy = virtualMouseY(mouseY);

        graphics.pose().pushPose();
        graphics.pose().scale((float)uiScale, (float)uiScale, 1.0f);
        graphics.pose().translate(0.0f, (float)-scrollOffset, 0.0f);

        graphics.fill(left, top, right, bottom, 0xF0181818);
        graphics.fill(left + 1, top + 1, right - 1, top + 28, 0xFF292925);

        graphics.fill(left, top, right, top + 1, 0xFF777064);
        graphics.fill(left, bottom - 1, right, bottom, 0xFF4D4A44);
        graphics.fill(left, top, left + 1, bottom, 0xFF68645C);
        graphics.fill(right - 1, top, right, bottom, 0xFF3E3C38);

        graphics.drawCenteredString(font, title, center, top + 10, 0xFFF3F0E8);

        int infoX = left + 26;
        drawControl(graphics, left + 20, top + 34, 280, 18,
                Component.literal(signalInfoExpanded ? "SIGNAL INFO  \u25be" : "SIGNAL INFO  \u25b8"),
                true, inside(vmx, vmy, left + 20, top + 34, 280, 18));

        if (signalInfoExpanded) {
            graphics.fill(left + 14, top + 58, right - 14, top + 146, 0xFF111111);
            graphics.drawString(font,
                    Component.translatable("screen.create_signal_extensions.route_interface.signal", payload.signal()),
                    infoX, top + 62, 0xFFE8E8E8);
            graphics.drawString(font,
                    Component.translatable("screen.create_signal_extensions.route_interface.aspect", payload.aspect()),
                    infoX, top + 77, 0xFFE8E8E8);
            graphics.drawString(font,
                    Component.translatable("screen.create_signal_extensions.route_interface.destination",
                            payload.destination().isBlank() ? "-" : payload.destination()),
                    infoX, top + 92, 0xFFE8E8E8);

            String current = Component.translatable(
                    "screen.create_signal_extensions.route_interface.no_active_route").getString();
            if (!payload.currentRouteKey().isBlank()) {
                for (OpenRouteConfigPayload.RouteEntry route : routes) {
                    if (route.key().equals(payload.currentRouteKey())) {
                        current = route.label().isBlank()
                                ? Component.translatable(
                                        "screen.create_signal_extensions.route_interface.unmapped").getString()
                                : route.label();
                        break;
                    }
                }
            }
            graphics.drawString(font,
                    Component.translatable("screen.create_signal_extensions.route_interface.current", current),
                    infoX, top + 107, 0xFFFFFFFF);

            graphics.drawString(font, Component.literal("Automatic route range"), infoX, top + 126, 0xFFAAA58F);
            drawControl(graphics, left + 188, top + 122, 34, 18, Component.literal("-"),
                    payload.automaticRouteRange() > 16, inside(vmx, vmy, left + 188, top + 122, 34, 18));
            graphics.drawCenteredString(font, Component.literal(payload.automaticRouteRange() + " blocks"),
                    left + 240, top + 127, 0xFFE8E8E8);
            drawControl(graphics, left + 258, top + 122, 34, 18, Component.literal("+"),
                    payload.automaticRouteRange() < 256, inside(vmx, vmy, left + 258, top + 122, 34, 18));
        }

        int rh = routeHeaderBase();
        drawControl(graphics, left + 20, top + rh, 280, 18,
                Component.literal(routesExpanded ? "ROUTES  \u25be" : "ROUTES  \u25b8"),
                true, inside(vmx, vmy, left + 20, top + rh, 280, 18));

        if (routesExpanded) {
            int routeBase = routeContentBase();
            graphics.fill(left + 14, top + routeBase, right - 14, top + routeBase + 239, 0xFF111111);

            if (routes.isEmpty()) {
                graphics.drawCenteredString(font,
                        Component.translatable("screen.create_signal_extensions.route_interface.no_routes"),
                        center, top + routeBase + 41, 0xFF888888);
            } else {
                OpenRouteConfigPayload.RouteEntry route = routes.get(routeIndex);
                boolean activeRoute = route.key().equals(payload.currentRouteKey());

                Component routeHeader = Component.translatable(
                        activeRoute
                                ? "screen.create_signal_extensions.route_interface.route_number_active"
                                : "screen.create_signal_extensions.route_interface.route_number",
                        routeIndex + 1, routes.size());
                graphics.drawCenteredString(font, routeHeader, center, top + routeBase + 21,
                        activeRoute ? 0xFFF3F0E8 : 0xFFC8C8C8);

                drawControl(graphics, left + 24, top + routeBase + 17, 34, 24, Component.literal("<"),
                        true, inside(vmx, vmy, left + 24, top + routeBase + 17, 34, 24));
                drawControl(graphics, left + 262, top + routeBase + 17, 34, 24, Component.literal(">"),
                        true, inside(vmx, vmy, left + 262, top + routeBase + 17, 34, 24));

                graphics.drawString(font, Component.literal("Indication"), left + 32, top + routeBase + 41, 0xFFAAA58F);
                graphics.drawString(font, Component.literal("Route Type"),
                        left + 174, top + routeBase + 41, 0xFFAAA58F);

                drawControl(graphics, left + 32, top + routeBase + 62, 130, 18,
                        Component.literal(route.routeIndication().replace("_", " ")), true,
                        inside(vmx, vmy, left + 32, top + routeBase + 62, 130, 18));

                drawControl(graphics, left + 174, top + routeBase + 62, 122, 18,
                        Component.literal(route.routeType() + "  \u25be"), true,
                        inside(vmx, vmy, left + 174, top + routeBase + 62, 122, 18));

                if (!routeTypeDropdownOpen) {
                    graphics.drawString(font, Component.literal("Label"),
                            left + 32, top + routeBase + 88, 0xFFE0E0E0);
                    graphics.drawCenteredString(font, Component.literal("Large Theatre"),
                            left + 260, top + routeBase + 88, 0xFFE0E0E0);

                    graphics.fill(left + 32, top + routeBase + 99, left + 208, top + routeBase + 121, 0xFF080808);
                    graphics.fill(left + 32, top + routeBase + 99, left + 208, top + routeBase + 100, 0xFF777064);
                    graphics.fill(left + 32, top + routeBase + 120, left + 208, top + routeBase + 121, 0xFF4A4842);
                    graphics.fill(left + 32, top + routeBase + 99, left + 33, top + routeBase + 121, 0xFF777064);
                    graphics.fill(left + 207, top + routeBase + 99, left + 208, top + routeBase + 121, 0xFF4A4842);

                    graphics.fill(left + 228, top + routeBase + 99, left + 296, top + routeBase + 121, 0xFF080808);
                    graphics.fill(left + 228, top + routeBase + 99, left + 296, top + routeBase + 100, 0xFF777064);
                    graphics.fill(left + 228, top + routeBase + 120, left + 296, top + routeBase + 121, 0xFF4A4842);
                    graphics.fill(left + 228, top + routeBase + 99, left + 229, top + routeBase + 121, 0xFF777064);
                    graphics.fill(left + 295, top + routeBase + 99, left + 296, top + routeBase + 121, 0xFF4A4842);
                }
            }

            drawControl(graphics, left + 20, top + routeBase + 125, 62, 20, Component.literal("SAVE"),
                    routeControlsActive, inside(vmx, vmy, left + 20, top + routeBase + 125, 62, 20));
            drawControl(graphics, left + 86, top + routeBase + 125, 62, 20, Component.literal("CLEAR"),
                    routeControlsActive, inside(vmx, vmy, left + 86, top + routeBase + 125, 62, 20));
            drawControl(graphics, left + 152, top + routeBase + 125, 148, 20, Component.literal("DELETE ROUTE"),
                    routeControlsActive, inside(vmx, vmy, left + 152, top + routeBase + 125, 148, 20));
            drawControl(graphics, left + 20, top + routeBase + 149, 112, 18, Component.literal("SMALL THEATRE"),
                    routeControlsActive, inside(vmx, vmy, left + 20, top + routeBase + 149, 112, 18));
            drawControl(graphics, left + 148, top + routeBase + 149, 132, 18, Component.literal("CLEAR LOADED ROUTES"),
                    true, inside(vmx, vmy, left + 148, top + routeBase + 149, 132, 18));
            drawControl(graphics, left + 94, top + routeBase + 173, 132, 18, Component.literal("PATH MAP"),
                    routeControlsActive, inside(vmx, vmy, left + 94, top + routeBase + 173, 132, 18));
            graphics.drawString(font, Component.literal("Route learning"), left + 20, top + routeBase + 201, 0xFFAAA58F);
            drawControl(graphics, left + 84, top + routeBase + 197, 152, 18,
                    Component.literal(payload.allowNewRoutes() ? "ALLOW NEW ROUTES: ON" : "ALLOW NEW ROUTES: OFF"), true,
                    inside(vmx, vmy, left + 84, top + routeBase + 197, 152, 18));
        }

        int dh = destinationHeaderBase();
        drawControl(graphics, left + 20, top + dh, 280, 18,
                Component.literal(destinationRulesExpanded
                        ? "DESTINATION RULES  (* any, ? one)  \u25be"
                        : "DESTINATION RULES  (* any, ? one)  \u25b8"),
                true, inside(vmx, vmy, left + 20, top + dh, 280, 18));

        if (destinationRulesExpanded) {
            int db = destinationContentBase();
            graphics.fill(left + 14, top + db, right - 14, top + db + 130, 0xFF111111);
            String ruleCount = destinationRules.isEmpty() ? "New rule" : "Rule " + (destinationRuleIndex + 1) + " / " + destinationRules.size();
            graphics.drawCenteredString(font, Component.literal(ruleCount), center, top + db + 23, 0xFFC8C8C8);
            drawControl(graphics, left + 24, top + db + 18, 34, 22, Component.literal("<"), !destinationRules.isEmpty(), inside(vmx, vmy, left + 24, top + db + 18, 34, 22));
            drawControl(graphics, left + 262, top + db + 18, 34, 22, Component.literal(">"), !destinationRules.isEmpty(), inside(vmx, vmy, left + 262, top + db + 18, 34, 22));
            graphics.drawString(font, Component.literal("Station / destination pattern"), left + 32, top + db + 39, 0xFFE0E0E0);
            graphics.drawCenteredString(font, Component.literal("Theatre"), left + 260, top + db + 39, 0xFFE0E0E0);
            graphics.fill(left + 28, top + db + 50, left + 212, top + db + 72, 0xFF080808);
            graphics.fill(left + 228, top + db + 50, left + 296, top + db + 72, 0xFF080808);
            drawControl(graphics, left + 20, top + db + 80, 58, 20, Component.literal("NEW"), true, inside(vmx, vmy, left + 20, top + db + 80, 58, 20));
            drawControl(graphics, left + 82, top + db + 80, 76, 20, Component.literal("SAVE"), true, inside(vmx, vmy, left + 82, top + db + 80, 76, 20));
            drawControl(graphics, left + 162, top + db + 80, 76, 20, Component.literal("DELETE"), !destinationRules.isEmpty(), inside(vmx, vmy, left + 162, top + db + 80, 76, 20));
            drawControl(graphics, left + 20, top + db + 104, 132, 20, Component.literal("SMALL THEATRE"), !destinationRules.isEmpty(), inside(vmx, vmy, left + 20, top + db + 104, 132, 20));
        }

        int done = doneBase();
        drawControl(graphics, left + 122, top + done, 76, 20, Component.literal("DONE"),
                true, inside(vmx, vmy, left + 122, top + done, 76, 20));

        if (routesExpanded && !routeTypeDropdownOpen) {
            labelBox.render(graphics, (int)vmx, (int)vmy, partialTick);
            theatreBox.render(graphics, (int)vmx, (int)vmy, partialTick);
        }
        if (destinationRulesExpanded) {
            destinationPatternBox.render(graphics, (int)vmx, (int)vmy, partialTick);
            destinationTheatreBox.render(graphics, (int)vmx, (int)vmy, partialTick);
        }

        if (routesExpanded && routeControlsActive && routeTypeDropdownOpen && !routes.isEmpty()) {
            int routeBase = routeContentBase();
            String[] types = {"STRAIGHT", "DIVERGING LEFT", "DIVERGING RIGHT"};
            for (int i = 0; i < types.length; i++)
                drawControl(graphics, left + 174, top + routeBase + 82 + i * 18, 122, 18,
                        Component.literal(types[i]), true,
                        inside(vmx, vmy, left + 174, top + routeBase + 82 + i * 18, 122, 18));
        }

        graphics.pose().popPose();

        // Beta 7a: the title bar stays fixed while oversized content scrolls beneath it.
        graphics.pose().pushPose();
        graphics.pose().scale((float)uiScale, (float)uiScale, 1.0f);
        graphics.fill(left, top, right, top + 29, 0xFF292925);
        graphics.fill(left, top, right, top + 1, 0xFF777064);
        graphics.drawCenteredString(font, title, center, top + 10, 0xFFF3F0E8);
        if (maxScroll > 0.5) {
            int trackX = right - 7;
            int trackTop = top + 32;
            int trackBottom = virtualHeight - 5;
            if (trackBottom > trackTop + 12) {
                graphics.fill(trackX, trackTop, trackX + 3, trackBottom, 0xFF2B2B2B);
                double visible = Math.max(1.0, virtualHeight - 8.0);
                int thumbH = Math.max(14, (int)Math.round((visible / panelHeight) * (trackBottom - trackTop)));
                thumbH = Math.min(thumbH, trackBottom - trackTop);
                int travel = Math.max(1, trackBottom - trackTop - thumbH);
                int thumbY = trackTop + (int)Math.round((scrollOffset / maxScroll) * travel);
                graphics.fill(trackX - 1, thumbY, trackX + 4, thumbY + thumbH, 0xFFB8B8B8);
            }
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0.5 && scrollY != 0.0) {
            scrollOffset = Math.max(0.0, Math.min(maxScroll, scrollOffset - scrollY * 24.0));
            return true;
        }
        return super.mouseScrolled(virtualMouseX(mouseX), virtualMouseY(mouseY), scrollX / uiScale, scrollY / uiScale);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(virtualMouseX(mouseX), virtualMouseY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && scrollbarDragging && maxScroll > 0.5) {
            double rawY = mouseY / uiScale;
            int trackTop = panelTop + 32;
            int trackBottom = virtualHeight - 5;
            double visible = Math.max(1.0, virtualHeight - 8.0);
            int thumbH = Math.max(14, (int)Math.round((visible / panelHeight()) * (trackBottom - trackTop)));
            thumbH = Math.min(thumbH, trackBottom - trackTop);
            int travel = Math.max(1, trackBottom - trackTop - thumbH);
            double ratio = (rawY - trackTop - thumbH / 2.0) / travel;
            scrollOffset = Math.max(0.0, Math.min(maxScroll, ratio * maxScroll));
            return true;
        }
        return super.mouseDragged(virtualMouseX(mouseX), virtualMouseY(mouseY), button, dragX / uiScale, dragY / uiScale);
    }

    @Override
    public void onClose() {
        ClientPayloadHandlers.markRouteInterfaceClosed(payload.pos(), payload.sessionId());
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
