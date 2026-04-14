# SSL Configuration Implementation Checklist

## ✓ Phase 1: Code Changes (COMPLETED)

- [x] Enhanced RestClientConfig.java with multi-source certificate loading
- [x] Updated application.properties with SSL configuration options
- [x] Added Apache HttpClient dependency to pom.xml
- [x] Created SSLDebugUtil.java for certificate debugging
- [x] Implemented fallback mechanisms (JKS → PEM → System → Permissive)

## Phase 2: Certificate Setup (NEXT - Choose One)

### Option A: Automated Setup (Recommended - Windows Users)
- [ ] Open Command Prompt as Administrator
- [ ] Navigate to: `C:\ProjectSoftwares\Projects\elite4-main\payment-services`
- [ ] Run: `setup-ssl-certificates.bat`
- [ ] Wait for completion (should see success message)
- [ ] Verify files created:
  - [ ] `src/main/resources/cacert.pem` (should be 150+ KB)
  - [ ] `src/main/resources/cacerts.jks` (should be 200+ KB)

### Option B: Automated Setup (Recommended - Linux/Mac Users)
- [ ] Open Terminal
- [ ] Navigate to: `/path/to/elite4-main/payment-services`
- [ ] Make executable: `chmod +x setup-ssl-certificates.sh`
- [ ] Run: `./setup-ssl-certificates.sh`
- [ ] Wait for completion (should see success message)
- [ ] Verify files created:
  - [ ] `src/main/resources/cacert.pem`
  - [ ] `src/main/resources/cacerts.jks`

### Option C: Manual Setup
- [ ] Download from curl.se:
  ```bash
  curl https://curl.se/ca/cacert.pem -o payment-services/src/main/resources/cacert.pem
  ```
- [ ] Convert to JKS:
  ```bash
  keytool -import -alias "mozilla-ca-bundle" \
    -file payment-services/src/main/resources/cacert.pem \
    -keystore payment-services/src/main/resources/cacerts.jks \
    -storepass changeit \
    -trustcacerts \
    -noprompt
  ```
- [ ] Verify keystore:
  ```bash
  keytool -list -keystore payment-services/src/main/resources/cacerts.jks \
    -storepass changeit | grep -c "trustedCertEntry"
  # Should show: 100+
  ```

### Option D: Use System Certificates Only
- [ ] Skip certificate download
- [ ] Application will automatically use Java default truststore
- [ ] Recommended for: Java 11+ with updated system certificates

## Phase 3: Build & Verify (ESSENTIAL)

### Building
- [ ] Navigate to project root: `C:\ProjectSoftwares\Projects\elite4-main`
- [ ] Run Maven build:
  ```bash
  mvn clean install -DskipTests
  ```
- [ ] Wait for build completion
- [ ] Check for errors (should see "BUILD SUCCESS")

### If Build Fails
- [ ] Check Java version: `java -version` (should be 11+)
- [ ] Check Maven: `mvn -version`
- [ ] Check certificate files exist if you created them
- [ ] Run: `mvn clean` and try again

## Phase 4: Run & Test (VERIFICATION)

### Start Application
- [ ] Run payment services:
  ```bash
  mvn -pl payment-services spring-boot:run
  ```
- [ ] Wait for startup (usually 30-60 seconds)

### Verify SSL Configuration
Look for these log messages:
- [ ] `INFO: RestTemplate configured with SSL/TLS support`
- [ ] `INFO: ✓ Successfully loaded SSL context from...`

Expected log examples:
```
✓ Successfully loaded SSL context from JKS keystore (130 certificates)
✓ Successfully loaded SSL context from PEM file (130 certificates)
✓ Successfully loaded SSL context from system default certificates
```

### If SSL Configuration Failed
- [ ] Check logs for error messages
- [ ] Enable DEBUG logging:
  ```properties
  logging.level.javax.net.ssl=DEBUG
  ```
- [ ] Restart and check detailed logs
- [ ] See troubleshooting section below

## Phase 5: External Connectivity Tests (VALIDATION)

### Test CloudFront Access
- [ ] In separate terminal, run:
  ```bash
  curl -v https://d6xcmfyh68wv8.cloudfront.net/docs/whitelists/
  ```
- [ ] Expected: HTTP 200 (or 302 redirect)
- [ ] Not expected: SSL handshake errors

