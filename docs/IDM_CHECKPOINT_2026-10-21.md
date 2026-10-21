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

## ملحق متابعة OSS / FTP — مساء 2026-10-21

- طلب المستخدم الاستمرار على نفس المشروع والاستفادة من مكتبات مفتوحة المصدر بدل إعادة كتابة المنظومات من الصفر. لذلك لم يُستبدل محرك HTTP/HLS ولم تُنشأ Repositories أخرى.
- **E038:** Run `39605054630`، SHA `4eaaea470ec137892ad302c75071d99f870b8f47`، ناجح 108/108؛ ملفات XML العشرة فُحصت في الجلسة. C04/ C07 زادت تغطيتهما، لكن C07 لا يزال يحتاج Restart من Disk ثابت، وليس مجرد Manager جديد من object الذاكرة. السجل الأصلي `docs/Download_Manager_Execution_Plan_v1.md` هو المرجع.
- **Apache Commons Net 3.13.0:** اخترناه لنقل FTP Binary/Passive/REST/Authentication في Windows (ترخيص Apache-2.0)، و**Apache FtpServer 1.2.1** لتشغيل خادم حقيقي في الاختبارات فقط. API https://commons.apache.org/proper/commons-net/apidocs/org/apache/commons/net/ftp/FTPClient.html ؛ Maven Central https://central.sonatype.com/artifact/commons-net/commons-net/3.13.0 .
- الملفات المضافة في نفس الفرع: `downloader/core/src/desktopMain/kotlin/ir/amirab/downloader/ftp/FtpTransferClient.kt`، و`downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/ftp/FtpTransferClientTest.kt`، مع تعديل `downloader/core/build.gradle.kts` لحصر Client في desktopMain وServer في desktopTest. تفاصيل وحدود التراخيص في `docs/Open_Source_Reuse_FTP_2026-10-21.md`.
- محرك FTP الحالي يملك Socket Data/Control حقيقي، يستعمل UTF-8 وPassive/BINARY، يتحقق من SIZE/MDTM عند REST، يكتب إلى `.ftp-part` ثم يلتزم عند اكتمال السيرفر والعدد، ويحفظ هوية Remote في Sidecar بلا أسرار. اختبارات Loopback تتضمن كامل البايتات، Resume، تغيير Remote، وInvalid Auth.
- **بوابة FTP المفتوحة:** هذا Transport داخل Library حتى الآن، **لم يُوصَل بعد** إلى `FtpDownloadItem/Job` أو `DownloaderInUiRegistry` أو أزرار New URL/Queue. لا تفك تعطيل OK لروابط FTP حتى يثبت الربط وظيفيًا واختبارات التكامل؛ لا ادعاء دعم FTP للمستخدم في المثبت النهائي بعد.
- **تشغيل CI الجاري للتحقق من المكتبة:** Run `39673643097`، SHA `f2029ffe8a8e1564fc3053b98c74a7b170b2bdba`. عند كتابة الملحق كان غير منتهٍ؛ اقرأ النتيجة وLogs/XML، أصلح المشاكل ولا تسمي هذا النجاح مسبقًا. وثائق لاحقة لم تغيّر Gradle/Source ولم تُبنَ ضمن هذا SHA.
- مصادر محتملة لاحقًا دون تطبيق آلي: Lucide SVG (ISC + أيقونات مشتقة تتطلب MIT)، والإبقاء على yt-dlp من مصدره مع تحديث وإدارة تبعيات FFmpeg وفق ترخيص كل Build. لا تعِد كتابة منظومات قائمة ولا تضف مكتبة بلا ميزة واختبار.
- **الخطوة القادمة مباشرة:** حسم CI للاختبارات الـFTP، ثم إزالة أي خلل Test/Build، تحديث سجل الأدلة E039، وتنفيذ ربط FTP إلى Job/Registry/LinkChecker/UI بشكل كامل، بعدها اختبارات Password/Anonymous/REST/HostLimit/StopAll وLAN/WAN. قبل ترحيل بيانات سجّل Backup مستقل، لا تعتمد على فرع Git وحده.

