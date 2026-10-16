# Bee Search D106 — Managed Original Store: полный контракт для реализации

> **Происхождение.** Основной текст разделов 1–17 восстановлен из полного отчёта Codex от 2026-10-16 («Вставленный текст(20261016-054110).txt», исходный HEAD `494d9953`). Независимая проверка DeepSeek («Вставленный текст (2)(2).txt») дала **ACCEPT WITH CONDITIONS**. Этот файл включает ограниченные уточнения из этой проверки и последующие решения OWNER по Backup/Restore от 2026-10-16. Он **не** означает, что код уже реализован или проверки Android выполнены. Исходные контракты Stage1A–D сохраняют силу. В случае противоречия принятому нормативному тексту — STOP с точной ссылкой, не импровизировать.
>
> **Текущий checkout:** `C:\App\Bee_search_stage0_overlay_registry`, branch `task/d106-stage0-overlay-registry`, ожидаемый HEAD `a31ce3abc24c7f16b79348e6590d0c4d44d93a34`. Preflight повторить. Новых checkout и внешних папок не создавать.

## 0. Сверка с review и решениями OWNER

1. **Copy/hash:** отдельный небольшой GPX-specific loop, без правок `MediaFileWriter`; сравнимое поведение подтвердить тестами.
2. **OWNER correction — exclusive publication:** hard link снят после EACCES/SELinux на API29/API37. Final создаётся через `O_CREAT|O_EXCL` в эксклюзивном каталоге O; привязанные proof/witness и реальные dev/ino плюс SHA/length подтверждают принадлежность. Staging и final — разные файлы.
3. **PARSED precondition:** обеспечивает будущий Coordinator; Store не читает/не обновляет Room/journal.
4. **`owner` sidecar:** вспомогательное свидетельство для восстановления, а не универсальное разрешение на удаление.
5. **Dedup:** не реализовать SHA-дедупликацию здесь; это не запрет на другие хранилища.
6. **STOP:** перечисленные в §17 границы нельзя нарушать без OWNER.
7. **Android verification:** exclusive mkdir/create, no-replace, directory sync, bound ownership, partial copy/crash и инварианты на API29/API37; явно отличать PASS / NOT TESTED; без rename/link fallback.
8. **Cleanup после публикации:** staging сохранять до надёжного `FILE_PUBLISHED`, затем возможен отдельный строго принадлежащий операции cleanup; ни ABORT, ни sidecar сами по себе не разрешают удалить READY final.
9. **OWNER: Android Auto Backup:** оставить настройки в покое; прежнее обсуждение A/B/C из дизайна снято. Собственный локальный Backup/Restore — отложенная отдельная задача для всех постоянных пользовательских файлов.

**Важно:** это не новый архитектурный review. Текст принят как восстановленный рабочий контракт для уже санкционированной реализации; фактическая Android qualification всё ещё обязательна.

---

## 1. Технические предпосылки

| Статус | Проверенный факт и следствие |
|---|---|
| ACCEPTED | Stage1A §7 требует app-private originals, exact bytes/SHA/length, проверку путей, отсутствие перезаписи неизвестного final и recoverable publication |
| ACCEPTED | Stage1D §2.5 уже сохраняет request UUID, O и descriptor; отдельного job UUID, URI или file locator нет |
| ACCEPTED | Stage1D §4: O назначается перед созданием файла; journal принадлежит Coordinator; READY — граница успеха |
| ACCEPTED | Missing original сохраняет canonical geometry, но препятствует FULL protection |
| OBSERVED | minSdk=29, compileSdk/targetSdk=37, Room 2.8.4; база уже v15 |
| OBSERVED | Manifest включает Auto Backup; существующие XML исключают другие assets, но не будущие GPX-каталоги |
| DOCUMENTED | Internal files подходят для долговременных private originals; cache может быть очищен Android, поэтому staging для восстановления нельзя размещать в cache. Android app-specific storage |
| DOCUMENTED | flush() пользовательского буфера должен предшествовать FileDescriptor.sync(). Синхронизация файла сама по себе не подтверждает сохранение directory entry. FileDescriptor, fsync |
| UNKNOWN | Фактическая поддержка выбранной публикации и directory sync на API29/API37 в этом исследовании не проверялась |

