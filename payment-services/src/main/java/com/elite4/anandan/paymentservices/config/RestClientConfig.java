package com.elite4.anandan.paymentservices.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Configuration class for REST client beans with SSL/TLS support.
 * Supports multiple certificate sources in order of preference:
 * 1. JKS Keystore (cacerts.jks) - Mozilla CA bundle in native Java format
 * 2. PEM File (cacert.pem) - Mozilla CA bundle in PEM format (from curl.se)
 * 3. System Default - Java default truststore
 * 4. Permissive (Development only) - Accepts all certificates
 */
@Slf4j
@Configuration
public class RestClientConfig {

    private static final String JKS_KEYSTORE_RESOURCE = "cacerts";
    private static final String PEM_CERTIFICATE_RESOURCE = "cacert.pem";
    private static final String KEYSTORE_PASSWORD = "changeit";

    /**
     * Creates a RestTemplate bean for making HTTP/HTTPS requests.
     * Configured with SSL/TLS support using multiple certificate sources.
     *
     * Supports:
     * - CloudFront URLs (https://d6xcmfyh68wv8.cloudfront.net)
     * - Razorpay API (https://api.razorpay.com)
     * - All external HTTPS services
     *
     * @param builder RestTemplateBuilder for constructing RestTemplate
     * @return configured RestTemplate instance with SSL/HTTPS support
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        log.info("═══════════════════════════════════════════════════════════════════");
        log.info("  RestTemplate Bean Initialization");
        log.info("═══════════════════════════════════════════════════════════════════");

        try {
            log.info("Step 1: Loading SSL Context...");
            SSLContext sslContext = loadSSLContext();

            log.info("Step 2: Setting default SSL Socket Factory for HTTPS connections...");
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            log.debug("   ✓ Default SSL Socket Factory set globally");

            log.info("Step 3: Configuring RestTemplate with timeouts and SSL support...");
            RestTemplate restTemplate = builder
                    .setConnectTimeout(Duration.ofSeconds(15))
                    .setReadTimeout(Duration.ofSeconds(60))
                    .build();

            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("✓ RestTemplate configured with SSL/TLS support");
            log.info("  • SSL Context Loaded: YES");
            log.info("  • Connect Timeout: 15 seconds");
            log.info("  • Read Timeout: 60 seconds");
            log.info("  • HTTPS Support: ENABLED");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("═══════════════════════════════════════════════════════════════════");

            return restTemplate;

        } catch (Exception e) {
            log.warn("╔════════════════════════════════════════════════════════════════╗");
            log.warn("║        FAILED to configure SSL context with custom certs       ║");
            log.warn("║         Falling back to default RestTemplate (no SSL config)   ║");
            log.warn("╚════════════════════════════════════════════════════════════════╝");
            log.warn("Error details: {}", e.getMessage());
            log.debug("Full stacktrace:", e);

            log.info("Using default RestTemplate with basic configuration:");
            log.info("  • SSL Context Loaded: NO");
            log.info("  • Connect Timeout: 15 seconds");
            log.info("  • Read Timeout: 60 seconds");
            log.info("  • HTTPS Support: Java Default (may fail if certificates not in system truststore)");

            return builder
                    .setConnectTimeout(Duration.ofSeconds(15))
                    .setReadTimeout(Duration.ofSeconds(60))
                    .build();
        }
    }

    /**
     * Attempts to load SSL context using multiple certificate sources.
     * Tries in order: JKS keystore → PEM file → System default → Permissive
     *
     * @return SSLContext configured with available certificates
     * @throws Exception if all certificate loading methods fail
     */
    private SSLContext loadSSLContext() throws Exception {
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║     SSL Context Initialization - Certificate Source Detection  ║");
        log.info("╚════════════════════════════════════════════════════════════════╝");
        log.debug("Attempting to load SSL context with certificates");

        // Try 1: Load from JKS keystore (preferred)
        try {
            log.info("► ATTEMPT 1: Loading from JKS Keystore (cacerts.jks)");
            SSLContext context = loadSSLContextFromJKS();
            log.info("✓ SUCCESS: Using JKS Keystore as certificate source");
            log.info("╔════════════════════════════════════════════════════════════════╗");
            log.info("║              SSL Context: JKS KEYSTORE - ACTIVE                ║");
            log.info("╚════════════════════════════════════════════════════════════════╝");
            return context;
        } catch (Exception e) {
            log.warn("✗ FAILED: JKS keystore not available");
            log.debug("   Reason: {}", e.getMessage());
        }

        // Try 2: Load from PEM file (Mozilla CA bundle from curl.se)
        try {
            log.info("► ATTEMPT 2: Loading from PEM File (cacert.pem)");
            SSLContext context = loadSSLContextFromPEM();
            log.info("✓ SUCCESS: Using PEM File as certificate source");
            log.info("╔════════════════════════════════════════════════════════════════╗");
            log.info("║              SSL Context: PEM FILE - ACTIVE                    ║");
            log.info("╚════════════════════════════════════════════════════════════════╝");
            return context;
        } catch (Exception e) {
            log.warn("✗ FAILED: PEM certificate file not available");
            log.debug("   Reason: {}", e.getMessage());
        }

        // Try 3: Load system default certificates
        try {
            log.info("► ATTEMPT 3: Loading from System Default Certificates");
            SSLContext context = loadSSLContextFromSystem();
            log.info("✓ SUCCESS: Using System Default Certificates as certificate source");
            log.info("╔════════════════════════════════════════════════════════════════╗");
            log.info("║         SSL Context: SYSTEM DEFAULT CERTIFICATES - ACTIVE      ║");
            log.info("╚════════════════════════════════════════════════════════════════╝");
            return context;
        } catch (Exception e) {
            log.warn("✗ FAILED: System certificates not available");
            log.debug("   Reason: {}", e.getMessage());
        }

        // Try 4: Last resort - permissive trust manager (development only)
        log.warn("► ATTEMPT 4: All certificate sources failed - Using Permissive Trust Manager");
        log.warn("⚠ WARNING: Using permissive trust manager - accepts all certificates");
        log.warn("⚠ THIS IS NOT RECOMMENDED FOR PRODUCTION!");
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║       SSL Context: PERMISSIVE (DEVELOPMENT ONLY) - ACTIVE       ║");
        log.info("║         ⚠ ACCEPTS ALL CERTIFICATES - NOT PRODUCTION READY      ║");
        log.info("╚════════════════════════════════════════════════════════════════╝");
        return createPermissiveSSLContext();
    }

