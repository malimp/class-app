# کلاس (ClassApp) — V1.8

برنامه ساده مدیریت آموزشگاه زبان (اندروید، جاوا، بدون وابستگی خارجی).

## ساخت
1. پوشه را در Android Studio (نسخه‌ای که AGP 8.6 را پشتیبانی کند) باز کنید؛ JDK 17 لازم است.
2. `gradle-wrapper.properties` موجود است ولی `gradle-wrapper.jar` و اسکریپت `gradlew` نیست.
   Android Studio خودش Gradle را دانلود می‌کند. برای خط فرمان یک‌بار `gradle wrapper` بزنید.
3. Build > Build APK(s).

## ساختار
- `MainActivity.java` — صفحه‌ها و جریان‌ها
- `Store.java` — داده، تنظیمات (معلم‌ها، توضیحات آماده)، قوانین یکپارچگی، پشتیبان
- `ReportForm.java` — فرم گزارش روزانه و تولید متن
- `Reports.java` — خواندن معلم/کلاس از متن، تشخیص تکراری
- `Merge.java` — ادغام پشتیبان بدون حذف
- `Summary.java` — خلاصه آماری (غیبت، تأخیر، موارد مشابه)
- `XlsxReader.java` — خواندن .xlsx
- `JalaliDate.java`, `Text.java` — تاریخ و نرمال‌سازی فارسی

## ساخت APK بدون نصب Android Studio (GitHub Actions)
1. یک مخزن GitHub بسازید و **محتوای همین پوشه** را در ریشه مخزن آپلود کنید (نه پوشه والد).
2. تب Actions > «Build APK» > Run workflow.
3. پس از پایان، فایل `ClassApp-debug-apk` را از بخش Artifacts دانلود و روی گوشی نصب کنید
   (نصب از منبع ناشناس را برای مرورگر/فایل‌منیجر فعال کنید).
