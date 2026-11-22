# Deploying I-ITSM to the cloud

The whole system (PostgreSQL, Spring Boot backend, web app) runs from one file, `docker-compose.prod.yml`, on any
machine with Docker. The web container (nginx) is the only thing exposed to the internet: it serves the site and
forwards `/api` and `/ws` to the backend, exactly like the Vite dev server does locally.

```
Internet ──▶ :80 web (nginx) ──/api, /ws──▶ backend (Spring Boot :8080) ──▶ postgres (:5432, private)
                 └── static React app (PWA)
Mobile app ──▶ same address (EXPO_PUBLIC_API_URL)
```

## Option A (recommended): one cloud VM with Docker

Works on any provider. Student-friendly ones (check current terms when you sign up; free tiers change):

| Provider | What is free | Notes |
|---|---|---|
| **Azure for Students** | Credit for 12 months, no credit card, sign up with your university email | Create an Ubuntu VM (B1ms or B2s is plenty) |
| **Oracle Cloud Always Free** | Ampere ARM VM, free indefinitely | Needs a card for identity verification; all images used here run on ARM |
| **GitHub Student Developer Pack** | Credits at DigitalOcean and others | Droplet with 2 GB RAM |

Steps (Ubuntu VM, about 15 minutes):

1. **Create the VM** (Ubuntu 24.04, 2 GB RAM or more) and allow inbound ports **22** (SSH) and **80** (HTTP) in the
   provider's firewall / network security group. Add **443** if you will set up HTTPS.
2. **Install Docker** on the VM:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker $USER && newgrp docker
   ```
3. **Get the code and set the secrets:**
   ```bash
   git clone https://github.com/Dileep1714/I-ITSM.git && cd I-ITSM
   cp .env.example .env
   nano .env      # POSTGRES_PASSWORD, JWT_SECRET (openssl rand -base64 48), PUBLIC_URL=http://<VM public IP>,
                  # GEMINI_API_KEY (optional, enables the AI copilot)
   ```
4. **Start everything:**
   ```bash
   docker compose -f docker-compose.prod.yml up -d --build
   docker compose -f docker-compose.prod.yml logs -f backend   # wait for "Started BackendApplication"
   ```
5. Open **http://&lt;VM public IP&gt;/** and sign in with a demo account (password `Password123`).
   Set `SEED_DEMO_DATA=false` in `.env` for a system without demo data.

Updating after new commits: `git pull && docker compose -f docker-compose.prod.yml up -d --build`.
The database lives in the `iitsm-pgdata` Docker volume and survives restarts and rebuilds.

### HTTPS (optional, recommended)

Needed for the installable PWA on phones and for secure cookies. Point a domain at the VM (a free one from DuckDNS
works), then put Caddy in front, which obtains and renews certificates automatically: change the web service's
port to `8080:80`, run Caddy with `reverse_proxy localhost:8080` for your domain, open port 443, and set
`PUBLIC_URL=https://your-domain`.

## Option B: platform services (no server to manage)

On **Render** (or similar): create a PostgreSQL database, a **Web Service** from `backend/Dockerfile` with the same
environment variables as in `docker-compose.prod.yml`, and a second Web Service from `frontend/Dockerfile` with
`backend` in `nginx.conf` replaced by the backend's address. Free plans there usually sleep when idle (the first
request then takes about a minute) and free databases may expire, so Option A is more reliable for a demo.

## The mobile app against the deployed server

Build the APK with the server address baked in; it then works on any network:

```bash
cd mobile
EXPO_PUBLIC_API_URL=http://<VM public IP or https://your-domain> npx eas-cli build -p android --profile preview
```

## Security checklist

- Never commit `.env` or `backend/ai.local.properties` (both are git-ignored).
- Use a long random `JWT_SECRET` and `POSTGRES_PASSWORD`; the defaults in `application.properties` are for local
  development only.
- Only port 80/443 is public; PostgreSQL and the backend are reachable only inside Docker's network.
