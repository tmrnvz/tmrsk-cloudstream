# TmrPal eğitim şablonu

Bu klasör, lisanslı veya içerik sahibinden izin alınmış bir yayın sitesi için CloudStream eklentisi geliştirmeyi öğrenmek amacıyla hazırlanmıştır.

- Örnek alan adı: `https://tmrpal.example`
- Örnek bölümler: Yabancı Diziler, Yerli Diziler, Filmler
- Gerçek bir sitenin kataloğuna veya oynatma bağlantılarına bağlanmaz.
- `loadLinks()` bilerek kapalıdır.
- Derlemeye dahil değildir.

## Uyarlama sırası

1. Kullanma izniniz olan sitenin HTML yapısını tarayıcı geliştirici araçlarıyla inceleyin.
2. `.content-card`, `.title`, `img` gibi örnek CSS seçicilerini gerçek seçicilerle değiştirin.
3. Listeleme, arama ve detay sayfalarını ayrı ayrı test edin.
4. Yalnızca yetkili video URL/API uç noktası varsa `loadLinks()` bölümünü uygulayın.
5. Testler tamamlanınca `settings.gradle.kts` içindeki devre dışı listesinden çıkarın.