### Test Razorpay Connection
- [ ] Run:
  ```bash
  curl -v https://api.razorpay.com/v1/health
  ```
- [ ] Expected: HTTP 200
- [ ] Not expected: Certificate verification errors

### Test from Application
- [ ] Add to your code for testing:
  ```java
  import com.elite4.anandan.paymentservices.util.SSLDebugUtil;
  SSLDebugUtil.testSSLConnection("https://d6xcmfyh68wv8.cloudfront.net");
  ```
- [ ] Should see detailed certificate information in logs

## Phase 6: Production Preparation (OPTIONAL)

- [ ] Set up certificate update schedule (monthly)
- [ ] Document certificate location and password
- [ ] Create backup of keystore files
- [ ] Configure monitoring for SSL errors
- [ ] Review and remove permissive trust manager for production
- [ ] Implement certificate pinning (if needed)

## Troubleshooting Checklist

### SSL Certificate Not Found
- [ ] Verify file exists: `src/main/resources/cacert.pem` or `cacerts.jks`
- [ ] Check file size (should be 150+ KB)
- [ ] Rerun setup script

### keytool Not Found
- [ ] Set JAVA_HOME environment variable
- [ ] Add `%JAVA_HOME%\bin` to PATH
- [ ] Restart terminal and try again

### Build Fails with Certificate Error
- [ ] Update Java: `java -version` (ensure 11+)
- [ ] Download certificates with setup script
- [ ] Run `mvn clean` before rebuild

### Application Starts but SSL Still Fails
- [ ] Enable DEBUG logging in application.properties
- [ ] Check application logs for detailed errors
- [ ] Use SSLDebugUtil to inspect certificate chain
- [ ] Ensure firewall allows HTTPS (port 443)

### Certificate Validity Issues
- [ ] Check expiration: `openssl x509 -in cacert.pem -noout -dates`
- [ ] Redownload fresh certificates from curl.se
- [ ] Rerun setup script

## Success Criteria ✓

Your implementation is complete when:

- [ ] Application starts without SSL errors
- [ ] Log shows: `INFO: ✓ Successfully loaded SSL context from...`
- [ ] CloudFront URL is accessible
- [ ] Razorpay API calls work
- [ ] No certificate validation warnings (except dev mode)
- [ ] External HTTPS services respond normally

## Documentation References

Read these files for more information:

1. **SSL_IMPLEMENTATION_COMPLETE.md** - Full implementation guide
2. **SSL_QUICK_REFERENCE.md** - Quick reference for developers
3. **CURL_SE_CERTIFICATE_GUIDE.md** - curl.se reference
4. **SSL_RESOLUTION_GUIDE.md** - Troubleshooting guide

## Quick Start Command Reference

### Windows - Full Setup
```cmd
cd C:\ProjectSoftwares\Projects\elite4-main\payment-services
setup-ssl-certificates.bat
cd ..
mvn clean install -DskipTests
mvn -pl payment-services spring-boot:run
```

### Linux/Mac - Full Setup
```bash
cd /path/to/elite4-main/payment-services
chmod +x setup-ssl-certificates.sh
./setup-ssl-certificates.sh
cd ..
mvn clean install -DskipTests
mvn -pl payment-services spring-boot:run
```

## Support Resources

| Issue | Resource |
|-------|----------|
| SSL Certificate Questions | CURL_SE_CERTIFICATE_GUIDE.md |
| Troubleshooting | SSL_RESOLUTION_GUIDE.md |
| Quick Answers | SSL_QUICK_REFERENCE.md |
| Full Implementation | SSL_IMPLEMENTATION_COMPLETE.md |
| Certificate Debugging | SSLDebugUtil.java |

## Maintenance Schedule

- [ ] Monthly: Check certificate expiration
- [ ] Quarterly: Update certificates from curl.se
- [ ] After Java Updates: Re-validate SSL configuration
- [ ] On Errors: Run setup script and rebuild

## Sign-Off

- [ ] All phases completed
- [ ] All verification tests passed
- [ ] Documentation reviewed
- [ ] Ready for deployment

**Completion Date:** ________________
**Completed By:** ________________
**Notes:** ________________________________________________

---

**Document Version:** 2.0
**Last Updated:** April 3, 2026

