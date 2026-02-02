package com.example.nbtgiver.client;

import com.example.nbtgiver.NbtGiverMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class NbtGiverClient implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.nbtgiver.open",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.inventory"
        ));

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof InventoryScreen) {
                NbtGiverScreen.addButton(screen, () -> client.setScreen(new NbtGiverScreen(screen)));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new NbtGiverScreen(null));
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(NbtGiverMod.S2C_RESPONSE, (client, handler, buf, responseSender) -> {
            boolean ok = buf.readBoolean();
            String message = buf.readString();
            client.execute(() -> {
                Screen screen = client.currentScreen;
                if (screen instanceof NbtGiverScreen giverScreen) {
                    Text status = ok ? Text.translatable("screen.nbtgiver.status.ok") : Text.translatable("screen.nbtgiver.status.error", message);
                    giverScreen.setStatus(status, ok);
                }
            });
        });
    }
}
