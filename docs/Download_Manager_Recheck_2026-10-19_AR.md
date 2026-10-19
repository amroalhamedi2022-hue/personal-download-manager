# إعادة تدقيق مدير التحميل — GPT‑6.1 Sol

التاريخ: 19 أكتوبر 2026 — Asia/Jerusalem.

## الحكم المختصر

النسخة الحالية **تبنى وتنتج مثبت Windows EXE حقيقيًا، لكنها ليست بديل IDM مكتمل المتطلبات ولا نسخة مستقرة معتمدة بعد**. أبرز المشكلات في Retry والإيقاف والطوابير وحماية البيانات. وبعض تفاصيل نافذة التحميل المعتمدة لم تُنفذ.

يوصى بإعادة فتح P2-10 والتحقق من P2-05 وP2-07 المتأثرين. تبقى الأدلة السابقة ناجحة ضمن السيناريوهات المحددة التي اختبرتها فقط، ولا تحذف.

هذا تقرير تدقيق لقطة، وليس خطة تنفيذ بديلة. المرجع الإلزامي يبقى `docs/Download_Manager_Execution_Plan_v1.md` وتصميم `docs/design`. لا يحذف التقرير أي ميزة مخططة ولا يأذن بتنفيذ الأفكار الاختيارية تلقائيًا.

## 1. النسخة ونطاق المراجعة وحدودها

