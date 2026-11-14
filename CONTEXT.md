# CONTEXT — ISEU Schedule Android App

> **Назначение документа:** Полный контекст архитектуры, сетевого протокола, модели данных, механизмов кеширования и последних доработок Android-приложения **ISEU Schedule**. Предназначен для разработчиков и ИИ-ассистентов.

---

## 📌 О проекте

**ISEU Schedule** — легковесное, быстрое и полностью автономное Android-приложение для просмотра расписания занятий студентов Международного государственного экологического института им. А.Д. Сахарова БГУ (МГЭИ им. А.Д. Сахарова БГУ).

- **Текущая версия:** `v1.1.0` (Production-ready).
- **Application ID:** `by.iseu.schedule`
- **Min SDK:** `26` (Android 8.0 Oreo) | **Target SDK:** `34` (Android 14 UpsideDownCake).
- **Размер сборки:** ~1.65 МБ (оптимизировано с помощью R8 / ProGuard, урезаны лишние локали через `localeFilters += listOf("ru")`).
- **Ключевые возможности:**
  - 100% автономная работа без интернета (кеширование расписания на устройстве).
  - Интерактивный таймер пар с обратным отсчетом до конца пары или перемены.
  - Опциональный анонс первой пары на завтра после завершения занятий текущего дня.
  - Поддержка нескольких факультетов: **ФМОС** (экологи) и **ФЭМ** (медики).
  - Подсветка изменений в расписании (переносы, замены преподавателей и аудиторий).
  - Виджет для рабочего стола Android с отображением занятий на день.
  - Автоматическая синхронизация светлой/тёмной темы с системой.

---

## 🛠️ Технологический стек

- **Язык разработки:** Kotlin 2.0.21
- **UI Framework:** Jetpack Compose + Material 3 (Material You Dynamic Colors)
- **Архитектура:** Clean Architecture + MVVM (Model-View-ViewModel) + StateFlow / Flow
- **Сетевой клиент:** OkHttp 4.12.0 (lazy-инициализация, единый CookieJar, Keep-Alive, Rate-limiting)
- **HTML-парсинг:** Jsoup 1.18.1
- **Сериализация и кеш:** `kotlinx.serialization.json` (быстрый строго типизированный JSON на диске)
- **Асинхронность:** Kotlin Coroutines (`Dispatchers.IO`, `viewModelScope`, `flowOn`)
- **Тестирование:** JUnit 4, Kotlinx Coroutines Test, Mockito / In-memory fakes

---

## 📁 Структура проекта

