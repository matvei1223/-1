package com.example.nbtgiver;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NbtWhitelistFilter {
    private final Set<String> allowedRootKeys;
    private final Set<String> allowedPaths;

    public NbtWhitelistFilter(Set<String> allowedRootKeys, Set<String> allowedPaths) {
        this.allowedRootKeys = new HashSet<>(allowedRootKeys);
        this.allowedPaths = new HashSet<>(allowedPaths);
    }

    public NbtCompound filter(NbtCompound input) {
        NbtCompound output = new NbtCompound();
        for (String key : input.getKeys()) {
            NbtElement value = input.get(key);
            if (value == null) {
                continue;
            }
            if (allowedRootKeys.contains(key)) {
                if ("display".equals(key) && value instanceof NbtCompound displayCompound) {
                    NbtCompound filteredDisplay = filterDisplay(displayCompound);
                    if (!filteredDisplay.isEmpty()) {
                        output.put(key, filteredDisplay);
                    }
                } else {
                    output.put(key, value.copy());
                }
            }
        }
        applyAllowedPaths(input, output);
        return output;
    }

    private NbtCompound filterDisplay(NbtCompound displayCompound) {
        NbtCompound output = new NbtCompound();
        for (String key : displayCompound.getKeys()) {
            String path = "display." + key;
            if (allowedPaths.contains(path)) {
                output.put(key, displayCompound.get(key).copy());
            }
        }
        return output;
    }

    private void applyAllowedPaths(NbtCompound input, NbtCompound output) {
        for (String path : allowedPaths) {
            if (!path.contains(".")) {
                continue;
            }
            String[] parts = path.split("\\.");
            if (parts.length < 2) {
                continue;
            }
            if (!input.contains(parts[0])) {
                continue;
            }
            NbtElement root = input.get(parts[0]);
            if (!(root instanceof NbtCompound rootCompound)) {
                continue;
            }
            NbtCompound targetRoot = output.contains(parts[0]) ? output.getCompound(parts[0]) : new NbtCompound();
            copyAllowedPath(rootCompound, targetRoot, List.of(parts).subList(1, parts.length));
            if (!targetRoot.isEmpty()) {
                output.put(parts[0], targetRoot);
            }
        }
    }

    private void copyAllowedPath(NbtCompound source, NbtCompound target, List<String> remaining) {
        if (remaining.isEmpty()) {
            return;
        }
        String key = remaining.get(0);
        if (!source.contains(key)) {
            return;
        }
        NbtElement element = source.get(key);
        if (remaining.size() == 1) {
            target.put(key, element.copy());
            return;
        }
        if (element instanceof NbtCompound compound) {
            NbtCompound nestedTarget = target.contains(key) ? target.getCompound(key) : new NbtCompound();
            copyAllowedPath(compound, nestedTarget, remaining.subList(1, remaining.size()));
            if (!nestedTarget.isEmpty()) {
                target.put(key, nestedTarget);
            }
        } else if (element instanceof NbtList list) {
            target.put(key, list.copy());
        }
    }

    public static boolean isDepthWithinLimit(NbtElement element, int limit) {
        return depth(element, 0) <= limit;
    }

    private static int depth(NbtElement element, int current) {
        if (element == null) {
            return current;
        }
        int next = current + 1;
        if (element instanceof NbtCompound compound) {
            int max = next;
            for (String key : compound.getKeys()) {
                max = Math.max(max, depth(compound.get(key), next));
            }
            return max;
        }
        if (element instanceof NbtList list) {
            int max = next;
            for (NbtElement child : list) {
                max = Math.max(max, depth(child, next));
            }
            return max;
        }
        return next;
    }
}
