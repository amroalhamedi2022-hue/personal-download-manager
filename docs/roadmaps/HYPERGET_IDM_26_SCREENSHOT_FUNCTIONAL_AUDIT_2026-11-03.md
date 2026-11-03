# HyperGet — التدقيق الوظيفي المصوّر IDM vs HyperGet (مرجع 2026-10-21)

**تاريخ الاستلام/الفحص:** 2026-11-03. **الأصل:** `archiveNew.zip` الذي أعاد المستخدم رفعه. **SHA-256:** `f83ae37695224ddaa6f0095b0d2c23f592ac27b4c7a35650481a8fd1f817e8bd`. **المجموع:** 26 صورة PNG، لا 27؛ 7 واجهة IDM + 8 إعدادات IDM + 6 واجهة HyperGet قديمة + 5 إعدادات HyperGet قديمة. ليس ضمن الحزمة ملف `SCREENSHOT_INDEX_AR.md`، ولا توجد صورة سابعة وعشرون؛ لا تُخترَع. اسم P01–P26 هنا **فهرس جديد حسب ترتيب الملفات داخل ZIP** وليس الأرقام الأصلية المعتمدة سابقًا.

> **حماية الخصوصية:** ZIP الخام والصور محفوظة في مكتبة ChatGPT الخاصة فقط، ولا تُنسخ صور تحتوي بيانات محلية أو أسرار إلى GitHub. خاصةً P26 فيها قيمة API key ظاهرة. إذا كان المفتاح ما زال صالحًا، يجب تدويره وإخفاؤه قبل مشاركة لقطات أخرى. لا تُنسخ القيمة نصيًا هنا. وتوجد في الصور مسارات/بيانات شخصية محتملة.

> **الدليل والحدود:** الصور مأخوذة في 21-10-2026، وتُثبت ما ظهر *تاريخيًا* على الشاشة فقط؛ لا تُثبت وظائف HyperGet الحالية ولا اختبارات Windows الحالية. كل اختلاف أدناه **مرشح فحص** وليس حكمًا بأن الكود الحالي ينقصه. تاريخ اعتماد الصورة لا يساوي قبول End-to-End للوظيفة.

## 1. فهرس كل صورة وربطها بمهام المشروع

