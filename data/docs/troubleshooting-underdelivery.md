# Troubleshooting under-delivery

Educational summary written for the Campaign Doctor demo. It is not vendor documentation.

## Diagnostic order

Work through the checks in order, because each one rules out a class of causes. First confirm the problem with the pacing ratio: below 0.90 is under-pacing. Then look at the daily trend to see whether delivery fell gradually, fell off a cliff on a specific date, or was never on track. Then compare bid volume with win rate. Then compare effective CPM with the max bid. Finally check the audience, frequency cap and creative status.

## Reading the pattern

Many bids but a low win rate: the price is too low for the inventory. Raise the max bid, or move to inventory with cheaper clearing prices. Effective CPM close to the max bid confirms the cap is binding.

Few bids and a healthy win rate: the campaign is short of reach. Widen the audience, loosen filters or raise the frequency cap. Raising the bid will not help.

A cliff on one date, with bids falling sharply: look for a creative disapproval or expiry, a tag error, an edited targeting setting or a budget or daily cap that ran out. See creative-and-supply.md.

Pacing above 1.10: the problem is the reverse. Check for accelerated pacing and consider a daily cap. See pacing.md.

## What to report

State the pacing ratio, the pattern that matches, the most likely cause and one concrete next step. Say what you could not determine from the available data rather than guessing, and name the check that would settle it.
