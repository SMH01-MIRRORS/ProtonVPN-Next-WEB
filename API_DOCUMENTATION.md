# Proton VPN API Documentation (Unofficial & Deep Dive) 🛠️

[English](#english) | [Русский](#русский)

---

## English

This document provides a comprehensive technical guide to the Proton VPN API, reverse-engineered and implemented in this project. Since official documentation is unavailable, this serves as a primary reference for the networking layer.

### 1. Infrastructure & Base URLs

Proton uses several domains for its API. In case of censorship, the app can switch between them.

- **Primary:** `https://vpn-api.proton.me/`
- **Secondary:** `https://api.protonmail.ch/`
- **Alternative:** `https://api.protonvpn.ch/`

### 2. Networking Layer (`NetworkModule`)

Proton's backend is highly sensitive to headers. If they are missing or incorrect, the server returns `403 Forbidden` or `422 Unprocessable Entity`.

#### Headers Implementation
All requests must be intercepted to include these mandatory headers:

```kotlin
val headerInterceptor = Interceptor { chain ->
    val userAgent = "ProtonVPN/5.15.95.5 (Android XX; MODEL XXX-XXX)"
    val request = chain.request().newBuilder()
        .addHeader("User-Agent", userAgent)
        .addHeader("x-pm-appversion", "android-vpn@5.15.95.5-dev+play")
        .addHeader("x-pm-apiversion", "4")
        .addHeader("Accept", "application/vnd.protonmail.v1+json")
        .build()
    chain.proceed(request)
}
```

### 3. Authentication Flow

#### A. Standard Login (SRP Protocol)
Proton uses **Secure Remote Password (SRP)**. This allows authentication without ever sending the password to the server.

1. **Get Auth Info (`POST /auth/v4/info`):** Retrieves the server's SRP parameters (Modulus, Salt, ServerEphemeral).
2. **Perform Login (`POST /auth/v4`):** The client computes the `ClientProof` (M2) locally and sends it along with the `ClientEphemeral` (A).
3. **2FA (`POST /auth/v4/2fa`):** If enabled, requires a TOTP code.

#### B. Loginless / Guest Authentication
Allows users to connect to free servers without creating a permanent account or providing credentials.

1. **Anonymous Session (`POST /auth/v4/sessions`):**
   - **Body:** Requires a `challengePayload` (JSON containing device info and integrity hashes).
   - **Headers:** Supports CAPTCHA via `x-pm-human-verification-token`.
   - **Returns:** An anonymous `AccessToken` and `UID`.

2. **Credentialless Upgrade (`POST /auth/v4/credentialless`):**
   - **Auth:** Uses the anonymous token as `Authorization: Bearer <token>`.
   - **Body:** Same `challengePayload`.
   - **Returns:** Full session tokens (`AccessToken`, `RefreshToken`, `UID`) for VPN usage.

### 4. VPN & Tunnel Management

#### A. Fetching Logical Servers (`GET /vpn/v2/logicals`)
Returns the hierarchy of locations. Supports delta updates via `If-Modified-Since`.
- **Query Params:**
    - `WithEntriesForProtocols=wireguard`: Only fetch servers supporting WireGuard.
    - `WithState=true`: Include current maintenance/online status.
- **Key Field:** `X25519PublicKey` – The server's public key for WireGuard.

#### B. Server Loads (`GET /vpn/v1/loads`)
Returns current load data for all servers.

#### C. Registering WireGuard Keys (`POST /vpn/v1/certificate`)
This endpoint is used to register your local public key on the Proton backend.
- **Request:** `{"ClientPublicKey": "YOUR_BASE64_PUBLIC_KEY"}`
- **Response:** Returns the internal IP (`10.x.x.x`) assigned to your tunnel and DNS settings.

#### D. Location Info (`GET /vpn/v1/location`)
Retrieves the client's current public IP and geographic location.

---

## Русский

Это самое полное техническое руководство по API Proton VPN, воссозданное в процессе разработки этого клиента.

### 1. Инфраструктура и Базовые URL

- **Основной:** `https://vpn-api.proton.me/`
- **Дополнительный:** `https://api.protonmail.ch/`

### 2. Сетевой уровень (`NetworkModule`)

Бэкенд Proton требует специфические заголовки `User-Agent`, `x-pm-appversion` и `x-pm-apiversion`. Без них сервер вернет `403`.

### 3. Процесс аутентификации

#### A. Стандартный вход (SRP Протокол)
Используется **Secure Remote Password (SRP)**, что исключает передачу пароля в открытом или зашифрованном виде.

1. **Auth Info (`POST /auth/v4/info`):** Получение соли и параметров сервера.
2. **Perform Login (`POST /auth/v4`):** Передача доказательства владения паролем (`ClientProof`).
3. **2FA (`POST /auth/v4/2fa`):** Проверка двухфакторного кода.

#### B. Вход без учетных данных (Loginless / Guest)
Позволяет подключаться к бесплатным серверам без регистрации.

1. **Анонимная сессия (`POST /auth/v4/sessions`):**
   - **Тело:** Требует `challengePayload` (JSON с данными об устройстве и хешами целостности).
   - **Капча:** Поддерживается через заголовки `x-pm-human-verification-token`.
   - **Результат:** Временный анонимный `AccessToken` и `UID`.

2. **Апгрейд до VPN-сессии (`POST /auth/v4/credentialless`):**
   - **Авторизация:** Используется полученный анонимный токен в заголовке `Authorization: Bearer <token>`.
   - **Тело:** Тот же `challengePayload`.
   - **Результат:** Полноценные токены сессии (`AccessToken`, `RefreshToken`) для работы с VPN.

### 4. Управление VPN и Туннелем

#### A. Список серверов (`GET /vpn/v2/logicals`)
Иерархия локаций. Поддерживает инкрементальные обновления через `If-Modified-Since`.
- **Важное поле:** `X25519PublicKey` – Публичный ключ сервера для WireGuard.

#### B. Загрузка серверов (`GET /vpn/v1/loads`)
Процент нагрузки серверов для динамического выбора наименее загруженного.

#### C. Регистрация ключей WireGuard (`POST /vpn/v1/certificate`)
"Привязка" вашего публичного ключа.
- **Результат:** Внутренний IP (`10.x.x.x`) и DNS.

#### D. Информация о местоположении (`GET /vpn/v1/location`)
Текущий публичный IP и геопозиция клиента.

---

### Disclaimer / Отказ от ответственности
This documentation is for educational purposes only. It is the result of reverse-engineering and may change without notice.
Данная документация создана исключительно в образовательных целях на основе реверс-инжиниринга и может измениться без уведомления.
