package com.example.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;

import org.junit.Test;

/** The app and the bridge both call this class, so one fixed vector pins both sides of the handshake. */
public class BridgeAuthTest {

    private static final String TOKEN = "test-token";
    private static final String ENDPOINT = "127.0.0.1:40000";
    private static final String CLIENT_NONCE = "00112233445566778899aabbccddeeff";
    private static final String SERVER_NONCE = "ffeeddccbbaa99887766554433221100";

    /** Computed independently with Python's hmac module over the same label, role, endpoint and nonces. */
    @Test public void fixedVector() {
        assertEquals("3c73f04bb5927eb03eddb120939fa3ab5cf1ec3aa44486e496d11158d8947df1",
                BridgeAuth.proof(TOKEN, BridgeAuth.ROLE_SERVER, ENDPOINT, CLIENT_NONCE, SERVER_NONCE));
        assertEquals("aa054eaff088a57881e27365fdfba1a7063cb5d5c7a378ee6c346f99b13b7fc9",
                BridgeAuth.proof(TOKEN, BridgeAuth.ROLE_CLIENT, ENDPOINT, CLIENT_NONCE, SERVER_NONCE));
    }

    @Test public void proofsDifferBetweenRolesNoncesAndTokens() {
        String server = BridgeAuth.proof(TOKEN, BridgeAuth.ROLE_SERVER, ENDPOINT, CLIENT_NONCE, SERVER_NONCE);
        assertNotEquals(server, BridgeAuth.proof(TOKEN, BridgeAuth.ROLE_CLIENT, ENDPOINT, CLIENT_NONCE, SERVER_NONCE));
        assertNotEquals(server, BridgeAuth.proof(TOKEN, BridgeAuth.ROLE_SERVER, ENDPOINT, SERVER_NONCE, CLIENT_NONCE));
        assertNotEquals(server, BridgeAuth.proof(TOKEN, BridgeAuth.ROLE_SERVER, ENDPOINT, BridgeAuth.newNonce(), SERVER_NONCE));
        assertNotEquals(server, BridgeAuth.proof("other-token", BridgeAuth.ROLE_SERVER, ENDPOINT, CLIENT_NONCE, SERVER_NONCE));
    }

    /** A relayed connection has another client endpoint: another port, or the same port from another address. */
    @Test public void proofsDifferByEndpointAddressAndPort() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        assertEquals(ENDPOINT, BridgeAuth.endpoint(loopback, 40000));
        String server = BridgeAuth.proof(TOKEN, BridgeAuth.ROLE_SERVER, ENDPOINT, CLIENT_NONCE, SERVER_NONCE);
        assertNotEquals(server, BridgeAuth.proof(TOKEN, BridgeAuth.ROLE_SERVER,
                BridgeAuth.endpoint(loopback, 40001), CLIENT_NONCE, SERVER_NONCE));
        assertNotEquals(server, BridgeAuth.proof(TOKEN, BridgeAuth.ROLE_SERVER,
                BridgeAuth.endpoint(InetAddress.getByName("127.0.0.2"), 40000), CLIENT_NONCE, SERVER_NONCE));
    }

    @Test public void noncesAreFreshHex() {
        String first = BridgeAuth.newNonce();
        assertTrue(BridgeAuth.isNonce(first));
        assertNotEquals(first, BridgeAuth.newNonce());
        assertFalse(BridgeAuth.isNonce(null));
        assertFalse(BridgeAuth.isNonce(first.toUpperCase()));
        assertFalse(BridgeAuth.isNonce(first + "0"));
        assertFalse(BridgeAuth.isNonce(first.substring(1) + "\n"));
    }

    @Test public void matchesOnlyTheExactProof() {
        String proof = BridgeAuth.proof(TOKEN, BridgeAuth.ROLE_CLIENT, ENDPOINT, CLIENT_NONCE, SERVER_NONCE);
        assertTrue(BridgeAuth.matches(proof, proof));
        assertFalse(BridgeAuth.matches(proof, null));
        assertFalse(BridgeAuth.matches(proof, proof.substring(1)));
        assertFalse(BridgeAuth.matches(proof, TOKEN));
    }
}
