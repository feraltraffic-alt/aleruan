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

## Настройки

| Переменная | Где | Зачем |
| --- | --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | бэкенд | PostgreSQL |
| `KEYCLOAK_ISSUER` | бэкенд | Issuer Keycloak, например `http://localhost:8180/realms/aleruan` |
| `DEALOS_SANCTIONS_URL` | бэкенд | Ссылка на XML санкционного списка ЕС с личным токеном FSF (бесплатная регистрация на webgate.ec.europa.eu/fsd/fsf). Пусто — проверка по санкциям выключена |
| `DEALOS_MARGIN_WARNING_PCT` | бэкенд | Порог маржи сделки (% от цены), ниже которого показывается предупреждение. По умолчанию 10 |
| `VITE_KEYCLOAK_URL` | фронтенд | Включает вход через Keycloak, например `http://localhost:8180` |
| `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT` | фронтенд | По умолчанию `aleruan` и `dealos-web` |
| `VITE_USE_MOCKS=true` | фронтенд | Демо-режим без бэкенда |

Профиль `local` (`-Dspring-boot.run.profiles=local`) отключает Keycloak: все запросы выполняются как администратор.

## Проверка контрагентов

| Страна | Источники | Что проверяется |
| --- | --- | --- |
| Любая страна ЕС с VAT | VIES | Действительность VAT, номер консультации как доказательство |
| CZ | ARES, Registr DPH | Реквизиты, дата регистрации, ликвидация, инсольвенция, ненадёжный плательщик, опубликованные счета |
| PL | Biała lista, KRS | Статус НДС, счета, ликвидация/банкротство |
| Все | Санкционный список ЕС | Совпадение названия — жёлтый статус, решение за человеком |
| Вне ЕС | — | Жёлтый статус «нужна ручная проверка» |

Каждый ответ реестра сохраняется целиком вместе с номером запроса.
