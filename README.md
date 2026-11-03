# Video Atölye Pro — Fotoğraf Stüdyosu 0.2

Android 8 ve üzeri için cihazda çalışan kişisel fotoğraf/video düzenleyici.
Fotoğraf stüdyosunun yeni sürümü `PhotoEditorActivity` üzerinden açılır.
Galerideki kaynak dosya değiştirilmez; düzenleme tarifi ve kaynak kopyası uygulamanın özel alanında tutulur.

## Fotoğraf araçları

- Pozlama, parlaklık, kontrast, parlak alanlar, gölgeler.
- Doygunluk, sıcaklık, yeşil/magenta ton, siyah beyaz, ölçülü renk görünümleri, kenar karartma.
- Elle taşınabilen/boyutlandırılabilen serbest kadraj; 1:1, 4:5, 9:16, 16:9, 3:2 oranları.
- 90 derece döndürme; yatay/dikey çevirme; EXIF yönünü okuyarak açma.
- İsteğe bağlı, satır kaydırılan yazı; renk, boyut ve konum.
- 40 adımlık geri/ileri alma; basılı tutarak orijinal karşılaştırma; yakınlaştırma ve kaydırma; RGB histogram.
- Son fotoğrafın otomatik yerel taslağı. Uygulama yeniden açıldığında düzenleme tarifi geri gelir; geri alma geçmişi yeniden başlatılır.
- JPEG (50–100 kalite) veya PNG; kaynak çözünürlüğü (16 MP sınırı), en fazla 4096/2048 px. Kaynak büyütülmez.
- Yeni belge olarak dışa aktarma; Android paylaşım ekranı. Başarısız dışa aktarmada yarım belgeyi kaldırma denenir.

Görüntü üretimi, yüz/beden değiştirme, nesne silme veya arka plan üretme yoktur.
RAW geliştirme, HDR dışa aktarma, profesyonel renk yönetimi, katman/maskeler, rötuş ve yapay zekâ **uygulanmadı**.
PNG kayıpsız sıkıştırma kullansa da renk/kadraj değişiklikleri pikselleri değiştirir. EXIF/GPS çıktıya aktarılmaz.
Önizleme en fazla 1280 px'dir; dışa aktarma kaynak dosyayı yeniden çözer. Büyük dosyalarda bellek yetersizse hata gösterilir; daha küçük boyut seçilmelidir.
Tek fotoğraf taslağı vardır; yeni fotoğraf açılınca önceki taslak değişir. Dosya boyutu sınırı 128 MB'dır.

## Telefonda kullanım

1. APK'yı kur ve ana sayfadan **Fotoğraf Stüdyosu**'nu aç.
2. **Aç** ile fotoğraf seç. Araç sekmeleri yatay kaydırılabilir; araç paneli dikey kaydırılabilir.
3. Kaydırıcı değerinin üstüne dokunarak sayısal değer girebilirsin.
4. **Kadraj → Kadrajı elle ayarla**: köşelerden boyutlandır, ortadan taşı, Uygula'ya dokun.
5. **Önce / sonra** düğmesini basılı tutarak orijinali gör; bırakınca düzenlemeye dön.
6. **Kaydet** ile biçim/boyut/kalite ve hedef klasör seç. Ardından paylaşabilirsin.

## Derleme ve test

JDK 17, Android SDK 35, Gradle 8.9; `gradle testDebugUnitTest assembleDebug`.
GitHub Actions aynı doğrulamayı çalıştırır ve debug APK dosyasını artifact olarak sunar.
APK debug/test sürümüdür. Cihaz/üretici farklılıkları nedeniyle gerçek telefonda kabul testi gereklidir.

### Doğrulanan kontroller — 3 Kasım 2026

- `assembleDebug`: başarılı; APK imzası `apksigner verify` ile doğrulandı.
- 17 Robolectric testi: 17 başarılı, 0 hata. Android 9 üzerinde piksel/işlem testleri; Android 15 üzerinde ekran açılışı.
- Piksel koruma, seçilen bölgeden kırpma, döndürme/ayna, renk işlemleri, PNG yeniden okuma, EXIF yönü, küçültme ve bozuk dosya kontrolü.
- Fotoğraf içe aktarma → düzenleme → JPEG dışa aktarma ve taslak ayar kaydı akışı.
- 393 × 851 ölçüsünde stüdyo ekranı render edilip incelendi.
- `lintDebug`: hata yok. Dil kaynakları ve sabit portre yönü gibi uyarılar mevcut.
- Bunlar otomatik yerel doğrulamalardır; gerçek telefonda kullanım ve üretici dosya sağlayıcıları doğrulanmadı.

### Gerçek telefon kabul listesi (kullanıcı testi bekliyor)

- Küçük PNG, EXIF yönlü JPEG, 12 MP fotoğraf ve bozuk dosya seçimi.
- Her ışık/renk aracı, geri/ileri, sıfırlama ve önce/sonra.
- Elle kadraj ve bütün oranlar; döndürme/ayna; iki parmak yakınlaştırma.
- Türkçe ve çok satırlı yazının kaydedilen görüntüde görünmesi.
- JPEG/PNG dışa aktarma, dosyanın galeri/dosya yöneticisinde yeniden açılması, paylaşılması.
- Kaydetme ekranından vazgeçme; depolama yetersizliği; çok büyük dosyada anlaşılır hata.
- Uygulamayı kapatıp açınca son fotoğrafın ve ayarların geri gelmesi.
- Kaynak dosyanın değişmeden kalması.

Video ve ses alanları önceki geliştirme kodudur; bu sürümde fotoğraf çalışmasına dahil edilmemiştir.
Video otomatik önemli an seçimi ve farklı otomatik kurgu seçenekleri henüz uygulanmamıştır.
