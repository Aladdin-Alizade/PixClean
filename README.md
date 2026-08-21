# PixClean

Android qalereya təmizləyicisi: **eyni** və **oxşar** şəkilləri tapır, üzləri **şəxslərə görə**
qruplaşdırır. Hər şey telefonun içində işləyir — nə şəkil, nə də üz vektoru cihazdan çıxmır.

---

## Nə edir

| | |
|---|---|
| **Eyni fayllar** | Bayt-bayt eyni olan nüsxələr. Səhv ehtimalı sıfırdır. |
| **Oxşar şəkillər** | Yenidən sıxılmış, kiçildilmiş, EXIF ilə çevrilmiş nüsxələr — WhatsApp/Telegram-dan gələn variantlar. |
| **Şəxslər** | Hər şəkildəki üzlər tapılır, vektora çevrilir və eyni adam bir qrupa yığılır. |
| **Əməliyyatlar** | Solda saxlanan nüsxə, sağda dublikatlar. Seçib zibilə atmaq, həmişəlik silmək və ya başqa alboma köçürmək. |
| **Qovluq seçimi** | İstənilən qovluğu (path-i) seçib yalnız onu analiz edə bilərsiniz — alt qovluqları ilə birlikdə. |

Minlərlə şəkil normaldır: nəticələr SQLite-da saxlanılır, ikinci taramada yalnız **yeni və dəyişmiş**
şəkillər analiz olunur.

---

## Hansı şəkillər analiz olunur

İki səviyyəli seçim var, hər ikisi **Tənzimləmələr**-dədir:

**1. Qovluq (path) seçimi.** «Qovluq seç» ilə istənilən qovluğu göstərirsiniz — məsələn
`/storage/emulated/0/DCIM/Camera` və ya yaddaş kartındakı bir qovluq. Seçilən qovluq və onun
bütün alt qovluqları analiz olunur, qalan hər şey kənarda qalır. Bir neçə qovluq əlavə edə,
istənilən vaxt «Bütün qalereya»ya qayıda bilərsiniz.

Bu, sadəcə süzgəc deyil: seçilmiş qovluq **birbaşa gəzilir**, ona görə MediaStore-un
indeksləmədiyi fayllar da tapılır — `.nomedia` işarəsi olan qovluqlar, yenicə kopyalanmış
qovluqlar, çıxarıla bilən yaddaş. Eyni fayl həm MediaStore-da, həm də seçilmiş qovluqda
görünürsə, bir dəfə sayılır (yol üzrə müqayisə) — əks halda şəkil öz-özünün dublikatı kimi
görünərdi.

**2. Albom açarları.** Qalereyadakı albomların siyahısı fayl sayı ilə birlikdə göstərilir;
söndürülmüş albom hash-lənmir, üz axtarışına düşmür və qruplarda görünmür.

---

## Dəqiqlik necə təmin olunur

### Eyni fayllar — üç mərhələli süzgəc

```
ölçü → ilk 64 KB SHA-256 → tam SHA-256
```

Ölçüsü unikal olan fayl heç vaxt oxunmur. Ölçüsü üst-üstə düşənlərin yalnız ilk 64 KB-ı oxunur.
Tam hash isə yalnız həm ölçüsü, həm də başlanğıcı eyni olanlar üçün hesablanır. Praktikada bu,
10 000 şəkillik qalereyada bir neçə yüz faylın oxunması deməkdir.

### Oxşar şəkillər — üç müstəqil imza razılaşmalıdır

| İmza | Necə hesablanır | Nəyə davamlıdır |
|---|---|---|
| **pHash** (64 bit) | 32×32 boz müstəvinin DCT-si, sol-yuxarı 8×8 blok medianla müqayisə | yenidən sıxılma, ölçü dəyişməsi, işıqlanma |
| **dHash** (64 bit) | 9×8 müstəvidə qonşu piksel qradiyenti | pHash-dən **fərqli** şəkildə səhv edir |
| **Rəng imzası** (48 bayt) | 4×4 şəbəkənin orta RGB-si, müqayisədə orta çıxılır | qlobal işıq/ağ balans fərqi silinir, **quruluş** müqayisə olunur |

