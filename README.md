# MiStick Launcher

Lekki, bezpieczny launcher (ekran główny) dla **Xiaomi Mi TV Stick** (MDZ-24-AA, MDZ-27-AA) i innych urządzeń z Android TV 9+.
Wygląd inspirowany Google TV: ciemne tło `#0F0F0F`, niebieski akcent `#1A73E8`, zaokrąglone karty 16:9 z animacją powiększenia.

- 100% Kotlin + AndroidX Leanback, **bez** zewnętrznych SDK, reklam, analityki i śledzenia
- **Brak uprawnienia INTERNET** – launcher nie wysyła żadnych danych
- Wyszukiwanie głosowe przez systemowe rozpoznawanie mowy (launcher nie potrzebuje uprawnienia do mikrofonu)

## Funkcje

| Element | Opis |
|---|---|
| Nagłówek | Zegar i data (aktualizowane automatycznie), przycisk wyszukiwania |
| **Moje Aplikacje** | Aplikacje zainstalowane przez Ciebie (bez systemowych – można zmienić w ustawieniach) |
| **Wszystkie Aplikacje** | Pełna lista aplikacji z ikoną na ekranie głównym, alfabetycznie |
| **Ustawienia** | Ustawienia launchera i skrót do ustawień systemu |
| Wyszukiwarka | Bez rozróżniania wielkości liter i polskich znaków („zdjecia” znajdzie „Zdjęcia”) |
| Auto-odświeżanie | Lista aktualizuje się po instalacji / odinstalowaniu aplikacji |

Przycisk **Wstecz** i **Home** na ekranie głównym przewijają do pierwszego wiersza (launcher nigdy się nie zamyka).

## Struktura projektu

```
app/src/main/
├── AndroidManifest.xml          # HOME + LEANBACK_LAUNCHER, leanback feature, banner, isGame=false
├── java/com/mistick/launcher/
│   ├── MainActivity.kt          # punkt wejścia, hostuje HomeFragment
│   ├── HomeFragment.kt          # BrowseSupportFragment z wierszami aplikacji
│   ├── HomeHeaderView.kt        # nagłówek z zegarem/datą/wyszukiwaniem
│   ├── AppCardPresenter.kt      # karta: baner lub duża ikona + nazwa, animacja fokusu
│   ├── AppItem.kt               # model danych aplikacji
│   ├── AppRepository.kt         # pobieranie listy aplikacji (w tle, korutyny)
│   ├── SearchActivity.kt / AppSearchFragment.kt  # wyszukiwarka
│   ├── SettingsActivity.kt      # ustawienia
│   └── LauncherPrefs.kt         # zapis ustawień
└── res/  (layout, values, values-pl, drawable, mipmap-anydpi-v26)
```

Teksty są po angielsku (`values/strings.xml`) i po polsku (`values-pl/strings.xml`) – język wybiera się automatycznie wg języka systemu.

## Budowanie w Android Studio

1. Zainstaluj **Android Studio** (Hedgehog 2023.1 lub nowsze) – zawiera JDK 17 i Android SDK.
2. `File → Open` → wskaż folder `MiStickLauncher`.
3. Poczekaj na synchronizację Gradle (pobierze Gradle 8.4, AGP 8.2.2, Kotlin 1.9.22, SDK 34).
4. `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
5. Gotowy plik: `app/build/outputs/apk/release/app-release.apk` (lub `debug/app-debug.apk`).

Z linii poleceń:

```bash
./gradlew assembleRelease      # Windows: gradlew.bat assembleRelease
```

> Wersja release jest podpisana kluczem debug, żeby dało się ją od razu zainstalować. Do publikacji podmień `signingConfig` w `app/build.gradle` na własny keystore.
> Wersja debug ma identyfikator `com.mistick.launcher.debug` (można ją mieć obok wersji release).

## Instalacja na Mi Stick przez ADB

1. Na sticku: **Ustawienia → Preferencje urządzenia → Informacje** → kliknij 7× w **Kompilacja**, aby włączyć opcje programisty.
2. **Ustawienia → Preferencje urządzenia → Opcje programisty** → włącz **Debugowanie USB** (oraz, jeśli jest, **Debugowanie sieciowe / ADB przez sieć**).
3. Sprawdź adres IP sticka: **Ustawienia → Sieć i Internet → (Twoja sieć Wi-Fi)**.
4. Na komputerze (z zainstalowanym [platform-tools](https://developer.android.com/tools/releases/platform-tools)):

```bash
adb connect 192.168.1.50:5555          # podaj IP swojego sticka, zaakceptuj okno na TV
adb install -r app/build/outputs/apk/release/app-release.apk
```

5. Ustaw jako domyślny ekran główny – naciśnij **Home** na pilocie i wybierz **MiStick Launcher → Zawsze**.
   Jeśli system nie pyta (niektóre wersje Android TV ignorują inne launchery), uruchom launcher z listy aplikacji albo:

```bash
adb shell cmd package set-home-activity com.mistick.launcher/.MainActivity
```

### Przywrócenie oryginalnego launchera

```bash
adb uninstall com.mistick.launcher
```

## Uwagi

- Na nowszych wersjach Google TV / Android TV 12+ system może wymuszać własny ekran główny po naciśnięciu Home – wtedy launcher działa jako aplikacja z listy lub wymaga ustawienia przez `adb` (patrz wyżej).
- Baner w liście aplikacji TV to wektorowy placeholder (`res/drawable/banner.xml`). Możesz go zastąpić plikiem `res/drawable-xhdpi/banner.png` (320×180 px).
