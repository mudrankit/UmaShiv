# AI-Powered System Design Interview Assistant

A responsive full-stack web app for practicing system design interviews. Users can ask prompts like `Design Uber` or `Design WhatsApp` and get:

- High-level architecture
- Low-level design
- Core components
- Trade-offs
- Follow-up questions
- Interviewer-mode prompts
- A quick interview-readiness rating

## Stack

- Frontend: React + Vite
- Backend: Spring Boot
- AI providers: OpenAI, Google Gemini, Groq, plus a built-in demo fallback

## Project structure

```text
.
|-- backend
|-- frontend
`-- README.md
```

## Backend setup

From [backend](D:\New project\backend):

```powershell
cd D:\New project\backend
mvn spring-boot:run
```

Environment variables:

```powershell
$env:APP_AI_PROVIDER="groq"
$env:APP_AI_GROQ_API_KEY="your_groq_api_key"
$env:APP_AI_GROQ_MODEL="llama-3.1-8b-instant"

# Or use Gemini
$env:APP_AI_GEMINI_API_KEY="your_gemini_api_key"
$env:APP_AI_GEMINI_MODEL="gemini-2.5-flash"

# Or use OpenAI
$env:APP_AI_OPENAI_API_KEY="your_openai_api_key"
$env:APP_AI_OPENAI_MODEL="gpt-4o-mini"
```

If no provider key is configured, the app still works in demo mode with structured mock responses. Users can also choose the provider and model directly from the UI before each generation.

## Frontend setup

From [frontend](D:\New project\frontend):

```powershell
cd D:\New project\frontend
npm install
npm run dev
```

Optional frontend environment file:

```text
VITE_API_BASE_URL=http://localhost:8080
```

If `VITE_API_BASE_URL` is not set, the Vite dev proxy forwards `/api` requests to the Spring Boot backend.

## API endpoints

- `POST /api/design/generate`
- `POST /api/design/rate`

Sample generate payload:

```json
{
  "prompt": "Design Uber",
  "interviewerMode": true
}
```

## Product notes

- Responsive layout for desktop and mobile
- Example prompt chips for quick demos
- Follow-up interviewer mode toggle
- Design rating feedback card
- Provider and model picker for OpenAI, Gemini, Groq, or demo mode

## Next improvements

- Persist prompt history and saved designs
- Add Redis caching for repeated prompts
- Support multiple AI providers through a strategy layer
- Render diagrams for architecture flows

## Authentication setup

The app now requires sign-up or login before the assistant can be used. Sessions are persisted on the same device using a backend session token and browser local storage.

Backend auth environment variables:

```powershell
$env:APP_AUTH_GOOGLE_CLIENT_ID="your_google_client_id"
$env:APP_AUTH_STORE_FILE="./data/auth-store.json"
$env:APP_AUTH_SESSION_DAYS="30"
```

Frontend environment variables:

```text
VITE_API_BASE_URL=http://localhost:8080
VITE_GOOGLE_CLIENT_ID=your_google_client_id
```

Notes:
- Regular email, username, and password registration works without extra setup.
- Google sign-in requires the same Google client ID to be configured in both backend and frontend.
- Authenticated sessions are remembered on the same device until logout or expiry.
## Production deployment

For production, deploy the frontend and backend separately and use Postgres for authentication data and remembered sessions.

Recommended stack:
- Frontend: Vercel
- Backend: Render Web Service
- Database: Render Postgres or Neon Postgres

Backend production environment variables:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<database>
SPRING_DATASOURCE_USERNAME=<username>
SPRING_DATASOURCE_PASSWORD=<password>
SPRING_JPA_HIBERNATE_DDL_AUTO=update
APP_AUTH_GOOGLE_CLIENT_ID=<google_client_id>
APP_AUTH_SESSION_DAYS=30
APP_WEB_ALLOWED_ORIGINS=https://your-frontend-domain.com
APP_AI_PROVIDER=groq
APP_AI_GROQ_API_KEY=<groq_key>
APP_AI_GROQ_MODEL=llama-3.1-8b-instant
APP_AI_GEMINI_API_KEY=<gemini_key>
APP_AI_GEMINI_MODEL=gemini-2.5-flash
APP_AI_OPENAI_API_KEY=<openai_key>
APP_AI_OPENAI_MODEL=gpt-4o-mini
```

Frontend production environment variables:

```text
VITE_API_BASE_URL=https://your-backend-domain.com
VITE_GOOGLE_CLIENT_ID=<google_client_id>
```

Google sign-in notes:
- Add your production frontend domain to Google OAuth authorized JavaScript origins.
- Use the same Google client ID in both frontend and backend.
- Deploy the backend over HTTPS so browser auth flows work correctly.
## Operations notes

- Backend health endpoint: `/api/health`
- Set `APP_WEB_ALLOWED_ORIGINS` to your deployed frontend origin, for example `https://your-frontend-domain.com`
- Keep Google OAuth authorized JavaScript origins aligned with the same frontend domain
- The repository includes `render.yaml` and `frontend/vercel.json` as deployment starting points