# Privacy Policy

**Effective date:** [YYYY-MM-DD]
**Last updated:** [YYYY-MM-DD]

> ⚠️ **DRAFT — fill the `[bracketed]` placeholders before publishing.** This draft reflects the data flows actually present in the vibi app as of the date above. Have it reviewed by counsel familiar with GDPR (EU/EEA), the UK GDPR, the CCPA/CPRA (California), and Korea's PIPA before you ship it. Items that still need real values are listed in **§16 Open items**.

This Privacy Policy explains how **[Legal Entity Name]** ("**vibi**", "**we**", "**us**", or "**our**") collects, uses, shares, and protects information when you use the **vibi** mobile application (the "**App**"; iOS bundle `com.vibi.ios`, Android package `com.vibi.cmp`) and related services (together, the "**Service**").

By using the Service, you agree to the practices described here. If you do not agree, please do not use the Service.

---

## 1. Who we are (Data Controller)

| | |
|---|---|
| **Controller** | [Legal Entity Name] |
| **Country of establishment** | Republic of Korea |
| **Address** | [Registered business address] |
| **Privacy contact** | [privacy@yourdomain.com] |
| **EU/EEA representative (GDPR Art. 27)** | [Name / address, if you offer the Service in the EU] |
| **UK representative** | [Name / address, if applicable] |
| **Korea privacy officer (개인정보 보호책임자)** | [Name / contact] |

---

## 2. The data we collect

We only collect what the App needs to function. **We do not use any advertising, analytics, or third-party tracking SDKs**, and we do not track you across other apps or websites.

### 2.1 Information you provide
- **Account information** — when you sign in with Google or Apple, we receive your **email address**, **display name**, and a provider **user identifier**. With Sign in with Apple you may choose to hide your email (Apple relay address).
- **Media you upload for editing** — the **video and audio files** you select to process (e.g., for voice/music separation and rendering), and the project settings you create (segment timings, audio levels, language selection).

### 2.2 Information created when you use the Service
- **Processed media** — derived audio stems and rendered videos produced from your uploads.
- **Account identifiers** — an internal user ID and authentication token (JWT) issued by our backend.
- **Purchase records** — when you buy credits, the app store sends us a **receipt / purchase token**, **product ID**, **platform** (Apple or Google), and **transaction ID**. We never receive or store your full payment card details — payment is handled by Apple or Google.
- **Credit balance and usage** — how many credits you hold and consume.

### 2.3 Information collected automatically
- **Crash data** — if the App crashes, the operating system (Apple / Google) may provide us diagnostic crash logs. We do **not** embed a third-party crash-reporting SDK.
- **Technical data needed to deliver content** — e.g., IP address and request metadata that our backend necessarily processes to route and secure network requests.

### 2.4 Device permissions
The App requests these permissions only for the stated purpose, and only when needed:

| Permission | Platform | Why |
|---|---|---|
| Microphone | iOS / Android | Record audio to insert into your timeline |
| Photo library (read) | iOS / Android | Let you pick a video to edit |
| Save to photo library | iOS / Android | Save the finished video to your device |

We do **not** collect location, contacts, or health data.

### 2.5 Children
The Service is **not directed to children under 14**. We do not knowingly collect data from children below that age (or below the higher minimum required in your country — e.g., consent ages of up to 16 in parts of the EU). If you believe a child has provided us data, contact us at [privacy@yourdomain.com] and we will delete it.

---

## 3. How we use your data

| Purpose | Examples | Legal basis (GDPR) |
|---|---|---|
| Provide the core Service | Authenticate you; upload, separate, and render your media | Performance of a contract (Art. 6(1)(b)) |
| Process purchases | Validate store receipts; grant credits | Performance of a contract |
| Keep the Service secure and working | Prevent abuse, debug crashes | Legitimate interests (Art. 6(1)(f)) |
| Comply with law | Tax, accounting, responding to lawful requests | Legal obligation (Art. 6(1)(c)) |
| Communicate with you | Service or account notices | Contract / legitimate interests |

We do **not** sell your personal data, and we do **not** use it for advertising or to train third-party models.

---

## 4. How your media is processed

When you edit a video, the App uploads your media to our backend and to **Cloudflare R2** object storage for processing (audio separation and video rendering). Processing is automated. Once a job finishes and you download the result, your uploaded source files and intermediate stems are retained only as described in **§7**.

---

## 5. Who we share data with

We share data only with the service providers ("processors") needed to run the Service:

| Recipient | Purpose | Data shared |
|---|---|---|
| **Google** (Sign-In) | Authentication | OAuth token, profile basics |
| **Apple** (Sign in with Apple) | Authentication | OAuth token, profile basics |
| **Apple App Store / Google Play** | Process in-app purchases | Purchase receipt / token |
| **Cloudflare** (R2 object storage) | Store and transfer your media | Uploaded media, rendered output |
| **[Backend / compute provider, if separate from above]** | Run the audio-separation and rendering jobs | Uploaded media |