```
schedule_app/
├── app/
│   ├── build.gradle.kts                # Конфигурация Android, зависимости, R8, localeFilters
│   ├── proguard-rules.pro              # Правила обфускации и сохранения data-моделей
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml     # Декларация Activity, виджета, разрешений (INTERNET)
│       │   ├── java/com/example/iseuschedule/
│       │   │   ├── MainActivity.kt     # Главная Activity, поддержка dynamic theme
│       │   │   ├── data/
│       │   │   │   ├── ScheduleRepository.kt     # Сетевой клиент, кеш, RateLimiter, ASP.NET State
│       │   │   │   ├── model/
│       │   │   │   │   ├── ScheduleModels.kt     # WeekSchedule, DaySchedule, Lesson, LessonType
│       │   │   │   │   ├── LessonTimeHelper.kt   # Расчет текущих/следующих пар и интервалов
│       │   │   │   │   ├── ScheduleDiffHelper.kt # Детекция изменений между кешем и сетью
│       │   │   │   │   └── TimeProvider.kt       # Интерфейс провайдера времени для тестов
│       │   │   │   ├── parser/
│       │   │   │   │   └── ScheduleParser.kt     # Парсинг HTML таблицы #TT, групп, ViewState
│       │   │   │   └── settings/
│       │   │   │       └── UserSettingsRepository.kt # Хранение настроек в SharedPreferences
│       │   │   ├── ui/
│       │   │   │   ├── components/
│       │   │   │   │   └── LiveLessonTimerCard.kt # Карточка обратного отсчета пары и анонса на завтра
│       │   │   │   ├── main/
│       │   │   │   │   ├── MainScreen.kt          # UI экрана расписания, SettingsDialog, дни недели
│       │   │   │   │   └── MainScreenViewModel.kt # Бизнес-логика, StateFlow, смена недели/курса/группы
│       │   │   │   └── theme/
│       │   │   │       ├── Color.kt, Theme.kt, Type.kt # Стили и палитра Material 3
│       │   │   └── widget/
│       │   │       └── ScheduleWidgetProvider.kt  # Провайдер виджета домашнего экрана
│       │   └── res/
│       │       ├── layout/widget_schedule.xml     # XML-разметка RemoteViews для виджета
│       │       ├── xml/schedule_widget_info.xml   # Конфигурация метаданных виджета
│       │       └── values/strings.xml, colors.xml
│       └── test/java/com/example/iseuschedule/
│           ├── data/
│           │   ├── ScheduleRepositoryCacheTest.kt # Тесты Stale-While-Revalidate, офлайна и файлов
│           │   ├── SecurityValidationTest.kt      # Тесты защиты от Path Traversal и спецсимволов
│           │   ├── parser/ScheduleParserTest.kt   # Юнит-тесты HTML-парсера таблицы
│           │   └── settings/UserSettingsTest.kt   # Тесты сохранения факультета, курса, группы
│           └── ui/main/MainScreenViewModelRealTest.kt # Тесты состояний ViewModel и переключения недель
├── gradle.properties                   # JVM аргументы сборки без локальных путей
├── build.gradle.kts                    # Root buildscript
├── settings.gradle.kts                 # Репозитории и плагины
├── README.md                           # Описание для пользователей GitHub
└── CONTEXT.md                          # Архитектурный контекст проекта (этот файл)
```

---

## 🌐 Сетевой протокол и взаимодействие с сервером МГЭИ

### 1. Архитектура веб-сервера
Расписание университета размещено по адресу:
`http://rsp.iseu.by/Raspisanie/TimeTable/umu.aspx`

Сервер работает на платформе **Microsoft ASP.NET WebForms**. Все формы управляются состоянием сессии и скрытыми полями:
- `__VIEWSTATE` — сериализованное дерево контролов страницы.
- `__VIEWSTATEGENERATOR` — идентификатор генератора ViewState.
- `__EVENTVALIDATION` — токен безопасности для защиты от подделки запросов.
- `__EVENTTARGET` — идентификатор контрола, вызвавшего обратную отправку (PostBack).
- `__EVENTARGUMENT` — параметры события.

### 2. Почему нативный HTTP-клиент, а не WebView или браузерный парсинг?
- **Скорость:** Прямой HTTP POST запрос с парсингом Jsoup занимает **~300-800 мс** против **10-15 секунд** у Selenium/Headless Chromium/WebView.
- **Потребление ресурсов:** Не расходует оперативную память устройства, не греет процессор и не сажает аккумулятор.
- **Надежность:** Работает даже при слабом EDGE / 3G соединении.

### 3. Полный жизненный цикл ASP.NET PostBack в приложении

```
[1. Начальный GET-запрос]
       │
       ▼
Извлечение куки сессии (ASP.NET_SessionId) и полей (__VIEWSTATE, __EVENTVALIDATION)
       │
       ▼
[Нужен факультет ФЭМ (id="4")?]
       ├── ДА ──► [2. POST с __EVENTTARGET="ddlFac", ddlFac="4"]
       │                │
       │                ▼
       │          Обновление __VIEWSTATE (появляются курсы и группы ФЭМ)
       │                │
       └── НЕТ (ФМОС) ──┘
       │
       ▼
[3. POST с __EVENTTARGET="ddlCourse", ddlFac=..., ddlCourse=targetCourse]
       │
       ▼
Сервер возвращает страницу со списком групп в элементе <select id="ddlGroup">
       │
       ▼
Поиск нужной группы в ddlGroup (строгое сопоставление имени группы со значением value)
       │
       ▼
[4. POST с __EVENTTARGET="ddlGroup", ddlFac=..., ddlCourse=..., ddlGroup=groupValue]
       │
       ▼
[Требуется неделя, отличная от текущей (weekOffset != 0)?]
       ├── ДА ──► [5. POST с __EVENTTARGET="ddlWeek", ddlWeek=weekValue]
       │                │
       └── НЕТ ─────────┘
       │
       ▼
[6. Финальный POST с кнопкой ShowTT="Показать"]
Передача всех полей формы + ShowTT="Показать"
       │
       ▼
Сервер возвращает готовую HTML-таблицу <table id="TT">
       │
       ▼
Jsoup + ScheduleParser парсят расписание в структуру WeekSchedule
```

