import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";

/*
=========================================================
🔥 FIREBASE CONFIGURATION (DUMMY/TEMPLATE FILE)
=========================================================

⚠️ ATTENTION OTHER DEVELOPERS:
This is a dummy configuration file for reference purposes only.
The actual `firebase.config.ts` file contains sensitive API keys 
and is ignored via `.gitignore` to prevent exposure.

👉 HOW TO USE THIS:
1. Copy this entire file.
2. Rename the new file to `firebase.config.ts` in this exact directory.
3. Replace the placeholder values below with your actual Firebase Project keys.
*/

const firebaseConfig = {
    apiKey: "YOUR_FIREBASE_API_KEY_HERE",
    authDomain: "YOUR_PROJECT_ID.firebaseapp.com",
    projectId: "YOUR_PROJECT_ID",
    storageBucket: "YOUR_PROJECT_ID.firebasestorage.app",
    messagingSenderId: "123456789012",
    appId: "1:123456789012:web:abcdef1234567890abcdef"
};

// Initialize Firebase App
const app = initializeApp(firebaseConfig);

// Export Authentication instance for usage across the app
export const auth = getAuth(app);
