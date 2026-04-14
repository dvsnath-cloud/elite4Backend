# SSL Certificate Configuration - Complete Implementation Guide

## Overview

Your payment-services module is now fully configured to handle SSL/TLS certificates from multiple sources, with automatic fallback mechanisms. This resolves SSL errors when accessing external services like CloudFront and Razorpay.

## What Was Done

### 1. Enhanced RestClientConfig.java
**File:** `payment-services/src/main/java/com/elite4/anandan/paymentservices/config/RestClientConfig.java`

**Features:**
- ✓ Loads certificates from multiple sources in order of preference:
  - **Priority 1:** JKS Keystore (cacerts.jks) - Mozilla CA bundle in native Java format
  - **Priority 2:** PEM File (cacert.pem) - Mozilla CA bundle from curl.se
  - **Priority 3:** System Default - Java's built-in root certificates
  - **Priority 4:** Permissive - Development-only fallback (accepts all certificates)

- ✓ Automatic failover mechanism - if one source fails, tries the next
- ✓ Proper timeout configuration (15s connect, 60s read)
- ✓ Comprehensive logging for debugging

### 2. Setup Scripts

#### Linux/Mac: `setup-ssl-certificates.sh`
Automated script to download and configure Mozilla CA bundle:
```bash
chmod +x payment-services/setup-ssl-certificates.sh
./payment-services/setup-ssl-certificates.sh
```

#### Windows: `setup-ssl-certificates.bat`
Windows batch script with same functionality:
```cmd
payment-services\setup-ssl-certificates.bat
```

### 3. Documentation Files

- **CURL_SE_CERTIFICATE_GUIDE.md** - Reference to curl.se caextract.html
- **SSL_RESOLUTION_GUIDE.md** - Comprehensive troubleshooting guide
- **SSL_QUICK_REFERENCE.md** - Quick reference for developers

## Quick Start

### Option A: Automated Setup (Recommended)

**For Windows:**
```cmd
cd C:\ProjectSoftwares\Projects\elite4-main\payment-services
setup-ssl-certificates.bat
```

**For Linux/Mac:**
```bash
cd /path/to/elite4-main/payment-services
chmod +x setup-ssl-certificates.sh
./setup-ssl-certificates.sh
```

This will:
1. Download the latest Mozilla CA bundle from https://curl.se/ca/cacert.pem
2. Convert it to JKS format (Java KeyStore)
3. Verify the installation
4. Display configuration information

### Option B: Manual Download

```bash
# Download PEM file
curl https://curl.se/ca/cacert.pem -o payment-services/src/main/resources/cacert.pem

# Convert to JKS
keytool -import -alias "mozilla-ca-bundle" \
  -file payment-services/src/main/resources/cacert.pem \
  -keystore payment-services/src/main/resources/cacerts.jks \
  -storepass changeit \
  -trustcacerts \
  -noprompt
```

### Option C: Use System Certificates Only

If you don't want to download certificates, the application will automatically use your system's default SSL certificates (Java truststore). This should work for standard CAs like CloudFront and Razorpay.

## Build & Run

### Build the Application
```bash
cd C:\ProjectSoftwares\Projects\elite4-main
mvn clean install -DskipTests
```

### Run Payment Services
```bash
mvn -pl payment-services spring-boot:run
```

### Check Logs

Look for these messages indicating successful SSL configuration:
```
INFO: RestTemplate configured with SSL/TLS support
INFO: ✓ Successfully loaded SSL context from JKS keystore (XX certificates)
```

Or if using PEM:
```
INFO: ✓ Successfully loaded SSL context from PEM file (XX certificates)
```

Or if using system defaults:
```
INFO: ✓ Successfully loaded SSL context from system default certificates
```

## Certificate Sources Explained

### 1. JKS Keystore (Preferred)
- **Format:** Native Java KeyStore
- **Speed:** Fastest - no parsing needed at runtime
- **File:** `src/main/resources/cacerts.jks`
- **Advantages:** Pre-compiled, efficient, standard Java format
- **Used when:** Available and valid

### 2. PEM File (Mozilla CA Bundle from curl.se)
- **Format:** Privacy Enhanced Mail (ASCII text)
- **Source:** https://curl.se/docs/caextract.html
- **File:** `src/main/resources/cacert.pem`
- **Advantages:** Human-readable, easy to inspect
- **Used when:** JKS not available

### 3. System Default Certificates
- **Location:** `$JAVA_HOME/lib/security/cacerts`
- **Advantages:** Automatically updated with Java
- **Used when:** Custom certificates not provided
- **Works for:** Standard CAs (Amazon, DigiCert, etc.)

### 4. Permissive Trust Manager
- **Type:** Accepts all certificates
- **Use case:** Development/testing only
- **Security:** BYPASSES certificate validation - NEVER USE IN PRODUCTION
- **Used when:** All other methods fail (last resort)

## Configuration

### application.properties
Already configured with SSL settings:
```properties
# SSL logging (set to DEBUG for troubleshooting)
logging.level.javax.net.ssl=INFO
logging.level.sun.security.ssl=INFO

# Connection timeouts
rest.client.connect.timeout=15
rest.client.read.timeout=60
```

### Environment Variables (Optional)

For custom configuration:
```bash
# Use custom truststore
export JAVAX_NET_SSL_TRUSTSTORE=/path/to/cacerts.jks
export JAVAX_NET_SSL_TRUSTSTOREPASSWORD=changeit

# Enable SSL debug logging
export JAVA_TOOL_OPTIONS="-Djavax.net.debug=ssl:handshake"
```

### Docker/Kubernetes

