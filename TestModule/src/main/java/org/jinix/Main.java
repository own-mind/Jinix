package org.jinix;

public class Main {
    public static void main(String[] args) {
        Jinix.load();

        var n = 100000;
        var instance = new Main();
        var start = System.currentTimeMillis();
        var result = instance.test(n);
        System.out.println("Test: " + result + ", time: " + (System.currentTimeMillis() - start) / 1000f);

        start = System.currentTimeMillis();
        result = instance.testNative(n);
        System.out.println("Test native: " + result + ", time: " + (System.currentTimeMillis() - start) / 1000f);
    }

    @Nativize
    public int testNative(int n){
        var result = 0;
        for (int r = 0; r < n; r++) {
            int[] array = new int[10000];
            array[0] = 1;
            for (int i = 2; i < 10000; i++) {
                array[i] = array[i - 1] + array[i - 2];
            }
            result += array[9999];
        }

        return result;
    }

    public int test(int n) {
        var result = 0;
        for (int r = 0; r < n; r++) {
            int[] array = new int[10000];
            array[0] = 1;
            for (int i = 2; i < 10000; i++) {
                array[i] = array[i - 1] + array[i - 2];
            }
            result += array[9999];
        }

        return result;
    }
}