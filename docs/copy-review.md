# App text review

Proposed changes from the five writing teams. Unchanged strings are not listed. `—` means the text is removed from the screen.

**244 strings reviewed:** 143 keep, 48 shorten, 22 delete, 8 show-in-ui, 10 merge, 13 unused. **106 would change.**

## Access (33)

### 1. Welcome and sign-in

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `welcome_title` | Votre sortie, sur mesure. → **Votre sortie, sur mesure** | Your outing, made to measure. → **Your outing, made to measure** |  |
| `welcome_tagline` | Planifiez votre sortie, suivez-la arrêt par arrêt, et collectionnez vos timbres. → **Planifiez, suivez chaque arrêt, collectionnez vos timbres.** | Plan your outing, follow it stop by stop, and collect your stamps. → **Plan, follow each stop, collect your stamps.** |  |

### 2. Authentication errors

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `auth_error_invalid_email` | Ce courriel n’est pas valide. → **Entrez un courriel valide.** | This email isn’t valid. → **Enter a valid email.** |  |
| `auth_error_email_in_use` | Un compte existe déjà pour ce courriel. Connectez-vous ou continuez avec Google. → **Ce courriel est déjà utilisé. Connectez-vous ou continuez avec Google.** | An account already exists for this email. Sign in or continue with Google. → **This email already has an account. Sign in or continue with Google.** |  |
| `auth_error_weak_password` | Choisissez un mot de passe d’au moins 6 caractères. → **Mot de passe : au moins 6 caractères.** | Choose a password with at least 6 characters. → **Password must be at least 6 characters.** |  |
| `auth_error_provider_disabled` | La connexion par courriel n’est pas encore activée. → **Mode de connexion indisponible. Essayez-en un autre.** | Email sign-in isn’t enabled yet. → **This sign-in method isn’t available. Try another.** |  |

### 3. Register

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `register_one_account_note` | Un seul compte par courriel : Google ou mot de passe mènent au même profil, itinéraires et passeport. → **—** | One account per email: Google or password sign-in reach the same profile, itineraries and passport. → **—** | Yes: remove the `Text` in `ScreenRegister.kt:62` |

### 4. Password reset

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `password_reset_title` | Réinitialiser le mot de passe → **Mot de passe oublié** | Reset password → **Reset password** |  |
| `password_reset_body` | Entrez votre courriel et nous vous enverrons un lien pour choisir un nouveau mot de passe. → **—** | Enter your email and we’ll send you a link to choose a new password. → **—** | Yes: remove the `Text` in `ScreenPasswordReset.kt:57` |
| `password_reset_sent` | Si un compte existe pour ce courriel, le lien vient d’être envoyé. → **Lien envoyé si un compte existe pour ce courriel.** | If an account exists for this email, the link has been sent. → **If this email has an account, we’ve sent a link.** |  |

### 5. Onboarding step 1: Visitor type

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `onboarding_step` | Étape %1$d de %2$d → **Étape %1$d sur %2$d** | Step %1$d of %2$d → **Step %1$d of %2$d** | Yes: stop passing it as the app bar title on the 4 onboarding pages and in the `AppChrome.kt:155` preview; keep it in `StepProgress` |
| `onboarding_visitor_type_subtitle` | Choisissez votre profil d’exploration. → **—** | Choose how you explore. → **—** | Yes: remove `BodyText` at `OnboardingPages.kt:69` |
| `visitor_title` | Visiteur·euse → **En visite** | Visitor → **Visitor** |  |
| `visitor_description` | J’explore une ville où je n’habite pas → **—** | I’m exploring a city I don’t live in → **—** | Yes: make `HorizontalInfoCard.subtitle` optional; update `OnboardingPages.kt:72` and the preview at `HorizontalInfoCard.kt:112` |
| `resident_title` | Résident·e → **J’habite ici** | Resident → **Resident** |  |

