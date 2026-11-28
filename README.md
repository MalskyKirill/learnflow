# LearnFlow

Учебная платформа для курса по ORM и Hibernate. Модель на Spring Data JPA хранит пользователей, курсы, модули, уроки, записи на курсы, задания, решения, отзывы и результаты тестов в PostgreSQL.

## Локальный запуск

Понадобятся JDK 21 и запущенный Docker с Docker Compose. Maven устанавливается через Maven Wrapper.

Если файла `.env` ещё нет, выполни `cp .env.example .env` и задай в нём `POSTGRES_PASSWORD`. При занятом порте 5432 укажи свободный порт в `POSTGRES_PORT`.

```sh
docker compose up -d --wait postgres
./mvnw spring-boot:run
```

Spring использует профиль `dev` по умолчанию. Hibernate создаёт или обновляет таблицы при старте. Чтобы получить список курсов, выполни в другом терминале:

```sh
curl http://localhost:8080/api/courses
```

Для остановки PostgreSQL используй `docker compose stop postgres`.

### Демо-курс

Для первого запуска демо задай `DEMO_TEACHER_PASSWORD` и `DEMO_STUDENT_PASSWORD` в файле `.env` и запусти приложение с профилями `dev,demo`.

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo
```

Профиль создаёт категорию «ORM и Hibernate», учётные записи `teacher@demo.learnflow.example` и `student@demo.learnflow.example` и курс «Практика ORM с Hibernate» с уроком, заданием и викториной. Студент уже записан на курс. Повторный запуск сохраняет прогресс и пароли.

Чтобы узнать ID курса, запроси список курсов студента.

```sh
curl -fsS --user student@demo.learnflow.example http://localhost:8080/api/me/courses
```

С этим ID открой `GET /api/courses/{courseId}/program`. В ответе будут ID модуля и урока для просмотра викторины и задания.

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

Понадобятся `curl` и `jq`. Укажи email существующих учётных записей преподавателя и студента с настроенными паролями и ID категории курса. `curl` запрашивает пароль при каждом защищённом запросе.

```sh
API=http://localhost:8080/api
TEACHER=teacher@demo.learnflow.example
STUDENT=student@demo.learnflow.example
CATEGORY_ID=1

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
option_id=$(printf '%s' "$quiz" | jq -er '.questions[0].options[1].id')
attempt_body=$(jq -nc --arg questionId "$question_id" --argjson optionId "$option_id" \
  '{answers:{($questionId):$optionId}}')
student_write "$API/quizzes/$quiz_id/attempts" --data "$attempt_body"
curl -fsS --user "$STUDENT" "$API/me/quiz-attempts"
curl -fsS --user "$TEACHER" "$API/quizzes/$quiz_id/attempts"
```

## Тесты

```sh
./mvnw verify
```

Интеграционные тесты используют профиль `test` и запускают отдельный PostgreSQL 16 через Testcontainers. Для проверки достаточно запущенного Docker. В Windows вместо `./mvnw` используй `mvnw.cmd`.

`./mvnw -Dtest=RestApiWorkflowTest test` запускает оба сценария через HTTP на случайном порту. Тест сам создаёт учётные записи и категорию в отдельной тестовой базе.
