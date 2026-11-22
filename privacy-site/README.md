# Logic Mix — privacy site

Static pages (no JavaScript, relative links) generated from the single source of truth in
`app/src/main/assets/privacy/`. **Do not edit the HTML by hand.**

## Update
```
# edit app/src/main/assets/privacy/policy_*.md or config.properties
python3 tools/build_privacy_site.py          # regenerate
python3 tools/build_privacy_site.py --check  # CI-style check
```

## Deploy (any static host)
- GitHub Pages: publish the `privacy-site/` folder (Pages source: branch + `/privacy-site`, or copy to a `gh-pages` branch).
- Firebase Hosting / Netlify / Cloudflare Pages: set the public directory to `privacy-site`.
- The site works in a sub-path because all links are relative.

## After you have the final HTTPS URL
1. Set `policyUrl=` in `app/src/main/assets/privacy/config.properties` (e.g. the `en/index.html` or `index.html` URL).
2. Set `contactEmail=` and replace `version` (remove `-DRAFT`) once legal review is done.
3. Run the generator and republish.
4. Use the same URL in Google Play Console (App content → Privacy policy) and AdMob (Privacy & messaging).