- التطبيق: `84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546` من [المستودع الخاص](https://github.com/amroalhamedi2022-hue/personal-download-manager)؛ آخر Commit توثيق. كود الحزمة الرسمية `58e0e57ad66abb36137e1b4e7a06cd5fd0b4205b`.
- الإضافة: `491c2d64589e6697c53a6bdb15ccce38a6fc860f` من المستودع الخاص المنفصل.
- قُرئ ملف الخطة كاملًا، وجُردت بنوده الـ82. قرئت مواصفات النافذة وفُحصت الصورة المعتمدة بصريًا بعد مطابقة SHA-256: `f4e8fb922db21564fa1051d9bea9efa0b753abca44c4e3b49543ee9debd171a7`.
- فُحص بالتفصيل 56 ملفًا من التطبيق و8 من الإضافة، مع جرد شجرة تحتوي 1065 ملف Kotlin. قورنت هوية النسخة المحلية بـGitHub: 1191 ملفًا متطابقًا، وقُرئت التغييرات المهمة مباشرة من النسخة البعيدة.
- هذه ليست قراءة سطرية لجميع ملفات Kotlin ولا شهادة أمان شاملة. مطابقة المتطلبات شاملة للقائمة، لكن التدقيق البرمجي عميق للمسارات عالية الخطورة وعينة الواجهة والأمان والتكامل.
- لم يتغير أي كود أو ملف بالمستودع أثناء التدقيق. لم ينشأ مستودع أو فرع جديد ولم يحذف شيء. تعديلات الخطة المقترحة أدناه لم تُطبّق على GitHub في هذه المراجعة.
- لم يُشغّل EXE تفاعليًا، ولم يُنفذ تلف/اختراق على بيانات المستخدم. النتائج A مثبتة من مراجعة المصدر والمسار المنطقي، وليس من إعادة إنتاج تشغيلية جديدة. النتائج R مخاطر تحتاج اختبارات قبل الجزم بالأثر.

## 2. ما ثبت فعليًا

أُعيدت قراءة حالة وسجل Run [39254027409](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39254027409): `completed/success` على Commit الحزمة. نجحت Core/Monitor/Shared App desktopTest وCompile في 9m08s و82 مهمة؛ ثم نجح إنشاء NSIS `DownloadManager.exe` في 2m46s و88 مهمة.

Artifact [12187193228](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39254027409/artifacts/12187193228): 171,634,190 بايت. بصمة أرشيف Artifact، وليست بصمة EXE منفردًا:

```text
ace6c07c6ad56449b36b26788729aa7e7f2e15bbb5bf5ce6af85c7f035c36d4b
```

هذا دليل بناء واختبارات محددة؛ لا يثبت اكتمال المتطلبات أو نجاح تثبيت وترقية وإزالة أو مطابقة مرئية. الحزمة الحالية نسخة تجربة، ولا توصف بإصدار مستقر نهائي.

الأساس الموجود فعليًا: HTTP Range وتقسيم واستكمال، اختبارات بايتات وSHA-256، حدود موقع/ملف/طابور، معالجة Unknown/Overflow، تصنيفات وطوابير، شريطان Liquid Progress، واجهة وTray أساسيان، وإضافة Native Messaging/HTTP مبنية.

### جرد الاختبارات

| ملف الاختبار | دوال @Test المعلنة |
| --- | ---: |
| RestartPersistenceSafetyTest.kt | 4 |
| DownloadCoreBaselineTest.kt | 40 |
| RestartPersistenceTest.kt | 1 |
| ProcessingDownloadItemStateTest.kt | 8 |
| CategoryRestartPersistenceTest.kt | 1 |

المجموع **54 دالة اختبار معلنة في المصدر**؛ ليس ذلك قراءة جديدة لـ54 نتيجة XML منفردة. نجاح مهام الاختبارات مثبت في Run؛ رقم 48 في ملخص الخطة قديم. اختبار RetryAwareTestJob لا يشغل Retry الفعلية لـHTTP/HLS، واختبار تخزين الجدولة يعطل Start/End؛ لا يعوضان اختبارات Job فعلية وSleep/Wake.

## 3. نتائج مؤكدة من المصدر

الأولوية تعني ترتيب الإصلاح، لا ادعاء حدوث خسارة فعلية. العالية تمنع اعتماد الاستقرار ضمن السيناريو المتأثر.

### A01 — استعادة Retry تتجاوز التحكم بالطوابير والتزامن

- الأولوية: عالية.
- الدليل والأثر: DownloadManager.boot يستدعي restoreInterruptedRetry لكل مهمة، ثم ينشئ HTTP/HLS نطاق تنزيل مباشرة بعد التأخير. لا يمر المسار عبر ManualDownloadQueue أو قبول الطابور، ولا يفحص أن الطابور متوقف أو خارج الجدول. حدود اتصالات Host تبقى موجودة لكنها لا تعوّض سقف عدد الملفات أو الجدول.
- اختبار المنع المطلوب: حفظ خمس Retry مستحقة، ثم إعادة التشغيل مع حد يدوي ملف واحد وطابور متوقف: ينبغي ألا يبدأ أكثر من ملف واحد وألا يبدأ الطابور المتوقف. الاختبارات الحالية لا تغطي الحالة.
- المصدر: [DownloadManager.kt:71](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/DownloadManager.kt#L71)، [HttpDownloadJob.kt:545](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/http/HttpDownloadJob.kt#L545)، [DownloadSystem.kt:63](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/util/DownloadSystem.kt#L63).

### A02 — حالة النشاط لا تضبط في مسار Retry المستعادة

- الأولوية: عالية.
- الدليل والأثر: restoreInterruptedRetry ينتقل إلى retry ثم resumeWithNewScope دون استدعاء resume الذي يضبط _isDownloadActive=true. لذلك يمكن أن يعرض status أن المهمة تعمل بينما isDownloadActive=false. حارس resume وفحوص changeConfig يستعملان العلم الثاني. الخلل في اتساق الحالة مؤكد؛ حدوث تنزيل مزدوج أو نقل وجهة أثناء النشاط أثر محتمل يحتاج اختبارًا متزامنًا.
- اختبار المنع المطلوب: استعادة Retry ثم فحص status وisDownloadActive، وإرسال Resume متزامن وتغيير مسار. المطلوب انتقال حالة ذري واحد ونطاق تنزيل واحد.
- المصدر: [DownloadJob.kt:23](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/DownloadJob.kt#L23)، [HttpDownloadJob.kt:158](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/http/HttpDownloadJob.kt#L158)، [HttpDownloadJob.kt:572](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/http/HttpDownloadJob.kt#L572).

### A03 — إيقاف الكل لا يشمل Retrying وResuming وPreparingFile

- الأولوية: عالية.
- الدليل والأثر: DownloadManager.stopAll يرشح حالة Downloading فقط. DownloadSystem.stopAnything يمسح قائمة الطابور اليدوي ثم يستدعيه؛ Retry يدوية أو مستعادة غير مسجلة ضمن activeItems لطابور قد تبقى وتستأنف بعد الضغط على إيقاف الكل. استدعاء queue.stop غير المنتظر لا يضمن إيقاف كل مهمة خارج الطابور.
- اختبار المنع المطلوب: بدء Retry يدوية، الضغط على إيقاف الكل قبل نهاية المهلة، والانتظار بعد موعدها. يجب ألا يحدث أي طلب شبكة، وأن يصفر retryAt.
- المصدر: [DownloadManager.kt:344](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/DownloadManager.kt#L344)، [DownloadSystem.kt:181](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/util/DownloadSystem.kt#L181).

### A04 — سقف Retry قد يتحول إلى حلقة غير محدودة بلا تقدم

- الأولوية: عالية.
- الدليل والأثر: يمسح clearRetryState عدد failedDownloadTries مباشرة بعد إنشاء الأجزاء وبدء العمال، قبل إثبات وصول بايت جديد. خادم يقبل فحص Metadata ويُفشل نقل الأجزاء دائمًا قد يعيد العداد للصفر في كل دورة، فلا يصل إلى الحد النهائي للمهمة.
- اختبار المنع المطلوب: خادم HEAD/Metadata ناجح وطلبات الأجزاء تفشل دون نقل؛ ضبط محاولتين والتحقق أن المهمة تتوقف بعد العدد المحدد ولا تعود إلى أول Backoff في كل دورة.
- المصدر: [HttpDownloadJob.kt:199](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/http/HttpDownloadJob.kt#L199)، [HttpDownloadJob.kt:520](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/http/HttpDownloadJob.kt#L520)، [HLSDownloadJob.kt:150](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls/HLSDownloadJob.kt#L150).

### A05 — احترام Retry-After غير شامل لكل مسارات الطلب

- الأولوية: عالية.
- الدليل والأثر: الفشل الشبكي أو استجابة HTTP في المحاولة الأولى يدخل pause مباشرة. وعلى مستوى PartDownloader يوجد تأخير ثابت ثانية واحدة للمحاولات الداخلية قبل الوصول إلى سياسة Job؛ وقد تعاد 429 قبل مهلة الخادم. RetryPolicy أيضًا يختصر مهلة خادم طويلة إلى 15 دقيقة. وجود دالة تحسب Header صحيحًا لا يثبت تطبيق المهلة على كل طلب.
- اختبار المنع المطلوب: اختبار 429 في HEAD وأول GET وطلب جزء وHeader بقيمة 3600؛ إثبات عدم إرسال طلب قبل الموعد، أو التحول إلى انتظار/تدخل واضح بدل تقصير مهلة الخادم.
- المصدر: [HttpDownloadJob.kt:509](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/http/HttpDownloadJob.kt#L509)، [PartDownloader.kt:33](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/part/PartDownloader.kt#L33)، [RetryPolicy.kt:19](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/RetryPolicy.kt#L19).

### A06 — طلبات مقاطع HLS تفقد Headers وCookies والمصادقة

- الأولوية: متوسطة.
- الدليل والأثر: HLSPartDownloader يبني HttpDownloadCredentials(url) فقط ولا يستقبل Credentials المهمة؛ فتصل بيانات الجلسة لطلب Manifest الأصلي ولا تنتقل بالضرورة إلى المقاطع. النتيجة المتوقعة فشل مصادر تحتاج Cookie/Referer/Authorization. ينبغي نقل الحقول الضرورية فقط وبسياسة تمنع تسريبها إلى Host آخر.
- اختبار المنع المطلوب: Manifest ومقاطع على خادم اختبار يتطلب Header جلسة، ثم Redirect إلى Host آخر للتأكد من حجب الأسرار غير المسموح بنقلها.
- المصدر: [HLSPartDownloader.kt:56](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls/HLSPartDownloader.kt#L56)، [HLSDownloadJob.kt:506](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls/HLSDownloadJob.kt#L506).

### A07 — استجابات فشل HLS تُحوّل إلى إلغاء عادي

- الأولوية: متوسطة.
- الدليل والأثر: عند !responseInfo.isSuccessFul يغلق HLSPartDownloader الاتصال ويرمي CancellationException. PartDownloader يصنف الإلغاء العادي كحالة لا تعاد؛ بذلك تضيع هوية 403/429/503 وRetry-After وقد يتوقف الجزء دون معالجة Job المطلوبة.
- اختبار المنع المطلوب: إرجاع 403 و429 و503 لمقطع، والتحقق من تصنيف الخطأ وإظهار السبب وإعادة المحاولة المسموح بها بدل إلغاء صامت.
- المصدر: [HLSPartDownloader.kt:67](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls/HLSPartDownloader.kt#L67)، [PartDownloader.kt:169](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/part/PartDownloader.kt#L169).

### A08 — تحديث قائمة مقاطع HLS لا يطبق القائمة المحسوبة

- الأولوية: متوسطة.
- الدليل والأثر: updateParts يبني newPartsToSave عند وجود قائمة سابقة ويخرج بلا setParts(newPartsToSave). تعمل حالة أول قائمة وحالة اختلاف المدة، لكن تحديث الروابط أو إضافة مقاطع مع تطابق المدد لا ينعكس على parts.
- اختبار المنع المطلوب: تحميل قائمة أولى ثم تحديثها بروابط مختلفة أو مقطع جديد مع مدد مشتركة متطابقة؛ يجب أن تتغير الروابط والأجزاء مع الحفاظ على تقدم المقاطع المتطابقة.
- المصدر: [HLSDownloadJob.kt:562](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls/HLSDownloadJob.kt#L562).

### A09 — فساد ملف التصنيفات قد يُستبدل بقائمة فارغة

- الأولوية: عالية.
- الدليل والأثر: TransactionalFileSaver.readObject يبتلع خطأ القراءة أو فك JSON ويعيد null؛ CategoryFileStorage يحوله إلى emptyList مع بقاء isCategoriesSet=true لأن الملف موجود. CategoryManager ينشر القائمة الفارغة ثم يحفظها دوريًا، فيمكن استبدال الدليل الأصلي التالف بدل الاحتفاظ به وإظهار استرجاع.
- اختبار المنع المطلوب: نسخة اختبار categories.json تالفة مع نسخة سليمة محفوظة؛ تشغيل المدير يجب أن يعرض خطأ/استرجاع ويحافظ على الملف التالف للمراجعة، ولا يكتب [] تلقائيًا.
- المصدر: [TransactionalFileSaver.kt:49](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/db/TransactionalFileSaver.kt#L49)، [CategoryFileStorage.kt:22](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/util/category/CategoryFileStorage.kt#L22)، [CategoryManager.kt:24](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/util/category/CategoryManager.kt#L24).

### A10 — المصادقة على API المحلية اختيارية افتراضيًا وقد تفشل بشكل مفتوح

- الأولوية: عالية.
- الدليل والأثر: القيم الافتراضية apiEnabled=true وapiAuthEnabled=false. AppRepository يحول المفتاح غير الصالح إلى null حتى إذا طلب المستخدم تفعيل المصادقة، وEndpoints.skipWhen يتجاوز المصادقة عند null. الربط 127.0.0.1 لا يعرّض الخدمة للإنترنت مباشرة، لكنه لا يصادق البرامج المحلية. لا يدعي التقرير وجود استغلال متصفح مثبت.
- اختبار المنع المطلوب: إنشاء إعداد جديد والتحقق من إيقاف API غير المطلوبة؛ عند مفتاح غير صالح مع Auth مفعّل يجب أن يتوقف الخادم/يظهر خطأ، لا أن يقبل الطلب دون مفتاح. ثم اختبار رفض طلبات محلية غير مصرح بها.
- المصدر: [DefaultAppSettings.kt:41](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/storage/appsettings/DefaultAppSettings.kt#L41)، [AppRepository.kt:55](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/repository/AppRepository.kt#L55)، [Endpoints.kt:38](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/integration/server/src/main/kotlin/com/abdownloadmanager/integration/Endpoints.kt#L38).

### A11 — حفظ أسرار داخل JSON دون طبقة حماية Windows

- الأولوية: عالية — فجوة حماية.
- الدليل والأثر: HttpDownloadItem وHLSDownloadItem حقول Serializable تضم username/password/headers؛ وتخزين القائمة يستخدم JSON مباشرة. PerHostSettingsItem يحمل كلمة مرور كذلك. لا يظهر في المسارات المدققة CredentialReference/DPAPI أو Windows Credential Manager. هذا تخزين نصي محلي، لا دليل على تسريب خارجي فعلي، لكنه لا يحقق P4-08.
- اختبار المنع المطلوب: تخزين Credentials اختبارية غير حقيقية، فحص عدم ظهورها كنص في الملفات والتصدير والLogs، وتجربة استيراد غير سري منفصل.
- المصدر: [HttpDownloadItem.kt:14](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/http/HttpDownloadItem.kt#L14)، [HLSDownloadItem.kt:12](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls/HLSDownloadItem.kt#L12)، [DownloadListFileStorage.kt:63](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/db/DownloadListFileStorage.kt#L63)، [PerHostSettingsItem.kt:6](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/util/perhostsettings/PerHostSettingsItem.kt#L6).

### A12 — نافذة التحميل لا تطابق كامل المرجع المعتمد

- الأولوية: متوسطة — مطابقة واجهة.
- الدليل والأثر: الصورة المعتمدة ومواصفاتها تتطلب رسم سرعة، إحصاءات أسفل الشريط، وأزرارًا متمركزة. ProgressDownloadPage الحالي يحتوي الشريطين السائلين وجدول الأجزاء، لكنه لا يستدعي رسم سرعة ولا صف الإحصاءات أسفل الشريط؛ RenderActions يدفع الأزرار إلى النهاية بواسطة Spacer(weight=1). غياب نسبة ثانية صحيح، والعنوان يعرض النسبة. لا توجد لقطة تشغيل Windows تثبت المطابقة.
- اختبار المنع المطلوب: اختبار لقطة للنافذة عند 100% و150% و200% وRTL، مع حالات Unknown/Retry/Paused/Error، ومقارنتها بالصورة المقبولة دون تغيير التصميم.
- المصدر: [ProgressDownloadPage.kt:86](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/singleDownloadPage/ProgressDownloadPage.kt#L86)، [ProgressDownloadPage.kt:650](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/singleDownloadPage/ProgressDownloadPage.kt#L650)، [APPROVED_FILE_DOWNLOAD_WINDOW.md](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/docs/design/APPROVED_FILE_DOWNLOAD_WINDOW.md).

### A13 — المظاهر وحزم الأيقونات لم تكتمل

- الأولوية: متوسطة — متطلب ناقص.
- الدليل والأثر: DefaultThemes.getAll يسجل سبعة مظاهر Baseline (Dark, Light, Obsidian, Deep Ocean, Twilight, Black, Light Gray)، وليس ثماني عائلات × Light/Dark. MyIcons مزود واحد ثابت؛ لا دليل على اختيار حزمتين Modern Outline/Simple وأحجام ثلاث محفوظة بالنطاق المطلوب.
- اختبار المنع المطلوب: مصفوفة 16 مظهرًا × حزمتين × ثلاثة أحجام مع حفظ الاختيارات وإعادة التشغيل وRTL/High DPI وتباين السائل.
- المصدر: [DefaultThemes.kt:220](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/ui/theme/DefaultThemes.kt#L220)، [MyIcons.kt:8](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/util/ui/icon/MyIcons.kt#L8)، [ThemeManager.kt:24](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/ui/theme/ThemeManager.kt#L24).

### A14 — إغلاق P2-10 أوسع من دليل الاختبارات والتوثيق متضارب

- الأولوية: متوسطة — جودة دليل.
- الدليل والأثر: اختبار managerBootRestoresOneRetry يستخدم RetryAwareTestJob الذي يزيد عدادًا فقط؛ لا يشغل HTTP/HLS Retry الفعلية. اختبار Queue يعطل Start/End للجدولة، فلا يثبت التنفيذ بعد Sleep/Wake أو رفض البدء خارج النافذة. ملخص الخطة يقول P2 لم تبدأ، وموضع آخر يذكر Commit قديمًا و48 اختبارًا، مع أن المصدر الحالي يعلن 54 دالة اختبار. اكتمال Build صحيح لكنه لا يثبت انعدام العيوب المذكورة.
- اختبار المنع المطلوب: إعادة P2-10 إلى جزئي/قيد التحقق إلى أن تختبر A01–A05 بمحرك حقيقي، وتحديث ملخص الخطة والSHA والأرقام دون طمس الأدلة التاريخية.
- المصدر: [RestartPersistenceSafetyTest.kt:190](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/RestartPersistenceSafetyTest.kt#L190)، [RestartPersistenceTest.kt:28](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/queue/RestartPersistenceTest.kt#L28)، [Download_Manager_Execution_Plan_v1.md:121](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/docs/Download_Manager_Execution_Plan_v1.md#L121).

## 4. مخاطر متوقعة — غير مثبتة بتجربة فشل جديدة

### R01 — احتمال إنشاء Jobs مكررة أثناء التشغيل المتزامن

distinctBy يحمي قائمة الإقلاع فقط؛ createJob لا يملك get-or-create ذريًا ولا يستخدم jobModificationLock المستخدم للحذف. اختبر نداءي resume/reset/update متزامنين لمعرف لا يملك Job.

### R02 — سباق Pause وإطلاق Retry

retry يغير الحقول قبل الحصول على retryLock، ويطلق coroutine ثم يعين retryJob لاحقًا؛ pause يلغي job الحالي دون join ثم يحفظ حالة جديدة. يلزم اختبار Pause لحظة الإنشاء ولحظة انتهاء التأخير لمنع Resume شبحية أو موعد متقادم.

### R03 — الرجوع غير الذري في حفظ Metadata

tryAtomicMove عند استثناء محدد يحذف target ثم ينسخ source. انقطاع/فشل النسخ عند ذلك قد يفقد الملف الملتزم السابق. يلزم backup/restore واختبار filesystem لا يدعم النقل الذري؛ لا يدعي التدقيق حدوثه على NTFS الطبيعي.

### R04 — فقد آخر تعديل تصنيف عند الخروج

الحفظ بـsample كل 500ms؛ exitApp يستدعي exitProcess بعد إيقاف التنزيلات دون flush تصنيفات صريح في المسارات المراجعة. اختبر تعديلًا ثم خروجًا سريعًا، وحفظًا متزامنًا إلزاميًا في Shutdown.

### R05 — هوية ملف بلا Validator كافٍ

validateRemoteFileIdentity يسمح بتساوي الحجم عند غياب ETag ولا يقارن Last-Modified المخزنة؛ نفس الحجم لا يثبت نفس المحتوى. يلزم سياسة محافظة وIf-Range/تحقق أقوى وشرح درجة اليقين، خصوصًا عند Refresh URL.

### R06 — أداء السائل والتحجيم وRTL

rememberInfiniteTransition ينشأ حتى في السكون ولا يتوقف صراحة عند !active. النافذة غير قابلة للتحجيم ويعتمد الشريط على اتجاه رسم من اليسار. هذه مخاطر أداء/إتاحة تحتاج قياسًا ولقطات، لا أحكامًا بوجود بطء فعلي.

## 5. مطابقة جميع بنود الخطة

الخطة تحتوي 82 بندًا؛ 17 تحمل [x] تاريخيًا. الأرقام ليست نسبة إنجاز، فالبنود ليست متساوية الحجم وبعض الإغلاقات تحتاج إعادة فتح. كل بند محفوظ في النطاق، بما فيه Torrent وAndroid.

| البند | حالته في الخطة | حكم التدقيق | الفجوة أو حدود الدليل |
| --- | --- | --- | --- |
| P0-01 | مؤشر مكتمل | مكتمل موثق | موافقة سابقة مسجلة. |
| P0-02 | مؤشر مكتمل | مكتمل موثق | Windows 11 وEdge والعربية/الإنجليزية. |
| P0-03 | مؤشر مكتمل | اعتماد تصميم فقط | المرجع مقبول؛ اكتمال التطبيق المرئي ليس جزءًا من هذا القبول. |
| P0-04 | مؤشر مكتمل | مكتمل موثق | الاسم المؤقت والنطاق محفوظان؛ الاسم النهائي مؤجل. |
| P0-05 | مؤشر مكتمل | مكتمل موثق | مواقع Plugins مؤجلة ولا تمنع بناء الإطار. |
| P1-01 | مؤشر مكتمل | مكتمل موثق | Baseline وSHA مسجلان. |
| P1-02 | مؤشر مكتمل | مكتمل موثق | مستودع قائم وفروع استرجاع؛ لم تُمس في التدقيق. |
| P1-03 | مؤشر مكتمل | منجز كأساس | الترخيص وtoolchain موجودان؛ ليس تدقيق كل اعتماد. |
| P1-04 | مؤشر مكتمل | مكتمل في نطاقه | Build أصلي موثق؛ منفصل عن قبول Windows. |
| P1-05 | مؤشر مكتمل | جرد موجود يحتاج تحديثًا | هذا التدقيق يبيّن فروقًا عن حالات الإنجاز السابقة. |
| P1-06 | مؤشر مكتمل | منفذ ومختبر آليًا | اختبارات HTTP محلية موجودة؛ فجوات Job/HLS/UI باقية. |
| P1-07 | مفتوح | جزئي | Strict checksums موجودة؛ provenance/PGP/SBOM أعمق غير مكتمل. |
| P1-08 | مؤشر مكتمل | منفذ كأساس | هوية ومجلد بيانات منفصلان؛ اختبار التعايش والتثبيت مطلوب. |
| P2-01 | مفتوح | جزئي | تقسيم ثابت ومتعدد مختبران؛ Dynamic end-to-end غير مثبت. |
| P2-02 | مؤشر مكتمل | مختبر ضمن حالات محددة | Range وETag والحجم لها أدلة؛ لا تعميم لهوية محتوى بلا Validator. |
| P2-03 | مفتوح | جزئي | Flush/Persist مختبران؛ قتل عملية واستعادة فعلية غير مكتملين. |
| P2-04 | مفتوح | جزئي مع عيب | Pause/Resume لجزء وسياسة حذف؛ إيقاف الكل يحتاج A03. |
| P2-05 | مفتوح | جزئي مع عيوب | Backoff موجود؛ A04/A05 تمنع إعلان سياسة شاملة. |
| P2-06 | مفتوح | جزئي | أعطال HTTP عابرة مختبرة؛ تبدل شبكة/نفاد محاولات واختبارات HLS ناقصة. |
| P2-07 | مؤشر مكتمل | مختبر آليًا مع تراجع محتمل | Host/Queue/Part أدلتها موجودة؛ A01 يعيد فتح التحقق بعد Restart. |
| P2-08 | مؤشر مكتمل | مختبر في بيئة محددة | مساحة وأسماء عربية ومسارات وSparse >5GB؛ لا يعمم على كل filesystem. |
| P2-09 | مؤشر مكتمل | مختبر آليًا | Unknown/ETA/Overflow لها اختبارات؛ RTL بصريًا غير مثبت. |
| P2-10 | مؤشر مكتمل | يوصى بإعادة فتحه | حفظ وتصحيح عضويات موجود؛ Retry الفعلية والجدولة تحتاج A01–A05/A14. |
| P3-01 | مفتوح | جزئي من Baseline | واجهة وجداول وقوائم موجودة؛ مطابقة IDM النهائية غير منجزة. |
| P3-02 | مفتوح | جزئي | أوامر أساسية موجودة؛ تجربة كل مسار وإيقاف الكل غير مقبولين نهائيًا. |
| P3-03 | مفتوح | جزئي | السائل موجود؛ رسم السرعة وتوزيع المرجع ناقصان A12. |
| P3-04 | مفتوح | جزئي | Table/فرز/بحث/تحديد موجودة؛ نافذة الأعمدة المطلوبة كاملة غير مثبتة. |
| P3-05 | مفتوح | جزئي/متطلبات ناقصة | سبعة مظاهر بدل 16؛ حزمتا الأيقونات وثلاثة أحجام غير منجزة A13. |
| P3-06 | مفتوح | جزئي | إعدادات موجودة؛ وضعان وبحث وReset لكل قسم بالنطاق الكامل غير مثبت. |
| P3-07 | مفتوح | غير منجز بالنطاق المطلوب | نسخ احتياطي واستيراد/تصدير غير سري مع حماية أسرار بحاجة تنفيذ. |
| P3-08 | مفتوح | جزئي من Baseline | Tray وإشعارات موجودة؛ مطابقة التحكم والسلوك النهائي تحتاج اختبار. |
| P4-01 | مفتوح | جزئي | إضافة خاصة مبنية؛ Edge/Windows حي غير مثبت. |
| P4-02 | مفتوح | جزئي مع فجوة أمان | Native Messaging موجود؛ API اختياري بلا Auth افتراضيًا A10. |
| P4-03 | مفتوح | جزئي | التقاط وإرسال وروابط/وسائط أساسية؛ كل المسارات والزر العائم تحتاج قبولًا. |
| P4-04 | مفتوح | جزئي | استثناءات ومراقبة Clipboard كأساس؛ التفاعل النهائي غير مختبر. |
| P4-05 | مفتوح | جزئي مع فجوة حماية | نقل Headers/Cookies موجود؛ أقل صلاحيات وسياسة أسرار بحاجة تدقيق. |
| P4-06 | مفتوح | جزئي غير مثبت حيًا | Fallback في الإضافة موجود؛ لا اختبار برنامج مغلق/رفض/التقاط مرة واحدة. |
| P4-07 | مفتوح | غير منجز بالنطاق المطلوب | تحرير URL يدوي جزئي لا يحقق المسارات الثلاثة والتحقق الآمن. |
| P4-08 | مفتوح | غير مكتمل | أسرار Serializable/JSON A11؛ لا قبول حماية Windows. |
| P5-01 | مفتوح | جزئي كأساس إدخال | قوائم نصية واستخراج أولي؛ ليس Link Collector بحدود زحف كاملة. |
| P5-02 | مفتوح | غير متحقق بالنطاق المطلوب | إزالة تكرار آمنة للQuery والموارد تحتاج اختبارًا مخصصًا. |
| P5-03 | مفتوح | غير منجز كاملًا | نوافذ إضافة متعددة ليست شاشة حزم ومصادر وحالات متكاملة. |
| P5-04 | مفتوح | غير منفذ | نماذج حزم/أجزاء متوقعة وترتيب رقمي موثق مطلوبة. |
| P5-05 | مفتوح | غير منفذ | حالات مصدر/حساب/Captcha ودرجة يقين غير موجودة كمنظومة. |
| P5-06 | مفتوح | غير منفذ بالنطاق المطلوب | Silent Add الموجود لا يعادل سياسة ثقة لكل مصدر. |
| P5-07 | مفتوح | غير منفذ | إطار Crawler/Host Plugins مرخص ومعزول مطلـوب. |
| P5-08 | مفتوح | غير منفذ | تثبيت/إصدارات/تحديث/رجوع Plugins مطلوب. |
| P5-09 | مفتوح | غير منفذ | لا قبول لتنفيذ Plugins عامة دون عزل. |
| P5-10 | مفتوح | غير منفذ | موقع اختبار لحزمة متعددة الأجزاء مطلوب. |
| P6-01 | مفتوح | مؤجل بقرار | المواقع لم يحددها المستخدم؛ لا يوقف الإطار. |
| P6-02 | مفتوح | مؤجل/غير منفذ | مصفوفة دعم لكل موقع بعد اختيار العينة. |
| P6-03 | مفتوح | غير منفذ | مدير Captcha وحالاته مطلوب. |
| P6-04 | مفتوح | غير منفذ بالنطاق المطلوب | Fallback عام لا يثبت جلسة Captcha. |
| P6-05 | مفتوح | غير منفذ | سياسة مرايا متحققة الهوية مطلوبة. |
| P6-06 | مفتوح | غير منفذ | كشف نقص الحزمة بالدليل مطلوب. |
| P6-07 | مفتوح | غير منفذ | اختيار مكتبة أرشفة وترخيص ودعم حقيقي. |
| P6-08 | مفتوح | غير منفذ | فك اختياري آمن مع كلمات مرور وعدم حذف/تشغيل تلقائي. |
| P6-09 | مفتوح | غير منفذ | اختبارات Zip Slip/Symlink/Zip bomb قبل قبول فك الضغط. |
| P7-01 | مفتوح | جزئي جدًا | HLS أساسي مع A06–A08؛ yt-dlp/YouTube وDASH/الجودة/الترجمة غير منجزة. |
| P7-02 | مفتوح | جزئي كأساس تجميع مقاطع | ليس دمج صوت/فيديو متحققًا بأداة وإدارة فشل كاملة. |
| P7-03 | مفتوح | غير معتمد/مؤجل | Live لم يثبت؛ لا يستنتج من HLS المسجل. |
| P7-04 | مفتوح | غير منفذ | Site Grabber بحدود ومعاينة مطلوب. |
| P7-05 | مفتوح | غير منفذ | معاينة ZIP جزئية مطلوبة. |
| P7-06 | مفتوح | جزئي كأساس جدولة | الجداول موجودة؛ حصص ومزامنة وسياسة مهام فائتة غير مكتملة. |
| P7-07 | مفتوح | جزئي من Baseline | إجراءات طاقة موجودة؛ كل الحزمة وفحص فيروسات واختبارات القبول ناقصة. |
| P7-08 | مفتوح | موجود غير معتمد كاملًا | Proxy/PAC/SOCKS وUser-Agent كأساس؛ مصفوفة تشغيل فعلية ناقصة. |
| P7-09 | مفتوح | بند قرار محفوظ | FTP/MMS/قديم لا يعتبر مدعومًا بلا تقييم؛ لم يحذف. |
| P7-10 | مفتوح | جزئي | CLI/API/Drag-and-drop كأساس؛ API الافتراضية تخالف الحماية المقصودة. |
| P7-11 | مفتوح | مؤجل وغير منفذ | Torrent وحدة مستقلة محفوظة بالخطة؛ لا تخلط مع HTTP. |
| P8-01 | مفتوح | جزئي مثبت بالبناء | EXE NSIS أُنتج؛ reproducibility/توقيع/Portable ليست مثبتة. |
| P8-02 | مفتوح | غير مختبر | تثبيت/ترقية/إزالة/تعايش مع AB تحتاج Windows فعلي. |
| P8-03 | مفتوح | غير مكتمل | فرع Git يحفظ المصدر لا بيانات المستخدم؛ ترحيل/نسخة بيانات ورجوع مطلوب. |
| P8-04 | مفتوح | غير مختبر حيًا | نجاح runner لا يثبت UI/Edge على جهاز المستخدم. |
| P8-05 | مفتوح | غير منفذ | لا قياسات مقارنة موثقة لسرعة/CPU/RAM/قرص. |
| P8-06 | مفتوح | غير منفذ | اختبارات 1000/10000 رابط والحزم مطلوبة. |
| P8-07 | مفتوح | جزئي وغير معتمد | ترخيص وStrict verification أساس؛ A10/A11 ومراجعة أعمق باقية. |
| P8-08 | مفتوح | غير مكتمل | التقرير الحالي تدقيق وليس شهادة قبول نهائي. |
| P9-01 | مفتوح | مؤجل | موافقة منفصلة بعد استقرار Windows. |
| P9-02 | مفتوح | مؤجل | وجود KMP/Android مصدرًا ليس اختبار Android. |
| P9-03 | مفتوح | مؤجل | UI وصلاحيات وخلفية Android لاحقًا. |
| P9-04 | مفتوح | مؤجل | متصفح ووسائط وPlugins المنصة تحتاج تقييمًا مستقلًا. |
| P9-05 | مفتوح | مؤجل | مصفوفة فروق واختبارات أجهزة مطلوبة. |

## 6. اختبارات القبول المطلوبة قبل وصف النسخة مستقرة

1. HTTP/HLS Job فعليان: استعادة Retry، Stop All، حدود الملفات والطوابير، Pause متزامن، نفاد المحاولات و429/Retry-After. لا يكتفى بدوال الحساب وJobs وهمية.
2. قتل Process اختبار مستقل أثناء تنزيل كبير، Restart ومقارنة بايتات وSHA-256، مع Metadata سليمة وتالفة و.tmp منقطع. لا قطع كهرباء أو ملء قرص المستخدم.
3. T18: نوم/استيقاظ، موعد فائت، طابور متوقف، وعدم إجراء إكمال مبكر. حفظ إعدادات مواعيد مع تعطيلها ليس اختبار تشغيل الجدولة.
4. تثبيت NSIS وترقية وإزالة على Windows ببيانات مصطنعة، والتحقق من تعايش AB الأصلي وحفظ تنزيلات المستخدم. سكربت NSIS يجعل إزالة بيانات المستخدم اختيارية؛ لا يغني ذلك عن التجربة.
5. Edge: إضافة خاصة، App مغلق أو رافض، التقاط مرة واحدة، Cookies/Headers ضرورية فقط، وFallback لا يضيع الطلب. Windows Registry الحالي يكتب Chrome/Firefox؛ غياب مفتاح Edge الصريح ليس وحده دليل فشل دون تجربة fallback.
6. مطابقة المرجع: الرسم البياني وصف الإحصاءات والأزرار المتمركزة والاتصالات والجدول؛ نسبة في العنوان فقط؛ Light/Dark و100/150/200% وRTL ولوحة مفاتيح. لا استبدال لتصميم Liquid Progress.
7. حماية الأسرار/API: مفاتيح سليمة/غير سليمة، رفض غير المصرح، Logs منقحة، ونسخة بيانات قبل ترحيل التخزين. فرع Git ليس نسخة بيانات.
8. حمل 1000 و10000 مهمة وقياس CPU/RAM/قرص والاستجابة؛ لا مقارنة سرعة مع IDM دون نفس الخادم والملف والقيود والبيئة.

## 7. مطالب أصلية ما زالت ناقصة — وليست إضافات جديدة

- المظاهر الـ16، حزمتا الأيقونات وأحجامها، مطابقة الواجهة ونافذة الأعمدة.
- Refresh URL بالمسارات الثلاثة والتحقق من هوية الملف.
- Link Collector وحزم الألعاب وعدد الأجزاء المتوقع والمرايا وإطار Plugins الآمن.
- مدير Captcha ومواقع فعلية موثقة، وفك أرشيفات متعدد الأجزاء بصورة آمنة.
- YouTube/yt-dlp وDASH والجودة والصوت والترجمة والدمج المتحقق.
- Torrent مستقل، Site Grabber، معاينة ZIP، الحصص والمزامنة المتقدمة.
- تحديثات آمنة منفصلة للتطبيق والإضافة والمحركات؛ DummyUpdateChecker متعمد حاليًا وليس Updater كاملًا.
- قبول Windows التفاعلي والأداء؛ Android لاحقًا بالموافقة المنفصلة.

لم يحذف أي من هذه المطالب، وتأجيل اختيار المواقع لا يبرر إيقاف بناء الإطار العام.

## 8. إضافات اختيارية مقترحة — بعد الموثوقية وليس بدل المطلوب

- **لماذا التنزيل بطيء؟** لوحة تفسر القيد الفعال: حد المستخدم/الطابور/الموقع، انتظار Retry، قرص أو خادم؛ دون وعد بتشخيص يقيني من بيانات ناقصة.
- **وضع اتصال مكلف أو هوتسبوت:** تنبيه قبل تنزيل كبير وسياسة قابلة للتجاوز بموافقة المستخدم. Windows يوفر Network Cost؛ ربطه بـJVM يحتاج تقييمًا. [مرجع Microsoft](https://learn.microsoft.com/en-us/uwp/api/windows.networking.connectivity.connectioncost).
- **مؤشر ثقة الاستكمال:** قوي بـValidator/بصمة أو محدود بالدليل المتاح، مع سبب مفهوم لرفض Refresh URL.
- **فحص صحة الإعدادات:** فحص محلي للوجهة والمساحة وربط الإضافة وتعارض الحدود، وتقرير منقح من الأسرار.

لا ينفذ أي اقتراح تلقائيًا ولا يؤخر إصلاحات سلامة البيانات.

## 9. ترتيب المتابعة داخل الخطة الأصلية

| الأولوية | العمل | دليل الخروج |
| --- | --- | --- |
| 1 | إعادة فتح P2-10 ومعالجة A01–A05 وسباقات التشغيل | Job حقيقي، صفر Resume بعد Stop، حدود فعلية بعد Restart |
| 2 | البيانات/API وA09–A11 | تلف آمن، Auth fail-closed، حماية أسرار وBackup بيانات |
| 3 | HLS وA06–A08 | مقاطع بمصادقة، أخطاء مصنفة، تحديث Playlist وناتج صالح |
| 4 | إغلاق بقية P2 وT03/T18 | Crash/شبكة/Dynamic وجدولة مثبتة |
| 5 | P3 مع المرجع: رسم وإحصاءات ثم ثيمات وأيقونات وأعمدة | قبول Windows ولقطات ومراجعة المستخدم |
| 6 | P4 ثم P5–P7 | مصفوفة دعم لكل ميزة وموقع دون حذف المؤجل |
| 7 | P8 واعتماد EXE؛ P9 لاحقًا | تثبيت وترقية وإزالة وأداء وأمان وقبول نهائي |

## 10. تصحيح دقيق للتقارير السابقة

- إنتاج EXE ونجاح Build صحيحان؛ اكتمال كل التطبيق غير صحيح.
- إغلاق P2-10 بالكامل كان سابقًا لأوانه بالنظر إلى مسارات العطل المكتشفة. يبقى E025 دليل Build واختبارات محددة، ويوصى بإعادة فتح المهمة لا محو الدليل.
- منع التكرار المثبت يخص اختيار معرف واحد في قائمة الإقلاع؛ لا يثبت انعدام كل سباق تشغيل أو إطلاق Retry مكرر تحت التعطل.
- Liquid Progress موجود؛ مطابقة كامل النافذة للصورة غير متحققة، وهناك فروق مباشرة من الكود.
- Git backup يحمي المصدر، لا بيانات المستخدم أو تصنيفاته وأسراره.

## 11. المراجع

- [الخطة الإلزامية](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/docs/Download_Manager_Execution_Plan_v1.md).
- [مواصفات النافذة](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/docs/design/APPROVED_FILE_DOWNLOAD_WINDOW.md).
- [الصورة الأصلية](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/docs/design/file-download-window-approved-2026-10-17.webp).
- [سجل Windows](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39254027409).
- [المستودع الخاص للإضافة](https://github.com/amroalhamedi2022-hue/personal-download-manager-browser-integration/tree/491c2d64589e6697c53a6bdb15ccce38a6fc860f).
- [RFC 9110 — Retry-After](https://www.rfc-editor.org/rfc/rfc9110.html#field.retry-after): مهلة قبل الطلب اللاحق؛ اختبار Parser ليس إثبات التزام كل مسار.
- [GPT‑6.1 Sol — OpenAI](https://developers.openai.com/api/docs/models/gpt-6.1-sol). استُخدم GPT‑6.1 Sol للتدقيق الحالي؛ لم يجر تعديل اسم نموذج داخل تطبيق مدير التحميل نفسه.

### ملفات التطبيق المقروءة أو المفحوصة تفصيليًا

- [private-windows-validation.yml](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/.github/workflows/private-windows-validation.yml)
- [nsis-script-template.nsi](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/desktop/app/resources/installer/nsis-script-template.nsi)
- [Di.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/di/Di.kt)
- [NativeMessagingManifestApplier.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/nativemessaging/NativeMessagingManifestApplier.kt)
- [NativeMessagingHost.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/nativemessaging/host/NativeMessagingHost.kt)
- [NativeMessagingRouter.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/nativemessaging/host/NativeMessagingRouter.kt)
- [HomeComponent.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/home/HomeComponent.kt)
- [HomePage.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/home/HomePage.kt)
- [ProgressDownloadPage.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/singleDownloadPage/ProgressDownloadPage.kt)
- [ShowDownloadDialogs.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/singleDownloadPage/ShowDownloadDialogs.kt)
- [AppRepository.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/repository/AppRepository.kt)
- [Tray.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/ui/widget/Tray.kt)
- [APPROVED_FILE_DOWNLOAD_WINDOW.md](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/docs/design/APPROVED_FILE_DOWNLOAD_WINDOW.md)
- [DownloadManager.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/DownloadManager.kt)
- [DownloadListFileStorage.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/db/DownloadListFileStorage.kt)
- [QueueFileStorage.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/db/QueueFileStorage.kt)
- [TransactionalFileSaver.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/db/TransactionalFileSaver.kt)
- [SegmentedDownloadDestination.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/destination/SegmentedDownloadDestination.kt)
- [DownloadJob.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/DownloadJob.kt)
- [DownloadJobStatus.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/DownloadJobStatus.kt)
- [IDownloadItem.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/IDownloadItem.kt)
- [RetryPolicy.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/RetryPolicy.kt)
- [HLSDownloadItem.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls/HLSDownloadItem.kt)
- [HLSDownloadJob.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls/HLSDownloadJob.kt)
- [HLSPartDownloader.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/hls/HLSPartDownloader.kt)
- [HttpDownloadItem.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/http/HttpDownloadItem.kt)
- [HttpDownloadJob.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/http/HttpDownloadJob.kt)
- [HttpPartDownloader.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/part/HttpPartDownloader.kt)
- [PartDownloader.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/part/PartDownloader.kt)
- [DownloadQueue.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/queue/DownloadQueue.kt)
- [ManualDownloadQueue.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/queue/ManualDownloadQueue.kt)
- [QueueManager.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/commonMain/kotlin/ir/amirab/downloader/queue/QueueManager.kt)
- [RestartPersistenceSafetyTest.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/RestartPersistenceSafetyTest.kt)
- [DownloadCoreBaselineTest.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/part/DownloadCoreBaselineTest.kt)
- [RestartPersistenceTest.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/queue/RestartPersistenceTest.kt)
- [ProcessingDownloadItemStateTest.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/downloader/monitor/src/commonTest/kotlin/ir/amirab/downloader/monitor/ProcessingDownloadItemStateTest.kt)
- [Endpoints.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/integration/server/src/main/kotlin/com/abdownloadmanager/integration/Endpoints.kt)
- [Integration.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/integration/server/src/main/kotlin/com/abdownloadmanager/integration/Integration.kt)
- [IntegrationSettings.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/integration/server/src/main/kotlin/com/abdownloadmanager/integration/IntegrationSettings.kt)
- [KtorServer.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/integration/server/src/main/kotlin/com/abdownloadmanager/integration/KtorServer.kt)
- [MyServer.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/integration/server/src/main/kotlin/com/abdownloadmanager/integration/MyServer.kt)
- [build.gradle.kts](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/build.gradle.kts)
- [BaseHomeComponent.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/pages/home/BaseHomeComponent.kt)
- [BaseAppRepository.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/repository/BaseAppRepository.kt)
- [DefaultAppSettings.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/storage/appsettings/DefaultAppSettings.kt)
- [DefaultThemes.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/ui/theme/DefaultThemes.kt)
- [ThemeManager.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/ui/theme/ThemeManager.kt)
- [DownloadSystem.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/util/DownloadSystem.kt)
- [CategoryFileStorage.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/util/category/CategoryFileStorage.kt)
- [CategoryManager.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/util/category/CategoryManager.kt)
- [PerHostSettingsItem.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/util/perhostsettings/PerHostSettingsItem.kt)
- [MyIcons.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/util/ui/icon/MyIcons.kt)
- [DefaultAppSettings.desktop.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/desktopMain/kotlin/com/abdownloadmanager/shared/storage/appsettings/DefaultAppSettings.desktop.kt)
- [PlatformThemeDefinitions.desktop.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/desktopMain/kotlin/com/abdownloadmanager/shared/ui/theme/PlatformThemeDefinitions.desktop.kt)
- [CategoryRestartPersistenceTest.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/app/src/desktopTest/kotlin/com/abdownloadmanager/shared/util/category/CategoryRestartPersistenceTest.kt)
- [FileExtensions.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/84d0719bcb0c4ee57c5e3aba8260ab4f2b15b546/shared/utils/src/commonMain/kotlin/ir/amirab/util/FileExtensions.kt)

### ملفات الإضافة المقروءة

- [src/backend/Backend.ts](https://github.com/amroalhamedi2022-hue/personal-download-manager-browser-integration/blob/491c2d64589e6697c53a6bdb15ccce38a6fc860f/src/backend/Backend.ts)
- [src/backend/NativeMessagingApi.ts](https://github.com/amroalhamedi2022-hue/personal-download-manager-browser-integration/blob/491c2d64589e6697c53a6bdb15ccce38a6fc860f/src/backend/NativeMessagingApi.ts)
- [src/background/actions.ts](https://github.com/amroalhamedi2022-hue/personal-download-manager-browser-integration/blob/491c2d64589e6697c53a6bdb15ccce38a6fc860f/src/background/actions.ts)
- [src/background/background.ts](https://github.com/amroalhamedi2022-hue/personal-download-manager-browser-integration/blob/491c2d64589e6697c53a6bdb15ccce38a6fc860f/src/background/background.ts)
- [src/linkgrabber/DownloadLinkInterceptor.ts](https://github.com/amroalhamedi2022-hue/personal-download-manager-browser-integration/blob/491c2d64589e6697c53a6bdb15ccce38a6fc860f/src/linkgrabber/DownloadLinkInterceptor.ts)
- [src/linkgrabber/Manifest3DownloadLinkInterceptor.ts](https://github.com/amroalhamedi2022-hue/personal-download-manager-browser-integration/blob/491c2d64589e6697c53a6bdb15ccce38a6fc860f/src/linkgrabber/Manifest3DownloadLinkInterceptor.ts)
- [src/manifest/manifest.chrome.ts](https://github.com/amroalhamedi2022-hue/personal-download-manager-browser-integration/blob/491c2d64589e6697c53a6bdb15ccce38a6fc860f/src/manifest/manifest.chrome.ts)
- [src/manifest/shared.ts](https://github.com/amroalhamedi2022-hue/personal-download-manager-browser-integration/blob/491c2d64589e6697c53a6bdb15ccce38a6fc860f/src/manifest/shared.ts)

