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

1. Open the `Paper_Trade_frontend` project in Android Studio.
2. Build and run the application on an Android Emulator.
3. The backend URL is `BASE_URL` in `AuthScreen.kt`:
   * Physical phone over USB: `http://localhost:8080`, plus `adb reverse tcp:8080 tcp:8080` each time the phone is reconnected.
   * Android emulator: `http://10.0.2.2:8080`.

> **Warning:** If the app shows "Can't reach the server" while the backend is running, the phone can't see `localhost`. Re-run `adb reverse tcp:8080 tcp:8080` (it resets when the phone is unplugged or restarted) or use the right `BASE_URL` above.


## CleverTap Integration Plan (not yet implemented)

The SDK is not added yet. This is the planned event and profile design.

**Setup:** add the CleverTap Android SDK, set the Account ID and Token in the manifest, and initialise it in an `Application` class. Push needs Firebase (FCM) and the notification permission on Android 13+.

**User profile** (send with `onUserLogin` at signup and login; refresh after each trade):
`Name`, `Email`, `Identity` (user id), `Balance`, `Portfolio Value`, `Total P&L`, `Number of Holdings`, `Total Trades`, `Signup Date`, `Last Trade Date`, `Favourite Stock`.

**Events**

| Area | Event | Properties |
|---|---|---|
| Onboarding | `Onboarding Viewed`, `Onboarding Completed`, `Onboarding Skipped` | `Slide Number` |
| Auth | `Signed Up`, `Logged In`, `Logged Out`, `Login Failed` | `Reason` |
| Stocks | `Stocks List Viewed`, `Stock Viewed` | `Symbol`, `Price`, `Change %` |
| Charts | `Chart Range Changed`, `Chart Type Changed` | `Symbol`, `Range`, `Type` |
| Trade | `Trade Initiated`, `Trade Completed`, `Trade Failed` | `Symbol`, `Side`, `Quantity`, `Price`, `Total`, `Failure Reason` |
| Dashboard | `Dashboard Viewed` | `Balance`, `Holdings Count` |
| Errors | `Server Unreachable` | `Screen` |

* Record `Trade Completed` as a Charged event (`pushChargedEvent`), with the trade total as the amount.
* Send `Trade Completed` and the balance/P&L properties from the backend (CleverTap server API), since the server sets the final price. Screen views, taps and chart changes come from the app.

**Dashboard ideas:** funnel (Signed Up → Stock Viewed → Trade Initiated → Trade Completed); segments (never traded, frequent traders, low balance, big losses); retention by signup week; push, in-app and email campaigns (e.g. "no trade in 3 days"); App Inbox for trade summaries.

**Suggested first pass:** auth events, `Stock Viewed`, the trade events and the profile properties.