    /**
     * Loads SSL context from JKS keystore (cacerts.jks).
     * This is the preferred method as it's fast and uses native Java format.
     *
     * @return SSLContext loaded from JKS keystore
     * @throws Exception if keystore cannot be loaded
     */
    private SSLContext loadSSLContextFromJKS() throws Exception {
        log.debug("Loading SSL context from JKS keystore: {}", JKS_KEYSTORE_RESOURCE);

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream keystoreInputStream = classLoader.getResourceAsStream(JKS_KEYSTORE_RESOURCE);

        if (keystoreInputStream == null) {
            log.debug("   [JKS] Resource not found in classpath: {}", JKS_KEYSTORE_RESOURCE);
            throw new IllegalArgumentException("JKS keystore not found: " + JKS_KEYSTORE_RESOURCE);
        }

        log.debug("   [JKS] Found resource in classpath");

        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(keystoreInputStream)) {
            log.debug("   [JKS] Creating KeyStore instance (type: JKS)");
            KeyStore keyStore = KeyStore.getInstance("JKS");

            log.debug("   [JKS] Loading keystore from stream with password");
            keyStore.load(bufferedInputStream, KEYSTORE_PASSWORD.toCharArray());

            int certCount = keyStore.size();
            log.debug("   [JKS] Keystore loaded successfully with {} certificates", certCount);

            log.debug("   [JKS] Creating TrustManagerFactory");
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
            tmf.init(keyStore);
            log.debug("   [JKS] TrustManagerFactory initialized with keystore");

            log.debug("   [JKS] Creating SSLContext (protocol: TLSv1.2)");
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());
            log.debug("   [JKS] SSLContext initialized and ready");

