package dev.resora.screen;

import dev.resora.ResoraClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ResoraScreen extends Screen {
    private static final int PANEL_COLOR = 0xC0101010;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int SECONDARY_TEXT_COLOR = 0xFFAAAAAA;

    private final Screen parent;
    private Button downloadButton;
    private int panelTop;
    private int buttonWidth;

    public ResoraScreen(Screen parent) {
        super(Component.translatable("resora.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ResoraClient client = ResoraClient.getInstance();
        panelTop = Math.max(20, (height - 202) / 2);
        buttonWidth = Math.min(240, width - 40);
        int buttonX = (width - buttonWidth) / 2;

        downloadButton = addRenderableWidget(Button.builder(
                Component.translatable("resora.button.download"),
                button -> client.saveLatestPack()
        ).bounds(buttonX, panelTop + 64, buttonWidth, 20).build());
        downloadButton.active = client.hasLatestPack();

        addRenderableWidget(Button.builder(
                automaticLabel(client),
                button -> {
                    client.toggleAutomaticSaving();
                    button.setMessage(automaticLabel(client));
                }
        ).bounds(buttonX, panelTop + 90, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(
                replaceLabel(client),
                button -> {
                    client.toggleReplaceExisting();
                    button.setMessage(replaceLabel(client));
                }
        ).bounds(buttonX, panelTop + 114, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(
                normalizeLabel(client),
                button -> {
                    client.toggleNormalizeArchives();
                    button.setMessage(normalizeLabel(client));
                }
        ).bounds(buttonX, panelTop + 138, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("resora.button.done"),
                button -> onClose()
        ).bounds(buttonX, panelTop + 168, buttonWidth, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (downloadButton != null) {
            downloadButton.active = ResoraClient.getInstance().hasLatestPack();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int panelWidth = Math.min(268, width - 20);
        int panelLeft = (width - panelWidth) / 2;
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 198, PANEL_COLOR);
        super.render(graphics, mouseX, mouseY, partialTick);

        ResoraClient client = ResoraClient.getInstance();
        graphics.drawCenteredString(font, title, width / 2, panelTop + 12, TEXT_COLOR);
        graphics.drawCenteredString(font,
                Component.translatable("resora.screen.server", client.getCurrentServerAddress()),
                width / 2, panelTop + 31, SECONDARY_TEXT_COLOR);
        graphics.drawCenteredString(font, trimmed(client.getStatus()), width / 2, panelTop + 46, SECONDARY_TEXT_COLOR);
    }

    private String trimmed(String value) {
        return font.plainSubstrByWidth(value, Math.max(80, buttonWidth));
    }

    private static Component automaticLabel(ResoraClient client) {
        return Component.translatable("resora.setting.automatic", state(client.isAutomaticSaving()));
    }

    private static Component replaceLabel(ResoraClient client) {
        return Component.translatable("resora.setting.replace", state(client.isReplacingExisting()));
    }

    private static Component normalizeLabel(ResoraClient client) {
        return Component.translatable("resora.setting.normalize", state(client.isNormalizingArchives()));
    }

    private static Component state(boolean enabled) {
        return Component.translatable(enabled ? "resora.state.on" : "resora.state.off");
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
