# tmrsk CloudStream Eklentisi

İlk sürüm iki sağlayıcı içerir:

- **Canlı Yayın:** IPTV-org Türkiye listesindeki kamuya açık internet yayınlarını dinamik okur.
- **PuhuTV:** PuhuTV'nin resmî web kataloğunu, bölüm listesini ve sunduğu oynatma bağlantılarını kullanır.

## Derleme

```bash
./gradlew tmrsk:make
```

Oluşan eklenti paketi `builds` dizinine yazılır. Proje CloudStream TestPlugins
şablonunu kullanır ve GitHub Actions ile bir eklenti deposu olarak yayımlanabilir.

## Notlar

- Kaynakların kullanım koşulları geçerlidir.
- DRM, üyelik veya coğrafi kısıtlamalar aşılmaz.
- Sağlayıcıların web/API yapısı değişirse eklenti güncelleme gerektirebilir.
