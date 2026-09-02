# 🛒 GreenCart - Companion Backend & API Architecture

This directory provides the companion REST API server and database schema designed for the **GreenCart** Android Mobile Application.

## 🚀 Quick Start

1. **Install Dependencies:**
   ```bash
   npm install
   ```

2. **Run in Development Mode:**
   ```bash
   npm run dev
   ```
   Server will start on `http://localhost:8080`.

3. **Database Schema:**
   Inspect [schema.sql](schema.sql) for PostgreSQL / MySQL database migrations and relational data models.

## 📡 API Endpoints Overview

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/health` | Service health check |
| `GET` | `/api/categories` | Fetch all supermarket departments |
| `GET` | `/api/products` | Query products with category & search filters |
| `GET` | `/api/products/:id` | Detailed product specifications |
| `POST` | `/api/orders` | Submit new checkout orders |
| `POST` | `/api/subscriptions` | Initialize automated recurring grocery subscriptions |
| `POST` | `/api/fcm/register` | Register device FCM token for push notifications |

---
**Developed by Kalatuwawage Hansanie Prabodha — Full-Stack Systems Portfolio Project**
