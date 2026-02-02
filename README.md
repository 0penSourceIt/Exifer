<h1 align="center">
💀 EXIFER 💀
</h1>
<p align="center">
<code>[ GPS • DEVICE • OWNER • PAYLOAD • HASH ]</code>
</p>

Exifer is a next-generation **cybersecurity-focused EXIF and metadata forensic analyzer** for Android.

It is designed to help users, investigators, journalists, and privacy-aware individuals detect:

- Hidden metadata leaks
- Location exposure (GPS stalking risk)
- Device fingerprinting identifiers
- Editing/tampering traces
- Embedded payload signatures (ZIP/PDF/DEX/ELF)
- Integrity hashes (SHA-256)
- High entropy steganography suspicion

This is not just an EXIF viewer — it is a **mobile forensic intelligence scanner**.

---

## 🚀 Key Features

### ✅ Complete Metadata Extraction
- Full Android EXIF tag sweep
- MakerNote extraction (Canon, Nikon, Sony, Olympus, Panasonic, Fujifilm)
- Deep metadata parsing:
    - XMP
    - IPTC
    - ICC Profiles

### 🛰 GPS & Privacy Leak Detection
- Decimal coordinate extraction
- Location exposure risk classification

### 🔥 Cybersecurity Intelligence Layer
- Metadata Risk Score (0–100)
- Tampering detection (Photoshop/Snapseed/Lightroom traces)
- SHA-256 file fingerprinting
- Entropy scoring for steganography suspicion

### ⚠ Embedded Payload Scanner
Detects possible hidden content inside images:

- ZIP archives
- PDF documents
- DEX/APK payloads
- ELF executables

Includes:
- Head scan
- Tail scan
- Full sliding-window sweep (RAM-safe)

### 🧹 EXIF Removal / Sanitization
One-tap metadata stripping via bitmap re-encoding:

- Removes GPS, author, serials, identifiers
- Outputs clean image to Downloads

### 📄 PDF Forensic Report Export
Generate a full metadata intelligence report:

- Paginated PDF output
- Saved automatically in Downloads/Exif_Data_Reports

### 🖥 Hacker-Style UI Console
- Categorized forensic breakdown
- Full datastream mode
- Smart search with synonyms + fuzzy matching
- Quick Intel summary panel

---

## 🛠 Tech Stack

- **Kotlin + Jetpack Compose**
- AndroidX ExifInterface
- Drew Noakes Metadata Extractor
- iText PDF Export Engine

---

## 🔍 Forensic Categories Included

- GPS / Location Intelligence
- Device Identification & Fingerprinting
- Date & Timeline Reconstruction
- Software / Editing History
- Owner & Identity Leakage
- Embedded Thumbnail Traces
- Unique File Identifiers
- Network / Source Metadata
- Camera Forensic Settings
- Payload & Security Signals

---

## 📌 Transparency & AI Attribution

This project is **fully open source** and built with transparency.

### AI Tools Used During Development

This application was developed with assistance from:

- **Google Gemini Pro**
- **OpenAI GPT GO**

AI was used as a development accelerator for:

- Code structuring
- Metadata extraction logic
- Forensic feature expansion
- UI component scaffolding
- Iconography assistance

### Human Design & Prompt Direction

While AI contributed to implementation support, the **UI concept, design direction, and feature vision were provided by the human prompter/developer**.

All final decisions, integrations, and architecture were curated manually.

---

## ⚖ Disclaimer

Exifer is provided for:

- Privacy awareness
- Personal security auditing
- Digital forensic education
- Metadata leak prevention

This tool is **not intended for illegal surveillance or misuse**.  
Users are responsible for complying with local laws and ethical practices.

---

## 📂 Project Status

✅ Stable Release  
🚀 Open for Contributions  
🔧 Actively Expanding Forensic Capabilities

---

## 🤝 Contributions

Pull requests are welcome.

If you want to add:

- Steganography deep analysis
- Batch multi-image navigation
- JSON forensic export
- Threat scoring improvements

Feel free to contribute.

---

## ⭐ Support

If you find Exifer useful, please consider starring the repository.

> Privacy is not optional. Metadata is a weapon.

---

## 📜 License (Community Non-Commercial)

This project is released as **free community software**.

You are allowed to:

✅ Use the code  
✅ Modify the code  
✅ Share and redistribute  
✅ Build upon it for educational or community benefit

You are **NOT allowed** to:

❌ Sell this project or its forks  
❌ Use it in paid/commercial products  
❌ Repackage it as a commercial EXIF tool

### License Type: CC BY-NC 4.0

This repository is licensed under:

**Creative Commons Attribution-NonCommercial 4.0 International**

You must give credit, and any usage must remain strictly non-commercial.

🔗 https://creativecommons.org/licenses/by-nc/4.0/
