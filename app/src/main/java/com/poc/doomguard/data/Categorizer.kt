package com.poc.doomguard.data

// SPIKE: crude keyword categorizer for reel text (creator, audio, caption). '#tags' are split into
// plain tokens; a few Tamil/Hindi-romanised words are included.
// Pure Kotlin. Highest keyword hit count wins; ties go to the earlier category in the map.
object Categorizer {
    const val OTHER = "other"

    private val keywords: Map<String, List<String>> = linkedMapOf(
        "comedy" to listOf("comedy", "funny", "meme", "memes", "joke", "jokes", "lol", "lmao", "humor", "humour", "prank", "skit", "relatable", "standup", "roast", "vadivelu", "goundamani", "santhanam", "brahmanandam", "trolls", "troll"),
        "fitness" to listOf("fitness", "workout", "gym", "exercise", "yoga", "muscle", "gains", "abs", "cardio", "bodybuilding", "protein", "fitfam", "legday", "weightloss", "running", "calisthenics"),
        "food" to listOf("food", "recipe", "recipes", "cooking", "cook", "foodie", "chef", "delicious", "baking", "dinner", "breakfast", "lunch", "restaurant", "streetfood", "biryani", "dosa", "snack", "dessert"),
        "music" to listOf("music", "song", "songs", "singer", "guitar", "dj", "rap", "lyrics", "cover", "original audio", "beats", "remix", "piano", "love song", "feelingsong", "bgm", "kadhal", "melody", "whatsappstatus"),
        "news" to listOf("news", "breaking", "politics", "election", "government", "president", "minister", "headline", "war", "update", "bjp", "modi", "parliament"),
        "travel" to listOf("travel", "trip", "vacation", "wanderlust", "explore", "beach", "tour", "hotel", "roadtrip", "backpacking", "destination", "singapore", "dubai", "ooty", "vlog", "trekking"),
        "tech" to listOf("tech", "ai", "coding", "programming", "software", "developer", "gadget", "iphone", "android", "startup", "chatgpt", "robot", "engineering", "systems", "career", "cloud", "cybersecurity", "python", "java", "javascript", "data", "institute"),
        "fashion" to listOf("fashion", "outfit", "ootd", "style", "streetwear", "makeup", "beauty", "model", "dress", "grwm", "skincare", "saree", "hairstyle")
    )

    /** Returns the best-matching category for the reel's [texts], or [OTHER] when nothing matches. */
    fun categorize(texts: List<String>): String {
        val tokens = texts.joinToString(" ").lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return OTHER
        // Space-padded so `" $it "` below matches whole tokens and phrases only.
        val joined = " " + tokens.joinToString(" ") + " "
        var best = OTHER
        var bestScore = 0
        for ((category, words) in keywords) {
            // Whole-token match (so "ai" doesn't hit "said"); multi-word keywords match as phrases.
            val score = words.count { joined.contains(" $it ") }
            if (score > bestScore) {
                best = category
                bestScore = score
            }
        }
        return best
    }
}
