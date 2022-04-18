package com.lscode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Hello world!
 *
 */
public class App 
{
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );
        Map a = new HashMap<>();
        a.put("a", "121");
        System.out.println( "end" );

        long np = (long)(-12);

        logger.info("Hello world.{}", new Random().nextInt(1));
    }
}
