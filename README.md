# MangaOS

تطبيق أندرويد لقراءة المانجا والمانهوا، مبني بـ Kotlin و Jetpack Compose و Material 3.

المرحلة الثانية أضافت: صفحة تفاصيل مانجا كاملة، قائمة جانبية محسّنة، وضعي العرض Grid/List
مع حفظ التفضيل، إعادة تصميم واجهات الحساب، حقول ملف شخصي موسّعة (الدولة/العمر/اللغة المفضلة)،
العربية كلغة افتراضية، وإدارة مصادر متوافقة مع صيغة فهرس Mihon (تثبيت/تحديث/حذف مع صلاحيات Android).

## التقنية

- Kotlin 2.0.21، AGP 8.7.3، Gradle 8.11.1، JDK 17
- minSdk 24، compileSdk/targetSdk 35
- Jetpack Compose (BOM 2024.10.01) + Material 3 + Navigation Compose
- ViewModel + Coroutines/Flow + DataStore Preferences + Coil
- Clean Architecture: `presentation` / `domain` / `data` / `core` / `navigation` / `designsystem`

## البنية

```
app/src/main/java/com/mangaos/app/
├── core/
│   ├── designsystem/   Color, Theme (4 themes), Type, Dimens
│   ├── ui/             Snackbar host + shared components (card, list row, search, states)
│   └── util/           LocaleManager, Validators, TimeAgo, Formatters
├── data/
│   ├── extension/      Mihon index parser + extension store/installer
│   ├── local/          DataStore settings
│   └── repository/     Extension-backed catalogue, persisted library, download queue
├── domain/model/       Manga, Chapter, Extension, UserProfile, Download, ...
├── presentation/
│   ├── navigation/     RootNavHost, MainScaffold, Destinations
│   ├── auth/           Choice, sign-up, sign-in, profile, verification
│   ├── latest/ manga/ library/ sources/ downloads/ storage/ notifications/
│   ├── settings/ about/ detail/ common/
└── di/ServiceLocator.kt
```

## الثيمات واللغات

- الثيمات الثمانية: Default، Sakura، Shana، Miku، Neon، Sky، Forest، Coffee — لكل ثيم نسخة فاتحة وأخرى داكنة تتبع النظام افتراضياً، مع وضع AMOLED.
- الأزرق (`#018AFE` ومشتقاته) لون تمييزي مستخرج من الشعار، وليس لوناً طاغياً.
- اللغات: العربية (RTL، اللغة الافتراضية)، English، 日本語 عبر String Resources وحفظ اختيار المستخدم.

## المصادر

خمسة عشر مصدراً أصلياً مدمجاً داخل التطبيق (`Native In-App Sources`)، كل واحد بتنفيذه الخاص
داخل `app/src/main/java/com/mangaos/app/sources/implementations/`:

| المصدر | اللغة | العنوان |
| --- | --- | --- |
| MangaTek | ar | mangatek.com |
| Kawaii Manga | fr | www.mangakawaii.fr |
| Despair Manga | ar | despair-world.com |
| ProComic | ar | procomic.pro |
| MangaAr | ar | mangaar.com |
| Thunder Scans | en | en-thunderscans.com |
| Asura Scans | en | asurascans.com |
| Team X Manga | ar | olympustaff.com |
| Azora | ar | azorafly.com |
| Manga Dar | ar | mangadar.com |
| Manga Origin | ar | teammangaorigin.blogspot.com |
| Manga Swat | ar | meshmanga.com |
| Manga Tik | ar | mangatik.com |
| Manga Time | ar | mangatime.org |
| Dilar | ar | dilar.tube |

- لا يوجد تثبيت أو تحديث أو مستودع إضافات: كل مصدر جزء من الـ APK، ويُفعّل أو يُعطّل من شاشة
  المصادر فقط. الحالة محفوظة في DataStore ولا تمسّ المكتبة أو سجل القراءة أو التنزيلات.
- لكل مصدر آلية اتصال خاصة به: HTML مع Jsoup، أو واجهة JSON، أو feeds، أو tRPC. لا يُفرض
  Parser موحّد على الجميع، والواجهة المشتركة (`MangaSource`) تحدّد فقط كيف يتحدّث التطبيق مع المصدر.
- معرّف المصدر ثابت (`SourceDefinition.id`)، والعمل يُخزَّن دائماً كمفتاح `sourceId/id`، فلا تختلط
  الأعمال المتشابهة من مصادر مختلفة.
- أيقونة كل مصدر أصلية داخل `res/drawable-nodpi/source_<id>.png` وتظهر بجانب اسمه.
- **MangaTek**: صفحاته قد تحمل نص الترجمة كطبقة منفصلة عن الصورة (فقاعات بمواضعها). تُفكّ
  الطبقة (`MangaTekOverlay`) وتُرسم فوق الصورة في خطّ صور واحد يشترك فيه القارئ والتنزيلات
  (`OverlayImageInterceptor`)، فلا تُفقد الترجمة ولا يختل موضع الفقاعات.

`tools/verify_native_sources.py` يفحص المصادر الحقيقية (بحث ← تفاصيل ← فصول ← صفحات) بنفس قواعد
الاستخراج، ويُشغَّل يدوياً من المستودع.

### حدود معروفة

