package com.elite4.anandan.paymentservices.util;

import lombok.extern.slf4j.Slf4j;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * Utility class for SSL certificate debugging and testing.
 * Use this to diagnose SSL/TLS issues with external services.
 */
@Slf4j
public class SSLDebugUtil {

    /**
     * Tests SSL connection to a URL and logs certificate information.
     *
     * @param urlString the URL to test (e.g., "https://d6xcmfyh68wv8.cloudfront.net")
     */
    public static void testSSLConnection(String urlString) {
        log.info("Testing SSL connection to: {}", urlString);

        try {
            URL url = new URL(urlString);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("HEAD");

            // Connect and get certificate info
            conn.connect();

            log.info("✓ SSL Connection successful!");
            log.info("Response code: {}", conn.getResponseCode());
            log.info("Response message: {}", conn.getResponseMessage());

            // Get certificate chain
            Certificate[] certificates = conn.getServerCertificates();
            log.info("Certificate chain length: {}", certificates.length);

            for (int i = 0; i < certificates.length; i++) {
                if (certificates[i] instanceof X509Certificate) {
                    X509Certificate cert = (X509Certificate) certificates[i];
                    log.info("\n--- Certificate {} ---", i + 1);
                    logCertificateInfo(cert);
                }
            }

            conn.disconnect();

        } catch (Exception e) {
            log.error("✗ SSL Connection failed: {}", e.getMessage());
            log.error("Error type: {}", e.getClass().getName());
            e.printStackTrace();
        }
    }

    /**
     * Logs detailed certificate information.
     *
     * @param cert the X509Certificate to log
     */
    private static void logCertificateInfo(X509Certificate cert) {
        try {
            log.info("Subject: {}", cert.getSubjectX500Principal());
            log.info("Issuer: {}", cert.getIssuerX500Principal());
            log.info("Serial Number: {}", cert.getSerialNumber());
            log.info("Not Before: {}", cert.getNotBefore());
            log.info("Not After: {}", cert.getNotAfter());
            log.info("Signature Algorithm: {}", cert.getSigAlgName());
            log.info("Public Key Algorithm: {}", cert.getPublicKey().getAlgorithm());

            // Check if certificate is valid now
            try {
                cert.checkValidity();
                log.info("✓ Certificate is valid");
            } catch (Exception e) {
                log.warn("✗ Certificate is NOT valid: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error logging certificate info: {}", e.getMessage());
        }
    }

    /**
     * Checks the current SSL context and trust managers.
     */
    public static void checkSSLContext() {
        log.info("=== SSL Context Information ===");

        try {
            SSLContext sslContext = SSLContext.getDefault();
            log.info("Default SSLContext Protocol: {}", sslContext.getProtocol());

            javax.net.ssl.TrustManagerFactory tmf =
                    javax.net.ssl.TrustManagerFactory.getInstance(
                            javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((java.security.KeyStore) null);

            log.info("Trust managers count: {}", tmf.getTrustManagers().length);

            // Check Java truststore location
            String javaHome = System.getProperty("java.home");
            String truststorePath = javaHome + "/lib/security/cacerts";
            log.info("Expected truststore: {}", truststorePath);

            java.io.File truststoreFile = new java.io.File(truststorePath);
            if (truststoreFile.exists()) {
                log.info("✓ Truststore exists");
                log.info("Truststore size: {} bytes", truststoreFile.length());
            } else {
                log.warn("✗ Truststore not found at expected location");
            }

        } catch (Exception e) {
            log.error("Error checking SSL context: {}", e.getMessage());
        }
    }

    /**
     * Lists Java system properties related to SSL/TLS.
     */
    public static void listSSLSystemProperties() {
        log.info("=== SSL System Properties ===");

        java.util.Properties props = System.getProperties();
        props.keySet().stream()
                .map(Object::toString)
                .filter(key -> key.contains("ssl") || key.contains("trust") || key.contains("key"))
                .sorted()
                .forEach(key -> {
                    String value = System.getProperty(key);
                    // Mask passwords
                    if (key.contains("Password")) {
                        value = "***";
                    }
                    log.info("{}: {}", key, value);
                });
    }

    /**
     * Main method for standalone testing.
     * Usage: java SSLDebugUtil https://d6xcmfyh68wv8.cloudfront.net
     *
     * @param args URL to test
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: SSLDebugUtil <url>");
            System.out.println("Example: SSLDebugUtil https://d6xcmfyh68wv8.cloudfront.net");
            System.exit(1);
        }

        listSSLSystemProperties();
        checkSSLContext();
        testSSLConnection(args[0]);
    }
}

