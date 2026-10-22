# Paper Trade

Paper Trade is a Groww-inspired paper-trading Android application built primarily as a learning and SDK-integration project. It allows users to simulate stock trading using a virtual balance, backed by a real backend and database for persistent state.

## Tech Stack & Architecture

The project follows a standard three-layer architecture:

* **Frontend:** Android app built with Kotlin and Jetpack Compose.
* **Backend:** REST API built with Java and Spring Boot.
* **Database:** PostgreSQL 18 (via Docker) for data persistence.
* **Analytics:** CleverTap Android SDK for user event tracking and segmentation.

## Project Structure

* `Paper_Trade_frontend/`: The Android Kotlin/Compose frontend application.
* `Paper_Trade_backend/`: The Java Spring Boot backend application.
* `Paper_Trade_Project_PRD_and_Handoff.pdf`: Detailed Product Requirements Document containing full technical context, architecture, and development handoff instructions.

## Core Features

* User authentication and onboarding.
* Initial virtual balance of $1,000 for new accounts.
* Stock market data viewing (via free market-data APIs).
* Simulated buying and selling of stocks.
* Portfolio dashboard showing holdings, available cash, and profit/loss.
* Rich CleverTap event generation for analytics and user segmentation.

## Getting Started

### Database & Backend

1. Create your local secrets file (it is gitignored, never commit it):
   ```bash
   cp Paper_Trade_backend/.env.example Paper_Trade_backend/.env
   ```
   Fill in `DB_PASSWORD`, `JWT_SECRET` (32+ random chars) and `FINNHUB_KEY` (free key from https://finnhub.io).
2. Start PostgreSQL with Docker Compose from the repo root:
   ```bash
   docker compose --env-file Paper_Trade_backend/.env up -d
   ```
3. Run the Spring Boot application from `Paper_Trade_backend` (the working directory must be that folder so `.env` is found). It runs on `http://localhost:8080`.

### Frontend

1. Open the `Paper_Trade_frontend` project in Android Studio and run it on your phone.
2. Debug builds pick the backend automatically (`Backend.kt`): if a server answers on `http://localhost:8080` they use it, otherwise they use production (`BACKEND_URL` in `gradle.properties`). Release builds always use production.
3. To use the local backend from a USB-connected phone, run `adb reverse tcp:8080 tcp:8080` (it resets when the phone is unplugged or restarted).

> **Local vs production database:** `Paper_Trade_backend/.env` must not set `DATABASE_URL`, so local runs use the Docker Postgres. Production (Render) sets its own `DATABASE_URL`. Keep production credentials under a `PROD_` prefix in `.env`, where they are ignored.

## Frontend Guide (Android, Kotlin + Jetpack Compose)

All source is in `Paper_Trade_frontend/app/src/main/java/com/fredrick/papertrade/`.

### App flow

`PaperTradeApplication` -> `MainActivity` -> `PaperTradeApp` (in `MainActivity.kt`) decides what to show: onboarding (first launch) -> `AuthScreen` (login/signup) -> `MainScreen` (three tabs: Stocks, Trade, Dashboard).

### Files

| File | What it does | Android / Compose concept |
|---|---|---|
| `PaperTradeApplication.kt` | App entry point. Registers the custom in-app templates before CleverTap starts and turns on verbose SDK logs in debug builds. | An `Application` subclass runs once per process, before any screen. |
| `MainActivity.kt` | The single Activity. Starts CleverTap/FCM, requests notification permission, handles push and deep-link intents, and hosts `PaperTradeApp` (session restore, login state, which screen is shown). | One-Activity apps: Compose draws everything inside `setContent`. `launchMode="singleTop"` plus `onNewIntent` receives links while the app is open. |
| `Backend.kt` | Picks the server per request: `localhost:8080` in debug builds when it answers, otherwise production. | A cached `@Volatile` value plus a short network probe on `Dispatchers.IO`. |
| `AuthScreen.kt` | Login/signup form, form validation, and `request(...)`, the one HTTP helper every screen uses (JWT in the `Authorization` header). | Plain `HttpURLConnection` inside `withContext(Dispatchers.IO)`; the UI must never do network work on the main thread. |
| `MainScreen.kt` | Logged-in shell: bottom navigation, current tab, balance state, and tab requests from in-apps and deep links. | `remember { mutableStateOf }` keeps UI state; `LaunchedEffect` runs side effects when keys change. |
| `StocksScreen.kt` | Stock list with live prices (refreshes every 30 s), company logos, and the Native Display banner slot. | `LazyColumn` only composes visible rows; Coil's `AsyncImage` loads logos. |
| `TradeScreen.kt` | Buy/sell form and chart. Sends intent only; the backend sets the price and commits the trade. | State hoisting: balance changes go up through `onBalanceChange`, not stored here. |
| `ChartCard.kt` | Price chart (line or candles) with a range picker. | Drawn on a Compose `Canvas`; touch handling picks the nearest candle. |
| `DashboardScreen.kt` | Portfolio value, profit/loss, holdings, and the inbox bell with an unread badge. | `collectAsState()` turns a `StateFlow` into Compose state. |
| `OnboardingScreen.kt`, `IntroScreen.kt`, `ContentScreens.kt`, `PageIndicator.kt` | The animated first-launch slides and page dots. | `AnimatedContent` for transitions and `Animatable` for the progress dot. |
| `ui/theme/` | Colours, typography, theme. | Material 3 theming. |
| `Analytics.kt` | Every CleverTap event and profile update in one place (including first-trade and low-balance detection). | A Kotlin `object` is a singleton; keeping events here avoids typos across screens. |
| `MyFcmMessageListenerService.kt` | Receives FCM push data, hands it to CleverTap, and posts a fallback notification. | A `FirebaseMessagingService` is started by the system when a push arrives, even if the app is closed. |
| `InAppHandler.kt` | CleverTap in-app listeners; maps button key-value `action` to a tab; remembers the last tap so a trade can be attributed to it. | Listener (callback) interfaces implemented once and registered with the SDK. |
| `InAppTemplates.kt` | Custom in-app templates ("Paper Trade Message", "Paper Trade Push Primer") and the Open Stocks/Trade/Portfolio functions. | Templates are registered in code and synced to the dashboard (debug builds). |
| `InAppUi.kt` | Draws the custom template as a centered card or a bottom sheet, with an optional https image. | `Dialog` with a window gravity change gives a bottom sheet. |
| `InAppGate.kt` | Holds in-apps back while signed out or mid-trade; the SDK queues them and shows them later. | A set of "block reasons"; the SDK is suspended while the set is non-empty. |
| `DeepLinks.kt` | `papertrade://stocks`, `trade?symbol=AAPL`, `portfolio`; validates the symbol and only switches tabs. | A manifest `<intent-filter>` with a custom URI scheme sends matching links to `MainActivity`. |
| `Inbox.kt` | Initialises CleverTap App Inbox (trade receipts), tracks the unread count, opens the SDK inbox screen. | The SDK screen is a classic View `Activity`, so the app also depends on Material Components. |
| `NativeDisplay.kt` | Receives Native Display units and draws the "make your first trade" banner; reports views and taps. | Data-only campaigns: the server sends content, the app owns the UI. |

### Concepts worth knowing

* **Compose state:** UI is a function of state. Change a `mutableStateOf` value and only the affected composables redraw.
* **Side effects:** `LaunchedEffect(key)` runs a coroutine when `key` changes; `DisposableEffect` adds cleanup when leaving the screen.
* **Coroutines:** network calls use `suspend` functions on `Dispatchers.IO`, launched from `rememberCoroutineScope()`.
* **`StateFlow`:** shared, observable state that lives outside composables (inbox unread count, banner, tab requests from links).
* **Server is the source of truth:** the app sends "buy 3 AAPL"; the backend fetches the price, checks the balance and decides things like `firstTrade`.
* **Debug vs release:** debug builds probe `localhost`, sync in-app templates and log CleverTap verbosely; release builds always use production.

## CleverTap Integration

**Implemented**

| Area | Status |
|---|---|
| Android push | FCM service, token registration, viewed/clicked tracking, Android 13+ permission flow, fallback notification. |
| In-app | SDK listeners, custom templates (card or bottom sheet, optional image), push primer, tab-navigation button actions, gating while signed out or mid-trade, click attribution to trades. |
| App Inbox | Trade receipts from an App Inbox campaign triggered by `Trade Placed`; bell and unread badge on the Dashboard. |
| Native Display | Banner on the Stocks screen for users who have not traded (campaign: `App Launched`, have not done `Charged`); hides after any trade. |
| Deep links | `papertrade://stocks`, `papertrade://trade?symbol=AAPL`, `papertrade://portfolio` for in-app, push and banner buttons. |
| First-trade campaign | The backend returns `firstTrade` on `POST /trades`; the app sends `First Trade Completed` (once per user). Verified with a push campaign. |
| Low-balance nudge | `Low Balance Reached` fires once when a trade takes the balance from $100 or more to under $100 (`Analytics.LOW_BALANCE_THRESHOLD`). |

**Events:** `Signed Up`, `Logged In`, `Logged Out`, `Login Failed`, `Signup Failed`, `Screen Viewed` (`Screen`), `Onboarding Viewed`/`Completed`, `Stock Viewed`, `Portfolio Viewed`, `Chart Range Changed`, `Chart Type Changed`, `Trade Placed` (`Symbol`, `Side`, `Quantity`, `Price` per share, `Total`, `Balance After`, `First Trade`), `Trade Failed`, `First Trade Completed`, `Low Balance Reached`, `InApp Dismissed`, `InApp Action Completed`, `Push Permission Response`, and a Charged event per trade (payment mode "Virtual Balance"). The SDK records in-app, inbox and Native Display views and clicks itself.

**Profile properties:** `Name`, `Email`, `Identity` (random public id), `Account Balance`, `Low Balance`, `Holdings Count`, `Total Trades`, `Last Trade Symbol`, `Last Trade Date`, `First Trade Date`.

**Campaign notes**

* Event properties (for example `Balance After`) are on the **Event** tab of the personalization picker, and only for the campaign's trigger event. Profile `Account Balance` is updated just after the event, so it shows the old balance.
* In-apps are capped per session by CleverTap settings, so a repeat low-balance crossing in the same session may not show a second popup.
* An event only appears in the dashboard after it has fired once.

**Not yet implemented:** rich push (images, buttons, carousel) and server-side events from the backend.
