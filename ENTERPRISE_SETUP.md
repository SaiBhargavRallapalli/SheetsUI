# Enterprise Setup Guide – SheetsUI

This guide ensures SheetsUI works correctly with Google Sheets, Forms, Drive, Tasks, and Docs APIs.

## 1. Enable APIs in Google Cloud Console

For project **sheetsui-cb4ec** (or your Firebase project):

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project (or create one linked to Firebase)
3. Open **APIs & Services** → **Enabled APIs & services**
4. Click **+ ENABLE APIS AND SERVICES**
5. Enable these APIs:
   - **Google Drive API** (for spreadsheets & forms list, export)
   - **Google Sheets API** (for spreadsheet data)
   - **Google Forms API** (for Forms list; forms are stored in Drive)
   - **Google Tasks API** (optional – for Create Task from row)
   - **Google Docs API** (optional – for Generate Doc from row)

## 2. OAuth Scopes

The app uses:
- **Core** (sign-in): Drive, Sheets, Forms
- **Enterprise** (when using Tasks/Docs): Tasks, Docs (granted on first use or via Settings)

If you see **403 Forbidden** when opening sheets or forms:
1. Confirm all required APIs are enabled above
2. **Sign out** and **sign in again** to refresh OAuth tokens
3. On first sign-in, approve the requested permissions

## 3. Tasks & Docs (Optional)

To use **Create Task** and **Generate Doc** in row details:
1. Enable **Google Tasks API** and **Google Docs API** in Cloud Console
2. In the app: **Settings** → **Request Enterprise Permissions** (grants Tasks & Docs access)
3. Or tap **Create Task** / **Generate Doc** – you’ll be prompted to grant access when needed

## 4. Troubleshooting

| Issue | Fix |
|-------|-----|
| 403 when opening a sheet | Enable Sheets API; sign out and sign in again |
| Forms tab empty | Enable Drive API; sign out and sign in again |
| Create Task fails | Enable Tasks API; use Settings → Request Enterprise Permissions |
| Generate Doc fails | Enable Docs API; use Settings → Request Enterprise Permissions |
| "Unverified app" | Tap Advanced → Go to SheetsUI (for testing) |

## 5. Production Checklist

- [ ] All required APIs enabled in Cloud Console
- [ ] OAuth consent screen configured (if publishing)
- [ ] Google Cloud billing enabled if required for quotas
- [ ] Firebase project linked to correct Google Cloud project
