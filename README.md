# Concurrent Balance Service

سرویس مدیریت همزمان موجودی حساب‌های مالی، ساخته شده با Java 21 و Spring Boot.

## نحوه اجرا

### پیش‌نیازها
- نصب **Java 21 (JDK 21)** روی سیستم
- دسترسی به اینترنت در اولین اجرا برای دریافت نسخه موردنیاز Maven توسط Maven Wrapper و دانلود وابستگی‌های پروژه (Dependencies)

> پس از دانلود موفق Maven و تمام وابستگی‌ها، در اجراهای بعدی معمولاً نیازی به اینترنت نیست؛ مگر اینکه وابستگی جدیدی اضافه شود یا فایل‌های موردنیاز در کش محلی موجود نباشند.

### دستورات اجرا در ویندوز (PowerShell)

ساخت پروژه:

```powershell
.\mvnw.cmd clean package
```

اجرای پروژه:

```powershell
.\mvnw.cmd spring-boot:run
```

### نکات
- نیازی به نصب جداگانه Maven نیست؛ پروژه از **Maven Wrapper** استفاده می‌کند.
- نیازی به راه‌اندازی دیتابیس خارجی نیست؛ پروژه از **H2 In-Memory Database** استفاده می‌کند.
- برای اطمینان از نصب صحیح Java 21، دستورات زیر را اجرا کنید:

```powershell
java -version
javac -version
```

هر دو دستور باید نسخه 21 را نمایش دهند.

## Architecture

پروژه از معماری لایه‌ای با جداسازی واضح مسئولیت‌ها پیروی می‌کند:

```
src/main/java/net/roozbe/mofidassessment/
├── controller/          # لایه REST API (اعتبارسنجی ورودی، mapping به HTTP)
│   ├── request/         # Request DTOها با Jakarta Validation
│   └── response/        # Response DTOها
├── service/             # لایه Business Logic (BalanceService interface + impl)
│   └── model/           # مدل‌های لایه سرویس
├── da/                  # لایه Data Access
│   ├── entity/          # JPA Entityها (AccountEntity, TransactionEntity)
│   └── repository/      # Spring Data Repositoryها
├── mapper/              # MapStruct Mapperها (Entity ↔ Model ↔ DTO)
└── exception/           # کلاس‌های Exception سفارشی
```

**اجزای اصلی:**
- **`BalanceService` interface** — قرارداد سرویس مطابق با مشخصات challenge: عملیات `credit`، `debit`، `transfer`، `getBalance`
- **`BalanceServiceImpl`** — پیاده‌سازی تمام business logic شامل کنترل Concurrency و Idempotency
- **`AccountEntity`** — نماینده حساب کاربر با فیلد `@Version` برای Optimistic Locking و متدهای `credit()`/`debit()` برای حفظ invariantها (رویکرد Rich Domain Model)
- **`TransactionEntity`** — ثبت هر transaction ID پردازش‌شده، تضمین Idempotency از طریق unique constraint
- **`GlobalExceptionHandler`** — ترجمه domain exceptionها به HTTP responseهای مناسب

## Concurrency

### چطور Thread Safety تضمین شده؟

