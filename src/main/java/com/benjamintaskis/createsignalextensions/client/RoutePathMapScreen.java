package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.network.DeleteLoadedRoutePayload;
import com.benjamintaskis.createsignalextensions.network.OpenRouteConfigPayload;
import com.benjamintaskis.createsignalextensions.network.RequestRouteConfigPayload;
import com.benjamintaskis.createsignalextensions.network.SaveRouteLabelPayload;
import com.benjamintaskis.createsignalextensions.network.SetAutomaticRouteRangePayload;
import com.benjamintaskis.createsignalextensions.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Beta 6c route-map editor. The map is rotated into a signal-origin,
 * left-to-right view while a compass preserves the relationship to Minecraft
 * north. Visual geometry remains separate from the learned route key.
 */
public class RoutePathMapScreen extends Screen {
    private OpenRouteConfigPayload payload;
    private final List<OpenRouteConfigPayload.RouteEntry> routes = new ArrayList<>();
    private String selectedKey;
    private boolean selectedOnly;
    private int refreshTicks;

    private int panelLeft, panelTop, panelWidth, panelHeight;
    private double uiScale = 1.0;
    private int virtualWidth, virtualHeight;
    private double scrollOffset = 0.0;
    private double maxScroll = 0.0;
    private boolean scrollbarDragging = false;
    private int mapLeft, mapTop, mapRight, mapBottom;
    private int editorLeft, editorRight;
    private EditBox labelBox;
    private EditBox theatreBox;

    public RoutePathMapScreen(OpenRouteConfigPayload payload, String selectedKey) {
        super(Component.literal("Route Map"));
        this.payload = payload;
        routes.addAll(payload.routes());
        this.selectedKey = selectedKey == null ? "" : selectedKey;
        ensureSelection();
    }

    public boolean isFor(BlockPos pos) { return payload.pos().equals(pos); }

    public void applyUpdate(OpenRouteConfigPayload update) {
        if (!isFor(update.pos())) return;
        boolean editing = labelBox != null && (labelBox.isFocused() || (theatreBox != null && theatreBox.isFocused()));
        payload = update;
        routes.clear();
        routes.addAll(update.routes());
        ensureSelection();
        if (!editing) refreshEditor();
    }

    private void updateUiScale() {
        // Beta 7a: fit the complete 790x410 design first. The configured minimum
        // is a readability floor for HEIGHT. If fitting the height would require
        // going below that floor, keep the floor and scroll vertically instead.
        // Width is always allowed to force a smaller scale because this GUI never
        // uses horizontal scrolling.
        double minimum = ClientConfig.minimumGuiScale();
        double byWidth = (width * 0.96) / 790.0;
        double byHeight = (height * 0.96) / 410.0;
        double fit = Math.min(1.0, Math.min(byWidth, byHeight));
        if (fit >= minimum)
            uiScale = fit;
        else
            uiScale = Math.max(0.10, Math.min(byWidth, minimum));

        virtualWidth = Math.max(1, (int)Math.floor(width / uiScale));
        virtualHeight = Math.max(1, (int)Math.floor(height / uiScale));

        double visiblePanelHeight = Math.max(1.0, virtualHeight - 8.0);
        maxScroll = Math.max(0.0, 410.0 - visiblePanelHeight);
        scrollOffset = Math.max(0.0, Math.min(scrollOffset, maxScroll));
    }

    private double virtualMouseX(double mouseX) { return mouseX / uiScale; }
    private double virtualMouseY(double mouseY) { return mouseY / uiScale + scrollOffset; }

    private void layout() {
        panelWidth = 790;
        panelHeight = 410;
        panelLeft = (virtualWidth - panelWidth) / 2;
        panelTop = 4;
        editorRight = panelLeft + panelWidth - 12;
        editorLeft = Math.max(panelLeft + 330, editorRight - 220);
        mapLeft = panelLeft + 12;
        mapTop = panelTop + 64;
        mapRight = editorLeft - 10;
        mapBottom = panelTop + panelHeight - 48;
    }