We may also disclose data when **required by law**, to **enforce our Terms**, or in connection with a **merger, acquisition, or sale of assets** (you will be notified of any such change).

We do **not** share data with advertisers or data brokers.

---

## 6. International data transfers

We are based in the **Republic of Korea**, and our service providers (including Cloudflare) may process and store data on servers located outside your country, including in regions where those providers operate their global infrastructure. Where we transfer personal data out of the EEA/UK, we rely on appropriate safeguards such as the **EU Standard Contractual Clauses** (and the UK Addendum). Cross-border transfers affecting Korean users are handled in accordance with **PIPA Art. 28-8**. Contact us for a copy of the relevant safeguards. **[Confirm the specific R2 region(s) you provision and name them here before publishing.]**

---

## 7. How long we keep data

| Data | Retention |
|---|---|
| Account data (email, name, user ID) | Until you delete your account, then removed within **[30] days** |
| Uploaded source media & intermediate stems | **[e.g., deleted within 24–72 hours of job completion]** |
| Rendered output | **[e.g., until you delete it / up to N days]** |
| Purchase records | As required by tax/accounting law (typically **[5] years**) |
| Crash logs | **[retention period]** |

---

## 8. Your rights

Depending on where you live, you have some or all of the following rights. To exercise any of them, contact **[privacy@yourdomain.com]**. We will respond within the time the law requires (e.g., 30 days under GDPR, 45 days under CCPA). You can also **delete your account directly in the App**, which deletes your account data.

### 8.1 EU / EEA & UK (GDPR / UK GDPR)
Access, rectification, erasure, restriction, portability, objection, and the right to withdraw consent. You may also lodge a complaint with your local supervisory authority.

### 8.2 California (CCPA/CPRA)
The right to know, delete, correct, and to opt out of "sale" or "sharing" of personal information. **We do not sell or share personal information** as those terms are defined under the CCPA. We will not discriminate against you for exercising your rights.

### 8.3 Korea (PIPA / 개인정보 보호법)
The right to access, correct, delete, suspend processing of, and withdraw consent for your personal information. You may contact our privacy officer (§1) and may also file a report with the Personal Information Protection Commission (개인정보분쟁조정위원회 / privacy.go.kr).

### 8.4 Other regions
We extend equivalent rights to users elsewhere on request, to the extent applicable law allows.

---

## 9. Security

We protect your data with encryption in transit (HTTPS/TLS), access controls, token-based authentication, and presigned, time-limited URLs for media transfer. No method of transmission or storage is 100% secure, but we work to protect your information and will notify you and regulators of a breach where the law requires.

---

## 10. Account deletion

You can delete your account at any time from within the App (Settings → Account). Deletion removes your account data per **§7**. Some records may be retained where the law requires (e.g., purchase/tax records).

---

## 11. Do Not Track

The App contains no third-party tracking, so there is nothing to track across other apps or sites. We do not respond to browser "Do Not Track" signals because the App is not a website.

---

## 12. Third-party services

Signing in with Google or Apple, and purchasing through the App Store or Google Play, is governed by **their** privacy policies in addition to ours:
- Google: https://policies.google.com/privacy
- Apple: https://www.apple.com/legal/privacy/

---

## 13. Changes to this policy

We may update this policy. If we make material changes, we will notify you in the App or by email and update the "Last updated" date. Continued use after changes means you accept the updated policy.

---

## 14. Contact us

Questions or requests: **[privacy@yourdomain.com]**
[Legal Entity Name], [Registered business address]

---

## 15. App store data-disclosure mapping (internal note — remove before publishing)

For the **App Store "App Privacy"** label and **Google Play "Data safety"** form, declare:
- **Collected & linked to identity:** email, name, user ID, photos/videos, audio, purchase history.
- **Collected, not linked:** crash data (diagnostics).
- **Used for tracking:** none.
- Keep this consistent with `iosApp/iosApp/PrivacyInfo.xcprivacy`.

## 16. Open items (internal note — remove before publishing)

Filled from the codebase/context: app name & bundle IDs, country of establishment (Korea), Cloudflare R2 as storage, auth/payment providers, no-tracking posture, permissions, minimum age (14).

Still need real values — only **you** can supply these:
1. **Legal entity name + registered business address.**
2. **Privacy contact email** (+ EU/UK Art.27 representatives if you launch in those markets, and the named Korea privacy officer).
3. **Retention periods** in §7 — pick real numbers for media, output, crash logs.
4. **R2 region(s)** you actually provision (§6).
5. **Effective / last-updated dates.**
6. Host this document at a public URL and wire it into the App (LoginScreen + CreditPurchaseSheet links).
