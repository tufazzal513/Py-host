/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export default function App() {
  return (
    <div className="min-h-screen bg-gray-900 text-gray-100 flex flex-col items-center justify-center p-8">
      <div className="max-w-2xl text-center space-y-6">
        <div className="flex justify-center">
          <svg className="w-24 h-24 text-green-500" fill="currentColor" viewBox="0 0 24 24">
            <path d="M17.523 15.3414c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.5511 0 .9993.4482.9993.9993.0004.5511-.4482.9997-.9993.9997m-11.046 0c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.5511 0 .9993.4482.9993.9993 0 .5511-.4482.9997-.9993.9997m11.4045-6.02l1.9973-3.4592c.1158-.201.0467-.4569-.1535-.5731-.201-.1154-.4566-.0464-.5727.1546l-2.0122 3.4862c-1.4395-.658-3.1118-1.0261-4.8967-1.0261-1.7845 0-3.4568.3681-4.8959 1.0261l-2.0126-3.4862c-.1162-.201-.3718-.2704-.5727-.1546-.2007.1162-.2693.3721-.1535.5731l1.9969 3.4592C2.656 10.9577.3045 14.5367.0428 18.7302h23.914c-.261-4.1935-2.6125-7.7725-5.0293-9.4088" />
          </svg>
        </div>
        <h1 className="text-4xl font-bold text-white">PyMobile IDE - Android Native</h1>
        <p className="text-lg text-gray-400">
          This repository contains a native Android Kotlin Jetpack Compose application. 
          The web preview environment is not configured to run the Android emulator.
        </p>
        <div className="bg-gray-800 p-6 rounded-lg text-left border border-gray-700">
          <h2 className="text-2xl font-semibold mb-4 text-white">How to Build the APK</h2>
          <ol className="list-decimal list-inside space-y-3 text-gray-300">
            <li><strong>Push to GitHub:</strong> Connect this project to a GitHub repository.</li>
            <li><strong>GitHub Actions:</strong> The included <code>.github/workflows/android-build.yml</code> workflow will automatically build the Android APK.</li>
            <li><strong>Download APK:</strong> Go to the "Actions" tab in your GitHub repository, select the latest build, and download the <code>app-debug.apk</code> artifact.</li>
            <li><strong>Local Development:</strong> Clone the repository locally and open it with <strong>Android Studio</strong>.</li>
          </ol>
        </div>
      </div>
    </div>
  );
}
