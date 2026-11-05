# HyperGet — الخطة الرئيسية الدائمة للوظائف ومطابقة IDM/JDownloader

**الإصدار:** 1.0 | **تاريخ التأسيس:** 2026-11-03 | **النوع:** Living master plan + handoff + acceptance register  
**المستودع:** `amroalhamedi2022-hue/personal-download-manager`  
**الموقع المعتمد داخل GitHub:** `docs/roadmaps/HYPERGET_FUNCTIONALITY_MASTER_PLAN.md`  
**نسخة احتياطية في مكتبة ChatGPT:** `/HyperGet/HYPERGET_FUNCTIONALITY_MASTER_PLAN.md`  
**الحاكم عند اختلاف النسخ:** أحدث هذا الملف على GitHub بعد التحقق من HEAD، وليس ملخص محادثة قديمة. تُحدّث نسخة المكتبة عند كل دفعة موثقة، ويُذكر إن تعذرت مزامنتها.

> **العقد الصريح:** تنفيذ **كل** طلبات الوظائف من بداية المشروع إلى آخر طلب: المحرك والتخزين، كل شريط تحكم وأمر واختصار ونافذة وإعداد له وظيفة، مطابقة أدوات وتبويبات IDM، الجامع والحزم وميزات JDownloader، المتصفح والوسائط وFTP وaria2، والتحقق والتسليم. **ممنوع العمل على ألوان الثيمات، Liquid Glass، الرسومات والهوامش والرموز البصرية** في هذا المسار. مع ذلك، وجود الزر، إمكان تفعيله، ترتيب وصول لوحة المفاتيح، ربطه بالفعل، والحفظ بعد Restart **وظائف ملزمة**. لا يُحذف مطلب قديم لأنه لم يرد في آخر رسالة.

## 0. ملخص الحالة الحقيقية عند إنشاء الخطة — تحديث قبل أي تنفيذ

- `main` عند الفحص: `457d3d24494b689506459f86c8e25fe4a3669d9e` (الرقم يتغير؛ افحصه أولًا). به بعض إصلاحات النقل والبحث والأعمدة والهوية والـinstaller. **لا تفترض أن ما في PR #3 مدمج به**.
- فرع وظائف PR #3 (Draft): `automation/idm-ftp-cancel-and-category-flush`، الكود `3582e0a043e938442cc675a0e1cf4f63c9fe6e07`؛ أساس PR هو `codex/repair-rf00-rf01` وليس `main` مباشرة. موضوع FTP/HTTP/HLS/Browser/aria2 فيه كود واختبارات فعلية. **لا تدمج PR #3 بالكامل في main آليًا**؛ الفروع متشعبة وتحتاج ترقية انتقائية والتحقق من التبعيات.
- مسار الثيمات PR #4 (Draft) مستقل وممنوع تعديله في هذه الخطة. لا تعتمد أحدث SHA له دون فحص.
- آخر نتيجة معروفة على `3582e0a`: **14/15 GitHub Actions PASS** و**1/15 FAIL**. الفاشل اختبار المقارنة الفعلية المثبتة `41644174970`، عند 16 تحميلًا متزامنًا: تجربة body-copy permits = 56 أعطت throughput **-24.60%**؛ الذاكرة +11.25% Working Set و+10.99% Private Bytes، في حين الحد المسموح 15%. لذلك **ليس إصلاحًا مقبولًا**. تحوّل 60/48/56 تاريخ تجارب، لا نجاح. لا تخفّض عدد التحميلات الفعالة، ولا توسّع عتبة 15%، ولا تعدّل الاختبار لتجاوز الفشل.
- نجاحات على الكود نفسه: Windows GUI smoke `41644174856`؛ HTTP real E2E `41644175005`؛ HTTPS/Range `41644174993`؛ FTP installed E2E `41644174967`؛ HLS Windows `41644175399`؛ Chrome/Firefox `41644174813`؛ queue/part I/O `41644175162`؛ cross-version upgrade/rollback `41644174974`؛ aria2 adapter `41644174869` وWindows EXE+aria2 evaluation `41644174895`؛ JVM memory `41644174816`؛ source safety `41644174830`.
- aria2: يوجد adapter فعلي خلف خيار اختياري معطّل افتراضيًا. **ليس بديلًا معتمدًا للمحرك بعد**، ولا يُحزم/يُفعّل افتراضيًا حتى تكامل وقياس وتراخيص وأمن وقبول صحيح. HLS/media/crawler تبقى وحدات مستقلة. اختلاف الملاحظات القديمة عن أحدث كود لا يلغي الدليل الحالي.
- تدقيق `2026-10-30` يحصي **76 بند Windows نشطًا**: 22 مكتمل/مثبت، 32 جزئي، 21 غير منفذ، 1 منفذ دون قبول نهائي؛ و**5 Android** في ملحق مؤجل. تدقيق تاريخي لا يُحدّث تلقائيًا من نجاحات لاحقة. **P7-11 ملغى بقرار سابق ولا يُعاد**. لا نسبة إنجاز «نهائية» دون جرد وعينة قبول على HEAD موحّد.

## 1. المصادر الملزمة والتاريخ الذي لا يضيع

1. `docs/Download_Manager_Execution_Plan_v1.md` — P0–P9، فهرس الإعدادات، T01–T20، وسجلات E؛ **لا تستبدل نصه الأصلي**.
2. `docs/IDM_Requirements_Trace_82_2026-10-21.md` — جميع IDs الأصلية؛ عددها التاريخي 82 قبل إلغاء P7-11. لاحظ أن ✓ التاريخية ليست قبولًا جديدًا.
3. `docs/IDM_REQUIREMENTS_AUDIT_2026-10-30.md` — لقطة الحالة 76+5؛ تُستخدم للمراجعة لا للادعاء بأن كل شيء بقي كما هو.
4. `docs/IDM_Repair_Addendum_2026-10-21.md` — N01–N26، مراحل M00–M11، ومراجع A01–A14 وR01–R06 وC00–C39.
5. `docs/AUTONOMOUS_ACCEPTANCE.md` وملفات `docs/checkpoints/` و`docs/releases/` و`docs/handoffs/` الموجودة **في الفرع المعني**؛ لا تفترض أن ملفات PR #4 موجودة في PR #3 أو main.
6. سجلات Windows Actions/artifacts، سجل المصدر والـSHA، والتطبيق المُثبّت الفعلي؛ مصدر الحقيقة أعلى من ملخصات المساعد.
7. ملف مقارنة المستخدم المرجعي: **`IDM_Repair_Handoff_2026-10-21.zip`** بما فيه `MASTER_REPAIR_PLAN_AR.md`، `VERIFIED_STATE_AR.md`، `SCREENSHOT_INDEX_AR.md`، و`refs/screenshots/P01–P27`. **غير متاح للقراءة حاليًا** بعد البحث في Library وDrive؛ لا ادعاء بتدقيق صوره أو مطابقة كاملة. يجب استيراده عند توفره وفتح 27 صورة واحدة واحدة وتسجيل كل أداة/إعداد مفقود، دون إسقاط العمل الموثّق بالنص. لا تخلط بينه وبين ZIPات Windows build أو `jules_session`.
8. توثيق IDM الرسمي **للوظائف فقط**: Main Dialog, Options, Scheduler, Site Grabber؛ الاستفادة من وصف الأوامر والإعدادات لا نسخ المظهر أو الأصول المحمية:  
   - https://www.internetdownloadmanager.com/support/using_idm/using_idm.html  
   - https://www.internetdownloadmanager.com/support/options.html  
   - https://support.internetdownloadmanager.com/support/idm-scheduler/idm_scheduler.html
