<div dir="rtl">

# AGENTS.md — تعليمات لوكلاء البرمجة بالذكاء الاصطناعي

> 🇬🇧 English: [AGENTS.md](AGENTS.md) · التوثيق للبشر: [README.ar.md](README.ar.md) · مواصفة التصميم: [docs/HIG-tvOS.md](docs/HIG-tvOS.md)

هذا الملف هو دليل التشغيل لأي وكيل (agent) يعتمد على نموذج لغوي كبير (LLM) — مثل Claude وGPT وGemini وCursor وCodex… —
ويعمل في هذا المستودع. اقرأه كاملًا قبل أول تعديل.

---

## 0. المهمة

اجعل جهاز Android TV يبدو ويُحَسّ ويتصرّف **تمامًا مثل Apple tvOS**. يحكم المالك على كل
تغيير بسؤال واحد: *هل سيمرّ هذا على أنه من صنع Apple؟* وأيٌّ مما يلي يعني الرفض
الفوري:

- مكوّنات Material Design ‏(`androidx.compose.material*`)، أو تأثيرات التموّج (ripples)، أو مظهر Android المثقل
  بالارتفاعات والظلال (elevation)
- التنسيق العام المعتاد لـ"قوالب الذكاء الاصطناعي": تدرّجات كحلية، وحدود ملوّنة حول كل بطاقة، وبطاقات متماثلة
  بلا تسلسل بصري (hierarchy)، وألوان للزينة فقط
- بيانات مختلَقة (أرقام مزيّفة، أو محتوى مؤقت (placeholder)، أو عبارة "حُدِّث قبل دقيقتين" وهي غير صحيحة)
- أي شيء يتباطأ (lag) على الجهاز المستهدف

## 1. ثوابت غير قابلة للتفاوض

1. **إرشادات HIG مُلزِمة.** يحوي `docs/HIG-tvOS.md` القواعد والأرقام (المنطقة الآمنة (safe area) ‏80/60 pt،
   وسلّم الخطوط (type scale) في tvOS، وعروض الشبكات، وشريط التبويبات (tab bar) بارتفاع 68 pt وعلى بُعد 46 pt من الأعلى، وحالات التركيز (focus) الخمس،
   وتأثير المنظور المتحرك (parallax)، وLiquid Glass). **الوحدات: dp = نقطة tvOS ‏(pt) ÷ 2** ‏(1920×1080 بكثافة 320 dpi).
2. **استخدم عُدّة التصميم (design kit)**، ولا تستخدم قيمًا خامًا أبدًا: `design/Tv.kt` ‏(TvType، TvColors، TvDims، TvShapes،
   TvMotion)، و`design/LiquidGlass.kt`، و`design/Components.kt`. لأي رمز تصميم (token) جديد، أضِفه إلى العُدّة
   (أو إلى كائن الرموز الخاص بالواجهة نفسها) مع تعليق يوضّح السبب.
3. **صفر إطارات في وضع الخمول.** في حالة السكون يجب ألّا تَرسم الشاشة شيئًا. لا `rememberInfiniteTransition`
   على أي واجهة ظاهرة دائمًا، ولا حلقات تعمل مع كل إطار. حرِّك العناصر عبر `graphicsLayer` أو مرحلة الرسم (draw phase)،
   باستخدام نوابض (springs) أو انتقالات (tweens) تُنفَّذ مرة واحدة. تحقّق بأداة gfxinfo ‏(§5).
4. **بيانات حقيقية فقط.** من TMDB وOpen-Meteo وموجز Aerial من Apple وصفحة Instagram العامة.
   عند غياب البيانات، أخفِ العنصر.
5. **اتجاه التخطيط (layout direction).** يعمل الجهاز بلغة عربية (RTL). وواجهات tvOS مُجبَرة على **LTR**
   ‏(`CompositionLocalProvider(LocalLayoutDirection provides Ltr)`). أما لوحة الصلاة العربية
   فهي **RTL** عن قصد وتستخدم الأرقام العربية الهندية (Arabic-Indic digits).
