# Lunchbox Delivery A SRKREC Startup : Delivery Agent App

An Android app for the delivery agents of a lunchbox service. It gives each agent a clear route for the day, makes marking a stop a single tap, and keeps working when the network doesn't.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![Min SDK](https://img.shields.io/badge/minSdk-24-blue)
![Language](https://img.shields.io/badge/language-Java-orange)
![Backend](https://img.shields.io/badge/backend-Firebase-FFCA28)

<!-- Add screenshots here: Home, Route, Profile -->

## Why this exists

Our lunchbox startup collects boxes from homes across town and delivers them to the people who ordered them, every day, to hundreds of stops. Agents were working from phone calls and paper lists. This app replaces that: the admin assigns routes in the morning, agents see them instantly, and the office sees progress in real time.

## Features

**Daily route**
- Stops sorted by pickup order, each showing Box ID, customer, phone, pickup point, delivery address, notes and item count
- Filter chips (All, Pending, Picked, Delayed, Delivered) and live search across Box ID, name, address, phone and pickup point
- Header counters for pending, done and late stops that double as shortcuts to the filtered list

**Fast, forgiving status updates**
- One-tap *Picked Up*, *Delayed* and *No Box* buttons
- Every action shows an **Undo** snackbar and is only saved after about 3.5 seconds, so a mis-tap never reaches the office
- Marking a stop delayed notifies the admin and leaves a permanent "was delayed" flag for later reporting

**Route control**
- Drag-to-reorder on the Route tab, with the order saved for the day
- Hand a stop over to another agent from the Share tab

**Built for real streets**
- Offline queue: changes made without signal are stored on the phone and synced automatically when connectivity returns
- Tap-to-call for customers, and a direct call to the admin once a stop is picked up
- Push notifications for reassignments and updates

**Secure, quick access**
- Email and password sign-in for agent accounts only
- A 4-digit MPIN for everyday opening, stored as a hash on the device and resettable in Settings

## How a delivery changes state

```
Pending ──► Picked
   ├──► Delayed ──► Picked
   └──► NoBox
```

*Delivered* is set from the admin dashboard at the end of the run.

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java |
| UI | Material Components, RecyclerView, ChipGroup, BottomNavigationView |
| Auth | Firebase Authentication |
| Data | Cloud Firestore with real-time listeners |
| Push | Firebase Cloud Messaging |
| Offline | Firestore persistence plus a SharedPreferences write queue |

## Project structure

```
com.lunchbox.delivery
├── activities/   Splash, Login, Mpin, Main
├── fragments/    Home, Route, Profile, Settings, Share
├── adapters/     DeliveryCardAdapter, RouteAdapter, ShareDeliveryAdapter
├── models/       Delivery, User
├── services/     MyFirebaseMessagingService
└── utils/        NetworkMonitor, OfflineQueueManager, RouteResetManager
```

`MainActivity` holds the one Firestore listener for the signed-in agent's deliveries and shares the list with every tab, so screens never run duplicate queries.

## Getting started

1. Clone the repository and open it in Android Studio (JDK 21).
2. Add your own `google-services.json` to `app/`. Firebase config is never committed.
3. For release builds, set your signing details in `local.properties`.
4. Sync Gradle, then run on a device or emulator running Android 7.0 (API 24) or newer.

The app needs a Firebase project with Authentication (email/password), Firestore and Cloud Messaging enabled, plus a delivery-agent account created through the admin dashboard.

## Related projects

- **Admin Dashboard**: assigns routes, monitors deliveries, manages agents and customers
- **Customer Portal**: lets customers track today's box, message the office, and skip a day
- See [`docs/TECHNICAL.md`](docs/TECHNICAL.md) for the full architecture

## License

Private project. All rights reserved.