9. ممنوع نقل كود من `khodemadi/HyperGet` دون شرح مسبق وموافقة المستخدم. استخدم مكتبات موثوقة مع مراجعة التراخيص؛ لا تنسخ واجهة IDM حرفيًا أو أصوله.

## 2. قاعدة الحالة والتحديث المستمر

**الرموز:** `[x]` = **قبول مثبت بدليل مذكور على SHA محدد**؛ `[ ]` = لم يُغلق، حتى لو يوجد كود جزئي.  
**حالات البند عند تحديثه:** `NOT_STARTED` / `PARTIAL` / `CODED` / `CI_PASS` / `WIN_E2E_PASS` / `USER_ACCEPTED` / `BLOCKED` / `DEFERRED` / `CANCELLED`. لا تخلط `CODED` و`PASS`، ولا تحوّل `CI_PASS` إلى `USER_ACCEPTED`.

**كل سجل إغلاق** يجب أن يتضمن: `ID | الحالة | الفرع | SHA | ما تغيّر فعلًا | اختبار وحدة/Integration | Windows Installed E2E | SHA256 إن كان تنزيلًا | المخاطر المتبقية | التاريخ`. لا يعاد إنشاء مشكلة محلولة؛ عند بدء كل دفعة ابحث في تاريخ الـcommits والـActions والملفات المرتبطة بالـID.

**قاعدة المزامنة:** GitHub النسخة القابلة للتحرير، والمكتبة نسخة احتياطية تاريخية. بعد كل دفعة: تحديث مربعات المهام والدليل في هذا الملف بــcommit؛ مطابقة مخطط المتطلبات الأصلي؛ تحديث نسخة المكتبة بنفس المحتوى؛ إضافة سجل مختصر أدناه. عند تعارض فرعين يُفحص HEAD من كل فرع قبل cherry-pick، ولا يُكتب فوق عمل موازٍ.

## 3. بوابات الإصدار — لسرعة التسليم دون خسارة أي مطلب

| النسخة/البوابة | النطاق | لا تُغلق إلا بعد |
| --- | --- | --- |
| **G0 — freeze** | تثبيت متطلبات الأصل/السلوك وصور المقارنة والـbaseline | بنود F وP وN وT مرتبطة بمعرّفات؛ ZIP غير المتاح موثّق `BLOCKED` |
| **G1 — HyperGet Windows Core** | محرك متين؛ HTTP/HTTPS/FTP/HLS؛ تشغيل/إيقاف/استكمال؛ طوابير/حفظ؛ متصفح؛ أزرار ضرورية | جميع اختبارات سلامة البيانات و1/4/16 مقارنة أداء PASS؛ ترقية آمنة؛ EXE واحد، بلا قفز شروط |
| **G2 — IDM Functional Parity** | كل Toolbar/Menus/Context/Shortcuts/Tabs/Settings/Tray/Scheduler/Grabber الأساسية | تدقيق أمر-بأمر وإعداد-بإعداد مع أدلة بعد restart؛ الاختلافات المصرّح بها موثقة |
| **G3 — HyperGet Extended** | Collector/Packages/Plugins/Sites/Captcha/Mirrors/Archives/Media & DASH/yt-dlp/FFmpeg | تغطية fixture ومواقع مصرّح بها، هوية/أمان، استكمال، ومراقبة الفشل |
| **G4 — Final Complete Windows** | stress/توقيعات وتراخيص/أداء مقارن/تثبيت/ترقية/رجوع وتقرير تغطية | لا فجوة P0–P8/N/T مجهولة أو مسكوت عنها؛ الأخطار المفتوحة مذكورة صراحة؛ إصدار مستخدم موثّق |

**الموازاة الآمنة:** بعد G0، مسار (A) سلامة المحرك والأداء، (B) جرد وربط أوامر IDM/الإعدادات، (C) اختبارات المتصفح والـIPC يمكنها التقدم بالتوازي في ملفات مستقلة. يبدأ أساس Packages/Plugins على نماذج منفصلة قبل اكتمال UI، لكن تفعيله يمر من سلامة المحرك. وسائط/أرشيف لا تعطل تسليم G1؛ تبقى على قائمة G3، لا تتحول إلى «اختياري» دون إذن. ابنِ Windows فقط عند تغيّر كود يستدعيه، ولا تنشئ عدة CI builds لعبث وثائقي.

## 4. قائمة العمل التفصيلية — كل ID ثابت طوال عمر المشروع

### F00 — سلامة المصدر والقياس واستعادة الشغل

- [x] **F00-01** — تثبيت اسم **HyperGet** والنطاق Windows أولًا، فصل الوظائف PR #3 عن الثيمات PR #4، والمصدر `main` (دليل كود/وثائق حتى 2026-11-03؛ لا يعني جميع الملفات متطابقة).
- [ ] **F00-02** — إعادة جرد حيّ للمتطلبات `P0–P8 + N01–N26 + T01–T20 + A/R/C + طلبات المحادثات` على أحدث `main` وPR #3، ومعالجة تناقضات الوثائق؛ تقرير diff: `in main / only PR3 / only PR4 / missing / blocked`.
- [ ] **F00-03** — **تم استلام واستخراج أرشيف المقارنة يوم 2026-11-03 (26 صورة فعلية: 15 IDM و11 HyperGet)**، وتم إنشاء فهرس وظيفي لكل صورة في `docs/roadmaps/HYPERGET_IDM_26_SCREENSHOT_FUNCTIONAL_AUDIT_2026-11-03.md`؛ **مفتوح** حتى مطابقة كل control بالكود الحالي وباختبار Windows مثبت. رقم «27» تاريخي ولم توجد الصورة الإضافية داخل ZIP، ولا نفترض وجودها.
- [ ] **F00-04** — خطة cherry-pick/patch لكل وظيفة نجحت في PR #3 إلى main، مع تحقّق الاختبارات على main دون نقل تعديلات الثيمات، وتوثيق تعارضات dependency.
- [ ] **F00-05** — جرد مكتبات/تبعيات/توقيعات/المصدر/الـGPL/APK غير المعني؛ عدم نقل أسرار أو تغيير DB/IDs أو فقدان توافق الإضافة القديمة.

### F01 — المحرك والبيانات والسلامة (أعلى أولوية)

