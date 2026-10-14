# Размещение витрины: Raspberry Pi + Cloudflare Tunnel

Этот документ описывает запуск. Он **не подтверждает**, что субдомен уже опубликован.
Витрина использует отдельный Compose-проект и новую БД. Не подключайте к ней том
существующей базы с `LocalDateTime` в неизвестном часовом поясе.

## 1. Проверка Raspberry Pi

**На Pi** — команды только читают состояние:

```bash
uname -m
free -h
df -h .
docker info --format 'Architecture={{.Architecture}} MemoryLimit={{.MemoryLimit}}'
docker compose version
ss -ltn '( sport = :8082 )'
systemctl is-active cloudflared
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}'
```

Нужны работающий Docker Compose v2, свободная память для Java/PostgreSQL и место
для образов и БД. Для `aarch64` образы собираются непосредственно на Pi как ARM64.
На 32-битной системе сначала проверьте поддержку всех базовых образов; amd64-образ
с рабочего компьютера не является готовым ARM64-образом. Значение
`MemoryLimit=false` означает, что ограничения памяти Docker не обеспечиваются;
не считайте их активными без проверки хоста.

Java heap ограничен 256 MiB; это не ограничение всей памяти контейнера — JVM также
использует память вне heap.

Порт 8082 должен быть свободен. В противном случае задайте другой
`TELEMETRY_HTTP_PORT` и используйте его также в Cloudflare. Текущие контейнеры
портфолио не нужно останавливать или менять.

## 2. Перенос исходников

После публикации изменений в Git можно клонировать/обновить репозиторий на Pi.
Для ещё не закоммиченных изменений передавайте архив рабочей версии: `git archive`
не включает такие изменения.

**На компьютере**, из корня проекта:

```bash
tar -czf /tmp/telemetry-showcase-source.tar.gz \
  Dockerfile .dockerignore pom.xml mvnw .mvn src \
  simulator.py requirements-simulator.txt showcase deployment \
  compose.showcase.yml .env.showcase.example tests docs README.md
```

Передача **с компьютера на Pi**; замените `<user>` и `<pi-host>` своими значениями:

```bash
scp /tmp/telemetry-showcase-source.tar.gz <user>@<pi-host>:~/telemetry-showcase-source.tar.gz
```

**На Pi**, при первом запуске в новом каталоге:

```bash
mkdir -p ~/telemetry-showcase
cd ~/telemetry-showcase
tar -xzf ~/telemetry-showcase-source.tar.gz
```

Не распаковывайте архив поверх чужих локальных изменений. При обновлении сохраните
свой `.env.showcase`; он не входит в архив.

## 3. Настройка и запуск

**На Pi**, из каталога проекта:

```bash
umask 077
cp .env.showcase.example .env.showcase
nano .env.showcase
```

Замените `POSTGRES_PASSWORD` уникальным паролем. Не присылайте его в чат и не
добавляйте `.env.showcase` в Git. При обновлении существующей установки не копируйте
example повторно: пароль уже инициализированной PostgreSQL должен соответствовать
исходному. Изменение переменной само по себе не меняет пароль внутри БД.

```bash
docker compose --env-file .env.showcase -f compose.showcase.yml config --quiet
docker compose --env-file .env.showcase -f compose.showcase.yml up -d --build --wait --wait-timeout 180
docker compose --env-file .env.showcase -f compose.showcase.yml ps
python3 tests/check_showcase_http.py http://127.0.0.1:8082
```

Первичная сборка на Pi может занимать заметное время. Spring Boot стартует после
готовности PostgreSQL; симулятор — после готовности приложения. Nginx запускается
независимо и может обслуживать описание и песочницу без Java.

Открывается только `127.0.0.1:8082`. База и Java API доступны внутри сети Compose.
Публичный Nginx разрешает GET/HEAD к `/api/v1/demo/snapshot`, отклоняет параметры
запроса и блокирует остальные API, Actuator и Swagger. Снимок кешируется на пять
секунд; ошибки не подменяются старым успешным кешем после истечения срока.

Проверка реального приёма:

```bash
docker compose --env-file .env.showcase -f compose.showcase.yml logs --tail 20 simulator
curl --fail http://127.0.0.1:8082/api/v1/demo/snapshot
```

В журнале ожидается `accepted`, а в JSON — возрастающие ID и UTC-время. В течение
двух минут должны появиться все четыре статуса. Пустой snapshot сразу после
запуска допустим, постоянное отсутствие данных требует проверки журналов:

```bash
docker compose --env-file .env.showcase -f compose.showcase.yml logs --tail 100 app simulator
```

## 4. Cloudflare Tunnel

Если `cloudflared` работает **службой на Pi**, добавьте в существующий tunnel
published application route:

- Hostname: `telemetry.adskiyponchik.dev`.
- Service type: HTTP.
- Service URL: `http://127.0.0.1:8082` (или выбранный свободный порт).

Существующие маршруты основного портфолио сохраните. Не требуется открывать порт
8082 на роутере или публиковать Java/PostgreSQL наружу. При локально управляемом
tunnel добавьте соответствующее ingress-правило перед завершающим catch-all,
проверьте конфигурацию и перезапустите только `cloudflared`.

Если `cloudflared` работает **в контейнере**, его `127.0.0.1` указывает на сам
контейнер. Сначала определите его существующую сеть. Подключите `web` к этой общей
сети постоянным Compose override с уникальным сетевым alias
`telemetry-showcase-web`; tunnel должен обращаться к
`http://telemetry-showcase-web:80`. Сеть БД оставьте отдельной. Не публикуйте
`0.0.0.0:8082` как обход этого различия.

Официальная справка: [Published applications / Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/routing-to-tunnel/).

**С компьютера**, после настройки маршрута:

```bash
python3 tests/check_showcase_http.py https://telemetry.adskiyponchik.dev
```

Откройте страницу по HTTPS и убедитесь, что время показаний обновляется, EN/DE
работают и надпись Preview отсутствует. При ошибке 502 сначала проверьте локальный
адрес с Pi; успешная локальная проверка и неуспешная внешняя указывают на маршрут
или tunnel, а не подтверждают проблему Spring Boot.

## 5. Проверка остановки и восстановление

**На Pi** — этот сценарий временно останавливает только демонстрационный поток:

```bash
docker compose --env-file .env.showcase -f compose.showcase.yml stop simulator
```

После 20 секунд без новых показаний страница должна показать устаревшие данные.
Затем:

```bash
docker compose --env-file .env.showcase -f compose.showcase.yml stop app
```

После истечения кеша эфир сообщает о потере связи. Страница и локальная песочница
должны оставаться доступны. Восстановление:

```bash
docker compose --env-file .env.showcase -f compose.showcase.yml up -d --wait --wait-timeout 180
```

При обновлении используйте ту же команду с `--build`; дождитесь готовности и
повторите HTTP-проверку. Для отката верните предыдущую проверенную версию
исходников/образов и пересоздайте сервисы с тем же `.env.showcase` и томом БД.
В этой версии изменения схемы БД не требуются.

Остановка витрины с сохранением данных:

```bash
docker compose --env-file .env.showcase -f compose.showcase.yml down
```

Не добавляйте `-v`, если не собираетесь удалить демонстрационную БД. Очистка записей
старше семи дней выполняется сервисом автоматически, только для демодатчика.