Thread Safety از طریق **Pessimistic Locking** در سطح دیتابیس با `SELECT ... FOR UPDATE` تضمین شده:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM AccountEntity a WHERE a.id = :id")
Optional<AccountEntity> findByIdWithLock(@Param("id") String id);
```

وقتی یک thread قفل یک ردیف account را به دست می‌آورد، تمام threadهای دیگر که می‌خواهند همان account را تغییر دهند **تا آزاد شدن قفل** (در زمان commit/rollback) **منتظر می‌مانند**.

### چرا این روش؟

1. **Correctness بر Performance ارجحیت دارد** — در یک سیستم مالی، صحت عملیات قابل مذاکره نیست. Pessimistic Locking تضمین می‌کند که چرخه read-modify-write در سطح دیتابیس atomic باشد.
2. **سادگی** — در مقایسه با Optimistic Locking با منطق retry، این روش ساده و قطعی است. هیچ retry storm در شرایط contention بالا ایجاد نمی‌شود.
3. **گرانولاریتی در سطح Account** — قفل روی ردیف individual account است، نه یک global lock. عملیات روی حساب‌های مستقل به صورت parallel پردازش می‌شوند بدون اینکه یکدیگر را block کنند.

### در شرایط Concurrent چه اتفاقی برای عملیات روی یک Account می‌افتد؟

```
Thread 1 → debit(A, 700, "TX-1")    [قفل A را می‌گیرد]
Thread 2 → debit(A, 700, "TX-2")    [منتظر آزاد شدن قفل A]
Thread 1 → commit                   [قفل آزاد می‌شود، balance = 300]
Thread 2 → قفل را می‌گیرد            [balance = 300 را می‌خواند، 300 < 700 → InsufficientBalanceException]
```

این مکانیزم جلوی Negative Balance، Lost Update و Incorrect Balance را می‌گیرد.

### Trade-offها

- **Blocking در شرایط Contention:** عملیات پرتکرار روی یک account به صورت serialize اجرا می‌شوند. این برای صحت عملیات در یک سیستم مالی قابل قبول است و در بیشتر workloadهای واقعی که contention روی یک account متوسط است مشکلی ایجاد نمی‌کند.
- **کاهش احتمالی throughput به ازای هر account** در مقایسه با رویکردهای lock-free، اما با تضمین consistency.

## Idempotency

### چطور از اجرای چندباره یک Transaction جلوگیری شده؟

هر عملیات یک `transactionId` دریافت می‌کند. قبل از پردازش، سرویس بررسی می‌کند آیا این ID قبلاً در جدول `TB_TRANSACTION` وجود دارد:

```java
if (transactionRepository.existsByTransactionId(transactionId)) {
    return; // درخواست تکراری را بدون خطا نادیده می‌گیرد
}
```

پس از پردازش موفق، transaction ID با یک **unique constraint** روی ستون `transactionId` ثبت می‌شود.

### اگر یک Transaction به صورت همزمان چند بار دریافت شود چه اتفاقی می‌افتد؟

unique constraint روی `transactionId` به عنوان آخرین خط دفاعی عمل می‌کند:

1. **درخواست اول** — بررسی Idempotency پاس می‌شود، قفل account گرفته می‌شود، پردازش و ثبت transaction → موفق.
2. **درخواست تکراری همزمان** — دو سناریو:
   - اگر بررسی Idempotency *بعد از* commit درخواست اول اجرا شود → `existsByTransactionId` مقدار `true` برمی‌گرداند → بدون خطا برمی‌گردد.
   - اگر `save` مربوط به `TransactionEntity` به صورت همزمان اجرا شود → نقض unique constraint باعث rollback کل transaction می‌شود و balance بدون تغییر باقی می‌ماند.

این طراحی تضمین می‌کند که حتی در تنگ‌ترین race conditionها، یک transaction **دقیقاً یک بار** اعمال شود.

### Trade-offها

- **هزینه ذخیره‌سازی:** هر transaction ID برای همیشه ذخیره می‌شود. در یک سیستم production، رکوردهای قدیمی می‌توانند بعد از یک TTL بایگانی یا حذف شوند.
- **بازگشت بدون خطا برای درخواست تکراری:** سرویس برای درخواست‌های تکراری HTTP 200 برمی‌گرداند که رفتار مورد انتظار برای عملیات idempotent است. گزینه جایگزین بازگشت HTTP 409 بود، اما بازگشت موفق با الگوهای retry-friendly سمت client سازگارتر است.

## Transfer

### چطور Atomicity تضمین شده؟

عملیات Transfer در یک `@Transactional` واحد اجرا می‌شود. هم debit از حساب مبدأ و هم credit به حساب مقصد بخشی از یک database transaction واحد هستند. اگر هر بخشی fail شود (موجودی ناکافی، حساب ناشناخته، یا هر runtime error)، **کل transaction به حالت قبل بازمی‌گردد (rollback)** — هیچ وضعیت ناقصی هرگز قابل مشاهده نیست.

```java
@Transactional
public void transfer(String sourceAccountId, String destinationAccountId, long amount, String transactionId) {
    // ... validation, بررسی idempotency ...
    // قفل هر دو account، debit از مبدأ، credit به مقصد
    // ثبت transaction
    // همه یا هیچ: commit یا rollback
}
```

### آیا احتمال Deadlock وجود دارد؟

**خیر.** از Deadlock با **گرفتن همیشگی قفل‌ها به ترتیب مشخص (deterministic)** جلوگیری شده — مرتب‌شده بر اساس `accountId`:

```java
String firstId = sourceAccountId.compareTo(destinationAccountId) < 0 ? sourceAccountId : destinationAccountId;
String secondId = sourceAccountId.compareTo(destinationAccountId) < 0 ? destinationAccountId : sourceAccountId;

