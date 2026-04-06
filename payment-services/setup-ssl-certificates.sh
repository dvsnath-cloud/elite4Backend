#!/bin/bash
#
# SSL Certificate Setup Script
# Downloads Mozilla CA bundle from curl.se and configures it for Java
# Reference: https://curl.se/docs/caextract.html
#

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PAYMENT_SERVICES_DIR="$PROJECT_ROOT/payment-services"
RESOURCES_DIR="$PAYMENT_SERVICES_DIR/src/main/resources"

CACERT_PEM_URL="https://curl.se/ca/cacert.pem"
CACERT_PEM_FILE="$RESOURCES_DIR/cacert.pem"
CACERTS_JKS_FILE="$RESOURCES_DIR/cacerts.jks"
KEYSTORE_PASSWORD="changeit"

echo "============================================================"
echo "SSL Certificate Setup for Payment Services"
echo "Reference: https://curl.se/docs/caextract.html"
echo "============================================================"
echo ""

# Create resources directory if needed
if [ ! -d "$RESOURCES_DIR" ]; then
    echo "Creating resources directory: $RESOURCES_DIR"
    mkdir -p "$RESOURCES_DIR"
fi

# Step 1: Download Mozilla CA Bundle
echo "Step 1: Downloading Mozilla CA bundle from curl.se..."
echo "URL: $CACERT_PEM_URL"

if command -v curl &> /dev/null; then
    curl -s "$CACERT_PEM_URL" -o "$CACERT_PEM_FILE"
    echo "✓ Downloaded successfully"
else
    echo "✗ Error: curl is not installed"
    exit 1
fi

# Verify download
if [ ! -f "$CACERT_PEM_FILE" ]; then
    echo "✗ Failed to download certificate file"
    exit 1
fi

FILE_SIZE=$(wc -c < "$CACERT_PEM_FILE")
echo "✓ File size: $FILE_SIZE bytes"

# Verify it's a PEM file
if ! grep -q "BEGIN CERTIFICATE" "$CACERT_PEM_FILE"; then
    echo "✗ Downloaded file does not contain valid PEM certificates"
    exit 1
fi

CERT_COUNT=$(grep -c "BEGIN CERTIFICATE" "$CACERT_PEM_FILE")
echo "✓ Found $CERT_COUNT certificates in PEM file"
echo ""

# Step 2: Convert PEM to JKS (Java KeyStore)
echo "Step 2: Converting PEM to JKS format..."

# Check if keytool is available
if ! command -v keytool &> /dev/null; then
    echo "✗ Error: keytool not found in PATH"
    echo "   Make sure JAVA_HOME is set and bin directory is in PATH"
    exit 1
fi

# Remove old keystore if it exists
if [ -f "$CACERTS_JKS_FILE" ]; then
    echo "Removing existing keystore: $CACERTS_JKS_FILE"
    rm -f "$CACERTS_JKS_FILE"
fi

# Import certificates into keystore
echo "Importing certificates into JKS keystore..."
keytool -import -alias "mozilla-ca-bundle" \
    -file "$CACERT_PEM_FILE" \
    -keystore "$CACERTS_JKS_FILE" \
    -storepass "$KEYSTORE_PASSWORD" \
    -trustcacerts \
    -noprompt \
    2>&1 | grep -v "Warning:" || true

if [ ! -f "$CACERTS_JKS_FILE" ]; then
    echo "✗ Failed to create JKS keystore"
    exit 1
fi

echo "✓ Successfully created JKS keystore: $CACERTS_JKS_FILE"
echo ""

# Step 3: Verify keystore
echo "Step 3: Verifying keystore..."

KEYSTORE_CERT_COUNT=$(keytool -list -keystore "$CACERTS_JKS_FILE" \
    -storepass "$KEYSTORE_PASSWORD" 2>&1 | grep "trustedCertEntry" | wc -l)

echo "✓ Keystore contains $KEYSTORE_CERT_COUNT certificates"
echo ""

# Step 4: Display certificate information
echo "Step 4: Certificate Information"
echo "================================"
echo "PEM File: $CACERT_PEM_FILE"
echo "  - Certificates: $CERT_COUNT"
echo "  - Size: $FILE_SIZE bytes"
echo ""
echo "JKS Keystore: $CACERTS_JKS_FILE"
echo "  - Certificates: $KEYSTORE_CERT_COUNT"
echo "  - Password: $KEYSTORE_PASSWORD"
echo ""

# Step 5: Display some certificate details
echo "Step 5: Sample Certificate Details"
echo "==================================="
echo ""
echo "First certificate in PEM file:"
openssl x509 -in "$CACERT_PEM_FILE" -text -noout 2>/dev/null | head -20
echo ""

# Step 6: Configuration
echo "Step 6: Configuration"
echo "===================="
echo ""
echo "The following files have been created/updated:"
echo "  1. $CACERT_PEM_FILE"
echo "  2. $CACERTS_JKS_FILE"
echo ""
echo "Your RestClientConfig will automatically use these files in this order:"
echo "  1. JKS Keystore (cacerts.jks) - Preferred, fastest"
echo "  2. PEM File (cacert.pem) - Fallback"
echo "  3. System Default Certificates - Last resort"
echo "  4. Permissive Trust Manager - Development only"
echo ""

# Step 7: Display usage examples
echo "Step 7: Usage Examples"
echo "===================="
echo ""
echo "Build and run your application:"
echo "  cd $PROJECT_ROOT"
echo "  mvn clean install -DskipTests"
echo "  mvn -pl payment-services spring-boot:run"
echo ""
echo "Expected log output:"
echo "  INFO: ✓ Successfully loaded SSL context from JKS keystore (XXX certificates)"
echo ""

# Step 8: Testing
echo "Step 8: Testing"
echo "=============="
echo ""
echo "Test HTTPS connectivity:"
echo ""
echo "Using curl:"
echo "  curl -v https://d6xcmfyh68wv8.cloudfront.net/docs/whitelists/"
echo "  curl -v https://api.razorpay.com/v1/health"
echo ""
echo "Using openssl:"
echo "  openssl s_client -connect d6xcmfyh68wv8.cloudfront.net:443 -showcerts"
echo ""

# Step 9: Verify certificates are up to date
echo "Step 9: Certificate Validity"
echo "============================"
echo ""
echo "Check certificate expiration dates:"
openssl x509 -in "$CACERT_PEM_FILE" -noout -dates 2>/dev/null | head -2
echo ""

echo "============================================================"
echo "✓ SSL Certificate Setup Complete!"
echo "============================================================"
echo ""
echo "Next steps:"
echo "  1. Rebuild your application: mvn clean install"
echo "  2. Check application logs for SSL configuration messages"
echo "  3. Test HTTPS connections to external services"
echo ""
echo "For updates, run this script again:"
echo "  $0"
echo ""

