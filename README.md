# Video Atölye Pro 0.3 — Android

Telefon üzerinde çalışan, filigransız yerel fotoğraf ve video düzenleme sürümü.
Bu sürüm profesyonel düzenleyicilerdeki bütün özellikleri içermez.

## Kullanım

1. APK'yı Android 8 veya daha yeni telefona kur.
2. **Video ve çoklu montaj stüdyosu → Video ekle** ile klipleri seç.
3. Zaman çizelgesinden bir klip seç; süre aralığını, sırasını, hızını, rengini,
   sesini, döndürmesini ve yazısını düzenle. Önizlemedeki yerden ikiye bölebilirsin.
4. Müzik seç, sesini ayarla. Müzik montaj süresine kadar döngüye alınır.
5. Kadraj ve 480p/720p/1080p yüksekliğini seç, **MP4 oluştur ve kaydet**.
   İşlem sürerken uygulamayı açık tut. Vazgeçersen **İptal et**.
6. Fotoğraf için ana ekrandaki ayrı stüdyoyu kullan. PNG ve JPEG kaydı vardır.

## İşlevler ve sınırlar

| Alan | Bu sürüm |
|---|---|
| Video montaj | Tek video sırası; ekle, sil, çoğalt, sırala, böl, saniyeyle kırp |
| Video işleme | 0.25–4× gerçek hız, ses seviyesi/sessiz, 90° döndürme, ayna, parlaklık/doygunluk |
| Yazı | Klip boyunca sabit çok satırlı metin; otomatik konuşma çözümü yok |
| Müzik | Bir müzik katmanı, döngü ve seviye; otomatik ducking/beat sync yok |
| Kadraj | 16:9, 9:16, 1:1, 4:5; orijinal sığdırılır, boş alan oluşabilir |
| MP4 | Media3 Transformer ile H.264/AAC; yeniden kodlama cihaz codec desteğine bağlı |
| Hızlı kurgu | Işık, kenar detayı ve kare değişimi puanından 3 taslak; anlamsal AI yok |
| Yazarak ayarla | Dikey/yatay/kare, sessiz, siyah beyaz, 2x, 0.5x, N saniye komutları |
| Proje | Otomatik yerel kayıt, JSON içe/dışa aktarma, 30 adım geri al/yinele |
| Kapak | Seçilen klibin kaynak karesini JPEG kaydetme; video efektleri bu kareye uygulanmaz |
| Fotoğraf | Yön/ayna, merkez kadraj, parlaklık/kontrast/doygunluk/sıcaklık, filtreler, kenar karartma, yazı, karşılaştırma, geri al/yinele |
| Fotoğraf boyutu | Belleğe göre örneklenir, uzun kenar en fazla 4096 px; önizleme en fazla 1280 px; RAW yok |
| İleri işlevler | Serbest çok katmanlı zaman çizelgesi, geçiş/crossfade, nesne silme, takip/maske, gelişmiş eğriler ve bulut AI yok |

Kaynak dosyalar değiştirilmez. Proje JSON'u medyayı içermez; videoları ve müziği
taşır/silersen kaynak erişimi kaybolabilir. Cihazlar arasında JSON taşımak medya
izinlerini taşımaz. Kaynak seçimini yeniden yapman gerekebilir. Önizleme seçili
klibindir; müzik dışa aktarmada eklenir. HDR görüntüler SDR'ye ton eşlenir.

## Derleme

Java 17, Gradle 8.10.2, Android SDK 35 ve build-tools 34+ gerekir:

```sh
gradle assembleDebug lintDebug assembleDebugAndroidTest
```

`VIDEO_ATOLYE_KEYSTORE` ortam değişkeni önceki sürümün özel debug anahtarına
işaret ediyorsa APK aynı anahtarla imzalanır. Anahtar bu depoya dahil değildir.
Başka anahtarla imzalanmış APK mevcut uygulamanın üzerine güncellenemez.
İmza değişirse uygulamayı kaldırmadan önce proje dosyanı dışa aktar.

## Test

`app/src/test` altında 6 Android benzetim testi, `app/src/androidTest` altında
Android cihazında çalışan 8 senaryo bulunur. Sonuçlar `TEST-SONUCLARI.md` dosyasında.
Kapsam:
proje doğrulama/taslak süresi, fotoğraf renk/kadraj ve durum geri yükleme,
farklı boyut/ses biçimindeki iki klibin müzik+yazı+hızla dışa aktarımı,
yavaş/sessiz çıktı, stüdyo açılışı, kare analizi, PNG/JPEG kayıt ve iptal.
Test varlıkları FFmpeg ile üretilmiş sentetik görüntü ve seslerdir.

APK imza doğrulaması ve lint, gerçek cihaz codec ve izin testinin yerine geçmez.
Android 16 telefondaki son davranış kullanıcı tarafından ayrıca denenmelidir.
