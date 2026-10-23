# Aleruan Deal OS

Система управления сделками Aleruan-Industry: сделки и маржа, проверка контрагентов по госреестрам,
стройка (сметы, график, приёмка, доп. работы, стройжурнал, дефекты), фактуры, доказательный пакет,
склад и доставка.

Техническая спецификация: https://claude.ai/artifact/M9PdsMKyoC64cbiyBVYVJu

## Структура

| Папка | Что внутри |
| --- | --- |
| `backend/` | Java 21, Spring Boot 3, Spring Modulith — модульный монолит |
| `frontend/` | React + TypeScript (Vite), PWA |
| `infra/` | docker-compose для локальной разработки: PostgreSQL, Keycloak, MinIO |
| `.github/workflows/` | CI: сборка и тесты бэкенда и фронтенда |

## Быстрый старт

```bash
# 1. Инфраструктура
cd infra && cp .env.example .env && docker compose up -d

# 2. Бэкенд (профиль local — без авторизации)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local

# 3. Фронтенд
cd frontend && npm install && npm run dev
```

Фронтенд без бэкенда: `VITE_USE_MOCKS=true npm run dev` — работает на демо-данных.
