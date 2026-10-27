package com.panoramakit.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Panorama picker with a live preview.
 *
 * <p>The screen draws the actual panorama cube map behind itself, so the preview on the right is the
 * very texture the main menu will use. Hovering (or keyboard-focusing) an entry previews it without
 * saving anything; clicking applies it; closing the screen without clicking restores the saved
 * selection.
 *
 * <p>Every control carries hover text explaining what it does, the layout only uses as much space as
 * the two panels need, and the overlay plus caption fade in briefly instead of snapping so opening
 * the screen and moving between entries feels smooth.
 */
public final class PanoramaSelectScreen extends Screen {
    private static final int MARGIN = 12;
    private static final int GAP = 10;
    private static final int ROW_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 38;
    private static final int FOOTER_HEIGHT = 38;
    private static final int CAPTION_HEIGHT = 30;
    private static final int BUTTON_HEIGHT = 18;

    /** How long the dim overlay and caption take to fade in, and to settle after a preview change. */
    private static final int OPEN_FADE_MILLIS = 180;
    private static final int PREVIEW_FADE_MILLIS = 140;

    private static final int DIM = 0xB4121720;
    private static final int CAPTION_BACKDROP = 0x9C0B0F16;
    private static final int FRAME = 0x70FFFFFF;
    private static final int ACCENT = 0xFFFFC107;
    private static final int TEXT_BRIGHT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAEB8C4;

    private final @Nullable Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private @Nullable PanoramaList list;
    private @Nullable Row focusedRow;
    private String previewed = "";
    private long previewChangedAt = System.currentTimeMillis();

    private int paneLeft;
    private int paneTop;
    private int paneRight;
    private int paneBottom;

