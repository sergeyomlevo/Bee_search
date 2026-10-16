# Навигация по GPX processing

Входной слой закрытого GPX parser/profile/processor pipeline. Этот индекс
ссылается на контракты; checklist содержит статус выполнения,
а handoff — текущую точку продолжения.

| Путь | Назначение |
|---|---|
| [GpxInput.kt](GpxInput.kt), [GpxRawInput.kt](GpxRawInput.kt) | Повторяемое чтение, исходный digest/length и закрытие источника. |
| [AndroidGpxXmlReader.kt](AndroidGpxXmlReader.kt), [GpxXmlBoundary.kt](GpxXmlBoundary.kt), [GpxXmlGuard.kt](GpxXmlGuard.kt), [GpxXmlLexical.kt](GpxXmlLexical.kt), [GpxXmlSemantics.kt](GpxXmlSemantics.kt) | Закрытая XML/security boundary и Android XML adapter. |
| [AndroidGpxProfileReader.kt](AndroidGpxProfileReader.kt) | Закрытый GPX 1.1 profile reader и canonical draft handoff. |
| [GpxProcessor.kt](GpxProcessor.kt), [GpxParseResult.kt](GpxParseResult.kt) | Закрытый двухпроходный processor и типизированный accepted/rejected результат. |
| [GpxCm1Encoder.kt](GpxCm1Encoder.kt) | Закрытый CM1 comparison material; это не persistent ID, dedup key или backup codec. |
| [JVM GPX tests](../../../../../../../test/java/org/beesearch/app/data/gpx) | Regression tests XML boundary, input, CM1, processor и cancellation. |
| [domain GPX tests](../../../../../../../test/java/org/beesearch/app/domain/gpx) | Проверки canonical model/profile semantics. |

Managed Original Store, копирование/публикация оригиналов и coordinator здесь
ещё не реализованы. Persistence ownership продолжается в [Stage1D](../../../../../../../../../docs/d106-stage1d-gpx-persistence-contract.md);
общая нормативная цепочка: [Stage1A](../../../../../../../../../docs/d106-stage1-gpx-spec.md),
[Stage1B](../../../../../../../../../docs/d106-stage1b-gpx-parser-identity-contract.md),
[Stage1C](../../../../../../../../../docs/d106-stage1c-gpx-implementation-contract.md).

Документация проекта: [README.md](../../../../../../../../../README.md).
