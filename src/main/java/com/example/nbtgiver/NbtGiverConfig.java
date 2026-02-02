package com.example.nbtgiver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NbtGiverConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean allowOps = true;
    public List<String> allowedPlayers = new ArrayList<>();
    public int cooldownSeconds = 2;
    public int maxPerRequest = 64;
    public List<String> allowedItems = new ArrayList<>();
    public List<String> nbtAllowedRootKeys = new ArrayList<>(Arrays.asList(
        "display",
        "Enchantments",
        "AttributeModifiers",
        "CustomModelData",
        "Unbreakable",
        "Damage",
        "HideFlags",
        "RepairCost"
    ));
    public List<String> nbtAllowedPaths = new ArrayList<>(Arrays.asList(
        "display.Name",
        "display.Lore",
        "display.color"
    ));

    public static NbtGiverConfig load() {
        Path configPath = getPath();
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                NbtGiverConfig config = GSON.fromJson(reader, NbtGiverConfig.class);
                if (config == null) {
                    return new NbtGiverConfig();
                }
                return config;
            } catch (IOException | JsonParseException e) {
                NbtGiverMod.LOGGER.warn("Failed to load config, using defaults.", e);
            }
        }
        NbtGiverConfig config = new NbtGiverConfig();
        config.save();
        return config;
    }

    public void save() {
        Path configPath = getPath();
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            NbtGiverMod.LOGGER.warn("Failed to save config.", e);
        }
    }

    public Set<String> allowedRootKeysSet() {
        return new HashSet<>(nbtAllowedRootKeys);
    }

    public Set<String> allowedPathsSet() {
        return new HashSet<>(nbtAllowedPaths);
    }

    private static Path getPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("nbtgiver.json");
    }
}