### متابعة نتائج تجميع FTP التشخيصية (لا تعتبرها اجتياز FTP)

- `39673643097` فشل قبل بدء الاختبارات: `desktopMain` و`desktopTest` غير محلولين في Gradle DSL؛ أصلح `3f9c72f1` باستخدام `getByName(...)`.
- `39674929421` فشل عند Compile Test FTP: `PropertiesUserManagerFactory` في package `org.apache.ftpserver.usermanager` وليس `...impl`. الإنتاج `FtpTransferClient.kt` لم يظهر خطأ compile فيه. أصلح `ad0e979c` وأصبح users fixture database داخل root المؤقت.
- اختبار معرّف بالضبط هو Run `39676264990` برأس كود `ad0e979cd2efd734f3f2cbc98c949491e3d19415` (تحقق منه عند العودة؛ لا تعتبره نجاحًا تلقائيًا). آخر تعديلات docs لا تغيّر كود الاختبار.
- الاستكمال فورًا: اقرأ نتيجة Run، واحسب اختبارات XML؛ إذا فشل صحح ولا تحذف tests، ثم اربط FTP في نموذج Job/Registry/UI، واختبر Loopback على Windows وLAN.

- **استرجاع FTP مستقل:** `backup/pre-ftp-open-source-2026-10-21` عند SHA `4eaaea470ec137892ad302c75071d99f870b8f47` (آخر Run ناجح 108/108 قبل مكتبة FTP). فرع المصدر فقط وليس نسخة بيانات مستخدم.\n
- `39676264990` compiled source/test but 4/4 FTP fixture tests failed before transport because Apache FtpServer PropertiesUserManagerFactory needs users file to already exist. Exact XML failure: `FtpServerConfigurationException: User data file specified but could not be located`. Fixed by `Files.createFile(userDbFile)` in `07f7c97f8ccb049eb5f5a58321502be003b86b91`; new CI `39677806687` in progress at update. Tests not removed or relaxed.

## Continuation checkpoint — 2026-10-22: wider protocols and FTP recovery

- **Source and branch unchanged:** `amroalhamedi2022-hue/personal-download-manager`, `codex/repair-rf00-rf01`; browser extension repository left untouched. No data migrations performed. Recovery branches `backup/pre-m02-hls-2026-10-21` and `backup/pre-ftp-open-source-2026-10-21` remain.
- **M04 FTP milestone evidence E039:** GitHub Windows Run `39677806687` on SHA `07f7c97f8ccb049eb5f5a58321502be003b86b91` **completed successfully**. The test results artifact `12383421597` was downloaded and 11 XML suites parsed: **112 tests, 0 failures, 0 errors, 0 skipped**, including **4/4 real Apache FtpServer socket tests** for transfer/hash, REST resume after interruption, changed remote identity and wrong auth/scheme. Windows compilation and single-EXE packaging succeeded; installer SHA-256 `15793bc935361c2b4075f87820d981f5944189f86a5114bd149f828e31c80137`. **This does not close FTP UI/Job/Queue/WAN gates.**
- **New production FTP hardening** `9af40bb60b4a972eb4d326eb067b4a6af724176d`: store remote identity resume sidecar **after RETR is accepted**, not after unsuccessful attempt to open a missing remote file.
- **Three extra FTP integration tests** `e265285e915680f2a733aa9e003f40b0a8044c4a`: reject missing file with no output/sidecar, refuse overwriting an existing completed local destination, and prove persisted sidecar omits authentication password. They are **pending CI validation** at checkpoint, so 115/115 is not yet a verified claim.
- **CI verification for new tests:** Run `39680257919` at SHA `e265285e915680f2a733aa9e003f40b0a8044c4a`, which may still be pending/in progress. An earlier run `39680200624` covered the production hardening alone and can be cancelled by concurrency. Fetch latest result, log/11 XML suites, then add E040 with SHA and results or fix failure.
- **Scope correction 2026-10-22:** User explicitly withdrew the previously added P7-11/T21/D10 workstream. These items were removed from active planning; no production code, native libraries, interfaces, or package dependencies had been introduced for this workstream. Continue HTTP/HTTPS, FTP, media HLS/DASH, yt-dlp, and browser integration as originally requested.
- **Immediate follow-up:** (1) CI Run `39680257919` **passed**, source SHA `e265285e915680f2a733aa9e003f40b0a8044c4a`; 11 XML suites reviewed, **115/115 tests** (including **7/7 real FTP tests**), Windows one-EXE installer SHA-256 `cba15e14e8d956c7d69b6d4f8793ecb9a5f65769c3cb558177db4d5b0e85dbe1`. E040 is in the master plan. (2) Integrate FTP into actual distinct `IDownloadItem`/Downloader/Job/Queue/Monitor/LinkChecker/credential storage; no plaintext password or legacy JSON rewrite. (3) LAN/WAN/Windows UI tests. (4) M06–M09 visuals/settings/CPU **profile → locate hotspots → fix → retest**; (5) remaining M10 media/browser capabilities, then M11 final acceptance.
- **Reporting format requested:** at the end of **every response** state *implemented/tested*, *remaining*, and *next exact milestone*. Never equate committed untested code with a passing CI result, or loopback tests with public internet.