| # | المجموعة | المرجع الأصلي داخل ZIP | ما يظهر في الصورة | IDs في الخطة | فحص الوظيفة/القبول المطلوب |
| --- | --- | --- | --- | --- | --- |
| P01 | IDM UI | `Screenshot 2026-10-21 103059.png` (663×148) | إضافة رابط جديد؛ عنوان الرابط مع مصادقة موقع اختيارية، اسم المستخدم وكلمة المرور | F03-08, F02-04, F05-07 | تحقق من نموذج إدخال URL/FTP وإجراءات فحص الرابط والأسرار؛ لا تُظهر كلمة السر في السجلات |
| P02 | IDM UI | `Screenshot 2026-10-21 103205.png` (1917×1020) | النافذة الرئيسية: شجرة التصنيفات، الجدول، أشرطة الأدوات، حالة صفوف التنزيلات | F03-01, F03-05, F03-06, F03-07 | جرد أعمدة الجدول، أوامر الأشرطة، الفرز، الحفظ بعد إعادة التشغيل |
| P03 | IDM UI | `Screenshot 2026-10-21 103747.png` (331×297) | قائمة «المهام»: إضافة رابط، تحميل جماعي، من الحافظة، ماسك الموقع، Drop Target، استيراد/تصدير، خروج | F03-02, F03-08, F07-01, F08-08, F09-02, F09-03, F09-04 | هذه هي أكبر فجوات قائمة File في لقطة HyperGet القديمة؛ اختبر كل أمر مع مدخلات سليمة وفاشلة |
| P04 | IDM UI | `Screenshot 2026-10-21 103756.png` (325×173) | قائمة «ملف»: إيقاف، إزالة، تنزيل الآن، إعادة تحميل مع حالات تمكين حسب الصف | F03-02, F01-03, F01-04 | اختبار stop/remove/delete-record-vs-file/restart ومطابقة حالة enabled/disabled |
| P05 | IDM UI | `Screenshot 2026-10-21 103800.png` (344×383) | قائمة «التحميل»: إيقاف الكل، حذف المكتملة، بحث Ctrl+F، البحث التالي F3، الجدولة، بدء/إيقاف الطابور، محدد السرعة، خيارات | F03-02, F03-04, F06-02, F06-03, F01-09 | أزرار القوائم والاختصارات لها نفس handler/الحالة؛ F3 وSpeed limiter والجدولة اختبارات مستقلة |
| P06 | IDM UI | `Screenshot 2026-10-21 103803.png` (484×295) | قائمة «عرض»: إخفاء الفئات، ترتيب الملفات، شريط الأدوات، Drop Target، تخصيص قائمة الرابط، الثيم الداكن، الخطوط واللغة | F03-02, F03-05, F03-06, F03-13, F09-03 | اختبار hide/show, sorting, drop-target actions, language؛ لا تغييرات ألوان ضمن هذه المحادثة |
| P07 | IDM UI | `Screenshot 2026-10-21 103821.png` (730×301) | قائمة View > Toolbar: التخصيص، حجم أزرار، الأزرار القديمة/الجديدة، اختيار نمط icons | F03-01, F03-02, F03-13 | حصر فعل تخصيص وترتيب/إظهار الأزرار وحفظها فقط؛ أساليب الرسوم مؤجلة لمحادثة الثيمات |
| P08 | IDM Settings | `Screenshot 2026-10-21 103843.png` (560×662) | عام: تمكين تكامل المتصفح، التشغيل مع Windows، مراقبة الحافظة، قائمة متصفحات، الاستثناءات ولوائح المتصفح وhotkeys | F04-01, F05-02, F05-03, F03-12 | قائمة المتصفحات المعتمدة Chrome/Edge/Firefox؛ fallback عند غياب الامتداد/التطبيق، عدم تجميع بيانات بلا إذن |
| P09 | IDM Settings | `Screenshot 2026-10-21 103851.png` (588×657) | أنواع الملفات: ملحقات الالتقاط، استثناءات المواقع، عدم التقاط العناوين، استثناءات إضافية | F04-02, F05-03, F05-04 | تأكد من قواعد امتداد/Host وسلامة wildcard وتوريثها، وعدم التقاط ما استُثني |
| P10 | IDM Settings | `Screenshot 2026-10-21 103908.png` (572×655) | حفظ إلى: تصنيف→مجلد، أنواع الملفات المخصصة لكل تصنيف، مجلد افتراضي ومجلد مؤقت | F04-03, F06-04, F01-08, F01-10 | تحقق من الفصل بين temporary/final، تغيير تصنيف لاحقًا ومراجعة file-publish/no overwrite |
| P11 | IDM Settings | `Screenshot 2026-10-21 103911.png` (571×656) | التنزيلات: بدء تلقائي، رسائل بدء/اكتمال، نافذة تقدم، duplicate URL policy، User-Agent، فحص الفيروسات | F04-04, F03-08, F03-10, F03-11, F06-05 | لكل خيار اختبار تشغيل/إيقاف، restart/persist، وعدم اعتبار خيارات موجودة بمجرد ظهورها |
| P12 | IDM Settings | `Screenshot 2026-10-21 103914.png` (573×669) | الاتصال: حد الاتصالات الافتراضي، قواعد لكل موقع، حد حركة البيانات ونافذة التنبيه | F04-05, F01-09, F10-01 | الحد global/host/file، تغيير حي أثناء النقل، حفظ restart، وقياس 1/4/16 benchmark |
| P13 | IDM Settings | `Screenshot 2026-10-21 103917.png` (582×653) | Proxy/Socks: بدون وكيل/النظام/PAC/يدوي، عناوين وبروتوكولات، كلمات مرور وFTP passive | F04-06, F02-04, F05-07, F10-05 | التحقق من credential vault وعدم تسريب Authorization وHTTPS verify الافتراضي |
| P14 | IDM Settings | `Screenshot 2026-10-21 103929.png` (578×662) | VPN/اتصال هاتفي: اتصال Windows واختيار نوع إعادة الاتصال، محاولات تأخير | F04-07, F02-07 | مطلوب قرار دعم legacy/dial-up مستقل، دون ادعاء تنفيذه أو تعطيل أساسي |
| P15 | IDM Settings | `Screenshot 2026-10-21 103936.png` (582×653) | أصوات: أحداث نجاح/فشل/بدء/اكتمال الطابور، اختيار ملف صوت واختباره | F04-08, F03-12, F06-05 | مقارنة الصوت لكل حدث بخيارات HyperGet الحالية؛ كتم/تشغيل وصلاحية الملف وحفظ restart |
| P16 | HyperGet UI | `Screenshot 2026-10-21 102931.png` (515×201) | نموذج إضافة رابط تاريخي: عنوان URL ونوع تلقائي وOK/Cancel | F03-08, F02-04 | مقارنة P01؛ هذه لقطة 2026-10-21 وقديمة، لا تُستخدم كحكم أن FTP غير منفذ الآن |
| P17 | HyperGet UI | `Screenshot 2026-10-21 103113.png` (1919×1013) | النافذة الرئيسية التاريخية: قائمة فارغة، categories/queues، toolbar محدود، جدول | F03-01, F03-05, F03-06 | مقارنة P02؛ افحص الكود والـWindows GUI الحالي، لا تعتبر اللقطة حالة 2026-11-03 |
| P18 | HyperGet UI | `Screenshot 2026-10-21 103959.png` (996×284) | File menu: New Download, Import Clipboard, Batch Download, Exit | F03-02, F09-04 | مقارنة P03: مسار Site Grabber/Drop Target/import-export والجمع من الحافظة |
| P19 | HyperGet UI | `Screenshot 2026-10-21 104002.png` (1222×682) | Tasks menu: Start Queue, Stop Queue, Stop All, Delete | F03-02, F06-02 | مقارنة P04/P05: أوامر Pause/Resume/Delete completed/Find next/Speed limiter والجدولة |
| P20 | HyperGet UI | `Screenshot 2026-10-21 104006.png` (1312×559) | Tools menu: Browser Integration, Per Host Settings, Settings | F03-02, F04-05, F05-02 | سلوك Browser Integration/host rules، وأن كل أمر يفتح إعدادًا حقيقيًا |
| P21 | HyperGet UI | `Screenshot 2026-10-21 104011.png` (1150×555) | Help menu: Support, Source, Check update, About | F03-02, F09-05 | تأكد من آلية Check for Update وإظهار الإصدار وUpdate/rollback، لا ربط وهمي |
| P22 | HyperGet Settings | `Screenshot 2026-10-21 104019.png` (1919×1023) | Appearance: Theme, Language, UI Scale, toolbar labels, relative time, startup | F03-12, F03-13, F04-01 | فقط السلوك والحفظ للغة/الاختصارات/startup؛ لا تدخل أعمال ألوان/ثيمات |
| P23 | HyperGet Settings | `Screenshot 2026-10-21 104022.png` (1886×1009) | Appearance continuation: units, average speed, sounds, progress/completion dialog | F03-10, F03-11, F03-12, F04-04, F04-08 | اختبار تأثير الإعداد على النوافذ والاختصارات والخبرة بعد restart |
| P24 | HyperGet Settings | `Screenshot 2026-10-21 104026.png` (1909×1015) | Download Engine: directory, category default, speed, threads, concurrent downloads, retry, dynamic parts, per-host | F04-03, F04-05, F01-06, F01-09, F06-04 | ربط حقيقي بمحرك النقل؛ لا تعادل thread count تلقائيًا مع host/global caps |
| P25 | HyperGet Settings | `Screenshot 2026-10-21 104029.png` (1914×1008) | Engine continuation: proxy, DNS, User-Agent, SSL option, last-modified, partial files | F04-06, F01-07, F01-08, F01-10 | اختبار SSL fail-closed؛ لا تغيّر TLS إلى غير آمن افتراضيًا، وحافظ على الملفات |
| P26 | HyperGet Settings | `Screenshot 2026-10-21 104031.png` (1919×507) | Browser Integration: enable, port, API key field | F05-02, F05-04, F05-07, F10-05 | **تنبيه:** لقطة 2026-10-21 تُظهر مفتاح API غير محجوب؛ لا تنسخه إلى GitHub/السجلات. افحص إخفاء/تدوير المفتاح والمصادقة الفعلية |