    public PanoramaSelectScreen(@Nullable Screen parent) {
        super(Component.translatable("panoramakit.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Force the first tick to (re-)apply the configured panorama, so the preview always matches
        // what the main menu currently shows.
        this.previewed = "";
        this.previewChangedAt = System.currentTimeMillis();

        int top = HEADER_HEIGHT;
        int bottom = Math.max(top + 48, this.height - FOOTER_HEIGHT);
        int panelWidth = Math.max(140, Math.min(260, (this.width - 3 * MARGIN - GAP) / 2));
        this.paneLeft = MARGIN + panelWidth + GAP;
        this.paneTop = top;
        this.paneRight = Math.max(this.paneLeft + 80, this.width - MARGIN);
        this.paneBottom = bottom;

        List<String> names = PanoramaLibrary.listPanoramas();

        PanoramaList panoramaList = new PanoramaList(this.minecraft, panelWidth, bottom - top, ROW_HEIGHT);
        this.list = panoramaList;
        panoramaList.updateSizeAndPosition(panelWidth, bottom - top, MARGIN, top);
        panoramaList.add(new Row(PanoramaLibrary.VANILLA, Component.translatable("panoramakit.screen.vanilla"),
                Component.translatable("panoramakit.screen.vanilla.tooltip")));
        Component entryTooltip = Component.translatable("panoramakit.screen.entry.tooltip");
        for (String name : names) {
            panoramaList.add(new Row(name, Component.literal(name), entryTooltip));
        }
        if (names.isEmpty()) {
            panoramaList.add(new Row(
                    Component.translatable("panoramakit.screen.empty"),
                    Component.translatable("panoramakit.screen.empty.hint")));
        }
        this.addRenderableWidget(panoramaList);

        int folderWidth = 96;
        int refreshWidth = 62;
        int doneWidth = 56;
        int spacing = 6;
        int totalWidth = folderWidth + refreshWidth + doneWidth + spacing * 2;
        int x = (this.width - totalWidth) / 2;
        int y = this.height - 28;

        this.addRenderableWidget(Button.builder(Component.translatable("panoramakit.button.open_folder"), button -> openFolder())
                .bounds(x, y, folderWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("panoramakit.button.open_folder.tooltip")))
                .build());
        x += folderWidth + spacing;
        this.addRenderableWidget(Button.builder(Component.translatable("panoramakit.button.refresh"), button -> this.rebuildWidgets())
                .bounds(x, y, refreshWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("panoramakit.button.refresh.tooltip")))
                .build());
        x += refreshWidth + spacing;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(x, y, doneWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("panoramakit.button.done.tooltip")))
                .build());
    }

    @Override
    public void tick() {
        if (this.list == null) {
            return;
        }

        String target = null;
        Row hovered = this.list.hoveredRow();
        if (hovered != null && hovered.name != null) {
            target = hovered.name;
        }

        Row focused = this.focusedRow;
        if (target == null && focused != null && focused.name != null && focused.isButtonFocused()) {
            target = focused.name;
        }

        if (target == null) {
            target = PanoramaConfig.selectedPanorama();
        }

        if (!target.equals(this.previewed)) {
            this.previewed = target;
            this.previewChangedAt = System.currentTimeMillis();
            PanoramaLibrary.preview(target);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        float intro = easeOut(elapsed(this.openedAt, OPEN_FADE_MILLIS));
        float settle = easeOut(elapsed(this.previewChangedAt, PREVIEW_FADE_MILLIS));

        // The panorama itself: the same cube map texture the main menu renders.
        this.extractPanorama(graphics, a);

        // Dim everything that is not the preview pane, so the pane reads as a preview window.
        int dim = withAlpha(DIM, intro);
        graphics.fill(0, 0, this.width, this.paneTop, dim);
        graphics.fill(0, this.paneBottom, this.width, this.height, dim);
        graphics.fill(0, this.paneTop, this.paneLeft, this.paneBottom, dim);
        graphics.fill(this.paneRight, this.paneTop, this.width, this.paneBottom, dim);

        int frame = withAlpha(FRAME, intro);
        graphics.fill(this.paneLeft - 1, this.paneTop - 1, this.paneRight + 1, this.paneTop, frame);
        graphics.fill(this.paneLeft - 1, this.paneBottom, this.paneRight + 1, this.paneBottom + 1, frame);
        graphics.fill(this.paneLeft - 1, this.paneTop, this.paneLeft, this.paneBottom, frame);
        graphics.fill(this.paneRight, this.paneTop, this.paneRight + 1, this.paneBottom, frame);

        Component subtitle = Component.translatable("panoramakit.screen.subtitle");
        graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 7, withAlpha(TEXT_BRIGHT, intro), true);
        graphics.text(this.font, subtitle, (this.width - this.font.width(subtitle)) / 2, 19, withAlpha(TEXT_DIM, intro), false);

        String name = this.previewed.isEmpty() ? PanoramaConfig.selectedPanorama() : this.previewed;
        boolean inUse = Objects.equals(name, PanoramaConfig.selectedPanorama());
        Component caption = Component.translatable(
                inUse ? "panoramakit.screen.preview.in_use" : "panoramakit.screen.preview.previewing",
                displayName(name));
        Component hint = Component.translatable(
                inUse ? "panoramakit.screen.hint.in_use" : "panoramakit.screen.hint.preview");

        graphics.fill(this.paneLeft + 1, this.paneBottom - CAPTION_HEIGHT, this.paneRight, this.paneBottom,
                withAlpha(CAPTION_BACKDROP, intro));
        // Clipped to the pane: panorama folder names can be long, and the caption must not spill out.
        graphics.enableScissor(this.paneLeft + 6, this.paneBottom - CAPTION_HEIGHT, this.paneRight - 4, this.paneBottom);
        graphics.text(this.font, caption, this.paneLeft + 10, this.paneBottom - 24, withAlpha(TEXT_BRIGHT, settle), true);
        graphics.text(this.font, hint, this.paneLeft + 10, this.paneBottom - 13, withAlpha(TEXT_DIM, settle), false);
        graphics.disableScissor();
    }

    @Override
    public void onClose() {
        // Closing without applying restores whatever is saved in the config; hovering only previews.
        String selected = PanoramaConfig.selectedPanorama();
        if (!selected.equals(this.previewed)) {
            this.previewed = selected;
            PanoramaLibrary.select(selected);
        }
        ClientCompat.setScreen(this.parent);
    }

    private void choose(String name) {
        PanoramaKitClient.selectPanorama(name);
        this.previewed = name;
        this.previewChangedAt = System.currentTimeMillis();
    }

    private static Component displayName(String name) {
        return PanoramaLibrary.VANILLA.equals(name)
                ? Component.translatable("panoramakit.screen.vanilla")
                : Component.literal(name);
    }

    private static void openFolder() {
        Path folder = PanoramaLibrary.panoramasDirectory();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            return;
        }
        ClientCompat.openFolder(folder);
    }

    private static float elapsed(long startMillis, int durationMillis) {
        float progress = (System.currentTimeMillis() - startMillis) / (float) durationMillis;
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    private static float easeOut(float progress) {
        float inverse = 1.0F - progress;
        return 1.0F - inverse * inverse * inverse;
    }

    /** Scales the alpha channel of an ARGB colour; used for the fade ins. */
    private static int withAlpha(int argb, float factor) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * factor);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** The list of selectable panoramas on the left side of the screen. */
    private static final class PanoramaList extends ContainerObjectSelectionList<Row> {
        private PanoramaList(Minecraft minecraft, int width, int height, int itemHeight) {
            super(minecraft, width, height, 0, itemHeight);
            this.centerListVertically = false;
        }

        @Override
        public int getRowWidth() {
            return Math.max(80, this.getWidth() - 8);
        }

        void add(Row row) {
            this.addEntry(row);
        }

        @Nullable
        Row hoveredRow() {
            return this.getHovered();
        }
    }

    /** One row: a panorama name that previews on hover and applies on click. */
    private final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final @Nullable String name;
        private final @Nullable Button button;
        private final Component text;

        private @Nullable Component secondary;

        private Row(@Nullable String name, Component text, @Nullable Component tooltip) {
            this.name = name;
            this.text = text;
            this.button = name == null
                    ? null
                    : Button.builder(text, pressed -> choose(name))
                            .tooltip(tooltip == null ? null : Tooltip.create(tooltip))
                            .build();
        }

        /** Placeholder row: no widget, just two short lines of text that fit the list panel. */
        private Row(Component text, Component secondary) {
            this(null, text, null);
            this.secondary = secondary;
        }

        boolean isButtonFocused() {
            return this.button != null && this.button.isFocused();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            int x = this.getContentX();
            int width = this.getContentWidth();
            Button rowButton = this.button;

            if (rowButton == null) {
                graphics.text(PanoramaSelectScreen.this.font, this.text, x, this.getContentY() + 4, TEXT_DIM, false);
                if (this.secondary != null) {
                    graphics.text(PanoramaSelectScreen.this.font, this.secondary, x, this.getContentY() + 15, TEXT_DIM, false);
                }
                return;
            }

            if (Objects.equals(this.name, PanoramaConfig.selectedPanorama())) {
                graphics.fill(this.getX(), this.getY() + 3, this.getX() + 3, this.getY() + this.getHeight() - 3, ACCENT);
                x += 5;
                width -= 5;
            }

            if (rowButton.isFocused()) {
                PanoramaSelectScreen.this.focusedRow = this;
            }

            rowButton.setRectangle(width, this.getContentHeight(), x, this.getContentY());
            rowButton.extractRenderState(graphics, mouseX, mouseY, a);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.button == null ? List.of() : List.of(this.button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.button == null ? List.of() : List.of(this.button);
        }
    }
}