## M04 staged FTP Job integration — 2026-10-22 continuation

- **No change to scope or rollback baseline.** Master plan `docs/Download_Manager_Execution_Plan_v1.md` retains 81 active requirements after user explicitly removed P7-11, with all other protocols/media requirements preserved.
- **M04 production source staged:** `downloader/core/src/commonMain/kotlin/ir/amirab/downloader/ftp/FtpDownloadCredentials.kt`, `FtpDownloadItem.kt`; desktop-only `FtpDownloadDestination.kt`, `FtpDownloadJob.kt`, `FtpDownloader.kt`, `FtpCredentialsProvider.kt`. FTP credentials/item have distinct `@SerialName("ftp")` for future safe polymorphic registration and store NO password; `FtpSessionCredentials` is non-serializable and redacts `toString`. Default production provider permits anonymous FTP only; a non-anonymous account requires a verified protected Windows secret-store adapter before UI enablement.
- **FTP Job behavior under review:** accepted Resume transitions to Resuming before lazy worker admission (Stop All safety); blocking transport runs on Dispatchers.IO; Pause closes FTP socket and joins worker; progress length from actual FTP metadata, output staged and committed after complete 226. Destination moves paired partial + sidecar on edits and deletes staged files on task removal; completed output left untouched when staging cleanup. Core `SpeedLimiter` reused in `FtpTransferClient` with per-item byte/s cap instead of a duplicate custom algorithm. Unit tests: `FtpModelTest` (4), `FtpDownloadDestinationTest` (3), `FtpTransferClientTest` newly enhanced with bounded-speed test and **real end-to-end DownloadManager Job fixture** using temporary in-memory login provider and Apache local FTP server (2). Original seven FTP transport tests retained.
- **Current source-test gate:** GitHub Windows Run `39686443526` at SHA `b4b3d66a3e8eee9555a39e2604a838e43ad0ae41` **pending/in progress at documentation time**. Earlier jobs were cancelled by workflow concurrency as new code arrived; do not cite cancelled runs as passed. Fetch the final run job logs/XML; fix actual compile/test errors, rerun, and only then append E041 to master evidence.
- **Not yet integrated in the live app:** FTP Job/Downloader are **NOT added** to `Di.kt`/JSON/DownloaderInUiRegistry. Thus Add URL for ftp:// is still deliberately disabled. Host/global/queue speed limits, persisted restart, failed auth user flow, Windows Credential Manager/DPAPI, UI LinkChecker, real LAN/WAN and system installer testing are still **open M04 N07–N12**. Current direct FTP test already passed E040 115/115 before this stage; do not treat that as test evidence for the new Job classes.
- **Next exact milestones:** (1) Run `39686443526` pass/fix; (2) actual FTP Job loopback and Stop All/pause/resume/restart regression; (3) protected credential store, UI LinkChecker/registry/monitor end-to-end; (4) protocol-specific host/global limits and Windows/LAN/WAN acceptance; (5) other remaining M02–M11 and CPU/RAM profiling **measure → diagnose → optimize → remeasure**. Never enable the Add URL confirmation button for unsupported paths.

