@echo off
REM
REM SSL Certificate Setup Script for Windows
REM Downloads Mozilla CA bundle from curl.se and configures it for Java
REM Reference: https://curl.se/docs/caextract.html
REM

setlocal enabledelayedexpansion

echo ============================================================
echo SSL Certificate Setup for Payment Services (Windows)
echo Reference: https://curl.se/docs/caextract.html
echo ============================================================
echo.

REM Configuration
set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%..\."
set "PAYMENT_SERVICES_DIR=%PROJECT_ROOT%\payment-services"
set "RESOURCES_DIR=%PAYMENT_SERVICES_DIR%\src\main\resources"

set "CACERT_PEM_URL=https://curl.se/ca/cacert.pem"
set "CACERT_PEM_FILE=%RESOURCES_DIR%\cacert.pem"
set "CACERTS_JKS_FILE=%RESOURCES_DIR%\cacerts.jks"
set "KEYSTORE_PASSWORD=changeit"

REM Create resources directory if needed
if not exist "%RESOURCES_DIR%" (
    echo Creating resources directory: %RESOURCES_DIR%
    mkdir "%RESOURCES_DIR%"
)

REM Step 1: Download Mozilla CA Bundle
echo Step 1: Downloading Mozilla CA bundle from curl.se...
echo URL: %CACERT_PEM_URL%
echo.

curl -s "%CACERT_PEM_URL%" -o "%CACERT_PEM_FILE%"

if errorlevel 1 (
    echo X Error: Failed to download certificate file
    pause
    exit /b 1
)

echo Completed successfully
echo.

REM Verify file exists
if not exist "%CACERT_PEM_FILE%" (
    echo X Failed to create certificate file
    pause
    exit /b 1
)

for %%A in ("%CACERT_PEM_FILE%") do set "FILE_SIZE=%%~zA"
echo File size: %FILE_SIZE% bytes
echo.

REM Step 2: Convert PEM to JKS (Java KeyStore)
echo Step 2: Converting PEM to JKS format...
echo.

REM Check if keytool is available
where keytool >nul 2>nul
if errorlevel 1 (
    echo X Error: keytool not found in PATH
    echo   Make sure JAVA_HOME is set correctly
    echo.
    echo   Current JAVA_HOME: %JAVA_HOME%
    echo.
    pause
    exit /b 1
)

echo keytool found: %keytool%
echo.

REM Remove old keystore if it exists
if exist "%CACERTS_JKS_FILE%" (
    echo Removing existing keystore...
    del "%CACERTS_JKS_FILE%"
)

REM Import certificates into keystore
echo Importing certificates into JKS keystore...
keytool -import -alias "mozilla-ca-bundle" ^
    -file "%CACERT_PEM_FILE%" ^
    -keystore "%CACERTS_JKS_FILE%" ^
    -storepass %KEYSTORE_PASSWORD% ^
    -trustcacerts ^
    -noprompt

if errorlevel 1 (
    echo X Failed to create JKS keystore
    pause
    exit /b 1
)

echo.
echo Successfully created JKS keystore: %CACERTS_JKS_FILE%
echo.

REM Step 3: Verify keystore
echo Step 3: Verifying keystore...
echo.

keytool -list -keystore "%CACERTS_JKS_FILE%" -storepass %KEYSTORE_PASSWORD% ^
    | find /c "trustedCertEntry" > nul
if %errorlevel%==0 (
    keytool -list -keystore "%CACERTS_JKS_FILE%" -storepass %KEYSTORE_PASSWORD% ^
        | find /c "trustedCertEntry"
    echo certificates loaded successfully
) else (
    echo Warning: Could not verify certificate count
)
echo.

REM Step 4: Configuration
echo Step 4: Configuration
echo ====================
echo.
echo The following files have been created/updated:
echo   1. %CACERT_PEM_FILE%
echo   2. %CACERTS_JKS_FILE%
echo.
echo Your RestClientConfig will automatically use these files in this order:
echo   1. JKS Keystore (cacerts.jks) - Preferred, fastest
echo   2. PEM File (cacert.pem) - Fallback
echo   3. System Default Certificates - Last resort
echo   4. Permissive Trust Manager - Development only
echo.

REM Step 5: Build instructions
echo Step 5: Build Instructions
echo =========================
echo.
echo Build and run your application:
echo   cd %PROJECT_ROOT%
echo   mvn clean install -DskipTests
echo   mvn -pl payment-services spring-boot:run
echo.
echo Expected log output:
echo   INFO: ^✓ Successfully loaded SSL context from JKS keystore (XXX certificates)
echo.

REM Step 6: Testing
echo Step 6: Testing
echo ==============
echo.
echo Test HTTPS connectivity with curl:
echo   curl -v https://d6xcmfyh68wv8.cloudfront.net/docs/whitelists/
echo   curl -v https://api.razorpay.com/v1/health
echo.

REM Step 7: Summary
echo ============================================================
echo Successfully configured SSL certificates!
echo ============================================================
echo.
echo Next steps:
echo   1. Rebuild your application: mvn clean install
echo   2. Check application logs for SSL configuration messages
echo   3. Test HTTPS connections to external services
echo.
echo To update certificates in the future, run this script again
echo.

pause

