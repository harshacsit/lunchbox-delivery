# 🍱 Lunchbox Delivery for college startup

A complete lunchbox delivery management system with an **Android agent app** and a **web admin dashboard**, backed by Firebase.

---

## Overview

Lunchbox Delivery has two parts that work together:

- **Android App** — delivery agents log in, see their assigned deliveries, and update statuses in real time
- **Web Dashboard** — admin imports customers, assigns routes, tracks live delivery progress, and manages the team

---

## Tech Stack

| Layer | Technology |
|---|---|
| Android App | Java, Firebase Auth, Firestore, FCM |
| Admin Dashboard | HTML / CSS / Vanilla JS |
| Database | Firebase Firestore |
| Auth | Firebase Authentication |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| Excel Import | SheetJS (xlsx.js) |

---

## Project Structure

```
├── Android App
│   ├── activities/
│   │   ├── SplashActivity.java       — launch screen, routes to login or MPIN
│   │   ├── LoginActivity.java        — Firebase email/password login
│   │   ├── MpinActivity.java         — 4-digit PIN screen (set or verify)
│   │   └── MainActivity.java         — main screen with fragments + bottom nav
│   ├── fragments/
│   │   ├── HomeFragment.java         — delivery list with filter chips
│   │   ├── RouteFragment.java        — drag-to-reorder route planner
│   │   ├── ProfileFragment.java      — agent stats and profile info
│   │   └── SettingsFragment.java     — notifications, MPIN reset, sign out
│   ├── adapters/
│   │   ├── DeliveryCardAdapter.java  — card UI: Picked / Delayed / Call Admin
│   │   └── RouteAdapter.java         — drag-and-drop route list
│   ├── models/
│   │   ├── Delivery.java             — delivery data model
│   │   └── User.java                 — agent/user data model
│   ├── services/
│   │   └── MyFirebaseMessagingService.java — push notification handler
│   └── utils/
│       ├── NetworkMonitor.java       — online/offline detection
│       ├── OfflineQueueManager.java  — queues status updates when offline
│       └── RouteResetManager.java    — archives deliveries to history
│
├── Web Dashboard
│   ├── dashboard.html                — main admin panel (all features)
│   └── login.html                   — admin login page
│
└── res/
    ├── layout/                       — XML layouts for all screens
    ├── drawable/                     — icons, shapes, status badge
    ├── anim/                         — transition animations
    └── values/                       — colors, strings, themes
```

---

## Features

### Android App (Agent Side)

- **Secure login** — Firebase Auth with role check (delivery staff only)
- **MPIN** — 4-digit PIN for quick daily access after first login
- **Home tab** — all assigned deliveries with filter chips (All / Pending / Picked / Delayed / Delivered)
- **Route tab** — drag-to-reorder delivery sequence, save custom pickup order
- **Status updates** — mark Picked Up or Delayed with one tap; writes to Firestore instantly
- **Call buttons** — call customer or call admin directly from each card
- **Offline mode** — status updates queue locally and sync when internet returns
- **Push notifications** — FCM alerts for new assignments and reassignments
- **Profile tab** — total deliveries, completion rate, zone, vehicle info
- **Settings** — notification toggles, MPIN reset, password change, sign out

### Web Dashboard (Admin Side)

- **Live dashboard** — real-time stats: total, pending, completed, delayed
- **Delayed panel** — instant view of all delayed deliveries with one-click reassign
- **Auto Assign** — reads all active customers, assigns to agents by zone + load balance, creates delivery records in bulk
- **Deliveries panel** — full list with status filter, agent filter, search, manual add, reassign
- **Customer master list** — permanent customer database; add, search, pause customers
- **Excel import** — upload `.xlsx` / `.csv`, preview rows, import in bulk (up to 400 rows per batch)
- **Agent management** — create agents (creates Firebase Auth account), view UID for Excel, track performance
- **History** — load any past date, view summary stats and full delivery log
- **Daily reset** — archives today's deliveries to `history/{date}/`, clears active list for tomorrow

---

## Firestore Data Structure

```
users/
  {uid}/             — role: "admin" or "delivery"
    name, email, phone, zone, fcmToken, maxDeliveries, ...

customers/
  {docId}/           — permanent customer master list
    name, phone, pickupLocation, deliveryAddress, zone, assignedAgent, active

deliveries/
  {docId}/           — today's active deliveries
    customerName, customerPhone, pickupLocation, deliveryAddress,
    assignedTo, assignedName, status, pickupOrder, deliveryDate, timestamp

history/
  {YYYY-MM-DD}/
    deliveries/{docId}/   — archived delivery records
    summary/stats         — totalDeliveries, delivered, delayed, completionRate

notifications/
  {docId}/           — FCM notification queue (sent by background process)
```

---

## Delivery Status Flow

```
Pending → Picked → (done)
Pending → Delayed → Picked (after reassign)
```

Agents see **Pending** and **Delayed** deliveries with action buttons. After marking **Picked**, the card shows a **Call Admin** button. Delivered status is set by the system after archiving.

---

## Setup

### Firebase

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Enable **Authentication** (Email/Password)
3. Enable **Firestore** in production mode
4. Enable **Cloud Messaging**
5. Download `google-services.json` and place in `app/`
6. Set Firestore security rules (admin role required for write access to users collection)

### Admin Account

Create an admin user in Firebase Auth, then manually add a Firestore document:

```
users/{uid}/
  name: "Admin"
  role: "admin"
  email: "admin@yourcompany.com"
```

### Android App

```bash
# Open in Android Studio
# Sync Gradle
# Run on device or emulator (API 24+)
```

### Web Dashboard

Open `login.html` in any browser — no server needed. Firebase config is already embedded in the HTML files.

---

## Daily Workflow

1. **Morning** — Admin opens dashboard → Auto Assign → agents receive their route
2. **During day** — Agents mark deliveries Picked / Delayed in real time; admin monitors live
3. **If delayed** — Admin sees alert in Delayed panel → reassigns to another agent in one click
4. **Evening** — Admin runs Daily Reset → data archived to history → ready for tomorrow

---

## Excel Import Format

| Column | Required | Notes |
|---|---|---|
| CustomerName | ✅ | |
| Phone | | |
| PickupLocation | ✅ | |
| DeliveryAddress | ✅ | |
| Zone | | e.g. north, south |
| AgentUID | | Firebase UID from Agents panel |
| Notes | | Special instructions |
| ItemCount | | Defaults to 1 |

Download the template from the Excel Import panel in the dashboard.

---

## Offline Support

The app uses two layers of offline protection:

1. **Firestore SDK persistence** — automatically caches reads and queues writes
2. **OfflineQueueManager** — secondary queue in SharedPreferences for status updates; flushes automatically when connectivity is restored

---

## Dependencies

### Android (`build.gradle`)

- `firebase-auth`, `firebase-firestore`, `firebase-messaging`
- `material` (MaterialComponents theme required)
- `cardview`, `recyclerview`

### Web Dashboard

- Firebase JS SDK 9.22.0 (compat mode, loaded via CDN)
- SheetJS 0.18.5 (Excel parsing, loaded via CDN)

---

## Known Notes

- The `DeliveryListActivity.java` is a deprecated stub kept to avoid stale reference errors — it can be deleted once all references are removed
- `activity_mpin.xml` uses `Widget.MaterialComponents.Button.OutlinedButton` style; `MpinActivity.java` builds its UI entirely in code as a crash-safe fallback — the XML layout is not used at runtime
- The `service_account.json` is for server-side FCM sending; do not commit this file to public repositories

---

## License

Private project — all rights reserved.
