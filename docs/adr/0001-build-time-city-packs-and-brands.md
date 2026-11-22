# Select City Packs and Brands at build time

The app must be white-labelled for other cities, so everything city-specific lives in a City Pack and all visual identity lives in a separate Brand, which lets one city be presented under several Brands. Each build selects one City Pack and one Brand, with no in-app city picker, because a white-label client ships its own app and a runtime picker would add screens and states the hackathon demo does not need.