    @Override
    protected void init() {
        updateUiScale();
        layout();
        labelBox = new EditBox(font, editorLeft + 8, mapTop + 54, Math.max(70, editorRight - editorLeft - 16), 18,
                Component.literal("Route label"));
        labelBox.setMaxLength(32);
        labelBox.setBordered(false);
        addRenderableWidget(labelBox);

        theatreBox = new EditBox(font, editorLeft + 8, mapTop + 97, 52, 18,
                Component.literal("Large theatre"));
        theatreBox.setMaxLength(1);
        theatreBox.setBordered(false);
        theatreBox.setFilter(value -> value.isEmpty() || (value.length() == 1 &&
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789/\\".contains(value)));
        addRenderableWidget(theatreBox);
        refreshEditor();
    }

    private void ensureSelection() {
        if (findRouteIndex(selectedKey) >= 0) return;
        if (!payload.currentRouteKey().isBlank() && findRouteIndex(payload.currentRouteKey()) >= 0)
            selectedKey = payload.currentRouteKey();
        else
            selectedKey = routes.isEmpty() ? "" : routes.get(0).key();
    }

    private int findRouteIndex(String key) {
        if (key == null || key.isBlank()) return -1;
        for (int i = 0; i < routes.size(); i++) if (routes.get(i).key().equals(key)) return i;
        return -1;
    }

    private OpenRouteConfigPayload.RouteEntry selectedRoute() {
        int index = findRouteIndex(selectedKey);
        return index >= 0 ? routes.get(index) : null;
    }

    private void refreshEditor() {
        if (labelBox == null || theatreBox == null) return;
        OpenRouteConfigPayload.RouteEntry route = selectedRoute();
        boolean active = route != null;
        labelBox.setEditable(active);
        theatreBox.setEditable(active);
        labelBox.setValue(route == null ? "" : route.label());
        theatreBox.setValue(route == null ? "" : route.theatreIndication());
    }

    private void changeRoute(int amount) {
        if (routes.isEmpty()) return;
        saveCurrent(false);
        int index = findRouteIndex(selectedKey);
        if (index < 0) index = 0;
        index = Math.floorMod(index + amount, routes.size());
        selectedKey = routes.get(index).key();
        refreshEditor();
    }

    private void cycleRouteType() {
        OpenRouteConfigPayload.RouteEntry route = selectedRoute();
        if (route == null) return;
        String next = switch (route.routeType()) {
            case "STRAIGHT" -> "DIVERGING LEFT";
            case "DIVERGING LEFT" -> "DIVERGING RIGHT";
            default -> "STRAIGHT";
        };
        int index = findRouteIndex(route.key());
        routes.set(index, new OpenRouteConfigPayload.RouteEntry(route.key(), route.label(), route.theatreIndication(),
                route.smallTheatreIndications(), next, route.routeIndication(), route.visualPath()));
        rebuildLocalPayload();
        PacketDistributor.sendToServer(new SaveRouteLabelPayload(payload.pos(), route.key(), route.label(), route.theatreIndication(), next, route.routeIndication()));
    }

    private void rebuildLocalPayload() {
        payload = new OpenRouteConfigPayload(payload.pos(), payload.signal(), payload.aspect(), payload.destination(),
                payload.currentRouteKey(), List.copyOf(routes), payload.destinationRules(), payload.automaticRouteRange(),
                payload.allowNewRoutes(), payload.refreshResponse(), payload.sessionId());
    }

    private void saveCurrent(boolean refreshLocal) {
        OpenRouteConfigPayload.RouteEntry route = selectedRoute();
        if (route == null || labelBox == null || theatreBox == null) return;
        String label = labelBox.getValue().trim();
        String theatre = theatreBox.getValue().trim().toUpperCase();
        int index = findRouteIndex(route.key());
        OpenRouteConfigPayload.RouteEntry updated = new OpenRouteConfigPayload.RouteEntry(route.key(), label, theatre,
                route.smallTheatreIndications(), route.routeType(), route.routeIndication(), route.visualPath());
        routes.set(index, updated);
        rebuildLocalPayload();
        PacketDistributor.sendToServer(new SaveRouteLabelPayload(payload.pos(), route.key(), label, theatre, route.routeType(), route.routeIndication()));
        if (refreshLocal) refreshEditor();
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks >= 10) {
            refreshTicks = 0;
            PacketDistributor.sendToServer(new RequestRouteConfigPayload(payload.pos(), payload.sessionId()));
        }
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void drawControl(GuiGraphics g, int x, int y, int w, int h, Component text, boolean active, boolean hovered) {
        int fill = !active ? 0xFF242424 : hovered ? 0xFF4A4A42 : 0xFF363632;
        int border = !active ? 0xFF454545 : hovered ? 0xFFB8A96A : 0xFF777064;
        int textColor = active ? 0xFFF2F2F2 : 0xFF777777;
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, textColor);
    }

    private List<OpenRouteConfigPayload.RouteEntry> visibleRoutes() {
        if (!selectedOnly) return routes;
        OpenRouteConfigPayload.RouteEntry selected = selectedRoute();
        return selected == null ? List.of() : List.of(selected);
    }

    private record OrientedTransform(double ox, double oz, double dirX, double dirZ,
                                     double leftX, double leftZ, double scale,
                                     double screenOriginX, double screenCenterY) {
        double u(double x, double z) { return (x - ox) * dirX + (z - oz) * dirZ; }
        double v(double x, double z) { return (x - ox) * leftX + (z - oz) * leftZ; }
        int x(double x, double z) { return (int)Math.round(screenOriginX + u(x, z) * scale); }
        int y(double x, double z) { return (int)Math.round(screenCenterY - v(x, z) * scale); }
    }

    private OpenRouteConfigPayload.RouteEntry referenceRoute(List<OpenRouteConfigPayload.RouteEntry> visible) {
        OpenRouteConfigPayload.RouteEntry selected = selectedRoute();
        if (selected != null && selected.visualPath().size() >= 2) return selected;
        for (OpenRouteConfigPayload.RouteEntry route : visible)
            if (route.visualPath().size() >= 2) return route;
        return null;
    }

    private OrientedTransform calculateTransform(List<OpenRouteConfigPayload.RouteEntry> visible) {
        OpenRouteConfigPayload.RouteEntry ref = referenceRoute(visible);
        if (ref == null) return null;
        List<OpenRouteConfigPayload.PathPoint> rp = ref.visualPath();
        OpenRouteConfigPayload.PathPoint origin = rp.get(0);
        OpenRouteConfigPayload.PathPoint forward = null;
        for (int i = 1; i < rp.size(); i++) {
            OpenRouteConfigPayload.PathPoint p = rp.get(i);
            if (Math.hypot(p.x() - origin.x(), p.z() - origin.z()) > 0.01) { forward = p; break; }
        }
        if (forward == null) return null;
        double dx = forward.x() - origin.x(), dz = forward.z() - origin.z();
        double len = Math.hypot(dx, dz);
        double dirX = dx / len, dirZ = dz / len;
        double leftX = dirZ, leftZ = -dirX;

        double maxU = 1.0, minV = 0.0, maxV = 0.0;
        for (OpenRouteConfigPayload.RouteEntry route : visible) {
            for (OpenRouteConfigPayload.PathPoint p : route.visualPath()) {
                double rx = p.x() - origin.x(), rz = p.z() - origin.z();
                double u = rx * dirX + rz * dirZ;
                double v = rx * leftX + rz * leftZ;
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }
        }
        double availableW = Math.max(20, mapRight - mapLeft - 72);
        double availableH = Math.max(20, mapBottom - mapTop - 42);
        double vSpan = Math.max(1.0, maxV - minV);
        // Beta 6c: the configured ceiling defines the horizontal ruler. A 48-block
        // interface therefore puts 48 blocks near the right edge even when the
        // currently selected learned path is shorter.
        double horizontalExtent = Math.max(payload.automaticRouteRange(), maxU);
        double scale = Math.min(availableW / Math.max(1.0, horizontalExtent), availableH / vSpan);
        double originScreenX = mapLeft + 44;
        double centerY = mapTop + 21 + availableH / 2.0 + (minV + maxV) * scale / 2.0;
        return new OrientedTransform(origin.x(), origin.z(), dirX, dirZ, leftX, leftZ, scale, originScreenX, centerY);
    }

    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color, int thickness) {
        int dx = x2 - x1, dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) { g.fill(x1 - thickness/2, y1 - thickness/2, x1 + (thickness+1)/2, y1 + (thickness+1)/2, color); return; }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + dx * i / steps, y = y1 + dy * i / steps;
            g.fill(x - thickness/2, y - thickness/2, x + (thickness+1)/2, y + (thickness+1)/2, color);
        }
    }

    private double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx=x2-x1, dy=y2-y1, ls=dx*dx+dy*dy;
        if (ls <= 1e-6) return Math.hypot(px-x1, py-y1);
        double t=((px-x1)*dx+(py-y1)*dy)/ls; t=Math.max(0,Math.min(1,t));
        return Math.hypot(px-(x1+t*dx), py-(y1+t*dy));
    }

    private void selectNearestPath(double mouseX, double mouseY) {
        OrientedTransform t = calculateTransform(routes);
        if (t == null) return;
        double best=7.0; String bestKey=null;
        for (OpenRouteConfigPayload.RouteEntry route:routes) {
            List<OpenRouteConfigPayload.PathPoint> path=route.visualPath();
            for(int i=1;i<path.size();i++) {
                var a=path.get(i-1); var b=path.get(i);
                double d=distanceToSegment(mouseX,mouseY,t.x(a.x(),a.z()),t.y(a.x(),a.z()),t.x(b.x(),b.z()),t.y(b.x(),b.z()));
                if(d<best){best=d;bestKey=route.key();}
            }
        }
        if(bestKey!=null && !bestKey.equals(selectedKey)){ saveCurrent(false); selectedKey=bestKey; refreshEditor(); }
    }

    private void drawDistanceTicks(GuiGraphics g, OrientedTransform t, OpenRouteConfigPayload.RouteEntry route) {
        if (route == null || route.visualPath().size() < 2) return;
        List<OpenRouteConfigPayload.PathPoint> path = route.visualPath();
        double travelled = 0.0;
        double nextTick = 16.0;
        for (int i=1;i<path.size();i++) {
            var a=path.get(i-1); var b=path.get(i);
            double seg=Math.hypot(b.x()-a.x(), b.z()-a.z());
            if(seg<1e-6) continue;
            while(nextTick <= travelled + seg + 1e-6 && nextTick <= payload.automaticRouteRange() + 1e-6) {
                double f=(nextTick-travelled)/seg;
                double wx=a.x()+(b.x()-a.x())*f, wz=a.z()+(b.z()-a.z())*f;
                int sx=t.x(wx,wz), sy=t.y(wx,wz);
                int ax=t.x(a.x(),a.z()), ay=t.y(a.x(),a.z()), bx=t.x(b.x(),b.z()), by=t.y(b.x(),b.z());
                double sdx=bx-ax, sdy=by-ay, sl=Math.hypot(sdx,sdy);
                if(sl>0.1){int px=(int)Math.round(-sdy/sl*3), py=(int)Math.round(sdx/sl*3); drawLine(g,sx-px,sy-py,sx+px,sy+py,0xFF9A968C,1);}
                String label=Integer.toString((int)nextTick);
                g.drawString(font, Component.literal(label), sx-font.width(label)/2, sy+5, 0xFF8A877F);
                if (Math.abs(nextTick - payload.automaticRouteRange()) < 0.1)
                    g.drawString(font, Component.literal("LIMIT"), sx-font.width("LIMIT")/2, sy-13, 0xFFE0C96E);
                nextTick += 16.0;
            }
            travelled += seg;
        }
    }

    private void drawRangeBoundary(GuiGraphics g, OrientedTransform t) {
        int x = (int)Math.round(t.screenOriginX() + payload.automaticRouteRange() * t.scale());
        if (x <= mapLeft + 4 || x >= mapRight - 3) return;
        for (int y = mapTop + 8; y < mapBottom - 8; y += 8)
            g.fill(x, y, x + 1, Math.min(y + 4, mapBottom - 8), 0xFF77736A);
        String label = Integer.toString(payload.automaticRouteRange());
        g.drawCenteredString(font, Component.literal(label), x, mapTop + 7, 0xFFC9C4B8);
        g.drawString(font, Component.literal("LIMIT"), Math.min(x + 4, mapRight - font.width("LIMIT") - 4), mapTop + 18, 0xFF9A968C);
    }

    private void drawCompass(GuiGraphics g, OrientedTransform t) {
        int cx=mapLeft+18, cy=mapTop+17;
        // Minecraft north is -Z in world coordinates. Rotate it with the same transform as the track.
        double nu = -t.dirZ();
        double nv = -t.leftZ();
        double sx=nu, sy=-nv;
        double len=Math.hypot(sx,sy); if(len<1e-6) return; sx/=len; sy/=len;
        int ex=cx+(int)Math.round(sx*11), ey=cy+(int)Math.round(sy*11);
        drawLine(g,cx,cy,ex,ey,0xFFE8E8E8,2);
        g.fill(ex-2,ey-2,ex+3,ey+3,0xFFE8E8E8);
        g.drawString(font, Component.literal("N"), ex-3, ey-12, 0xFFF2F2F2);
    }

    private boolean handleScrollbarPress(double rawX, double rawY) {
        if (maxScroll <= 0.5) return false;
        int right = panelLeft + panelWidth;
        int trackX = right - 7;
        int trackTop = panelTop + 32;
        int trackBottom = virtualHeight - 5;
        if (trackBottom <= trackTop + 12 || rawX < trackX - 4 || rawX > trackX + 7 || rawY < trackTop || rawY > trackBottom)
            return false;
        double visible = Math.max(1.0, virtualHeight - 8.0);
        int thumbH = Math.max(14, (int)Math.round((visible / 410.0) * (trackBottom - trackTop)));
        thumbH = Math.min(thumbH, trackBottom - trackTop);
        int travel = Math.max(1, trackBottom - trackTop - thumbH);
        double ratio = (rawY - trackTop - thumbH / 2.0) / travel;
        scrollOffset = Math.max(0.0, Math.min(maxScroll, ratio * maxScroll));
        scrollbarDragging = true;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX,double mouseY,int button) {
        double rawMx = mouseX / uiScale, rawMy = mouseY / uiScale;
        double mx = rawMx, my = rawMy + scrollOffset;
        if(button==0) {
            if (handleScrollbarPress(rawMx, rawMy)) return true;
            if (maxScroll > 0.5 && rawMy >= panelTop && rawMy < panelTop + 29 && rawMx >= panelLeft && rawMx < panelLeft + panelWidth) return true;
            int controlsY=panelTop+panelHeight-32;
            if(inside(mx,my,panelLeft+12,controlsY,34,20)){changeRoute(-1);return true;}
            if(inside(mx,my,panelLeft+50,controlsY,34,20)){changeRoute(1);return true;}
            if(inside(mx,my,panelLeft+92,controlsY,104,20)){selectedOnly=!selectedOnly;return true;}
            if(inside(mx,my,panelLeft+202,controlsY,34,20)){
                PacketDistributor.sendToServer(new SetAutomaticRouteRangePayload(payload.pos(),payload.sessionId(),Math.max(16,payload.automaticRouteRange()-16))); return true;}
            if(inside(mx,my,panelLeft+326,controlsY,34,20)){
                PacketDistributor.sendToServer(new SetAutomaticRouteRangePayload(payload.pos(),payload.sessionId(),Math.min(256,payload.automaticRouteRange()+16))); return true;}
            if(inside(mx,my,editorLeft+8,mapTop+130,editorRight-editorLeft-16,20)){cycleRouteType();return true;}
            if(inside(mx,my,editorLeft+8,mapTop+186,editorRight-editorLeft-16,20)){saveCurrent(true);return true;}
            if(inside(mx,my,editorLeft+8,mapTop+158,editorRight-editorLeft-16,20)){
                int idx=findRouteIndex(selectedKey); if(idx>=0) minecraft.setScreen(new SmallTheatreMappingScreen(payload,idx,selectedKey)); return true;}
            if(inside(mx,my,editorLeft+8,mapTop+214,editorRight-editorLeft-16,20)){
                OpenRouteConfigPayload.RouteEntry r=selectedRoute(); if(r!=null){PacketDistributor.sendToServer(new DeleteLoadedRoutePayload(payload.pos(),payload.sessionId(),r.key())); routes.remove(findRouteIndex(r.key())); ensureSelection(); rebuildLocalPayload(); refreshEditor();} return true;}
            if(inside(mx,my,panelLeft+panelWidth-92,controlsY,80,20)){saveCurrent(false);minecraft.setScreen(new RouteInterfaceScreen(payload,selectedKey));return true;}
            if(inside(mx,my,mapLeft,mapTop,mapRight-mapLeft,mapBottom-mapTop)){if(!selectedOnly)selectNearestPath(mx,my);return true;}
        }
        return super.mouseClicked(mx,my,button);
    }

    @Override
    public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        // uiScale is set in init/resize; render everything except the dimmed world
        // through the same transform so the map, editor, text and controls shrink
        // together while preserving their proportions.
        layout();
        g.fill(0,0,width,height,0x78000000);
        double vmx = virtualMouseX(mouseX), vmy = virtualMouseY(mouseY);
        g.pose().pushPose();
        g.pose().scale((float)uiScale, (float)uiScale, 1.0f);
        g.pose().translate(0.0f, (float)-scrollOffset, 0.0f);
        int right=panelLeft+panelWidth,bottom=panelTop+panelHeight;
        g.fill(panelLeft,panelTop,right,bottom,0xF0181818); g.fill(panelLeft+1,panelTop+1,right-1,panelTop+28,0xFF292925);
        g.fill(panelLeft,panelTop,right,panelTop+1,0xFF777064); g.fill(panelLeft,bottom-1,right,bottom,0xFF4D4A44);
        g.fill(panelLeft,panelTop,panelLeft+1,bottom,0xFF68645C); g.fill(right-1,panelTop,right,bottom,0xFF3E3C38);
        g.drawCenteredString(font,title,virtualWidth/2,panelTop+10,0xFFF3F0E8);

        OpenRouteConfigPayload.RouteEntry selected=selectedRoute(); int selectedIndex=findRouteIndex(selectedKey);
        String selectedName=selected==null?"No route":(selected.label().isBlank()?"Route "+(selectedIndex+1):selected.label());
        g.drawString(font,Component.literal(selected==null?"-":selectedName+"  •  "+selected.routeType()),panelLeft+12,panelTop+34,0xFFE8E8E8);
        g.drawString(font,Component.literal("Automatic route range: "+payload.automaticRouteRange()+" blocks"),panelLeft+12,panelTop+47,0xFFAAA58F);

        g.fill(mapLeft,mapTop,mapRight,mapBottom,0xFF0B0B0B); g.fill(mapLeft,mapTop,mapRight,mapTop+1,0xFF55514A); g.fill(mapLeft,mapBottom-1,mapRight,mapBottom,0xFF373530);
        List<OpenRouteConfigPayload.RouteEntry> visible=visibleRoutes(); OrientedTransform t=calculateTransform(visible);
        if(t==null){
            g.drawCenteredString(font,Component.literal("No stored path geometry yet."),(mapLeft+mapRight)/2,(mapTop+mapBottom)/2-5,0xFF888888);
            g.drawCenteredString(font,Component.literal("Run a train through the route to learn it."),(mapLeft+mapRight)/2,(mapTop+mapBottom)/2+8,0xFF777777);
        } else {
            for(int pass=0;pass<2;pass++) for(OpenRouteConfigPayload.RouteEntry route:visible){
                boolean isSelected=route.key().equals(selectedKey); if((pass==0&&isSelected)||(pass==1&&!isSelected))continue;
                boolean isCurrent=route.key().equals(payload.currentRouteKey()); int color=isSelected?0xFFFFD966:isCurrent?0xFF78C878:0xFF666666; int thick=isSelected?3:2;
                List<OpenRouteConfigPayload.PathPoint> path=route.visualPath();
                for(int i=1;i<path.size();i++){var a=path.get(i-1);var b=path.get(i);drawLine(g,t.x(a.x(),a.z()),t.y(a.x(),a.z()),t.x(b.x(),b.z()),t.y(b.x(),b.z()),color,thick);}
            }
            OpenRouteConfigPayload.RouteEntry marker=referenceRoute(visible);
            if(marker!=null){var start=marker.visualPath().get(0);int sx=t.x(start.x(),start.z()),sy=t.y(start.x(),start.z());g.fill(sx-3,sy-3,sx+4,sy+4,0xFFE8E8E8);g.drawString(font,Component.literal("SIGNAL"),sx-font.width("SIGNAL")/2,sy+8,0xFFE8E8E8);}
            drawCompass(g,t);
            drawRangeBoundary(g,t);
            drawDistanceTicks(g,t,selected!=null?selected:marker);
            if(!selectedOnly) for(int i=0;i<routes.size();i++){var route=routes.get(i);if(route.visualPath().isEmpty())continue;var end=route.visualPath().get(route.visualPath().size()-1);String label=route.label().isBlank()?Integer.toString(i+1):route.label();g.drawString(font,Component.literal(label),t.x(end.x(),end.z())+4,t.y(end.x(),end.z())-4,route.key().equals(selectedKey)?0xFFFFE6A3:0xFFBBBBBB);}
        }

        // Beta 6c: roomy route editor with one action per row.
        g.fill(editorLeft,mapTop,editorRight,mapBottom,0xFF111111);
        g.drawCenteredString(font,Component.literal("ROUTE EDITOR"),(editorLeft+editorRight)/2,mapTop+10,0xFFAAA58F);
        g.drawString(font,Component.literal(selectedName),editorLeft+8,mapTop+27,0xFFE8E8E8);
        g.drawString(font,Component.literal("Label"),editorLeft+8,mapTop+43,0xFFAAA58F);
        g.fill(editorLeft+6,mapTop+52,editorRight-6,mapTop+76,0xFF050505);
        g.drawString(font,Component.literal("Large theatre indication"),editorLeft+8,mapTop+86,0xFFAAA58F);
        g.fill(editorLeft+6,mapTop+95,editorLeft+66,mapTop+119,0xFF050505);
        drawControl(g,editorLeft+8,mapTop+130,editorRight-editorLeft-16,20,Component.literal(selected==null?"ROUTE TYPE":selected.routeType()),selected!=null,inside(vmx,vmy,editorLeft+8,mapTop+130,editorRight-editorLeft-16,20));
        drawControl(g,editorLeft+8,mapTop+158,editorRight-editorLeft-16,20,Component.literal("EDIT SMALL THEATRE..."),selected!=null,inside(vmx,vmy,editorLeft+8,mapTop+158,editorRight-editorLeft-16,20));
        drawControl(g,editorLeft+8,mapTop+186,editorRight-editorLeft-16,20,Component.literal("SAVE"),selected!=null,inside(vmx,vmy,editorLeft+8,mapTop+186,editorRight-editorLeft-16,20));
        drawControl(g,editorLeft+8,mapTop+214,editorRight-editorLeft-16,20,Component.literal("DELETE ROUTE"),selected!=null,inside(vmx,vmy,editorLeft+8,mapTop+214,editorRight-editorLeft-16,20));

        int controlsY=panelTop+panelHeight-32;
        drawControl(g,panelLeft+12,controlsY,34,20,Component.literal("<"),!routes.isEmpty(),inside(vmx,vmy,panelLeft+12,controlsY,34,20));
        drawControl(g,panelLeft+50,controlsY,34,20,Component.literal(">"),!routes.isEmpty(),inside(vmx,vmy,panelLeft+50,controlsY,34,20));
        drawControl(g,panelLeft+92,controlsY,104,20,Component.literal(selectedOnly?"SHOW ALL":"SELECTED ONLY"),selected!=null,inside(vmx,vmy,panelLeft+92,controlsY,104,20));
        drawControl(g,panelLeft+202,controlsY,34,20,Component.literal("-"),payload.automaticRouteRange()>16,inside(vmx,vmy,panelLeft+202,controlsY,34,20));
        g.drawCenteredString(font,Component.literal(payload.automaticRouteRange()+" blocks"),panelLeft+281,controlsY+6,0xFFE8E8E8);
        drawControl(g,panelLeft+326,controlsY,34,20,Component.literal("+"),payload.automaticRouteRange()<256,inside(vmx,vmy,panelLeft+326,controlsY,34,20));
        drawControl(g,panelLeft+panelWidth-92,controlsY,80,20,Component.literal("BACK"),true,inside(vmx,vmy,panelLeft+panelWidth-92,controlsY,80,20));

        if(labelBox!=null) labelBox.render(g,(int)vmx,(int)vmy,partialTick);
        if(theatreBox!=null) theatreBox.render(g,(int)vmx,(int)vmy,partialTick);
        g.pose().popPose();

        // Keep the title bar fixed while the map body scrolls.
        g.pose().pushPose();
        g.pose().scale((float)uiScale, (float)uiScale, 1.0f);
        int fixedRight = panelLeft + panelWidth;
        g.fill(panelLeft, panelTop, fixedRight, panelTop + 29, 0xFF292925);
        g.fill(panelLeft, panelTop, fixedRight, panelTop + 1, 0xFF777064);
        g.drawCenteredString(font, title, virtualWidth / 2, panelTop + 10, 0xFFF3F0E8);
        if (maxScroll > 0.5) {
            int trackX = fixedRight - 7;
            int trackTop = panelTop + 32;
            int trackBottom = virtualHeight - 5;
            if (trackBottom > trackTop + 12) {
                g.fill(trackX, trackTop, trackX + 3, trackBottom, 0xFF2B2B2B);
                double visibleHeight = Math.max(1.0, virtualHeight - 8.0);
                int thumbH = Math.max(14, (int)Math.round((visibleHeight / 410.0) * (trackBottom - trackTop)));
                int travel = Math.max(1, trackBottom - trackTop - thumbH);
                int thumbY = trackTop + (int)Math.round((scrollOffset / maxScroll) * travel);
                g.fill(trackX - 1, thumbY, trackX + 4, thumbY + thumbH, 0xFFB8B8B8);
            }
        }
        g.pose().popPose();
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
            int thumbH = Math.max(14, (int)Math.round((visible / 410.0) * (trackBottom - trackTop)));
            thumbH = Math.min(thumbH, trackBottom - trackTop);
            int travel = Math.max(1, trackBottom - trackTop - thumbH);
            double ratio = (rawY - trackTop - thumbH / 2.0) / travel;
            scrollOffset = Math.max(0.0, Math.min(maxScroll, ratio * maxScroll));
            return true;
        }
        return super.mouseDragged(virtualMouseX(mouseX), virtualMouseY(mouseY), button, dragX / uiScale, dragY / uiScale);
    }

    @Override
    public void onClose(){ saveCurrent(false); minecraft.setScreen(new RouteInterfaceScreen(payload,selectedKey)); }
    @Override public boolean isPauseScreen(){return false;}
}