            log.info("   [JKS] ✓ Successfully loaded JKS keystore from: {}", JKS_KEYSTORE_RESOURCE);
            log.info("   [JKS] ✓ Certificate count: {}", certCount);
            log.info("   [JKS] ✓ SSL Protocol: TLSv1.2");
            log.info("   [JKS] ✓ Trust Manager: PKIX");
            return sslContext;
        }
    }

    /**
     * Loads SSL context from PEM certificate file (cacert.pem).
     * This is the Mozilla CA bundle from curl.se (https://curl.se/docs/caextract.html)
     *
     * @return SSLContext loaded from PEM file
     * @throws Exception if PEM file cannot be loaded
     */
    private SSLContext loadSSLContextFromPEM() throws Exception {
        log.debug("Loading SSL context from PEM certificate file: {}", PEM_CERTIFICATE_RESOURCE);

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream certInputStream = classLoader.getResourceAsStream(PEM_CERTIFICATE_RESOURCE);

        if (certInputStream == null) {
            log.debug("   [PEM] Resource not found in classpath: {}", PEM_CERTIFICATE_RESOURCE);
            throw new IllegalArgumentException("PEM certificate file not found: " + PEM_CERTIFICATE_RESOURCE);
        }

        log.debug("   [PEM] Found resource in classpath");

        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(certInputStream)) {
            log.debug("   [PEM] Creating empty KeyStore instance (type: JKS)");
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, null);
            log.debug("   [PEM] Empty keystore created");

            log.debug("   [PEM] Creating CertificateFactory for X.509");
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            log.debug("   [PEM] Parsing certificates from PEM file");

            java.util.Collection<? extends Certificate> certificates =
                    certificateFactory.generateCertificates(bufferedInputStream);

            log.debug("   [PEM] Parsed {} certificates from PEM file", certificates.size());

            if (certificates.isEmpty()) {
                log.error("   [PEM] No certificates found in PEM file: {}", PEM_CERTIFICATE_RESOURCE);
                throw new IllegalArgumentException("No certificates found in PEM file: " + PEM_CERTIFICATE_RESOURCE);
            }

            int certificateCount = 0;
            for (Certificate certificate : certificates) {
                if (certificate instanceof X509Certificate) {
                    String alias = "cert-" + certificateCount++;
                    keyStore.setCertificateEntry(alias, certificate);
                    log.debug("   [PEM] Loaded certificate: {} ({})", alias, certificateCount);
                }
            }

            log.debug("   [PEM] Total certificates loaded into keystore: {}", certificateCount);

            log.debug("   [PEM] Creating TrustManagerFactory");
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
            tmf.init(keyStore);
            log.debug("   [PEM] TrustManagerFactory initialized with {} certificates", certificateCount);

            log.debug("   [PEM] Creating SSLContext (protocol: TLSv1.2)");
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());
            log.debug("   [PEM] SSLContext initialized and ready");

            log.info("   [PEM] ✓ Successfully loaded PEM file from: {}", PEM_CERTIFICATE_RESOURCE);
            log.info("   [PEM] ✓ Certificate count: {}", certificateCount);
            log.info("   [PEM] ✓ SSL Protocol: TLSv1.2");
            log.info("   [PEM] ✓ Trust Manager: PKIX");
            return sslContext;
        }
    }

    /**
     * Loads SSL context using system default certificates.
     * These are Java's built-in root CA certificates.
     *
     * @return SSLContext using system default certificates
     * @throws Exception if system certificates cannot be loaded
     */
    private SSLContext loadSSLContextFromSystem() throws Exception {
        log.debug("Loading SSL context from system default certificates");
        log.debug("   [SYSTEM] Location: $JAVA_HOME/lib/security/cacerts");

        log.debug("   [SYSTEM] Getting TrustManagerFactory instance");
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());

        log.debug("   [SYSTEM] Initializing TrustManagerFactory with system default keystore (null)");
        tmf.init((KeyStore) null);

        log.debug("   [SYSTEM] Creating SSLContext (protocol: TLSv1.2)");
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");

        log.debug("   [SYSTEM] Initializing SSLContext with system trust managers");
        sslContext.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());
        log.debug("   [SYSTEM] SSLContext initialized and ready");

        String javaHome = System.getProperty("java.home");
        String truststorePath = javaHome + "/lib/security/cacerts";
        log.info("   [SYSTEM] ✓ Successfully loaded system default certificates");
        log.info("   [SYSTEM] ✓ Truststore location: {}", truststorePath);
        log.info("   [SYSTEM] ✓ SSL Protocol: TLSv1.2");
        log.info("   [SYSTEM] ✓ Trust Managers: {}", tmf.getTrustManagers().length);

        return sslContext;
    }

    /**
     * Creates a permissive SSL context that accepts all certificates.
     * WARNING: ONLY for development/testing - NEVER in production!
     *
     * @return SSL context with permissive trust manager
     * @throws Exception if SSL context creation fails
     */
    private SSLContext createPermissiveSSLContext() throws Exception {
        log.warn("⚠ Creating PERMISSIVE SSL context - THIS ACCEPTS ALL CERTIFICATES!");
        log.warn("⚠ USE ONLY FOR DEVELOPMENT/TESTING - NEVER IN PRODUCTION!");
        log.debug("   [PERMISSIVE] Creating X509TrustManager that accepts all certificates");

        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        log.debug("   [PERMISSIVE] Initialized SSLContext with TLSv1.2");

        sslContext.init(null, new TrustManager[]{createPermissiveTrustManager()},
                new java.security.SecureRandom());
        log.debug("   [PERMISSIVE] SSLContext initialized with permissive trust manager");

        log.info("   [PERMISSIVE] ✓ Permissive SSL context created (DEVELOPMENT MODE)");
        log.info("   [PERMISSIVE] ✓ SSL Protocol: TLSv1.2");
        log.info("   [PERMISSIVE] ✓ Trust Manager: PERMISSIVE (Accepts All)");
        log.warn("   [PERMISSIVE] ⚠ Certificate validation is DISABLED - NOT SECURE!");

        return sslContext;
    }

    /**
     * Creates a permissive X509TrustManager that accepts all certificates.
     *
     * @return X509TrustManager that accepts all certificates
     */
    private X509TrustManager createPermissiveTrustManager() {
        log.debug("   [PERMISSIVE] Creating X509TrustManager instance");
        return new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                log.trace("[PERMISSIVE-TRUSTMGR] getAcceptedIssuers() called - returning empty array");
                return new X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
                log.debug("[PERMISSIVE-TRUSTMGR] checkClientTrusted() called - authType: {}, cert count: {}",
                    authType, certs != null ? certs.length : 0);
                log.debug("[PERMISSIVE-TRUSTMGR] ✓ Client certificate accepted without validation");
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
                log.debug("[PERMISSIVE-TRUSTMGR] checkServerTrusted() called - authType: {}, cert count: {}",
                    authType, certs != null ? certs.length : 0);
                if (certs != null && certs.length > 0) {
                    try {
                        X509Certificate cert = certs[0];
                        log.debug("[PERMISSIVE-TRUSTMGR] Server certificate: {}",
                            cert.getSubjectX500Principal());
                    } catch (Exception e) {
                        log.debug("[PERMISSIVE-TRUSTMGR] Could not extract certificate details");
                    }
                }
                log.debug("[PERMISSIVE-TRUSTMGR] ✓ Server certificate accepted without validation");
            }
        };
    }
}




