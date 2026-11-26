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

- الثيمات: Default، Miko، Neon، Sky — لكل ثيم نسخة فاتحة وأخرى داكنة تتبع النظام افتراضياً.
- الأزرق (`#018AFE` ومشتقاته) لون تمييزي مستخرج من الشعار، وليس لوناً طاغياً.
- اللغات: العربية (RTL، اللغة الافتراضية)، English، 日本語 عبر String Resources وحفظ اختيار المستخدم.

## المصادر

- الفهرس مدمج في `MihonIndexParser` ويُقرأ عند فتح شاشة المصادر (مع نسخة مخبأة عند انقطاع الشبكة).
- المستخدم يرى المصادر فقط: لا يظهر اسم المستودع ولا رابطه ولا صيغة الفهرس في أي مكان.
- التثبيت يمرّ عبر `PackageInstaller` بعد فحص اسم الحزمة وإصدار الـ APK، وبعد موافقة المستخدم
  على «تثبيت التطبيقات غير المعروفة» (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`).
- **وقت التشغيل**: التطبيق يضمّ تنفيذ Mihon الحقيقي (`source-api` + `core-common`) ويسجّل
  الخدمات التي تحقنها الإضافات (Context، `NetworkHelper`، OkHttp، Jsoup، RxJava، Injekt،
  QuickJS). تُحمَّل حزم الإضافات المثبتة بـ `DelegateLastClassLoader`، وتُقرأ الفئات المعلنة في
  بيان الإضافة فقط. كل طلب بحث/تفاصيل/فصول/صور يمر عبر `MihonRuntime`، والفشل في إضافة واحدة
  لا يعطّل البقية. راجع «Sources and Mihon extensions» أدناه للتفاصيل.

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

## ما لا يزال ناقصاً

- لا مصادقة حقيقية (Firebase/Google)؛ التحقق من البريد محلي بوضوح.
- بعض المصادر تحتاج تسجيل دخول تفاعلي أو WebView؛ لا توجد شاشة تسجيل دخول بعد، لذلك تعمل
  المصادر التي لا تتطلب ذلك فقط.
- الإشعارات داخل التطبيق فقط، وليست إشعارات نظام.
- لا بيانات تجريبية: التطبيق لا يعرض أي مانجا من صنعه.

## Sources and Mihon extensions

MangaOS loads the same extension APKs that Mihon/Keiyoushi publish and runs their source code
inside the app; it does not scrape the sites itself.

- The extension catalogue is the Keiyoushi `index.pb`. The parser reads the repository's
  nested container (`field 101 -> repeated field 1`) and still accepts the older flat layout.
- Installing/updating downloads the APK, verifies package name, version and size, and hands it
  to Android's `PackageInstaller`. Removing uses the system uninstall dialog.
- Installed APKs are loaded with a delegate-last class loader (child-first below Android 8).
  Extensions resolve the real Mihon API (`source-api` + `core-common`), OkHttp, Jsoup, RxJava
  and Injekt from the host, exactly as they do inside Mihon.
- Only classes declared in the extension manifest (`tachiyomi.extension.class` /
  `tachiyomi.extension.factory`) are instantiated. A broken extension is reported in the
  Sources screen and never stops the other extensions from loading.
- Every browse/search/detail/chapter/page call goes through `MihonRuntime`, serialized per
  source because extension sources are stateful. Images are fetched by the reader with the
  headers the source requires (Referer/User-Agent/cookies).

The Sources screen lists the sources that actually loaded, plus the extension catalogue with
install/update/remove actions, language filters, live search and distinct loading, empty,
offline, error, incompatible and no-usable-source states.

### Known limits

- Extension installation requires the "install unknown apps" permission for MangaOS.
- Some extensions need a WebView (Cloudflare) or a source login; sign-in screens are not
  implemented, so only sources that work without interactive login are usable.
- The size of the extension index and APK downloads is capped (4 MiB / 40 MiB).
