# تطبيق الآيفون (iOS) — قارئ الباركود المتعدد

تطبيق SwiftUI أصلي بنفس وظائف نسخة أندرويد (منتجات، زبائن، طلبيات، مدفوعات،
كاميرا/باركود، تسجيل دخول Google، قاعدة بيانات مشتركة مع رفع/سحب يدوي).

يُبنى تلقائيًا على GitHub Actions (خادم macOS) للتأكد من الترجمة. لتشغيله على
آيفون فعلي تحتاج جهاز **Mac + Xcode 16**.

## التشغيل على Mac

```bash
brew install xcodegen        # مرة واحدة
cd ios
xcodegen generate            # يولّد MultiBarcode.xcodeproj
open MultiBarcode.xcodeproj
```

في Xcode:
1. اختر هدف **MultiBarcode** → Signing & Capabilities → فعّل *Automatically manage
   signing* واختر حسابك (Apple ID مجاني يكفي للتجربة 7 أيام؛ حساب Apple Developer
   المدفوع للتوزيع عبر TestFlight).
2. وصّل آيفونك واختره كوجهة ثم اضغط Run.

## إعداد Firebase (لتفعيل الحساب والمزامنة)

بدون هذه الخطوة يعمل التطبيق **محليًا فقط** (بدون تسجيل دخول أو مشاركة).

1. في [Firebase Console](https://console.firebase.google.com) → نفس مشروع
   `baghdadi-testing` → أضف تطبيق **iOS** بمعرّف الحزمة `com.baghd.barcode`.
2. نزّل ملف **GoogleService-Info.plist** وأضفه إلى المشروع في Xcode
   (اسحبه إلى مجلد `Sources`، فعّل *Copy items if needed* و *Add to target*).
3. أضف **URL Scheme**: افتح الملف وانسخ قيمة `REVERSED_CLIENT_ID`، ثم في Xcode
   → Target → Info → URL Types → أضف نوعًا جديدًا وألصق القيمة في *URL Schemes*.
   (هذا ضروري لعمل تسجيل الدخول عبر Google.)
4. تأكد أن مزوّد **Google** مفعّل في Authentication، وأن قواعد Firestore المشتركة
   منشورة (نفس قواعد نسخة أندرويد).

## ملاحظات

- الحد الأدنى: iOS 16.
- الرموز من SF Symbols؛ العملة الشيكل ₪؛ الواجهة عربية RTL.
- المزامنة يدوية بالكامل: كل شيء يُحفظ محليًا، والرفع/السحب بالأزرار — مطابق لنسخة أندرويد.