Основные нормативные основания: Stage1A §§3, 7, 12–14 (C:/App/Bee_search_stage0_overlay_registry/docs/d106-stage1-gpx-spec.md), Stage1B §§6, 9, 11–12 (C:/App/Bee_search_stage0_overlay_registry/docs/d106-stage1b-gpx-parser-identity-contract.md), Stage1C §§7, 15, 30–31 (C:/App/Bee_search_stage0_overlay_registry/docs/d106-stage1c-gpx-implementation-contract.md), Stage1D §§2.5, 3–4, 7–9 (C:/App/Bee_search_stage0_overlay_registry/docs/d106-stage1d-gpx-persistence-contract.md).

## 2. Повторное использование

| Механизм | Решение | Обоснование |
|---|---|---|
| Потоковый copy/hash из MediaFileWriter.kt | Feature-local независимый GPX copy/hash loop | Создать небольшой самостоятельный потоковый цикл с теми же принципами (bounded buffer, incremental SHA, checked Long, cancellation checkpoints); существующий media-код не изменять, эквивалентность проверять тестами |
| writeAndPublishMediaFile() целиком | Не использовать непосредственно | Он объединяет copy и немедленную публикацию; GPX должен оставаться staging до PARSED |
| Cleanup из media writer | Reuse patterns без переноса реализации | Полезны ownership текущей операции и suppressed cleanup errors; удаление final должно учитывать journal/READY |
| PrivateBlobSource | Reuse для будущего backup adapter | Уже предоставляет repeatable read без delete; проверка размера/mtime не заменяет полный SHA |
| Containment/symlink checks из PrivateBlobSource | Adapt | Подходящий существующий образец; GPX дополнительно проверяет открытый descriptor |
| Attachment/physical-object file stores | Reuse conventions | Internal identities, private roots, проверка путей; их recursive cleanup и rollback переносить нельзя |
| SnapshotArchive | Reuse test/streaming patterns | Его временные spool-файлы не являются recovery staging для GPX |
| Native Android file APIs | New feature-local composition | Нужен отдельный GPX Store, без общего framework и без изменения закрытых файловых компонентов |

Исследованы текущие MediaFileWriter (C:/App/Bee_search_stage0_overlay_registry/app/src/main/java/org/beesearch/app/data/media/MediaFileWriter.kt:15), PrivateBlobSource (C:/App/Bee_search_stage0_overlay_registry/app/src/main/java/org/beesearch/app/data/backuprepository/PrivateBlobSource.kt:17), оба media store и SnapshotArchive. Их runtime qualification не переносится автоматически на новый Store.

## 3. Файловая структура и locator

PROPOSED:

filesDir/
  gpx-staging/
    <requestUUID>/
      owner
      <originalObjectUUID>.part
      publication  # immutable publication witness; появляется перед final copy
  gpx-originals/
    <originalObjectUUID>/
      owner
      original.gpx
      verified

Правила:

- R — уже принятый request UUID; O — уже назначенный original object UUID.
- UUID преобразуются в фиксированное каноническое представление.
- Provider filename, URI, Territory name и пользовательский текст в пути не участвуют.
- Absolute path не сохраняется и не переносится между установками.
- Staging и final находятся на одном filesystem; это проверяется, а не предполагается по сходству путей.
- Одинаковые байты с разными O получают разные файлы. Физическая SHA-дедупликация **в этом Slice** не выполняется (ограничение текущей реализации, не глобальный архитектурный запрет на другие файловые подсистемы).

Room v16 и persisted locator не нужны. Final однозначно выводится из O; staging — из R и O. Это соответствует существующему Stage1D.

Файл `owner` — минимальная служебная запись фиксированного формата с R/O. Он нужен как свидетельство принадлежности staging после process death; не содержит фаз, timestamps или второго journal. Его нет в canonical graph и protection payload. `owner` сам по себе не является границей доверия, доказательством авторизации или основанием для удаления final: необходимы остальные проверки ownership и актуальная проверка Coordinator.

Каталог операции создаётся эксклюзивно. Уже существующий каталог без валидного ownership-свидетельства не присваивается новой операции.

## 4. Минимальные Store API

Все типы — internal. Точные Kotlin-сигнатуры остаются implementation freedom, но обязанности фиксируются:

| Операция | Вход | Результат |
|---|---|---|
| copyToStaging | R, O, внешний open: () -> InputStream | Завершённый staging и вычисленный exact descriptor |
| openStaged | R, O, ожидаемый descriptor | Новый read-only stream для каждого parser pass |
| publish | R, O, ожидаемый descriptor | Verified final либо ограниченный typed failure |
| inspectFinal | O, ожидаемый descriptor | Verified / missing / corrupt / access failure |
| inspectOperation | R, O | Принадлежность, наличие файлов, измеримые размеры |
| cleanupStaging | R/O и актуальное разрешение Coordinator | До publication: abort/no-READY. После publication: durable FILE_PUBLISHED ownership, expected descriptor и согласованные bound proof/witness/verified final. Incomplete final компенсируется до удаления staging witness. |
| cleanupAbortedOriginal | R/O/descriptor и актуальное разрешение Coordinator | Условное удаление принадлежащего незавершённой операции final |

