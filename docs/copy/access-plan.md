# Access copy plan (auth, onboarding, Settings)

Draft by the Copy Lead for the French and English Editors. Nothing here is applied yet.

## Summary

74 owned keys, each listed once.

| Decision | Keys |
|---|---:|
| keep | 47 |
| shorten | 11 |
| unused | 8 |
| delete | 5 |
| show-in-UI | 2 |
| merge | 1 |
| **Total** | **74** |

10 keys need a layout or behaviour change, so the String Steward cannot apply them alone. Their last column starts with `Yes`.

No access key is used only by a `@Preview`. The 8 `unused` keys have no reference in Kotlin or XML, and none is tagged `future`.

## 1. Welcome and sign-in

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `app_name` | keep | Plan Montréal | Plan Montreal | Plan Montréal | Plan Montreal | Settled name | No |
| `welcome_title` | shorten | Votre sortie, sur mesure. | Your outing, made to measure. | Votre sortie, sur mesure | Your outing, made to measure | Titles take no period; see question 2 | No |
| `welcome_tagline` | shorten | Planifiez votre sortie, suivez-la arrêt par arrêt, et collectionnez vos timbres. | Plan your outing, follow it stop by stop, and collect your stamps. | Planifiez, suivez chaque arrêt, collectionnez vos timbres. | Plan, follow each stop, collect your stamps. | The title already says "sortie" | No |
| `auth_continue_with_google` | keep | Continuer avec Google | Continue with Google | Continuer avec Google | Continue with Google | Clear action | No |
| `label_or` | keep | ou | or | ou | or | Separates two sign-in methods | No |
| `auth_email` | keep | Courriel | Email | Courriel | Email | Field label | No |
| `auth_password` | keep | Mot de passe | Password | Mot de passe | Password | Field label | No |
| `auth_forgot_password` | keep | Mot de passe oublié? | Forgot password? | Mot de passe oublié? | Forgot password? | Clear action | No |
| `auth_sign_in` | keep | Se connecter | Sign in | Se connecter | Sign in | Clear action | No |
| `auth_create_account` | keep | Créer un compte | Create an account | Créer un compte | Create an account | Welcome action and Register title | No |

## 2. Authentication errors

Shown on Welcome, Register and Password reset. All must stay, with a next action.

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `auth_error_invalid_credentials` | keep | Courriel ou mot de passe incorrect. | Incorrect email or password. | Courriel ou mot de passe incorrect. | Incorrect email or password. | Fields and reset link give the next action | No |
| `auth_error_invalid_email` | shorten | Ce courriel n’est pas valide. | This email isn’t valid. | Entrez un courriel valide. | Enter a valid email. | States the next action | No |
| `auth_error_email_in_use` | shorten | Un compte existe déjà pour ce courriel. Connectez-vous ou continuez avec Google. | An account already exists for this email. Sign in or continue with Google. | Ce courriel est déjà utilisé. Connectez-vous ou continuez avec Google. | This email already has an account. Sign in or continue with Google. | Fewer words, same next action | No |
| `auth_error_weak_password` | shorten | Choisissez un mot de passe d’au moins 6 caractères. | Choose a password with at least 6 characters. | Mot de passe : au moins 6 caractères. | Password must be at least 6 characters. | Keeps the requirement | No |
| `auth_error_network` | keep | Connexion impossible. Vérifiez votre réseau et réessayez. | Can’t connect. Check your network and try again. | Connexion impossible. Vérifiez votre réseau et réessayez. | Can’t connect. Check your network and try again. | Cause and next action | No |
| `auth_error_provider_disabled` | shorten | La connexion par courriel n’est pas encore activée. | Email sign-in isn’t enabled yet. | Mode de connexion indisponible. Essayez-en un autre. | This sign-in method isn’t available. Try another. | Adds the missing next action; the Google flow can also raise this error, so "courriel" can be wrong | No |
| `auth_error_google` | keep | La connexion avec Google a échoué. Réessayez. | Google sign-in failed. Try again. | La connexion avec Google a échoué. Réessayez. | Google sign-in failed. Try again. | Cause and next action | No |
| `auth_error_unknown` | keep | Une erreur est survenue. Réessayez. | Something went wrong. Try again. | Une erreur est survenue. Réessayez. | Something went wrong. Try again. | Fallback with next action | No |

