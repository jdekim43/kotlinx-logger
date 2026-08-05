package org.slf4j.impl;

import kim.jade.kotlinx.logger.integration.slf4j.KotlinxLoggerMdcAdapter;
import org.slf4j.spi.MDCAdapter;

@SuppressWarnings("ALL")
public class StaticMDCBinder {

    public static final StaticMDCBinder SINGLETON = new StaticMDCBinder();

    private StaticMDCBinder() {
    }

    public MDCAdapter getMDCA() {
        return new KotlinxLoggerMdcAdapter();
    }

    public String getMDCAdapterClassStr() {
        return KotlinxLoggerMdcAdapter.class.getName();
    }
}