## 2. أولويات الفروقات الوظيفية المستخرجة من الصور

| أولوية | الفارق/الحزمة | Reference ID(s) | كيفية إثبات الإغلاق |
| --- | --- | --- | --- |
| P0 | توحيد أوامر قوائم File/Tasks/Downloads/Tools/Help وشريط الأدوات/Context/Shortcut؛ لا أزرار صورية | P02–P07 / P17–P21 | action census بأسماء commands في app/main، نفس ID handler، Windows GUI مثبت، مع الحالات disabled |
| P0 | متصفح وقواعد اعتراض نوع الملفات وHost exceptions وHotkeys وبديل التنزيل | P08–P09 / P26 | Capture Chrome/Edge/Firefox وتجاهل المستثنى وفشل host بدون فقدان التنزيل، مع حماية الأسرار |
| P0 | مجلدات التصنيفات وtemporary/final/retry/cancel سياسة ملفات | P10–P11 / P24–P25 | change category + restart + final SHA/path، سيناريو تضارب الأسماء وfull disk |
| P0 | أسرار API Key وFTP وبروكسي: mask، store، redaction، rotation | P01/P13/P26 | no secret in screenshot/log/export، WinCred/IPC auth، negative tests |
| P1 | عمليات التحميل الجماعي، clipboard batch، site grabber، Drop Target، Import/Export | P03/P18 | 20 links، duplicate detection، queue persistence، cancel، fixture local site، import/export without secrets |
| P1 | الجدولة Start/Stop Queue وFind next F3 وSpeed limiter وقرار كل قائمة | P05/P19 | live action + restart state + keyboard event وقيود الحمل |
| P1 | إعدادات الاتصالات/الخادم/حد البيانات والبروكسي/PAC | P12–P14/P24–P25 | HTTP/FTP/HLS probes & caps، 1/4/16 Windows benchmark، fail-safe proxy auth |
| P1 | إعادة تعيين خيار تقدم/اكتمال التحميل والأصوات والتحذيرات | P11/P15/P22–P23 | start/download/complete/fail/queue event، صوت/حوار قابل للتخصيص والاستمرار بعد restart |
| P2 | خيارات VPN والديل أب القديمة/حالات التوافق | P14 | قرار رسمي بحسب دعم Windows الحالي؛ لا ربطها زورًا بمحرك FTP |
| خارج جلسة الوظائف | ألوان وThemes/Neon/3D style/حجم الأيقونات كتصميم بصري | P06–P07/P22 | تترك لمسار الثيمات؛ يُختبر فقط **عمل زر التخصيص والحفظ** في جلسة الوظائف |

