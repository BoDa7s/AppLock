---
layout: page
title: Privacy Policy
description: How AdamAppLock handles your data
permalink: /privacy/
last_updated: 2025-12-21
---

# Privacy Policy
**Effective date:** 2025-12-21

AdamAppLock (“the App”) helps you lock selected apps with a passcode or biometric. It is designed to work **on-device** and does not send your personal data to our servers.

## What we collect
- **No personal data is collected or transmitted off your device.**
- The App does not include ads, analytics SDKs, or third-party trackers.

## What’s stored on your device
- **Passcode data:** Stored locally as a salted one-way hash (PBKDF2) and protected using Android Keystore–backed encryption (AES-GCM). The plaintext passcode is never stored.
- **Settings & selections:** Locked-app list, language/theme, lock timers, and related preferences.
- **Runtime state:** Temporary data required for overlays and permission checks.

## Permissions we request (and why)
- **Display over other apps (Appear on top):** To show the lock screen above protected apps.
- **Usage access:** To detect which app is in the foreground so we can protect the ones you choose.
- **Battery settings (recommended):** Some phones aggressively limit background work. Setting Battery to “Unrestricted” (or allowing background activity) can improve reliability.

These permissions are used **only** for protection features and local checks. They are not used to collect personal data.

## Data sharing
- We do **not** sell or share data. The App does not connect to any remote servers.

## Security
- Sensitive values (like passcode hashes) are stored using platform security best practices.
- You are responsible for keeping your passcode confidential and your device secure.

## Children’s privacy
- The App is not directed to children under 13. If you believe a child has provided sensitive information, contact us and we will provide guidance on removal.

## Changes to this policy
We may update this policy for clarity or new features. When we do, we’ll update the **Effective date** above and publish changes on this page.

## Contact & support
_Questions?_ Open an issue on 
**<a href="https://github.com/BoDa7s/AppLock/issues" target="_blank" rel="noopener">GitHub</a>**.