### 6. Onboarding step 2: Profile

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `profile_prefill_note` | Prérempli depuis Google si disponible. → **—** | Prefilled from Google when available. → **—** | Yes: remove `BodyText` at `OnboardingPages.kt:113` and the preview line at `AppChrome.kt:162` |
| `profile_district_of_stay` | Arrondissement de séjour → **Arrondissement de séjour** | Borough of stay → **Borough where you’re staying** |  |

### 7. Onboarding step 3: Budget and Audience

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `budget_subtitle` | Par personne, pour une sortie. → **Par personne, par itinéraire.** | Per person, per outing. → **Per person, per itinerary.** |  |
| `budget_under_20` | < 20 $ → **< 20 $** | < $20 → **< $20** |  |
| `budget_under_50` | < 50 $ → **< 50 $** | < $50 → **< $50** |  |
| `budget_under_100` | < 100 $ → **< 100 $** | < $100 → **< $100** |  |
| `budget_100_plus` | 100 $ + → **100 $ +** | $100+ → **$100+** |  |
| `audience_title` | Type de public → **Public** | Audience → **Audience** |  |
| `audience_bars_hidden_note` | Les bars sont masqués pour les profils familial ou sans alcool. → **Bars masqués** | Bars are hidden for family-friendly or no-alcohol preferences. → **Bars hidden** | Yes: show it only when `draft.audience.hidesBars` (`OnboardingPages.kt:203`) |

### 8. Onboarding step 4: Interests

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `interests_subtitle` | Facultatif. Sert à classer les résultats, jamais à les masquer. → **Pour classer les résultats, sans rien masquer.** | Optional. Used to rank results, never to hide them. → **Used to rank results, not hide them.** |  |

### 10. Unused keys

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `settings_profile_description` | Nom, courriel, âge, arrondissement → **—** | Name, email, age, borough → **—** |  |
| `cuisine_quebecois` | Québécoise → **—** | Québécois → **—** |  |
| `cuisine_italian` | Italienne → **—** | Italian → **—** |  |
| `cuisine_lebanese` | Libanaise → **—** | Lebanese → **—** |  |
| `cuisine_japanese` | Japonaise → **—** | Japanese → **—** |  |
| `cuisine_vegetarian` | Végé → **—** | Vegetarian → **—** |  |
| `cuisine_cafe` | Café → **—** | Café → **—** |  |
| `cuisine_bistro` | Bistro → **—** | Bistro → **—** |  |

## Home and Itineraries (14)

### Home

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `home_underway_label` | EN COURS → **En cours** | UNDERWAY → **Underway** |  |
| `itinerary_title_in` | %1$s à %2$s → **%1$s · %2$s** | %1$s in %2$s → **%1$s · %2$s** |  |
| `home_next_stop` | Prochain arrêt %1$s · %2$s → **Prochain arrêt : %1$s** | Next stop %1$s · %2$s → **Next stop: %1$s** | Yes: B |
| `home_resume_itinerary` | Reprendre l’itinéraire → **Reprendre** | Resume itinerary → **Resume** |  |
| `home_plan_title` | Votre sortie, sur mesure → **Journée ou soirée à %1$s** | Your outing, made to measure → **Day or night out in %1$s** | Yes: C |
| `home_plan_body` | Une journée ou une soirée à %1$s, arrêt par arrêt. → **—** | A day or night out in %1$s, stop by stop. → **—** | Yes: C |
| `home_suggestions` | SUGGESTIONS → **Suggestions** | SUGGESTIONS → **Suggestions** |  |

### Itineraries

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `itineraries_empty_body` | Vos itinéraires planifiés et passés apparaîtront ici. → **Aucun itinéraire** | Your planned and past itineraries will appear here. → **No itineraries** |  |
| `itineraries_archived` | Archivés → **Archivés · jamais commencés** | Archived → **Archived · never started** | Yes: D |
| `itinerary_delete_body` | Ce brouillon sera supprimé définitivement. → **Cette action est irréversible.** | This draft will be permanently deleted. → **This can’t be undone.** |  |
| `itinerary_delete_error` | Impossible de supprimer cet itinéraire. Réessayez. → **Impossible de supprimer cet itinéraire. Réessayez.** | Could not delete this itinerary. Try again. → **Couldn’t delete this itinerary. Try again.** |  |

