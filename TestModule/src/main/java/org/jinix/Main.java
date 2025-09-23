package org.jinix;

public class Main {
    public static void main(String[] args) {
        System.out.print(new Main().getHelloWorld());
    }

    @Nativize
    public String getHelloWorld(){
        return "Hello World";
    }

    @Nativize
    public void a(int[] arr){

    }

    @Nativize
    public String b(int a, Main c){
        return "Hello World";
    }
}