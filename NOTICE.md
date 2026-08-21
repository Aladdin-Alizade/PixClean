# Üçüncü tərəf komponentlər

## MobileFaceNet (`app/src/main/assets/face.tflite`)

Üz tanıma modeli. Tətbiqin içində gəlir — istifadəçi heç nə yükləmir.

| | |
|---|---|
| Fayl | `app/src/main/assets/face.tflite` |
| Ölçü | 5 233 396 bayt |
| SHA-256 | `d8ba40c0127fb8ca9917e8fddc79bbbda063657bc92a496d34da0bc8a760443b` |
| Giriş / çıxış | 112×112×3 float32 → 192 ölçülü vektor |
| Lisenziya | MIT |

Mənbə: [syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing](https://github.com/syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing)
(`app/src/main/assets/MobileFaceNet.tflite`, MIT).

Eyni fayl [NaumanHSA/Android-Face-Recognition-MTCNN-FaceNet](https://github.com/NaumanHSA/Android-Face-Recognition-MTCNN-FaceNet)
deposunda da (MIT) bayt-bayt eyni git blob ilə mövcuddur — hər iki depoda blob `3249c511be86366d`.
Model arxitekturası [sirius-ai/MobileFaceNet_TF](https://github.com/sirius-ai/MobileFaceNet_TF)
(Apache-2.0) əsasındadır.

## ML Kit Face Detection

Üzlərin şəkildə tapılması üçün. Modelləri APK-ya daxildir, internet tələb etmir.
`com.google.mlkit:face-detection`, Apache-2.0 / Google şərtləri.

## TensorFlow Lite

Model icra mühiti. `org.tensorflow:tensorflow-lite`, Apache-2.0.
