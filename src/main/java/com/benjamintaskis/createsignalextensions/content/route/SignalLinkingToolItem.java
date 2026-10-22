package com.benjamintaskis.createsignalextensions.content.route;

import com.benjamintaskis.createsignalextensions.content.signal.SignalAspectLinkManager;
import com.benjamintaskis.createsignalextensions.content.signal.SingleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.SingleColourDisplayedAspect;
import com.benjamintaskis.createsignalextensions.content.signal.DoubleColourLightSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.EndOfLineMarkerBlock;
import com.benjamintaskis.createsignalextensions.content.signal.GroundShuntSignalBlock;
import com.benjamintaskis.createsignalextensions.content.signal.MainlineAddonLampBlock;
import com.benjamintaskis.createsignalextensions.content.signal.RepeaterSignalBlock;
import com.benjamintaskis.createsignalextensions.content.theatre.LargeTheatreBoxBlockEntity;
import com.benjamintaskis.createsignalextensions.content.theatre.SmallTheatreBoxBlockEntity;
import com.benjamintaskis.createsignalextensions.content.theatre.ShuntTheatreBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/**
 * Alpha 2e blanket linking workflow:
 *  - sneak-right-click FIRST: select the thing other equipment is linked to/from;
 *  - normal right-click SECOND: link/toggle the clicked equipment against that selection.
 * A new sneak-selection always replaces every previous selection, regardless of type.
 */
public class SignalLinkingToolItem extends Item {
    private static final String SELECTED_TYPE = "LinkSelectionType";
    private static final String TYPE_CREATE_SIGNAL = "create_signal";
    private static final String TYPE_ROUTE_INTERFACE = "route_interface";
    private static final String TYPE_ASPECT_DISPLAY = "aspect_display";

    private static final String X = "SelectedSignalX";
    private static final String Y = "SelectedSignalY";
    private static final String Z = "SelectedSignalZ";
    private static final String DIM = "SelectedSignalDimension";

    // Keep the old following-source field names so existing tools/world testing remain harmless.
    private static final String FOLLOW_MODE = "FollowingSignalLinkMode";
    private static final String FOLLOW_X = "FollowingSourceX";
    private static final String FOLLOW_Y = "FollowingSourceY";
    private static final String FOLLOW_Z = "FollowingSourceZ";
    private static final String FOLLOW_DIM = "FollowingSourceDimension";

    private static final String RI_X = "SelectedRouteInterfaceX";
    private static final String RI_Y = "SelectedRouteInterfaceY";
    private static final String RI_Z = "SelectedRouteInterfaceZ";
    private static final String RI_DIM = "SelectedRouteInterfaceDimension";

    // 0.1.3a: temporary chunk held by this tool while a linking selection is active.
    private static final String FORCE_CHUNK_X = "TemporaryForcedChunkX";
    private static final String FORCE_CHUNK_Z = "TemporaryForcedChunkZ";
    private static final String FORCE_CHUNK_DIM = "TemporaryForcedChunkDimension";
    private static final String FORCE_CHUNK_ACTIVE = "TemporaryForcedChunkActive";

