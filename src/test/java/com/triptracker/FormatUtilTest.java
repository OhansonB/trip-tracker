package com.triptracker;

import org.junit.Test;
import static org.junit.Assert.*;

public class FormatUtilTest {

    @Test
    public void testSmallNumbers() {
        assertEquals("0", FormatUtil.shortenNumber(0));
        assertEquals("1", FormatUtil.shortenNumber(1));
        assertEquals("9999", FormatUtil.shortenNumber(9999));
    }

    @Test
    public void testThousands() {
        assertEquals("10k", FormatUtil.shortenNumber(10000));
        assertEquals("15k", FormatUtil.shortenNumber(15000));
        assertEquals("100k", FormatUtil.shortenNumber(100000));
        assertEquals("1000k", FormatUtil.shortenNumber(999999));
    }

    @Test
    public void testMillions() {
        assertEquals("1m", FormatUtil.shortenNumber(1000000));
        assertEquals("2.5m", FormatUtil.shortenNumber(2500000));
        assertEquals("1000m", FormatUtil.shortenNumber(999999999));
    }

    @Test
    public void testBillions() {
        assertEquals("1b", FormatUtil.shortenNumber(1000000000));
        assertEquals("2.147b", FormatUtil.shortenNumber(2147483647));
    }

    @Test
    public void testNegativeNumbers() {
        // Negative numbers should just return the string representation
        assertEquals("-1", FormatUtil.shortenNumber(-1));
    }
}
