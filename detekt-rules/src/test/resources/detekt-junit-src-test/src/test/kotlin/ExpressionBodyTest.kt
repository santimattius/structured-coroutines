import kotlinx.coroutines.*
import org.junit.jupiter.api.Test

class ExpressionBodyTest {
    @Test
    fun smoke() = runBlocking {
        println("x")
    }
}
