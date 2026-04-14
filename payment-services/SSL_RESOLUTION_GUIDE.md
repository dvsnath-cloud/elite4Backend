# SSL Certificate Error Resolution Guide

## Problem
You're encountering SSL certificate verification errors when trying to access:
- `https://d6xcmfyh68wv8.cloudfront.net/docs/whitelists/`
- Razorpay API endpoints
- Other external HTTPS services

Error messages typically look like:
```
PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: 
unable to find valid certification path to requested target
```

## Root Causes

1. **Missing CA Certificates**: Java's truststore doesn't have the required root/intermediate certificates
2. **Certificate Chain Issues**: The certificate chain is incomplete
3. **Expired Certificates**: Certificates in the truststore are expired
4. **Self-signed Certificates**: The server uses self-signed certificates not in the truststore
5. **Hostname Verification**: The certificate doesn't match the requested hostname

## Solutions

### Solution 1: Use System Default Certificates (RECOMMENDED)
The RestClientConfig is already configured to use system default certificates.

**How it works:**
```java
// Uses Java's default truststore which includes all major CAs
javax.net.ssl.TrustManagerFactory tmf = 
    javax.net.ssl.TrustManagerFactory.getInstance(
        javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
tmf.init((java.security.KeyStore) null); // null = system default
```

**For CloudFront:**
- AWS CloudFront uses certificates signed by Amazon's CA
- These are included in Java's default truststore
- Should work out of the box

### Solution 2: Update Java Certificates
If you're using an old Java version, update the certificates:

```bash
# For Windows
java -version

# For Mac/Linux
java -version

# Download latest JDK from oracle.com
# Java 11+: Includes up-to-date root CA certificates
```

### Solution 3: Add Custom CA Certificate
If you need to add a specific certificate:

```bash
# 1. Export the certificate
openssl s_client -connect d6xcmfyh68wv8.cloudfront.net:443 -showcerts

# 2. Copy the certificate to a file (cert.pem)

# 3. Import into Java truststore
keytool -import -alias cloudfront \
  -file cert.pem \
  -keystore cacerts \
  -storepass changeit
```

### Solution 4: Configure JVM System Properties
Add these JVM arguments when starting your application:

```bash
# Option A: Use system truststore explicitly
java -Djavax.net.ssl.trustStore=$JAVA_HOME/lib/security/cacerts \
     -Djavax.net.ssl.trustStorePassword=changeit \
     -jar payment-services-0.0.1-SNAPSHOT.jar

# Option B: Add certificate to default truststore
java -Dcom.sun.net.ssl.checkRevocation=false \
     -jar payment-services-0.0.1-SNAPSHOT.jar

# Option C: Enable debug mode to see certificate chain
java -Djavax.net.debug=ssl:handshake \
     -jar payment-services-0.0.1-SNAPSHOT.jar
```

### Solution 5: Configure for Docker/Kubernetes
In your Dockerfile:

```dockerfile
FROM openjdk:17-jdk-slim

# Update certificates
RUN apt-get update && \
    apt-get install -y ca-certificates && \
    apt-get clean

# OR copy custom certificates
COPY cacerts /usr/local/share/ca-certificates/
RUN update-ca-certificates

COPY payment-services-0.0.1-SNAPSHOT.jar app.jar

# Run with SSL environment variables
ENV JAVA_OPTS="-Djavax.net.ssl.trustStore=/etc/ssl/certs/ca-certificates.crt"
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Debugging Steps

### Step 1: Check Certificate Chain
```bash
# See certificate details
openssl s_client -connect d6xcmfyh68wv8.cloudfront.net:443 -showcerts

# Verify certificate expiration
echo | openssl s_client -servername d6xcmfyh68wv8.cloudfront.net -connect d6xcmfyh68wv8.cloudfront.net:443 2>/dev/null | openssl x509 -noout -dates
```

### Step 2: Check Java Truststore
```bash
# List all certificates in truststore
keytool -list -v -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit

# Check if specific cert exists
keytool -list -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit | grep -i amazon
```

### Step 3: Enable SSL Debug Logging
Add to `application.properties`:

```properties
# Enable SSL/TLS debug logging
logging.level.javax.net.ssl=DEBUG
logging.level.sun.security.ssl=DEBUG
logging.level.sun.security.ssl.SSLImpl=DEBUG
logging.level.com.sun.net.ssl.internal.ssl=DEBUG
```

Then run application and check logs for certificate chain details.

### Step 4: Test HTTPS Connection
```bash
# From Java
wget https://d6xcmfyh68wv8.cloudfront.net/docs/whitelists/

# Or use curl
curl -v https://d6xcmfyh68wv8.cloudfront.net/docs/whitelists/

# Test with specific Java truststore
java -Djavax.net.ssl.trustStore=$JAVA_HOME/lib/security/cacerts \
     -Djavax.net.ssl.trustStorePassword=changeit \
     -cp . TestSSL https://d6xcmfyh68wv8.cloudfront.net
```

## Current Configuration in payment-services

The `RestClientConfig.java` now includes:

1. **System Certificate Loading**
   ```java
   SSLContext sslContext = createSSLContextWithSystemCertificates();
   ```

2. **Fallback Mechanism**
   - If system certificates fail, uses permissive trust manager (for dev/test)
   - Production: Ensure proper certificate validation

3. **Proper Timeouts**
   - Connect timeout: 15 seconds
   - Read timeout: 60 seconds

4. **HTTPS Support**
   ```java
   javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
   ```

## For CloudFront Specifically

CloudFront URLs use certificates from:
- Amazon's CA (Amazon Root CA)
- DigiCert (often intermediate)

These are standard CAs included in Java 11+.

**Action Required:**
1. Update Java to version 11 or higher (if using older version)
2. Ensure your `application.properties` includes proper RestTemplate configuration
3. Test connection to CloudFront URL

## Environment Variables

Set these in your `.env` or deployment configuration:

```bash
# For custom truststore
JAVAX_NET_SSL_TRUSTSTORE=/path/to/cacerts
JAVAX_NET_SSL_TRUSTSTOREPASSWORD=changeit

# For disabling cert revocation check (if needed)
COM_SUN_NET_SSL_CHECKREVOCATION=false

# For SSL debug
JAVA_TOOL_OPTIONS="-Djavax.net.debug=ssl:handshake"
```

## Production Recommendations

1. **Always validate certificates** - Never use permissive trust manager in production
2. **Keep Java updated** - Regular updates include CA certificate updates
3. **Monitor certificate expiration** - Set alerts before expiration
4. **Use proper certificate chain** - Ensure all intermediate certificates are included
5. **Implement certificate pinning** (if needed) - For critical APIs like payment gateways

## Additional Resources

- [Java SSL/TLS Guide](https://docs.oracle.com/javase/tutorial/security/jsse/)
- [CloudFront SSL Certificates](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cnames-and-https-requirements.html)
- [Spring RestTemplate SSL Configuration](https://spring.io/guides/gs/consuming-rest/)
- [keytool Documentation](https://docs.oracle.com/javase/10/tools/keytool.html)

## Quick Checklist

- [ ] Java version is 11 or higher
- [ ] System truststore has been updated (`update-ca-certificates` on Linux)
- [ ] RestClientConfig is properly configured
- [ ] SSL debug logging is enabled (in development)
- [ ] Certificate chain is complete
- [ ] Connection timeouts are set (RestClientConfig does this)
- [ ] No firewall blocking HTTPS (port 443)

## Support

If issues persist:
1. Enable SSL debug logging
2. Check the full certificate chain
3. Verify Java version and truststore
4. Test with curl/wget to isolate Java issue
5. Check application logs for detailed error messages

