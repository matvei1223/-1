package com.example.nbtgiver.client;

import com.example.nbtgiver.NbtGiverMod;
import com.example.nbtgiver.RequestMode;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextFieldWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Objects;

public class NbtGiverScreen extends Screen {
    private static final int SCREEN_WIDTH = 260;
    private static final int SCREEN_HEIGHT = 220;

    private final Screen parent;

    private TextFieldWidget itemIdField;
    private TextFieldWidget countField;
    private MultilineTextFieldWidget nbtField;
    private Text statusText = Text.empty();
    private boolean statusOk = true;

    private ItemStack previewStack = ItemStack.EMPTY;
    private String lastItemId = "";
    private String lastCount = "";
    private String lastNbt = "";

    public NbtGiverScreen(Screen parent) {
        super(Text.translatable("screen.nbtgiver.title"));
        this.parent = parent;
    }

    public static void addButton(Screen screen, Runnable onClick) {
        int left = (screen.width - 176) / 2;
        int top = (screen.height - 166) / 2;
        ButtonWidget button = ButtonWidget.builder(Text.literal("NBT"), btn -> onClick.run())
            .dimensions(left + 146, top + 4, 26, 12)
            .build();
        screen.addDrawableChild(button);
    }

    @Override
    protected void init() {
        int left = (this.width - SCREEN_WIDTH) / 2;
        int top = (this.height - SCREEN_HEIGHT) / 2;

        itemIdField = new TextFieldWidget(textRenderer, left + 70, top + 24, 170, 18, Text.translatable("screen.nbtgiver.item_id"));
        itemIdField.setText("minecraft:diamond_sword");

        countField = new TextFieldWidget(textRenderer, left + 70, top + 48, 50, 18, Text.translatable("screen.nbtgiver.count"));
        countField.setText("1");

        nbtField = new MultilineTextFieldWidget(textRenderer, left + 20, top + 78, 220, 80, Text.translatable("screen.nbtgiver.nbt"));
        nbtField.setMaxLength(NbtGiverMod.MAX_SNBT_LENGTH);

        addDrawableChild(itemIdField);
        addDrawableChild(countField);
        addDrawableChild(nbtField);

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.nbtgiver.validate"), button -> sendRequest(RequestMode.VALIDATE))
            .dimensions(left + 20, top + 166, 70, 20)
            .build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.nbtgiver.give"), button -> sendRequest(RequestMode.GIVE))
            .dimensions(left + 95, top + 166, 70, 20)
            .build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.nbtgiver.close"), button -> close())
            .dimensions(left + 170, top + 166, 70, 20)
            .build());

        updatePreview();
    }

    @Override
    public void tick() {
        super.tick();
        itemIdField.tick();
        countField.tick();
        nbtField.tick();

        if (!Objects.equals(itemIdField.getText(), lastItemId)
            || !Objects.equals(countField.getText(), lastCount)
            || !Objects.equals(nbtField.getText(), lastNbt)) {
            updatePreview();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = (this.width - SCREEN_WIDTH) / 2;
        int top = (this.height - SCREEN_HEIGHT) / 2;

        context.drawTextWithShadow(textRenderer, title, left + 20, top + 8, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.nbtgiver.item_id"), left + 20, top + 28, 0xA0A0A0);
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.nbtgiver.count"), left + 20, top + 52, 0xA0A0A0);
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.nbtgiver.nbt"), left + 20, top + 66, 0xA0A0A0);

        super.render(context, mouseX, mouseY, delta);

        context.drawItem(previewStack, left + 20, top + 124);
        int statusColor = statusOk ? 0x55FF55 : 0xFF5555;
        context.drawTextWithShadow(textRenderer, statusText, left + 20, top + 192, statusColor);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public void setStatus(Text status, boolean ok) {
        this.statusText = status;
        this.statusOk = ok;
    }

    private void updatePreview() {
        lastItemId = itemIdField.getText();
        lastCount = countField.getText();
        lastNbt = nbtField.getText();

        ItemStack stack = ItemStack.EMPTY;
        try {
            Identifier id = Identifier.of(lastItemId);
            Item item = Registries.ITEM.get(id);
            if (item != null) {
                int count = parseCount(lastCount);
                stack = new ItemStack(item, Math.max(1, Math.min(64, count)));
                if (!lastNbt.isBlank()) {
                    NbtElement parsed = StringNbtReader.parse(lastNbt);
                    if (parsed instanceof NbtCompound compound) {
                        stack.setNbt(compound);
                    }
                }
            }
        } catch (Exception ignored) {
            stack = ItemStack.EMPTY;
        }

        previewStack = stack;
    }

    private int parseCount(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void sendRequest(RequestMode mode) {
        if (MinecraftClient.getInstance().player == null) {
            setStatus(Text.translatable("screen.nbtgiver.status.error", "Нет игрока"), false);
            return;
        }
        var buf = PacketByteBufs.create();
        buf.writeInt(mode.ordinal());
        buf.writeString(itemIdField.getText());
        buf.writeInt(parseCount(countField.getText()));
        buf.writeString(nbtField.getText());
        ClientPlayNetworking.send(NbtGiverMod.C2S_REQUEST, buf);
        setStatus(Text.translatable("screen.nbtgiver.status.info", "Отправлено..."), true);
    }

    private void close() {
        client.setScreen(parent);
    }
}
