# Configuration Guide

## Quick Configuration Checklist

Before deploying the application, configure these essential settings:

### 1. Email Configuration (REQUIRED)

Edit `src/main/resources/application.properties`:

```properties
# Gmail Configuration
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password

# For Gmail, create an App Password:
# 1. Go to https://myaccount.google.com/apppasswords
# 2. Select app: Mail
# 3. Select device: Other (Custom name)
# 4. Generate and copy the 16-character password
```

### 2. Database Configuration (OPTIONAL)

**Default: H2 In-Memory (Development)**
```properties
spring.datasource.url=jdbc:h2:mem:pdfclassifier
```

**Production: PostgreSQL**
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/pdfclassifier
spring.datasource.username=postgres
spring.datasource.password=your_password
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

### 3. Security Configuration (RECOMMENDED)

Generate a secure JWT secret:
```bash
openssl rand -base64 64
```

Update in application.properties:
```properties
app.jwt.secret=PASTE_GENERATED_SECRET_HERE
```

### 4. File Upload Directories

Create directories:
```bash
mkdir uploads
mkdir processed
```

Or configure custom paths:
```properties
app.upload.dir=/var/app/uploads
app.processed.dir=/var/app/processed
```

## Minimal Configuration for Testing

For quick testing, only configure email:

1. **Create Gmail App Password**
   - Visit: https://myaccount.google.com/apppasswords
   - Generate password
   
2. **Update application.properties**
   ```properties
   spring.mail.username=your-email@gmail.com
   spring.mail.password=16-char-app-password
   ```

3. **Build and Deploy**
   ```bash
   ./build-and-deploy.sh
   ```

## Configuration Examples

### Example 1: Development Setup
```properties
# H2 Database (in-memory)
spring.datasource.url=jdbc:h2:mem:pdfclassifier

# Gmail for testing
spring.mail.username=test@gmail.com
spring.mail.password=test-app-password

# Local directories
app.upload.dir=./uploads
app.processed.dir=./processed
```

### Example 2: Production Setup
```properties
# PostgreSQL Database
spring.datasource.url=jdbc:postgresql://db.example.com:5432/pdfclassifier
spring.datasource.username=pdfapp
spring.datasource.password=SecurePassword123!

# Corporate Email Server
spring.mail.host=smtp.company.com
spring.mail.port=587
spring.mail.username=noreply@company.com
spring.mail.password=EmailPassword123!

# Production directories
app.upload.dir=/var/app/data/uploads
app.processed.dir=/var/app/data/processed

# Secure JWT secret
app.jwt.secret=GeneratedSecretKey...
```

## Troubleshooting Configuration

### Email Issues
**Gmail says "Less secure app access":**
- Enable 2-Step Verification
- Use App Passwords instead of account password

**Connection timeout:**
- Check firewall allows port 587
- Verify SMTP server address

### Database Issues
**H2 Console not accessible:**
```properties
spring.h2.console.enabled=true
```
Access at: `http://localhost:8080/pdf-classifier/h2-console`

**PostgreSQL connection refused:**
- Verify PostgreSQL is running
- Check pg_hba.conf allows connections
- Confirm database exists

### File Upload Issues
**Permission denied:**
```bash
chmod 755 uploads
chmod 755 processed
```

**Directory not found:**
- Create directories before starting
- Use absolute paths in configuration
