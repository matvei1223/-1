package com.example.nbtgiver;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NbtGiverMod implements ModInitializer {
    public static final String MOD_ID = "nbtgiver";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Identifier C2S_REQUEST = Identifier.of(MOD_ID, "request");
    public static final Identifier S2C_RESPONSE = Identifier.of(MOD_ID, "response");
    public static final int MAX_SNBT_LENGTH = 16 * 1024;
    public static final int MAX_NBT_DEPTH = 16;

    private static NbtGiverConfig config;
    private static final Map<UUID, Long> LAST_GIVE = new HashMap<>();

    @Override
    public void onInitialize() {
        config = NbtGiverConfig.load();
        ServerPlayNetworking.registerGlobalReceiver(C2S_REQUEST, (server, player, handler, buf, responseSender) -> {
            RequestMode mode = RequestMode.fromId(buf.readInt());
            String itemId = buf.readString();
            int count = buf.readInt();
            String snbt = buf.readString();

            server.execute(() -> handleRequest(player, mode, itemId, count, snbt));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_GIVE.remove(handler.player.getUuid()));
    }

    private void handleRequest(ServerPlayerEntity player, RequestMode mode, String itemId, int count, String snbt) {
        ValidationResult validation = validateRequest(player, itemId, count, snbt, mode == RequestMode.GIVE);
        if (!validation.ok()) {
            sendResponse(player, false, validation.message());
            return;
        }

        if (mode == RequestMode.VALIDATE) {
            sendResponse(player, true, Text.translatable("screen.nbtgiver.status.ok").getString());
            return;
        }

        Item item = Registries.ITEM.get(Identifier.of(itemId));
        ItemStack stack = new ItemStack(item, count);
        if (validation.nbt() != null && !validation.nbt().isEmpty()) {
            stack.setNbt(validation.nbt());
        }

        boolean inserted = player.getInventory().insertStack(stack);
        if (!inserted && !stack.isEmpty()) {
            player.dropItem(stack, false);
        }

        sendResponse(player, true, Text.translatable("screen.nbtgiver.status.ok").getString());
        LOGGER.info("NBT Giver: {} requested {}x {} with NBT={}, result=success", player.getEntityName(), count, itemId, validation.nbt() != null && !validation.nbt().isEmpty());
    }

    private ValidationResult validateRequest(ServerPlayerEntity player, String itemId, int count, String snbt, boolean enforceCooldown) {
        if (!isAllowed(player)) {
            LOGGER.warn("NBT Giver: {} blocked (not allowed).", player.getEntityName());
            return ValidationResult.error("Нет прав на выдачу");
        }

        if (enforceCooldown) {
            long now = Util.getMeasuringTimeMs();
            long last = LAST_GIVE.getOrDefault(player.getUuid(), 0L);
            if (now - last < config.cooldownSeconds * 1000L) {
                return ValidationResult.error("Слишком часто. Подождите немного.");
            }
            LAST_GIVE.put(player.getUuid(), now);
        }

        Identifier id;
        try {
            id = Identifier.of(itemId);
        } catch (IllegalArgumentException e) {
            return ValidationResult.error("Некорректный item id");
        }

        Item item = Registries.ITEM.get(id);
        if (item == null || item == Registries.ITEM.get(Identifier.of("minecraft", "air"))) {
            return ValidationResult.error("Предмет не найден");
        }

        if (count < 1 || count > config.maxPerRequest) {
            return ValidationResult.error("Количество должно быть от 1 до " + config.maxPerRequest);
        }

        if (!config.allowedItems.isEmpty() && !config.allowedItems.contains(id.toString())) {
            return ValidationResult.error("Предмет не разрешен конфигом");
        }

        NbtCompound filtered = null;
        if (snbt != null && !snbt.isBlank()) {
            if (snbt.length() > MAX_SNBT_LENGTH) {
                return ValidationResult.error("NBT слишком длинный");
            }
            try {
                NbtElement parsed = StringNbtReader.parse(snbt);
                if (!(parsed instanceof NbtCompound compound)) {
                    return ValidationResult.error("NBT должен быть объектом");
                }
                if (!NbtWhitelistFilter.isDepthWithinLimit(compound, MAX_NBT_DEPTH)) {
                    return ValidationResult.error("NBT слишком глубокий");
                }
                Set<String> rootKeys = config.allowedRootKeysSet();
                Set<String> paths = config.allowedPathsSet();
                NbtWhitelistFilter filter = new NbtWhitelistFilter(rootKeys, paths);
                filtered = filter.filter(compound);
                if (filtered.isEmpty() && !compound.isEmpty()) {
                    return ValidationResult.error("NBT не проходит whitelist");
                }
            } catch (Exception e) {
                return ValidationResult.error("Ошибка парсинга NBT: " + e.getMessage());
            }
        }

        return ValidationResult.ok(filtered);
    }

    private boolean isAllowed(ServerPlayerEntity player) {
        if (config.allowOps && player.hasPermissionLevel(2)) {
            return true;
        }
        if (config.allowedPlayers.isEmpty()) {
            return true;
        }
        String name = player.getEntityName();
        String uuid = player.getUuidAsString();
        return config.allowedPlayers.stream().anyMatch(entry -> entry.equalsIgnoreCase(name) || entry.equalsIgnoreCase(uuid));
    }

    private void sendResponse(ServerPlayerEntity player, boolean ok, String message) {
        var buf = PacketByteBufs.create();
        buf.writeBoolean(ok);
        buf.writeString(message);
        ServerPlayNetworking.send(player, S2C_RESPONSE, buf);
    }

    private record ValidationResult(boolean ok, String message, NbtCompound nbt) {
        static ValidationResult ok(NbtCompound nbt) {
            return new ValidationResult(true, "", nbt);
        }

        static ValidationResult error(String message) {
            return new ValidationResult(false, message, null);
        }
    }
}
