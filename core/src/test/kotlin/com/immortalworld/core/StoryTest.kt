package com.immortalworld.core
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
@RunWith(Parameterized::class)
class StoryTest(private val name: String, private val check: () -> Unit) {
    @Test fun runCheck() = check()
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = StoryChecks.cases.map { (name, test) -> arrayOf(name, test) }
    }
}
