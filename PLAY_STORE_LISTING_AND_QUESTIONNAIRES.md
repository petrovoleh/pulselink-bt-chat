# Повний пакет для публікації PulseLink у Google Play Console

Цей документ містить **усі готові тексти, посилання, файли та відповіді на всі опитувальники Google Play Console** для швидкої публікації **PulseLink (`com.pulselink.btchat`)**.

---

## 1. Готові файли для завантаження

| Призначення в Google Play Console | Шлях до готового файлу |
| :--- | :--- |
| **App Bundle для релізу (`.aab`, підписаний)** | `PulseLink-v1.1.0-release.aab` |
| **Підписаний Release APK (для прямого встановлення)** | `PulseLink-v1.1.0-release.apk` |
| **Ключ підпису (Keystore)** | `pulselink-release-key.jks` (Alias: `pulselink_key`, Pass: `PulseLink2026!`) |
| **Іконка додатку (512 × 512 PNG)** | `pulselink_playstore_icon_512.png` |
| **Банер для стору (Feature Graphic 1024 × 500 PNG)** | `playstore_assets/feature_graphic_1024x500.png` |
| **Скріншоти телефону (1080 × 2400 PNG, 5 шт.)** | `playstore_assets/screenshot_1_chats.png` .. `screenshot_5_settings.png` |
| **URL Політики конфіденційності (Privacy Policy)** | `https://petrovoleh.github.io/pulselink-bt-chat/privacy-policy.html` *(або GitHub Raw / Pages з папки `docs/privacy-policy.html`)* |

---

## 2. Store Listing (Опис сторінки в Google Play)

### Українська (`uk-UA`)
- **Назва додатку (до 30 символів):** `PulseLink: Офлайн BT Месенджер`
- **Короткий опис (до 80 символів):** `Офлайн чат і HD фото по Bluetooth 5 Long Range без інтернету, мережі та SIM.`
- **Повний опис (до 4000 символів):**
```text
PulseLink — це повністю автономний офлайн-месенджер, який працює напряму між смартфонами через радіоканал Bluetooth Low Energy (Standard 1M, далекобійний Bluetooth 5 Extended Range Coded PHY та швидкісний канал L2CAP). Жодного інтернету, мобільного зв'язку, Wi-Fi, серверів чи реєстрації!

МОЖЛИВОСТІ PULSELINK:
• Далекобійний режим Bluetooth 5 (LE Coded PHY S=8): зв'язок через стіни та на відкритій місцевості на значно більшій відстані, ніж у звичайного Bluetooth.
• HD Фото до 800px та Стікери без інтернету: знімайте фото камерою або обирайте з галереї — додаток передає чіткі WebP-зображення до 800 пікселів через швидкісний прямий канал 1M L2CAP або розумну довідправку пакетів Selective-ACK.
• Автоматичне наскрізне шифрування (E2EE): приватні чати захищені криптографією ECDH (NIST P-256) + AES-128-GCM без потреби сканувати QR-коди.
• Живий Радар відстані: бачте пристрої PulseLink, які перебувають у радіусі дії прямо зараз, із реальною оцінкою відстані в метрах та рівнем сигналу.
• Офлайн GPS-координати та сигнал SOS: діліться супутниковими координатами (відкриваються на карті одним дотиком) або надсилайте пріоритетний червоний сигнал SOS.
• Загальний Ефір (Mesh Chat) та Multi-Hop ретрансляція: спілкуйтеся з усіма поруч або передавайте повідомлення далі через сусідні вузли.
• Три профілі енергозбереження (Еко / Стандарт / Турбо) та кнопка миттєвого очищення даних (Panic Wipe).
```