## 3. Register

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `register_one_account_note` | delete | Un seul compte par courriel : Google ou mot de passe mènent au même profil, itinéraires et passeport. | One account per email: Google or password sign-in reach the same profile, itineraries and passport. | — | — | Only matters on a conflict, and `auth_error_email_in_use` covers that moment | Yes: remove the `Text` in `ScreenRegister.kt:62` |
| `auth_password_confirm` | keep | Confirmer le mot de passe | Confirm password | Confirmer le mot de passe | Confirm password | Field label | No |
| `auth_error_password_mismatch` | keep | Les mots de passe ne correspondent pas. | Passwords don’t match. | Les mots de passe ne correspondent pas. | Passwords don’t match. | Inline error on the field to fix | No |
| `register_submit` | keep | Créer mon compte | Create my account | Créer mon compte | Create my account | Clear action | No |

## 4. Password reset

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `password_reset_title` | shorten | Réinitialiser le mot de passe | Reset password | Mot de passe oublié | Reset password | One-line app bar: the French risks truncation at 360 dp and 1.3x | No |
| `password_reset_body` | delete | Entrez votre courriel et nous vous enverrons un lien pour choisir un nouveau mot de passe. | Enter your email and we’ll send you a link to choose a new password. | — | — | Title, Email field and "Envoyer le lien" already say this | Yes: remove the `Text` in `ScreenPasswordReset.kt:57` |
| `password_reset_send` | keep | Envoyer le lien | Send reset link | Envoyer le lien | Send reset link | Clear action | No |
| `password_reset_sent` | shorten | Si un compte existe pour ce courriel, le lien vient d’être envoyé. | If an account exists for this email, the link has been sent. | Lien envoyé si un compte existe pour ce courriel. | If this email has an account, we’ve sent a link. | Keeps the condition, so it never reveals who has an account | No |

## 5. Onboarding step 1: Visitor type

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `onboarding_step` | show-in-UI | Étape %1$d de %2$d | Step %1$d of %2$d | Étape %1$d sur %2$d | Step %1$d of %2$d | Progress bar already shows the step; text stays as its accessibility description, in the settled "sur" form | Yes: stop passing it as the app bar title on the 4 onboarding pages and in the `AppChrome.kt:155` preview; keep it in `StepProgress` |
| `onboarding_visitor_type_title` | keep | Comment explorez-vous la ville? | How are you exploring the city? | Comment explorez-vous la ville? | How are you exploring the city? | Names the task | No |
| `onboarding_visitor_type_subtitle` | delete | Choisissez votre profil d’exploration. | Choose how you explore. | — | — | Repeats the title | Yes: remove `BodyText` at `OnboardingPages.kt:69` |
| `visitor_title` | keep | Visiteur·euse | Visitor | En visite | Visitor | Glossary term | No |
| `visitor_description` | delete | J’explore une ville où je n’habite pas | I’m exploring a city I don’t live in | — | — | Visitor and Resident explain each other | Yes: make `HorizontalInfoCard.subtitle` optional; update `OnboardingPages.kt:72` and the preview at `HorizontalInfoCard.kt:112` |
| `resident_title` | keep | Résident·e | Resident | J’habite ici | Resident | Glossary term | No |
| `label_coming_soon` | keep | Bientôt disponible | Coming soon | Bientôt disponible | Coming soon | Explains the disabled card and placeholder routes | No |

## 6. Onboarding step 2: Profile

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `profile_title` | keep | Votre profil | Your profile | Votre profil | Your profile | Names the task | No |
| `profile_prefill_note` | delete | Prérempli depuis Google si disponible. | Prefilled from Google when available. | — | — | Filled fields show this; also inaccurate for email accounts, whose address is prefilled too | Yes: remove `BodyText` at `OnboardingPages.kt:113` and the preview line at `AppChrome.kt:162` |
| `profile_name` | keep | Nom | Name | Nom | Name | Field label | No |
| `profile_age_range` | keep | Tranche d’âge | Age range | Tranche d’âge | Age range | Age text must stay | No |
| `age_range_18_24` | keep | 18–24 | 18–24 | 18–24 | 18–24 | `translatable="false"`, in use | No |
| `age_range_25_34` | keep | 25–34 | 25–34 | 25–34 | 25–34 | `translatable="false"`, in use | No |
| `age_range_35_44` | keep | 35–44 | 35–44 | 35–44 | 35–44 | `translatable="false"`, in use | No |
| `age_range_45_54` | keep | 45–54 | 45–54 | 45–54 | 45–54 | `translatable="false"`, in use | No |
| `age_range_55_plus` | keep | 55+ | 55+ | 55+ | 55+ | `translatable="false"`, in use | No |
| `profile_district_of_stay` | keep | Arrondissement de séjour | Borough of stay | Arrondissement de séjour | Borough where you’re staying | Field label; City Pack display term | No |

