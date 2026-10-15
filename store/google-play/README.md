# scrmoods on Google Play

Everything needed to publish the app, in order. Texts to paste are below.
Files in this folder: `icon-512.png` (app icon), `feature-graphic-1024x500.png` and `-ru.png` (the banner on the store page).

## How the app is built for Google Play

The project now builds two versions of the same app:

| | GitHub version (`github`) | Google Play version (`play`) |
|---|---|---|
| Built as | `scrmoods.apk` (Releases → latest) | `scrmoods-play.aab` (Actions → the run → Artifacts → `scrmoods-play-bundle`) |
| Updates | downloads them itself from GitHub | Google Play updates it |
| Internet | only to check for updates | none at all |
| Exact reminders | allowed automatically | the person allows "Alarms & reminders" (Settings in the app shows a link) |
| Signed with | the key in the repo | your private upload key (GitHub secrets), then Google's key |

The two versions are signed differently, so one can't update the other. To move from the GitHub version to the Play
version: Settings → Export data → uninstall → install from Google Play → Import data.

## Step by step

1. **Developer account.** Go to <https://play.google.com/console>, sign up as an individual (one-time fee of $25),
   and verify your identity. Georgia supports developer accounts but not merchant (paid) accounts, so the app is free for now.
2. **Upload key.** Add the four secrets from `README-upload-key.txt` (sent separately, kept outside the repo) in GitHub:
   the `scrmoods` repository → Settings → Secrets and variables → Actions.
3. **Build.** Push the repo. In Actions, open the run and download the `scrmoods-play-bundle` artifact (a zip with `scrmoods-play.aab`).
4. **Create the app** in Play Console: name `scrmoods`, default language English (United States) — add Russian later under
   *Store listing → Manage translations*, App, Free.
5. **App content** (Policy → App content), answers below: privacy policy, ads, app access, content rating,
   target audience, data safety, health apps, government apps, financial features.
6. **Store listing:** texts below, `icon-512.png`, the feature graphic, and at least 2 phone screenshots
   (take them on the phone: journal, quick check-in, trends, medication planner, summary).
7. **Closed testing.** Testing → Closed testing → create a track → upload `scrmoods-play.aab` → add testers
   (email list or a Google Group). New personal accounts need **at least 12 testers who stay opted in for 14 days in a row**
   before production access. Share the opt-in link with friends and family; each must accept and install from Play.
8. **Production.** After 14 days, Dashboard → *Apply for production*, answer the short questions about the test,
   then create a production release with the latest bundle.

## Store listing — English

**App name (≤30):** scrmoods — mood journal

**Short description (≤80):**
A gentle daily mood journal: mood, sleep, water, meals and medications.

**Full description:**
scrmoods is a calm, private journal for tracking how you feel — made by someone who tracks their own mood every day.

• A mood scale from 0 to 10, with mixed days, and simple daily questions
• Quick check-in: one question at a time, then a good-night wish
• Sleep: a morning question in the widget, a notification or the journal
• Water and meals with cute drops and bowls; medications with little pills
• Medication planner (optional): courses with dates, ¼ and ½ doses, dose changes in stages, and reminders
• Trends and a summary in plain words, with the patterns in your own answers — like "on days you reached your water goal, your outlook was better"
• A gentle heads-up when something has been off for a week
• Home-screen widget for quick notes, +1 water and +1 meal
• Themes, English and Russian

Private by design: no account, no ads, no analytics, and no internet access. Everything stays on your phone; export your data as a file whenever you like.

scrmoods is not a medical device and doesn't give medical advice. Please talk to your doctor about your treatment.

## Store listing — Russian

**Название (≤30):** scrmoods — дневник настроения

**Краткое описание (≤80):**
Бережный дневник настроения: сон, вода, еда и лекарства каждый день.

**Полное описание:**
scrmoods — спокойный личный дневник, чтобы отмечать, как вы себя чувствуете. Его сделал человек, который сам каждый день отслеживает своё настроение.

• Шкала настроения от 0 до 10, смешанные дни и простые ежедневные вопросы
• Быстрая отметка: по одному вопросу, а в конце пожелание спокойной ночи
• Сон: утренний вопрос в виджете, уведомлении или дневнике
• Вода и еда — милые капли и миски; лекарства — маленькие таблетки
• Планер лекарств (по желанию): курсы с датами, дозы ¼ и ½, смена дозы по этапам и напоминания
• Тренды и итоги простыми словами, с закономерностями в ваших ответах — например, «в дни, когда вы выпивали норму воды, настрой был лучше»
• Бережное напоминание, если что-то не так уже неделю
• Виджет на главный экран: быстрая заметка, +1 стакан воды и +1 приём пищи
• Темы оформления, английский и русский

Конфиденциально: без аккаунта, рекламы, аналитики и доступа к интернету. Всё хранится на вашем телефоне; данные можно в любой момент выгрузить в файл.

scrmoods не является медицинским изделием и не даёт медицинских советов. Обсуждайте лечение с врачом.

## App content answers

- **Privacy policy URL:** `https://scroup.github.io/scrmoods-web/privacy.html` (the page `privacy.html` in the scrmoods-web repo; check the address opens after pushing).
- **Ads:** No, the app has no ads.
- **App access:** All functionality is available without special access (no login).
- **Content rating:** category *Health & Fitness* / reference questionnaire: no violence, sexual content, profanity, drugs, gambling, user-to-user communication or location sharing. Expected rating: Everyone / PEGI 3 (the mental-health topic doesn't change it).
- **Target audience:** 18 and over. Not designed for children.
- **Data safety:**
  - Does your app collect or share any of the required user data types? **No.**
  - (All data stays on the device; the Play version has no internet permission. Android's own device backup is not collection by the developer.)
  - Is all of the user data encrypted in transit? — not applicable (nothing is transmitted).
  - Do you provide a way for users to request that their data is deleted? — data is deleted when the app is uninstalled or its data is cleared.
- **Health apps declaration:** the app has health features: *mental health / mood tracking*, *medication and treatment management* (reminders, planner), *sleep*, *nutrition / hydration*. It does not connect to medical devices, is not a medical device, and doesn't provide diagnosis or treatment. No Health Connect.
- **Government apps:** No. **Financial features:** None.
- **Category:** Health & Fitness. **Tags:** mood tracker, journal, mental health, habit tracker.
- **Contact details:** an email address is required and shown on the store page; a website is optional (the GitHub page).

## Later: earning from the app

Google Play can't pay developers in Georgia (merchant accounts aren't supported there), so the app starts free.
Options later: a merchant account in a supported country, or keeping the app free and adding a voluntary
support link outside the app's features (check Google Play's payments policy before adding any link).
