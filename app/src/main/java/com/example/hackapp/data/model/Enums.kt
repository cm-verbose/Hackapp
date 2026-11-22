package com.example.hackapp.data.model

// Stored in places.db as the constant name (Room's built-in enum converter).
// Renaming a constant is a schema change: update etl/config/reference.yaml as well.

enum class VenueType { RESTAURANT, FAST_FOOD, CAFE, BAKERY, DESSERT, FOOD_TRUCK, BAR }

enum class MealSlot { BREAKFAST, LUNCH, DINNER, SNACK, EVENING }

enum class PlaceStatus { ACTIVE, TEMPORARILY_CLOSED, CLOSED }

enum class PriceSource { GOOGLE_RANGE, GOOGLE_LEVEL_ESTIMATE, NONE }

enum class DescriptionSource { GOOGLE_EDITORIAL, GOOGLE_GENERATIVE, OSM, GENERATED }

enum class Dietary { VEGETARIAN, VEGAN, VEGETARIAN_ONLY, VEGAN_ONLY }

enum class AreaKind { BOROUGH, ZONE }
