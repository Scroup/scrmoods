package com.scrmoods.app

import java.time.LocalDate

/** A quote with its author, or a gentle reminder (no author). */
data class Quote(val text: String, val author: String? = null)

/**
 * Quote of the day. The list cycles one entry per day: three quotes, then a reminder.
 * Each date always gets the same entry, so earlier pages of the journal keep their quote.
 */
object Quotes {
    val all = listOf(
        Quote("A journey of a thousand miles begins with a single step.", "Lao Tzu"),
        Quote("We suffer more often in imagination than in reality.", "Seneca"),
        Quote("Adopt the pace of nature: her secret is patience.", "Ralph Waldo Emerson"),
        Quote("Kiss your partner for six seconds, long enough for the affection to register."),

        Quote("The best way out is always through.", "Robert Frost"),
        Quote("Hope is the thing with feathers that perches in the soul.", "Emily Dickinson"),
        Quote("I am not afraid of storms, for I am learning how to sail my ship.", "Louisa May Alcott"),
        Quote("Don't forget to express gratitude."),

        Quote("This too shall pass.", "Persian proverb"),
        Quote("Very little is needed to make a happy life; it is all within yourself, in your way of thinking.", "Marcus Aurelius"),
        Quote("Fall seven times, stand up eight.", "Japanese proverb"),
        Quote("Tell the people you care about that you love them."),

        Quote("Men are disturbed not by things, but by the views which they take of things.", "Epictetus"),
        Quote("Nothing is so strong as gentleness, nothing so gentle as real strength.", "Francis de Sales"),
        Quote("Well begun is half done.", "Aristotle"),
        Quote("Drink a glass of water and take three slow breaths."),

        Quote("Gratitude is not only the greatest of virtues, but the parent of all the others.", "Cicero"),
        Quote("There is no remedy for love but to love more.", "Henry David Thoreau"),
        Quote("Write it on your heart that every day is the best day in the year.", "Ralph Waldo Emerson"),
        Quote("Step outside for ten minutes. Daylight helps."),

        Quote("The soul becomes dyed with the colour of its thoughts.", "Marcus Aurelius"),
        Quote("The best time to plant a tree was twenty years ago. The second best time is now.", "Chinese proverb"),
        Quote("Let us be grateful to people who make us happy; they are the charming gardeners who make our souls blossom.", "Marcel Proust"),
        Quote("You don't have to finish everything today."),

        Quote("Finish each day and be done with it. You have done what you could.", "Ralph Waldo Emerson"),
        Quote("One kind word can warm three winter months.", "Japanese proverb"),
        Quote("He who has a why to live can bear almost any how.", "Friedrich Nietzsche"),
        Quote("Send a friend a message, just to say hi."),

        Quote("Beyond a wholesome discipline, be gentle with yourself.", "Max Ehrmann"),
        Quote("Do what you can, with what you have, where you are.", "Theodore Roosevelt"),
        Quote("Rest is not idleness.", "John Lubbock"),
        Quote("Notice one small good thing today and write it down."),

        Quote("Be kind, for everyone you meet is fighting a hard battle.", "Ian Maclaren"),
        Quote("It is not that we have a short time to live, but that we waste a lot of it.", "Seneca"),
        Quote("Nature does not hurry, yet everything is accomplished.", "Lao Tzu"),
        Quote("Rest counts as progress."),

        Quote("No man is free who is not master of himself.", "Epictetus"),
        Quote("Simplicity, simplicity, simplicity!", "Henry David Thoreau"),
        Quote("Waste no more time arguing about what a good man should be. Be one.", "Marcus Aurelius"),
        Quote("A short walk is still a walk."),

        Quote("If one advances confidently in the direction of his dreams, he will meet with a success unexpected in common hours.", "Henry David Thoreau"),
        Quote("Healing is a matter of time, but it is sometimes also a matter of opportunity.", "Hippocrates"),
        Quote("Be as kind to yourself as you would be to a friend."),
    )

    fun forDate(date: LocalDate): Quote = all[Math.floorMod(date.toEpochDay(), all.size.toLong()).toInt()]
}
