# بحث App Commander وحدود أندرويد

تاريخ المراجعة: 2026-10-18

## المشاريع التي استفدنا من أفكارها

| المشروع | الأفكار المفيدة | الترخيص/ملاحظة |
|---|---|---|
| App Manager | معلومات المكوّنات، AppOps، Profiles، APK inspection، Batch actions | GPLv3+؛ استُخدمت الأفكار فقط ولم يُنسخ كود |
| Hail | فصل أوضاع العمل: Shizuku وRoot وDevice Owner، وخيارات Disable/Hide/Suspend | GPL-3.0؛ استُخدمت مصفوفة القدرات كمرجع |
| Activity Launcher | تشغيل الـActivities وإنشاء الاختصارات | ISC؛ استُخدمت الفكرة العامة |
| Shizuku | منح تطبيق عادي وصولًا إلى system APIs بصلاحيات ADB/root | Apache-2.0؛ مستخدم كتبعِيّة رسمية |
| Canta | إزالة تطبيقات المستخدم والنظام للمستخدم الحالي وقاعدة Debloat | LGPL-3.0؛ قاعدة UAD مخطط لها لاحقًا بعد مراجعة الترخيص |
| Inure | واجهة جميلة، تحليلات، Terminal، Themes، Root/Shizuku | نستلهم تجربة الاستخدام فقط |
| SuperFreezZ | إيقاف نشاط التطبيقات في الخلفية وسيناريوهات التجميد | مرجع لسلوك الأتمتة القادم |

## ما يسمح به كل مستوى

| الوظيفة | عادي | Usage Access | Shizuku/ADB | Device Owner | Root |
|---|:---:|:---:|:---:|:---:|:---:|
| عرض التطبيقات والمعلومات الأساسية | ✓ | ✓ | ✓ | ✓ | ✓ |
| Usage stats | — | ✓ | ✓ | ✓ | ✓ |
| تشغيل Activities المصدّرة | ✓ | ✓ | ✓ | ✓ | ✓ |
| Force stop لتطبيق آخر | — | — | ✓ | — | ✓ |
| تعطيل تطبيق أو مكوّن | — | — | ✓ | محدود | ✓ |
| Suspend/Hide | — | — | حسب الجهاز | ✓ | ✓ |
| AppOps والخلفية | — | قراءة محدودة | ✓ | محدود | ✓ |
| منح/سحب Runtime permissions | إعدادات النظام | — | ✓ عندما يسمح النظام | سياسات محدودة | ✓ |

## قرارات المنتج

- كل زر يعرض القدرة الفعلية المتاحة بدل ادعاء أن أندرويد يسمح بكل شيء.
- التطبيق Offline ولا يملك إذن Internet.
- أوامر Shizuku لا تمرر مدخلات حرة إلى shell؛ أسماء الحزم والمكوّنات تتحقق بنمط صارم.
- الأتمتة لا تُفعّل حتى يصبح محرك التنفيذ وسجل التراجع جاهزين.
- أي Debloat سيعرض تصنيف خطورة وتحذيرًا خاصًا لحزم النظام.

## المصادر الأساسية

- Android package visibility: https://developer.android.com/training/package-visibility
- DevicePolicyManager: https://developer.android.com/reference/android/app/admin/DevicePolicyManager
- UsageStatsManager: https://developer.android.com/reference/android/app/usage/UsageStatsManager
- Shizuku: https://github.com/RikkaApps/Shizuku
- App Manager: https://github.com/MuntashirAkon/AppManager
- Hail: https://github.com/aistra0528/Hail
- Activity Launcher: https://github.com/ActivityLauncher/ActivityLauncher
- Canta: https://github.com/samolego/Canta
- Inure: https://github.com/Hamza417/Inure
- SuperFreezZ: https://github.com/amitv87/SuperFreezZ
