# Elite4 CoLive — Docker & AWS Deployment Guide

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Prerequisites](#prerequisites)
3. [Local Docker Setup](#local-docker-setup)
4. [AWS EC2 Deployment](#aws-ec2-deployment)
5. [AWS ECS Deployment (Alternative)](#aws-ecs-deployment-alternative)
6. [Environment Variables Reference](#environment-variables-reference)
7. [SSL / HTTPS Setup](#ssl--https-setup)
8. [Troubleshooting](#troubleshooting)

---

## Architecture Overview

```
                    ┌─────────────────────┐
                    │   Nginx (port 80)   │
                    │   React Frontend    │
                    └─────────┬───────────┘
                              │
           ┌──────────────────┼──────────────────┐
           │                  │                  │
           ▼                  ▼                  ▼
┌──────────────────┐ ┌───────────────┐ ┌──────────────────┐
│ Registration Svc │ │ Payment Svc   │ │ Notification Svc │
│ (port 8082)      │ │ (port 8083)   │ │ (port 8084)      │
└────────┬─────────┘ └───────┬───────┘ └──────────────────┘
         │                   │
         └─────────┬─────────┘
                   ▼
          ┌────────────────┐
          │  MongoDB 7     │
          │  (port 27017)  │
          └────────────────┘
```

**Services:**

| Service               | Port | Description                         |
|----------------------|------|-------------------------------------|
| Frontend (Nginx)     | 80   | React SPA + reverse proxy           |
| Registration Services| 8082 | Auth, users, rooms, tenants, rent   |
| Payment Services     | 8083 | Razorpay payments, routing          |
| Notification Services| 8084 | Email, SMS, WhatsApp, Telegram      |
| MongoDB              | 27017| Database for all services           |

---

## Prerequisites

- **Docker** >= 24.0
- **Docker Compose** >= 2.20
- **Git**
- For AWS: An AWS account with EC2 or ECS access

---

## Local Docker Setup

### 1. Project Structure

Ensure both repos are in the same parent directory:

```
Projects/
├── elite4-main/                  # Backend (Spring Boot microservices)
│   ├── registration-services/
│   │   └── Dockerfile
│   ├── payment-services/
│   │   └── Dockerfile
│   ├── notification-services/
│   │   └── Dockerfile
│   └── .dockerignore
├── elite4UI-master/
│   └── client/                   # Frontend (React)
│       ├── Dockerfile
│       ├── nginx.conf
│       └── .dockerignore
├── docker-compose.yml
└── .env.example
```

### 2. Configure Environment

```bash
# Copy the example env file
cp .env.example .env

# Edit with your actual values
nano .env
```

### 3. Build & Run

```bash
# Build and start all services
docker compose up --build -d

# Check status
docker compose ps

# View logs
docker compose logs -f

# View logs for a specific service
docker compose logs -f registration-services
```

### 4. Verify

- **Frontend**: http://localhost
- **Registration API**: http://localhost:8082/adminservices/login
- **Payment API**: http://localhost:8083/payments/health
- **MongoDB**: `mongosh mongodb://root:StrongPassword!123@localhost:27017/admin`

### 5. Stop

```bash
docker compose down          # Stop containers
docker compose down -v       # Stop + remove volumes (DELETES DATA)
```

---

## AWS EC2 Deployment

### Step 1: Launch EC2 Instance

1. Go to **AWS Console → EC2 → Launch Instance**
2. Choose:
   - **AMI**: Amazon Linux 2023 or Ubuntu 22.04 LTS
   - **Instance type**: `t3.medium` (minimum 2 vCPU, 4 GB RAM) or `t3.large` for production
   - **Storage**: 30 GB gp3 (minimum)
   - **Key pair**: Create or select an existing key pair
3. **Security Group** — create with these inbound rules:

| Port | Protocol | Source      | Description          |
|------|----------|-------------|----------------------|
| 22   | TCP      | Your IP     | SSH access           |
| 80   | TCP      | 0.0.0.0/0   | HTTP (frontend)      |
| 443  | TCP      | 0.0.0.0/0   | HTTPS (if using SSL) |

> **Important**: Do NOT expose ports 8082, 8083, 8084, 27017 to the public. Nginx handles all external traffic on port 80.

### Step 2: Connect & Install Docker

```bash
# SSH into the instance
ssh -i your-key.pem ec2-user@<EC2_PUBLIC_IP>

# --- For Amazon Linux 2023 ---
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker ec2-user

# Install Docker Compose plugin
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Verify
docker --version
docker compose version

# Re-login for group changes
exit
ssh -i your-key.pem ec2-user@<EC2_PUBLIC_IP>
```

```bash
# --- For Ubuntu 22.04 ---
sudo apt update && sudo apt upgrade -y
sudo apt install -y docker.io docker-compose-v2 git
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker ubuntu
exit
ssh -i your-key.pem ubuntu@<EC2_PUBLIC_IP>
```

### Step 3: Clone Repositories

```bash
mkdir -p ~/elite4 && cd ~/elite4

# Clone both repos
git clone <YOUR_BACKEND_REPO_URL> elite4-main
git clone <YOUR_FRONTEND_REPO_URL> elite4UI-master
```

### Step 4: Configure Environment

```bash
cd ~/elite4

# Copy and edit environment file
cp .env.example .env
nano .env
```

**Critical values to update for production:**

```env
# Use a strong MongoDB password
MONGO_ROOT_PASSWORD=<GENERATE_A_STRONG_PASSWORD>

# Razorpay production keys
RAZORPAY_KEY_ID=rzp_live_XXXXXXXXXXXX
RAZORPAY_KEY_SECRET=XXXXXXXXXXXXXXXXXXXX

# Email settings
MAIL_HOST=smtp.gmail.com
MAIL_USERNAME=your-app-email@gmail.com
MAIL_PASSWORD=your-app-password

# Optional: WhatsApp, Telegram, Twilio
```

### Step 5: Deploy

```bash
cd ~/elite4

# Build and start (first time takes 5-10 minutes)
docker compose up --build -d

# Monitor startup
docker compose logs -f

# Verify all containers are running
docker compose ps
```

### Step 6: Allocate Elastic IP (Important!)

EC2 public IPs change on reboot. You need a fixed IP for your domain:

1. Go to **AWS Console → EC2 → Elastic IPs**
2. Click **Allocate Elastic IP address** → **Allocate**
3. Select the new IP → **Actions → Associate Elastic IP address**
4. Choose your EC2 instance → **Associate**
5. Note down the Elastic IP (e.g., `54.123.45.67`)

### Step 7: Configure Google Domains DNS

1. Go to [Google Domains](https://domains.google.com) → select your domain
2. Click **DNS** in the left sidebar
3. Scroll to **Custom records** and add these records:

| Host Name | Type | TTL  | Data                 |
|-----------|------|------|----------------------|
| @         | A    | 300  | `<YOUR_ELASTIC_IP>`  |
| www       | A    | 300  | `<YOUR_ELASTIC_IP>`  |

> **Example**: If your domain is `elite4colive.com` and Elastic IP is `54.123.45.67`:
> - `@` → A → `54.123.45.67` (maps `elite4colive.com`)
> - `www` → A → `54.123.45.67` (maps `www.elite4colive.com`)

4. Click **Save**
5. DNS propagation takes **5–30 minutes** (sometimes up to 48 hours)

**Verify DNS propagation:**

```bash
# From your local machine
nslookup yourdomain.com
# or
dig yourdomain.com +short
# Should return your Elastic IP
```

### Step 8: Enable HTTPS with Let's Encrypt (Free SSL)

Once your domain points to the EC2 IP:

```bash
# SSH into EC2
ssh -i your-key.pem ec2-user@<ELASTIC_IP>

cd ~/elite4

# Create a certs directory
mkdir -p certs

# Run certbot via Docker to get SSL certificate
docker run --rm -it \
  -v ~/elite4/certs:/etc/letsencrypt \
  -p 80:80 \
  certbot/certbot certonly \
  --standalone \
  -d yourdomain.com \
  -d www.yourdomain.com \
  --email your-email@gmail.com \
  --agree-tos \
  --no-eff-email
```

> **Note**: Stop the frontend container first: `docker compose stop frontend` before running certbot, then start it back after.

Now update `nginx.conf` in `elite4UI-master/client/nginx.conf` to handle HTTPS:

```nginx
# Redirect HTTP to HTTPS
server {
    listen 80;
    server_name yourdomain.com www.yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name yourdomain.com www.yourdomain.com;

    ssl_certificate     /etc/nginx/certs/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/live/yourdomain.com/privkey.pem;

    # ... keep all existing location blocks ...
}
```

Add volume mount in `docker-compose.yml` under the `frontend` service:

```yaml
  frontend:
    # ... existing config ...
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./certs:/etc/nginx/certs:ro
```

Then rebuild:

```bash
docker compose up --build -d frontend
```

**Auto-renew certificate** (add to crontab):

```bash
crontab -e
# Add this line:
0 3 1 */2 * cd ~/elite4 && docker compose stop frontend && docker run --rm -v ~/elite4/certs:/etc/letsencrypt -p 80:80 certbot/certbot renew && docker compose up -d frontend
```

### Step 9: Update Security Group for HTTPS

Go to **AWS Console → EC2 → Security Groups** and add:

| Port | Protocol | Source    | Description |
|------|----------|-----------|-------------|
| 443  | TCP      | 0.0.0.0/0 | HTTPS      |

### Step 10: Verify Deployment

```bash
# From your local machine
curl https://yourdomain.com                    # Frontend (HTTPS)
curl https://yourdomain.com/adminservices/     # API via nginx proxy
```

Open `https://yourdomain.com` in your browser — you should see the Elite4 CoLive login page.

---

## AWS ECS Deployment (Alternative)

For a more managed approach using AWS ECS with Fargate:

### Step 1: Push Images to ECR

```bash
# Create ECR repositories
aws ecr create-repository --repository-name elite4/registration-services
aws ecr create-repository --repository-name elite4/payment-services
aws ecr create-repository --repository-name elite4/notification-services
aws ecr create-repository --repository-name elite4/frontend

# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com

# Build and push each image
cd ~/elite4

# Build backend images
docker compose build registration-services payment-services notification-services frontend

# Tag and push
docker tag elite4-registration <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/elite4/registration-services:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/elite4/registration-services:latest

docker tag elite4-payment <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/elite4/payment-services:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/elite4/payment-services:latest

docker tag elite4-notification <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/elite4/notification-services:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/elite4/notification-services:latest

docker tag elite4-frontend <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/elite4/frontend:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/elite4/frontend:latest
```

### Step 2: Create ECS Cluster

1. Go to **ECS Console → Create Cluster**
2. Choose **Fargate** (serverless) or **EC2** (self-managed)
3. Create task definitions for each service
4. Create services and configure load balancer

> ECS deployment is more complex but offers better scaling, monitoring, and zero-downtime deployments. Consider this for production workloads with high traffic.

---

## Environment Variables Reference

### MongoDB

| Variable           | Default                                            | Description          |
|-------------------|----------------------------------------------------|----------------------|
| MONGO_ROOT_PASSWORD | StrongPassword!123                                | MongoDB root password |
| MONGODB_URI       | mongodb://root:password@mongo:27017/admin           | Full connection URI  |
| MONGODB_DATABASE  | admin / payments                                    | Database name        |

### Registration Services

| Variable                | Default              | Description                    |
|------------------------|----------------------|--------------------------------|
| SERVER_PORT            | 8082                 | Service port                   |
| NOTIFICATION_SERVICE_URL | http://notification-services:8084 | Notification service URL |
| PAYMENT_SERVICE_URL    | http://payment-services:8083 | Payment service URL      |
| FILE_STORAGE_TYPE      | LOCAL                | Storage type (LOCAL/S3/AZURE)  |
| FILE_STORAGE_LOCAL_PATH | /app/colive-files/  | Local file storage path        |
| TENANT_DEFAULT_PASSWORD | Tenant@123          | Default password for tenants   |

### Payment Services

| Variable              | Default                    | Description              |
|----------------------|----------------------------|--------------------------|
| SERVER_PORT          | 8083                       | Service port             |
| RAZORPAY_KEY_ID      | rzp_test_...               | Razorpay API key         |
| RAZORPAY_KEY_SECRET  | acXTG6s...                 | Razorpay API secret      |
| RAZORPAY_COMPANY_NAME | CoLive Connect            | Company name for payments|
| RAZORPAY_WEBHOOK_SECRET |                          | Webhook signature secret |
| RAZORPAY_PLATFORM_FEE | 4900                     | Platform fee in paise    |
| RAZORPAY_ROUTE_ENABLED | true                    | Enable payment routing   |

### Notification Services

| Variable               | Default | Description                |
|-----------------------|---------|----------------------------|
| SERVER_PORT           | 8084    | Service port               |
| MAIL_HOST             | smtp.gmail.com | SMTP server         |
| MAIL_PORT             | 587     | SMTP port                  |
| MAIL_USERNAME         |         | Email username             |
| MAIL_PASSWORD         |         | Email password / app password |
| WHATSAPP_API_TOKEN    |         | Meta WhatsApp API token    |
| WHATSAPP_PHONE_NUMBER_ID |      | WhatsApp phone number ID   |
| TELEGRAM_BOT_TOKEN    |         | Telegram bot token         |
| TWILIO_ACCOUNT_SID    |         | Twilio account SID         |
| TWILIO_AUTH_TOKEN     |         | Twilio auth token          |
| TWILIO_FROM_NUMBER    |         | Twilio sender number       |

### Frontend

| Variable                         | Default              | Description            |
|---------------------------------|----------------------|------------------------|
| REACT_APP_REGISTRATION_API_URL  | http://localhost:8082 | Registration API URL   |
| REACT_APP_PAYMENT_API_URL       | http://localhost:8083 | Payment API URL        |
| REACT_APP_NOTIFICATION_API_URL  | http://localhost:8084 | Notification API URL   |

> **Note**: When using Docker Compose with nginx proxy, set frontend env vars to empty strings. Nginx proxies `/adminservices/`, `/registrations/`, `/rentpayments/`, and `/payments/` to the backend services.

---

## Troubleshooting

### Container won't start

```bash
# Check logs
docker compose logs <service-name>

# Check container status
docker compose ps

# Restart a specific service
docker compose restart registration-services
```

### MongoDB connection issues

```bash
# Test MongoDB connectivity from inside a container
docker exec -it elite4-registration sh
# Inside container:
# wget -qO- http://mongo:27017 || echo "Cannot reach MongoDB"
```

### Frontend shows blank page

```bash
# Verify nginx config
docker exec -it elite4-frontend nginx -t

# Check that backend services are reachable from nginx
docker exec -it elite4-frontend wget -qO- http://registration-services:8082/adminservices/ || echo "Cannot reach backend"
```

### Out of disk space

```bash
# Clean Docker build cache
docker system prune -a

# Check disk usage
df -h
docker system df
```

### Update deployment

```bash
cd ~/elite4

# Pull latest code
cd elite4-main && git pull && cd ..
cd elite4UI-master && git pull && cd ..

# Rebuild and restart
docker compose up --build -d
```

### Backup MongoDB

```bash
# Create backup
docker exec elite4-mongo mongodump --username root --password <PASSWORD> --authenticationDatabase admin --out /dump
docker cp elite4-mongo:/dump ./mongo-backup-$(date +%Y%m%d)

# Restore backup
docker cp ./mongo-backup-20240101 elite4-mongo:/dump
docker exec elite4-mongo mongorestore --username root --password <PASSWORD> --authenticationDatabase admin /dump
```

---

## Quick Reference Commands

```bash
# Start all services
docker compose up -d

# Stop all services
docker compose down

# Rebuild and restart
docker compose up --build -d

# View logs (follow)
docker compose logs -f

# Check status
docker compose ps

# Enter a container shell
docker exec -it elite4-registration sh

# Restart single service
docker compose restart registration-services

# Scale (if needed)
docker compose up -d --scale registration-services=2
```
