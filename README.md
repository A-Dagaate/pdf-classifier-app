# PDF Classifier Application

A Spring Boot web application featuring two-factor authentication, PDF upload, machine learning classification, and email notifications.

## Features

- ✅ **Two-Factor Authentication (2FA)** using TOTP (Google Authenticator compatible)
- ✅ **Secure User Authentication** with BCrypt password encryption
- ✅ **PDF File Upload** with validation
- ✅ **ML-Based Classification** of PDF documents (text and images)
- ✅ **Email Notifications** with processed file attachments
- ✅ **Responsive Web Interface** using Thymeleaf templates
- ✅ **RESTful Architecture**
- ✅ **Deployable as WAR** to Apache Tomcat

## Technology Stack

### Backend
- **Spring Boot 3.2.0** - Application framework
- **Spring Security** - Authentication and authorization
- **Spring Data JPA** - Database access
- **H2/PostgreSQL/MySQL** - Database options
- **Spring Mail** - Email functionality

### Security
- **BCrypt** - Password hashing
- **TOTP** - Time-based One-Time Password (2FA)
- **JWT** - Session management
- **ZXing** - QR code generation

### PDF Processing
- **Apache PDFBox** - PDF text and image extraction
- **Apache Tika** - Content type detection

### Frontend
- **Thymeleaf** - Server-side template engine
- **CSS3** - Styling
- **Vanilla JavaScript** - Client-side validation

### Deployment
- **Apache Tomcat 10+** - Application server
- **Maven** - Build tool

## Project Structure

```
pdf-classifier-app/
├── src/
│   ├── main/
│   │   ├── java/com/example/pdfclassifier/
│   │   │   ├── PdfClassifierApplication.java
│   │   │   ├── config/
│   │   │   │   └── SecurityConfig.java
│   │   │   ├── controller/
│   │   │   │   └── MainController.java
│   │   │   ├── entity/
│   │   │   │   ├── User.java
│   │   │   │   └── PdfDocument.java
│   │   │   ├── repository/
│   │   │   │   ├── UserRepository.java
│   │   │   │   └── PdfDocumentRepository.java
│   │   │   ├── security/
│   │   │   │   ├── CustomUserDetailsService.java
│   │   │   │   └── TwoFactorAuthenticationFilter.java
│   │   │   └── service/
│   │   │       ├── UserService.java
│   │   │       ├── TotpService.java
│   │   │       ├── PdfProcessingService.java
│   │   │       └── EmailService.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── static/
│   │       │   └── css/
│   │       │       └── style.css
│   │       └── templates/
│   │           ├── login.html
│   │           ├── register.html
│   │           ├── verify-2fa.html
│   │           ├── dashboard.html
│   │           └── settings.html
│   └── test/
├── pom.xml
├── DEPLOYMENT.md
└── README.md
```

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+
- Apache Tomcat 10+

### 1. Clone or Create Project
```bash
mkdir pdf-classifier-app
cd pdf-classifier-app
```

### 2. Configure Email
Edit `src/main/resources/application.properties`:
```properties
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
```

### 3. Build Application
```bash
mvn clean package
```

### 4. Deploy to Tomcat
```bash
cp target/pdf-classifier.war $TOMCAT_HOME/webapps/
$TOMCAT_HOME/bin/startup.sh
```

### 5. Access Application
Open browser: `http://localhost:8080/pdf-classifier`

## Usage Guide

### 1. Register Account
1. Navigate to application URL
2. Click "Register here"
3. Fill in username, email, and password
4. Submit registration

### 2. Login
1. Enter credentials
2. Click "Login"

### 3. Enable 2FA (Recommended)
1. Go to Settings
2. Click "Enable 2FA"
3. Scan QR code with Google Authenticator/Authy
4. Save backup codes
5. Test by logging out and back in

### 4. Upload PDF
1. From Dashboard, click "Choose File"
2. Select a PDF file (max 10MB)
3. Click "Upload and Process"
4. Wait for processing notification

### 5. Receive Results
- Check email for classification results
- Download processed file from email attachment
- View document history on Dashboard

## Machine Learning Classification

The application includes a placeholder ML classification implementation. To integrate actual ML capabilities:

### Option 1: External ML API
Integrate with cloud services:
- **AWS Rekognition** - Image classification
- **Google Vision API** - Document analysis
- **Azure Computer Vision** - Text and image processing

### Option 2: Embedded ML Library
Use Java ML libraries:
- **DeepLearning4J** - Deep learning for Java
- **TensorFlow Java** - TensorFlow bindings
- **Weka** - Machine learning algorithms

