# Privacy Policy and Disclaimer | Политика конфиденциальности и отказ от ответственности

*Last updated: June 25, 2026*

## English

### 1. Introduction
ProtonVPN-Next is an open-source, unofficial client for Proton VPN services. This document outlines how data is handled by the application. By using this application, you agree to the terms described herein.

### 2. No Affiliation
This project is **NOT** affiliated with, authorized, maintained, sponsored, or endorsed by Proton AG or any of its affiliates. This is an independent, community-driven project.

### 3. Data Collection and Handling

#### 3.1 Proton Services
The application interacts directly with Proton's official APIs to provide VPN services.
- **Authentication**: Your credentials (username/password) are processed using the Secure Remote Password (SRP) protocol. The application does not store your password in plain text.
- **VPN Traffic**: All your internet traffic is routed through Proton's servers. This application does not intercept, log, or monitor your VPN traffic. Please refer to [Proton's Privacy Policy](https://protonvpn.com/privacy-policy) for details on how they handle your data.

#### 3.2 Analytics and Crash Reporting (Optional)
The application uses **Sentry** for crash reporting and anonymous usage analytics to help improve the app.
- This is **OPT-IN** and can be disabled at any time in the app settings (Settings -> Error Reporting).
- Data collected may include device model, OS version, and stack traces when a crash occurs. No personally identifiable information (PII) is sent to Sentry.

#### 3.3 Local Storage
The application stores certain data locally on your device:
- Encrypted session tokens and cryptographic keys.
- Server list cache.
- User settings and preferences.
  This data remains on your device and is not uploaded to any third-party servers other than Proton's APIs during normal operation.

#### 3.4 API Block Bypass (Optional)
To ensure accessibility in regions where Proton's official APIs are restricted, the application offers several API Bypass strategies.
- **Proxies (Netlify, Cloudflare, Deno, Custom)**: If enabled, your API requests (containing authentication and server list queries) may be routed through third-party infrastructure. While authentication is encrypted (SRP), these proxies will see your IP address and the fact that you are accessing Proton services.
- **ByeDPI (DPI Deception)**: Uses advanced packet fragmentation and deception techniques locally on your device. Since this is a local SOCKS5 proxy, your traffic does not pass through any third-party proxy servers, and your IP address is not shared with any external proxy provider. All data is processed locally before reaching Proton's APIs.
  These features are **OPTIONAL** and can be configured in the app settings (Settings -> API Block Bypass).

### 4. Mandatory Acceptance
By using this application, you acknowledge that you have read and accepted this Privacy Policy. Access to the application is conditional upon this acceptance.

### 5. Disclaimer of Warranty
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

---

## Русский

### 1. Введение
ProtonVPN-Next — это неофициальный клиент с открытым исходным кодом для сервисов Proton VPN. В этом документе описывается, как приложение обрабатывает данные. Используя это приложение, вы соглашаетесь с условиями, описанными здесь.

### 2. Отсутствие аффилированности
Этот проект **НЕ** связан, не авторизован, не поддерживается и не одобряется компанией Proton AG или её филиалами. Это независимый проект сообщества.

### 3. Сбор и обработка данных

#### 3.1 Сервисы Proton
Приложение напрямую взаимодействует с официальными API Proton для предоставления услуг VPN.
- **Аутентификация**: Ваши учетные данные (имя пользователя/пароль) обрабатываются по протоколу Secure Remote Password (SRP). Приложение не хранит ваш пароль в открытом виде.
- **VPN-трафик**: Весь ваш интернет-трафик направляется через серверы Proton. Данное приложение не перехватывает, не регистрирует и не отслеживает ваш VPN-трафик. Пожалуйста, ознакомьтесь с [Политикой конфиденциальности Proton](https://protonvpn.com/privacy-policy) для получения подробной информации о том, как они обрабатывают ваши данные.

#### 3.2 Аналитика и отчеты об ошибках (Опционально)
Приложение использует **Sentry** для отчетов о сбоях и анонимной аналитики использования, чтобы помочь улучшить приложение.
- Это работает по принципу **ДОБРОВОЛЬНОГО СОГЛАСИЯ** и может быть отключено в любое время в настройках приложения (Настройки -> Отчеты об ошибках).
- Собираемые данные могут включать модель устройства, версию ОС и трассировку стека при возникновении сбоя. Личная информация (PII) в Sentry не отправляется.

#### 3.3 Локальное хранилище
Приложение сохраняет определенные данные локально на вашем устройстве:
- Зашифрованные токены сессий и криптографические ключи.
- Кэш списка серверов.
- Пользовательские настройки и предпочтения.
  Эти данные остаются на вашем устройстве и не передаются на сторонние серверы, кроме API Proton в процессе обычной работы.

#### 3.4 Обход блокировок API (Опционально)
Для обеспечения доступности в регионах, где официальные API Proton ограничены, приложение предлагает несколько стратегий обхода блокировок.
- **Прокси (Netlify, Cloudflare, Deno, Пользовательские)**: Если эта функция включена, ваши запросы к API (содержащие данные аутентификации и запросы списка серверов) могут направляться через стороннюю инфраструктуру. Хотя аутентификация зашифрована (SRP), эти прокси будут видеть ваш IP-адрес и факт обращения к сервисам Proton.
- **ByeDPI (Обман DPI)**: Использует продвинутую фрагментацию пакетов и методы обмана цензора локально на вашем устройстве. Поскольку это локальный SOCKS5 прокси, ваш трафик не проходит через сторонние прокси-серверы, и ваш IP-адрес не передается внешним провайдерам прокси. Все данные обрабатываются локально перед отправкой к API Proton.
  Эти функции являются **ОПЦИОНАЛЬНЫМИ** и могут быть настроены в настройках приложения (Настройки -> Обход блокировок API).

### 4. Обязательное принятие
Используя это приложение, вы подтверждаете, что прочитали и приняли настоящую Политику конфиденциальности. Доступ к приложению возможен только при условии этого принятия.

### 5. Отказ от ответственности
ПРОГРАММНОЕ ОБЕСПЕЧЕНИЕ ПРЕДОСТАВЛЯЕТСЯ «КАК ЕСТЬ», БЕЗ КАКИХ-ЛИБО ГАРАНТИЙ, ЯВНЫХ ИЛИ ПОДРАЗУМЕВАЕМЫХ. НИ ПРИ КАКИХ ОБСТОЯТЕЛЬСТВАХ АВТОРЫ ИЛИ ПРАВООБЛАДАТЕЛИ НЕ НЕСУТ ОТВЕТСТВЕННОСТИ ПО КАКИМ-ЛИБО ИСКАМ, ЗА УЩЕРБ ИЛИ ПО ИНЫМ ОБЯЗАТЕЛЬСТВАМ, ВОЗНИКШИМ В РЕЗУЛЬТАТЕ ИСПОЛЬЗОВАНИЯ ПРОГРАММНОГО ОБЕСПЕЧЕНИЯ.
