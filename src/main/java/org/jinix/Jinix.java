package org.jinix;

public class Jinix {
    public static void load() {
        System.load(System.getProperty("user.dir") + "/.jinix/libjinix.so");
        init();
    }

    private static native void init();
}
