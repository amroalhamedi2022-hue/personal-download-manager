# IDM Project — Checkpoint for continuation (2026-10-21)

> **المصدر المرجعي الأصلي للحالة:** `docs/Download_Manager_Execution_Plan_v1.md`. هذا الملف نقطة تسليم مختصرة لا يستبدل المصدر ولا متطلبات P0–P9 (82 بندًا).

## المصدر الدائم

- مستودع التطبيق **الخاص**: `amroalhamedi2022-hue/personal-download-manager`.
- مستودع إضافة المتصفح **الخاص**: `amroalhamedi2022-hue/personal-download-manager-browser-integration`، لم نغيره في هذه الجلسة.
- الفرع الحالي للتنفيذ: `codex/repair-rf00-rf01`. لا تنشئ مستودعًا جديدًا، ولا Force Push/Reset/حذف ميزات.
- نقطة الاسترجاع الجديدة: `backup/pre-m02-hls-2026-10-21` مثبتة عند `7feb85d0f933190698b06e29519bb425075f25ef`.
- آخر رأس قبل بدء هذه الجلسة: `7feb85d0...`. آخر نجاح قديم Run 63 `39551908963` — 100/100. فشل Run 64 `39553199126` بسبب Timeout 20s لـ`concurrentResumeCommandsStartOneActualHlsLifecycle`.

## مصدر المتطلبات والمرئيات

حزمة الملفات في مكتبة ChatGPT: `IDM_Repair_Handoff_2026-10-21.zip`. اقرأ: `START_HERE_AR.md`، `MASTER_REPAIR_PLAN_AR.md`، `VERIFIED_STATE_AR.md`، `SCREENSHOT_INDEX_AR.md`، ثم `refs/project_docs/*` و`refs/design/*` و27 صورة مقارنة في `refs/screenshots/*`. **لا تضع الصورة P26 في مستودع عام لأنها قد تتضمن مفتاح تكامل خاص.**

- مرجع Liquid Progress المعتمد: `docs/design/file-download-window-approved-2026-10-17.webp`. يظهر شريطان سائلان، رسم سرعة حقيقي، إحصاءات، جدول أجزاء، نسبة في عنوان النافذة فقط، **دون نسبة يمين شريط التقدم**.
- فهرس حفظ البنود الـ82: `docs/IDM_Requirements_Trace_82_2026-10-21.md`. أُنشئ آليًا من النص الأصلي: 82/82 IDs؛ **16 خانة [x] تاريخية لا تُعد بحد ذاتها دليل نجاح**.

## ما غُيّر فعلاً في المصدر

1. `DownloadManager.kt` Commit `fb613194`: استبدال `tryEmit(OnJobCompleted)` بـ`emit` للحماية من إسقاط الحدث الطرفي عندما يمتلئ Buffer. **سبب فشل HLS في Run64 لم يُثبت يقينًا**.
2. `ActualRestoredRetryJobTest.kt` `ec5ef3ca` ثم تصحيح `011413a8`: Timeout 20s نفسه مع حالة/طلبات/بايتات تشخيصية؛ لا رفع للمهلة أو حذف للاختبار.
3. `.github/workflows/private-windows-validation.yml` `dd54060d`: يفشل إذا لم يوجد Installer EXE واحد، ويحسب SHA256 ويرفع EXE فقط لا Portable ثاني.
4. `scripts/Measure-WindowsDownloadManager.ps1` `929e7946` و`1f8b6615`: أداة baseline Windows process tree، WorkingSet وPrivateBytes وCPU وHandles/Threads. **لا قياسات حقيقية حتى الآن، وPowerShell نفسه لم يُختبر هنا**.
5. `docs/IDM_Requirements_Trace_82_2026-10-21.md` `b78ebc47`: حفظ متطلبات P0–P9 كلها. والملحق `docs/IDM_Repair_Addendum_2026-10-21.md` يلخص N01–N26.
6. `.github/workflows/private-hls-race-validation.yml` `23db0ed7`: إعادة اختبار HLS المستهدف ثلاث مرات باستقلال عبر Actions؛ لا تضع علامة النجاح حتى تقرأ XML.
7. `desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/singleDownloadPage/ProgressDownloadPage.kt` `77ae67b7`: إيقاف rememberInfiniteTransition عندما يكون شريط السائل غير نشط (Paused/Retry/Finished) مع الحفاظ على الحركة أثناء النقل الحقيقي. **لم يُثبت قياس RAM/CPU أو مطابقة الصورة بعد**.
8. الخطة الأصلية حُدثت عدة مرات حتى E036، منها `3af800cf` و`3a640ac5` بنتائج المصدر وRun68. أُضيفت أدلة E034–E036 للـBuild والتكرار والواجهة. 

## الدليل الآلي المؤكد لهذه الجلسة