## 7. Onboarding step 3: Budget and Audience

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `budget_audience_title` | keep | Budget et public | Budget and audience | Budget et public | Budget and audience | Names the task; also the Settings card | Yes: the Settings card shows only the Events budget, unlabelled (`ScreenSettings.kt:133`). Show all budgets or label it |
| `budget_subtitle` | shorten | Par personne, pour une sortie. | Per person, per outing. | Par personne, par itinéraire. | Per person, per itinerary. | Uses the glossary term | No |
| `budget_under_20` | keep | < 20 $ | < $20 | < 20 $ | < $20 | Value | No |
| `budget_under_50` | keep | < 50 $ | < $50 | < 50 $ | < $50 | Value | No |
| `budget_under_100` | keep | < 100 $ | < $100 | < 100 $ | < $100 | Value | No |
| `budget_100_plus` | keep | 100 $ + | $100+ | 100 $ + | $100+ | Value | No |
| `audience_title` | shorten | Type de public | Audience | Public | Audience | "Type de" adds nothing | No |
| `audience_adults_only` | keep | 18+ seulement | 18+ only | 18+ seulement | 18+ only | Age text must stay | No |
| `audience_family_friendly` | keep | Familial | Family-friendly | Familial | Family-friendly | Short chip | No |
| `audience_no_alcohol` | keep | Sans alcool | No alcohol | Sans alcool | No alcohol | Short chip | No |
| `audience_bars_hidden_note` | show-in-UI | Les bars sont masqués pour les profils familial ou sans alcool. | Bars are hidden for family-friendly or no-alcohol preferences. | Bars masqués | Bars hidden | The selected chip gives the condition, so only the effect remains | Yes: show it only when `draft.audience.hidesBars` (`OnboardingPages.kt:203`) |

## 8. Onboarding step 4: Interests

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `interests_title` | keep | Intérêts et cuisines | Interests and cuisines | Intérêts et cuisines | Interests and cuisines | Names the task; also the Settings card | No |
| `interests_subtitle` | shorten | Facultatif. Sert à classer les résultats, jamais à les masquer. | Optional. Used to rank results, never to hide them. | Pour classer les résultats, sans rien masquer. | Used to rank results, not hide them. | "Passer" already shows it is optional | No |
| `interests_event_types` | keep | Types d’événements | Event types | Types d’événements | Event types | Section label | No |
| `interests_cuisines` | keep | Cuisines | Cuisines | Cuisines | Cuisines | Section label | No |
| `action_finish` | keep | Terminer | Finish | Terminer | Finish | Clear action | No |
| `action_skip` | keep | Passer | Skip | Passer | Skip | Clear action | Yes: "Passer" silently discards chips already selected (`OnboardingViewModel.kt:90`). Hide it once anything is selected |
| `onboarding_save_error` | keep | Impossible d’enregistrer votre profil. Réessayez. | Couldn’t save your profile. Try again. | Impossible d’enregistrer votre profil. Réessayez. | Couldn’t save your profile. Try again. | Error with next action | No |

## 9. Settings

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `settings_language` | keep | Langue | Language | Langue | Language | Section label | No |
| `language_french` | keep | Français | Français | Français | Français | `translatable="false"`, in use | No |
| `language_english` | keep | English | English | English | English | `translatable="false"`, in use | No |
| `settings_profile` | merge | Profil | Profile | Profil | Profile | Section and card directly below both say "Profil"; keep the section title | Yes: use the person's name as card title and email · age · District as subtitle (`ScreenSettings.kt:121`) |
| `settings_no_preferences` | keep | Aucune préférence | No preferences | Aucune préférence | No preferences | Empty value | No |

