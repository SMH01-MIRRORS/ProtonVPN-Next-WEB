# Proton VPN API Documentation (Unofficial & Deep Dive) 🛠️

[English](#english) | [Русский](#русский)

---

## English

This document provides a comprehensive technical guide to the Proton VPN API, reverse-engineered and implemented in the ProtonVPN-Next project. It covers authentication, infrastructure, tunnel management, and censorship resistance.

### 1. Infrastructure & Base URLs

Proton utilizes multiple domains to ensure service availability. The app dynamically switches between them based on connectivity and censorship.

- **Primary:** `https://vpn-api.proton.me/`
- **Secondary:** `https://api.protonmail.ch/`
- **Alternative:** `https://api.protonvpn.ch/`

#### Censorship Resistance (DoH Fallback)
If all primary domains are blocked, the client uses **DNS-over-HTTPS** to discover alternative API hosts.
- **Query Method:** TXT record lookup via Google/Cloudflare DoH.
- **Domain Pattern:** `[session_id.]d[base32_host].protonpro.xyz`
- **Example:** A query for `dvpn-api.proton.me.protonpro.xyz` returns a list of hidden mirrors (e.g., Netlify or Cloudfront endpoints).

### 2. Networking Layer & Security

Proton's backend strictly validates client identity via headers and SSL parameters.

#### Mandatory Headers
Every request must include the following headers, or the server will return `403 Forbidden`.

| Header | Example Value | Description |
| :--- | :--- | :--- |
| `User-Agent` | `ProtonVPN/5.15.x (Android ...)` | Identifies the client platform and version. |
| `x-pm-appversion` | `android-vpn@5.15.95.5-dev+play` | Specific build identifier. |
| `x-pm-apiversion` | `4` | API version (v4 is standard for core/auth). |
| `Accept` | `application/vnd.protonmail.v1+json` | Content negation. |
| `x-pm-uid` | `SESSION_ID` | Required for authenticated requests. |

#### Certificate Pinning (SPKI)
The app implements strict certificate pinning using SHA-256 SPKI hashes for all known Proton domains and fallback mirrors to prevent MITM attacks in hostile network environments.

### 3. Authentication Flow

#### A. Standard Login (SRP-6a)
Proton uses the **Secure Remote Password (SRP)** protocol, ensuring the password is never sent to the server.

1.  **Get Auth Info (`POST /auth/v4/info`):**
    - **Body:** `{"Username": "...", "Intent": "Auto"}`
    - **Returns:** `Modulus`, `ServerEphemeral`, `Salt`, `SRPSession`.
2.  **Perform Login (`POST /auth/v4`):**
    - **Body:** Includes `ClientEphemeral` and `ClientProof` (computed locally).
    - **Returns:** `AccessToken`, `RefreshToken`, `UID` (Session ID).
3.  **Two-Factor Auth (`POST /auth/v4/2fa`):**
    - Required if `Code` 8003 is returned.
4.  **Session Refresh (`POST /auth/v4/refresh`):**
    - Uses a short-lived `AccessToken` and a long-lived `RefreshToken`.

#### B. Loginless / Guest Authentication
For free users without accounts, the app uses a multi-step "credentialless" flow.

1.  **Create Anonymous Session (`POST /auth/v4/sessions`):**
    - Requires a `challengePayload` (device integrity data).
    - Returns a temporary `AccessToken`.
2.  **Credentialless Upgrade (`POST /auth/v4/credentialless`):**
    - Exchanges the anonymous token for a full VPN session.
    - Uses the same `challengePayload` for verification.

### 4. VPN & Tunnel Management

#### A. Logical Servers (`GET /vpn/v2/logicals`)
Retrieves the complete server list. Supports incremental updates via `If-Modified-Since`.

- **Parameters:**
    - `WithEntriesForProtocols=wireguard`: Filters for WireGuard support.
    - `WithState=true`: Includes real-time status (Online/Maintenance).
- **Structure:** `LogicalServer` -> `PhysicalServer`.
- **Key Field:** `X25519PublicKey` – The server's public key for the handshake.

#### B. Certificate & Key Registration (`POST /vpn/v1/certificate`)
This endpoint "activates" a client's public key on the backend to allow a VPN connection.

- **Request:**
  ```json
  {
    "ClientPublicKey": "BASE64_ED25519_KEY",
    "Mode": "persistent" 
  }
  ```
- **Certificate Modes:**
    - **Standard (Ephemeral):** Default. The registration is short-lived.
    - **Persistent (Extended):** Requested via `Mode: "persistent"`. Used for "Always-on" or stable connections where the key shouldn't rotate frequently.
- **Response:**
    - `IPv4` / `IPv6`: Internal tunnel IPs (e.g., `10.2.0.2`).
    - `DNS`: List of internal DNS servers.
    - `RefreshTime`: When the client should renew the registration.

#### C. User VPN Status (`GET /vpn/v2`)
Returns the user's subscription tier (`MaxTier`), concurrent connection limits (`MaxConnect`), and VPN-specific credentials.

### 5. Localized Data
- **City Names (`GET /vpn/v1/cities/names`):** Fetches localized translations for server locations based on `x-pm-locale`.

---

## Русский

Данный документ представляет собой техническое руководство по API Proton VPN, разработанное в рамках проекта ProtonVPN-Next.

### 1. Инфраструктура

- **Основные домены:** `vpn-api.proton.me`, `api.protonmail.ch`.
- **Обход блокировок (DoH):** В случае блокировки доменов, приложение использует DNS-over-HTTPS для поиска зеркал через TXT-записи домена `protonpro.xyz`.

### 2. Сетевой уровень

Для успешных запросов обязательны заголовки:
- `x-pm-appversion`: Версия приложения.
- `x-pm-apiversion`: `4`.
- `User-Agent`: Специфическая строка ProtonVPN.

Безопасность обеспечивается через **Certificate Pinning** (SPKI) для всех узлов API.

### 3. Аутентификация

#### A. SRP (Secure Remote Password)
Протокол исключает передачу пароля. Клиент доказывает владение паролем локально, обмениваясь эфемерными ключами с сервером (`auth/v4/info` и `auth/v4`).

#### B. Вход без регистрации (Loginless)
Метод для новых пользователей, позволяющий получить доступ к бесплатным серверам без создания аккаунта, используя проверку целостности устройства (`challengePayload`).

### 4. Управление VPN

#### A. Список серверов (`vpn/v2/logicals`)
Иерархический список локаций. Важное поле: `X25519PublicKey` — публичный ключ сервера для WireGuard.

#### B. Типы сертификатов (`vpn/v1/certificate`)
При регистрации публичного ключа клиента можно указать режим:
- **Стандартный (Ephemeral):** Краткосрочная привязка ключа.
- **Персистентный (Persistent):** Режим «расширенного сертификата», предотвращающий частую ротацию ключей и обеспечивающий стабильность при «Always-on» VPN.

#### C. Ответ сервера при регистрации:
- **IPv4/IPv6:** Внутренние IP-адреса туннеля (обычно подсеть `10.2.0.0/16`).
- **DNS:** Список защищенных DNS-серверов Proton.

### 5. Локализация
Эндпоинт `vpn/v1/cities/names` возвращает переводы названий городов для отображения в интерфейсе в соответствии с заголовком `x-pm-locale`.

---

### Disclaimer / Отказ от ответственности
This documentation is for educational purposes only. It is the result of reverse-engineering and may change without notice.
Данная документация создана исключительно в образовательных целях на основе реверс-инжиниринга и может измениться без уведомления.
