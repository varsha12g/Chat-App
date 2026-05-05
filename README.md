# Relay Chat

Relay Chat is a full-stack realtime chat application with JWT authentication, MongoDB persistence, protected message history, one-to-one rooms, group rooms, and STOMP over SockJS live messaging.

## Architecture

- `backend/`: Spring Boot 3, Java 17, Spring Security, BCrypt, JWT, MongoDB, STOMP WebSocket endpoint at `/chat`.
- `frontend/`: React + Vite app with register, login, protected chat workspace, SockJS/STOMP client, room creation, live messages, and historical message loading.
- MongoDB stores users, rooms, and messages. REST handles auth, room discovery, room creation, users, and history. WebSocket handles live send/subscribe.

## Backend API

- `POST /api/auth/register`: username, email, password.
- `POST /api/auth/login`: username/email as `identifier`, password.
- `GET /api/messages/{roomId}`: protected message history for room members.
- `GET /api/rooms`, `POST /api/rooms`: protected room list and creation.
- `GET /api/users`, `GET /api/users/me`: protected user lookup.
- WebSocket endpoint: `/chat`.
- Publish: `/app/sendMessage/{roomId}`.
- Subscribe: `/topic/{roomId}`.

## Local Setup

### Backend

```bash
cd backend
cp .env.example .env
```

Set `MONGODB_URI`, `JWT_SECRET`, `ALLOWED_ORIGINS`, and `PORT` in your shell or deployment environment. For local development:

```bash
export MONGODB_URI=mongodb://localhost:27017/realtime_chat
export JWT_SECRET=replace-with-a-random-secret-of-at-least-32-characters
export ALLOWED_ORIGINS=http://localhost:5173
export PORT=8080
mvn spring-boot:run
```

The backend also imports `backend/.env` automatically for local runs. Railway should still use dashboard environment variables.

### Frontend

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

Set `VITE_BACKEND_URL=http://localhost:8080` locally.

## Railway Backend Deployment

1. Create a Railway project from this repo and set the root directory to `backend`.
2. Add a MongoDB service or use MongoDB Atlas.
3. Configure environment variables:
   - `MONGODB_URI`
   - `JWT_SECRET`
   - `ALLOWED_ORIGINS=https://your-vercel-app.vercel.app`
   - `PORT` is provided by Railway.
4. Deploy. The project includes `railway.toml`, `Procfile`, and `system.properties`.

## Vercel Frontend Deployment

1. Import the repo into Vercel and set the root directory to `frontend`.
2. Set `VITE_BACKEND_URL=https://your-railway-backend.up.railway.app`.
3. Build command: `npm run build`.
4. Output directory: `dist`.

`frontend/vercel.json` rewrites all routes to `index.html`, so client-side routing works on refresh.

## Implementation Notes

- JWT auth is stateless for REST and validated again on STOMP `CONNECT`.
- Passwords are hashed with BCrypt before persistence.
- Rooms enforce membership before history fetches and message publishing.
- CORS and SockJS origins are driven by `ALLOWED_ORIGINS`.
- REST CORS allows credentials, and the frontend sends `credentials: include`. Auth is JWT-based through the `Authorization` header, so no cross-site auth cookie is required.
- The UI uses a fresh Relay visual identity with a split auth layout, compact chat workspace, responsive room rail, and practical modal-based room creation.
