# 🌿 GreenCart — Native Android Mobile Grocery & Subscription Platform

<div align="center">

[![Android SDK](https://img.shields.io/badge/Android%20SDK-API%2036-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Java](https://img.shields.io/badge/Java-11-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20AAC-0284C7?style=for-the-badge&logo=android)](https://developer.android.com/topic/architecture)
[![Payment Gateway](https://img.shields.io/badge/Payment-PayHere%20SDK-FF5722?style=for-the-badge)](https://www.payhere.lk/)
[![Database](https://img.shields.io/badge/Database-Room%20SQLite%20Cache-4285F4?style=for-the-badge&logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![Firebase](https://img.shields.io/badge/Cloud-Firebase%20FCM-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)](https://firebase.google.com/)

**A comprehensive, production-ready Native Android application for organic supermarket shopping, recurring automated delivery subscriptions, interactive live driver tracking, PayHere payment processing, and gamified loyalty rewards.**

[🌐 Live Interactive Web Showcase](https://hansanie-prabodha.github.io/greencart-mobile-app/) • [📱 Architecture Overview](#-system-architecture) • [🚀 Setup Guide](#-getting-started) • [📡 Backend API](#-companion-backend-api)

</div>

---

## 🌟 Executive Overview & Key Capabilities

**GreenCart** is an end-to-end mobile e-commerce platform engineered with native Android best practices. It bridges instant grocery delivery with long-term automated recurring subscriptions and sensor-driven gamification to maximize user retention and operational efficiency.

### 🎯 Core Highlights

1. **📦 Recurring Grocery Subscriptions Engine**
   - Automated delivery scheduling (**Daily**, **Weekly**, or **Monthly**).
   - Instant Pause, Resume, and Time-Slot configuration via interactive Bottom Sheets.
   - Dedicated subscription discount tier calculation.

2. **💳 Secure Multi-Method Checkout & PayHere Gateway**
   - Direct integration with **PayHere Mobile SDK** for seamless credit/debit card tokenization.
   - Dynamic **GreenPoints Loyalty Redemption** with automatic subtotal deduction.
   - Saved delivery addresses and multi-card management.

3. **🗺️ Live GPS Driver Tracking (Google Maps API)**
   - Fullscreen interactive route map displaying live delivery rider location.
   - ETA estimation, destination waypoints, and one-tap driver calling.

4. **🎰 Motion-Activated Gamification ("Shake to Win")**
   - Hardware accelerometer sensor listener triggering surprise rewards on phone shake.
   - Interactive scratch cards awarding instant discount vouchers and GreenPoints coins.

5. **💾 Offline-First Architecture & Room DB**
   - Local SQLite database persistence caching product listings and cart state.
   - **WorkManager** background workers ensuring background token sync and queued requests.

6. **🔔 Firebase Cloud Messaging (FCM)**
   - Deep-linked push notifications for order status updates, dispatch tracking, and promotional alerts.

---

## 🏛️ System Architecture

GreenCart adheres strictly to the **MVVM (Model-View-ViewModel)** architectural pattern recommended by Google:

```
                  ┌─────────────────────────────────────┐
                  │          UI Layer (View)            │
                  │  Activities, Fragments, Adapters    │
                  └──────────────────┬──────────────────┘
                                     │ (Observes LiveData / ViewBinding)
                  ┌──────────────────▼──────────────────┐
                  │             ViewModel               │
                  │ CartViewModel, ProductViewModel     │
                  └──────────────────┬──────────────────┘
                                     │ (Coordinates Data Operations)
                  ┌──────────────────▼──────────────────┐
                  │            Repository               │
                  └─────────┬─────────────────┬─────────┘
                            │                 │
              ┌─────────────▼──────┐   ┌──────▼─────────────┐
              │    Local Cache     │   │   Remote Network   │
              │  Room DB (SQLite)  │   │  Retrofit2 / FCM   │
              └────────────────────┘   └────────────────────┘
```

### 📂 Directory & Package Structure

```
app/src/main/java/com/hansanie/greencart/
├── activity/       # MainActivity, AuthActivity, SplashActivity, OnboardingActivity
├── adapter/        # ProductAdapter, CartAdapter, OrderAdapter, CategoryAdapter
├── dao/            # CartDao, ProductDao, OrderDao (Room Database Access Objects)
├── database/       # AppDatabase & SQLite schema migrations
├── dto/            # Data Transfer Objects for API requests and payloads
├── fragment/       # Home, Cart, Checkout, Subscriptions, Tracking, ShakeToWin, Profile
├── listener/       # Custom interface callbacks and sensor listeners
├── model/          # Product, CartItem, Subscription, DeliveryAddress, Order
├── network/        # RetrofitClient, ApiService, FCM Service, Token Sync
├── util/           # CustomToast, AppConstants, SensorHelper, CurrencyFormatter
├── viewmodel/      # CartViewModel, ProductViewModel, CheckoutViewModel
└── worker/         # SyncWorker, FcmTokenRegistrationWorker (WorkManager)
```

---

## 🛠️ Technology Stack & Libraries

| Category | Technology / Library | Purpose |
|---|---|---|
| **Language** | Java 11 / Kotlin | Core Native Android Development |
| **Target SDK** | Android 14+ (API 34–36) | Modern Android Runtime Compatibility |
| **Architecture** | MVVM + Jetpack Architecture Components | Separation of Concerns & State Management |
| **Persistence** | Android Jetpack Room DB (SQLite) | Offline Caching & Local Entity Management |
| **Networking** | Retrofit 2 + OkHttp 3 + Gson | Asynchronous REST API Client & Logging |
| **Payment Gateway** | PayHere Android SDK | Secure in-app digital payment processing |
| **Maps & Location** | Google Play Services (Maps & Location) | Real-time GPS Delivery Tracking |
| **Push Notifications** | Firebase Cloud Messaging (FCM) | Background notification event handling |
| **Background Tasks** | Android Jetpack WorkManager | Guaranteed asynchronous synchronization |
| **Animations & UI** | Airbnb Lottie + Material 3 | Smooth micro-animations and modern design |
| **Image Loading** | Bumptech Glide | Optimized caching and async image rendering |

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio** (Ladybug / Koala / Hedgehog or newer)
- **JDK 11** or **JDK 17**
- **Android SDK** with API Level 34+ installed

### 1. Clone the Repository
```bash
git clone https://github.com/your-username/greencart-mobile-app.git
cd greencart-mobile-app
```

### 2. Configure Local Properties & API Keys
Copy the example template to create your `local.properties` file:
```bash
cp local.properties.example local.properties
```
Edit `local.properties` to set your credentials:
```properties
sdk.dir=C:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
PAYHERE_MERCHANT_ID=YOUR_PAYHERE_MERCHANT_ID
PAYHERE_MERCHANT_SECRET=YOUR_PAYHERE_MERCHANT_SECRET
PAYHERE_SANDBOX=true
```

### 3. Firebase Setup
Copy the template Firebase configuration:
```bash
cp app/google-services.json.example app/google-services.json
```
*(Or link your own Firebase project from the Firebase Console).*

### 4. Build & Run
Open the project in Android Studio and press **Run (Shift + F10)** on an emulator or physical device.

---

## 📡 Companion Backend API

A lightweight companion Node.js / Express backend server is included in [`docs/backend-support-sample/`](docs/backend-support-sample/):

```bash
cd docs/backend-support-sample
npm install
npm run dev
```

Inspect [`schema.sql`](docs/backend-support-sample/schema.sql) for database table models and relationships.

---

## 🔒 Security & Privacy Notice

- All confidential API tokens, production Firebase credentials, and merchant secrets have been sanitized and replaced with dummy environment variables for public portfolio demonstration.
- No sensitive client data or proprietary credentials are included in this repository.

---

## 👨‍💻 Developer & Attribution

Developed with ❤️ by **Kalatuwawage Hansanie Prabodha**  
*Full-Stack Systems Engineer & Mobile Application Architect*  
*Specializing in Native Mobile, Backend Systems, Enterprise Architecture, and Cloud Infrastructure.*

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
