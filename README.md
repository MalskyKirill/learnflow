# LearnFlow

Учебная платформа для курса по ORM и Hibernate. Модель на Spring Data JPA хранит пользователей, курсы, модули, уроки, записи на курсы, задания, решения, отзывы и результаты тестов в PostgreSQL.

## Локальный запуск

Понадобятся JDK 21 и запущенный Docker с Docker Compose. Maven устанавливается через Maven Wrapper.

Если файла `.env` ещё нет, выполни `cp .env.example .env` и задай в нём `POSTGRES_PASSWORD`. При занятом порте 5432 укажи свободный порт в `POSTGRES_PORT`.

```sh
docker compose up -d --wait postgres
./mvnw spring-boot:run
```

Spring использует профиль `dev` по умолчанию. Hibernate создаёт или обновляет таблицы при старте. Сейчас точка входа инициализирует подключение к базе и завершает работу. Для остановки PostgreSQL используй `docker compose stop postgres`.

## Тесты

```sh
./mvnw verify
```

Интеграционные тесты используют профиль `test` и запускают отдельный PostgreSQL 16 через Testcontainers. Для проверки достаточно запущенного Docker. В Windows вместо `./mvnw` используй `mvnw.cmd`.
