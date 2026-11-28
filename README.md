# LearnFlow

Учебная платформа для курса по ORM и Hibernate. Модель на Spring Data JPA хранит пользователей, курсы, модули, уроки, записи на курсы, задания, решения, отзывы и результаты тестов в PostgreSQL.

## Быстрый запуск демо

Понадобится запущенный Docker с Docker Compose. Скопируй `.env.example` в `.env`, если его ещё нет. Задай в нём `POSTGRES_PASSWORD`, `DEMO_TEACHER_PASSWORD` и `DEMO_STUDENT_PASSWORD`.

```sh
SPRING_PROFILES_ACTIVE=dev,demo docker compose up -d --build --wait
curl -fsS http://localhost:8080/api/courses
```

Профиль `demo` создаёт категорию «ORM и Hibernate» и курс «Практика ORM с Hibernate» с уроком, заданием и викториной. Студент уже записан на курс. Повторный запуск сохраняет прогресс и пароли.

| Роль | Email для входа | Пароль из `.env` |
|---|---|---|
| Преподаватель | `teacher@demo.learnflow.example` | `DEMO_TEACHER_PASSWORD` |
| Студент | `student@demo.learnflow.example` | `DEMO_STUDENT_PASSWORD` |

Чтобы узнать ID демо-курса, запроси список курсов студента.

```sh
curl -fsS --user student@demo.learnflow.example http://localhost:8080/api/me/courses
```

С этим ID открой `GET /api/courses/{courseId}/program`. В программе будут ID модуля и урока для просмотра викторины и задания.

При занятых портах задай `APP_PORT` и `POSTGRES_PORT` в `.env`. После смены `APP_PORT` обнови порт в запросах. Для остановки выполни `docker compose down`. Данные PostgreSQL сохраняются в томе `postgres_data`.

### Локальная разработка

На хосте понадобится JDK 21. Compose поднимет PostgreSQL, Maven Wrapper загрузит Maven, а Hibernate создаст или обновит схему при старте приложения.

```sh
docker compose up -d --wait postgres
./mvnw spring-boot:run
```

Профиль `dev` активен по умолчанию. Для демо при запуске Maven добавь `-Dspring-boot.run.profiles=dev,demo`. После остановки приложения PostgreSQL можно остановить командой `docker compose stop postgres`.

## Модель и структура проекта

В модели 15 сущностей.

| Область | Сущности и связи |
|---|---|
| Учётные записи | `User` и `Profile` связаны один к одному общим идентификатором. |
| Программа | В `Category` входят `Course`. Преподаватель ведёт курс с упорядоченными `CourseModule`, а каждый модуль содержит `Lesson`. |
| Запись и отзывы | `Enrollment` связывает студентов и курсы отношением многие ко многим. `CourseReview` хранит отзыв студента о курсе. |
| Домашняя работа | Урок может содержать несколько `Assignment`. Студент отправляет одну `Submission` на задание. В решении хранятся балл и комментарий преподавателя. |
| Викторины | Модуль может содержать одну `Quiz` с `Question` и `AnswerOption`. `QuizSubmission` хранит балл за попытку, а `StudentAnswer` — выбранный вариант для каждого отвеченного вопроса. |

Основной код находится в пакете `ru.mephi.learnflow`.

| Пакеты | Ответственность |
|---|---|
| `controller`, `dto` | REST-маршруты, тела запросов, валидация и ответы. |
| `service` | Учебные сценарии, проверка полномочий и транзакции. |
| `entity`, `repository` | JPA-сущности, правила предметной области, ограничения и запросы к PostgreSQL. |
| `security`, `exception` | HTTP Basic, CSRF и ответы об ошибках. |
| `demo` | Начальное наполнение при активном профиле `demo`. |

`spring.jpa.open-in-view` отключён. Программа загружает модули с уроками через `JOIN FETCH`. Викторина загружает вопросы через `@EntityGraph`, затем отдельно получает варианты ответов. Сервисы собирают DTO внутри транзакции. `QuizServiceTest` проверяет, что DTO читается после завершения транзакции, а обращение к незагруженной коллекции вне неё вызывает `LazyInitializationException`.