### 4. Поддерживаемые факультеты (`ddlFac`)
- `2` — **ФМОС** (Факультет мониторинга окружающей среды) — по умолчанию.
- `4` — **ФЭМ** (Факультет экологической медицины).
*(Значение `3` — факультет повышения квалификации, не содержит студенческих дневных групп).*

---

## 💾 Архитектура кеширования и офлайн-режима

Приложение реализует паттерн **Stale-While-Revalidate**:

```
Пользователь открыл экран / сменил неделю
              │
              ├──► 1. Немедленно извлечь данные из кеша (RAM -> Диск)
              │       └── Если есть: мгновенный рендер UI (ScheduleUiState.Success)
              │
              └──► 2. Проверить валидность кеша (TTL: 1 час)
                      ├── Кеш свежий И forceRefresh == false:
                      │     └── Завершить (сетевой запрос пропускается)
                      │
                      └── Кеш устарел ИЛИ forceRefresh == true:
                            └── Фоновый сетевой запрос (loadFromNetwork)
                                  ├── УСПЕХ:
                                  │     ├── Сравнение с прошлым кешем (ScheduleDiffHelper)
                                  │     │     └── При наличии изменений: показ уведомления
                                  │     ├── Сохранение в RAM и на диск JSON
                                  │     ├── Обновление виджетов рабочего стола
                                  │     └── Эмиссия нового состояния в UI
                                  └── ОШИБКА:
                                        └── Если кеш уже был показан — остаемся на кеше
                                        └── Если кеша не было — показ ScheduleUiState.Error
```

### Структура файлов кеша:
- **Расписание недели:** `schedule_${sanitizedGroup}_week_${weekOffset}.json` (TTL: 1 час).
- **Списки доступных групп:** `groups_fac_${faculty}_course_${course}.json` (TTL: 7 дней).
- При очистке кеша удаляются все файлы по маске `schedule_*` и `groups_*`.

---

## 🔒 Безопасность и отказоустойчивость

1. **Защита от Path Traversal и инъекций (`sanitizeGroup`):**
   - Блокируются последовательности `..`, `/`, `\`, `:` и нулевые байты `\u0000`.
   - Разрешены только безопасные символы (буквы, цифры, дефис, подчеркивание, скобки, пробел).
   - Длина жестко ограничена 32 символами.
   - Исключена возможность записи файлов расписания за пределы директории внутреннего кеша приложения.

2. **Безопасность сетевых ресурсов:**
   - Потоки `ResponseBody` строго закрываются через `.use { ... }`, исключая утечки дескрипторов сокетов.
   - Запросы лимитируются внутренним `RateLimiter` (не чаще 1 запроса в 3 секунды при пакетных операциях), предотвращая блокировку IP со стороны университетского файрвола.

3. **Синхронизация настроек:**
   - Изменения `SharedPreferences` прослушиваются через `OnSharedPreferenceChangeListener`, гарантируя реактивное обновление темы и расписания без перезапуска приложения.

4. **Анонимность репозитория:**
   - Конфигурация сборки и репозиторий полностью очищены от абсолютных путей машины разработчика и персональных данных.

---

## 🚀 Команды для сборки и тестирования

- **Запуск всех юнит-тестов:**
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Сборка релизного APK (с минификацией R8):**
  ```bash
  ./gradlew assembleRelease
  ```
  Итоговый файл: `app/build/outputs/apk/release/app-release.apk`
- **Установка на подключенное устройство через ADB:**
  ```bash
  adb install -r app/build/outputs/apk/release/app-release.apk
  ```
- **Запуск главной активности приложения:**
  ```bash
  adb shell am start -n by.iseu.schedule/com.example.iseuschedule.MainActivity
  ```