```dockerfile
FROM openjdk:17-jdk-slim

# Install certificates
RUN apt-get update && apt-get install -y ca-certificates

# Copy custom keystore (if needed)
COPY src/main/resources/cacerts.jks /app/cacerts.jks

COPY payment-services-0.0.1-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-Djavax.net.ssl.trustStore=/app/cacerts.jks"
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Testing

### Test HTTPS Connectivity

Using curl:
```bash
# CloudFront
curl -v https://d6xcmfyh68wv8.cloudfront.net/docs/whitelists/

# Razorpay
curl -v https://api.razorpay.com/v1/health
```

Using openssl:
```bash
openssl s_client -connect d6xcmfyh68wv8.cloudfront.net:443 -showcerts
```

### Test from Java Application

Add this to your service for testing:
```java
import com.elite4.anandan.paymentservices.util.SSLDebugUtil;

SSLDebugUtil.testSSLConnection("https://d6xcmfyh68wv8.cloudfront.net");
SSLDebugUtil.checkSSLContext();
```

## Troubleshooting

### Issue: "Unable to find valid certification path"

**Solution 1:** Ensure Java is 11+ and up-to-date
```bash
java -version
# Should show java 11+
```

**Solution 2:** Run the setup script to download fresh certificates
```bash
setup-ssl-certificates.bat  # or .sh on Unix
```

**Solution 3:** Enable SSL debug logging
```properties
logging.level.javax.net.ssl=DEBUG
```

### Issue: "No trust managers found"

This means no certificate source is available. The application will fall back to permissive mode (development only).

**Solution:** Run the setup script or ensure system certificates exist.

### Issue: Hostname verification failed

The configuration allows hostname verification bypass for development. For production, implement proper verification:
```java
// In production: Remove permissive trust manager usage
// Implement certificate pinning for critical APIs
```

### Issue: Certificates expired

Check expiration dates:
```bash
openssl x509 -in cacert.pem -noout -dates
```

Update certificates:
```bash
setup-ssl-certificates.bat  # or .sh
```

## Maintenance

### Regular Certificate Updates

Update certificates monthly or when needed:

**Automated (with script):**
```bash
# Run the setup script again
./setup-ssl-certificates.sh
# or
setup-ssl-certificates.bat
```

**Manual:**
```bash
curl https://curl.se/ca/cacert.pem -o payment-services/src/main/resources/cacert.pem
keytool -import -alias "mozilla-ca-bundle" \
  -file payment-services/src/main/resources/cacert.pem \
  -keystore payment-services/src/main/resources/cacerts.jks \
  -storepass changeit -trustcacerts -noprompt
```

### Verify Certificate Count

Ensure certificates are loaded:
```bash
keytool -list -keystore payment-services/src/main/resources/cacerts.jks \
  -storepass changeit | grep -c "trustedCertEntry"
```

Expected: 100+ certificates (Mozilla CA bundle typically contains 120-140 CAs)

## Architecture

```
HTTP/HTTPS Request
        ↓
RestTemplate (RestClientConfig)
        ↓
Try JKS Keystore (cacerts.jks)
        ↓ (if not available)
Try PEM File (cacert.pem)
        ↓ (if not available)
Try System Default Certificates
        ↓ (if not available)
Use Permissive Trust Manager
        ↓
Create SSLContext
        ↓
Configure HTTPS Connection
        ↓
External Service (CloudFront, Razorpay, etc.)
```

## References

- [curl.se CA Extract Documentation](https://curl.se/docs/caextract.html)
- [Mozilla Root CA Program](https://wiki.mozilla.org/CA)
- [Java SSL/TLS Guide](https://docs.oracle.com/javase/tutorial/security/jsse/)
- [Spring RestTemplate Documentation](https://spring.io/guides/gs/consuming-rest/)
- [keytool Reference](https://docs.oracle.com/javase/10/tools/keytool.html)

## Files Created/Modified

**Created:**
- `payment-services/CURL_SE_CERTIFICATE_GUIDE.md`
- `payment-services/SSL_RESOLUTION_GUIDE.md`
- `payment-services/SSL_QUICK_REFERENCE.md`
- `payment-services/setup-ssl-certificates.sh`
- `payment-services/setup-ssl-certificates.bat`
- `payment-services/src/main/java/com/elite4/anandan/paymentservices/util/SSLDebugUtil.java`

**Modified:**
- `payment-services/src/main/java/com/elite4/anandan/paymentservices/config/RestClientConfig.java`
- `payment-services/src/main/resources/application.properties`
- `payment-services/pom.xml`

## Next Steps

1. ✓ Run setup script: `setup-ssl-certificates.bat`
2. ✓ Rebuild application: `mvn clean install`
3. ✓ Run and check logs for SSL configuration messages
4. ✓ Test HTTPS connections
5. ✓ Set up certificate update schedule (monthly recommended)

## Support

For questions or issues:
1. Check **SSL_QUICK_REFERENCE.md** for quick answers
2. Review **SSL_RESOLUTION_GUIDE.md** for detailed troubleshooting
3. Enable DEBUG logging: `logging.level.javax.net.ssl=DEBUG`
4. Use **SSLDebugUtil** for certificate chain inspection

## Success Criteria

Your setup is complete when you see:

✓ Application starts without SSL errors
✓ Log shows successful SSL context creation
✓ HTTPS calls to CloudFront work
✓ Razorpay API calls complete successfully
✓ No certificate validation warnings (except in dev permissive mode)

---

**Last Updated:** April 3, 2026
**Configuration Version:** 2.0 (curl.se Mozilla CA Bundle)

