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

1. Start the PostgreSQL database using Docker:
   ```bash
   docker run --name paper-trade-db \
     -e POSTGRES_DB=paper_trade \
     -e POSTGRES_USER=papertrade \
     -e POSTGRES_PASSWORD=papertrade \
     -p 5432:5432 \
     -v paper-trade-data:/var/lib/postgresql \
     -d postgres:18
   ```
2. Open the `Paper_Trade_backend` project in your IDE (e.g., IntelliJ IDEA) and run the Spring Boot application. It runs on `http://localhost:8080`.

### Frontend

1. Open the `Paper_Trade_frontend` project in Android Studio.
2. Build and run the application on an Android Emulator.
3. Note: The Android emulator communicates with the local Mac backend via `http://10.0.2.2:8080`.

