package com.poc.doomguard.data

import android.content.Context
import kotlin.random.Random

object Quotes {
    private const val PREFS = "doomguard_prefs"
    private const val KEY_LAST = "last_quote"

    private val all = listOf(
        "You are allowed to put the phone down. The world will keep spinning.",
        "A calm mind is built one quiet minute at a time.",
        "Be where your feet are.",
        "The best things in life are not on a screen.",
        "Your attention is a gift. Spend it on what matters.",
        "Rest is not a reward for finishing. It is part of the work.",
        "You don't have to see everything to live a full life.",
        "Small pauses make big days.",
        "Look up. Something good is happening right here.",
        "Progress, not perfection, one breath at a time.",
        "What you give your attention to grows.",
        "You are more than the feed you scroll.",
        "Peace begins with a single deep breath.",
        "Be kind to your mind. It has been working hard today.",
        "Choose presence over the endless scroll.",
        "The present moment is the only one you can actually live in.",
        "A little less noise, a little more you.",
        "Stillness is where clarity lives.",
        "You deserve a break that actually refills you.",
        "Today is a good day to notice how you feel.",
        "Joy is often found in the quiet, unscrolled moments.",
        "Take your time back. It is yours.",
        "You are doing better than you think.",
        "Let your mind be spacious today.",
        "Every pause is a chance to begin again.",
        "Real life has the best content. Go enjoy it.",
        "Breathe in calm. Breathe out the rush.",
        "You are enough, with or without the update.",
        "Gentle habits create a gentle life.",
        "Notice the sky. It is free, and it is lovely.",
        "Your worth is not measured in likes or views.",
        "Slow down. There is nothing urgent in the feed.",
        "Calm is a superpower, and you are practising it right now.",
        "One mindful choice can change the whole afternoon.",
        "Give your eyes a rest and your heart a moment.",
        "Reach for something that makes you feel alive.",
        "Be proud of every time you choose to pause.",
        "Care for your attention the way you care for your health.",
        "The most beautiful moments are rarely filmed.",
        "You are in charge of where your mind goes next."
    )

    /** Random quote that differs from the last one shown (last index persisted in prefs). */
    fun next(ctx: Context): String {
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getInt(KEY_LAST, -1)
        var i = Random.nextInt(all.size)
        if (i == last) i = (i + 1 + Random.nextInt(all.size - 1)) % all.size
        prefs.edit().putInt(KEY_LAST, i).apply()
        return all[i]
    }
}
