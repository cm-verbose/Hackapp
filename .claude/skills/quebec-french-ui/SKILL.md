---
name: quebec-french-ui
description: Québec French rules for mobile interface text. Use when writing, correcting or reviewing French app strings or Figma copy for Plan Montréal.
---

# Québec French for interface text

Write natural, neutral Québec French for adults. Use the Office québécois de la langue française (OQLF) as the reference: the Banque de dépannage linguistique for usage and Vitrine linguistique / Grand dictionnaire terminologique for terms. When unsure, check there or flag the string. Do not guess.

## Register

- Address the user as **vous**.
- Use standard Québec usage, without slang or France-only terms.
- Prefer épicène wording that avoids gender: "votre profil", not "cher utilisateur".
- Avoid parenthetical or dotted forms such as "utilisateur(trice)" or "utilisateur·rice".

## Québec vocabulary

| Use | Avoid here |
|---|---|
| déjeuner (morning), **dîner** (noon), **souper** (evening) | France's déjeuner (noon) and dîner (evening) |
| courriel | e-mail, mail |
| fin de semaine | week-end |
| stationnement | parking |
| billet | ticket |
| clavarder | chatter |

The app's Meals are **Dîner**, **Souper** and **Bouchée tardive**.

## Anglicisms and false friends

| Avoid | Use |
|---|---|
| éligible | admissible |
| canceller | annuler |
| opportunité (for a chance) | occasion |
| définitivement (for "certainly") | certainement, vraiment |
| adresser un problème | régler, traiter |
| supporter (for "to support") | prendre en charge |
| initier (for "to start") | lancer, amorcer |
| en autant que | pourvu que, dans la mesure où |
| sauver (a file or draft) | enregistrer |

## Interface forms

- **Buttons and menu actions use the infinitive:** Enregistrer, Continuer, Annuler, Ajouter.
- Instructions addressed to the user may use the imperative: "Choisissez une date."
- Titles are noun phrases when possible.
- Use sentence case. Capitalize only the first word and proper nouns.

## Typography

- Use a non-breaking space (` `) before `:` and `$`, and inside `« »`.
- No space, or a thin non-breaking space, before `;`, `!` and `?`. Be consistent across the app.
- Currency goes after the number: `25 $`, `< 50 $`, `100 $ +`.
- Use the typographic apostrophe `’`, written directly in `strings.xml`.
- Keep accents on capitals: À, É, Ê.
- Times use `18 h 30` in sentences. Compact controls, lists and schedules may use `18:30`.
- Dates follow the form `sam. 28 nov. 2026`, in lowercase.
- Use the en dash for ranges: `10–20 $`.

## Before finishing

Read each string aloud in its screen context. Check agreement, gender, plural forms and placeholder positions, because a `%1$s` value can change the agreement around it.
