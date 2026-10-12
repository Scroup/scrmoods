package com.scrmoods.app

import java.time.LocalDate

/** A quote with its author, or a gentle reminder (no author). */
data class Quote(val text: String, val author: String? = null)

/**
 * Quote of the day: light, encouraging quotes and small reminders. The list cycles one entry per day: three quotes, then a reminder.
 * Each date always gets the same entry, so earlier pages of the journal keep their quote.
 */
object Quotes {
    val all = listOf(
        Quote("A journey of a thousand miles begins with a single step.", "Lao Tzu"),
        Quote("Fall seven times, stand up eight.", "Japanese proverb"),
        Quote("Keep your eyes on the stars, and your feet on the ground.", "Theodore Roosevelt"),
        Quote("Kiss your partner for six seconds, long enough for the affection to register."),

        Quote("Do what you can, with what you have, where you are.", "Theodore Roosevelt"),
        Quote("Hope is the thing with feathers that perches in the soul.", "Emily Dickinson"),
        Quote("Optimism is the faith that leads to achievement.", "Helen Keller"),
        Quote("Don't forget to express gratitude."),

        Quote("Little by little, one travels far.", "Spanish proverb"),
        Quote("The best way out is always through.", "Robert Frost"),
        Quote("Well done is better than well said.", "Benjamin Franklin"),
        Quote("Tell the people you care about that you love them."),

        Quote("After rain comes sunshine.", "Proverb"),
        Quote("Every expert was once a beginner.", "Saying"),
        Quote("I am not afraid of storms, for I am learning how to sail my ship.", "Louisa May Alcott"),
        Quote("Progress, not perfection."),

        Quote("The sun himself is weak when he first rises, and gathers strength and courage as the day gets on.", "Charles Dickens"),
        Quote("Rome wasn't built in a day.", "Proverb"),
        Quote("This too shall pass.", "Persian proverb"),
        Quote("Drink a glass of water and stretch for a minute."),

        Quote("Where there's a will, there's a way.", "Proverb"),
        Quote("Although the world is full of suffering, it is full also of the overcoming of it.", "Helen Keller"),
        Quote("Rest is not idleness.", "John Lubbock"),
        Quote("You've handled hard days before. You can handle this one."),

        Quote("Every cloud has a silver lining.", "Proverb"),
        Quote("Adopt the pace of nature: her secret is patience.", "Ralph Waldo Emerson"),
        Quote("Write it on your heart that every day is the best day in the year.", "Ralph Waldo Emerson"),
        Quote("Celebrate one small win today, however small."),

        Quote("Laughter is the best medicine.", "Proverb"),
        Quote("Slow and steady wins the race.", "Aesop"),
        Quote("Be not afraid of going slowly; be afraid only of standing still.", "Chinese proverb"),
        Quote("Step outside for five minutes of fresh air."),

        Quote("Inch by inch, life's a cinch.", "Saying"),
        Quote("A smooth sea never made a skilled sailor.", "English proverb"),
        Quote("Big things have small beginnings.", "Proverb"),
        Quote("Send a friend a message just to say hi."),

        Quote("Bloom where you are planted.", "Saying"),
        Quote("Every day may not be good, but there is something good in every day.", "Saying"),
        Quote("Well begun is half done.", "Aristotle"),
        Quote("Your feelings are valid, and they will pass."),

        Quote("Patience is bitter, but its fruit is sweet.", "Proverb"),
        Quote("The darkest hour is just before the dawn.", "Thomas Fuller"),
        Quote("One today is worth two tomorrows.", "Benjamin Franklin"),
        Quote("Done is better than perfect."),

        Quote("Nothing great was ever achieved without enthusiasm.", "Ralph Waldo Emerson"),
        Quote("Keep calm and carry on.", "British poster, 1939"),
        Quote("Many hands make light work.", "Proverb"),
        Quote("Put on a song you love and really listen to it."),

        Quote("A good laugh and a long sleep are the best cures in the doctor's book.", "Irish proverb"),
        Quote("The best is yet to be.", "Robert Browning"),
        Quote("Healing is a matter of time, but it is sometimes also a matter of opportunity.", "Hippocrates"),
        Quote("Be as kind to yourself as you would be to a friend."),
    )

    fun forDate(date: LocalDate): Quote = all[Math.floorMod(date.toEpochDay(), all.size.toLong()).toInt()]
}