### Past Itinerary

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `past_stamps_earned` | Timbres obtenus → **Timbres** | Stamps earned → **Stamps** |  |
| `end_of_day_no_stamp` | aucun timbre → **Aucun** | no stamp → **None** |  |

### Archived Itinerary

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `itineraries_archived_note` | Archivé, car sa date est passée. → **Archivé : date passée** | Archived because its date has passed. → **Archived: date passed** | Yes: D |

## Planning (24)

### 1. Date and time

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `planning_when_title` | Quand sortez-vous? → **—** | When are you going out? → **—** | Yes: remove the heading in `PlanningDateTimePage.kt:107` |
| `planning_error_min_duration` | Un itinéraire dure au moins 2 heures. → **Prévoyez au moins 2 h.** | An itinerary lasts at least 2 hours. → **Allow at least 2 hours.** |  |
| `planning_error_latest_end` | Un itinéraire se termine au plus tard à 04:00. → **Terminez au plus tard à 4 h.** | An itinerary ends no later than 04:00. → **End by 04:00.** |  |

### 2. Budget

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `planning_budget_question` | Modifier votre budget pour cette sortie? → **—** | Change your budget for this outing? → **—** | Yes: remove the heading in `PlanningBudgetPages.kt:101` |
| `planning_budget_current` | Voici le budget actuellement prévu pour cette sortie. → **—** | This is the budget currently set for this outing. → **—** | Yes: remove the text in `PlanningBudgetPages.kt:106` |
| `budget_lunch` | Dîner (repas 1) → **Dîner** | Lunch (Meal 1) → **Lunch** |  |
| `budget_dinner` | Souper (repas 2) → **Souper** | Dinner (Meal 2) → **Dinner** |  |
| `budget_late_night` | Collation de fin de soirée → **Bouchée tardive** | Late-night snack → **Late bite** |  |
| `budget_late_night_description` | Facultatif. Ajoutez un budget pour une bouchée tardive. → **—** | Optional. Add a budget for a late bite. → **—** | Yes: remove the text in `BudgetFields.kt:60` (also affects onboarding) |
| `planning_budget_change` | Modifier pour cette sortie → **Modifier pour cet itinéraire** | Change for this outing → **Change for this itinerary** |  |
| `planning_budget_edit_title` | Budget de cette sortie → **Pour cet itinéraire seulement** | Budget for this outing → **For this itinerary only** |  |
| `planning_budget_edit_note` | Ces changements s’appliquent à cet itinéraire seulement. → **→ `planning_budget_edit_title`** | These changes apply to this Itinerary only. → **→ `planning_budget_edit_title`** | Yes: remove the text in `PlanningBudgetPages.kt:217` |

### 3. District pick

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `district_pick_instructions` | Choisissez 1 à 3 arrondissements, dans l’ordre de visite. → **Choisissez-en 1 à 3, dans l’ordre de visite.** | Choose 1 to 3 boroughs, in the order you’ll visit them. → **Choose 1 to 3, in the order you’ll visit them.** |  |
| `district_max_body` | Pour ajouter %1$s, choisissez lequel remplacer. → **Lequel remplacer par %1$s?** | To add %1$s, choose which one to replace. → **Replace which one with %1$s?** |  |
| `district_replace_label` | Remplacer : → **—** | Replace: → **—** | Yes: remove the label in `DistrictPickPage.kt:367` |

### 4. Meal choice

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `meal_question` | On planifie vos repas? → **—** | Should we plan your meals? → **—** | Maquette: remove the question |
| `meal_plan_yes` | Oui, planifiez mes repas → **Planifier mes repas** | Yes, plan my meals → **Plan my meals** |  |
| `meal_when` | Quand voulez-vous manger? → **—** | When do you want to eat? → **—** | Maquette: remove the heading |
| `meal_block_note` | Chaque repas réserve un bloc de 90 min : 1 h, plus 15 min avant et après. → **Bloc de %1$d min** | Each meal reserves a 90-minute block: 1 h, plus 15 min before and after. → **%1$d-min block** | Yes: placeholder added, show per Meal, remove the `InfoCallout` |

