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

## الحسابات والمزامنة

- **Firebase Authentication**: تسجيل بالبريد وكلمة المرور، وGoogle Sign-In، وتحقّق من البريد،
  واستعادة كلمة المرور. كل العمليات تمر عبر `FirebaseAuthManager` وحده.
- **ربط وسائل الدخول**: ربط Google بحساب موجود يستخدم `linkWithCredential` على المستخدم الحالي،
  فلا يتغير UID ولا تُفقد المكتبة؛ وإذا كان البريد مسجلًا بكلمة مرور يطلب التطبيق تلك الكلمة ثم
  يربط Google بدل إنشاء حساب ثانٍ.
- **كلمة المرور لحسابات Google**: تعيين كلمة المرور يربط مزوّد البريد بالحساب نفسه، وبعده يتحول
  الزر تلقائيًا إلى «تغيير كلمة المرور» اعتمادًا على المزوّدات المرتبطة فعليًا لا على نوع التسجيل.
- **مستند الحساب**: `users/{uid}` يحتوي الاسم واسم المستخدم واللغة والدولة والعمر وصورة مضغوطة
  (base64 صغير داخل المستند، لا Firebase Storage). حجز اسم المستخدم عبر معاملة على
  `usernames/{name}`.
- **المزامنة**: `CloudSync` يعكس كل تفضيلات الجهاز إلى `users/{uid}/state/prefs` بمفتاح مستقل
  وختم زمني لكل مفتاح، ويستمع للتغييرات ويعيد تطبيقها؛ فجهازان يعدّلان تفضيلين مختلفين يحتفظ كل
  منهما بتعديله، وتعديل المفتاح نفسه يُحلّ بالأحدث، ولا يرتد التعديل إلى صاحبه.
- **بوابة واحدة للحالة**: `SessionGate` يحوّل (الجلسة، تأكيد البريد، اكتمال المستند، جلسة الزائر)
  إلى حالة واحدة: `LOADING / SIGNED_OUT / GUEST / EMAIL_VERIFICATION / ACCOUNT_SETUP / READY`،
  و`RootNavHost` لا يرسم إلا الشاشة التي تخصها. الشاشات المحمية ليست مسارات تنقل أصلًا، فلا
  ديبلينك ولا Intent ولا Back Stack ولا جلسة محلية قديمة تستطيع الوصول إليها.
- **تأكيد البريد إلزامي**: حساب مسجّل وبريده غير مؤكد يرى `VerifyEmailScreen` فقط؛ لا إعداد حساب
  ولا واجهة رئيسية قبل التأكيد، ويعيد المحاولة بعد `reload()` لا بعد ضغطة زر.
- **إعداد الحساب خطوتان**: الاسم الظاهر واسم المستخدم والصورة في الخطوة الأولى، ثم التفضيلات.
  يُملأ النموذج مرة واحدة لكل حساب فقط، فلا يعود اسم حُذف إلى الظهور. حساب Google موجود مسبقًا
  لا يُطلب منه الإعداد مرة أخرى.
- **الشارة الذهبية**: مصدرها `badges/{uid}` فقط، وقواعد Firestore تمنع أي كتابة من العميل عليها.
  الحقل `isVerified` موجود على `users/{uid}` كمرآة للشارة، ولا تُقبل كتابته إلا إذا طابقت الشارة
  الفعلية (`isVerified == exists(badges/uid)`)، فلا يمكن منح التوثيق من التطبيق.
- **حذف الحساب**: اسم المستخدم + كلمة المرور + `reauthenticate`، ثم تفريغ المجموعات الفرعية
  وحذف المستند وحجز الاسم ثم حساب Firebase. لا تُعرض رسالة نجاح قبل نجاح هذه الخطوات.
- **الإشعارات**: `MangaMessagingService` يسجّل رمز FCM في `users/{uid}/devices/{token}` ويزيله
  عند تسجيل الخروج؛ والفحص الدوري للفصول يعمل بمهمة فريدة لكل حساب.

### القواعد والأمان

`firestore.rules` تتحقق من الشكل لا من الملكية وحدها: مفاتيح مسموحة فقط، أنواع وأطوال محدودة،
اسم مستخدم بنمط `^[a-z0-9_]{3,20}$`، دولة من الدول العربية، عمر بين 13 و99، صورة بحجم محدود،
ومنع كامل لحقول الإدارة (`verified`, `role`, `admin`, `banned`, `suspended`). المجموعات الفرعية
(المكتبة، التقدم، الفصول المقروءة، الأجهزة، الحالة، الإشعارات) مقيّدة بعدد حقول وبمعرّف الجهاز،
ومستند الحالة الوحيد المسموح هو `state/prefs` الذي تكتبه المزامنة (`fields` + `stamps` + `clientId`)،
وبقية المسارات مرفوضة.

قبل النشر: انشر القواعد بـ `firebase deploy --only firestore:rules`، ثم امنح التوثيق يدويًا
بمستند `badges/{uid}` بالقيمة `verified: true` من وحدة تحكم Firebase (أو Admin SDK). بعد أول
قراءة يكتب التطبيق `users/{uid}.isVerified = true` مطابقةً للشارة، فيظهر الحقل في وحدة التحكم.

## ما لا يزال ناقصاً

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

The primary server is the default source. `SourceRepository.defaultSourceId` is the first entry
of the registry, the enabled list is ordered with it first, and the Latest feed resolves
«saved choice → primary → first enabled» without writing anything. A failure keeps the choice and
shows a retry state instead of switching servers silently. The Latest screen has no source
switcher of its own; choosing a server belongs to the Sources screen.
