# Privacy — owner checklist

Nothing below was invented by the code; each item **needs your input**.

- [ ] OWNER ACTION REQUIRED: set a real `contactEmail` in `app/src/main/assets/privacy/config.properties`.
- [ ] OWNER ACTION REQUIRED: publish `privacy-site/` and set `policyUrl` (HTTPS) in the same file; the in-app "View online" button stays disabled until then.
- [ ] Replace `version=1.0-DRAFT` with a final version after review; run `python3 tools/build_privacy_site.py`.
- [ ] Legal review of the text (publisher identity/address, legal bases, retention, children's age limit).
- [ ] Professional review of the 7 translations (ru, uk, it, de, fr, es, zh-CN were produced by an AI assistant and are not professionally translated).
- [ ] Decide whether Remote Config should wait for consent.
- [ ] Play Console: App content → Privacy policy URL, Data safety form consistent with `docs/PRIVACY_DATA_AUDIT.md`.
- [ ] AdMob: Privacy & messaging (GDPR, US states) configured and published; add the privacy policy URL.
- [ ] Test on a device: Settings → About → Privacy policy opens offline in each language; toggling languages updates it; opening it does not change consent.
