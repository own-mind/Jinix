package org.jinix;

public class Main {
    public static void main(String[] args) {
        Jinix.load();
        System.out.print(new Main().getOne(0, "", null));
    }

    @Nativize
    public int getOne(int a, String b, Object c){
        return 1;
    }
}