package com.example.bridge;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mutual challenge–response for the control socket, shared by the bridge ({@link EventServer}) and
 * the app's client so the two can never compute a proof differently.
 *
 * <p>The token itself never travels: each side sends a fresh nonce, the server proves it holds the
 * token first, and the client answers only a proof that verified. Something squatting on the port
 * while no bridge is bound therefore learns nothing it can log in with. Proofs are domain-separated
 * by role, so asking the real bridge for a server proof never yields a client proof, and bound to the
 * client's end of the connection, so a squatter that kept the app's connection open after the real
 * bridge bound the port cannot replay the handshake to it: its own connection comes from another
 * endpoint, since the app's 4-tuple cannot exist twice.
 */
public final class BridgeAuth {

    private BridgeAuth() { }

    public static final String ROLE_SERVER = "server";
    public static final String ROLE_CLIENT = "client";

    private static final String LABEL = "keysnap-bridge-auth-v1";
    private static final int NONCE_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 16 random bytes as lowercase hex, fresh for every connection. */
    public static String newNonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        RANDOM.nextBytes(bytes);
        return hex(bytes);
    }

    /** Only what {@link #newNonce} produces; anything else ends the handshake. */
    public static boolean isNonce(String value) {
        return value != null && value.matches("[0-9a-f]{" + (NONCE_BYTES * 2) + "}");
    }

    /**
     * The client's end of a connection as both sides write it into the proof: the client from its
     * socket's local address and port, the bridge from the accepted socket's remote ones. The address
     * counts too, or a relay could connect from 127.0.0.2 with the app's port number.
     */
    public static String endpoint(InetAddress address, int port) {
        return address.getHostAddress() + ":" + port;
    }

    /**
     * HMAC-SHA256 keyed with the token over label, role, the client's {@link #endpoint} and both
     * nonces, one per line. Roles are fixed words, endpoints carry no newline and nonces are
     * fixed-length hex, so no two inputs share a message.
     */
    public static String proof(String token, String role, String clientEndpoint, String clientNonce, String serverNonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String message = LABEL + "\n" + role + "\n" + clientEndpoint + "\n" + clientNonce + "\n" + serverNonce;
            return hex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute the handshake proof", e);
        }
    }

    /** Constant-time comparison of a received proof with the expected one. */
    public static boolean matches(String expected, String received) {
        return received != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return out.toString();
    }
}