Qrup yaranması üçün hər üçü razılaşmalıdır. Bir hash-in təsadüfi uyğunluğu tək başına heç nə etmir.
Əlavə mühafizələr:

- **Aspekt nisbəti**: 16:9 ilə 1:1 şəkil eyni sayılmır.
- **Düz kadrlar**: rəng yayılması çox az olan (tam qara/ağ) şəkillər ümumiyyətlə müqayisəyə buraxılmır —
  onların hash-i mənasızdır və məhz belə şəkillər «yalançı dublikat» yaradır.
- **EXIF çevrilməsi**: bayraqla çevrilmiş şəkil ilə fiziki çevrilmiş nüsxə eyni qrupa düşür.
- **Zəncir effekti**: qrup qurulandan sonra hər üzv saxlanan nüsxə ilə **yenidən** yoxlanılır,
  belə ki A~B, B~C, amma A≁C halında qrup avtomatik bölünür.

Sürət üçün O(N²) müqayisə yoxdur: hər hash 8 baytlıq zolağa bölünür və Hamming məsafəsi ≤ 7 olan
hər cüt ən azı bir zolaqda üst-üstə düşür (göyərçin yuvası prinsipi). Yalnız həmin namizədlər
müqayisə olunur.

> **Recall haqqında dürüst qeyd:** zolaq indeksi pHash **və ya** dHash üzrə məsafəsi ≤ 7 olan bütün
> cütləri tapmağa zəmanət verir. Hər iki hash-in eyni anda 8-dən çox fərqləndiyi nadir hallar
> (məsələn, güclü kadrlaşdırma) buraxıla bilər. Bu, şüurlu seçimdir: yanlış qrup göstərmək
> istifadəçi üçün buraxılmış qrupdan daha bahalıdır.

### Üzlər

```
ML Kit ilə üz tapılır → keyfiyyət süzgəci → gözlərə görə düzləndirmə → 112×112 → vektor → klasterləmə
```

- **Keyfiyyət süzgəci**: 40 pikseldən kiçik üzlər, |yaw| > 45°, |roll| > 30° buraxılır.
- **Düzləndirmə** modeldən sonra ən vacib addımdır: eyni adamın iki fərqli baş bucağındakı
  düzləndirilməmiş kəsimi iki fərqli adam kimi görünür. Gözlər ArcFace şablonundakı yerlərə oturdulur.
- **Klasterləmə** üç mərhələdir: keyfiyyətə görə səpələmə → sürüşmüş mərkəzlərin birləşdirilməsi →
  hər üzün yenidən ən yaxın mərkəzə təyinatı. `O(N·K)` — 20 000 üz saniyələr çəkir, dəqiqələr yox.
- Verdiyiniz **adlar yenidən qruplaşdırmadan sonra da qalır**: yeni klaster üzvlərinin çoxluq
  səsverməsi ilə köhnə şəxsi miras alır.

---

## Üz tanıma: yaxşı işləməsi üçün

### 1. Model artıq içindədir

Üz tanıma modeli (**MobileFaceNet**, 5 MB) APK ilə birlikdə gəlir. İstifadəçi heç nə yükləmir,
heç nə seçmir — tətbiq quraşdırılan kimi tam tanıma rejimində işləyir.

Modelin mənbəyi, lisenziyası və SHA-256-sı [NOTICE.md](NOTICE.md) faylındadır (MIT).

İstəsəniz **Tənzimləmələr → Üz tanıma modeli → Öz modelinizi yükləyin** ilə başqa `.tflite`
faylı ilə əvəz edə bilərsiniz. Fayl qəbul edilməzdən əvvəl həqiqətən yüklənərək yoxlanılır;
tutmasa tətbiq öz modelinə qayıdır. Giriş ölçüsü normalizasiyanı özü müəyyən edir: 112×112
girişlərdə ArcFace üsulu, 160×160 girişlərdə FaceNet üsulu.

### 2. Simptoma görə tənzimləmə

