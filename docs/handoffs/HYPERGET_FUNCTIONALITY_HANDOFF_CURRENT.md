# HyperGet — HANDOFF سريع للوظائف (CURRENT)

**تحديث:** 2026-11-03 | **النطاق:** Functionality فقط؛ لا تلمس الثيمات أو Liquid Glass.

## الحالة المختصرة
- **repo:** `amroalhamedi2022-hue/personal-download-manager`.
- **main — أحدث تغيير كود بهذه الجولة:** `673b29dbd8e2219b48eaa8b192dd959f3aaf5652`؛ افحص HEAD الحالي قبل أي تعديل (قد توجد commits توثيقية بعده).
- **PR #3 وظائف:** `automation/idm-ftp-cancel-and-category-flush`، SHA `ff0fd84be441557eb2e00dc0016259e65b369ac3`، Draft؛ **لا تدمجه كاملًا**: main وPR3 متباعدان (873 ahead / 48 behind عند الفحص). 
- **PR #4 ثيمات:** `experiment/liquid-reference-match-20261030`، كان `a5867fdc940fd197b290ee32815ce91e2712dd79`؛ اتركه منفصلًا.
- **أُنجز برمجيًا:** backup/export/import لإعدادات التطبيق غير السرية عبر JSON versioned allowlist، Import ذري مع تحقّق، حماية Browser API Key بإخفاء افتراضي وزر Show/Hide؛ 11 ملفًا نُقلت انتقائيًا إلى main.
- **دليل PR3:** Windows Kotlin compile + backup tests **PASS** [41705173090](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41705173090)، ومعه source-only/FTP/HTTP/HTTPS/queue/aria2 المختبرة على نفس SHA.
- **دليل main:** [Private Windows validation #41706083060](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41706083060) **PASS كامل**: Windows compile/tests + installer + silent NSIS install + safe uninstall + upload؛ ملف EXE تم استخراجه من artifact، SHA-256 `74070291e71edca14119f3d49ace3bdea0b9632323146f544d8abdbe87af1b46`. ليس إصدار 1.0 نهائيًا.
- **مفتوح:** F04-11 **جزئي**: تصدير إعدادات proxy/per-host والحسابات والحزم/النسخ الاحتياطي والاسترجاع الكامل لم تُنفذ. F05-07 **جزئي**: إخفاء المفتاح في Desktop فقط، تدقيق شامل للأسرار ما زال مفتوحًا.
- **معطّل قبول PR3 الكامل:** Windows 1/4/16 benchmark فشل تاريخيًا [41644174970](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41644174970)؛ افحص الأحدث لنفس SHA.
- **مرجع الصور:** 26 صورة IDM مقابل HyperGet محفوظة خاصًا: `/HyperGet/IDM_REFERENCE_2026-10-21_26_IMAGES_PRIVATE.zip`، وفهرس نصي في `docs/roadmaps/HYPERGET_IDM_26_SCREENSHOT_FUNCTIONAL_AUDIT_2026-11-03.md`؛ **ممنوع رفع الصور الخام** لاحتمال وجود مفتاح ظاهر.

## الخطوة التالية والقانون
1. افحص أحدث GitHub HEAD/Actions/PR3/PR4؛ إذا فشل main Windows build حل الخطأ من السجل؛ إذا نجح افحص JUnit/installer SHA ثم اعتمد هذه الدفعة. لا تكرر الكود.
2. ثم معالجة benchmark 16 من سببه الجذري؛ وبعدها أدوات IDM الأخرى من [الخطة الرئيسية](../roadmaps/HYPERGET_FUNCTIONALITY_MASTER_PLAN.md)، خصوصًا وظائف Toolbar/Menu/Shortcut/Settings.
3. بعد **كل جولة كبيرة** عدّل **هذا الملف نفسه** (SHA، المنجز، الاختبارات، المتبقي، التالي) وحدّث IDs الخطة بالدليل، ورفع نسخته إلى مكتبة ChatGPT `/HyperGet/HYPERGET_FUNCTIONALITY_HANDOFF_CURRENT.md`. لا تخلط الثيمات مع الوظائف.

**رسالة الاستكمال:** «كمل وظائف HyperGet من أحدث `docs/handoffs/HYPERGET_FUNCTIONALITY_HANDOFF_CURRENT.md`، افحص HEAD وWindows Actions، نفّذ الناقص فقط وادمج الآمن في main، ثم حدّث الهند أوف والخطة والمكتبة». 