- ProComic و Dilar يعرضان صفحات الفصول داخل قارئ برمجي على الموقع نفسه (الصور تُبنى أو تُفكّ
  تشفيرها في المتصفح)، لذلك يظهر الفصل ورسالة واضحة بدل قائمة صفحات ناقصة.
- بعض المواقع تحجب شبكات البيانات/الخوادم (MangaTek, Manga Dar, Dilar وبعض مسارات
  Mangakawaii) بـ Cloudflare، فلا يمكن التحقق منها من بيئة الفحص؛ تبقى التنفيذات مبنية على
  آلية كل إضافة أصلية.

## البناء والتوقيع

البناء الرسمي يتم على GitHub Actions (`.github/workflows/android-release.yml`) عند كل push إلى
`main` أو عبر `workflow_dispatch`: اختبارات الوحدة ← بناء APK/AAB موقّع ← تحقق `apksigner`
← رفع Artifacts.

التوقيع يُقرأ من متغيرات البيئة التي يوفّرها الـ workflow:

| المتغير | GitHub Secret |
| --- | --- |
| `MANGAOS_KEYSTORE_PATH` | يُكتب من `KEYSTORE_BASE64` |
| `MANGAOS_KEYSTORE_PASSWORD` | `KEYSTORE_PASSWORD` |
| `MANGAOS_KEY_ALIAS` | `KEY_ALIAS` |
| `MANGAOS_KEY_PASSWORD` | `KEY_PASSWORD` |

لإنشاء الـ keystore ورفع الأسرار:

```bash
tools/setup-signing.sh            # يستخدم gh CLI والمستودع الحالي
tools/setup-signing.sh owner/repo # أو حدّد المستودع صراحةً
```

احتفظ بنسخة احتياطية من `signing/mangaos-release.jks` وكلمتي المرور: بدونهما لا يمكن إصدار
تحديثات بنفس هوية التطبيق. الملف مستثنى من git عبر `.gitignore`.

## التنزيلات والمكتبة المحلية والإشعارات

- **مدير تنزيلات حقيقي**: كل عملية تنزيل مهمة مستقلة تحمل فصلًا واحدًا أو عدة فصول، ولكل فصل
  حالة وتقدم وصفحات مكتوبة على القرص. تُنفَّذ قائمة الانتظار عبر `WorkManager`
  (`DownloadWorker`) بمهمة واحدة في كل مرة، فتستمر بعد مغادرة الشاشة أو إغلاق التطبيق وتُستأنف
  بعد إعادة تشغيل الجهاز، مع قيود الشبكة وإعادة المحاولة.
- **إشعارات تنزيل على مستوى النظام**: إشعار واحد ثابت لكل مهمة يتحدث في مكانه (غلاف، اسم العمل،
  شريط تقدم، نسبة حقيقية، حالة) مع إجراءات إيقاف مؤقت/استئناف/إلغاء/إعادة محاولة تعمل فعليًا.
- **مكتبة محلية دائمة**: سجل واحد لكل عمل (`local_library.json`)، تُدمج فيه الفصول المكتملة.
  البيانات والغلاف محفوظان محليًا، وصفحة التفاصيل والقارئ يعملان دون إنترنت اعتمادًا على
  الملفات وحدها؛ حذف سجل المهمة لا يحذف المحتوى، وحذف المحتوى إجراء مستقل.
- **إشعارات الفصول الجديدة**: فحص دوري كل 20 دقيقة (`PeriodicWorkRequest`) لأعمال المكتبة فقط،
  بمقارنة مع خط أساس محفوظ. أول مزامنة صامتة، ولا يتكرر إشعار الفصل نفسه، وفشل مصدر لا يوقف
  بقية المصادر. الضغط على الإشعار يفتح تفاصيل العمل بمعرّف المصدر والعمل.
- **فصل واضح**: تبويب الإشعارات داخل التطبيق يعرض إشعارات الفصول الجديدة فقط، ومهام التنزيل لا
  تدخله إطلاقًا.
- ذاكرة الصور المؤقتة (`cacheDir/image_cache`) منفصلة عن ملفات الفصول الدائمة في `filesDir`.

## ما لا يزال ناقصاً

- المصادقة محلية بالكامل: حسابات بكلمات مرور مجزّأة (SHA-256 بملح) داخل الجهاز، مع تسجيل خروج يعيد إلى شاشة المصادقة دون إعادة الإعداد الأولي؛ لا Firebase ولا مزامنة سحابية.
- بعض المصادر تحتاج تسجيل دخول تفاعلي أو WebView؛ لا توجد شاشة تسجيل دخول بعد، لذلك تعمل
  المصادر التي لا تتطلب ذلك فقط.
- التنزيلات تُنفَّذ بالتتابع (مهمة واحدة في الوقت نفسه) لضمان استقرار الذاكرة والشبكة.
- لا بيانات تجريبية: التطبيق لا يعرض أي مانجا من صنعه.

## Sources

Every source is a Kotlin implementation compiled into the APK; no extension APK is installed or
loaded at runtime. `NativeSourceRegistry` lists them, `NativeSourceManager` stores the
enable/disable choice, and `NativeMangaRepository` runs each call isolated so one broken site
cannot affect the others. `tools/verify_native_sources.py` checks the live sites with the same
parsing rules as the app.
