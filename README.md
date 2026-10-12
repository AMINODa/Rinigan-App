# Rinigan | رينيغان

[English](#english) | [العربية](#العربية) | [Français](#français)

---

<a id="english"></a>
## English

**Rinigan** is an Android virtual camera module based on **Xposed / LSPosed / LSPatch**. It replaces the camera feed that apps receive with a local video, images or a network stream, and provides basic audio and playback controls.

### Main features

- Replace the camera feed with a local video
- Supports Camera1 / Camera2
- Supports image replacement of photo results
- Supports microphone mute, audio replacement and original video sound
- Supports random playback, notification-bar switching and network streams
- Works with LSPosed, LSPatch, EdXposed and classic Xposed

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
- دعم استبدال نتائج التصوير بالصور
- دعم كتم الميكروفون واستبدال الصوت وصوت الفيديو الأصلي
- دعم التشغيل العشوائي والتبديل من شريط الإشعارات والبث الشبكي
- يعمل مع LSPosed و LSPatch و EdXposed و Xposed التقليدي

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
- Remplacement des photos par des images
- Coupure du micro, remplacement audio et son d'origine de la vidéo
- Lecture aléatoire, changement via la barre de notification et flux réseau
- Compatible avec LSPosed, LSPatch, EdXposed et Xposed classique

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