- Run 68 `39598393346` على **SHA `dd54060de966d3903e531e2a4377721fcf8043ba`**: **103/103 tests**, 0 فشل، 0 Errors، 0 Skips؛ مراجعة 9 XML من Artifact `12344773593`، Compile وPackaging على Windows Server 2022 وJetBrains Runtime 25.
- تم تنزيل وفحص Artifact `12344509919`: يحتوي **EXE واحدًا فقط** باسم `DownloadManager_1.0.0-codex-repair-rf00-rf01-snapshot_windows_x64.exe`، الحجم **78,318,152 bytes**؛ SHA-256: `db68391cf7d29ec5c6264082bd12e667130d95094ce74b91054bc87c1e9794f1`. هذا بناء اختباري، **ليس قبول Windows UI فعليًا ولا تسليمًا نهائيًا**.
- Run 68 أعاد اختبار HLS الفاشل واجتاز في تنفيذ واحد؛ سباق نادر ما زال يحتاج تكرار.

## العمل الجاري عند حفظ Checkpoint

- **Run HLS targeted:** `39600320720` عند Commit `23db0ed7`: **نجاح 3/3 تكرارات مستقلة** (1.728/1.641/1.660 ثانية، 0 failure/error/skip)، XML Artifact `12345914124`، Evidence E035. لم تُرفع مهلة 20 ثانية.
- **Run Windows بعد تعديل Liquid والرسم:** `39601103338` عند Commit `df8db76d20daa3acf38c8f5bd657c672d658140e`: **103/103، 0 failure/error/skip**؛ XML Artifact `12346721424`. نجح Compile وPackage، Installer SHA `8909cb2e0d3cd3b7621ffdf41584e1cd88c58544751b8f15898e9fc905784abe`؛ Evidence E036. لا تدعِ مطابقة UI runtime أو تحسن CPU مقاس.
- **الدفعة الأحدث غير المختبرة عند كتابة هذا Checkpoint:** `DefaultThemes.kt` أضيفت 16 عائلة Light/Dark (8×2) مع بقاء 7 Legacy IDs، واختبار `ModernThemePaletteContractTest.kt` بأربع حالات تشمل Contrast >= 4.5. آخر Commit كود `862d56a8458e96d73087b943a1ff0279fd0806ed`. GitHub Windows CI Run `39603132182` بدأ/قد يكون جاريًا؛ **تحقق من النتيجة وXML قبل إعلان النجاح**. آثار هذه الدفعة لم تشمل الحزمتي icon packs وأحجامهما بعد.
- المرجع الإضافي `docs/IDM_Repair_Addendum_2026-10-21.md` يلخص M00–M11 وN01–N26، والمصدر التفصيلي الأصلي محفوظ بالحزمة. لا تستبدله بالمختصر.
- التمييز ضروري بين SHA المبني في كل Run وHEAD أحدث يحتوي توثيقًا إضافيًا؛ لا تُنسب نتائج Run 68 إلى كود UI أو الثيمات اللاحق.

## متبقٍ / محجوب

- **M02:** C04 وC07 والتدقيق الكامل C06 وHLS concurrency؛ شرط خروجها *مفتوح* رغم 103/103.
- **M03:** RF04–RF07 وحماية أسرار/HTTP API/HLS metadata ومصادقة وروابط.
- **M04:** FTP غير مطبق؛ تم تشخيص أن `BaseEnterNewURLComponent.canAdd` يتطلب `DownloaderInUiRegistry` وأن سجل المحركات HTTP/HLS فقط. لا تفك قفل OK كحل زائف. يحتاج Item/Credentials/Job/Adapter/DI وUI واختبارات محلية ومصادقة وResume.
- **M05:** HTTP/HTTPS/HLS/FTP عبر شبكة عامة وLAN غير مقاس. اختبارات محلية لـHTTP/HLS لا تُثبت إنترنت.
- **M06–M08:** نافذة Liquid Progress الصحيحة، speed graph حقيقي، جدول أجزاء، ترتيب IDM وأقسام settings و16 themes و2 icon packs، اختبار Windows واقعي. تخفيف redraw كود جزئي فقط.
- **M09:** قياس RAM/CPU قبل/بعد 3 جولات لكل سيناريو، لم ينفذ.
- **M11:** EXE واحد مجتاز فحص Artifact، لكن Installer على Windows فارغ وترقية وEdge لم تُختبر فعليًا؛ التسليم النهائي لم يكتمل.

## أول خطوات دقيقة للجلسة القادمة

1. اقرأ أحدث SHA من `codex/repair-rf00-rf01` ثم نتائج Run `39603132182` على كود الثيمات، وأضف E037 بعد فحص XML؛ لا تعد E034–E036 من الصفر.
2. إذا فشل HLS المكرر، اقرأ XML التكرار الذي فشل وتشخيص HLS، واصل إصلاح الكود بدل رفع المهلة. إذا نجح، سجّل تكرار 3/3 وأكمل C04/C07.
3. نفّذ RF04/05 الخاصة بالمصادقة اللازمة لـFTP ثم محرك FTP فعلي مع Server Fixture، ولا تكتفِ بتحرير واجهة New URL.
4. تحقق عمليًا من Liquid Progress وSpeed Graph/Parts وبقية المطابقة، ثم RAM/CPU، ثم اختيار حزمة Release نهائية فقط بعد القبول.

**تذكير:** لا تعِد مصدرًا جديدًا، لا تمس بنية JSON/البيانات دون Backup مستقل، لا تسوّي بين Compile/Unit/Windows UI/Internet، ولا تكرر فحص المصدر المثبت بلا سبب.
