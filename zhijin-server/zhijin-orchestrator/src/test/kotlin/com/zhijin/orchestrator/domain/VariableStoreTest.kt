package com.zhijin.orchestrator.domain

import com.zhijin.orchestrator.domain.FieldSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VariableStoreTest {

    private val store = VariableStore()

    @Test
    fun `写入节点输出后按引用读取`() {
        store.writeNodeOutput("n1", "out", "hello")
        assertEquals("hello", store.resolveRef(FieldSource.Ref("n1", "out")))
    }

    @Test
    fun `读取未写入引用抛异常`() {
        assertThrows(IllegalStateException::class.java) {
            store.resolveRef(FieldSource.Ref("n1", "missing"))
        }
    }

    @Test
    fun `字面量直接返回`() {
        assertEquals(42L, store.resolveRef(FieldSource.Literal(42L)))
    }
}
