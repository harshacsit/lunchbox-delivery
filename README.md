# Lunchbox Delivery A SRKREC Startup: Agent App (Android)

The app our delivery agents use every day. It shows each agent their route for the day, lets them mark every stop as picked up, delayed or no-box, and keeps working when the signal drops. Admins manage everything from the web dashboard; this app is deliberately for agents only.

Package: `com.lunchbox.delivery` · Min SDK 24 · Java · Firebase (Auth, Firestore, Cloud Messaging)

---

## What agents can do

- **Sign in** with the email and password the admin created for them. Admin accounts are rejected here on purpose.
- **Set a 4-digit MPIN** so they don't type a password every morning. It is stored only as a SHA-256 hash on the device and can be reset from Settings.
- **See today's route** on the Home tab, sorted by pickup order, with filter chips (All, Pending, Picked, Delayed, Delivered) and a search box that matches Box ID, name, address, phone and pickup point.
- **Mark a stop** as *Picked Up*, *Delayed* or *No Box*. Every tap shows an **Undo** snackbar and only writes to Firestore after about 3.5 seconds, so a stray tap never becomes a wrong status.
- **Reorder the route** on the Route tab by dragging stops, then save the new order.
- **Call the customer** from the phone row on each card, or **call the admin** once a stop is marked Picked.
- **Hand over deliveries** to another agent from the Share tab. This only changes the assignee fields and never touches status.
- **Work offline.** Status changes made without a connection are queued on the device and pushed when the network comes back.
- **Get push notifications** for reassignments and updates.

## How a delivery moves

```
Pending ──► Picked
   │
   ├──► Delayed ──► Picked (after the agent or admin acts)
   └──► NoBox
```

*Delivered* is set by the admin ("Mark All Delivered") at the end of the run, not by agents. Marking **Delayed** also sets a permanent `wasDelayed` flag, so the dashboard can still show "was delayed" after the stop is eventually picked up.

## Project layout

```
app/src/main/java/com/lunchbox/delivery/
├── activities/   Splash, Login, Mpin, Main
├── fragments/    Home, Route, Profile, Settings, Share
├── adapters/     DeliveryCardAdapter, RouteAdapter, ShareDeliveryAdapter
├── models/       Delivery, User
├── services/     MyFirebaseMessagingService
└── utils/        NetworkMonitor, OfflineQueueManager, RouteResetManager
```

`MainActivity` owns a single Firestore listener (`deliveries` where `assignedTo == current user`) and exposes the list to all fragments, so tabs never run their own duplicate queries.

## Getting it running

1. Open the project in Android Studio (JDK 21, Gradle wrapper included).
2. Add your `google-services.json` to `app/`. It is not committed.
3. Provide signing details in `local.properties` (`signing.storeFile`, `signing.storePassword`, `signing.keyAlias`, `signing.keyPassword`) if you want a release build.
4. Sync Gradle and run on a device or emulator with API 24+.

To test properly you need an agent account: create one from the admin dashboard (Agents → Add Agent), which creates the Firebase Auth user and the `users/{uid}` document with `role: "delivery"`.

## Things worth knowing

- The MPIN screen is built entirely in Java. `activity_mpin.xml` still exists but is unused; the code-built version avoids a theme-related crash we hit earlier.
- Splash makes no Firestore calls. Firestore persistence is on by default in the SDK; enabling it manually caused startup crashes.
- Agent phone numbers come from the admin dashboard and are copied onto each delivery, which is how customers can call their agent.
- Never commit `service_account.json` or `google-services.json`.

## Roadmap

- iPhone agents via the PWA (`agent.html`) using Add to Home Screen; web push needs iOS 16.4+.
- Firebase Emulator Suite so local builds stop talking to production.
