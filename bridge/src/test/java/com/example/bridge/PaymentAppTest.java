package com.example.bridge;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PaymentAppTest {
    @Test public void setAlwaysLandsOnTheFirstAppAndSwitchFlipsBetweenTwo() {
        assertEquals("a.pay", PaymentApp.target("b.pay", "a.pay", null));
        assertEquals("a.pay", PaymentApp.target("a.pay", "a.pay", ""));
        assertEquals("b.pay", PaymentApp.target("a.pay", "a.pay", "b.pay"));
        assertEquals("a.pay", PaymentApp.target("b.pay", "a.pay", "b.pay"));
        assertEquals("a.pay", PaymentApp.target("c.other", "a.pay", "b.pay"));
    }

    /** Package names go to `cmd role` as argv; anything else is refused before a shell call. */
    @Test public void onlyPlainPackageNamesPass() {
        assertTrue(PaymentApp.valid("com.garanti.bonusapp"));
        assertTrue(PaymentApp.valid("com.google.android.gms"));
        assertFalse(PaymentApp.valid("gms"));
        assertFalse(PaymentApp.valid("com.x; reboot"));
        assertFalse(PaymentApp.valid("-p com.x"));
        assertFalse(PaymentApp.valid(null));
        assertFalse(PaymentApp.apply("bad name", null));
    }

    @Test public void firstComponentExpandsTheShortClassName() {
        assertEquals("com.google.android.gms/com.google.android.gms.tapandpay.hce.service.TpHceService",
                PaymentApp.firstComponent("com.google.android.gms/.tapandpay.hce.service.TpHceService\ncom.google.android.gms/.pay.X\n"));
        assertEquals("com.garanti.cepsubesi/com.garanti.pfm.HceService",
                PaymentApp.firstComponent("com.garanti.cepsubesi/com.garanti.pfm.HceService"));
        assertNull(PaymentApp.firstComponent("No services found\n"));
        assertEquals("com.a.b", PaymentApp.packageOf("com.a.b/com.a.b.Svc\n"));
    }
}
