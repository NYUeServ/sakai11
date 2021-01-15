package edu.nyu.classes.seats.brightspace;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import java.security.Signature;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

import java.math.BigInteger;
import java.net.URL;

import org.apache.http.impl.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.util.EntityUtils;


public class JwksStore {

    private URL jwksURL;
    private ConcurrentHashMap<String, StoredKey> keySet;

    class StoredKey {
        public PublicKey key;
        public long expireTime;
    }

    public JwksStore(URL jwksURL) {
        this.jwksURL = jwksURL;
        this.keySet = new ConcurrentHashMap<>();
    }

    private PublicKey getKey(String kid) {
        StoredKey entry = keySet.get(kid);

        if (entry != null && entry.expireTime > System.currentTimeMillis()) {
            return entry.key;
        } else {
            keySet.remove(kid);
            return null;
        }
    }

    private synchronized void refreshKeySet(String desiredKey) {
        if (getKey(desiredKey) != null) {
            // Great news!  Someone refreshed it for you already.
            return;
        }

        try {
            JSON keySetJSON = null;

            try (CloseableHttpClient client = HttpClients.createDefault()) {
                try (CloseableHttpResponse response = client.execute(new HttpGet(jwksURL.toString()))) {
                    String body = EntityUtils.toString(response.getEntity());

                    if (response.getStatusLine().getStatusCode() != 200) {
                        throw new RuntimeException(String.format("Failure fetching Jwks URL %s: %d (%s)",
                                                                 jwksURL,
                                                                 response.getStatusLine().getStatusCode(),
                                                                 body));

                    }

                    keySetJSON = JSON.parse(body);
                }
            }

            for (JSON keyData : keySetJSON.path("keys").asJSONList()) {
                if (!"RSA".equals(keyData.path("kty").asString(""))) {
                    throw new RuntimeException("kty not supported");
                }
                if (!"sig".equals(keyData.path("use").asString(""))) {
                    throw new RuntimeException("use not supported");
                }

                String kid = keyData.path("kid").asStringOrDie();
                Long expiry = keyData.path("exp").asLongOrDie() * 1000;

                if (expiry > System.currentTimeMillis()) {
                    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                    BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(keyData.path("n").asStringOrDie()));
                    BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(keyData.path("e").asStringOrDie()));

                    StoredKey entry = new StoredKey();
                    entry.key = keyFactory.generatePublic(new RSAPublicKeySpec(modulus, exponent));
                    entry.expireTime = expiry;

                    keySet.put(kid, entry);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isSignatureOK(String kid, byte[] payload, byte[] signature) {
        try {
            PublicKey publicKey = getKey(kid);

            if (publicKey == null) {
                refreshKeySet(kid);
                publicKey = getKey(kid);
            }

            if (publicKey == null) {
                throw new RuntimeException("Failure fetching public key with id: " + kid);
            }

            Signature sigcheck = Signature.getInstance("SHA256withRSA");
            sigcheck.initVerify(publicKey);
            sigcheck.update(payload);

            return sigcheck.verify(signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