## 10. Unused keys

Delete from both files. No UI change.

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `settings_profile_description` | unused | Nom, courriel, âge, arrondissement | Name, email, age, borough | — | — | The card shows the real values | No |
| `cuisine_quebecois` | unused | Québécoise | Québécois | — | — | Cuisine chips use labels from the data | No |
| `cuisine_italian` | unused | Italienne | Italian | — | — | Cuisine chips use labels from the data | No |
| `cuisine_lebanese` | unused | Libanaise | Lebanese | — | — | Cuisine chips use labels from the data | No |
| `cuisine_japanese` | unused | Japonaise | Japanese | — | — | Cuisine chips use labels from the data | No |
| `cuisine_vegetarian` | unused | Végé | Vegetarian | — | — | Cuisine chips use labels from the data | No |
| `cuisine_cafe` | unused | Café | Café | — | — | Cuisine chips use labels from the data | No |
| `cuisine_bistro` | unused | Bistro | Bistro | — | — | Cuisine chips use labels from the data | No |

## Hardcoded user-facing text

No hardcoded labels or sentences in the access screens outside `@Preview` fixtures. Three related items:

- `ScreenSettings.kt:127` and `:134` join values with a literal ` · `.
- `ScreenSettings.kt:141` joins values with a literal `, `.
- `OnboardingPages.kt:253` falls back to the raw cuisine code as a chip label when a translation is missing.

## Cross-team notes

- **planning:** `budget_late_night` appears on onboarding step 3 and still says "Collation de fin de soirée" / "Late-night snack". The settled term is Bouchée tardive / Late bite.
- **planning:** `budget_late_night_description` repeats what the switch and title show.
- **planning:** `budget_lunch` and `budget_dinner` carry "(repas 1)" / "(repas 2)" on the same screen, which adds no meaning for the person choosing a budget.
- **planning:** `StepProgress` uses the access key `onboarding_step` for its accessibility description, so the "sur" fix also gives Planning the settled "Étape N sur 6".
- **home:** `home_plan_title` repeats `welcome_title` with different punctuation. Question 2 affects both.

## Questions for the product owner

1. **Age and consent:** Accounts are 18+, but neither Register nor Google sign-in states this, and no terms or privacy text exists in the flow. What text should appear, and should it require acceptance? This plan does not invent legal copy.
2. **"Sortie" / "outing":** the glossary says to avoid "outing" for an Itinerary, but the brand line uses it, as does the glossary's own product description. Keep "Votre sortie, sur mesure" as a brand line, or change it to "Votre itinéraire, sur mesure"?
3. **Onboarding step 1:** Visitor is the only enabled choice until Resident ships. Keep this step, or skip it for now and make onboarding 3 steps?

## French Editor notes

Only the `FR proposed` column was edited. 10 strings changed; every placeholder is unchanged.

| Key | Change | Why |
|---|---|---|
| `auth_error_email_in_use` | "Ce courriel a déjà un compte" → "Ce courriel est déjà utilisé." | "Un courriel a un compte" is a calque of the English; "déjà utilisé" is the usual French form. Same next action |
| `auth_error_weak_password` | U+00A0 before the colon | Québec typography; `values/strings.xml` has no non-breaking spaces today |
| `password_reset_title` | "Nouveau mot de passe" → "Mot de passe oublié" | With `password_reset_body` deleted, the screen holds a title, an Email field and "Envoyer le lien". "Nouveau mot de passe" suggests typing a new password here; "Mot de passe oublié" repeats the link the person just tapped and is just as short (19 characters). The English "Reset password" is untouched |
| `password_reset_sent` | "Lien envoyé si ce courriel a un compte." → "Lien envoyé si un compte existe pour ce courriel." | Same calque. Still conditional, so it never confirms whether an account exists. Slightly longer than the English, acceptable in a multi-line callout |
| `visitor_title` | "Visiteur·euse" → "En visite" | `quebec-french-ui` forbids dotted forms, and the OQLF does not recommend the middle dot. Épicène rewrite; the glossary term stays Visitor in English |
| `resident_title` | "Résident·e" → "J’habite ici" | Same rule. Both cards now answer "Comment explorez-vous la ville?" without a gendered noun, and still explain each other with `visitor_description` gone |
| `budget_under_20`, `budget_under_50`, `budget_under_100` | `<` U+00A0 `20` U+00A0 `$` (decision stays `keep`) | Non-breaking space before `$`, and after `<` so the sign never ends a line |
| `budget_100_plus` | `100` U+00A0 `$` U+00A0 `+` (decision stays `keep`) | Non-breaking spaces on both sides of `$` so the value never wraps |

