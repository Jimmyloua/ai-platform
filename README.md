# AI Open Platform

A comprehensive AI open platform that enables skill acquisition, consumption, and execution across multiple channels (IM, meetings, assistant squares).

## Architecture

### Call Flow
```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     MESSAGE FLOW: CUI/IM → Agent                             │
│                                                                              │
│  ┌─────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐       │
│  │  CUI/IM │───▶│SkillServer  │───▶│SkillGateway │───▶│WeLinkPlugin │──┐    │
│  │ (Client)│    │(Entry Point)│    │  (Router)   │    │  (Agent)    │  │    │
│  └─────────┘    └─────────────┘    └─────────────┘    └─────────────┘  │    │
│       │              │                   │                  │           │    │
│       │              │                   │                  ▼           │    │
│       │              │                   │           ┌─────────────┐    │    │
│       │              │                   │           │ExternalAgent│◀───┘    │
│       │              │                   │           │  (OpenCode) │         │
│       │              │                   │           └─────────────┘         │
│       │              │                   │                   │               │
│       │              │                   └◀──────────────────┘               │
│       │              │                        (Response)                      │
│       │              └◀─────────────────────────────────────────────────────│
│       └◀─────────────────────────────────────────────────────────────────────│
│                                   (Final Response)                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component Architecture
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
│                         Entry Point: SkillServer                            │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │  • WebSocket Handler (CUI/IM connections)                           │     │
│  │  • ExecutionRouter (Gateway routing + local fallback)               │     │
│  │  • Session Management • Message Persistence (MySQL)                 │     │
│  └────────────────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Router: SkillGateway                                │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │  • Connection Pool (Client + Agent connections)                     │     │
│  │  • Session Registry (Redis)                                         │     │
│  │  • Message Router (Session-affinity routing via Redis pub/sub)      │     │
│  │  • AK/SK Authentication • Rate Limiting • Circuit Breaker           │     │
│  └────────────────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Agent: WeLinkPlugin                                 │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │  • Agent Connection Service (registers with Gateway as agent)       │     │
│  │  • External Agent Client (calls OpenCode/other agents)              │     │
│  │  • WeLink Integration (IM message handling)                         │     │
│  │  • Protocol Adaptation (OpenCode ↔ WeLink)                          │     │
│  └────────────────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         External: OpenCode Agent                            │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │  • AI Model Execution (Claude, GPT, etc.)                           │     │
│  │  • Tool/Function Calling • Streaming Responses                      │     │
│  └────────────────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | React 18, TypeScript, Vite, TanStack Query, Zustand |
| Backend | Spring Boot 3.2, Java 21, Spring WebFlux |
| Database | MySQL 8.0 |
| Cache/Pub-Sub | Redis 7.0 |
| Protocol | OpenAI API, Response API, OpenCode Protocol |
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
│   ├── skill-server/           # Entry point: WebSocket, routing
│   ├── skill-gateway/          # Router: Connection management, routing
│   └── welink-plugin/          # Agent: External agent client, WeLink integration
├── shared/                      # Shared libraries
│   └── protocol/               # Protocol definitions
└── infrastructure/              # Docker, K8s configs
```

## Message Flow Details

### 1. Request Flow (CUI → Agent)
```
1. CUI connects to SkillServer via WebSocket
2. User sends skill.execute or agent.execute message
3. SkillServer routes request to SkillGateway via GatewayClient
4. SkillGateway looks up registered agent (WeLinkPlugin) in SessionRegistry
5. SkillGateway routes message to WeLinkPlugin via Redis pub/sub
6. WeLinkPlugin receives message and calls ExternalAgentClient
7. ExternalAgentClient invokes OpenCode/external AI agent
```

### 2. Response Flow (Agent → CUI)
```
1. External agent returns streaming/non-streaming response
2. WeLinkPlugin sends response back to SkillGateway
3. SkillGateway routes response to originating SkillServer
4. SkillServer sends response to CUI via WebSocket
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