## E041 final result — Windows success, 2026-10-22

- **Verified green code baseline** SHA `f6a539955e51edb113c839b661238c8fda83a4ad`, GitHub Windows Run `39687560335`. Source after this SHA changed **docs only** and does not introduce code changes.
- **Real evidence:** downloaded Windows test artifact ZIP `12387447349`, hash `53c52c58db34a5506e632258734f1614865db87df64534408ed86c9d7dfad769`. Parsed all **13** JUnit XML suites: **124/124 tests passed, 0 errors, 0 failures, 0 skipped**. Core FTP transport tests `FtpTransferClientTest` **9/9**, `FtpModelTest` **4/4**, `FtpDownloadDestinationTest` **3/3**. The FTP transport suite now covers a real DownloadManager-managed transfer to Apache FtpServer with transient synthetic login provider; files and status verified after completion. The shared throttler is exercised with byte integrity.
- **Windows app compile and one-EXE package succeeded**, EXE installer SHA256 `97f1a000c155aa506ffd1b1368a5672f12d9eaf2e426b47c65229b8d75fa230d`. Installer ZIP artifact ID `12387307645`, ZIP hash `942f12f9084856d48c8ffac507106eb2104458e5305a5a7abf75652e726dd1d1`. No human Windows GUI or WAN run proven.
- **Earlier failed run explained:** Run `39686443526` source compiled but JUnit 4 rejected a newly added FTP speed test method whose Kotlin inferred JVM return type was not `void`; XML `initializationError`, not a transport failure. Fixed in `f6a53995`, rerun fully passed. No tests removed, disabled or skipped.
- **Protected FTP secret storage source research:** `docs/FTP_WINDOWS_CREDENTIAL_STORAGE_DECISION_2026-10-22.md` chooses Windows Credential Manager via Apache-2.0-option JNA as candidate pending native Windows integration; `FtpCredentialsProvider` already supports transient injection; default production anonymous-only. Do not store real user credentials as synthetic test fixtures do.
- **Next phase M04** (still NOT complete): DI/JSON/UI LinkChecker + monitor/queue integration with authenticated credential storage and restart persistence, actual HostLimit/global speed controls, Stop All/Pause/Resume end-to-end under rate limitation, tested Add URL eligibility, LAN then WAN and Windows UI. **FTP Add URL remains disabled; do not claim available to end user.** After M04, M06–M09 exact approved IDM UI and **CPU/RAM measure → diagnose → optimize → remeasure**, and M10/M11 remaining delivery gates. User explicitly removed P7-11; don't restore cancelled scope.

## M04 HostLimit/Stop-All gate — 2026-10-22

