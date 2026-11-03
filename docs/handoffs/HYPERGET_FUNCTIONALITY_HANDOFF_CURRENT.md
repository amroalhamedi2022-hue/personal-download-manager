# HyperGet — HANDOFF سريع للوظائف (CURRENT)

**آخر تحديث:** 2026-11-03 | **النطاق:** Functionality فقط؛ الثيمات وLiquid Glass في مسار آخر.

## الحالة المؤكدة
- **المستودع:** `amroalhamedi2022-hue/personal-download-manager`.
- **main عند بدء هذا التحديث:** `f37aab6270bee57ed23455273de9b3071230384b`؛ افحص أحدث HEAD دائمًا، ولا تعتمد هذا الرقم إذا تغيّر.
- **PR #3 (الوظائف):** `automation/idm-ftp-cancel-and-category-flush`، Draft، HEAD `3582e0a043e938442cc675a0e1cf4f63c9fe6e07`؛ base: `codex/repair-rf00-rf01`.
- **PR #4 (الثيمات):** Draft، HEAD `a5867fdc940fd197b290ee32815ce91e2712dd79`؛ **لا تعدّله ضمن الوظائف**.
- **آخر اختبار أداء حرج معروف لـPR #3:** [Windows installed active HTTP 1/4/16 #41644174970](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41644174970) فشل عند حمل 16 بسبب انخفاض throughput نحو 24.6%؛ لا تعتبر الـSHA جاهزًا للدمج حتى إصلاح السبب وإعادة الاختبار.
- **المنجز في الجولة الأخيرة:** توثيق واسترجاع المقارنة الأصلية فقط؛ **لا تغيير كود وظيفي، ولا EXE جديد**.
- **مرجع IDM:** 26 صورة PNG فعلية في مكتبة ChatGPT الخاصة `/HyperGet/IDM_REFERENCE_2026-10-21_26_IMAGES_PRIVATE.zip`؛ لا ترفع الصور الخام إلى GitHub (قد تتضمن أسرارًا).

## اقرأ قبل أي تنفيذ
- [خطة الـ81 مهمة (المصدر الحاكم)](../roadmaps/HYPERGET_FUNCTIONALITY_MASTER_PLAN.md)
- [تدقيق الصور والأزرار والإعدادات](../roadmaps/HYPERGET_IDM_26_SCREENSHOT_FUNCTIONAL_AUDIT_2026-11-03.md)
- `docs/IDM_REQUIREMENTS_AUDIT_2026-10-30.md` و`docs/IDM_Requirements_Trace_82_2026-10-21.md` و`docs/AUTONOMOUS_ACCEPTANCE.md`.

## الخطوة التالية
1. افحص HEAD وActions وPRs من جديد، وحدد ما نجح فعلًا وما هو غير مدمج؛ لا تكرر Commits.
2. عالج فشل benchmark 16 بتنميط throughput والذاكرة والاتصالات، **لا** بتغيير semaphore عشوائيًا أو تخفيف بوابة القبول.
3. أغلق وظائف الأوامر/Toolbar/Menu/Context/Tray والاختصارات والإعدادات من مقارنة الصور واحدًا واحدًا، مع اختبارات تأثيرها وحفظها بعد restart؛ بعدها collectors/packages/media حسب الخطة.
4. ادمج **فقط** ما اجتاز بوابات Windows ومراجعة تداخل الفروع، دون نقل الثيمات أو المساس ببيانات المستخدم.

## قانون تحديث HANDOFF بعد كل دفعة تطوير كبيرة
**حدّث هذا الملف نفسه، لا تنشئ نسخًا مؤرخة متناثرة. اجعله بحد أقصى ~25 سطرًا عمليًا:** (1) آخر SHA والفرع/PR، (2) المنجز، (3) نتيجة Windows/Actions، (4) المتبقي/العوائق، (5) الخطوة التالية. حدّث معه حالات IDs في الخطة الرئيسية **بعد الدليل**، وزامِن النسختين داخل مكتبة ChatGPT: `/HyperGet/HYPERGET_FUNCTIONALITY_HANDOFF_CURRENT.md` و`/HyperGet/HYPERGET_FUNCTIONALITY_MASTER_PLAN.md`. لا تزعم المزامنة إن فشلت.

**جملة الاستكمال:** «كمل وظائف HyperGet من `docs/handoffs/HYPERGET_FUNCTIONALITY_HANDOFF_CURRENT.md` والخطة الرئيسية؛ افحص أحدث HEAD وActions، لا تكرر المكتمل، لا تلمس الثيمات، نفذ الناقص واختبره وحدث HANDOFF في GitHub والمكتبة».