Checked and kept as proposed: the auth errors keep their cause and next action (`auth_error_provider_disabled` can also fire from the Google flow and on Password reset, so the provider-neutral wording is right); "Bars masqués" agrees in gender and number and reads as a status line once it only appears under a selected Familial or Sans alcool chip; "Étape %1$d sur %2$d" is the settled form; "Mot de passe oublié?" and "Comment explorez-vous la ville?" follow the app-wide convention of no space before "?"; "événement", "arrêt" and "timbre" match the rest of `values/strings.xml`; every apostrophe is already typographic.

Needs a human Québec French reader:

- `visitor_title` / `resident_title`: "En visite" and "J’habite ici" are my épicène proposals. If the team prefers nouns, the OQLF-recommended full doublets are "Visiteur ou visiteuse" / "Résident ou résidente", at the cost of length.
- `password_reset_sent`: confirm the sentence reads as neutral about the account’s existence to a Québec reader.
- `password_reset_title`: confirm "Mot de passe oublié" as the app bar title, since it departs from the English.
- `profile_district_of_stay`: "Arrondissement de séjour" is understandable but unusual; a reader may prefer "Arrondissement où vous logez" if space allows.
- `audience_adults_only`: "18+ seulement" is common on Montréal event listings; the formal form is "18 ans et plus".

Removed text: none of the five deletions harms French comprehension once the matching UI changes land. The Copy Lead’s question 1 (age, consent and legal text) is left entirely to the product owner; no such text was written here.

## English Editor notes

Only the `EN proposed` column was edited. 5 strings changed; every placeholder is unchanged. No legal, age or consent text was written (question 1 stays with the product owner).

| Key | Change | Why |
|---|---|---|
| `auth_error_weak_password` | "Password: at least 6 characters." to "Password must be at least 6 characters." | The label-and-colon form reads as a field label, not an error |
| `auth_error_provider_disabled` | "Sign-in method unavailable. Try another." to "This sign-in method isn’t available. Try another." | Full sentence, same next action, same contraction style as the rest |
| `password_reset_sent` | "Link sent if this email has an account." to "If this email has an account, we’ve sent a link." | Reads as a sentence. Still conditional, so it never confirms whether an account exists |
| `profile_district_of_stay` | "Borough of stay" to "Borough where you’re staying" | "Borough of stay" is a calque of "séjour". Still the City Pack display term |
| `interests_subtitle` | "To rank results, never hide them." to "Used to rank results, not hide them." | The fragment had no subject; keeps the Interest meaning of the French |

Recurring problems: telegraphic fragments and label-colon forms used as sentences (3 strings); calques from the French (2). All apostrophes were already typographic.

Meaning checks against the final French:

- `password_reset_title`: French "Mot de passe oublié", English "Reset password". Kept different on purpose. The English title names the task and sits above "Send reset link"; "Forgot password" would repeat the sign-in link and describe a state, not a task. Both point to the same screen, so the gap is small. Align to "Forgot password" only if the team wants one concept.
- `visitor_title` / `resident_title`: French "En visite" / "J’habite ici" are first-person statements; English "Visitor" / "Resident" are Glossary nouns. Under "How are you exploring the city?" the English cards read acceptably as labels, but the register differs. Glossary terms kept. For parity, "I’m visiting" / "I live here" would match, at the cost of the Glossary terms on these cards. Product owner's call.
- `auth_error_email_in_use`: French "est déjà utilisé", English "already has an account". Same situation and same next action; the English states the cause more plainly. Left as is.
- `password_reset_sent`: both versions are conditional and neither confirms that an account exists.
- Auth errors: all keep a next action except `auth_error_invalid_credentials` and `auth_error_password_mismatch`, where the fields and the reset link are the next action.
- `auth_error_unknown` ("Something went wrong. Try again.") is vague but is the fallback and matches the French.
- `welcome_title` uses "outing", which the Glossary says to avoid; left to question 2.
