package org.slf4j.impl;

import kim.jade.kotlinx.logger.integration.slf4j.KotlinxLoggerFactory;
import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

@SuppressWarnings("ALL")
@Deprecated
public class StaticLoggerBinder implements LoggerFactoryBinder {

    public static String REQUESTED_API_VERSION = "2.0.99";

    private static StaticLoggerBinder SINGLETON = new StaticLoggerBinder();

    @SuppressWarnings("FieldMayBeFinal")
    private static Object KEY = new Object();

    private final KotlinxLoggerFactory factory = new KotlinxLoggerFactory();

    private StaticLoggerBinder() {
    }

    public static StaticLoggerBinder getSingleton() {
        return SINGLETON;
    }

    static void reset() {
        SINGLETON = new StaticLoggerBinder();
    }

    public ILoggerFactory getLoggerFactory() {
        return factory;
    }

    public String getLoggerFactoryClassStr() {
        return KotlinxLoggerFactory.class.getName();
    }
}