### Option 3: Python ML Service
Call external Python service:
```java
// Example REST call to Python ML service
RestTemplate restTemplate = new RestTemplate();
ClassificationRequest request = new ClassificationRequest(content);
ClassificationResult result = restTemplate.postForObject(
    "http://ml-service:5000/classify", 
    request, 
    ClassificationResult.class
);
```

## Security Features

### Password Security
- BCrypt hashing with salt
- Minimum 8 character requirement
- Password confirmation on registration

### Two-Factor Authentication
- TOTP-based (RFC 6238)
- Compatible with Google Authenticator, Authy
- QR code generation for easy setup
- Session-based 2FA verification

### Session Management
- Secure session handling
- Auto-logout on browser close
- CSRF protection

### File Upload Security
- Extension validation
- MIME type verification using Apache Tika
- File size limits
- Isolated upload directories

## Configuration

### Database Options

**H2 (Development):**
```properties
spring.datasource.url=jdbc:h2:mem:pdfclassifier
spring.datasource.username=sa
spring.datasource.password=
```

**PostgreSQL (Production):**
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/pdfclassifier
spring.datasource.username=postgres
spring.datasource.password=your_password
```

**MySQL (Production):**
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/pdfclassifier
spring.datasource.username=root
spring.datasource.password=your_password
```

### Email Configuration

**Gmail:**
```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
```

**Office 365:**
```properties
spring.mail.host=smtp.office365.com
spring.mail.port=587
spring.mail.username=your-email@outlook.com
spring.mail.password=your-password
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/login` | GET | Login page |
| `/perform_login` | POST | Process login |
| `/register` | GET/POST | User registration |
| `/verify-2fa` | GET | 2FA verification page |
| `/perform-2fa` | POST | Process 2FA code |
| `/dashboard` | GET | Main dashboard |
| `/upload` | POST | Upload PDF file |
| `/settings` | GET | User settings |
| `/enable-2fa` | POST | Enable 2FA |
| `/disable-2fa` | POST | Disable 2FA |
| `/logout` | GET | User logout |

## Testing

### Manual Testing
1. Register multiple user accounts
2. Test with various PDF files
3. Verify email delivery
4. Test 2FA with different apps
5. Upload PDFs with different content

### Integration Testing
```bash
mvn test
```

### Load Testing
Use tools like Apache JMeter or Gatling to test:
- Concurrent uploads
- Multiple user sessions
- Database performance
- Email queue handling

## Troubleshooting

### Common Issues

**Port Conflict:**
```bash
# Change port in server.xml or
# Stop conflicting service
```

**Email Not Sending:**
- Verify SMTP credentials
- Check firewall allows port 587
- Use app-specific password for Gmail

**2FA Code Invalid:**
- Check server time synchronization
- Verify authenticator app time
- Try generating new secret

**PDF Upload Fails:**
- Check file permissions on upload directory
- Verify file size < 10MB
- Ensure valid PDF format

## Performance Optimization

### Database Indexing
```sql
CREATE INDEX idx_user_username ON users(username);
CREATE INDEX idx_pdf_status ON pdf_documents(processing_status);
```

### Caching
Add Spring Cache:
```java
@Cacheable("users")
public User findByUsername(String username) { ... }
```

### Async Processing
Already implemented for:
- PDF processing
- Email sending

## Deployment Checklist

- [ ] Update email configuration
- [ ] Set production database
- [ ] Generate secure JWT secret
- [ ] Enable HTTPS/SSL
- [ ] Configure log rotation
- [ ] Set up database backups
- [ ] Test email delivery
- [ ] Verify 2FA functionality
- [ ] Load test with sample data
- [ ] Document admin procedures

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/YourFeature`)
3. Commit changes (`git commit -m 'Add YourFeature'`)
4. Push to branch (`git push origin feature/YourFeature`)
5. Open Pull Request

## License

This project is provided as-is for educational and commercial use.

## Support

For detailed deployment instructions, see [DEPLOYMENT.md](DEPLOYMENT.md)

For issues:
1. Check application logs
2. Review configuration
3. Verify dependencies
4. Test database connectivity

## Future Enhancements

- [ ] Implement actual ML model integration
- [ ] Add batch PDF processing
- [ ] Support additional file formats
- [ ] Real-time processing status via WebSockets
- [ ] Advanced classification categories
- [ ] User role management (admin/user)
- [ ] API documentation with Swagger
- [ ] Docker containerization
- [ ] Kubernetes deployment configuration
- [ ] Microservices architecture option
