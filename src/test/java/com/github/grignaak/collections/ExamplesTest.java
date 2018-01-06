package com.github.grignaak.collections;

import org.junit.Test;

public class ExamplesTest {
    @Test
    public void run() {
        CowList<String> beatles = new CowArrayList<>();

        beatles.add( "john" );
        beatles.add( "paul" );
        beatles.add( "george" );
        beatles.add( "ringo" );

        CowList<String> famous = beatles.fork();

        beatles.add( "pete" );

        famous.add( "peter" );
        famous.add( "paul" );
        famous.add( "mary" );

        System.out.println("beatles: " + beatles);
        System.out.println("famous: " + famous);
    }
}