### 5. Stop selection

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `selection_district_progress` | Arrondissement %1$d sur %2$d · %3$s → %4$s → **Arrondissement %1$d sur %2$d** | Borough %1$d of %2$d · %3$s → %4$s → **Borough %1$d of %2$d** | Yes: drop the `%3$s` and `%4$s` arguments |
| `selection_meal_block_title` | Bloc repas %1$s – %2$s → **Bloc repas** | Meal block %1$s – %2$s → **Meal block** | Yes: drop the `%1$s` and `%2$s` arguments |
| `selection_meal_block_body` | Choisissez un restaurant ou un bar (dès 17:00). → **—** | Choose a restaurant or bar (from 17:00). → **—** | Yes: remove the body from `MealBlockPrompt` |
| `planned_departure_title` | À quelle heure partirez-vous? → **Départ prévu** | When will you leave? → **Planned departure** |  |

### 6. Review

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `review_remove_note` | Retirer un arrêt vous ramène à la sélection pour ce créneau. Les arrêts ne se réordonnent pas. → **—** | Removing a stop takes you back to selection for that time slot. Stops can’t be reordered. → **—** | Yes: depends on `RemoveStopWarningDialog` |

## Underway and Passport (24)

### Event option card (Planning selection)

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `end_time_unpublished` | fin non publiée → **—** | end time not published → **—** | Yes: remove the text from `EventOptionCard`, then delete the key |

### Leg handoff

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `leg_start_from_location` | Début %1$s · départ depuis votre position → **Début %1$s** | Starts %1$s · leaving from your location → **Starts %1$s** |  |
| `leg_depart` | Départ → **Départ** | Depart → **Departure** |  |
| `leg_arrive` | Arrivée → **Arrivée** | Arrive → **Arrival** |  |
| `leg_walk_minutes` | ≈ %1$d min à pied → **≈ %1$d min** | ≈ %1$d min walk → **≈ %1$d min** | Yes: pass the estimate for the selected mode. The key name becomes stale |
| `leg_how_to_get_there` | Comment y allez-vous? → **Comment y aller?** | How are you getting there? → **Getting there** |  |
| `leg_mode_uber` | Uber → **—** | (none, `translatable="false"`) → **—** | Yes: point the preview at the City Pack, then delete the key |
| `leg_fare_required` | Titre requis : %1$s → **Titre requis : %1$s · %2$s** | Fare needed: %1$s → **Ticket needed: %1$s · %2$s** | Yes: pass `Fare.agencyName` as `%2$s` in `TransportChoiceCard` and the `TransitFareRow` preview |
| `leg_fare_single_ride` | 1 passage → **—** | 1 ride → **—** | Yes: point the previews at the City Pack, then delete the key |
| `leg_fare_source` | Selon la grille tarifaire de %1$s. → **—** | Based on the %1$s fare table. → **—** | Yes: delete only after the merge above |
| `leg_google_maps_directions` | Itinéraire dans Google Maps → **Ouvrir dans Google Maps** | Directions in Google Maps → **Open in Google Maps** |  |

### Arrival

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `arrival_not_detected_note` | L’arrivée n’est pas détectée par GPS : confirmez-la. → **—** | Arrival isn’t detected by GPS, so please confirm it. → **—** | Yes: remove `hint` from `ArrivalStatusCard` and keep the arrival button prominent |
| `arrival_reminder_note` | Sans confirmation 30 min après l’heure prévue : → **—** | If not confirmed 30 min after the expected time: → **—** | Yes: build Arrival without this caption or a notification mock |