| Nə görürsünüz | Nə edin |
|---|---|
| Eyni adam 3-4 qrupa bölünüb | Üz həssaslığını **Geniş** edin; sonra qrupları əl ilə **Birləşdir** |
| İki fərqli adam bir qrupdadır | Həssaslığı **Sərt** edin |
| Qrup şəkillərindəki adamlar tapılmır | Üz axtarışı dəqiqliyini **Yüksək** edin (yenidən tarama tələb edir) |
| Çox sayda təsadüfi tək üz | «Ən azı neçə üzü olan qrup» → **2** və ya **3** |
| Tarama çox uzun çəkir | Dəqiqliyi **Normal**, taranacaq qovluğu daraldın |

Həssaslığı dəyişəndə **şəkillər yenidən analiz olunmur** — vektorlar bazadadır, yalnız
klasterləmə təkrarlanır, bu isə saniyələr çəkir. Yalnız *dəqiqlik* açarı və *model* dəyişikliyi
tam yenidən tarama tələb edir.

### 3. Adları verin

Qrupa ad verəndə ad həmin üzlərə bağlanır və **yenidən qruplaşdırmadan sonra da qalır**
(yeni klaster üzvlərinin çoxluq səsverməsi ilə). Ona görə əvvəlcə adları verin, sonra
həssaslıqla oynayın.

### Nə gözləmək olar

MobileFaceNet ilə eyni adamın aydın, önə baxan şəkilləri etibarlı qruplaşır. Profil,
çox qaranlıq və ya çox kiçik üzlər keyfiyyət süzgəcindən keçmir — bu, qəsdəndir: pis üzü
qrupa salmaqdansa kənarda saxlamaq daha az ziyandır. Uşaqlıq/böyüklük fərqi kimi hallarda
bir adam iki qrupa düşə bilər; **Birləşdir** funksiyası bunun üçündür.

## Telefona quraşdırmaq

APK-dan başqa **heç nə lazım deyil**. Üz aşkarlama modelləri (ML Kit blazeface + landmark,
~4 MB) APK-nın içindədir, ona görə ilk açılışda heç nə endirilmir və internet tələb olunmur.

| Telefon | Fayl |
|---|---|
| Bilmirsinizsə / linkdən endirirsinizsə | `pixclean.apk` (~30 MB, hər telefonda işləyir) |
| Müasir telefonlar (64-bit ARM) | `pixclean-arm64-v8a.apk` (~22 MB) |
| Köhnə 32-bit cihazlar | `pixclean-armeabi-v7a.apk` (~18 MB) |

APK-nın yarıdan çoxu native kitabxanadır (ML Kit üz detektoru + TensorFlow Lite) və native kod
hər prosessor arxitekturası üçün ayrıca kompilyasiya olunur. x86 kitabxanaları buraxılışdan
çıxarılıb — onlar emulyator, Chromebook və Windows Subsystem for Android üçündür, telefonda
işə yaramır və universal faylın 52 MB-ının 27 MB-ını tuturdular.

Play Store-dan gəlmədiyi üçün telefon «naməlum mənbə»yə icazə istəyəcək — APK-nı hansı
tətbiqlə açırsınızsa (brauzer, fayl meneceri), ona bir dəfə icazə verin.

Açılanda tətbiq **şəkil icazəsini özü soruşur** — «Bütün şəkillərə icazə ver» seçin, çünki
dublikat tapmaq üçün qalereyanın hamısını görmək lazımdır. «Yalnız seçilmişlər» kifayət etmir.
Bildiriş icazəsi ilk tarama başlayanda soruşulur və məcburi deyil — yalnız gedişat bildirişi üçündür.

**İnternet icazəsi APK-da yoxdur.** ML Kit onu tranzitiv gətirirdi, manifestdən silinib:

```
uses-permission: READ_MEDIA_IMAGES
uses-permission: READ_EXTERNAL_STORAGE (maxSdkVersion 32)
uses-permission: WRITE_EXTERNAL_STORAGE (maxSdkVersion 28)
uses-permission: POST_NOTIFICATIONS
uses-permission: FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC
```

Yəni «heç nə cihazdan kənara çıxmır» sadəcə vəd deyil — əməliyyat sistemi bunu təmin edir.

Üz tanıma modeli də APK-nın içindədir — ayrıca heç nə yükləmək lazım deyil.

---

## Build və quraşdırma

Tələblər: JDK 21, Android SDK 36.

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Testlər:

```bash
./gradlew testDebugUnitTest
```

Release (ABI-yə görə bölünür — telefon ~17 MB endirir, 55 MB yox):

