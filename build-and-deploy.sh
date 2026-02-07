#!/bin/bash

# PDF Classifier Application - Build and Deploy Script
# This script builds the application and optionally deploys to Tomcat

set -e

echo "======================================"
echo "PDF Classifier - Build & Deploy Script"
echo "======================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed${NC}"
    echo "Please install Java 17 or higher"
    exit 1
fi

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven is not installed${NC}"
    echo "Please install Maven 3.6 or higher"
    exit 1
fi

# Display versions
echo -e "${GREEN}Java Version:${NC}"
java -version
echo ""
echo -e "${GREEN}Maven Version:${NC}"
mvn -version
echo ""

# Clean and build
echo -e "${YELLOW}Building application...${NC}"
mvn clean package

if [ $? -eq 0 ]; then
    echo -e "${GREEN}Build successful!${NC}"
    echo ""
    echo "WAR file created at: target/pdf-classifier.war"
    echo ""
else
    echo -e "${RED}Build failed!${NC}"
    exit 1
fi

# Ask if user wants to deploy
read -p "Do you want to deploy to Tomcat? (y/n) " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]; then
    # Check if TOMCAT_HOME is set
    if [ -z "$TOMCAT_HOME" ]; then
        echo -e "${YELLOW}TOMCAT_HOME is not set${NC}"
        read -p "Enter Tomcat installation directory: " TOMCAT_HOME
    fi
    
    if [ ! -d "$TOMCAT_HOME" ]; then
        echo -e "${RED}Error: Tomcat directory not found: $TOMCAT_HOME${NC}"
        exit 1
    fi
    
    echo -e "${YELLOW}Deploying to Tomcat...${NC}"
    
    # Stop Tomcat if running
    if [ -f "$TOMCAT_HOME/bin/shutdown.sh" ]; then
        echo "Stopping Tomcat..."
        $TOMCAT_HOME/bin/shutdown.sh 2>/dev/null || true
        sleep 3
    fi
    
    # Copy WAR file
    echo "Copying WAR file to Tomcat webapps..."
    cp target/pdf-classifier.war $TOMCAT_HOME/webapps/
    
    # Create directories
    echo "Creating upload directories..."
    mkdir -p uploads
    mkdir -p processed
    
    # Start Tomcat
    if [ -f "$TOMCAT_HOME/bin/startup.sh" ]; then
        echo "Starting Tomcat..."
        $TOMCAT_HOME/bin/startup.sh
    fi
    
    echo ""
    echo -e "${GREEN}Deployment complete!${NC}"
    echo ""
    echo "Application will be available at:"
    echo "http://localhost:8080/pdf-classifier"
    echo ""
    echo "To view logs:"
    echo "tail -f $TOMCAT_HOME/logs/catalina.out"
else
    echo ""
    echo "Build complete. WAR file is ready for deployment."
    echo ""
    echo "To deploy manually:"
    echo "1. Copy target/pdf-classifier.war to Tomcat webapps directory"
    echo "2. Restart Tomcat"
fi

echo ""
echo -e "${GREEN}Done!${NC}"
