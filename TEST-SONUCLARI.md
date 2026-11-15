# Video Atölye Pro 0.3 — test sonuçları

15 Kasım 2026 tarihinde yapılan kontroller.

## Derleme ve paket

- `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`: başarılı.
- Android lint: 0 hata; yerelleştirme/deprecated API gibi 7 uyarı.
- APK ZIP bütünlüğü ve imza doğrulaması: başarılı.
- Paket `com.videoatolye.pro`, sürüm `0.3.0` / kod `30`, min SDK 26, hedef SDK 35.
- Önceki 0.2 fotoğraf APK'sıyla aynı imza sertifikası.
- Teslim APK SHA256: `dc7f0457a27fe6fc03e64fe7a53934e0217ced00e3ea45e1c2d879159aae4df5`.

## Android benzetimi — 6/6 geçti

Robolectric 4.14.1, Android API 28, native grafik işleme:

1. Türkçe/Arapça yazı, hız, kırpma ve proje JSON dönüşümü.
2. Farklı uzunluktaki kliplerde taslak süresi ve kaynak sınırları.
3. Geçersiz süre, NaN hız ve yanlış proje biçimini reddetme.
4. Fotoğrafı döndürme/kare kadraj ve parlaklık piksel kontrolü; kaynak pikselin korunması.
5. Fotoğraf düzenleme durumunu geri yükleme.
6. Ses işlemcisi sıfırlandıktan sonra hızın süre hesabının korunması.

## Sanal Android cihazı — 8 senaryo geçti

AOSP Android 9 / API 28, x86_64, yazılımsal CPU/GPU, hızlandırma yok.
İlk toplu koşuda 8 testin 7'si geçti. Müzikli hızlı montajın süre testi başarısızdı.
Müzik parçaları çıktı süresine göre sınırlandıktan sonra başarısız test yeniden geçti.
Yavaş/sessiz çıktı ve bölme/sıralama/geri alma ayrıca yeniden denendi.

| Senaryo | Sonuç |
|---|---|
| Proje JSON ve taslak süreleri, geçersiz aralıklar | Geçti |
| Fotoğraf piksel, kadraj ve durum geri yükleme | Geçti |
| Farklı video boyutu/ses oranı + müzik + yazı + 2× hız + dönüş/ayna | Düzeltme sonrası geçti |
| 0.5× hız ve sessiz MP4 | Geçti; ses izi bulunmadığı doğrulandı |
| Stüdyo/proje açılışı, arayüzden bölme, sıralama, geri alma | Geçti |
| Yerel kare analizi ve 3 taslak üretimi | Geçti |
| Gerçek PNG/JPEG dosyası yazma ve tekrar çözümleme | Geçti |
| Dışa aktarmayı iptal etme | Geçti |

Sanal cihazda üretilen müzikli çıktı ayrıca bilgisayara alınarak FFprobe ve FFmpeg
ile çözümlendi: H.264 görüntü + AAC ses, başlangıç zamanları 0, toplam süre
1.532517 saniye (hedef 1.5 saniye). Video görüntü süresi 1.416667 saniye;
kare/codec sınırları nedeniyle hedef süreyle küçük fark vardır. Ham boyut
480×270 ve -90° yön bilgisi, gösterimde 270×480 dikey kadraj oluşturur.
Yazının görüntüye işlendiği kare üzerinde görüldü. Dosya baştan sona çözümlendi.

## Doğrulamanın sınırları

Android 16 fiziksel telefon, bütün codec/HDR türleri, çok uzun/büyük dosyalar ve
her dosya sağlayıcısı bu ortamda denenmedi. Hızlandırmasız sanal cihazda sistem
arayüzü yanıt vermeme uyarıları ve sanal cihaz kapanmaları yaşandı; tamamlanan
uygulama testleri yukarıda ayrı raporlandı. Bu, bütün cihazlarda hatasızlık veya
profesyonel düzenleyicilerin tüm özelliklerinin hazır olduğu anlamına gelmez.
Eksik gelişmiş işlevler README tablosunda açıkça listelenmiştir.