Store:

- не назначает IDs и timestamps;
- не открывает URI через ContentResolver;
- не пишет journal;
- не возвращает READY или «импорт завершён»;
- не предоставляет произвольный resolve(path) или delete(originalId).

Переданный opener открывается Store один раз для копирования. Store закрывает полученный поток. Потоки, возвращённые openStaged, закрывает потребитель — существующий processor.

## 5. Ownership и cleanup

Принадлежность нельзя устанавливать только по имени, длине или SHA.

Для файловых действий необходимы:

1. Доверенный private root, полученный от Android context.
2. Только внутренние R/O и фиксированные имена.
3. Проверка containment и отсутствие symlink-компонентов ниже доверенного root.
4. Regular-file verification; открытие leaf без следования symlink.
5. Проверка соответствия открытого descriptor ожидаемому объекту.
6. Согласованная сериализация операций над соответствующим O.

Android system aliases корня допускаются после разрешения доверенного root, как в существующем PrivateBlobSource. Это не разрешает symlink внутри GPX-каталогов.

Staging cleanup: удаляются только известные entries операции и затем пустой каталог. Recursive delete отсутствует. Неизвестные дополнительные entries вызывают явный отказ.

Final cleanup: требуется актуальная авторизация будущего Coordinator, выполненная под той же границей сериализации, что и READY-transition. Она подтверждает:

- job действительно принадлежит R/O;
- intent — ABORT;
- READY отсутствует как в job, так и у связанного import/O;
- descriptor и принадлежность согласованы.

Переданного ранее snapshot с phase != READY недостаточно.

Store может получать эту проверку через узкий callback, выполняемый внутри общей критической секции. Новый durable token или таблица не нужны. Реальную Room-авторизацию и READY race реализует будущий Coordinator; первый файловый Slice проверяет этот seam контролируемыми тестами.

INFO-1 сохраняется: ABORT не является самостоятельным разрешением удалить READY original. При сомнении — отказ, без удаления.

## 6. Протокол staging → final

### Копирование

1. Coordinator заранее фиксирует COPYING и назначенный O.
2. Store эксклюзивно создаёт каталог операции и завершает ownership-запись.
3. Payload создаётся через exclusive create, без перезаписи существующего файла.
4. Исходный поток копируется без decoding/transcoding; SHA считается по записываемым исходным байтам.
5. Счётчик — checked Long; cancellation проверяется между чтениями и записями.
6. Применяется наследуемый G01: 16 MiB, без нового лимита. Байты сверх границы не записываются как допустимый original.
7. Выполняются flush → file sync → закрытие выходного и исходного потоков.
8. Файл повторно открывается read-only и проверяется по полному SHA/length.
9. Только после успешного завершения Store возвращает staging descriptor. Coordinator записывает COPIED.

Ошибка close/sync не превращается в успешную копию. Исходная cancellation сохраняет primary identity; вторичные ошибки close/cleanup подавляются на ней.

### Публикация

Предусловие: будущий Coordinator подтверждает текущий `journal=PARSED` и владение R/O; Store сам не читает Room и не проверяет journal. Store проверяет переданное разрешение и файловые инварианты в рамках своего API, но не заменяет проверку Coordinator.

OWNER-AUTHORIZED replacement: `Os.mkdir` эксклюзивно резервирует `gpx-originals/O/`;
`Os.open(O_CREAT|O_EXCL|O_NOFOLLOW)` создаёт `original.gpx`. Проверка наличия
пути не заменяет exclusivity. `renameTo`, `Files.move`, `Os.rename`, hard links,
JNI и скрытые fallback отсутствуют. Android Os и Linux open(2) подтверждают
отказ EEXIST при существующем target; фактическая поддержка проверяется отдельно.

Последовательность под общей сериализацией Store:

1. Полностью проверить принадлежащий R/O staging по SHA/length.
2. Эксклюзивно создать каталог O и final payload; сохранить реальные fstat dev/ino
   каталога и открытого payload. Существующий target не открывать для записи.
3. До первой записи payload создать immutable `owner` в final-каталоге и одинаковый
   `publication` witness в принадлежащем операции staging. Формат ограничен 1024
   байтами: версия, R, O, ожидаемые length/SHA, dev/ino каталога и payload.
   Оба файла создаются exclusively, sync/close; затем sync обоих каталогов.