    public SignalLinkingToolItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.create_signal_extensions.signal_linking_tool.summary").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.create_signal_extensions.signal_linking_tool.usage1").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.create_signal_extensions.signal_linking_tool.usage2").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.create_signal_extensions.signal_linking_tool.usage3").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.create_signal_extensions.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }


    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tool = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                if (level instanceof ServerLevel serverLevel)
                    releaseForcedChunk(tool, serverLevel);
                CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> {
                    clearSelection(tag);
                    clearForcedChunkData(tag);
                });
                player.displayClientMessage(Component.literal("Signal Linking Tool reset"), true);
            }
            return InteractionResultHolder.sidedSuccess(tool, level.isClientSide);
        }
        return InteractionResultHolder.pass(tool);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        ItemStack tool = context.getItemInHand();
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        // 0.1.3a: every sneak-right-click moves the tool's temporary force-load
        // ticket to the clicked chunk. This happens even for an otherwise ordinary
        // block, which makes it possible to keep a remote linking endpoint loaded.
        if (!level.isClientSide && player.isShiftKeyDown() && level instanceof ServerLevel serverLevel)
            moveForcedChunk(tool, serverLevel, clicked);

        // CREATE SIGNAL --------------------------------------------------------
        if (level.getBlockEntity(clicked) instanceof SignalBlockEntity) {
            if (!level.isClientSide) {
                if (player.isShiftKeyDown()) {
                    selectCreateSignal(tool, level, clicked);
                    player.displayClientMessage(Component.literal("Selected Create signal: " + coords(clicked)), true);
                } else {
                    String type = selectedType(tool);
                    if (TYPE_CREATE_SIGNAL.equals(type)) {
                        BlockPos source = selectedSignal(tool, level);
                        if (source == null || !(level.getBlockEntity(source) instanceof SignalBlockEntity)) {
                            player.displayClientMessage(Component.literal("Selected Create signal is missing or unloaded"), true);
                        } else if (source.equals(clicked)) {
                            player.displayClientMessage(Component.literal("Selected Create signal: " + coords(clicked)), true);
                        } else if (level instanceof ServerLevel serverLevel) {
                            boolean linked = SignalAspectLinkManager.get(serverLevel).toggleFollowingSignal(source, clicked);
                            player.displayClientMessage(Component.literal((linked
                                    ? "Following Create signal linked: "
                                    : "Following Create signal link removed: ") + coords(clicked)), true);
                        }
                    } else {
                        player.displayClientMessage(Component.literal("Sneak-right-click this Create signal first to select it"), true);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // END OF LINE MARKER ---------------------------------------------------
        // A marker is a following endpoint, never a selectable/source signal.
        if (level.getBlockState(clicked).getBlock() instanceof EndOfLineMarkerBlock) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                if (player.isShiftKeyDown()) {
                    player.displayClientMessage(Component.literal("End of Line Markers are link destinations; select the protecting NSW signal first"), true);
                } else if (TYPE_ASPECT_DISPLAY.equals(selectedType(tool))) {
                    SignalAspectLinkManager manager = SignalAspectLinkManager.get(serverLevel);
                    BlockPos sourceDisplay = followingSource(tool, level);
                    if (sourceDisplay == null || !isAspectDisplay(level, sourceDisplay)) {
                        player.displayClientMessage(Component.literal("Selected source signal is missing or unloaded"), true);
                    } else if (isDistantSignal(level, sourceDisplay)) {
                        player.displayClientMessage(Component.literal("Distant signals must follow an NSW signal, not an End of Line Marker"), true);
                    } else if (manager.linkedSignal(sourceDisplay) == null) {
                        player.displayClientMessage(Component.literal("Selected source NSW signal is not linked to a Create signal"), true);
                    } else {
                        boolean linked = manager.toggleFollowingDisplay(sourceDisplay, clicked);
                        player.displayClientMessage(Component.literal((linked
                                ? "End of Line Marker linked as following endpoint: "
                                : "End of Line Marker following link removed: ") + coords(clicked)), true);
                    }
                } else {
                    player.displayClientMessage(Component.literal("Sneak-right-click the protecting NSW signal first, then right-click this End of Line Marker"), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // NSW / DIAGNOSTIC ASPECT DISPLAY -------------------------------------
        if (isAspectDisplay(level, clicked)) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                // Alpha 5a1: theatre shunts are also ordinary NSW aspect displays, so
                // this branch is reached before the dedicated theatre branch below.
                // Give a selected Route Interface first refusal here so the integral
                // theatre can be linked without disturbing the shunt's Create-signal link.
                if (!player.isShiftKeyDown() && TYPE_ROUTE_INTERFACE.equals(selectedType(tool))
                        && level.getBlockEntity(clicked) instanceof ShuntTheatreBlockEntity shuntTheatre) {
                    BlockPos ri = selectedRouteInterface(tool, level);
                    if (ri == null || !(level.getBlockEntity(ri) instanceof RouteInterfaceBlockEntity)) {
                        player.displayClientMessage(Component.literal("Selected Route Interface is missing or unloaded"), true);
                    } else {
                        shuntTheatre.setLinkedRouteInterface(ri);
                        player.displayClientMessage(Component.literal(
                                "Shunt theatre linked to selected Route Interface at " + coords(ri)), true);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }

                SignalAspectLinkManager manager = SignalAspectLinkManager.get(serverLevel);
                if (player.isShiftKeyDown()) {
                    selectAspectDisplay(tool, level, clicked);
                    player.displayClientMessage(Component.literal(
                            ((level.getBlockState(clicked).getBlock() instanceof SingleColourLightSignalBlock
                                    || level.getBlockState(clicked).getBlock() instanceof DoubleColourLightSignalBlock
                                    || level.getBlockState(clicked).getBlock() instanceof GroundShuntSignalBlock)
                                    ? "Selected NSW signal head: " : "Selected aspect display: ") + coords(clicked)), true);
                } else {
                    String type = selectedType(tool);
                    if (TYPE_CREATE_SIGNAL.equals(type)) {
                        BlockPos signal = selectedSignal(tool, level);
                        if (isDistantSignal(level, clicked)) {
                            player.displayClientMessage(Component.literal(
                                    "Distant signals do not link to Create signals; select the distant, then link its following NSW signal"), true);
                        } else if (signal == null || !(level.getBlockEntity(signal) instanceof SignalBlockEntity)) {
                            player.displayClientMessage(Component.literal("Selected Create signal is missing or unloaded"), true);
                        } else {
                            manager.link(clicked, signal);
                            player.displayClientMessage(Component.literal(
                                    ((level.getBlockState(clicked).getBlock() instanceof SingleColourLightSignalBlock
                                            || level.getBlockState(clicked).getBlock() instanceof DoubleColourLightSignalBlock
                                            || level.getBlockState(clicked).getBlock() instanceof GroundShuntSignalBlock)
                                            ? "NSW signal linked to selected Create signal at "
                                            : "Aspect display linked to selected Create signal at ") + coords(signal)), true);
                        }
                    } else if (TYPE_ASPECT_DISPLAY.equals(type)) {
                        BlockPos sourceDisplay = followingSource(tool, level);
                        BlockPos sourceSignal = sourceDisplay == null ? null : manager.linkedSignal(sourceDisplay);
                        BlockPos targetSignal = manager.linkedSignal(clicked);
                        if (sourceDisplay == null || !isAspectDisplay(level, sourceDisplay)) {
                            player.displayClientMessage(Component.literal("Selected source signal is missing or unloaded"), true);
                        } else if (sourceDisplay.equals(clicked)) {
                            player.displayClientMessage(Component.literal("A signal cannot follow itself"), true);
                        } else if (isDistantSignal(level, sourceDisplay)) {
                            // Alpha 4: distant signals are standalone advance-warning heads.
                            // They do not protect a Create signal; their only required link is
                            // directly to the following NSW signal whose displayed aspect they repeat.
                            boolean linked = manager.toggleFollowingDisplay(sourceDisplay, clicked);
                            if (!linked) {
                                var sourceState = level.getBlockState(sourceDisplay);
                                if (sourceState.getBlock() instanceof SingleColourLightSignalBlock)
                                    level.setBlock(sourceDisplay, sourceState.setValue(
                                            SingleColourLightSignalBlock.ASPECT, SingleColourDisplayedAspect.UNLINKED), 3);
                            }
                            player.displayClientMessage(Component.literal((linked
                                    ? "Following NSW signal linked to distant: "
                                    : "Following NSW signal link removed from distant: ") + coords(clicked)), true);
                            return InteractionResult.sidedSuccess(level.isClientSide);
                        } else if (sourceSignal == null) {
                            player.displayClientMessage(Component.literal("Selected source NSW signal is not linked to a Create signal"), true);
                        } else if (targetSignal == null) {
                            player.displayClientMessage(Component.literal("This NSW signal is not linked to a Create signal"), true);
                        } else {
                            boolean linked = manager.toggleFollowingDisplay(sourceDisplay, clicked);
                            player.displayClientMessage(Component.literal((linked
                                    ? "Following NSW signal linked: "
                                    : "Following NSW signal link removed: ") + coords(clicked)), true);
                        }
                    } else {
                        player.displayClientMessage(Component.literal(
                                "Sneak-right-click a Create signal to link this head, or an NSW signal to create following links"), true);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // ROUTE INTERFACE ------------------------------------------------------
        if (level.getBlockEntity(clicked) instanceof RouteInterfaceBlockEntity routeInterface) {
            if (!level.isClientSide) {
                if (player.isShiftKeyDown()) {
                    selectRouteInterface(tool, level, clicked);
                    player.displayClientMessage(Component.literal("Selected Route Interface: " + coords(clicked)), true);
                } else if (TYPE_CREATE_SIGNAL.equals(selectedType(tool))) {
                    BlockPos signal = selectedSignal(tool, level);
                    if (signal == null || !(level.getBlockEntity(signal) instanceof SignalBlockEntity)) {
                        player.displayClientMessage(Component.literal("Selected Create signal is missing or unloaded"), true);
                    } else {
                        routeInterface.setLinkedSignal(signal);
                        player.displayClientMessage(Component.literal(
                                "Route Interface linked to selected Create signal at " + coords(signal)), true);
                    }
                } else {
                    player.displayClientMessage(Component.literal(
                            "Sneak-right-click a Create signal first, then right-click this Route Interface to link it"), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // INTEGRAL SHUNT THEATRE ---------------------------------------------
        if (level.getBlockEntity(clicked) instanceof ShuntTheatreBlockEntity shuntTheatre) {
            if (!level.isClientSide) {
                if (player.isShiftKeyDown()) {
                    selectAspectDisplay(tool, level, clicked);
                    player.displayClientMessage(Component.literal("Selected NSW theatre shunt signal: " + coords(clicked)), true);
                } else if (TYPE_ROUTE_INTERFACE.equals(selectedType(tool))) {
                    BlockPos ri = selectedRouteInterface(tool, level);
                    if (ri == null || !(level.getBlockEntity(ri) instanceof RouteInterfaceBlockEntity)) player.displayClientMessage(Component.literal("Selected Route Interface is missing or unloaded"), true);
                    else { shuntTheatre.setLinkedRouteInterface(ri); player.displayClientMessage(Component.literal("Shunt theatre linked to selected Route Interface at " + coords(ri)), true); }
                } else if (TYPE_CREATE_SIGNAL.equals(selectedType(tool))) {
                    BlockPos signal=selectedSignal(tool,level); if(signal==null||!(level.getBlockEntity(signal) instanceof SignalBlockEntity)) player.displayClientMessage(Component.literal("Selected Create signal is missing or unloaded"),true);
                    else { SignalAspectLinkManager.get((ServerLevel)level).link(clicked,signal); player.displayClientMessage(Component.literal("NSW theatre shunt linked to selected Create signal at "+coords(signal)),true); }
                } else player.displayClientMessage(Component.literal("Select a Create signal or Route Interface first"),true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // SMALL THEATRE --------------------------------------------------------
        if (level.getBlockEntity(clicked) instanceof SmallTheatreBoxBlockEntity smallTheatreBox) {
            if (!level.isClientSide) {
                if (player.isShiftKeyDown()) {
                    player.displayClientMessage(Component.literal(
                            "Theatre boxes are link destinations; sneak-right-click a Route Interface instead"), true);
                } else if (TYPE_ROUTE_INTERFACE.equals(selectedType(tool))) {
                    BlockPos ri = selectedRouteInterface(tool, level);
                    if (ri == null || !(level.getBlockEntity(ri) instanceof RouteInterfaceBlockEntity)) {
                        player.displayClientMessage(Component.literal("Selected Route Interface is missing or unloaded"), true);
                    } else {
                        smallTheatreBox.setLinkedRouteInterface(ri);
                        player.displayClientMessage(Component.literal(
                                "Small Theatre Box linked to selected Route Interface at " + coords(ri)), true);
                    }
                } else {
                    player.displayClientMessage(Component.literal(
                            "Sneak-right-click a Route Interface first, then right-click this theatre box"), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // LARGE THEATRE --------------------------------------------------------
        if (level.getBlockEntity(clicked) instanceof LargeTheatreBoxBlockEntity theatreBox) {
            if (!level.isClientSide) {
                if (player.isShiftKeyDown()) {
                    player.displayClientMessage(Component.literal(
                            "Theatre boxes are link destinations; sneak-right-click a Route Interface instead"), true);
                } else if (TYPE_ROUTE_INTERFACE.equals(selectedType(tool))) {
                    BlockPos ri = selectedRouteInterface(tool, level);
                    if (ri == null || !(level.getBlockEntity(ri) instanceof RouteInterfaceBlockEntity)) {
                        player.displayClientMessage(Component.literal("Selected Route Interface is missing or unloaded"), true);
                    } else {
                        theatreBox.setLinkedRouteInterface(ri);
                        player.displayClientMessage(Component.literal(
                                "Large Theatre Box linked to selected Route Interface at " + coords(ri)), true);
                    }
                } else if (TYPE_ASPECT_DISPLAY.equals(selectedType(tool))) {
                    BlockPos signal = followingSource(tool, level);
                    if (signal == null || !isMainlineCsaSignal(level, signal)) {
                        player.displayClientMessage(Component.literal("Selected CSA mainline signal is missing or unloaded"), true);
                    } else {
                        theatreBox.setLinkedSignal(signal);
                        player.displayClientMessage(Component.literal(
                                "Large Theatre Box linked directly to CSA signal at " + coords(signal)), true);
                    }
                } else {
                    player.displayClientMessage(Component.literal(
                            "Sneak-right-click a Route Interface or CSA mainline signal first, then right-click this theatre box"), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    private static void moveForcedChunk(ItemStack tool, ServerLevel newLevel, BlockPos clicked) {
        CompoundTag tag = tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int newChunkX = clicked.getX() >> 4;
        int newChunkZ = clicked.getZ() >> 4;
        String newDim = newLevel.dimension().location().toString();

        boolean same = tag.getBoolean(FORCE_CHUNK_ACTIVE)
                && tag.getInt(FORCE_CHUNK_X) == newChunkX
                && tag.getInt(FORCE_CHUNK_Z) == newChunkZ
                && newDim.equals(tag.getString(FORCE_CHUNK_DIM));
        if (same) return;

        releaseForcedChunk(tool, newLevel);
        newLevel.setChunkForced(newChunkX, newChunkZ, true);
        CustomData.update(DataComponents.CUSTOM_DATA, tool, data -> {
            data.putBoolean(FORCE_CHUNK_ACTIVE, true);
            data.putInt(FORCE_CHUNK_X, newChunkX);
            data.putInt(FORCE_CHUNK_Z, newChunkZ);
            data.putString(FORCE_CHUNK_DIM, newDim);
        });
    }

    private static void releaseForcedChunk(ItemStack tool, ServerLevel currentLevel) {
        CompoundTag tag = tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.getBoolean(FORCE_CHUNK_ACTIVE)) return;

        String dimString = tag.getString(FORCE_CHUNK_DIM);
        ResourceLocation dimId = ResourceLocation.tryParse(dimString);
        if (dimId != null) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimId);
            ServerLevel oldLevel = currentLevel.getServer().getLevel(key);
            if (oldLevel != null)
                oldLevel.setChunkForced(tag.getInt(FORCE_CHUNK_X), tag.getInt(FORCE_CHUNK_Z), false);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, tool, SignalLinkingToolItem::clearForcedChunkData);
    }

    private static void clearForcedChunkData(CompoundTag tag) {
        tag.remove(FORCE_CHUNK_ACTIVE);
        tag.remove(FORCE_CHUNK_X);
        tag.remove(FORCE_CHUNK_Z);
        tag.remove(FORCE_CHUNK_DIM);
    }

    private static void clearSelection(CompoundTag tag) {
        tag.remove(SELECTED_TYPE);
        tag.remove(X); tag.remove(Y); tag.remove(Z); tag.remove(DIM);
        tag.remove(RI_X); tag.remove(RI_Y); tag.remove(RI_Z); tag.remove(RI_DIM);
        tag.remove(FOLLOW_X); tag.remove(FOLLOW_Y); tag.remove(FOLLOW_Z); tag.remove(FOLLOW_DIM);
        tag.putBoolean(FOLLOW_MODE, false);
    }

    private static void selectCreateSignal(ItemStack tool, net.minecraft.world.level.Level level, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> {
            clearSelection(tag);
            tag.putString(SELECTED_TYPE, TYPE_CREATE_SIGNAL);
            tag.putInt(X, pos.getX()); tag.putInt(Y, pos.getY()); tag.putInt(Z, pos.getZ());
            tag.putString(DIM, level.dimension().location().toString());
        });
    }

    private static void selectRouteInterface(ItemStack tool, net.minecraft.world.level.Level level, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> {
            clearSelection(tag);
            tag.putString(SELECTED_TYPE, TYPE_ROUTE_INTERFACE);
            tag.putInt(RI_X, pos.getX()); tag.putInt(RI_Y, pos.getY()); tag.putInt(RI_Z, pos.getZ());
            tag.putString(RI_DIM, level.dimension().location().toString());
        });
    }

    private static void selectAspectDisplay(ItemStack tool, net.minecraft.world.level.Level level, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> {
            clearSelection(tag);
            tag.putString(SELECTED_TYPE, TYPE_ASPECT_DISPLAY);
            tag.putBoolean(FOLLOW_MODE, true);
            tag.putInt(FOLLOW_X, pos.getX()); tag.putInt(FOLLOW_Y, pos.getY()); tag.putInt(FOLLOW_Z, pos.getZ());
            tag.putString(FOLLOW_DIM, level.dimension().location().toString());
        });
    }

    private static String selectedType(ItemStack tool) {
        CompoundTag tag = tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getString(SELECTED_TYPE);
    }

    private static boolean isAspectDisplay(net.minecraft.world.level.Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        if (level.getBlockState(pos).getBlock() instanceof SingleColourLightSignalBlock) return true;
        if (level.getBlockState(pos).getBlock() instanceof DoubleColourLightSignalBlock) return true;
        if (level.getBlockState(pos).getBlock() instanceof GroundShuntSignalBlock) return true;
        if (level.getBlockState(pos).getBlock() instanceof MainlineAddonLampBlock) return true;
        if (level.getBlockState(pos).getBlock() instanceof RepeaterSignalBlock) return true;
        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        return id.startsWith("create:") && id.endsWith("nixie_tube");
    }

    private static boolean isMainlineCsaSignal(net.minecraft.world.level.Level level, BlockPos pos) {
        if (pos == null || !level.hasChunkAt(pos)) return false;
        return level.getBlockState(pos).getBlock() instanceof SingleColourLightSignalBlock
                || level.getBlockState(pos).getBlock() instanceof DoubleColourLightSignalBlock;
    }

    private static boolean isDistantSignal(net.minecraft.world.level.Level level, BlockPos pos) {
        if (pos == null || !level.hasChunkAt(pos)) return false;
        return level.getBlockState(pos).getBlock() instanceof SingleColourLightSignalBlock signal
                && signal.physicalType() == SingleColourLightSignalBlock.PhysicalType.DISTANT;
    }

    private static BlockPos followingSource(ItemStack tool, net.minecraft.world.level.Level level) {
        return selectedPos(tool, level, FOLLOW_X, FOLLOW_Y, FOLLOW_Z, FOLLOW_DIM);
    }

    private static BlockPos selectedSignal(ItemStack tool, net.minecraft.world.level.Level level) {
        return selectedPos(tool, level, X, Y, Z, DIM);
    }

    private static BlockPos selectedRouteInterface(ItemStack tool, net.minecraft.world.level.Level level) {
        return selectedPos(tool, level, RI_X, RI_Y, RI_Z, RI_DIM);
    }

    private static BlockPos selectedPos(ItemStack tool, net.minecraft.world.level.Level level,
                                        String x, String y, String z, String dimension) {
        CompoundTag tag = tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(x) || !tag.contains(y) || !tag.contains(z)) return null;
        if (tag.contains(dimension) && !tag.getString(dimension).equals(level.dimension().location().toString())) return null;
        return new BlockPos(tag.getInt(x), tag.getInt(y), tag.getInt(z));
    }

    private static String coords(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
