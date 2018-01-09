package com.github.grignaak.collections;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

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

        assertThat(beatles, is(asList("john", "paul", "george", "ringo", "pete")));
        assertThat(famous, is(asList("john", "paul", "george", "ringo", "peter", "paul", "mary")));
    }
}