- Production source changes in `FtpTransferClient.kt` (`544c0de1d10cbd9e32614e1c29e420773fbb158a`): wrap FTP streamed input in the existing shared `SpeedLimiter` objects for global, per-host, and per-file limits, rather than inventing a second FTP throttler.
- Source changes in `FtpDownloadJob.kt` (`3515867f9f384df0d22e2dffea8ceaafb69f0c9a` and `8abc372b0d88ef602b80555d3d1788955a440039`): use `DownloadManager.hostLimitRegistry.withConnectionPermit(host)`, wait cooperatively before opening FTP socket, show `Resuming` until the permit is granted, then mark `Downloading`, persist and stream. `Stop All` cancels that pending worker; permit releases in registry finally.
- New Apache loopback integration test `stopAllCancelsFtpWaitingOnHostPermitAndNextResumeFinishes`, SHA `b3c96fdcae981a7fa79f98154aa0c315b5ded93e`: holds a synthetic host permit, attempts FTP manager resume, verifies no output/extra live socket and that Stop All cancels pending task, then releases permit, resumes and compares all 2 MiB of data. This is the **first shared host gate/stop test**, not complete global/per-host throughput or real active-socket stop coverage.
- **CI in flight** at checkpoint: GitHub Actions Windows Run `39694465861`, SHA `b3c96fdcae981a7fa79f98154aa0c315b5ded93e`. Earlier overlapping runs `39694344208`, `39694372255`, `39694409887` were cancelled due to concurrency, not evidence of failure. Obtain terminal job and XML evidence; only then write E042. Latest known previous green E041 = 124/124.
- Other M04 gates remain: WinCred secret storage with proper native Windows credential round trip (Microsoft credential-secure-storage-for-java is archived; review `docs/FTP_WINDOWS_CREDENTIAL_STORAGE_DECISION_2026-10-22.md`), registry/JSON/DownloadInUi/LinkChecker/queue/scheduler, secure auth, disk persistence, actual LAN/WAN and GUI. FTP Add URL must **remain off**.

### HostLimit CI diagnostic (do not confuse HLS failure with FTP regression)

- Run `39694465861` on source `b3c96fdcae981a7fa79f98154aa0c315b5ded93e`, first attempt: JUnit XML archive `12390212479` inspected. **125 test cases in 13 suites; 124 passed and 1 failed**. All **10 FTP transfer tests (including new Stop All host-permit test)**, 4 FTP models and 3 destination tests **passed**.
- The sole failure was **existing intermittent HLS** `ActualRestoredRetryJobTest.concurrentResumeCommandsStartOneActualHlsLifecycle`: HLS playlist requests=1, segment requests=1, active=true but zero completed bytes at 20s; no verified diagnosis yet. Same test was previously intermittent and required repeat evidence in E035; do **not** mark it resolved or delete/increase timeout.
- Triggered **retry of the same failed Windows workflow job, same source SHA** through GitHub Actions Run `39694465861` attempt **2**. Treat results separately: if green, E042 must disclose first failure and open HLS intermittency; if red again, examine response/part-reader timing and root cause without weakening acceptance.
- The staged FTP common global/host/per-file speed and host connection permit enhancements remain unregistered from desktop Add URL pending credentials/UI tests; no regression suite skipped.

### HLS test-state diagnostic after two identical Windows failures

- Run `39694465861` SHA `b3c96fdcae981a7fa79f98154aa0c315b5ded93e`: **both attempts** failed **only** `ActualRestoredRetryJobTest.concurrentResumeCommandsStartOneActualHlsLifecycle` after the unchanged 20-second test deadline; XML of both attempts audited. Attempt 1 archive `12390212479`, attempt 2 archive `12391335876`. Each had **125 tests: 124 passing and 1 failed**, including **10/10 FTP transport tests, 4/4 FTP model, 3/3 FTP destination** successful; no FTP regressions reported. Existing HLS problem: playlist=1, segment requests=1, Downloading/active and final destination file not yet assembled (zero bytes), not proof of zero bytes in a per-segment temporary file.
- A source-only diagnostic update was made to the failing HLS assertion, **without changing 20s timeout or disabling/skipping tests**: include `activeServerTransfers` and each HLS segment's `PartDownloadStatus`, current bytes and expected length. The corrected commit is `487ab98d90d8b869cc2df000550f687a7eb9c6f4` (earlier instrument draft `e45ffd9c...` superseded). New Windows Run `39696222069` pending at this checkpoint. Use the detailed failure message to distinguish slow localhost response from PartDownloader/status-flow event loss; if green, still disclose HLS flakiness and retain regression; do not arbitrarily raise timeouts.
- Latest verified all-green production baseline remains E041 (124/124) at `f6a53995...`. **No E042 until entire 125-test suite is green and Windows package verified.**
