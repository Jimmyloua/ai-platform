# AI Open Platform

A comprehensive AI open platform that enables skill acquisition, consumption, and execution across multiple channels (IM, meetings, assistant squares).

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Front-end Layer                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    CUI (React Application)                           │    │
│  │  • OpenAI Protocol Support • Response API • Thinking/Markdown/MCP  │    │
│  │  • IM Embedding • Meeting Integration • Assistant Square             │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Platform Layer                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────────────┐   │
│  │    Skill     │  │    Skill     │  │         Skill Server             │   │
│  │  Acquisition │  │ Consumption  │  │  • WebSocket Connections          │   │
│  │              │  │              │  │  • Session Management            │   │
│  │              │  │              │  │  • Message Persistence (MySQL)    │   │
│  │              │  │              │  │  • IM Message Delivery            │   │
│  │              │  │              │  │  • Protocol Adaptation           │   │
│  └──────────────┘  └──────────────┘  └──────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Connection Layer                                  │
│  ┌─────────────────────────────┐  ┌────────────────────────────────────┐    │
│  │       Skill Gateway         │  │     Welink Connection Plugin       │    │
│  │  • AK/SK Auth               │  │  • OpenCode Integration            │    │
│  │  • WebSocket Management     │  │  • Config Management               │    │
│  │  • Multi-instance (Redis)   │  │  • AK/SK Authentication            │    │
│  │  • Protocol Adaptation      │  │  • Welink WebSocket Protocol       │    │
│  └─────────────────────────────┘  └────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | React 18, TypeScript, Vite, TanStack Query, Zustand |
| Backend | Spring Boot 3.2, Java 21, Spring WebFlux |
| Database | MySQL 8.0 |
| Cache/Pub-Sub | Redis 7.0 |
| Protocol | OpenAI API, Response API, WebSocket |
| Auth | AK/SK (HMAC-SHA256), JWT |
| Container | Docker, Kubernetes |

## Project Structure

```
aiPlatform/
├── frontend/                    # React application
│   ├── packages/
│   │   ├── cui/                # Core CUI component library
│   │   ├── cui-react/          # React bindings
│   │   └── cui-sdk/            # API client SDK
│   └── apps/
│       └── web/                # Main web application
├── backend/                     # Spring Boot services
│   ├── skill-server/           # Core skill server
│   ├── skill-gateway/          # API Gateway
│   └── welink-plugin/          # Welink integration
├── shared/                      # Shared libraries
│   └── protocol/               # Protocol definitions
└── infrastructure/              # Docker, K8s configs
```

## Getting Started

### Prerequisites
- Java 21+
- Node.js 18+
- pnpm 8+
- Docker & Docker Compose
- MySQL 8.0
- Redis 7.0

### Quick Start

```bash
# Start infrastructure
docker-compose up -d

# Backend
cd backend
./mvnw spring-boot:run

# Frontend
cd frontend
pnpm install
pnpm dev
```

## Development

See individual package READMEs for detailed development instructions.

## License

MIT