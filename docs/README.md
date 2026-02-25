# Hosting Your Privacy Policy

Play Store requires a **public URL** for your privacy policy. Here's how to get one for free.

## Option 1: GitHub Pages (Recommended, Free)

1. Create a GitHub repo for your app (e.g. `SheetsUI` or `sheetsui-app`).

2. Upload the `privacy-policy.html` file to the repo (e.g. in the root or in a `docs` folder).

3. Enable GitHub Pages:
   - Repo → **Settings** → **Pages**
   - Source: **Deploy from a branch**
   - Branch: `main` (or `master`), folder: `/` or `/docs`

4. Your privacy policy URL will be:
   - `https://YOUR_USERNAME.github.io/YOUR_REPO/privacy-policy.html`
   - Or if in root: `https://YOUR_USERNAME.github.io/YOUR_REPO/` (and rename to `index.html`)

**Example:**  
If your repo is `https://github.com/rsb/sheetsui`, the URL might be:  
`https://rsb.github.io/sheetsui/privacy-policy.html`

## Option 2: Use This Repo

If this project is already a git repo and you push it to GitHub:

1. Push the `docs` folder
2. Enable GitHub Pages on the repo
3. Use: `https://YOUR_USERNAME.github.io/SheetsUI/docs/privacy-policy.html`

## Option 3: Other Hosting

You can upload `privacy-policy.html` to any web host: Netlify, Vercel, your own website, etc.

---

**For Play Console:** Paste your privacy policy URL in the app listing under **Privacy Policy**.
