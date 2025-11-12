package org.jinix;

public class Main {
    public static void main(String[] args) {
        Jinix.load();
        var result = new Main().testNative(100);
        System.out.println(result);
    }

    private static final int NUMBER = 100;
    private int counter = 0;

    @Nativize
    public int testNative(int n){
        var sum = 0;

        for (int i = 0; i < n + Main.NUMBER; i++) {
            sum += giveNumber(i, this.counter++);
        }

        return sum;
    }

    private int giveNumber(int i, int counter) {
        return i * counter;
    }
}