## REST API

Список и карточки курсов доступны без входа. Для остальных маршрутов используй HTTP Basic с email и паролем учётной записи LearnFlow. Для `POST`, `PUT` и `DELETE` также передавай cookie и CSRF-заголовок из `GET /api/csrf`.

| Раздел | Маршруты |
|---|---|
| Профиль | `GET /api/me`, `GET /api/csrf` |
| Курсы | `GET/POST /api/courses`, `GET/PUT/DELETE /api/courses/{courseId}` |
| Программа | `GET /api/courses/{courseId}/program`, `GET/POST /api/courses/{courseId}/modules`, `GET/PUT/DELETE /api/modules/{moduleId}`, `POST /api/modules/{moduleId}/lessons`, `GET/PUT/DELETE /api/lessons/{lessonId}` |
| Запись на курс | `GET/POST/DELETE /api/courses/{courseId}/enrollment`, `GET /api/me/courses`, `GET /api/courses/{courseId}/students` |
| Задания и решения | `GET/POST /api/lessons/{lessonId}/assignments`, `GET/PUT/DELETE /api/assignments/{assignmentId}`, `GET/POST /api/assignments/{assignmentId}/submissions`, `GET /api/submissions/{submissionId}`, `PUT /api/submissions/{submissionId}/grade`, `GET /api/me/submissions` |
| Викторины | `GET/POST /api/modules/{moduleId}/quiz`, `GET /api/quizzes/{quizId}`, `POST /api/quizzes/{quizId}/questions`, `GET /api/quizzes/{quizId}/questions/{questionId}` |
| Попытки | `GET/POST /api/quizzes/{quizId}/attempts`, `GET /api/quiz-attempts/{attemptId}`, `GET /api/me/quiz-attempts` |

При создании ресурса API возвращает `201 Created` и заголовок `Location`. Ошибки приходят в формате `application/problem+json`. При ошибках валидации тело ответа включает список полей с сообщениями.

Преподаватель курса может редактировать и удалять модули и уроки. Удаление модуля вместе с уроками и заданиями возвращает `204 No Content`. Если с модулем связана викторина или к его заданиям есть решения, API возвращает `409 Conflict`. Такое же ограничение действует на удаление урока с решениями.

Только преподаватель курса может редактировать и удалять задания. Если к заданию есть решения, удаление возвращает `409 Conflict`. Этот же статус возвращает попытка установить максимум ниже уже выставленной оценки.

### Подготовка примеров

Запусти демо и выполни следующие блоки в одном терминале с `curl` и `jq`. Примеры создают отдельный курс в демо-категории. Когда `curl` запросит пароль, введи значение для этой учётной записи из `.env`.

```sh
API=http://localhost:8080/api
TEACHER=teacher@demo.learnflow.example
STUDENT=student@demo.learnflow.example
CATEGORY_ID=$(curl -fsS "$API/courses" |
  jq -er 'first(.[] | select(.title == "Практика ORM с Hibernate")) | .categoryId')

teacher_cookie=$(mktemp)
student_cookie=$(mktemp)

csrf_header() {
  curl -fsS --user "$1" --cookie-jar "$2" "$API/csrf" |
    jq -er '"\(.headerName): \(.token)"'
}

teacher_csrf=$(csrf_header "$TEACHER" "$teacher_cookie")
student_csrf=$(csrf_header "$STUDENT" "$student_cookie")

teacher_write() {
  curl -fsS --user "$TEACHER" --cookie "$teacher_cookie" \
    --header "$teacher_csrf" --header 'Content-Type: application/json' "$@"
}

student_write() {
  curl -fsS --user "$STUDENT" --cookie "$student_cookie" \
    --header "$student_csrf" --header 'Content-Type: application/json' "$@"
}
```

### Задание и оценка

Преподаватель создаёт курс, модуль, урок и задание. Студент записывается на курс, отправляет решение и читает выставленную оценку.

