package com.example.nbtgiver;

public enum RequestMode {
    VALIDATE,
    GIVE;

    public static RequestMode fromId(int id) {
        RequestMode[] values = values();
        if (id < 0 || id >= values.length) {
            return VALIDATE;
        }
        return values[id];
    }
}