### At the Stop

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `at_stop_next` | Prochain arrêt : %1$s · %2$d min à pied → **Prochain arrêt : %1$s · ≈ %2$d min** | Next stop: %1$s · %2$d min walk → **Next stop: %1$s · ≈ %2$d min** | Yes: pass the transit minutes used for Leave By |
| `at_stop_alert_note` | Alerte prioritaire 30 min avant : → **—** | High-priority alert 30 min before: → **—** | Yes: build At the Stop without this caption or a notification mock |

### Notifications

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `notification_arrival_title` | Êtes-vous arrivé·e? → **Êtes-vous sur place?** | Have you arrived? → **Have you arrived?** |  |
| `notification_arrival_body` | %1$s devait commencer à %2$s. → **%1$s · %2$s** | %1$s was due to start at %2$s. → **%1$s · %2$s** |  |
| `notification_leave_title` | Départ dans 30 min → **Partez avant %1$s** | Leaving in 30 min → **Leave by %1$s** | Yes: pass the Leave By time, formatted `19 h 15` in French |
| `notification_leave_body` | Quittez %1$s avant %2$s pour arriver à l’heure. → **Prochain arrêt : %1$s** | Leave %1$s by %2$s to arrive on time. → **Next stop: %1$s** | Yes: one argument, the next Stop's name |

### End of day

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `end_of_day_title` | Fin de la journée → **Itinéraire terminé** | End of day → **Itinerary ended** | Yes: one title, no hero line |
| `end_of_day_hero_day` | Belle journée! → **—** | What a day! → **—** | Yes: see `end_of_day_title` |
| `end_of_day_hero_night` | Belle soirée! → **—** | What a night! → **—** | Yes: see `end_of_day_title` |
| `end_of_day_confirmed_only` | Seuls les arrêts confirmés donnent un timbre. → **—** | Only confirmed stops earn a stamp. → **—** | Yes: show status and Stamp or "aucun timbre" per Stop |
| `end_of_day_finish` | Terminer la journée → **Terminer l’itinéraire** | End the day → **End itinerary** |  |

## Shared (11)

### Common actions

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `action_add` | + Ajouter → **—** | + Add → **—** |  |
| `action_open` | Ouvrir → **—** | Open → **—** |  |

### Loading, empty and error states

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `state_empty_title` | Rien à afficher pour le moment → **Rien à afficher** | Nothing to show yet → **Nothing to show** |  |
| `state_error_title` | Une erreur est survenue → **Chargement impossible** | Something went wrong → **Couldn’t load** |  |

### Underway actions and warnings

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `action_arrived` | Je suis arrivé·e → **Je suis là** | I’ve arrived → **I’m here** |  |
| `late_warning` | Vous risquez d’arriver en retard à %1$s. → **Retard possible : %1$s** | You may arrive late at %1$s. → **You may be late for %1$s** |  |
| `late_no_replanning` | Pas de replanification automatique. → **—** | No automatic replanning. → **—** | Yes: remove `detail` and its `Text` from `LateArrivalWarning` |

### Attribution, map and counts

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `data_attribution` | Données : Has Things (hasthings.com), Ville de Montréal, © contributeurs OpenStreetMap → **Données : Has Things (hasthings.com), Ville de Montréal, © les contributeurs d’OpenStreetMap** | Data: Has Things (hasthings.com), Ville de Montréal, © OpenStreetMap contributors → **Data: Has Things (hasthings.com), Ville de Montréal, © OpenStreetMap contributors** |  |
| `map_district_boundaries` | Carte · limites des arrondissements → **—** | Map · borough boundaries → **—** |  |

### Prices

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `price_free` | Gratuit → **gratuit** | Free → **free** |  |

### Stops and Stamps

| Key | FR now → proposed | EN now → proposed | UI change |
|---|---|---|---|
| `new_stamps_in_passport` | one: %d nouveau timbre dans votre passeport. / other: %d nouveaux timbres dans votre passeport. → **one: %d nouveau timbre / other: %d nouveaux timbres** | one: %d new stamp in your passport. / other: %d new stamps in your passport. → **one: %d new stamp / other: %d new stamps** |  |