AccountEntity firstAccount = accountRepository.findByIdWithLock(firstId);
AccountEntity secondAccount = accountRepository.findByIdWithLock(secondId);
```

بدون این ترتیب‌بندی، دو transfer همزمان `A→B` و `B→A` می‌توانستند Deadlock ایجاد کنند:
- Thread 1 قفل A را می‌گیرد، منتظر B می‌ماند
- Thread 2 قفل B را می‌گیرد، منتظر A می‌ماند

با ترتیب‌بندی sorted، هر دو thread اول A را قفل می‌کنند، سپس B — یکی منتظر می‌ماند، دیگری ادامه می‌دهد.

### Same-Account Transfer

انتقال از یک حساب به خودش (`transfer(A, A, 100, "TX-1")`) با `SameAccountTransferException` **رد می‌شود**. این یک تصمیم طراحی بر اساس این اصل است که same-account transfer یک عملیات بی‌اثر (no-op) است و احتمالاً نشان‌دهنده خطای client است. همچنین از مشکل فنی تلاش برای قفل کردن دوباره همان ردیف جلوگیری می‌کند.

## Validation

اعتبارسنجی‌های زیر اعمال شده‌اند:

| اعتبارسنجی | لایه | پاسخ |
|---|---|---|
| `amount <= 0` | Entity + DTO (`@Positive`) | HTTP 400 |
| حساب ناشناخته | Service | HTTP 404 |
| موجودی ناکافی | Entity | HTTP 409 |
| Same-account transfer | Service | HTTP 400 |
| فیلدهای خالی | DTO (`@NotBlank`) | HTTP 400 |

## نوع داده: `long` برای Amount/Balance

`long` مطابق با interface مشخص‌شده در challenge برای مقادیر پولی استفاده شده. مزایا:
- **بدون مشکلات دقت floating-point** — محاسبات integer دقیق برای پول
- **محدوده:** ±9.2 × 10¹⁸ — برای هر سناریوی واقعی بیش از کافی
- **Performance:** نوع primitive، بدون سربار object

در production، اگر واحدهای کسری مورد نیاز باشد (مثلاً سنت)، قرارداد رایج ذخیره مبالغ در کوچک‌ترین واحد ارزی است (مثلاً 10.50 به صورت 1050 سنت ذخیره شود).

## انتخاب‌های فناوری

| فناوری | چرا؟ | چه مسئله‌ای را حل می‌کند؟ | Trade-offها |
|---|---|---|---|
| **H2 (in-memory)** | دیتابیس بدون نیاز به setup برای challenge | فراهم کردن SQL semantics، transaction و row-level locking بدون dependency خارجی | داده‌ها با restart از بین می‌روند؛ مناسب production نیست |
| **Spring Data JPA** | الگوی declarative repository | ساده‌سازی data access، پشتیبانی از `@Lock` برای pessimistic locking | سربار اندک abstraction نسبت به raw JDBC |
| **MapStruct** | mapping در زمان compile | mapping بدون reflection و type-safe بین لایه‌ها | dependency اضافه در build-time |
| **Lombok** | کاهش boilerplate | تولید getter، setter و constructor در زمان compile | نیاز به plugin در IDE |

## REST API

```
POST   /api/v1/accounts                    → ایجاد حساب
POST   /api/v1/accounts/{id}/credits       → واریز (Credit)
POST   /api/v1/accounts/{id}/debits        → برداشت (Debit)
POST   /api/v1/accounts/transfers          → انتقال (Transfer)
GET    /api/v1/accounts/{id}/balance        → دریافت موجودی
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

## موارد پیاده‌سازی شده

- [x] Credit, Debit, Transfer, GetBalance
- [x] کنترل Concurrency (Pessimistic Locking)
- [x] Idempotency (ردیابی transaction با unique constraint)
- [x] Transfer Atomicity (یک `@Transactional` واحد، ترتیب sorted برای lock)
- [x] جلوگیری از Deadlock (قفل‌گیری مرتب‌شده بر اساس accountId)
- [x] اعتبارسنجی ورودی (amount، وجود account، same-account transfer)
- [x] REST API با error responseهای مناسب
- [x] تست‌های Unit، Idempotency و Concurrency
- [x] Rich Domain Model (invariantها در Entity)

## اگر زمان بیشتری داشتم

- **Audit Log / Event Sourcing:** ثبت تغییرات balance به صورت event stream برای قابلیت ردیابی کامل
- **Distributed Locking:** برای deploy چند instance، استفاده از Redis-based distributed lock یا database advisory lock
- **Rate Limiting:** محافظت در برابر سوءاستفاده روی endpointهای پرتکرار
- **Metrics:** افزودن Micrometer metrics برای throughput، latency و نرخ failure تراکنش‌ها
- **Containerization:** Dockerfile + docker-compose برای محیط یکسان
- **Database Migration:** Flyway/Liquibase برای نسخه‌بندی schema در production