## 3. مصفوفة أوامر واجهة IDM التي ينبغي فحصها (لا تعتبرها قائمة إنجاز)

- **Tasks/File:** New URL; batch URL; batch from clipboard; Site Grabber; Drop Target; export list; import list; exit؛ اختيار ملف؛ pause/stop/resume/re-download/remove.
- **Downloads:** stop all; delete completed; find Ctrl+F; find next F3; scheduler; start/stop queue; speed limiter and presets; options.
- **View/Toolbar:** categories show/hide; sort modes; toolbar customize/reorder/large/small; drop target show/hide; link-list customize; font/language behavior; active action parity in context/menu/toolbar/shortcut.
- **Settings/General:** startup with Windows; browser capture toggle; clipboard monitoring; browser whitelist; add browser; browser hotkeys; context-menu extensions; per-site exclusions.
- **Settings/File types:** interception extension allow-list; excluded domains; excluded URL patterns/words; defaults/reset.
- **Settings/Save to:** category→folder mapping; file extension rules; default final folder; temporary staging folder; path safety; last-used folder policy.
- **Settings/Downloads:** start confirmation; progress dialog; completion dialog; duplicate URL policy; edit while resuming; User-Agent; antivirus hook/consent.
- **Settings/Connection:** max per-file connections; per-site override; traffic/volume limits; host/global cap interactions.
- **Settings/Proxy:** no proxy; system; PAC; manual proxy; credentials; HTTP/HTTPS/FTP/SOCKS; passive FTP; auth/redirect leak prevention.
- **Settings/VPN:** Windows dial-up/reconnect capability — explicit supported/unsupported scope decision.
- **Settings/Sounds:** download complete/failed; queue start/finish/stop; sound file chooser/test; mute/default/notification toggles.
- **HyperGet legacy settings:** categories/default paths/global speed/concurrency/retries/dynamic splitting/per-host DNS/proxy/security/partial file retention/browser host authentication; validate current source and real effects, not existence of switches alone.