4. Потоково скопировать проверенный staging в новый final, вновь проверить exact
   SHA/checked length и G01. Выполнить flush → file sync → close.
5. Повторно открыть regular final без symlink и полностью проверить SHA/length;
   сопоставить реальные directory/payload dev/ino и оба свидетельства.
6. После успешной проверки exclusive-create `verified` с тем же bound record,
   sync/close и directory sync. Повторная проверка marker, ownership и SHA/length
   предшествует успешному возврату. До этого final — INCOMPLETE, не опубликованный original.
7. Coordinator отдельно фиксирует FILE_PUBLISHED; Store не изменяет Room/READY.
   Только после durable journal authority разрешён отдельный staging cleanup.

Существующий final никогда не переписывается, включая принадлежащий partial.
Повтор разрешён только для доказанно принадлежащего этой операции объекта:
реальные dev/ino совпадают с bound record, retained staging witness совпадает,
R/O/descriptor согласованы. Полные байты без marker можно повторно sync/verify и
завершить publication без reparse/новых IDs. Partial/corrupt payload возвращает
ошибку; будущий Coordinator может отдельно разрешить cleanup no-READY, затем
новую exclusive publication из сохранённого staging. Никакого resume по offset.

Crash до завершения proof/witness оставляет UNKNOWN/CONFLICT. Такой объект не
присваивается и не удаляется автоматически, даже если SHA совпадает. Crash во
время final copy после durable proof оставляет owned INCOMPLETE, пригодный для
авторизованной компенсации. Crash с полными байтами до marker допускает описанное
завершение после повторной проверки. Незавершённый marker fail-closed, не success. Отсутствующий marker даёт INCOMPLETE; marker с неверным proof даёт UNSAFE_OBJECT (invalid operational evidence), без автоматического присвоения или удаления.
При разрыве cleanup между удалениями неизвестные остатки также сохраняются для
решения владельца; отсутствие ответа не является разрешением удалять их вслепую.

`owner` или совпадение SHA сами по себе не доказывают ownership. Требуются private
trusted root, known entries, no-follow/regular descriptors, эксклюзивная резервация,
согласованные R/O/descriptor и dev/ino действительных объектов, retained witness
для незавершённой операции и текущая авторизация Coordinator для удаления.
После удаления staging текущий durable FILE_PUBLISHED ownership callback обязателен
в дополнение к bound final proof. Изменённый inode никогда не присваивается.
Протокол рассчитан на сериализованные writers приложения; враждебный процесс с
тем же UID или произвольная внешняя подмена operational evidence вне этой границы.
Это не защита от malicious same-UID writer и не новая DB/filesystem transaction.

