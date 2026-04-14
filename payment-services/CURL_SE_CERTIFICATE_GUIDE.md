# SSL Certificate Configuration - Using curl.se Mozilla CA Bundle

This guide explains how to properly configure SSL certificates for your Java application using the Mozilla CA bundle from curl.se/docs/caextract.html

## What is curl.se/docs/caextract.html?

The curl documentation provides:
- **mk-ca-bundle.pl** - A Perl script to extract Mozilla's root certificates
- **cacert.pem** - Pre-built Mozilla CA bundle (updated regularly)
- Instructions for converting to Java KeyStore format

## Current Status

Your project already has a Mozilla CA bundle approach. Now we'll optimize it.

## Solution: Download Fresh Mozilla CA Bundle

### Step 1: Download Latest cacert.pem
```bash
# Option 1: Download directly from curl.se
curl https://curl.se/ca/cacert.pem -o payment-services/src/main/resources/cacert.pem

# Option 2: Download from Mozilla (alternative)
curl https://raw.githubusercontent.com/mozilla/nixpkgs/master/pkgs/data/cacert/cacert.pem \
  -o payment-services/src/main/resources/cacert.pem

# Verify the download
ls -lh payment-services/src/main/resources/cacert.pem
```

### Step 2: Convert PEM to JKS (Java KeyStore)
```bash
# Create a Java keystore from the PEM file
keytool -import -alias "mozilla-ca-bundle" \
  -file payment-services/src/main/resources/cacert.pem \
  -keystore payment-services/src/main/resources/cacerts.jks \
  -storepass changeit \
  -trustcacerts \
  -noprompt

# Verify the keystore
keytool -list -v -keystore payment-services/src/main/resources/cacerts.jks \
  -storepass changeit | head -50
```

### Step 3: Update application.properties
```properties
# Point to the Mozilla CA bundle (JKS format)
javax.net.ssl.trustStore=${SSL_TRUSTSTORE:file:${project.basedir}/src/main/resources/cacerts.jks}
javax.net.ssl.trustStorePassword=${SSL_TRUSTSTORE_PASSWORD:changeit}
```

## Implementation Options

### Option A: Use PEM Bundle (Current Approach)
- Pros: Lightweight, easy to update
- Cons: Requires parsing at runtime
- Use when: You want to manage certificates in application

### Option B: Use JKS Keystore (Recommended)
- Pros: Native Java format, no parsing needed, faster loading
- Cons: Requires initial conversion
- Use when: You want optimal performance

### Option C: Use System Default (Already Configured)
- Pros: Automatically updated with Java
- Cons: May be outdated in older Java versions
- Use when: You trust system truststore

## Implementing Option B (Recommended)

### Step 1: Update RestClientConfig to Load JKS
Create a new configuration that loads the Mozilla CA bundle:

```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    try {
        // Load Mozilla CA bundle from JKS keystore
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream is = getClass().getResourceAsStream("/cacerts.jks")) {
            keyStore.load(is, "changeit".toCharArray());
        }
        
        // Create SSL context with the keystore
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(keyStore);
        
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
        
        // Configure RestTemplate with SSL context
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        
        return builder
            .setConnectTimeout(Duration.ofSeconds(15))
            .setReadTimeout(Duration.ofSeconds(60))
            .build();
            
    } catch (Exception e) {
        log.warn("Failed to load Mozilla CA bundle: {}. Using system defaults.", 
            e.getMessage());
        return builder.build();
    }
}
```

## Automated Certificate Update Script

Create a script to automatically keep certificates updated:

```bash
#!/bin/bash
# update-certificates.sh

CERT_URL="https://curl.se/ca/cacert.pem"
CERT_FILE="payment-services/src/main/resources/cacert.pem"
KEYSTORE_FILE="payment-services/src/main/resources/cacerts.jks"
KEYSTORE_PASS="changeit"

echo "Downloading latest Mozilla CA bundle from curl.se..."
curl -s "$CERT_URL" -o "$CERT_FILE"

if [ $? -eq 0 ]; then
    echo "✓ Downloaded successfully"
    
    # Convert to JKS
    echo "Converting PEM to JKS format..."
    rm -f "$KEYSTORE_FILE"
    keytool -import -alias "mozilla-ca-bundle" \
        -file "$CERT_FILE" \
        -keystore "$KEYSTORE_FILE" \
        -storepass "$KEYSTORE_PASS" \
        -trustcacerts \
        -noprompt
    
    if [ $? -eq 0 ]; then
        echo "✓ Successfully created JKS keystore"
        
        # Verify
        CERT_COUNT=$(keytool -list -keystore "$KEYSTORE_FILE" \
            -storepass "$KEYSTORE_PASS" | grep "trustedCertEntry" | wc -l)
        echo "✓ Loaded $CERT_COUNT certificates"
        
        echo "✓ Certificate update complete!"
    else
        echo "✗ Failed to create JKS keystore"
        exit 1
    fi
else
    echo "✗ Failed to download certificates"
    exit 1
fi
```