6. **الأسرار.** يوجد رمز TMDB في `local.properties` فقط ← `BuildConfig.TMDB_READ_TOKEN`.
   لا تُودِعه في git أبدًا، ولا تطبعه أبدًا، ولا تُضِف أي مفاتيح أخرى إلى الشيفرة المصدرية.

## 2. التقنيات والأوامر

- Kotlin 2.3، وAGP 8.13، وCompose BOM 2026.05، وMedia3 1.8، وCoil 3، وOkHttp 4. ‏minSdk 26، وtargetSdk 34.
- JDK 21: ‏`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. أي `java` آخر
  في PATH قد يكون Java 8 وسيُعطِّل Gradle.
- البناء + الاختبار: `./gradlew -q :app:testReleaseUnitTest :app:assembleRelease`
- **ثبِّت نسخة APK الخاصة بالإصدار (release) فقط** على الجهاز. نسخة Compose القابلة للتصحيح (debuggable) بطيئة جدًا عليه.
- للتثبيت المتوازي على محاكٍ واحد: الخيار `-PidSuffix=.yourname` يمنحك applicationId منفصلًا.

## 3. خريطة البنية المعمارية

| الحزمة | مسؤولة عن | نقاط الدخول الرئيسية |
|---|---|---|
| `design/` | عُدّة tvOS المشتركة (لا تنسخها وتعدّلها لكل شاشة) | `TvType`، `TvDims`، `LiquidGlass.liquidGlass()` |
| `ui/`، `home/`، `HomeViewModel.kt`، `MainActivity.kt` | مُشغِّل الواجهة الرئيسي (launcher): شريط التبويبات، وشريط العرض الرئيسي (hero carousel)، والصفوف، وشبكة التطبيقات، والتركيز | `HomeScreen`، `HomeViewModel.onDirection/onSelect`، `ContentRepository` |
| `search/` | تطبيق البحث + صفحة العمل (product page) | `SearchActivity` (الإضافتان `tmdb_id` و`tmdb_type` تفتحان صفحة العمل مباشرة) |
| `settings/` | تطبيق الإعدادات | `PagesDevice.kt` / `PagesLauncher.kt` يبنيان كائنات `PageSpec`؛ و`SettingsIcons.kt` يرسم المربّعات |
| `system/` | خدمة إمكانية الوصول (accessibility service) في العملية `:system`: موجِّه زر Home، وطبقات مركز التحكم (Control Center) ومبدِّل التطبيقات (App Switcher) | `SystemUiService`، `HomeKeyRouter` (600 ms للضغطة المطوّلة / 350 ms للضغطة المزدوجة) |
| `aerial/` | شاشة التوقف الجوية (`AerialDreamService`) | `AerialCatalogue`، `AerialPlayback`، `AerialNetwork` (شهادة الجذر من Apple، محصورة بنطاقات محددة) |
| `dashboard/` | شاشة توقف مواقيت الصلاة فوق الفيديو الجوي (`PrayerDashboardDream`) | `DashboardScreen`، `DashboardData` (الطقس + Instagram)، `ArabicText` |
| `prayer/` | الحسابات الفلكية لمواقيت الصلاة دون اتصال | `PrayerTimes.forDay`، `PrayerConfig.BaghdadJafari`، `PrayerSchedule` |
| `tmdb/` | عميل TMDB v3 | `trending`، `onProvider`، `search`، `details`، `logoPath`، `image()` |
| `prefs/` | `LauncherPrefs` (كائن `StateFlow` واحد على مستوى العملية) | `LauncherPrefs.get(ctx).update { … }` |

التنقّل في الشاشة الرئيسية **آلة حالات خالصة (pure state machine)** في `HomeViewModel` + `home/HomeNav.kt`. ولا يُستخدَم
نظام التركيز في Compose هناك. تصل المفاتيح عبر `onPreviewKeyEvent` ← `KeyRouter` ← الـ VM. أما الإعدادات
والبحث فيستخدمان نظام التركيز في Compose ‏(`focusRequester`، `clickable`).

## 4. قواعد التصميم عمليًا

- **التركيز = تكبير + رفع + إضاءة** (قاعدة بيضاء، ونص أسود، وظل). ولا يكون باللون وحده أبدًا.
  عند الضغط، انخفاض قصير (حالة "highlighted" في HIG).
- **Liquid Glass** ‏(`liquidGlass()`) لعناصر التنقّل، وشريط التبويبات، ومركز التحكم، والأزرار
  في حالة الخمول. الأزرار التي عليها التركيز تتحوّل إلى الأبيض.
- **الشبكات**: شبكة تطبيقات من 5 أعمدة بمقاس 160×96 dp، وفراغات 20 dp بينها، وهوامش جانبية 40 dp.
  صفوف الملصقات (poster rows) تستخدم عرض الأعمدة السبعة. اترك مساحة لتكبير التركيز حتى لا يتداخل العنصر
  الذي عليه التركيز مع جيرانه أبدًا.
- **الخطوط**: SF Pro / SF Arabic عبر `TvType` (أحجام وأوزان وتباعد أحرف مطابقة تمامًا لـ HIG). الأرقام
  الكبيرة تستخدم SemiBold/Bold. يظهر النص الثانوي أبيضَ بشفافية أقل (78/58/40 %)،
  ولا يكون أبدًا رماديًا على رمادي.
- **الحركة**: نوابض من `TvMotion`. التبديل بين التبويبات = تلاشٍ للخارج ← تلاشٍ للداخل لصفحات
  مُركَّبة (composed) مسبقًا؛ وكبسولة التبويب تنزلق. لا شيء يتكرر في حالة السكون.
- قبل أي عمل بصري، نفِّذ عملية `anti-slop-design` إن كانت متاحة في بيئة الوكيل (harness) لديك: حدِّد
  اتجاهًا، وثبِّت الرموز (tokens)، وتحقّق مقابل لقطات الشاشة.

## 5. سير العمل على الجهاز (الجهاز المستهدف)

```bash
S=<adb serial or ip:5555>
adb -s $S install -r app/build/outputs/apk/release/app-release.apk
# Reinstall unbinds the accessibility service — always re-enable:
adb -s $S shell 'settings put secure enabled_accessibility_services com.hamad.tvhome/com.hamad.tvhome.system.SystemUiService; settings put secure accessibility_enabled 1'
adb -s $S shell input keyevent KEYCODE_HOME
adb -s $S exec-out screencap -p > /tmp/shot.png      # then look at it
```

فحص الأداء (إلزامي لأي تغيير في الواجهة):

```bash
adb -s $S shell 'dumpsys gfxinfo com.hamad.tvhome reset; sleep 5; dumpsys gfxinfo com.hamad.tvhome | grep -E "Total frames|Janky|90th|99th"'
# idle target: ~0 frames · navigation target: janky < 15 %, p90 ≤ 20 ms
```

فخاخ تبدو كأخطاء برمجية لكنها ليست كذلك:

| الفخ | ماذا تفعل |
|---|---|
| الأمر `am force-stop com.hamad.tvhome` يُطفئ إمكانية الوصول بصمت | أعِد تفعيلها كما في الأعلى |
| خدمة إمكانية الوصول مفعّلة لكن `dumpsys accessibility` يُظهر `services:{}` | ربط قديم عالق (stale binding): نفِّذ `adb reboot` |
| الأمر `adb shell input keyevent` لا يصل أبدًا إلى موجِّه زر Home | المفاتيح المُحقَنة تتجاوز مُرشِّح إمكانية الوصول (a11y). اختبر الضغطة المطوّلة/المزدوجة على Home بجهاز التحكم الحقيقي، أو باستخدام `uinput` على المحاكي |
| لقطات شاشة التوقف تُظهر الفيديو أسود | طبقة الفيديو العتادية؛ لا يُلتقَط إلا ما فوقها. استخدم محاكيًا لرؤية إطارات الفيديو |
| الساعة تعرض عام 2017 بعد انقطاع الكهرباء | `settings put global ntp_server 162.159.200.123` |
| الأمر `am start` من الـ shell لا يفتح `SettingsActivity` / `SearchActivity` | هذان النشاطان غير مُصدَّرين (not exported). افتحهما من شريط تبويبات الشاشة الرئيسية (أيقونة البحث / الترس) |

## 6. قواعد العمل بعدة وكلاء (تعلّمناها بالطريقة الصعبة)

- اعمل في شجرات عمل git معزولة (worktrees)؛ **كل وكيل يملك ملفات أو حزمًا محددة صراحةً** ولا يعدّل غيرها.
- **قبل دمج أي فرع: `git diff --stat master <branch>`.** فقد أودع أحد الوكلاء ذات مرة
  شجرة عمل قديمة، فأعاد بصمت 38 ملفًا من عمل الوكلاء الآخرين إلى حالتها السابقة.
- لا تُلغِ تثبيت أي حزمة على جهاز المالك ولا تعطّلها ولا تحذفها ما لم يُطلَب ذلك صراحةً.
- وكيل واحد فقط يتحكم بالجهاز الحقيقي في كل مرة؛ والبقية يستخدمون محاكيًا مع `-PidSuffix`.
- قد تكون هناك جلسة حيّة أخرى تعدّل المستودع نفسه. إذا تغيّرت ملفات قبل دقائق، فنسِّق
  قبل أن تكتب.

## 7. الاختبار

- توجد اختبارات الوحدة (unit tests) في `app/src/test/…` ‏(JUnit 4). أي تغيير في المنطق الخالص يحتاج إلى اختبارات: التنقّل،
  والتمرير، والتنسيق، والحسابات الفلكية، والتحليل (parsing).
- تُقارَن مواقيت الصلاة بـ Aladhan ‏(`method=0`) بفارق لا يتجاوز دقيقة واحدة. إذا غيّرت
  الزوايا أو المدينة، فحدِّث القيم المرجعية في `PrayerTimesTest`.
- يُتحقَّق من الواجهة على الجهاز بلقطة شاشة إضافة إلى أرقام gfxinfo، وتُذكَر بأمانة في ملخّصك.

## 8. وصفات جاهزة

| المهمة | أين |
|---|---|
| إضافة صف إلى الشاشة الرئيسية | `home/ContentRepository.kt` (البيانات) + `ui/Rows.kt` (العرض)؛ والتزم بعروض الصفوف وفق HIG |
| إضافة صف/صفحة إلى الإعدادات | `settings/Pages*.kt` ← أعِد `Item`/`Header` داخل `PageSpec`؛ وإجراءات حقيقية تعمل فقط |
| تغيير مدينة الصلاة/طريقة الحساب | `prayer/PrayerTimes.kt` ← `PrayerConfig` جديد؛ وحدِّث الاختبارات |
| تغيير حساب Instagram | `dashboard/PrayerDashboardDream.kt` ← `INSTAGRAM_HANDLE` |
| شاشة توقف جديدة | أضِف `DreamService` + عنصر `<service>` في الـ manifest مع `BIND_DREAM_SERVICE` + ملف `xml/…_dream.xml` |
| مربّع جديد في مركز التحكم | `system/SystemControls.kt` (الإجراء) + `system/ControlCenter.kt` (المربّع)؛ وأخفِه إذا لم يُعثَر على ما يعالج الـ intent الخاص به |

## 9. الإيداعات (commits) والتقارير

- إيداعات وفق Conventional Commits: ‏`feat:`، `fix:`، `perf:`، `docs:`، `chore:`؛ وموضوع واحد لكل إيداع.
- يجب أن يكون تقريرك النهائي قصيرًا وسهل المسح بالعين: ما الذي تغيّر، وما الذي تحقّقت منه (أرقام
  ولقطات شاشة)، وما الذي لا يزال معطّلًا. لا تدّعِ أبدًا إجراء فحص لم تُنفِّذه.

</div>