- [x] **F01-01** — وجود اختبار HTTP/HTTPS Range الحقيقي والبصمة والـETag والحجم/الـ206/416، على فرع الوظائف؛ أدلة `41644175005`, `41644174993`، والقيمة الأصلية `P2-01/02` (قبول تكامل محدد لا إغلاق كل حالات المستخدم).
- [ ] **F01-02** — إغلاق الـ16 simultaneous *active* transfers ومقارنة 1/4/16 × 3 جولات × SHA دون تدهور >15% throughput أو >15% memory، مع CPU/IO؛ تحليل 60/56/48/تقلب CI والسبب الجذري. أحدث fail `41644174970`.
- [ ] **F01-03** — pause/resume/stop/stop-all/cancel/reset/delete record/delete partial/delete completed؛ عدم حذف مكتمل دون إذن، عدم مسح مصدر، وإظهار الفرق بين إزالة السجل والملف، مع undo حيث يغطي النطاق.
- [ ] **F01-04** — الذرّية في lifecycle مع كل السباقات: start/pause/cancel/delete/update URL/edit path/queue refill/app shutdown، no late write/no orphan sockets/no resurrection.
- [ ] **F01-05** — crash/kill/restart/recover مع journal وflush وذرية publish (HTTP/FTP/HLS/aria2)؛ فشل disk write/low space/identity swap/symlink/concurrent name دون تلف ولا إعلان نجاح كاذب.
- [ ] **F01-06** — retry budgets مؤرشفة، backoff/429/503/Retry-After/timeout/read/socket reset وhost-specific limits؛ التعامل مع نفاد كل المحاولات/تبديل الشبكة/اتصال محدود.
- [ ] **F01-07** — file identity: ETag/Last-Modified/length/HTTP 200 بدل 206، FTP SIZE/MDTM، تغير روابط أو HLS playlist وmirror؛ رفض خلط ملفات مختلفة؛ explicit restart من الصفر مع موافقة.
- [ ] **F01-08** — unknown/zero/huge/>4GiB/Unicode/RTL/long Windows paths/duplicates/permissions/sparse/readonly/unplugged disk؛ ETA غير معروف عند الجهل، وno-overwrite.
- [ ] **F01-09** — limits global/host/queue/file/part/speed + connection counters وتخزين الإعداد الفعّال وsource of effective value؛ fairness وعدم starvation وتغيير الإعداد أثناء النقل.
- [ ] **F01-10** — بيانات المهام/التصنيفات والطوابير وسجلات الخطأ والتاريخ والعلاقات بعد restart أو update، migration متوافق للخلف وrollback/backup من بيانات اختبار.
- [ ] **F01-11** — checksum SHA-256/MD5 عند الطلب (MD5 للتحقق العادي لا الأمان)، طول/حجم/المصدر والتقدّم، وعدم اعتبار تنزيل مكتمل إلا بعد تحقق ونشر نهائي.

### F02 — البروتوكولات وaria2

- [x] **F02-01** — FTP auth/anonymous/REST resume وWinCred واختبار loopback مثبت على فرع الوظائف، `41644175031`, `41644174967`; **لا يعني مدمجًا في main**.
- [x] **F02-02** — HLS redirect/manifest/segments/assembly+SHA وقيود same-origin/headers مثبتة في Windows `41644175399` على فرع الوظائف.
- [x] **F02-03** — aria2 adapter اختياري وRPC lifecycle تجريبي مع Windows test `41644174869`, `41644174895`؛ **ليس افتراضيًا، ولا معتمدًا لعموم الوظائف**.
- [ ] **F02-04** — تفعيل FTP من main وتأكيد UTF-8/Unicode/مسافات/منفذ/Passive/530/550/Server disconnect/REST unsupported/size mismatch؛ اختبار GUI + Installer + LAN إذا توفر، clean restart.
- [ ] **F02-05** — HLS master/media playlists، تغيّر التسلسل/segment URLs، auth وredirect، restart/pause، ملفات مفردة/مجمّعة؛ إنجاز اختبارات end-to-end ملف قابل للتشغيل وحفظ المصادر عند الفشل.
- [ ] **F02-06** — قرار aria2 النهائي: مقارنة **نفس ملفات/نفس بيئة** مع المحرك الحالي، 16 مهمة، headers/cookies/proxy/resume/cancel/path/metadata/loopback + WinCred/installer وGPL؛ إما اعتماد موثق دون فقد قدرات أو إبقاؤه opt-in، ولا تبديل صامت.
- [ ] **F02-07** — توثيق ودعم ما اعتمده المستخدم من أنواع التنزيل الإضافية؛ DASH في F08، ومراجعة SFTP/FTPS/MMS/legacy/dial-up كقرارات مطابقة منفصلة لا اختزالها في FTP. BitTorrent كان له قرار إلغاء تاريخي؛ **لا يُفعّل افتراضيًا**، ويُسجّل أي اختلاف لاحق قبل التنفيذ.

### F03 — كل أزرار وأشرطة وقوائم IDM (الوظيفة لا المظهر)

**مبدأ الصيانة:** أمر واحد يشترك فيه Toolbar/Menu/Right-click/Shortcut/Tray، وليس أربع implementations مختلفة؛ disabled/enabled/loading/selection/confirmation بعد تغير الحالة؛ كل تسمية ملائمة بالعربية والإنجليزية. حوافز من IDM الرسمي وليست دليلًا على وصول صور ZIP.

- [ ] **F03-01** — Toolbar: `Add URL`, `Resume/Start`, `Stop`, `Stop All`, `Delete`, `Delete Completed`, `Options`, `Scheduler`, `Start Queue`, `Stop Queue`, `Grabber`, `Refresh Address` إن كان معتمدًا؛ كل زر متصل بفعل حي وتتغير صلاحيته بوضوح.
- [ ] **F03-02** — القوائم العلوية `Tasks / Downloads / View / Help` وفروعها والـsubmenus، نفس الأوامر مع Toolbar بلا اختلاف في النتائج؛ copy URL/details/open file/open folder/properties/move/queue/category.
- [ ] **F03-03** — Right-click على ملف، مجموعة ملفات، فراغ القائمة، Category، Queue، System Tray، وصف الأوامر وعملها على multi-selection.
- [ ] **F03-04** — shortcuts حقيقية: `Ctrl+F` search، `Ctrl+C/V` نص داخل search، `Escape` مسح، `Ctrl+Shift+O` open folder، `Enter`, `Delete`, multi-select, focus/tab order، وحرّك الأفعال الأخرى وفق جدول مقارنة IDM.
- [ ] **F03-05** — شجرة Categories/Queues: add/edit/delete/move/merge، counts، default directory/file types/site rules، Filters `All/Unfinished/Finished/Queues` مع الحالة الصحيحة والبحث المركب.
- [ ] **F03-06** — جدول التنزيلات: Name/Size/Status/Remaining/Speed/Last Try Added/Description/URL/Category/Queue/Progress؛ sort stable، resizing، reorder، hide/show، exact pixels/defaults، horizontal scroll، selection 1k/10k، persist across reboot.
- [ ] **F03-07** — status bar: عدد الملفات والمحدد والمكتمل/النشط والسرعة/الحجم عند إمكانية الحساب، دون كذب في unknown؛ Menu/Toolbar/Status bar إظهار/إخفاء إن كان له إعداد وظيفي.
- [ ] **F03-08** — نافذة Add URL/Batch: clipboard, URL, headers/login, start now/later/queue/category/description/filename/directory/test address/errors؛ منع duplicate وحالات URL malformed.
- [ ] **F03-09** — نافذة Properties/Edit: URL/source, credentials ref, size/ETag/status, description/category/directory, refresh address، تغيير أثناء التنزيل بأمان.
- [ ] **F03-10** — نافذة Progress: `Info / Speed / On Completion / Connections (parts)` كما في المرجع، ذات بيانات فعلية؛ pause/resume/cancel/open folder/graph hide-show؛ حفظ Speed وOn Completion بعد **إغلاق التطبيق وإعادة فتحه**؛ عدم الخلط بين نسبة الملف وصفوف الأجزاء.
- [ ] **F03-11** — Download Complete dialog وسلوك فتح الملف/المجلد/الإغلاق/عدم الإظهار، حسب الخيارات؛ no auto-run غير مأذون، لا مطالبة كاذبة بالنجاح.
- [ ] **F03-12** — Tray actions: open/minimize/close-to-tray/exit, pause/start/stop queues, speed preset, options, updates, notification count، حفظ سياسة الإغلاق والصوت والبدء التلقائي.
- [ ] **F03-13** — قبول Windows keyboard/RTL/Unicode/long names/DPI 100/125/150/200% **للوصول إلى جميع الأوامر**؛ تعديل محاذاة أو مقاس فقط عند كسر وظيفة، لا تغيير ألوان/ثيمات أو Liquid Glass.
- [ ] **F03-14** — طابِق الصور **P01–P26 من الأرشيف المتاح** بندًا بندًا؛ أظهر الحالة per control مع أحدث main وPR3 ودليل Windows GUI، وابحث عن صورة إضافية فقط إن وجد مصدرها؛ لا إغلاق للمطابقة الكاملة بمجرد استخراج الصور.

