# MangaOS

تطبيق أندرويد لقراءة المانجا والمانهوا، مبني بـ Kotlin و Jetpack Compose و Material 3.
المرحلة الحالية هي **المرحلة الأولى**: الهوية البصرية، جميع الشاشات والتفاعلات، الترجمة بثلاث لغات،
وحالات التنزيل والتخزين المحلي على بيانات تجريبية — بدون مصادقة حقيقية وبدون مصادر مانجا خارجية.

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
│   ├── ui/             Snackbar host + shared components
│   └── util/           LocaleManager, Validators, TimeAgo, Formatters
├── data/
│   ├── local/          DemoCatalogue, DataStore settings
│   └── repository/     Demo/in-memory implementations
├── domain/model/       Manga, Chapter, UserProfile, DownloadTask, ...
├── presentation/
│   ├── navigation/     RootNavHost, MainScaffold, Destinations
│   ├── auth/           Login, sign-up, profile, verification
│   ├── latest/ manga/ library/ downloads/ storage/ notifications/
│   ├── settings/ about/ detail/
└── di/ServiceLocator.kt
```

## الثيمات واللغات

- الثيمات: Default، Miko، Neon، Sky — لكل ثيم نسخة فاتحة وأخرى داكنة تتبع النظام افتراضياً.
- الأزرق (`#018AFE` ومشتقاته) لون تمييزي مستخرج من الشعار، وليس لوناً طاغياً.
- اللغات: العربية (RTL)، English، 日本語 (LTR) عبر `res/values`, `values-en`, `values-ja`.

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

للبناء المحلي (اختياري، بدون توقيع):

```bash
./gradlew :app:assembleDebug
./gradlew :app:testReleaseUnitTest
```

## ما هو تجريبي في هذه المرحلة

- لا يوجد Firebase أو Google Sign-In أو أي مصادقة حقيقية؛ التحقق من البريد محلي ووهمي بوضوح.
- لا توجد مصادر مانجا خارجية: كل الأعمال والأغلفة من `DemoCatalogue` وأصول محلية.
- التنزيل محاكى داخل `DemoDownloadRepository` (Coroutines) وينقل الفصل إلى التخزين المحلي.
- الإشعارات داخل التطبيق فقط، وليست إشعارات نظام.