```sh
course_body=$(jq -nc --argjson categoryId "$CATEGORY_ID" \
  '{title:"Hibernate", categoryId:$categoryId}')
course_id=$(teacher_write "$API/courses" --data "$course_body" | jq -er .id)
module_id=$(teacher_write "$API/courses/$course_id/modules" \
  --data '{"title":"Основы JPA"}' | jq -er .id)
lesson_id=$(teacher_write "$API/modules/$module_id/lessons" \
  --data '{"title":"Связи сущностей"}' | jq -er .id)
assignment_id=$(teacher_write "$API/lessons/$lesson_id/assignments" \
  --data '{"title":"Опиши связь урока с модулем","maxScore":20}' | jq -er .id)

student_write --request POST "$API/courses/$course_id/enrollment"
submission_id=$(student_write "$API/assignments/$assignment_id/submissions" \
  --data '{"content":"Урок ссылается на модуль через ManyToOne"}' | jq -er .id)
teacher_write --request PUT "$API/submissions/$submission_id/grade" \
  --data '{"score":19,"feedback":"Добавь пример внешнего ключа"}'
curl -fsS --user "$STUDENT" "$API/me/submissions"
```

### Викторина и результаты

Преподаватель добавляет викторину к созданному модулю. Студент получает вопросы и отправляет выбранные варианты. В поле `answers` ключом служит ID вопроса, значением — ID варианта ответа.

```sh
quiz_id=$(teacher_write "$API/modules/$module_id/quiz" \
  --data '{"title":"Связи сущностей"}' | jq -er .id)
teacher_write "$API/quizzes/$quiz_id/questions" \
  --data '{"text":"Какая аннотация нужна для связи многие к одному?",
           "options":[{"text":"@OneToMany","correct":false},
                      {"text":"@ManyToOne","correct":true}]}'

quiz=$(curl -fsS --user "$STUDENT" "$API/modules/$module_id/quiz")
question_id=$(printf '%s' "$quiz" | jq -er '.questions[0].id')
option_id=$(printf '%s' "$quiz" | jq -er '.questions[0].options[] | select(.text == "@ManyToOne") | .id')
attempt_body=$(jq -nc --arg questionId "$question_id" --argjson optionId "$option_id" \
  '{answers:{($questionId):$optionId}}')
student_write "$API/quizzes/$quiz_id/attempts" --data "$attempt_body"
curl -fsS --user "$STUDENT" "$API/me/quiz-attempts"
curl -fsS --user "$TEACHER" "$API/quizzes/$quiz_id/attempts"
rm -f "$teacher_cookie" "$student_cookie"
```

## Тесты

Юнит-тесты правил записи, оценивания и прохождения викторин запускаются без Docker.

```sh
./mvnw '-Dtest=*UnitTest' test
```

Полный набор тестов запускается командой ниже.

```sh
./mvnw verify
```

Интеграционные тесты используют профиль `test` и запускают отдельный PostgreSQL 16 через Testcontainers. Нужен запущенный Docker. В Windows вместо `./mvnw` используй `mvnw.cmd`.

| Что проверить | Команда Maven Wrapper |
|---|---|
| Сдача задания, выставление оценки и история попыток через HTTP | `./mvnw -Dtest=RestApiWorkflowTest test` |
| Правила PostgreSQL и каскадное удаление | `./mvnw '-Dtest=*PersistenceTest,CourseStructureRepositoryTest' test` |
| Проверка ленивой загрузки и количества SQL-запросов | `./mvnw -Dtest=QuizServiceTest test` |
| Повторный запуск демо с сохранением прогресса | `./mvnw -Dtest=DemoDataInitializerTest test` |

[GitHub Actions](.github/workflows/ci.yml) запускает полный набор тестов и собирает Docker-образ для пушей и пул-реквестов в `main`. Текстовые отчёты Surefire находятся в `target/surefire-reports` и доступны в артефакте `surefire-reports` после выполнения workflow.