### English (`en-US`)
- **App Name (max 30 chars):** `PulseLink: Offline BT Mesh Chat`
- **Short Description (max 80 chars):** `Offline Bluetooth 5 Long Range mesh messenger with E2EE, 800px photos & GPS.`
- **Full Description:**
```text
PulseLink is a 100% offline peer-to-peer mesh messenger powered by Bluetooth Low Energy (Standard 1M, Bluetooth 5 Extended Range LE Coded PHY, and high-speed L2CAP channels). Communicate without internet, cellular coverage, Wi-Fi, servers, or account registration.

KEY FEATURES:
• Bluetooth 5 Long Range (LE Coded PHY S=8) & Standard 1M dual-mode radio control.
• High-Bitrate 800px Photos & Expressive Stickers over Bluetooth via direct L2CAP streaming and Selective-ACK chunk retransmission.
• Zero-Config End-to-End Encryption (ECDH P-256 + AES-128-GCM) for private direct chats.
• Live Proximity Radar showing real-time distance in meters and 4-bar signal strength.
• Offline Satellite GPS Coordinate Sharing & High-Priority SOS Alerts.
• Global Broadcast Mesh Chat with Multi-Hop Relaying and 1-tap Panic Wipe.
```

- **Категорія (Category):** `Communication` (Зв'язок / Комунікації)
- **Теги (Tags):** `Communication`, `Messaging`, `Tools`
- **Контактний Email:** `oleh.petrov.dev@gmail.com`

---

## 3. Відповіді на всі опитувальники Google Play Console (App Content / Policy Declarations)

### A. App Access (Доступ до додатку)
- **Оберіть:** `All functionality in my app is available without any access restrictions` (Усі функції доступні без обмежень, логінів чи паролів — додаток не потребує реєстрації).

### B. Ads (Реклама)
- **Оберіть:** `No, my app does not contain ads` (Ні, додаток не містить реклами).

### C. Content Rating (Віковий рейтинг IARC)
- **Категорія опитувальника:** `Social Networking, Forums, Blogs, Chat, or User-to-User Communications` (або `Utility / Communication`).
- **На всі питання про насильство, сексуальний контент, ненормативну лексику, наркотики, азартні ігри:** відповідаємо **`No` (Ні)**.
- **Додаткові питання про обмін контентом між користувачами:**
  - *Does the app natively allow users to exchange content with other users?* → **`Yes`** (локально по Bluetooth).
  - *Does the app share the user's current physical location with other users?* → **`Yes`** (лише за ручним натисканням кнопки «Офлайн GPS координати»).
  - *Does the app allow users to purchase digital goods?* → **`No`**.

### D. Target Audience and Content (Цільова аудиторія)
- **Target Age Group (Вікові групи):** оберіть **`18 and over`** (або `13-15`, `16-17`, `18 and over` — **НЕ обирайте дітей до 13 років**, щоб не потрібна була програма Designed for Families).
- *Could your store listing unintentionally appeal to children?* → **`No`**.

### E. Data Safety (Безпека даних) — Найважливіший розділ!
1. **Does your app collect or share any of the required user data types?**
   → Оберіть **`No` (Ні)**!
   *(Згідно з правилами Google Play Data Safety, локальна передача даних напряму з пристрою на пристрій користувача по Bluetooth без відправки на сервери розробника та без хмарного збору вважається такою, що не збирає дані на серверах).*

### F. Foreground Service Permission Declaration (`FOREGROUND_SERVICE_CONNECTED_DEVICE`)
У розділі **App Content → Foreground service permissions** для типу `connectedDevice`:
- **Чекбокс:** `Connected device (Bluetooth, NFC, IR, USB, or network connection)`
- **Текст пояснення (Description for Google Reviewer):**
  > `PulseLink is an offline peer-to-peer Bluetooth Low Energy messenger. The connectedDevice foreground service keeps the Bluetooth LE scanner and advertiser running with a visible user notification (including a one-tap "OFF" stop button) so the user can continuously discover nearby peer devices and receive incoming Bluetooth chat messages, photos, and emergency SOS alerts while the phone screen is off. Turning radio mode to OFF immediately stops the foreground service.`

### G. Інші декларації (швидкі відповіді)
- **News apps (Новинний додаток):** `No, my app is not a news app`
- **COVID-19 contact tracing and status apps:** `My app is not a publicly available COVID-19 contact tracing or status app`
- **Advertising ID:** `No` (додаток не використовує `com.google.android.gms.permission.AD_ID`)
- **Government apps (Державний додаток):** `No`
- **Financial features (Фінансові функції):** `My app doesn't provide any financial features`
- **Health apps (Медичні функції):** `My app does not have any health features`
