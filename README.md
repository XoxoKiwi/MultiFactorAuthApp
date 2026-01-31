# MultiFactorAuthApp

A sophisticated Android security application implementing a **Triple-Layer Authentication** system. This project utilizes behavioral biometrics—specifically **Keystroke Dynamics** and **Pattern Rhythm Analysis**—to ensure the user is the genuine owner.

## ⚙️ Security Layers

1.  **Biometric Layer**: Initial hardware-level fingerprint verification using the Android Biometric API.
2.  **Pattern Dynamics**: A custom-built pattern lock that analyzes the speed and consistency of the drawing motion.
3.  **Keystroke Dynamics**: A password field that utilizes a custom **QIFNN (Quantum-Inspired Firefly Neural Network)** logic to verify typing rhythm.

## 🧠 The "Brain": QIFNN Logic

The core of the app's security lies in the `QIFNN.kt` engine. Inspired by the Firefly Algorithm's optimization capabilities, it features:
* **Asymmetric Bias**: Penalizes slow, hesitant input (intruder behavior) by 3.5x while remaining lenient toward fast, muscle-memory input (owner behavior).
* **Adaptive Learning**: Updates the user's profile after every successful login using an Alpha Learning Rate, mimicking the "attractiveness" and "movement" mechanics of the Firefly algorithm to stay aligned with the user.
* **Jitter Detection**: Uses Standard Deviation to identify inconsistent "shaky" input typical of non-genuine users.

## 🛡️ Privacy & Security

* **Zero-Knowledge Storage**: All patterns and passwords are saved as **SHA-256 hashes**.
* **Anti-Extraction**: `allowBackup` is disabled to prevent data theft via ADB or cloud backups.
* **Lifecycle Protection**: Activities auto-terminate on `onStop()` to prevent background bypass.
