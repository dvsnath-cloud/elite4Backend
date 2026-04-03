package com.elite4.anandan.paymentservices;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

@SpringBootApplication
@EnableAsync
@EnableRetry
public class PaymentServiceApplication {
    public static void main(String[] args) {
        initSslTrustStore();
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

    /**
     * Fetches the Razorpay SSL cert chain and adds it to the JVM truststore
     * before any beans are created. Fixes PKIX path building failures.
     */
    private static void initSslTrustStore() {
        try {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);

            // Load existing certs from JVM cacerts
            String cacertsPath = System.getProperty("java.home") + "/lib/security/cacerts";
            try (InputStream is = java.nio.file.Files.newInputStream(java.nio.file.Path.of(cacertsPath))) {
                KeyStore defaultKs = KeyStore.getInstance(KeyStore.getDefaultType());
                defaultKs.load(is, "changeit".toCharArray());
                var aliases = defaultKs.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    ks.setCertificateEntry(alias, defaultKs.getCertificate(alias));
                }
            }

            // Fetch Razorpay cert chain using a temporary trust-all context
            SSLContext tempCtx = SSLContext.getInstance("TLS");
            tempCtx.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String t) {}
                public void checkServerTrusted(X509Certificate[] c, String t) {}
            }}, new java.security.SecureRandom());

            try (SSLSocket socket = (SSLSocket) tempCtx.getSocketFactory().createSocket("api.razorpay.com", 443)) {
                socket.startHandshake();
                Certificate[] certs = socket.getSession().getPeerCertificates();
                for (int i = 0; i < certs.length; i++) {
                    ks.setCertificateEntry("razorpay-" + i, certs[i]);
                }
                System.out.println("SSL: Added " + certs.length + " Razorpay certificates to truststore");
            }

            // Set as default SSL context
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(null, tmf.getTrustManagers(), null);
            SSLContext.setDefault(sslCtx);
            HttpsURLConnection.setDefaultSSLSocketFactory(sslCtx.getSocketFactory());
        } catch (Exception e) {
            System.err.println("SSL: Could not update truststore — " + e.getMessage());
        }
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