### F04 — الإعدادات كلها، وربط القيم بالوظيفة الفعلية

**تراتب التخصيص:** Global → Site → Package → Download، والقيمة الفعالة ومصدرها مرئيان. لكل إعداد: نوع/تحقق/افتراضي/حفظ/تطبيق Live أو restart/Reset/Export (غير سري)/اختبار تأثير. توحيد الخيارات المتكررة دون فقد معناها.

- [ ] **F04-01 General / Integration** — Browser integration toggles, startup with Windows, tray/exit, URL clipboard monitoring opt-in, context menu/shortcut integration, browser extension registration/health.
- [ ] **F04-02 File Types** — امتدادات يتم التقاطها أو استثناؤها، MIME وsite/domain rules، wildcards آمنة، filetype→category، والإرجاع للمتصفح عند الاستثناء.
- [ ] **F04-03 Connection** — connection profiles, max concurrent tasks, max connections/parts per file, host limits, global/per-file speed, dynamic sections, retries/timeouts، التغيير الفعلي الحي.
- [ ] **F04-04 Save To** — folders by category/site/filetype, remember last selected, temporary directory, collisions/rename/no-overwrite, free-space checks, disk changes and move/rollback.
- [ ] **F04-05 Downloads** — start/progress/complete dialogs, silent/queue start mode, checksum/virus scanning preferences, on-completion behavior, UA for manually added URLs, safe restart semantics.
- [ ] **F04-06 Proxy / Socks** — HTTP/HTTPS/SOCKS5 and auth, bypass list, PAC if supported, SSL/TLS handling without unsafe defaults؛ test against local fixture and external only as authorised.
- [ ] **F04-07 Site Logins** — credential list/add/edit/remove, per-site scope, Windows vault, clear logged-in state, no leaked password/cookie in JSON/logs/export.
- [ ] **F04-08 Scheduler / Queues** — weekdays/time windows, timezone, wake/sleep missed tasks, start/stop, limit concurrency/traffic, priority, repeat, daily data quota, reconnect and completion actions.
- [ ] **F04-09 Notifications / Sounds / Completion** — notifications, sound files and volume/mute, completion/error/queue finished, optional antivirus, exit/sleep/shutdown with countdown and cancel; **no shutdown in unisolated CI**.
- [ ] **F04-10 Advanced / UI Functional** — basic/advanced, setting search, descriptions, inherited values, reset section/global, file list columns, toolbar visibility, keyboard focus, drag/drop/drop target; لا نلمس الثيمات.
- [ ] **F04-11 Backup / Import / Export** — non-secret settings schema/version/migration/export redaction, backup/restore/rollback with data loss checks; secrets opt-in separate encrypted scheme not plain text.
- [ ] **F04-12 Legacy / parity decisions** — dial-up, site grabber settings, browser video button, IDM-specific legacy switches، وسجّل لكل واحدة `supported / alternative / deferred with explicit user decision` بدل إخفائها أو التظاهر أنها فعّالة.

### F05 — المتصفح والروابط المؤقتة والحسابات