Usage:
```bash
chmod +x update-certificates.sh
./update-certificates.sh
```

## For Docker/Kubernetes

### Dockerfile for Automated Certificate Updates
```dockerfile
FROM openjdk:17-jdk-slim

# Install curl for certificate download
RUN apt-get update && apt-get install -y curl ca-certificates && apt-get clean

# Download latest Mozilla CA bundle
RUN curl https://curl.se/ca/cacert.pem -o /usr/local/share/ca-certificates/cacert.pem

# Convert to JKS for Java
RUN keytool -import -alias "mozilla-ca-bundle" \
    -file /usr/local/share/ca-certificates/cacert.pem \
    -keystore /usr/local/openjdk-17/lib/security/cacerts \
    -storepass changeit \
    -trustcacerts \
    -noprompt

COPY payment-services-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Kubernetes CronJob for Certificate Updates
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: certificate-update
spec:
  schedule: "0 0 * * 0"  # Weekly update
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: cert-updater
            image: curlimages/curl:latest
            command:
            - /bin/sh
            - -c
            - |
              curl https://curl.se/ca/cacert.pem -o /certs/cacert.pem
              echo "Certificates updated at $(date)"
          volumes:
          - name: certs
            persistentVolumeClaim:
              claimName: certs-pvc
          restartPolicy: OnFailure
```

## Best Practices

1. **Regular Updates**
   - Update certificates monthly or when needed
   - Use the curl.se official source
   - Verify certificate count remains reasonable

2. **Verification**
   ```bash
   # Check certificate validity
   openssl x509 -in cacert.pem -text -noout
   
   # Check expiration
   openssl x509 -in cacert.pem -noout -dates
   ```

3. **Version Control**
   ```bash
   # Don't store large PEM files in git
   # Instead, store JKS keystore or auto-download
   echo "cacert.pem" >> .gitignore
   git add payment-services/src/main/resources/cacerts.jks
   ```

4. **Monitoring**
   - Alert when certificates expire
   - Log certificate loading
   - Monitor SSL handshake failures

## Testing Your Configuration

```bash
# Test with CloudFront
curl -v https://d6xcmfyh68wv8.cloudfront.net/docs/whitelists/ \
  --cacert payment-services/src/main/resources/cacert.pem

# Test Razorpay connection
curl -v https://api.razorpay.com/v1/health \
  --cacert payment-services/src/main/resources/cacert.pem
```

## Troubleshooting

### Issue: "unable to find valid certification path"
```bash
# Verify certificate is in bundle
grep -i "cloudfront\|amazon" payment-services/src/main/resources/cacert.pem

# Check if certificate is expired
openssl x509 -in cacert.pem -noout -dates
```

### Issue: "keytool error: java.lang.Exception"
```bash
# Ensure PEM file is valid
file payment-services/src/main/resources/cacert.pem

# Try with more verbose output
keytool -import -alias "mozilla-ca-bundle" \
  -file payment-services/src/main/resources/cacert.pem \
  -keystore payment-services/src/main/resources/cacerts.jks \
  -storepass changeit \
  -trustcacerts \
  -noprompt -v
```

## Summary

| Method | Pros | Cons | Recommended |
|--------|------|------|-------------|
| System Default | Automatic | May be outdated | ✓ For Java 11+ |
| PEM Bundle | Easy to manage | Runtime parsing | For flexibility |
| JKS Keystore | Fast, native | Requires conversion | ✓ For performance |
| Custom PEM | Full control | Manual management | For special cases |

## References

- [curl.se CA Extract Documentation](https://curl.se/docs/caextract.html)
- [Mozilla Root CA List](https://wiki.mozilla.org/CA)
- [Java KeyTool Documentation](https://docs.oracle.com/javase/10/tools/keytool.html)
- [Java SSL/TLS Configuration](https://docs.oracle.com/javase/tutorial/security/jsse/)