## 4. فحص قبول عملي لكل control

**لا يُغلَق أي control من هذه الصور حتى يتوفر:** (1) location in present source + commit SHA + main/PR attribution, (2) action wiring, (3) unit or integration fixture, (4) installed Windows GUI test where relevant, (5) persistence across full app restart for any setting, (6) negative/safety tests, (7) no visual/theme changes, (8) status in living master plan. Without that mark `partial` or `unverified`.

**UI comparison caution:** Current Oct-2026 screenshot may show English/AB Download Manager and an incomplete FTP add form; later commits could already have implemented FTP and branding. Never reimplement from screenshot alone. Compare with the latest `main`, functionality PR #3 and theme PR #4 without cherry-picking themes.

**Why 26?** ZIP contains 30 entries, 4 directory entries and 26 PNG files; 15 images of IDM and 11 of the then-current application. There is no index text or P27 in this ZIP. If another reference image exists, it needs a separate verified source; existing images remain fully indexed and actionable.

## 5. Source file identities (no pixels in repository)

| ID | Original relative path in user archive | SHA-256 |
| --- | --- | --- |
| P01 | `original idm UI/Screenshot 2026-10-21 103059.png` | `6414343f29e2720126ef8365038b4b9f9d89081d934ee86b7d68f7dfff9d71d4` |
| P02 | `original idm UI/Screenshot 2026-10-21 103205.png` | `3a9f85da8a4b09fd5c1fbe442022a7088e06770ef612ec4dbccdc4e9f04c53ea` |
| P03 | `original idm UI/Screenshot 2026-10-21 103747.png` | `2105a6da4184864a70f75e9a0e59c01c2e5ff724c9df9dcf859f4d6e9289f57b` |
| P04 | `original idm UI/Screenshot 2026-10-21 103756.png` | `ddfa8e7cdf7b67d3a7cea5f91feb0bebedd28b9e0ec7041ec0352adecad7f02b` |
| P05 | `original idm UI/Screenshot 2026-10-21 103800.png` | `a1b9a6a0155a421749664dee0ef183992462a644ffdfd0202268b197f6af8a58` |
| P06 | `original idm UI/Screenshot 2026-10-21 103803.png` | `fb40ba5bde640617239e2b313395c738d7dc1bc2bde1270f0972dc9e56d1f30c` |
| P07 | `original idm UI/Screenshot 2026-10-21 103821.png` | `62e31ed7dcc4bb1c08e0e8e882d03a9a72ea5e9542cd6d059a3b9de9c2af1bff` |
| P08 | `original idm settings/Screenshot 2026-10-21 103843.png` | `2212b990ee7334bb3a227362a245cb133357e1814afa31003e0fdd1f496d75b4` |
| P09 | `original idm settings/Screenshot 2026-10-21 103851.png` | `31c98bdc82ce09501b83b1c419276644e64537d0ab0f3841d2b5a6142ba79cad` |
| P10 | `original idm settings/Screenshot 2026-10-21 103908.png` | `26e97e07c3b738e064c63df59fba489d260da4f97345d2a84a26f58d00bc3801` |
| P11 | `original idm settings/Screenshot 2026-10-21 103911.png` | `95be1bc302b2fb0ac8059951c644287806e38624129008f7308351d34022b11f` |
| P12 | `original idm settings/Screenshot 2026-10-21 103914.png` | `32c4cb45c1398d46f67a16cb805cc410650c92be543cb1f391577f536b66e773` |
| P13 | `original idm settings/Screenshot 2026-10-21 103917.png` | `335cd3986d3204a034a676563ef005e724d8f1f05c733f25324c2718708de389` |
| P14 | `original idm settings/Screenshot 2026-10-21 103929.png` | `a4a0a1664086f310bf1a9f3339a5a7e689677a7f42c0712e8fd47c2a4796a4ad` |
| P15 | `original idm settings/Screenshot 2026-10-21 103936.png` | `6a2178d337df4a0fcc86a2d42a9363f35ed1a52f70fef7f6fc4518b998f3af99` |
| P16 | `our app UI/Screenshot 2026-10-21 102931.png` | `004085415b45d7b01a18687ea8ed3e15ba538de2c5385eeb0766d656da2c1925` |
| P17 | `our app UI/Screenshot 2026-10-21 103113.png` | `ec80c4eadda99370d5c394a2fd9e7598480ceedad274e16a96ee2f3f387944dd` |
| P18 | `our app UI/Screenshot 2026-10-21 103959.png` | `dd58c589772ba2ec6ddde98eb083496c929d7fbbcb9c3f3c6a284f4ff407f1f3` |
| P19 | `our app UI/Screenshot 2026-10-21 104002.png` | `f7f9bac2108219add5c869b2e5dfc8b8b7e6a85c2ab55778e17eb3b1f9d1bc14` |
| P20 | `our app UI/Screenshot 2026-10-21 104006.png` | `d91bb9867a028edd4f539ddec9f3329b67a72b3ae707869ce3eef27184aa73ff` |
| P21 | `our app UI/Screenshot 2026-10-21 104011.png` | `ba4ffc4636e6745850435d95a47a0b5e6f6d9c2ebf1be3aa265c70ccafd6c577` |
| P22 | `our app settings/Screenshot 2026-10-21 104019.png` | `243c0377fddf31ec3ce2da9076b2d8c62b0397a7a1808bebd45f31ebad3ba5d3` |
| P23 | `our app settings/Screenshot 2026-10-21 104022.png` | `aa8ea92c016860fea889db8eabf13e3807c88e24b89f31cd268e280d88c5c498` |
| P24 | `our app settings/Screenshot 2026-10-21 104026.png` | `29418189b99922a2d2054a4af0a128510996f94efe6f48c32ed3c202e8b51abe` |
| P25 | `our app settings/Screenshot 2026-10-21 104029.png` | `996d4c1a393998b2044f7303a1f5cc58aa1e61bdb73c47a1cf7c711801df2205` |
| P26 | `our app settings/Screenshot 2026-10-21 104031.png` | `70911550d586fd55b52e5b16bb8c07631ee8271c6aa8abbb45cb54f2647a8a8f` |

## 6. Related documents and next work

- Living plan: `docs/roadmaps/HYPERGET_FUNCTIONALITY_MASTER_PLAN.md` — jobs F00-03, F03-01…14, F04, F05, F06, F08, F09, F11.
- Historical requirements: `docs/IDM_Requirements_Trace_82_2026-10-21.md`, `docs/IDM_REQUIREMENTS_AUDIT_2026-10-30.md`, `docs/IDM_Repair_Addendum_2026-10-21.md`.
- Private source archive in ChatGPT Library: `/HyperGet/IDM_REFERENCE_2026-10-21_26_IMAGES_PRIVATE.zip`.
- Next: run current source/menu/settings census against the functional branch; update the table per control and implement missing behavior with exact-SHA Windows tests. **No product code changed by creating this audit.**