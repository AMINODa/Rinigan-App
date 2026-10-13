# Rinigan | رينيغان

[English](#english) | [العربية](#العربية) | [Français](#français)

---

<a id="english"></a>
## English

**Rinigan** is an Android virtual camera module based on **Xposed / LSPosed / LSPatch**. It replaces the camera feed that apps receive with a local video, images or a network stream, and provides basic audio and playback controls.

### Main features

- Replace the camera feed with a local video
- Supports Camera1 / Camera2
- **Per-App Transformation Engine (v5.2)**: automatic per-app rotation, scaling and mirroring of the replacement stream
- Supports image replacement of photo results
- Supports microphone mute, audio replacement and original video sound
- Supports random playback, notification-bar switching and network streams
- Works with LSPosed, LSPatch, EdXposed and classic Xposed

### Per-App Transformation Engine (new in v5.2)

The universal transformation engine normalizes orientation and aspect ratio for **every** calling app automatically:

- **Universal default fallback** — unclassified apps get a portrait, full-frame FIT_CENTER stream
- **Automatic calling-app detection** — the requesting package is resolved at runtime (host process + Binder UID)
- **Per-app profiles** — rotation (0/90/180/270), scale mode (FIT_CENTER / CENTER_CROP / STRETCH), target resolution, horizontal/vertical flip, auto portrait normalization
- **OpenGL matrix relay** — frames are transformed before reaching the app's Surface; Camera1 preview buffers are transformed at byte level (NV21)

Configure per app in the new **Per-App** tab, via `app_profiles` in `cs_config.json`, or by dropping an `app_profiles.json` file in `DCIM/Camera1`:

```json
{
  "profiles": {
    "com.whatsapp": { "rotation": 0, "scaleMode": "FIT_CENTER", "normalizePortrait": true },
    "com.instagram.android": { "rotation": 90, "scaleMode": "CENTER_CROP" }
  }
}
```

### Basic usage

After installing and enabling the module, configure its scope for the target app. When you first open the target app's camera screen, prepare a video with the matching resolution and name it `virtual.mp4`.

Common path:

```
/Internal storage/DCIM/Camera1/virtual.mp4
```

If the target app cannot access public storage, use its private directory:

```
/Internal storage/Android/data/[target package]/files/Camera1/virtual.mp4
```

Reopen the target app and the camera feed will be replaced.

### Notes

Some apps or the system camera may fail to hook due to different implementations; if you see a black screen, glitched frames or wrong orientation, first check the video resolution, encoding format and file path.

This project is for learning, research and legal use only. Please comply with your local laws and regulations.

## العربية

**Rinigan** هو وحدة كاميرا افتراضية لأندرويد تعتمد على **Xposed / LSPosed / LSPatch**، تستبدل صورة الكاميرا التي تحصل عليها التطبيقات بفيديو محلي أو صور أو بث شبكي، مع أدوات أساسية للتحكم بالصوت والتشغيل.

### الميزات الرئيسية

- استبدال صورة الكاميرا بفيديو محلي
- دعم Camera1 / Camera2
- **محرّك التحويل لكل تطبيق (v5.2)**: تدوير وقياس وانعكاس تلقائي للبث البديل حسب كل تطبيق
- دعم استبدال نتائج التصوير بالصور
- دعم كتم الميكروفون واستبدال الصوت وصوت الفيديو الأصلي
- دعم التشغيل العشوائي والتبديل من شريط الإشعارات والبث الشبكي
- يعمل مع LSPosed و LSPatch و EdXposed و Xposed التقليدي

### محرّك التحويل لكل تطبيق (جديد في v5.2)

يقوم المحرك الشامل بضبط الاتجاه ونسبة الأبعاد تلقائيًا لكل تطبيق يستدعي الكاميرا:

- **قاعدة عامة افتراضية** — أي تطبيق غير مصنّف يحصل على بث عمودي كامل الإطار بنمط FIT_CENTER
- **كشف تلقائي للتطبيق الطالب** — تُحدَّد الحزمة الطالبة وقت التشغيل (عملية التطبيق + Binder UID)
- **ملفات تعريف لكل تطبيق** — دوران (0/90/180/270)، نمط القياس (FIT_CENTER / CENTER_CROP / STRETCH)، دقة الهدف، انعكاس أفقي/رأسي، تطبيع عمودي تلقائي
- **مرحّل مصفوفة OpenGL** — تُحوَّل الإطارات قبل وصولها إلى Surface الخاص بالتطبيق؛ وتُحوَّل مخازن معاينة Camera1 على مستوى البايت (NV21)

يُضبَط كل تطبيق من تبويب **لكل تطبيق** الجديد، أو عبر `app_profiles` في `cs_config.json`، أو بوضع ملف `app_profiles.json` في `DCIM/Camera1`.

### الاستخدام الأساسي

بعد تثبيت الوحدة وتفعيلها، اضبط نطاقها للتطبيق الهدف. عند أول دخول لكاميرا التطبيق الهدف، جهّز فيديو بنفس الدقة وسمّه `virtual.mp4`.

المسار الشائع:

```
/التخزين الداخلي/DCIM/Camera1/virtual.mp4
```

إذا لم يتمكن التطبيق الهدف من الوصول إلى التخزين العام، استخدم مجلده الخاص:

```
/التخزين الداخلي/Android/data/[حزمة التطبيق]/files/Camera1/virtual.mp4
```

أعد فتح التطبيق الهدف وستُستبدل صورة الكاميرا.

### ملاحظات

بعض التطبيقات أو كاميرا النظام قد لا تعمل مع الاعتراض بسبب اختلاف طريقة تنفيذها؛ عند ظهور شاشة سوداء أو تشويش أو اتجاه خاطئ، تحقق أولاً من دقة الفيديو وصيغة الترميز ومسار الملف.

هذا المشروع مخصص للتعلم والبحث والاستخدام القانوني فقط. يرجى الالتزام بالقوانين المحلية.

## Français

**Rinigan** est un module Android de caméra virtuelle basé sur **Xposed / LSPosed / LSPatch**. Il remplace le flux de la caméra reçu par les applications par une vidéo locale, des images ou un flux réseau, et fournit des contrôles audio et de lecture de base.

### Fonctionnalités principales

- Remplacer le flux de la caméra par une vidéo locale
- Prise en charge de Camera1 / Camera2
- **Moteur de transformation par application (v5.2)** : rotation, mise à l'échelle et miroir automatiques du flux de remplacement pour chaque application
- Remplacement des photos par des images
- Coupure du micro, remplacement audio et son d'origine de la vidéo
- Lecture aléatoire, changement via la barre de notification et flux réseau
- Compatible avec LSPosed, LSPatch, EdXposed et Xposed classique

### Moteur de transformation par application (nouveau en v5.2)

Le moteur universel normalise l'orientation et le ratio pour **toutes** les applications appelantes :

- **Repli universel** — toute application non classifiée reçoit un flux portrait plein cadre en FIT_CENTER
- **Détection automatique de l'application appelante** — le paquet demandeur est résolu à l'exécution (processus hôte + Binder UID)
- **Profils par application** — rotation (0/90/180/270), mode d'échelle (FIT_CENTER / CENTER_CROP / STRETCH), résolution cible, miroir horizontal/vertical, normalisation portrait auto
- **Relais matriciel OpenGL** — les images sont transformées avant d'atteindre la Surface de l'application ; les tampons d'aperçu Camera1 sont transformés au niveau octet (NV21)

Configurez chaque application dans le nouvel onglet **Par app**, via `app_profiles` dans `cs_config.json`, ou en déposant un fichier `app_profiles.json` dans `DCIM/Camera1`.

### Utilisation de base

Après l'installation et l'activation du module, configurez sa portée pour l'application cible. À la première ouverture de la caméra de l'application cible, préparez une vidéo à la résolution correspondante nommée `virtual.mp4`.

Chemin courant :

```
/Stockage interne/DCIM/Camera1/virtual.mp4
```

Si l'application cible ne peut pas accéder au stockage public, utilisez son dossier privé :

```
/Stockage interne/Android/data/[paquet de l'application]/files/Camera1/virtual.mp4
```

Rouvrez l'application cible : le flux de la caméra sera remplacé.

### Remarques

Certaines applications ou la caméra système peuvent ne pas être interceptées selon leur implémentation ; en cas d'écran noir, d'image corrompue ou d'orientation incorrecte, vérifiez d'abord la résolution vidéo, le format d'encodage et le chemin du fichier.

Ce projet est destiné à l'apprentissage, à la recherche et à un usage légal uniquement. Veuillez respecter les lois locales.

## License

MIT License — see [LICENSE](LICENSE).