- [x] **F05-01** — real Chrome + Firefox native messaging capture smoke on installed Windows, `41644174813` (على PR #3 فقط).
- [ ] **F05-02** — ضمان Edge أيضًا وإصدار إضافة متوافق مع اسم HyperGet، الاستمرارية بعد upgrade/rollback، وnative host registration لبيانات قديمة.
- [ ] **F05-03** — automatic capture اختياري، Download selected/all، إرسال الصفحة، تصفية الروابط/الامتدادات، video button عند الدعم، اختبارات رفض/تعطّل host/fallback browser.
- [ ] **F05-04** — نقل cookies/headers/referer/authorization فقط للأصل المأذون، origin-bound redirects، permission minimisation، no browser history harvest/leaks.
- [ ] **F05-05** — Refresh Download Address للـ403/expired signed URLs عبر browser/manual/host plugin، فحص الهوية قبل إكمال جزء سابق.
- [ ] **F05-06** — Clipboard URL/batch/text file `.txt/.url` UTF-8/UTF-16، drag/drop أصلي متعدد الملفات، تجاوز المجلد/الملف الضخم/المكرر، وتوثيق Windows GUI drop الحقيقي.
- [ ] **F05-07** — site credentials/cookie expiration/user prompt، إخفاء الأسرار من الأخطاء/diagnostics وexports، مع سيناريوهات تسجيل دخول/خروج مصرح بها.

### F06 — الطوابير والجدولة والتصنيفات

- [x] **F06-01** — Queue + Part I/O lifecycle Windows regression على PR #3 `41644175162` (ضمان نطاق هذا الاختبار فقط).
- [ ] **F06-02** — Create/edit/rename/delete queue، add/remove/move ordering، priority + host fairness، queued paused لا يعاد تشغيله، حدود global/queue/site/part.
- [ ] **F06-03** — Scheduler start/stop/multiple queues, repeated schedules/day-of-week, missed wake events, auto retries, time/traffic quota، واستعادة الجدول بعد restart.
- [ ] **F06-04** — categories automatic path/extension/site rules، custom rule precedence، وعدم ضياع التصنيف/الطابور بعد إعادة فتح البرنامج.
- [ ] **F06-05** — on completion لكل Task/Queue/Batch: notification/sound/open folder/antivirus/script with consent/sleep/shutdown countdown optional (no accidental destructive action).

### F07 — Link Collector وPackages وPlugin Framework

- [ ] **F07-01** — Link collector يقبل page URL وplain URL/text/clipboard، crawling محدود depth/count/time/domain، cancellation وdedup يحافظ على query/signature.
- [ ] **F07-02** — review table للاسم/المصدر/الحجم/الحالة/عدد الحزم/نوع الرابط/الطريق؛ start later vs queue vs auto-confirm/auto-start per trusted source فقط.
- [ ] **F07-03** — Package Service دائم: id/name/expected part count/source of count/order/index/duplicate/unknown/missing/incomplete/grouped progress + restart.
- [ ] **F07-04** — host availability states: online/offline/uncertain/login/captcha/temporary/blocked، لا اعتبار فشل HEAD دليلًا على الغياب.
- [ ] **F07-05** — plugin API `Crawler/Host/Resolver` مع versioned contract, error types, timeouts, cancellation, network scopes, isolated tests، وتوافق repository data.
- [ ] **F07-06** — trusted plugin import/enable/disable/update/rollback/signature/checksum, settings and account storage، سياسة sandbox للغير موثوق قبل التنفيذ.
- [ ] **F07-07** — local fixture plugin يجلب حزمة أجزاء فيها duplicates/missing/locked links، end-to-end Windows إثبات قبل دعم مواقع حقيقية.

### F08 — Sites / Captcha / Mirrors / Archives / Media

- [ ] **F08-01** — supported-sites matrix لمواقع يختارها المستخدم لاحقًا وروابط مصرح بها: crawl/direct/account/rate-limit/captcha/refresh وسبب unsupported؛ لا افتراض قدرات عامة.
- [ ] **F08-02** — Captcha task queue single-active default: user browser challenge, notify, timeout/expire/cancel/retry/skip/background unaffected، لا تخزين حل قديم أو تجاوز حماية المواقع.
- [ ] **F08-03** — mirror selection/rank/failover مع immutable content identity والتحقق من hash/size/validators وعدم خلط بيانات من مصدرين مختلفين.
- [ ] **F08-04** — كشف روابط أجزاء RAR/ZIP/7z missing/duplicate/order مع **confidence** وأسباب؛ ZIP range preview if compatible.
- [ ] **F08-05** — optional extractor ZIP/RAR/7z multipart encrypted/password/user consent؛ no auto execution/delete، disk budget، archive traversal/symlink/bomb/path hazards guard، preserve sources on failure.
- [ ] **F08-06** — HLS quality variant selection، DASH manifest/segments، yt-dlp adapter for supported sites (including YouTube when compatible), playlists, subtitles/audio selection and update policy.
- [ ] **F08-07** — FFmpeg (or vetted equivalent) audio/video remux/merge with progress/cancel/timeout/validation/licensing، never lose source fragments until verified final.
- [ ] **F08-08** — explicit live-stream scope/capture/reconnect/stop/finalise policy، separate from VOD; Site Grabber/site copy wizard مع filters/depth/filetypes/size/request cap/preflight review.

### F09 — أدوات الإدخال والتكامل والتشغيل

- [ ] **F09-01** — CLI complete: add/start/stop/status/list/queue/import/export with safe path/headers and exit codes، لا HTTP API مفتوح بلا مصادقة.
- [ ] **F09-02** — batch URL generators (numeric ranges/wildcards) وpaste multiple + duplicate preview + parsing quote/Unicode؛ أكثر من ملف `.url/.txt` في drop واحدة.
- [ ] **F09-03** — open with/share via Windows/URI/clipboard/tray/drop-target optional؛ لا hijack browser silently.
- [ ] **F09-04** — import/export download list and jobs history/backup/restore، exclude secrets by default and preserve IDs/status/settings.
- [ ] **F09-05** — تحديث البرنامج: check/apply/restart/rollback provenance and known binary hash؛ non-invasive notification خيار المستخدم.

### F10 — أداء Windows، أمن، واختبار واقعي

- [ ] **F10-01** — gate real Windows HTTP 1/4/16 *active* transfers each 3 paired rounds × 63 SHA; measure throughput, WorkingSet and Private Bytes; no 15% regressions vs fixed baseline. Blocked by `41644174970`.
- [ ] **F10-02** — compare HyperGet vs actual installed IDM: same PC/CPU/network/files/times/sequences ×3 مع process tree، idle/minimised/window open/closed/task1/4/16; لا نسبة RAM «أخف من IDM» دون الدليل.
- [ ] **F10-03** — 1,000/10,000 items startup/search/sort/filter/multi-select/scroll/category counts، latency p50/p95، RAM/CPU/handles budgets.
- [ ] **F10-04** — 50 open/close windows + 30 minute soak + pause/resume/retry/100 cancel loops، no memory/handles growth unexplained and no UI stalls.
- [ ] **F10-05** — security review: cookies/secret redaction, IPC forged/oversized messages, plugin sandbox, SSRF/private address policy, path/symlink, archives, binary provenance/signatures/licences/GPL distribution.
- [ ] **F10-06** — Windows real E2E for DPI, accessibility/keyboard, Arabic+English names/links and system changes *on controls*, Windows 11 + Chrome/Edge/Firefox + clean and upgraded install.

### F11 — دمج وتسليم وتدقيق نهائي

- [x] **F11-01** — Windows cross-version upgrade/rollback test على فرع الوظائف `41644174974`؛ لا يعد إصدار المستخدم النهائي.
- [ ] **F11-02** — ترقية كل دفعة functional-only ناجحة إلى main بــselective merge/cherry-pick، مراجعة diff والتصادم مع PR #4 والثيمات، CI exact HEAD على main.
- [ ] **F11-03** — package exactly ONE Windows `.exe`, SHA-256, install/uninstall/upgrade/restore/compatibility, preserving files/settings/credentials, no `preview` or experimental identity، signed only if available and truthful.
- [ ] **F11-04** — final coverage report P0–P8/N01–N26/T01–T20 + original P01–P27 screenshot controls عند توفرها، مع `implemented / tested / blocked / explicit user deferred`، بلا مطالب منسية.
- [ ] **F11-05** — user-usable final installer link, version, exact commit, SHA, release notes, known issues and recovery instructions؛ لاحقًا التحديث على التطبيق نفسه وليس إعادة إصدار منفصل بلا ترقية.

### F12 — نطاق Android المحتفظ به **لكن ليس بوابة Windows**

- [ ] **F12-01** — احتفظ بـP9-01..05: إذن منفصل لتحديد Android، reuse مشتركة، UI/service/background/battery/browser media، واختبارات restart/network loss؛ **DEFERRED** حتى استقرار Windows، دون حذف.

## 5. مصفوفة المطابقة القديمة والجديدة

| المصدر | المجموعة الجديدة | التغطية المطلوبة/الحكم |
| --- | --- | --- |
| P0 / P1 | F00/F11 | هوية/نطاق/مصدر واختبارات وتراخيص |
| P2 | F01/F02/F06 | download lifecycle, retry, throughput, durable state |
| P3 | F03/F04/F09/F10 | واجهة وأوامر وتبويبات وإعدادات **وظيفيًا**؛ الألوان والشكل في PR #4 |
| P4 | F05 | browser, private headers, URL refresh |
| P5 | F07 | collector/packages/plugins |
| P6 | F08 | sites/captcha/mirrors/archive |
| P7 | F02/F06/F08/F09 | media/advanced IDM/FTP/proxy/CLI; P7-11 CANCELLED |
| P8 | F10/F11 | installed acceptance/perf/EXE |
| P9 | F12 | Android deferred preserved |
| N01–N06 | F10/F11 | install/benchmark/50-cycle evidence |
| N07–N12 | F02 | FTP |
| N13–N15 | F03/F10 | functional download window controls, data states and accessibility (not theme) |
| N16–N17 | PR #4 separately | themes/icon skin/motion **not** tasks in this functional plan |
| N18–N20 | F03 | menu/toolbar/context/columns/search/shortcuts |
| N21–N23 | F04/F03 | effective settings/backup/tray |
| N24–N26 | F01/F11 | network suite and durable trace |
| T01–T20 | F01–F11 | every applicable test retained in original plan |
| IDM screenshots P01–P26 (26 actual files; historical expectation 27) | F00-03/F03-14 | Original ZIP recovered; audit/index written; current-code wiring and installed Windows parity OPEN |

## 6. عقود الاختبارات اليومية وقياس التقدم

**المعادلة:** `Accepted = task completed + demonstrated on code SHA + expected integration/Windows evidence + verified published state`. لا تحسب خطًا برمجيًا، عدد commits أو نجاح compile وحده كنسبة تقدم.

- **كل Pull Request / slice:** source contract + unit + integration، exact SHA، no existing tests weakened، diff review and migration check.
- **المحرك:** SHA final matched + active sockets + persistence/kill/restart + pause/resume/cancel safe + 1/4/16 fairness + resource budget.
- **كل أمر/إعداد:** يظهر في ToolBar/Menu/Context/Shortcut المناسبة، يعمل على الحالة الصحيحة، يتعطّل عند عدم الإمكان، يحفظ ويستعيد بعد restart، وله UI Windows E2E لا مجرد mock.
- **كل plugin/site/media/archive:** fixtures success/failed/timeout/cancel/credentials/change-of-source، network/policy/licensing guard.
- **كل Release:** clean install → upgrade → perform live download → kill/restart → finish exact SHA → rollback/uninstall; no user-file loss; one valid installer.
- **لو المرجع ZIP مفقود:** أكمل البنود المنصوص عليها، ولكن تظل F00-03/F03-14/F11-04 مفتوحة؛ لا إعلان «100% IDM parity» حتى استلامه.

**مؤشرات تُحدّث من الأدلة لا من الانطباع:** `P0–P8 accepted/total`, `F accepted/total`, `N accepted/26`, `T passed/20`, `IDM command coverage passed/total`, `settings effective+persistent passed/total`, `Windows exact-SHA gates passed/total`, `active16 pass/fail`, `main vs feature-only divergence`. عند أول تحديث اختبارات G0 احسب المقام من تدقيق كود حديث ولا تخترع نسبة إجمالية.

## 7. أول ثلاث دفعات تنفيذ موصى بها (أعلى عائد للوقت)

**Batch A — Stability & promotion design (G0→G1، لا تنتظر ZIP):** اقرأ HEAD/main/PR3/Actions؛ تشخيص أداء active16 مع CPU+allocation+socket evidence بدل تغيير Semaphore عشوائيًا؛ اختر حلًا ينجح بوابات 1/4/16. بالتوازي جهّز خريطة PR3→main لمعرفة ما جُرّب وما لم يندمج. **Exit:** benchmark PASS + exact SHA + list of safe clean merges.

**Batch B — IDM functional command census (G1→G2):** ابنِ جدول التحكم من `F03/F04` × (Toolbar/Menu/Context/Shortcut/Tray/Dialog/Settings)، وجرد الحقيقي في main؛ نفذ الناقص حسب impact واختبار action acceptance، مع اختبارات حفظ الإعدادات بعد Restart. لا عمل على اللون/شكل شريط Liquid؛ التأكد من *وظائف* تبويباته فقط. **Exit:** لا زر وهمي ولا setting يعرض قيمة لا تؤثر في المحرك.

**Batch C — Packages foundation / site fixture (G2→G3):** `PackageService` وCollector محدود ثم trusted plugin API + fixture package 20-part وmirror + missing + captcha state. بناء ملحقات Media/DASH/FFmpeg على عقد منفصل. **Exit:** end-to-end + source validation + persist/restart + failure matrix. ثم تكملة بقية F08/F09/G4.

**تسريع حقيقي:** لا تعيد الشغل المقبول؛ لا ترسل commit جديدًا قبل تشخيص فشل السابق؛ لا تتبع «اختبارات 100%» على SHA آخر؛ لا تفتح فرعًا جديدًا بلا حاجة؛ merge جاهز بعد اختبار على main؛ مسارات متوازية في ملفات غير متداخلة؛ docs-only updates لا تستدعي Windows build؛ عند إصدار G1 لا تؤجل بسبب G3 مع المحافظة على كل بنوده.

## 8. سجل المتابعة والدليل — يُحدَّث بعد كل دفعة

| التاريخ | الكود / الفرع | أنجزنا فعليًا | الدليل | ما بقي / الإجراء التالي | مكتبة ChatGPT متزامنة؟ |
| --- | --- | --- | --- | --- | --- |
| 2026-11-03 | `3582e0a` PR #3 / `457d3d2` main قبل توثيق الخطة | baseline جرد/خطة فقط. 14 Windows Actions success, 1 paired benchmark fail؛ لا كود وظيفي جديد بهذا المستند | `41644174970` fail; successful IDs in §0 | أولًا F01-02 ثم F00-02/04؛ F00-03 BLOCKED على ZIP الأصلي | تتزامن عند رفع نسخة الخطة |

### بروتوكول Handoff لأي محادثة جديدة

1. اقرأ هذا الملف كاملًا؛ ثم `docs/IDM_REQUIREMENTS_AUDIT_2026-10-30.md`، `docs/Download_Manager_Execution_Plan_v1.md`، `docs/IDM_Repair_Addendum_2026-10-21.md` وملف handoff للفرع الذي **ستنّفذ عليه**.
2. افحص GitHub HEAD الفعلي لكل من `main` وPR #3 وPR #4 وأي branch للوظيفة، وActions على **نفس SHA**. لا تعتمد SHA داخل هذا السجل كأنه الأحدث.
3. ابحث عن ID الذي ستنفذه في الكود والـcommits/PR/CI، ثم نفذ **فقط الجزء الناقص** على مسار الوظائف. لا تلمس PR #4 أو الثيمات/الألوان/Liquid Glass.
4. حدث checkbox/status لهذا ID فقط بعد الدليل. اكتب URL/SHA/اختبار Windows ومشكلة متبقية؛ لا تغيّر النص القديم P/N/T أو تمسح FAIL history.
5. ارفع doc commit في GitHub مع تحديث الـevidence، وحدث نسخة `/HyperGet/HYPERGET_FUNCTIONALITY_MASTER_PLAN.md` في المكتبة إن أمكن. لا تترك النسختين مختلفتين بصمت.
6. في الرد للمستخدم: ما تغيّر، ما اختُبر وما فشل، SHA/PR/Actions/EXE إذا موجود، ما سيُنجز بعده، وعدد المتطلبات المغلقة وفق دليل.

**رسالة متابعة جاهزة:** «استكمل وظائف HyperGet اعتمادًا على `docs/roadmaps/HYPERGET_FUNCTIONALITY_MASTER_PLAN.md` في GitHub. افحص أحدث HEAD وActions وPR #3 أولًا، لا تكرر المكتمل، لا تمس PR #4 أو الثيمات، نفّذ أسرع دفعة مستقلة قابلة للاختبار، دمجها بعد النجاح في main دون نقل الثيمات، ثم حدّث نفس الخطة ونسخة المكتبة واذكر دليل SHA/Windows.»

## 2026-11-03 — فحص Google Drive لمرجع صور IDM (G0 / F00-03)

- بُحث داخل Google Drive المتصل عن ملف IDM_Repair_Handoff_2026-10-21.zip وعن SCREENSHOT_INDEX_AR.md وعن صور P01–P27، وبأنماط أسماء وأرشيفات وملفات صور؛ **لم يُسترجع الأرشيف الأصلي أو فهرسه، ولا تُعتبر الصور الـ27 مكتملة**. نتيجة البحث لا تثبت أن الملفات غير موجودة في كل المصادر.
- **عُثر على صورة فعلية مستقلة من واجهة IDM الأصلية** تعرض قائمة «عرض» وخيارات حجم/نمط شريط الأدوات: [image(20261014-203754).png](https://drive.google.com/file/d/1zi1XqRNRxM6qE6fJF7tgT9GuZ91VmgCs/view). تصلح مرجعًا وظيفيًا لاختبار أفعال View/Toolbar، وليست بديلًا عن مجموعة P01–P27.
- عُثر على لقطتي Task Manager للمقارنة التاريخية بين IDM ومدير التحميل: [1000066710.png](https://drive.google.com/file/d/1V79Hs0t4FOtp_sZNgmjJxKfRH5WtINab/view) و[image(20261014-210754).png](https://drive.google.com/file/d/12laj1JTccRxkp4G1vYQ8rPz_7pmvGCdd/view). **الأرقام فيها لقطات آنية، وليست benchmark مضبوطًا**.
- توجد صورة نموذج واجهة مرجعية [image(20261014-204353).png](https://drive.google.com/file/d/1lT2Y6fbxCPleFSA46yHzn5v2V3O0WD_K/view) موسومة «نموذج P0 — لا ينزل ملفات»، لذلك لا تُعدّ اختبار وظائف ولا لقطة من المنتج الأصلي.
- **الإجراء المستمر:** أبقِ F00-03 مفتوحًا؛ استكمل مراجعة جميع الصور الـ27 إن ظهرت في أرشيف أو مجلد محدد. في المقابل نفذ فهرس أدوات/اختصارات/إعدادات IDM من الوثائق والصور المثبتة ولا توقف بقية الوظائف. لا تلمس الثيمات.


## 2026-11-03 — وصلت صور المقارنة الأصلية، انتهاء عائق المصدر وبدء فحص كل control

**التصحيح الأحدث الحاكم بعد ملاحظة Drive أعلاه:** رفع المستخدم أرشيف `archiveNew.zip` في نفس اليوم، وفُتح بالفعل. يحتوي **26 PNG، لا 27**: original IDM UI = 7، original IDM settings = 8، old HyperGet UI = 6، old HyperGet settings = 5. أربعة مجلدات في ZIP. SHA-256 للأرشيف `f83ae37695224ddaa6f0095b0d2c23f592ac27b4c7a35650481a8fd1f817e8bd`.

- فهرس مقارنة بندية مكتمل المصدر: [`docs/roadmaps/HYPERGET_IDM_26_SCREENSHOT_FUNCTIONAL_AUDIT_2026-11-03.md`](HYPERGET_IDM_26_SCREENSHOT_FUNCTIONAL_AUDIT_2026-11-03.md)، يتضمن 26 صورة، اسم كل ملف، SHA-256، الوظائف الظاهرة، IDs، فجوات مرشحة للاختبار، ومعايير قبول على Windows.
- المصدر الخام **خاص** في مكتبة ChatGPT: `/HyperGet/IDM_REFERENCE_2026-10-21_26_IMAGES_PRIVATE.zip`، والفهرس النصي محفوظ أيضًا في المكتبة. **لا ترفع الصور الخام إلى GitHub**: إحدى صور HyperGet القديمة تكشف قيمة API key، وأخرى تحوي مسارات محلية؛ لا تُكرر هذه القيمة في docs/CI. لو كانت نشطة يجب تدويرها وإخفاؤها في الواجهة.
- أعلى الوظائف التي يجب تدقيقها من الصور: قائمة Tasks (batch URL/clipboard/site grabber/import/export/Drop Target)، Downloads (Stop All/Delete Completed/Find Next F3/Speed Limiter/Queue scheduler)، View (Sort/Category/Toolbar customization)، Settings (browser lists/exclusions, file types/site exclusions, category paths and staging folder, duplicate/notification policy, max connections/host, proxy/PAC/VPN legacy, event sounds). **المطلوب مطابقة *السلوك* لا الشكل.**
- **عائق المصدر القديم F00-03 تحول من `missing archive` إلى `reference recovered / implementation acceptance open`**. لا تعتبر الوظائف الموجودة في صور 2026-10-21 غير منفذة الآن؛ احكم من أحدث main وPR #3 وWindows Actions، ولا تكرر الشغل.
- **Next action:** جرّد Source الحالية أمام جدول كل control في الملف التفصيلي واملأ لكل منها `in main / only PR3 / partial / missing` مع اختبار Windows. F03-14/F11-04 تبقيان OPEN لحين التحقق الوظيفي.
- هذا تحديث **وثائقي فقط**؛ لا تغييرات كود/ثيم/أزرار في هذا الـCommit، ولا إعلان نجاح CI جديد.

| 2026-11-03 | `main` docs-only reference recovery | فُحص ZIP الحقيقي، 26/26 PNG، فهرس P01–P26 ودليل source+requirements؛ صور حساسة بقيت بالمكتبة | Audit المذكور + ZIP SHA-256 | action-by-action code audit ثم Windows evidence؛ صورة 27 غير موجودة بهذه الحزمة | ZIP وAudit وMaster محفوظة بالمكتبة |

### قاعدة جديدة 2026-11-03 — HANDOFF سريع وإجباري بعد كل دفعة كبيرة
- اكتب/حدّث **الملف الثابت** `docs/handoffs/HYPERGET_FUNCTIONALITY_HANDOFF_CURRENT.md` بعد كل جولة تطوير كبيرة أو دمج أو فشل مهم في GitHub Actions، وليس فقط عند امتلاء المحادثة.
- اجعله قصيرًا جدًا: HEAD/PR، التغيير الفعلي، نتيجة CI على نفس SHA، العائق والمتبقي، ثم الخطوة التالية. ممنوع إعادة شرح التاريخ كاملاً أو نقل نصوص حساسة من الصور.
- زامِن نفس الملف إلى `/HyperGet/HYPERGET_FUNCTIONALITY_HANDOFF_CURRENT.md` في مكتبة ChatGPT مع تحديث الخطة الرئيسية، أو اذكر أي فشل مزامنة بصراحة. هذه وثيقة للوظائف فقط، منفصلة عن handoff الثيمات.


## 2026-11-03 — تنفيذ وظيفي فعلي: Settings Backup + API Key Privacy (F04-11/F05-07، حالة جزئية)
- **PR #3:** `92e228f` نفّذ استيراد/تصدير تفضيلات غير سرية من نافذة Settings وتخزين `DataStore.updateData` الذري؛ `ff0fd84` أصلح smart-cast Kotlin وكشف المفتاح، وحجب Browser API Key افتراضيًا مع Show/Hide؛ **Windows Kotlin + unit tests PASS** run [41705173090](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41705173090). فشل البناء السابق عند `92e228f` بسبب smart-cast ثم أصلح، ولا يمحى من التاريخ.
- **main:** نُقلت الميزة وحدها إلى `673b29dbd8e2219b48eaa8b192dd959f3aaf5652` في 11 ملفًا دون دمج PR3 كله أو تغيير الثيمات. `main` وPR3 متباعدان؛ لا دمج شامل. اختبار main الخاص [41706083060](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41706083060) **PASS** للـWindows compile/tests وNSIS package/install/uninstall ورفع artifact؛ الـEXE `HyperGet_1.0.0-main-snapshot_windows_x64.exe` بطول 79,657,971 بايت وSHA-256 `74070291e71edca14119f3d49ace3bdea0b9632323146f544d8abdbe87af1b46` مستخرج من artifact `13267045399`؛ **مرشح اختبار، ليس إصدارًا نهائيًا بكل الوظائف**.
- **نطاق فعلي:** export يسمح مفاتيح إعدادات معروفة ويستبعد `apiAuthKey/apiEnabled/apiAuthEnabled/apiPort/ignoreSSLCertificates/autoStartOnBoot`، ويمنع استيرادها، يحد حجم الملف، يرفض قيم التزامن/المحاولات غير الصالحة، ويترك الإعدادات الحالية محفوظة عند فشل decode؛ زر export لا يكتب فوق ملف موجود، وزر import يتطلب تأكيد.
- **لا تغلق F04-11 أو F05-07 بالكامل:** الإعدادات المستقلة لـproxy/per-host والحسابات/الحزم وملفات backup الكاملة غير مشمولة؛ لا يوجد قبول Windows GUI يدوي، وتدقيق أمان عام ما زال مطلوبًا. تابع بعد اجتياز بناء main، ثم تحقق الـ16 download benchmark.

## 2026-11-04 — F03-04 next/previous search result (CODED; Windows CI pending)
- Branch `feature/idm-find-next-f3-20261104` based on `main` `eebc55752ee92364d9fca6f43362a91ef218665f`. Scope strictly functionality, no themes or Liquid Glass changes.
- Code commits `4b67c4f`, `caf4416`, `50f04f3`, `1a7033e`: real `F3` next and `Shift+F3` previous filtered match. Selection respects the table's active sort order, wraps around, and uses existing `ScrollToDownloadItem` to reveal a selected row. Empty query focuses Search instead of moving through unrelated downloads.
- Four shared desktop Kotlin regression tests added: forward/reverse, wrap, invisible selection, no matches, one item and 10k rows.
- **CODED, NOT WIN_E2E_PASS** until exact-head Kotlin compile/tests, installed Windows GUI keyboard and screen capture succeed. F03-04 remains OPEN overall: keyboard/text-copy and full shortcut/action parity require separate acceptance.
- PR #3 `ff0fd84` stays Draft and is NOT merged into main. PR #4 is visual-only and untouched. Main remains untouched in this batch.

## 2026-11-05 — F09-02 bounded batch URL generation (Windows verified candidate)
- Candidate branch `feature/batch-url-safety-20261105`, originally based on `main eebc55752ee92364d9fca6f43362a91ef218665f`, PR #7.
- Production changes: `BatchImportSafety.kt` and `BaseBatchDownloadComponent.kt`; overflow-safe Long count for `0..Int.MAX_VALUE`, strict 1,000-URL preflight BEFORE iteration, full-link and duplicate checks at confirmation, single-item behavior preserved, custom wildcard padding clamped to 10 digits, no printing signed URLs on invalid confirmation.
- Eight **real Kotlin desktop regression cases** added in `BatchImportSafetyTest.kt`: overflow/no iteration, invalid ranges, zero padding/order, duplicate rejection, malformed middle URL, exact count, 10k pre-enumeration rejection, and excessive custom padding.
- Local isolated `kotlinc 1.9.0` + Java 21 smoke PASS on pure helper/wildcard semantics; not a Gradle substitute.
- **Exact production/CI SHA `5b9c61629e37649ccf79bb0e86b61e46d25cb4cd` Windows Actions [41976032690](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41976032690): success**, including shared desktop Kotlin tests, compile, Windows package, installed NSIS/safe-uninstall. Later workflow-reconciliation and documentation-only commits do not change these Kotlin sources; recheck head status before merge.
- Scope excludes Liquid Glass, icons, palettes, HTTP/FTP engine and existing user data. F09-02 still PARTIAL overall: numeric generation safety is accepted; import/export, GUI paste UX and batch duplicate-preview have separate requirements.
- Concurrent PR #5 was independently merged to main as `e7c51b403e4fcc0ea10c415082f3c9059cb13eff` after Windows run `41825085206`; PR #3 benchmark memory failure and PR #6 HTTP 429 acceptance remain separate.

- **Cross-feature Windows proof:** [41977058414](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41977058414) SUCCESS on SHA `239d96e3b129c7c898c67d88a069eb99e5877817`: tested batch URL safety plus already-verified F3 main search together, built Windows NSIS and completed safe install/uninstall. CI branch-trigger cleanup/docs commits after this SHA did not change Kotlin. Full source-IDM user acceptance and unrelated PR3 memory benchmark remain OPEN.

## 2026-11-05 — P2-05 / F01-06 HTTP 429 hidden-retry regression verified
- PR #6 adds a bounded local-server Kotlin integration test for 429 + `Retry-After: 7`, requiring one observable HTTP attempt and correct status/header preservation at the low-level HTTP client. No HTTP/FTP/aria2 production engine code changed.
- Three Windows exact-source suites: [41976256808](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41976256808) SUCCESS (isolated); [41977309984](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41977309984) SUCCESS (with F3); [41978082441](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41978082441) SUCCESS (with F3 + Batch). Latest accepted combined code/workflow SHA `6244ac6665fb5c627f6c053d9f4045bebf057ed1`. Temporary workflow branch trigger removed afterward, code unchanged.
- **Do not close** F01-06/P2-05 overall: host-specific retry budgets, correct retry exhaustion, changing networks, and end-to-end job-level recovery are not all accepted. PR #3's memory benchmark remains FAIL and unmerged. No themes or visual code modified.