```bash
./gradlew assembleRelease
```

---

## Buraxılış: GitHub Releases

**Railway deyil, GitHub.** Railway server hostinq üçündür — bu tətbiqin serveri yoxdur və
olmamalıdır. Bütün analiz telefonda gedir.

`.github/workflows/release.yml` **yalnız teq push ediləndə** işə düşür: testləri qaçırır,
APK-ları imzalayır və GitHub Release-ə yükləyir. Yəni heç bir commit-in cavabdeh olmadığı
buraxılış mümkün deyil.

### Bir dəfəlik qurulum

**1. İmzalama açarı yaradın.** Bu açar tətbiqin kimliyidir — itirsəniz, mövcud
quraşdırmaların üstünə yeni versiya çıxara bilməzsiniz. Şifrəni özünüz seçin:

```bash
keytool -genkeypair -v -keystore pixclean-release.jks \
  -alias pixclean -keyalg RSA -keysize 2048 -validity 10000
```

Fayl **repoya girmir** (`.gitignore`-dadır). Yedəyini ayrıca saxlayın.

**2. GitHub Secrets əlavə edin** (repo → Settings → Secrets and variables → Actions):

| Secret | Dəyər |
|---|---|
| `KEYSTORE_BASE64` | `base64 -i pixclean-release.jks \| pbcopy` |
| `KEYSTORE_PASSWORD` | keystore şifrəsi |
| `KEY_ALIAS` | `pixclean` |
| `KEY_PASSWORD` | açar şifrəsi |

**3. Lokal build üçün** `local.properties`-ə əlavə edin (bu fayl da repoya girmir):

```
KEYSTORE_FILE=/tam/yol/pixclean-release.jks
KEYSTORE_PASSWORD=...
KEY_ALIAS=pixclean
KEY_PASSWORD=...
```

Açar olmasa da release build işləyir — sadəcə `*-unsigned.apk` çıxır və CI aydın xəta ilə dayanır.

### Hər buraxılış

Teq `app/build.gradle.kts`-dəki `versionName` ilə **üst-üstə düşməlidir**, yoxsa CI dayanır:

```bash
git tag v1.0.0 && git push --tags
```

Nəticə — dəyişməyən yükləmə linki:

```
https://github.com/<owner>/<repo>/releases/latest/download/pixclean.apk
```

Release-ə üç fayl qoyulur: `pixclean.apk` (universal, hər telefon), `pixclean-arm64-v8a.apk`
(~17 MB) və `pixclean-armeabi-v7a.apk`.

## Bilinən məhdudiyyətlər

- **Güclü kadrlaşdırma** (şəklin yarısı kəsilmiş nüsxə) oxşar kimi tapılmaya bilər — pHash
  kadrlaşdırmaya davamlı deyil.
- **Güzgü əksi** olunmuş nüsxələr ayrı şəkil sayılır.
- **Videolar** analiz olunmur, yalnız şəkillər.
- Üz qruplaşdırması **model olmadan** yalnız məhdud şəraitdə etibarlıdır (yuxarıdakı cədvələ baxın).
- Android 14+ «yalnız seçilmiş şəkillər» icazəsi ilə işləmir — dublikat tapmaq üçün bütün qalereya lazımdır.
  Hansı albomların analiz olunacağını **Tənzimləmələr → Hansı qovluqlar taransın** bölməsindən seçin.
- Başqa alboma **köçürülmüş** dublikatlar silinmir — növbəti taramada yenidən dublikat kimi görünəcəklər.
  Köçürdüyünüz albomu tənzimləmələrdən söndürsəniz, siyahıdan çıxacaq.

---

## Layihə quruluşu

```
core/      ScanEngine (bütün pipeline), parametrlər, icazələr
data/      SQLite, MediaStore oxuma, silmə/köçürmə əməliyyatları
dup/       SHA-256 mərhələləri, pHash/dHash/rəng imzası, LSH + union-find
faces/     ML Kit detektoru, düzləndirmə, TFLite/HOG vektorlaşdırma, klasterləmə
ui/        Compose ekranları; ui/theme/Color.kt — bütün rənglərin yeganə mənbəyi
service/   Uzun tarama üçün foreground service
```
