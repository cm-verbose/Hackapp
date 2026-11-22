---
name: plan-writing
description: Plan[City] writing rules for all user-facing text. Use when writing, editing or reviewing app strings, Figma copy, labels, helper text, errors, dialogs or accessibility descriptions in French or English.
---

# Plan[City] writing rules

The interface explains itself first. Words are the last resort.

## Decide in this order

For every piece of text, stop at the first answer that works:

1. **Delete it** if a control, icon, layout, state, value or earlier screen already makes it clear.
2. **Show it** through UI instead: a disabled state, selected state, inline value, icon or empty state.
3. **Merge it** with a nearby title, label or action.
4. **Shorten it** to the fewest words that keep the meaning.

## Text that must stay

Shorten these, but never remove them:

- Data licences and source attribution
- The notice that a ticket price is shared with other users
- The label saying a price is the median of user-reported prices
- Warnings before losing data, ending an Itinerary or removing Stops
- Errors, with what the user can do next
- Accessibility descriptions for icon-only controls
- Consent, age and legal text

## Style

- One idea per line. Avoid a second sentence unless it gives the next action.
- Titles name the task. Do not repeat them in a subtitle.
- Buttons name the action: "Keep this budget", not "OK".
- Do not explain how to tap, swipe, pick or scroll.
- Do not describe what the app is doing unless the user must act on it.
- Do not repeat a value already visible on the same screen.
- Use sentence case and no exclamation marks.
- Use the exact terms in `GLOSSARY.md`. Never swap in a synonym for variety.

## Android strings

- `values/strings.xml` is French and the default. `values-en/strings.xml` is English. Do not add `values-fr*`.
- Both files keep the same keys and the same placeholders in the same types.
- Never build a sentence by joining strings. Use one string with `%1$s` arguments.
- Do not reuse one key for two contexts because the English happens to match.
- Delete a key only when no code uses it, and delete it from both files.
- Check that text fits at 360 dp and at 1.3x font scale. French usually runs longer.