Источники: [Android Os](https://developer.android.com/reference/android/system/Os),
[Linux open(2)](https://man7.org/linux/man-pages/man2/open.2.html),
[FileDescriptor.sync](https://developer.android.com/reference/java/io/FileDescriptor#sync()).
File/directory sync не объявляется полной гарантией power-loss durability.

## 7. Idempotence / conflict matrix

| Состояние | Поведение |
|---|---|
| Тот же R/O, подтверждённый completed staging, descriptor совпадает | Повторная проверка и возврат существующего результата |
| Тот же O, другой ожидаемый descriptor | CONFLICT; ничего не перезаписывать |
| Final уже известен как опубликованный этим O и полностью совпадает | Verified existing result |
| Journal ещё PARSED, bound R/O/descriptor, directory/payload inode и staging witness согласованы | Полные verified bytes допускают завершение; partial не переписывается, нужен отдельный авторизованный cleanup |
| Final существует, но принадлежность не доказана | CONFLICT даже при совпадении SHA/length |
| Staging принадлежит операции, но копирование не завершено | Cleanup; не продолжать копирование с произвольного offset |
| Staging принадлежит неизвестной операции | Не присваивать и не удалять |
| Existing owned final повреждён/частичен | CORRUPT / INCOMPLETE; без перезаписи. Только отдельный текущий no-READY cleanup с bound ownership |
| Final имеет только owner sidecar либо неполный proof/witness | UNKNOWN/CONFLICT; не присваивать, не удалять |
| Expected final отсутствует | MISSING; автоматического повторного импорта нет |
| Два вызова публикуют один O | Один создаёт final; второй проверяет принадлежность или получает conflict |
| Разные O, одинаковые байты | Два отдельных originals |

Совпадение content identity не создаёт ownership.

## 8. Crash / cancel / recovery matrix

| Граница | Файлы | Обязательное поведение |
|---|---|---|
| До открытия источника | Payload отсутствует | Cancellation без успешного результата |
| Во время copy / ENOSPC | Возможен owned partial | Закрыть handles, попытаться удалить свой partial; при неудаче сохранить возможность cleanup |
| Ownership-запись прервана process death | Неполная служебная запись | Не считать staging завершённым; неизвестное содержимое не удалять автоматически |
| Copy завершён, COPIED ещё не записан | Staging есть, journal COPYING | Не запускать parser автоматически; будущий recovery выполняет принятую компенсацию |
| Во время parsing | Final отсутствует | Processor abort; будущий Coordinator удаляет PENDING/staging |
| PARSED, publication ещё не начата | Verified staging | Возможно завершение без reparse и новых IDs |
| Final copy прерван, journal ещё PARSED | Отдельный partial final и verified staging | INCOMPLETE; bound proof/witness и текущий no-READY permit нужны для cleanup, затем новая exclusive copy |
| Final bytes полны, verified marker ещё нет | Final не опубликован | Повторные sync/hash/ownership checks; затем exclusive marker, либо явный отказ |
| Crash до завершения proof/witness | Неизвестный reserved target | CONFLICT; не присваивать и не удалять автоматически |
| Final создан, sync/verification завершились ошибкой | Final может существовать | Ошибка операции не означает отсутствие side effect; сохранить staging-свидетельство для reconciliation |
| FILE_PUBLISHED, READY ещё нет | Verified final, staging может остаться | Будущий Coordinator решает continue/ABORT под общей сериализацией |
| Cancellation после файловой публикации | Final может существовать | Cancellation распространяется; Store не объявляет импорт завершённым и не удаляет final вслепую |
| READY committed, caller не уведомлён | Final остаётся | Correlation D1 позволяет определить успех; cancellation не откатывает READY |
| DB восстановлена без original | Final отсутствует | Degraded canonical result; FULL protection недоступна |
| Original восстановлен без согласованной DB | Orphan/unknown file | Не публиковать и не присваивать |
| Power loss | Состояние заранее не обещается | Повторная проверка и fail-closed reconciliation; отдельная qualification |

Возврат из withContext(IO) тоже может завершиться cancellation уже после файлового side effect. Поэтому отсутствие результата у caller не доказывает отсутствие final. Coroutine prompt cancellation

Process-kill тест не является power-loss тестом.

## 9. Store ↔ Coordinator ↔ journal

| Journal phase | Файловый факт / действие |
|---|---|
| SELECTED | Копирование ещё не разрешено |
| COPYING | O уже записан; Store создаёт owned staging и копирует |
| COPIED | Coordinator сохраняет возвращённые SHA/length вместе |
| PARSING | Processor читает только managed staging |
| PARSED | Полный PENDING graph принят; разрешена файловая публикация |
| FILE_PUBLISHED | Final проверен; факт записан Coordinator |
| READY | Coordinator завершил отдельную Room transaction с admission/correlation |
| Job cleanup | Не удаляет READY original |

Stage1D уже отделяет PENDING cleanup от файловой очистки и сохраняет O/descriptor. Текущий GpxPersistenceStore (C:/App/Bee_search_stage0_overlay_registry/app/src/main/java/org/beesearch/app/data/local/room/GpxPersistenceStore.kt:42) соответствует этой границе.

Store не добавляет writer фаз и не меняет structural triggers. Общая Room/filesystem transaction не предполагается.

## 10. Связь originals с parser

Parsing выполняется из завершённого managed staging, а не повторно из provider URI.

Будущий Coordinator вызывает существующий GpxProcessor.process с:

- repeatable opener из Store;
- expectedOriginalIdentity, полученным после copy;
- cancellation checkpoint.

Оба parser pass независимо открывают тот же immutable staging. Processor уже сравнивает их identity и внешний descriptor. Перед final publication Store снова проверяет байты.

Provider URI после успешного managed copy не нужен. Даже persistable URI permission не гарантирует доступ после перемещения или удаления документа. Android SAF permissions

CM1 не участвует в проверке source consistency. Изменения закрытого parser не требуются.

## 11. Failure taxonomy

PROPOSED bounded internal vocabulary:

- OWNERSHIP_VIOLATION
- UNSAFE_PATH_OR_FILE_TYPE
- CONFLICT
- IDENTITY_MISMATCH
- INPUT_LIMIT_EXCEEDED — существующий G01
- INVALID_EMPTY_SOURCE
- STORAGE_EXHAUSTED
- IO_FAILURE с фиксированным этапом: open/read/write/close/sync/publish/verify/cleanup
- UNSUPPORTED_FILESYSTEM_OPERATION

Правила:

- Cancellation остаётся исключением, не typed rejection.
- STORAGE_EXHAUSTED используется только при достоверной классификации errno; текст IOException не разбирается.
- Ошибка после publication сообщает, что final может существовать.
- Raw URI, path, exception message и исходный XML не входят в безопасные diagnostics.
- Новых persisted reason codes нет.
- Mapping в пользовательский результат принадлежит Coordinator.

## 12. Missing / corrupt original

inspectFinal(O, expected) возвращает:

| Результат | Значение |
|---|---|
| PRESENT_VERIFIED | Bound ownership/verified marker согласованы, regular file прочитан полностью, SHA/length совпали |
| INCOMPLETE | Нет valid completion marker; объект не признаётся опубликованным |
| MISSING | Ожидаемая directory entry отсутствует |
| CORRUPT | Файл прочитан, но descriptor не совпал |
| ACCESS_FAILURE | Проверить байты не удалось |
| UNSAFE_OBJECT | Symlink, неправильный тип или нарушение containment |

Размер и mtime допускаются как предварительные факты, но не как доказательство целостности.

Проверка не удаляет canonical geometry, не запускает GPX parsing и не заменяет original. Degraded-state, UI и FULL protection реализуются будущими владельцами. Полные H04/H05 этим Slice не закрываются.

## 13. Resource accounting

Store сообщает наблюдаемые факты:

- exact logical byte length завершённого original;
- текущий размер owned partial;
- наличие staging/final;
- результат полной проверки;
- факт завершения или прерывания конкретной операции.

Необходимо различать:

- logical lengths;
- дополнительные staging bytes;
- filesystem allocated blocks;
- свободное место.

Exclusive final copy создаёт второй отдельный payload. Logical staging bytes и final bytes учитываются отдельно; во время publication одновременно существуют обе копии и небольшие proof/marker files. Фактические allocated blocks и peak storage измеряются отдельно либо указываются UNKNOWN. Равенство inode staging/final больше не требуется и не ожидается.

Available-space preflight остаётся advisory: он не резервирует место и не исключает последующий ENOSPC. Новых aggregate limits, persisted counters и admission logic нет.

## 14. Backup / device transfer — действующее решение OWNER (2026-10-16)

Android Auto Backup и системный device transfer не входят в задачи этого Slice. Текущие Android manifest/XML backup rules **не изменять**, не выбирать A/B/C, не запускать самостоятельное исследование Android-транспортов и не делать их qualification условием завершения GPX Store. Действующее поведение системы не объявлять гарантированным и не обещать, что GPX гарантированно войдёт в системную копию: этот вопрос просто вне scope.

Долгосрочная цель Bee Search — единый **собственный локальный** Complete Backup/Restore для всей согласованной Room DB и постоянных пользовательских файлов (GPX, медиа, пользовательские растровые карты и т. п.). Система будет реализована позже, после стабилизации основных хранилищ и до накопления существенных незаменимых пользовательских данных. Для будущего адаптера GPX Store должен позволять перечислять принадлежащие permanent originals, читать полные проверенные байты и восстанавливать их по внутренним ID; сейчас ни Backup, ни Restore **не реализовать**. Staging, временная `owner` запись и прочие операционные файлы не относятся к долгосрочному пользовательскому payload.

Если Android системный механизм не мешает текущей задаче, его не изучают и не меняют. Это решение отменяет прежний предложенный в неподписанном черновике выбор вариантов A/B/C, не отменяя других требований файловой безопасности.

## 15. Acceptance tests и verification gates

| Assertion | J/A/M | Обязательная проверка |
|---|---|---|
| Exact copy/hash/count | J + A | Разные chunks, BOM/UTF16, бинарные байты без нормализации |
| G01 | J + A | 16 MiB−1 / exact / +1; over не публикуется |
| Checked counters | J | Overflow без огромного literal fixture |
| Source ownership | J + A | Open/read/close failures; закрытие owned streams |
| Cancellation | J + A | До copy, в copy, sync/publication boundaries, prompt return |
| Cancellation + close failure | J + A | Тот же cancellation object, secondary suppressed |
| Containment / symlink / regular file | A | Leaf и parent symlinks, directory вместо файла, выход за root |
| Publication | A | Exclusive mkdir/O_EXCL, existing target unchanged, sync/close/hash failures, без rename/link fallback |
| Ownership/idempotence | J + A | Равные байты без bound proof/witness не принимаются; подменённый inode не принимается; тот же complete owned объект повторяем |
| Cleanup safety | J + A | Чужие файлы неизменны; отказ авторизации; READY fixture не удаляется |
| Crash boundaries | A | Отдельный test process: partial final copy и complete-copy-before-marker; reopen/compensation/verify; missing proof fail-closed |
| Missing/corrupt | A | Полный SHA, truncation, same-length mutation, access failure |
| Storage exhaustion | J + A | Fault injection; реальный ENOSPC в изолированном sandbox, насколько воспроизводимо |
| Parser seam | A | Два открытия managed staging, expected descriptor, canonical/CM1 без изменений |
| Cost/accounting | M | Copy/hash/verify reads, elapsed, logical/physical bytes; UNKNOWN для недоступного |
| Android backup rules | Static | Проверить, что файлы настроек системного Backup не изменены; transport tests вне scope |
| Scope | Static | Нет runtime user import, READY writer, Room migration и coordinator |

Qualification следующей реализации:

1. Targeted JVM.
2. Полный :app:testDebugUnitTest.
3. Семь принятых compile/build/lint tasks.
4. Freeze production/tests/helpers и separate inventory.
5. Финальная сборка APK pair из frozen inputs.
6. Последовательные API29/API37, одинаковые APK и method set.
7. Post-source correspondence и diff hygiene.

Закрытая GPX Android regression сейчас механически содержит 64 метода: 10+15+12+11+4+12. Новые Store tests добавляются отдельно; итоговый N определяется по реализации. В relevant regression также входят media/private-blob и Stage1D tests согласно затронутым seams.

Невыполненный transport, process-death или ENOSPC gate обозначается NOT RUN/BLOCKED. Power-loss остаётся отдельным неподтверждённым доменом.

## 16. Точный scope Implementation Slice

PROPOSED:

- Один feature-local internal GpxOriginalStore в существующей GPX data boundary.
- Небольшие сопутствующие result/descriptor types.
- Deterministic paths без Room v16.
- Owned staging, copy/hash/sync/verify.
- Repeatable read для существующего processor.
- No-overwrite publication и проверка final.
- Typed inspection и строго авторизованный cleanup.
- Focused JVM/Android tests и fault injection.
- Android backup exclusions, transport tests, Local Backup/Restore — вне scope, действующие системные настройки без изменений.

Не входят Coordinator, journal writer, READY activation, пользовательский import, recovery runner, admission, codecs/Restore, UI/Registry и физическая дедупликация.

Код закрытых Slice и существующие media stores менять для удобства нового Store не требуется.

## 17. OWNER decisions и UNKNOWN

Перед реализацией необходимо:

1. Использовать настоящий полный контракт Managed Original Store вместе с решениями OWNER от 2026-10-16 (данный документ), а не восстанавливать его по краткому промпту.
2. Сохранить no-replace publication, ownership/cleanup boundary и согласованный ограниченный scope. OWNER разрешил implementation Slice, но это не разрешение на изменение закрытых GPX-контрактов.

Не требуется сейчас:

- Room v16;
- новый persisted locator;
- новые business identities;
- retention policy READY originals;
- решение об удалении READY originals;
- новая зависимость;
- гарантия power-loss durability;
- Android Auto Backup / device transfer policy;
- реализация общего локального Backup/Restore.

UNKNOWN до qualification:

- фактическая поддержка exclusive create/directory sync и recovery на обоих endpoints;
- поведение реального ENOSPC и конкретного provider при блокирующем чтении;
- физический peak storage и полная power-loss устойчивость.

**STOP / OWNER escalation:** не менять Room schema, существующие Android backup rules, принятые GPX-contracts, media-код, версию библиотек/новую dependency, retention/удаление READY originals, и не обещать непроверенную power-loss durability. При реальной необходимости такого изменения остановиться и указать точную зависимость. При unsupported exclusive create / directory sync — явный typed failure и фактический Android status; не делать автоматический fallback, который может заменить чужой final. Сценарии процесса, O_EXCL, directory sync, bound ownership proof и no-replace должны быть проверены на API29/API37; невозможность запуска честно указать как NOT TESTED, а не PASS.

Итоговая рекомендация: реализовывать отдельный файловый Store без изменения Room, с copy-before-parse и публикацией без замещения; Coordinator и recovery подключать последующим Slice. Stage1 и H05 остаются OPEN.


## 18. Реализация exclusive-publication correction

Production разделён по узким обязанностям: `GpxOriginalCopy.kt` — bounded copy/hash; `GpxOriginalFiles.kt` — Android POSIX/descriptor/sync boundary; `GpxOriginalPublication.kt` — exclusive O reservation, bound evidence, completion marker и компенсация; `GpxOriginalStore.kt` — согласованный feature API и process lock. Отдельный publication helper удерживает протокол восстановления и его proof рядом, не вводя универсальный framework или Coordinator. Все типы internal; Room, parser, media и backup rules не меняются.


### Execution status — BLOCKED / NOT QUALIFIED (2026-10-16)

Targeted JVM6/6, full1049 tests /122 classes (0 failures/errors/skips) and all7 build/lint gates PASS. ATTEMPT3 API29 selected body181/181 PASS, but actual process-kill recovery failed because the fixture occupied one final-copy block. Preserved as failed evidence; fixture enlarged to2048 points with explicit >32KiB and partial-length assertions. No production change for that correction.

Frozen ATTEMPT4/5 inputs572/572 MATCH; digest `1740C1111B3CB531015AF454DACE4F25C0771E9C8A29DD78922A51613318285F`. Closed GPX112/112 unchanged. ATTEMPT4 and unchanged retry ATTEMPT5 API29 each173/181 PASS,8 OutOfMemoryError results (ART growth limit16MiB; underlying cause UNKNOWN). ATTEMPT5 API37 only6/181 methods completed before Process crashed; ActivityManager reports startup bg ANR. Mandatory Android and actual process-death probes are NOT qualified. No commit/push. Historical attempts1–4 preserved under ignored `build/evidence/d106-managed-original-store/`; current attempt5 uses the same frozen DEV/test APK bytes on both endpoints.

Real ENOSPC NOT TESTED (typed mapping is fault-injected); power-loss UNKNOWN. Physical-device behavior NOT TESTED. Next minimum step is endpoint diagnostics, followed by mandatory frozen requalification; no reduced limits, skipped regression or unsafe publication fallback. Stage1 and H05 remain OPEN.

Decomposition reviewed: the >500-line Android Store test class remains cohesive as one isolated Store acceptance matrix; actual host-controlled process-death workflow is a separate test class. Production responsibilities are split as described above, each below300 lines.


## 19. OWNER acceptance / final qualification (2026-10-16)

**Managed Original Store - OWNER-ACCEPTED / PASS within agreed file Slice.**
This accepts the implemented exclusive-publication protocol and its qualification;
historical design observations and failed execution status in sections1-18 remain
historical evidence, superseded operationally by this acceptance.

- JVM regression1049/1049 (122 classes,0 failures/errors/skips); targeted6/6 and all7 build/lint gates PASS. No source/test changes or repeated builds were needed during final diagnosis/acceptance.
- ATTEMPT6 API29 and API37 each181/181 PASS (155 existing +26 Store/crash methods), identical APK bytes and selected method set. Separate actual process-stop/recovery at FINAL_COPY and DIRECTORY_SYNC each2/2 PASS per endpoint.
- Frozen572/572 MATCH, digest `1740C1111B3CB531015AF454DACE4F25C0771E9C8A29DD78922A51613318285F`; including diagnostic runner573/573 MATCH, digest `DF41D336DF0F474534A1529254361E02177438AD2F501281B93F088B35B63AF9`; closed GPX112/112 unchanged.
- Ignored evidence: `build/evidence/d106-managed-original-store/attempt-6/` (individual outcomes, serial-scoped commands, process-stop/recovery, freeze/post correspondence) and `diagnosis-1/` (heap/startup diagnosis). Failed ATTEMPTS1-5 are preserved.
- API29 old16MiB OOM was localized to early emulator heap initialization before property delivery; controlled runtime512MiB and the eight former failures8/8 PASS together. No additional Store/fixture changes. API37 ordinary startup and full qualification passed after normal boot, but its old exact blocking call remains UNKNOWN.

OWNER accepts the following limitations for this file Slice: real ENOSPC NOT TESTED
(fault-injected mapping is separate evidence); power-loss durability UNKNOWN;
physical-device tests NOT RUN; precise old API37 startup ANR cause UNKNOWN.
No corresponding untested guarantee is claimed.

Store still does not manage Room/journal, activate READY or expose user GPX import.
No Room/schema/parser/media/backup-rule changes. Stage1 and H05 remain OPEN.
OWNER authorized one local finalization commit, no push; subsequent Coordinator,
READY, recovery integration and protection/restore work require separate scope.
