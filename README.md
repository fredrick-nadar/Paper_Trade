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

## CleverTap Integration

**Implemented**

| Area | Status |
|---|---|
| Android push | FCM service, token registration, viewed/clicked tracking, Android 13+ permission flow, fallback notification. |
| In-app | SDK listeners, custom templates (Message card, Push Primer), tab-navigation button actions, in-app events. |
| Profile and events | `onUserLogin`, `Signed Up`, `Logged In`, `Stock Viewed`, `Portfolio Viewed`, `Trade Placed`, `Trade Failed`, and a Charged event per trade (payment mode "Virtual Balance"). |
| First-trade push | The backend returns `firstTrade` on `POST /trades`; the app sends `First Trade Completed` (once per user) and the `First Trade Date` profile property. A push campaign triggered on that event is verified working. |

**Not yet implemented:** App Inbox, Native Display, rich push, onboarding/chart events, `Logged Out` and `Login Failed`.

**Profile properties:** `Name`, `Email`, `Identity` (random public id), `Account Balance`, `Holdings Count`, `Total Trades`, `Last Trade Symbol`, `Last Trade Date`, `First Trade Date`.
