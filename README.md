# Multi Barcode Reader — قارئ الباركود المتعدد

تطبيق أندرويد بلغة **Kotlin** يقرأ عدة أكواد باركود و **QR** في وقت واحد (مثلاً ١٠ منتجات
موجودة معاً أمام الكاميرا)، يرسم مربعاً حول كل كود ويعرض قيمتها جميعاً على الشاشة لحظياً.

An Android app (Kotlin) that detects **many barcodes / QR codes at the same time**, draws a
box around each one, and lists every decoded value live on screen.

## كيف يعمل / How it works

- **CameraX** يوفّر معاينة الكاميرا وتدفّق الإطارات (`PreviewView` + `ImageAnalysis`).
- **ML Kit Barcode Scanning** (نموذج مضمّن يعمل دون إنترنت) يحلّل كل إطار ويعيد **كل** الأكواد
  الظاهرة فيه دفعة واحدة — وهذا بالضبط ما يلزم لمسح عدة منتجات معاً.
- `BarcodeOverlayView` يرسم مربعاً وملصقاً بالقيمة فوق كل كود، مع تحويل إحداثيات الصورة إلى
  إحداثيات الشاشة بنفس طريقة `FILL_CENTER`.
- مثبّت بسيط (stabilizer) يُبقي كل كود ظاهراً لفترة قصيرة بعد آخر التقاط لتقليل الوميض.

## البنية / Project structure

| File | Purpose |
|------|---------|
| `MainActivity.kt` | الأذونات، إعداد CameraX، تجميع النتائج وعرضها |
| `BarcodeAnalyzer.kt` | محلّل ML Kit الذي يعيد كل أكواد الإطار |
| `BarcodeOverlayView.kt` | رسم المربعات والملصقات فوق المعاينة |
| `BarcodeResultAdapter.kt` | قائمة بالقيم المقروءة أسفل الشاشة |
| `DetectedBarcode.kt` | نماذج بيانات خفيفة للواجهة |

## المتطلبات / Requirements

- Android Studio (Giraffe أو أحدث) و JDK 17.
- `minSdk 21`, `targetSdk 34`.
- جهاز/محاكي بكاميرا خلفية.

## التشغيل / Build & run

```bash
./gradlew assembleDebug      # build the APK
./gradlew installDebug       # install on a connected device
```

ثم وجّه الكاميرا نحو عدة أكواد في آنٍ واحد — سيظهر مربع مرقّم حول كل كود وتظهر القيم في
القائمة السفلية مع عدّاد لعددها.

> ملاحظة: مسح عدة أكواد صغيرة معاً يتطلب دقة كافية؛ قرّب الكاميرا أو استخدم زر الإضاءة عند
> الحاجة. عدد الأكواد غير محدود برقم ثابت — ML Kit يقرأ كل ما يستطيع تمييزه في الإطار.
