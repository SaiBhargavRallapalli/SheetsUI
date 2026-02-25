# Launch Checklist – SheetsUI

Before publishing to the Play Store, complete these steps:

## 1. Firebase Console

Add the new package `com.rsb.sheetsui` to your Firebase project:

1. Go to [Firebase Console](https://console.firebase.google.com) → your project
2. Project Settings (⚙️) → Your apps
3. If you see only `com.example.sheetsui`, add a new Android app:
   - Click "Add app" → Android
   - Package name: `com.rsb.sheetsui`
   - Add your app nickname, then register
4. Download the new `google-services.json` and replace `app/google-services.json`
5. Or: edit the existing app’s package in Firebase and re-download (if supported)

## 2. Google Cloud Console – OAuth

Add the new package and SHA-1 for Google Sign-In:

1. Go to [Google Cloud Console](https://console.cloud.google.com) → APIs & Credentials
2. Open your OAuth 2.0 Client ID (Android type)
3. Add a new Android client with:
   - Package name: `com.rsb.sheetsui`
   - SHA-1: from your **release** keystore  
     Run: `keytool -list -v -keystore /path/to/your/release.keystore`

## 3. Privacy Policy

Play Store requires a valid privacy policy URL. Options:

- Host `PRIVACY_POLICY.md` (in this repo) on GitHub Pages, your website, or a service like [TermsFeed](https://www.termsfeed.com/privacy-policy-generator/)
- When submitting to Play Console, use the hosted URL in the Privacy policy field

## 4. Play Console – App Signing

- Ensure you’re using the correct release keystore
- If using Play App Signing, add the upload key SHA-1 to Firebase and Google Cloud as above

## 5. OAuth Verification (if 100+ users)

If you expect more than 100 users and use sensitive scopes (Drive, Sheets), you may need [OAuth verification](https://support.google.com/cloud/answer/9110914) in Google Cloud Console.
