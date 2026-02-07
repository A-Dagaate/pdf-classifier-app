# PDF Classifier Application - Deployment Instructions

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Building the Application](#building-the-application)
3. [Deploying to Tomcat](#deploying-to-tomcat)
4. [Configuration](#configuration)
5. [Testing](#testing)
6. [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Software
- **Java Development Kit (JDK) 17 or higher**
  - Download from: https://adoptium.net/
  - Verify installation: `java -version`

- **Apache Maven 3.6 or higher**
  - Download from: https://maven.apache.org/download.cgi
  - Verify installation: `mvn -version`

- **Apache Tomcat 10 or higher**
  - Download from: https://tomcat.apache.org/download-10.cgi
  - Recommended version: Tomcat 10.1.x

### Email Configuration
Before deploying, update the email settings in `application.properties`:
```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
```

**For Gmail:**
1. Enable 2-Step Verification
2. Generate an App Password: https://myaccount.google.com/apppasswords
3. Use the generated password in application.properties

## Building the Application

### 1. Navigate to Project Directory
```bash
cd pdf-classifier-app
```

### 2. Clean and Build WAR File
```bash
mvn clean package
```

This will:
- Compile all Java classes
- Run tests
- Create a WAR file at: `target/pdf-classifier.war`

### 3. Verify Build Success
Look for:
```
[INFO] BUILD SUCCESS
[INFO] Total time: XX s
```

## Deploying to Tomcat

### Method 1: Manual Deployment (Recommended)

#### Step 1: Stop Tomcat
```bash
# Linux/Mac
cd $TOMCAT_HOME/bin
./shutdown.sh

# Windows
cd %TOMCAT_HOME%\bin
shutdown.bat
```

#### Step 2: Deploy WAR File
```bash
# Copy WAR file to Tomcat webapps directory
cp target/pdf-classifier.war $TOMCAT_HOME/webapps/

# Or on Windows
copy target\pdf-classifier.war %TOMCAT_HOME%\webapps\
```

#### Step 3: Start Tomcat
```bash
# Linux/Mac
cd $TOMCAT_HOME/bin
./startup.sh

# Windows
cd %TOMCAT_HOME%\bin
startup.bat
```

#### Step 4: Verify Deployment
- Check Tomcat logs: `$TOMCAT_HOME/logs/catalina.out`
- Wait for message: "Deployment of web application archive ... has finished"
- Access application at: `http://localhost:8080/pdf-classifier`

### Method 2: Tomcat Manager Deployment

#### Step 1: Configure Tomcat Manager
Edit `$TOMCAT_HOME/conf/tomcat-users.xml`:
```xml
<tomcat-users>
  <role rolename="manager-gui"/>
  <role rolename="manager-script"/>
  <user username="admin" password="admin123" roles="manager-gui,manager-script"/>
</tomcat-users>
```

#### Step 2: Deploy via Manager
1. Access Tomcat Manager: `http://localhost:8080/manager/html`
2. Login with credentials from tomcat-users.xml
3. Scroll to "WAR file to deploy"
4. Choose `target/pdf-classifier.war`
5. Click "Deploy"

### Method 3: Maven Tomcat Plugin

#### Step 1: Add Plugin Configuration to pom.xml
```xml
<plugin>
    <groupId>org.apache.tomcat.maven</groupId>
    <artifactId>tomcat7-maven-plugin</artifactId>
    <version>2.2</version>
    <configuration>
        <url>http://localhost:8080/manager/text</url>
        <server>TomcatServer</server>
        <path>/pdf-classifier</path>
    </configuration>
</plugin>
```

#### Step 2: Configure Maven Settings
Edit `~/.m2/settings.xml`:
```xml
<settings>
  <servers>
    <server>
      <id>TomcatServer</id>
      <username>admin</username>
      <password>admin123</password>
    </server>
  </servers>
</settings>
```

#### Step 3: Deploy
```bash
mvn tomcat7:deploy

# Or to redeploy
mvn tomcat7:redeploy
```

## Configuration

### Database Configuration

#### Development (H2 In-Memory)
Default configuration uses H2 in-memory database. No additional setup required.
Access H2 Console: `http://localhost:8080/pdf-classifier/h2-console`

#### Production (PostgreSQL/MySQL)

**For PostgreSQL:**
1. Add dependency to pom.xml:
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

2. Update application.properties:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/pdfclassifier
spring.datasource.username=postgres
spring.datasource.password=your_password
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

**For MySQL:**
1. Add dependency to pom.xml:
```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

2. Update application.properties:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/pdfclassifier
spring.datasource.username=root
spring.datasource.password=your_password
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
```

### File Upload Directories

Create required directories:
```bash
mkdir -p ./uploads
mkdir -p ./processed
```

Or update paths in application.properties:
```properties
app.upload.dir=/var/app/uploads
app.processed.dir=/var/app/processed
```

### Security Configuration

Update JWT secret in application.properties:
```properties
app.jwt.secret=YourVeryLongAndSecureSecretKeyHere_AtLeast256Bits
```

Generate a secure secret:
```bash
openssl rand -base64 64
```

## Testing

### 1. Access the Application
Open browser: `http://localhost:8080/pdf-classifier`

### 2. Register a New User
1. Click "Register here"
2. Fill in username, email, and password
3. Submit registration

### 3. Login
1. Enter username and password
2. Click "Login"

### 4. Enable 2FA (Optional)
1. Go to Settings
2. Click "Enable 2FA"
3. Scan QR code with Google Authenticator or Authy
4. Logout and login again
5. Enter 6-digit code from authenticator app

### 5. Upload PDF
1. From Dashboard, select a PDF file
2. Click "Upload and Process"
3. Wait for processing to complete
4. Check email for results

## Tomcat Configuration Best Practices

### 1. Increase Memory for Large PDFs
Edit `$TOMCAT_HOME/bin/setenv.sh` (create if doesn't exist):
```bash
export CATALINA_OPTS="$CATALINA_OPTS -Xms512m -Xmx2048m -XX:MaxPermSize=256m"
```

Windows (`setenv.bat`):
```bat
set CATALINA_OPTS=%CATALINA_OPTS% -Xms512m -Xmx2048m
```

### 2. Configure Connector for Large Files
Edit `$TOMCAT_HOME/conf/server.xml`:
```xml
<Connector port="8080" protocol="HTTP/1.1"
           connectionTimeout="20000"
           maxPostSize="10485760"
           redirectPort="8443" />
```

### 3. Enable HTTPS (Production)
1. Generate keystore:
```bash
keytool -genkey -alias tomcat -keyalg RSA -keystore keystore.jks
```

2. Configure connector in server.xml:
```xml
<Connector port="8443" protocol="org.apache.coyote.http11.Http11NioProtocol"
           maxThreads="150" SSLEnabled="true">
    <SSLHostConfig>
        <Certificate certificateKeystoreFile="conf/keystore.jks"
                     certificateKeystorePassword="changeit"
                     type="RSA" />
    </SSLHostConfig>
</Connector>
```

## Troubleshooting

### Application Won't Start

**Check Tomcat Logs:**
```bash
tail -f $TOMCAT_HOME/logs/catalina.out
```

**Common Issues:**
1. **Port already in use:**
   - Change port in server.xml or stop conflicting service
   
2. **Out of memory:**
   - Increase heap size in setenv.sh/bat
   
3. **Database connection failed:**
   - Verify database is running
   - Check credentials in application.properties

### File Upload Fails

1. **Check file permissions:**
```bash
chmod 755 ./uploads
chmod 755 ./processed
```

2. **Verify max upload size** in application.properties:
```properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

### Email Not Sending

1. **Verify SMTP settings** in application.properties
2. **Check firewall** allows outbound port 587
3. **Enable "Less secure app access"** for Gmail (or use App Password)
4. **Check application logs** for email errors

### 2FA Issues

1. **Time synchronization:**
   - Ensure server time is accurate
   - TOTP requires synchronized clocks

2. **QR Code not displaying:**
   - Check browser console for errors
   - Verify ZXing library is included

### Database Issues

**H2 Console Access:**
```
URL: http://localhost:8080/pdf-classifier/h2-console
JDBC URL: jdbc:h2:mem:pdfclassifier
Username: sa
Password: (leave blank)
```

**Reset Database:**
```bash
# Stop Tomcat
# Delete H2 database files (if file-based)
# Restart Tomcat
```

## Monitoring and Logs

### Application Logs
Location: `$TOMCAT_HOME/logs/catalina.out`

View in real-time:
```bash
tail -f $TOMCAT_HOME/logs/catalina.out
```

### Application-Specific Logs
Configure logging in `application.properties`:
```properties
logging.level.com.example.pdfclassifier=DEBUG
logging.file.name=logs/pdf-classifier.log
```

## Production Deployment Checklist

- [ ] Update email credentials
- [ ] Configure production database
- [ ] Generate secure JWT secret
- [ ] Set up HTTPS/SSL
- [ ] Configure firewall rules
- [ ] Set up backup for upload/processed directories
- [ ] Configure log rotation
- [ ] Test email delivery
- [ ] Test 2FA with multiple users
- [ ] Load test with multiple PDFs
- [ ] Set up monitoring and alerts
- [ ] Document admin procedures
- [ ] Create backup/restore procedures

## Additional Resources

- Spring Boot Documentation: https://docs.spring.io/spring-boot/
- Apache Tomcat Documentation: https://tomcat.apache.org/tomcat-10.1-doc/
- Google Authenticator: https://github.com/google/google-authenticator
- PDFBox Documentation: https://pdfbox.apache.org/

## Support

For issues or questions:
1. Check application logs
2. Review Tomcat logs
3. Verify configuration settings
4. Check database connectivity
5. Test email configuration separately
